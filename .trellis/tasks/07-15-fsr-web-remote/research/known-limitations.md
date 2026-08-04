# BBS Web Remote: implemented contracts and known limitations

Last source audit: 2026-07-16. This document separates implemented contracts
that still require real-device acceptance from intentional MVP boundaries.
Automated tests do not replace the manual device/client matrix below.

The reference consumer remains the independent `bbs-web-remote/` NeoForge
addon directory. FSR Addon/API stays exactly `2.0`; the addon's browser and
Minecraft collaboration wires are independently versioned, additive `1.1`
protocols and do not change the Core API version.

## Implemented contracts awaiting manual acceptance

### Per-device surface demand

The browser exposes `自动`, `流畅`, `高清`, `原画` and `自定义`. The preset
profiles and custom bounds are:

- `smooth`: 960x540, 30 FPS, JPEG quality 68;
- `high`: 1280x720, 60 FPS, JPEG quality 78;
- `original`: 1920x1080, 120 FPS, JPEG quality 90;
- `custom`: 320x180..1920x1080, 30..120 FPS, JPEG quality 30..95.

The explicit preference is stored under
`bbs-web-remote.media-quality` in `localStorage`; unavailable or malformed
storage falls back to `自动`. Automatic mode uses bounded browser hints rather
than a throughput probe: constrained/save-data/slow network, a phone-sized
screen or low reported memory/CPU selects `smooth`; a fine-pointer high-DPI
display with at least 8 GiB/8 logical cores and an unrestricted 4G/10 Mbps
connection may select `original`; other and unknown environments use `high`.
Network/device-environment changes are debounced and only reconnect media when
the effective tier changes.

The browser attaches one literal `profile=smooth|high|original` query parameter
or a complete custom width/height/FPS/quality query to `/ws/media`. A manual or automatic tier change replaces only that media
WebSocket; `/ws/ui`, control ownership and the active mirror session remain
online. The server parses this untrusted hint at the media-upgrade boundary:
missing, repeated, percent-encoded and unknown values fall back to `high`.
Legacy `mobile` maps to `smooth`, while `desktop` maps to `high`. With multiple
media viewers, width, height, FPS and JPEG quality take their bounded maxima;
zero viewers produces no active surface demand. `FsrRenderSurfaceAdapter` uses
the lock-consistent aggregate audience and puts its JPEG quality in the surface
wire metadata. Browser wire remains `1.1`; FSR Addon/API remains exactly `2.0`.

Parser/alias/fallback and component-wise aggregation are covered by the server
smoke regression. Browser regressions cover localStorage fallback, automatic
tier decisions, debounce, and media-only reconnect. Real phones, tablets,
mixed-tier viewers, achieved FPS, visual quality and bandwidth remain manual
acceptance items.

### Standalone world Replay placement

FSR captures the main render target with logical kind `WORLD_REPLAY` when a
`PlayCameraController` is active. With a real BBS screen, FSR records a
full-logical-viewport `WORLD_REPLAY` quad before native UI commands. With no
screen, active matching demand creates one placement-only mirror session using
the window's GUI-scaled and framebuffer dimensions. Its frame contains exactly
one full-viewport Replay quad; resize retains the session and advances its
session-local frame sequence.

The synthetic session closes when Replay/demand stops, a real UI takes over,
or mirror lifecycle cleanup runs. It has no native input owner and is therefore
view-only. `testUiSurfacePlacement` covers demand gating, painter order,
open/resize/close, bounds, UVs and isolation from real UI sessions. Actual
world playback, subtitles, GPU capture and browser composition remain manual
acceptance items.

### Direct form and Morph previews

The previous direct-form-preview gap is implemented in source. Every visible
form preview that passes through `FormRenderer.renderUI(...)` contributes its
viewport-clipped logical rectangle. At the end of the native UI frame, Core
captures one bounding union of those visible rectangles from the final main
framebuffer and publishes it as one `FORM_PREVIEW_ATLAS` JPEG plus one matching
union placement quad. This is intentionally neither one image per form cell nor
an entire-page screenshot; pixels and gaps inside the bounding union share the
same lossy media frame.

The Morph selector also publishes `MORPH_WORLD_PREVIEW` as a full-viewport
world-composite surface before native UI commands, preserving the intended
painter order with the real world behind the controls. Core keeps world
Film/Replay/Morph capture and UI form-preview capture on two fixed independent
lanes with separate generation/PBO/RGB/latest slots and two lazy long-lived
JPEG encoders. The addon keeps the form-preview lane on its own physical handle
and generation gate; a new atlas frame cannot retire a valid world frame, or
vice versa.

`UIModelRenderer.render(...)` now contributes its actual clipped viewport to
the same union after its immediate GPU draw and before unclip. This covers the
full model editor path (`UIFormRenderer`/`UIPickableFormRenderer`) that real
client feedback showed as a black browser viewport with otherwise-correct
native controls. A renderer that bypasses both `FormRenderer.renderUI(...)` and
`UIModelRenderer.render(...)` still needs an explicit collector hook.

`testUiSurfacePlacement` plus `compileClientJava` pass for the current repair.
The executed Core regressions cover Morph painter order, the form-preview union
rectangle's logical placement and existing generation/callback fences. The
executed addon targeted/browser regressions cover independent physical handles,
multiple surface UVs and high-quality filtering for surfaces while ordinary UI
textures retain nearest-neighbour sampling; the final addon clean gate also
passed. Real GPU readback, the actual
model/entity/item/block/particle
thumbnails, scrolling/clipping and Morph world composition are still unchecked
manual acceptance items.

### Fixed native Dashboard open

An authorized browser `CONTROLLER` may request one fixed native target: the BBS
Dashboard. This is not a headless editor or arbitrary screen factory. The
request cannot carry a class name, panel id, Film, file, path or command, and it
never replaces an unrelated open Minecraft screen. `VIEWER` authorization does
not permit the request.

Core exposes the additive API 2.0 method
`BBSClientApi.requestDashboardOpen(...)`. It requires `CLIENT_UI`, uses a
16-entry mailbox, admits at most one pending request per addon and drains at
most four requests per client tick. Stable results are `OPENED`,
`ALREADY_OPEN`, `NO_WORLD`, `BUSY`, `STALE`, `REJECTED` and `FAILED`.
Cancellation before drain admission prevents the native open; disconnect,
client stop and observed screen/world/Replay lifecycle transitions fence queued
work as stale. An active world Replay may open the Dashboard so its native
controls become reachable.

The addon carries this as an additive browser wire `1.1` fixed 13-byte
`OPEN_UI` (`0x33`) request. A result is only the bounded request outcome; a new
`OPENED` result must wait for the later real mirror `SESSION`. For
`ALREADY_OPEN`, an already-active authoritative browser session completes the
pending request immediately; if that session has not arrived yet, the browser
waits for the racing `SESSION`. The native session remains authoritative for
rendering and input in both branches. Source-level Core, addon and browser
regressions cover validation, single-flight/rate/lifecycle behavior. A real
Minecraft/browser round trip is still a manual acceptance item.

## Automated evidence status — 2026-07-16

The earlier pre-form-preview serialized fresh automated gate is green. It
completed a clean Core build with input/open-mailbox, Film,
surface-placement and demand tests; a clean addon targeted protocol,
collaboration, co-control and server-smoke matrix; the addon full check/build;
two JavaScript syntax checks; and all six browser Node regressions. The API is
still exactly `2.0`, both addon wires are additive `1.1`, `new` passes
`git diff --check`, and `old2.0/bbs-fs` remains clean.

For the current direct-preview/quality repair,
`new\gradlew.bat testUiSurfacePlacement compileClientJava --no-daemon
--console=plain` has exited `0`. The final Core gate
`new\gradlew.bat clean testUiSurfacePlacement build --no-daemon
--console=plain` also exited `0` in 15m19s. The current addon targeted/browser
gate (`testWireProtocol testServerSmoke testCoControl verifyWeb
compileClientJava`) exited `0` in about 2m42s; `verifyWeb` includes the quality,
mobile-input, reconnect and render-fidelity regressions. The final addon
`clean check build` then exited `0` (`BUILD SUCCESSFUL`, 52 tasks, 7m38s).
The post-repair incremental Core and addon builds also exit `0`; the new
floating-toolbar regression is included in the addon `verifyWeb` gate. Current
production jars are recorded in `implement.md`; the Core jar is 6,832,537
bytes with SHA-256
`8B66AD6D08BD7DE676D1231231844A2D6FEE279A048998237D9E0C620B71FF25`, and the
addon all jar is 2,952,115 bytes with SHA-256
`BA6E5BAA228A395AE59AB71547B2EB0CAFDADD44F9C065DAE6CA30D46D28091D`.

For that earlier snapshot, both production jars postdated every included
production source/resource. Exact commands, durations, hashes, sizes and
manifest dependencies are recorded in `implement.md`. This historical
automated evidence does not accept any unchecked manual browser/device,
two-client collaboration or performance item below.

The partial real-runtime smoke proved that NeoForge can load FSR and the addon
on a dedicated server, that client A initializes both collaboration broker
directions and the web service, and that client B reaches valid mod discovery.
Both clients did not enter the world together, so no create/join or live Film
collaboration item is accepted from that run.

## Intentional MVP boundaries

- FSR/Minecraft must remain running. There is no headless browser-only BBS
  editor and no second UI lifecycle.
- BBS controls are mirrored only from a real native `UIScreen`. An authorized
  controller may request the one fixed native Dashboard, but cannot select an
  arbitrary screen/panel or bypass native lifecycle and input. The standalone
  Replay exception remains a view-only placement session until such a real
  Dashboard session opens.
- Playback audio and synchronized audio/video transport are not implemented.
- No public relay, cloud account, TURN/WebRTC service, TLS termination or
  Internet exposure workflow is provided.
- No browser file upload, arbitrary local-file read or direct access to FSR
  managers, UI elements, OpenGL texture ids or mutable Film objects is exposed.
- Browser co-control serializes gestures and text focus onto one native UI. It
  is not simultaneous independent editing.
- Cross-Minecraft collaboration is server-ordered last-writer-wins, not a CRDT.
  Sessions and cached canonical snapshots are in memory and do not survive a
  server restart.
- Collaboration does not reuse Together's private protocol and is not wire
  compatible with Together clients.

## Known fidelity and operational constraints

- The core recorder covers clip push/pop, transformed colored quads, mapped
  static PNG texture quads, authoritative glyph runs and explicit surface
  quads. Raw integer texture paths, custom shaders, unmapped dynamic/animated
  textures and rendering that bypasses these terminal hooks may be absent from
  the browser frame. Form picker/list previews routed through
  `FormRenderer.renderUI(...)` are now covered by the visible-union
  `FORM_PREVIEW_ATLAS`; full-screen editor previews rendered through
  `UIModelRenderer`/`UIFormRenderer` are covered by the renderer-level area
  hook. Other direct GPU paths that do not enter that collector or register
  another explicit surface remain a fidelity limitation.
- `FORM_PREVIEW_ATLAS` is a single JPEG of the visible bounding union. It can
  include unchanged gaps/background between previews, and its quality remains
  bounded by the selected `smooth`/`high`/`original` JPEG preset. The browser
  requests high-quality scaling for surfaces, but smoothing cannot restore
  detail that was removed by capture downscale or JPEG compression.
- Wire 1.1 preserves glyph text, bounds, color and shadow, but the fallback uses
  a browser system font when no Minecraft glyph atlas is available. Layout
  bounds remain authoritative; exact glyph shape and emoji style can differ by
  operating system.
- Visual streams are latest-wins. Under GPU, encoder or network pressure an
  intermediate UI/surface frame may be skipped; reliable input transitions are
  handled separately.
- The service accepts plain HTTP and WebSocket only. In LAN mode its HttpOnly
  token is protected from page JavaScript, but transport confidentiality still
  depends on the trusted network.
- Host allowlisting is literal. The exact IP address or DNS name used in the
  browser URL must be listed without scheme or port. Reverse proxies and
  alternate public host names are outside the reviewed boundary.
- A pairing code is valid for two minutes and one successful redemption. A
  browser session lasts at most 15 minutes; reconnecting after expiry requires
  a newly issued code.
- The server caps active browser/UI/media participants and bounded caches. A
  seventeenth browser or another exhausted resource budget can be rejected
  instead of degrading all existing viewers.

## Manual acceptance matrix

Record browser/OS versions, Minecraft logs and any observed bandwidth/FPS next
to each checked item. Run the automated builds first:

```text
new\gradlew.bat build --no-daemon
bbs-web-remote\gradlew.bat compileClientJava check build --no-daemon
```

### Desktop loopback

- [ ] Launch NeoForge 1.21.1/Java 21 with FSR and the addon `-all.jar`; confirm
      the log advertises `http://127.0.0.1:27165` and no pairing requirement.
- [ ] Enter a world with no Minecraft screen open, connect a browser as
      `CONTROLLER`, request **Open BBS Dashboard**, and verify the matching
      `OPEN_UI_RESULT` is followed by the real native mirror `SESSION`.
- [ ] Verify `VIEWER` cannot request Dashboard; an existing Dashboard is
      idempotent, an unrelated open screen is not replaced, no-world is
      rejected, and a screen/world/Replay lifecycle change before dispatch
      returns stale without a delayed open.
- [ ] Open a BBS screen and connect current Chrome plus Firefox-class browsers.
      Verify session open, close, resize and screen replacement.
- [ ] Verify English, Chinese and emoji text is readable and remains inside the
      authoritative run bounds; compare shadowed text with the native screen.
- [ ] Verify move, left/right/middle click, drag, two-axis wheel, key
      press/repeat/release, Shift/Ctrl/Alt and committed Chinese IME text.
- [ ] Verify a Film preview surface is visible, painter-aligned and updates
      independently from the UI commands while playing and scrubbing.
- [ ] Close/switch the BBS screen during held mouse/key input; verify no stuck
      state and no stale frame or surface is accepted in the new session.

### Mobile LAN

- [ ] Start with explicit `bbs.webRemote.lan=true`, a literal
      `bbs.webRemote.allowedHosts` entry and the intended bind address/port.
- [ ] From an unauthenticated/incognito device, confirm dynamic assets and both
      WebSockets are unavailable before pairing.
- [ ] Pair one phone as `VIEWER`; confirm it cannot request controller
      permission. Rotate with `/bbswebremote pair`, then pair another browser as
      `CONTROLLER`; confirm the first code cannot be reused.
- [ ] In landscape, verify Fit, pinch zoom, zoomed pan, fit-scale two-finger
      scroll, L/R/M single-finger click/drag, modifier locks and mobile IME.
- [ ] Verify a phone selects 960x540@30/Q68. Add a desktop media viewer and
      verify `自动` displays the resolved tier. Select `高清` and `原画`, confirm
      only the media socket reconnects, and verify the preference survives a
      page reload.
- [ ] Mix preset and custom viewers; verify shared demand follows the bounded
      component-wise maximum, drops after that viewer disconnects, and accepts
      legacy `mobile`/`desktop` aliases without changing wire `1.1`.
- [ ] Let a token expire or revoke/evict it in a deterministic test build;
      confirm UI and media sockets close with no token printed in logs or JSON.

### Two-browser co-control

- [ ] Pair two distinct `CONTROLLER` sessions and verify unique participant
      identities, names, colored cursors and role badges.
- [ ] Hold a timeline drag in browser A; verify browser B remains a viewer of
      the motion but cannot interrupt the active lease.
- [ ] Release explicitly, time out, hide the tab and disconnect A in separate
      runs; verify synthetic releases complete and B can immediately acquire
      the next lease.
- [ ] Hold keys/buttons independently in A and B; disconnect one and confirm the
      other participant's held state is not released.

### Film and world Replay

- [ ] Verify Film panel playback/pause/scrub with `FILM_PREVIEW`, including
      subtitle composition and correct single Y flip.
- [ ] Run a world Replay with the Film UI active and confirm the shared physical
      capture is visible through the valid Film surface placement.
- [ ] Close the Film UI while world Replay remains active; verify the browser
      opens a placement-only session at the GUI-scaled size, receives a
      full-viewport Replay surface, handles resize without changing session,
      and closes it promptly when playback stops.
- [ ] Measure that surface capture/encoding does not block the render thread and
      that stale surface frames are dropped before reliable UI/input traffic.

### Form and Morph previews

- [ ] Open the native form picker/list and verify model, mob/entity, item,
      block, billboard/extruded and particle previews are visible in the remote
      browser rather than empty cells.
- [ ] Scroll and resize the form picker; verify the captured visible union
      follows viewport clipping, does not overwrite unrelated UI, and does not
      reuse a stale atlas after switching screens.
- [ ] Open Morph selection and verify the real world remains visible through
      `MORPH_WORLD_PREVIEW` behind the native picker and labels on both desktop
      and mobile profiles.
- [ ] Compare local and remote sharpness at all three media profiles and record
      whether the union crop, selected `smooth`/`high`/`original` JPEG quality
      or browser scaling is the limiting factor before changing bandwidth
      defaults.

### Two Minecraft clients and server collaboration

- [ ] Install FSR and the addon on two clients and the addon/FSR server side.
      Confirm non-operator `create`/`join` is denied by the default level-2
      policy, then grant the intended permissions.
- [ ] On client A run `/bbswebremote collab create <key>`; on client B run
      `/bbswebremote collab join <key>`. Open the intended Film on both and
      verify `status` reports the same session/revision/member count.
- [ ] Exchange ordinary property, keyframe and Replay list edits plus Undo/Redo;
      verify each atomic batch applies once and produces no echo loop.
- [ ] Verify remote editor type, playhead, cursor and Replay selection only
      appear in the matching semantic context and are cleared on leave.
- [ ] Edit the same semantic path concurrently and verify deterministic server
      order/last-writer outcome, followed by matching revisions.
- [ ] Trigger a structural commit large enough to require a checkpoint; verify
      bounded GZIP snapshot transfer, SHA-256 validation and readiness recovery.
- [ ] Join client B while A continues editing; verify snapshot N followed by
      contiguous N+1...current catch-up before B becomes ready.
- [ ] Force a sequence gap, stale revision, malformed/oversized mutation and
      snapshot retry; verify rejection/resync without partial Film mutation.
- [ ] Close the Film, disconnect and run `leave` in separate cases; verify final
      coalesced edits/checkpoints flush before cleanup and presence disappears
      immediately.

## Completion rule

The remote-open, queue/protocol-hardening, surface lifecycle and quality-profile
work now has source-level contracts and dedicated regressions. The current Core
clean gate, addon targeted/browser matrix and final addon `clean check build`
are green, with current production artifacts recorded in `implement.md`. The
MVP is not fully accepted until the applicable real browser/device, form/Morph
preview, mixed-quality, two-client collaboration and performance items have
recorded evidence.
