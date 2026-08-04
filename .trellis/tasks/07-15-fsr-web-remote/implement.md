# FSR 网页预览与远程控制：实施计划

## Current execution queue — synced 2026-07-16

Checkboxes in the first list describe source implementation, not final release
acceptance. The earlier pre-form-preview snapshot passed one serialized fresh
automated gate. The current direct-preview/quality repair has passed its final
clean Core gate and the addon targeted/browser matrix plus final addon clean
gate recorded below. All real-device/client and performance checks remain open
until evidence is recorded below.

Implemented in the current source snapshot:

- [x] FSR UI Mirror/input/surface and Film collaboration interfaces are additive,
  client-only API `2.0` extensions; `BBSApiVersion` remains exactly `2.0`.
- [x] The fixed native Dashboard open API is implemented through a 16-entry
  client-thread mailbox with one pending request per addon, four drains per tick,
  cancellation/lifecycle fences and stable `OPENED`, `ALREADY_OPEN`, `NO_WORLD`,
  `BUSY`, `STALE`, `REJECTED` and `FAILED` results.
- [x] `bbs-web-remote/` remains an independent Gradle/NeoForge addon directory.
  It depends on the adjacent `new/build/libs/bbs-2.3.1.jar`; it is not a `new/`
  subproject and has no dependency on `old2.0/bbs-fs`.
- [x] Browser wire remains `1.1`. Remote open is an additive fixed 13-byte
  `OPEN_UI` (`0x33`) Dashboard request plus bounded `OPEN_UI_RESULT`; it does not
  accept a class, panel, Film, path, file or command and does not create a second
  UI lifecycle.
- [x] Jetty UI/media separation, chunk/resource deduplication, latest-wins visual
  backpressure, LAN pairing/authentication, browser co-control and the optional
  common/server Film collaboration layer are present in source.
- [x] The previous ingress/caching hardening TODOs are implemented in source:
  authorization and bounds are checked before retaining browser envelopes,
  input key/text/message sizes are bounded before allocation, stable web bundle
  URLs revalidate with strong ETags/304, chunk feedback is validated/rate-limited,
  and collaboration metadata/warnings have bounded preflight paths.
- [x] Canvas2D desktop/mobile command replay, wire `1.1` glyph fallback,
  `FILM_PREVIEW`, in-UI `WORLD_REPLAY`, and placement-only standalone Replay
  source paths are implemented. These source contracts do not replace real GPU,
  browser or device acceptance.
- [x] Direct model/entity/item/block/particle form-list previews now use
  `FORM_PREVIEW_ATLAS`: visible `FormRenderer.renderUI(...)` regions are clipped
  to their viewports, combined into one bounding union, captured as one UI-lane
  JPEG and placed back at that union. It is neither one JPEG per cell nor an
  entire-page screenshot. Morph selection also exposes a full-viewport
  painter-first `MORPH_WORLD_PREVIEW` behind the native UI commands.
- [x] Surface capture now has two fixed, bounded, long-lived lanes: WORLD for
  Film/Replay/Morph logical aliases and UI for the form-preview union. The lanes
  retain independent generation/PBO/RGB/latest slots and two lazy JPEG encoder
  workers while sharing one client-lifetime monotonic sequence. The addon keeps
  the atlas on an independent physical handle/generation gate and enables
  high-quality smoothing only for raster surface draws.
- [x] The browser now exposes `自动` / `流畅` / `高清` / `原画` media quality.
  The selected preference is retained in `localStorage`; `自动` derives the
  effective tier from screen/DPR, pointer type, device memory/CPU and Network
  Information hints. A tier change reconnects only `/ws/media`, preserving the
  UI/control socket and participant/control state. Server aggregation uses the
  highest tier requested by any active media viewer and keeps `mobile` as a
  `smooth` alias and `desktop` as a `high` alias.
- [x] The bounded media presets are now `smooth` 960x540@30/Q68, `high`
  1280x720@60/Q78 and `original` 1920x1080@120/Q90. The browser wire remains
  additive `1.1`, and FSR Addon/API remains exactly `2.0`.
- [x] Targeted Core regression for this repair is green:
  `new\gradlew.bat testUiSurfacePlacement compileClientJava --no-daemon
  --console=plain` exited `0`. Placement/lifecycle assertions cover Morph
  painter order, the form-preview union rectangle's logical placement and the
  existing generation/callback fences; this is not real GPU or browser/device
  acceptance.
- [x] Final current Core gate is green:
  `new\gradlew.bat clean testUiSurfacePlacement build --no-daemon
  --console=plain` exited `0` in 15m19s.
- [x] Current addon targeted/browser gate is green:
  `bbs-web-remote\gradlew.bat testWireProtocol testServerSmoke testCoControl
  verifyWeb compileClientJava --no-daemon --console=plain` exited `0` in about
  2m42s. `verifyWeb` includes browser protocol, glyph, session lifecycle,
  render-fidelity, mobile-input and media-reconnect coverage, including quality
  persistence/auto selection and media-only reconnect behavior.
- [x] The earlier pre-form-preview 2026-07-16 serialized Core, addon and browser
  gate is green.
  For that earlier snapshot, both production jars postdate every included
  production source/resource; API remains exactly `2.0`, browser/collaboration
  wires remain additive `1.1`, `new` passes `git diff --check`, and
  `old2.0/bbs-fs` remains clean.

Active TODO, in priority order:

- [x] **P0 startup crash compatibility repair:** the 2026-07-17 17:10 client
  report failed in `FsrRenderSurfaceAdapter.<clinit>` while constructing its
  120 FPS preset. The workspace Core built at 15:22 is the current artifact
  and its classfile declares a 120 FPS ceiling, but the report's Minecraft
  instance loaded same-named jars from
  `D:\应用\pcl2\BBS\versions\1.21.1-NeoForge_21.1.235\mods`; that path was not
  available for post-crash hashing and the runtime constructor behavior proves
  that its loaded Core enforced the legacy 30 FPS ceiling. The addon now reads
  the ceiling reflectively from the actually loaded Core class (avoiding
  compile-time constant inlining), falls back to the API 2.0 compatibility
  ceiling of 30, and constructs every preset/custom demand through one clamping
  helper. A same-version legacy Core can therefore reduce media FPS without
  failing mod construction.
- [x] **P0 custom quality and 30–120 FPS:** raise the additive Core surface
  demand ceiling to 120 FPS; update all presets to 30/60/120 FPS; add a
  persistent browser custom width/height/JPEG-quality/FPS editor; strictly
  validate `/ws/media?profile=custom&width=...&height=...&fps=...&quality=...`;
  aggregate active viewers within 1920x1080/Q95/120 and preserve the existing
  no-deflate JPEG channel plus bounded latest-wins encoder/network queues.
  The implementation also preserves capture phase at 144/165 Hz, selects the
  newest ready PBO, writes one final media envelope allocation, and removes the
  browser's per-frame full-resolution flip canvas.
- [x] **P0 final addon clean gate for the direct-preview/quality repair:**
  `bbs-web-remote\gradlew.bat clean check build --no-daemon --console=plain`
  exited `0`, `BUILD SUCCESSFUL`, 52 tasks in 7m38s. The earlier overlapping
  attempt that ran while Core `clean` temporarily removed the adjacent FSR jar
  reached only `verifyLocalFsrJar`; it was not a source/test failure.
- [x] **P0 model-editor Web blank repair and movable toolbar:** direct-GPU
  `UIModelRenderer` viewports now join the clipped `FORM_PREVIEW_ATLAS` union,
  so `UIFormRenderer`/`UIPickableFormRenderer` editor models are no longer
  omitted while surrounding controls render. The canvas toolbar now has an
  explicit drag handle and collapse/restore button; bounded position and
  collapse state persist in `localStorage` and re-clamp after resize/orientation
  changes. `testUiSurfacePlacement compileClientJava` and all nine `verifyWeb`
  tasks, including the new floating-toolbar regression, pass.
- [ ] **P1 real-client form/Morph preview:** verify model, entity, item, block
  and particle thumbnails are visible in the remote form picker/list while
  scrolling and clipping; verify `MORPH_WORLD_PREVIEW` remains behind native UI
  controls on desktop and mobile with no stale frame after a screen change.
- [ ] **P1 real-client remote open:** with no native BBS screen active, verify a
  real authorized browser `CONTROLLER` can request the fixed Dashboard, receives
  a matching result, then observes the authoritative native `SESSION`; verify a
  `VIEWER`, no-world state and unrelated open Minecraft screen are rejected.
- [ ] Complete real desktop/mobile browser input, rendering and Film/world Replay
  smoke tests, including Chinese IME and two-browser lease handoff.
- [ ] Complete the two-Minecraft-client create/join/edit/Undo/Redo/conflict/
  checkpoint/disconnect collaboration matrix.
- [ ] Record desktop/mobile bandwidth, FPS and capture/encode render-thread cost
  against the PRD targets.

## Phase A — Baseline and contracts

- [x] Record `new` commit/worktree baseline and avoid unrelated migration files.
- [x] Confirm Jetty 12.1.11 as the embedded HTTP/WebSocket library and RFC 7692 per-message deflate as browser-native UI compression.
- [x] Define bounded, immutable client-only UI mirror DTOs and subscription/control contracts.
- [x] Add the fixed-target `requestDashboardOpen(...)` API, bounded client-thread
  mailbox, lifecycle/cancellation fences and deterministic status tests without
  changing Addon/API `2.0`.
- [x] Add API contract/unit tests for capability checks, session lifecycle and invalid input.
- [x] Update `new/docs/addon-api-2.md` without changing the API version.

Validation gate:

- `new/gradlew.bat compileClientJava`
- `new/gradlew.bat testUiInputMailbox` for input and fixed Dashboard open mailbox
- targeted UI mirror/surface/collaboration tests
- API version search confirms `2.0`

Rollback point: all core changes remain isolated to the new public client package, mirror bridge and narrow render/input hooks.

## Phase B — FSR capture and native input

- [x] Implement opt-in recorder lifecycle around active `UIScreen` frames.
- [x] Record leaf `Batcher2D` primitives without double-recording helper calls.
- [x] Add asset indirection/content identity and glyph positioning support.
- [x] Add control lease, held-state and bounded client-thread input dispatch.
- [x] Add diagnostics for unsupported/direct rendering regions.

Validation gate:

- deterministic frame capture tests for clip/quad/texture/text ordering
- input sequence tests for click, drag, wheel, modifiers and teardown releases
- `new/gradlew.bat build`

## Phase C — Independent addon and optimized transport

- [x] Scaffold `bbs-web-remote/` as its own NeoForge 1.21.1/Java 21 client mod.
- [x] Register an API 2.0 descriptor with `CLIENT_UI` and `CLIENT_RENDER` while keeping API version `2.0`.
- [x] Implement loopback Jetty HTTP/static asset server, compressed `/ws/ui`, and uncompressed `/ws/media` upgrades.
- [x] Implement one canonical bounded binary decoder/encoder shared by all server consumers.
- [x] Implement resource interning/content hashes, 4–16 KiB XXHash64+byte-verified chunks, full frame manifests, keyframes and missing-chunk resync.
- [x] Implement per-client backpressure, latest-frame replacement, rate/size limits and viewer/controller roles.
- [x] Implement pairing/session token and Origin validation; add LAN opt-in config.
- [x] Add the controller-only, fixed Dashboard `OPEN_UI` request as an additive
  browser wire `1.1` message, with bounded ingress, single-flight/rate control
  and lifecycle-correlated result handling.

Validation gate:

- protocol round-trip/property tests
- keyframe + delta reconstruction equals source frame
- corrupt/truncated/oversized payload rejection tests
- slow-client queue remains bounded
- addon Gradle build

Rollback point: server/protocol is wholly inside `bbs-web-remote/`; disabling the addon leaves FSR behavior unchanged.

## Phase D — Browser renderer and controls

- [x] Serve a dependency-light web bundle from addon resources.
- [x] Implement Canvas2D command replay, asset cache, surface composition and keyframe resync.
- [x] Implement desktop mouse/wheel/keyboard/IME and controller ownership UI.
- [x] Implement mobile landscape layout, pinch zoom/pan, touch pointer, virtual buttons/modifiers and hidden text input.
- [x] Add persistent Auto/Smooth/High/Original media quality selection; automatic
  mode reacts to bounded device/network hints, and changing the tier reconnects
  only the media WebSocket.
- [x] Add connection, bitrate/FPS, role and unsupported-surface diagnostics.
- [x] Add wire `1.1` authoritative UTF-8 glyph runs and Canvas atlas/system-font fallback for readable English, CJK and emoji text.
- [x] Add **Open BBS Dashboard** without recreating BBS controls in HTML; a
  new `OPENED` result waits for the native mirror `SESSION` lifecycle. For
  `ALREADY_OPEN`, an existing authoritative browser session completes the
  request immediately; otherwise the browser waits for the racing `SESSION`.

Validation gate:

- browser decoder/renderer tests with recorded fixture frames
- Java and Node glyph wire round-trip/bounds/malformed-input tests
- desktop Chrome/Firefox smoke test
- Android/iOS-class mobile browser smoke test or device emulation
- accessibility check for controls and focus handling

## Phase E — Film/Replay surfaces

- [x] Locate and register Film preview and world Replay framebuffer regions through the generic surface API.
- [x] Implement GPU downscale, render-thread three-slot PBO/fence rings, pooled
  RGB buffers and two bounded long-lived STB JPEG encoders for the fixed
  WORLD/UI lanes.
- [x] Keep UI and surface sequences independent; drop stale surfaces first under pressure.
- [x] Upgrade `smooth` to 960x540@30/Q68, `high` to 1280x720@60/Q78 and
  `original` to 1920x1080@120/Q90; add bounded custom demand, component-wise
  viewer aggregation and preserve legacy
  `mobile`/`desktop` aliases.

Validation gate:

- Film panel preview visible and aligned with its command bounds
- world Replay playback visible while native controls remain functional
- no synchronous GPU readback/encoding on render thread
- `smooth` 540p, `high` 720p and `original` 1080p bandwidth/performance
  measurements

## Phase F — Integration and quality

- [x] Run `trellis-check` across core, addon, protocol and web layers.
- [x] Verify loopback default, pairing, controller gesture/text-focus ownership and participant-scoped teardown in deterministic server tests.
- [x] Verify no new UI Mirror/render/Film DTO exposes local paths, tokens, GL
  ids, mutable FSR objects, `UIElement`, `UIBaseMenu` or `GuiGraphics`. The
  legacy API 2.0 render callback still exposes `GuiGraphics` for compatibility;
  this task did not add or widen that pre-existing signature.
- [x] Record known unsupported draw paths, intentional MVP boundaries and the
  outstanding manual matrix in `research/known-limitations.md`.
- [x] Build both projects from clean output directories while reusing the pinned
  local dependency cache.

Fresh automated gate for the earlier pre-form-preview 2026-07-16 source snapshot:

- [x] Core: `new\gradlew.bat clean testUiInputMailbox build --no-daemon
  --console=plain` — exit `0`, `BUILD SUCCESSFUL` in 4m51s (42 tasks).
  `BBSUiInputDispatcherTest`, `BBSFilmCollaborationApiTest`,
  `BBSUiMirrorDemandTest`, `BBSUiSurfacePlacementTest`, video export, `check`
  and `build` all passed.
- [x] Addon targeted: `bbs-web-remote\gradlew.bat clean testWireProtocol
  testCollaborationProtocol testCollaborationClient testCollaborationPresence
  testCollaborationSnapshot testCoControl testServerSmoke --no-daemon
  --console=plain` — exit `0`, `BUILD SUCCESSFUL` in 4m52s (38 tasks); all seven
  test mains passed. The first pre-gate run found a stale test fixture using
  assigned opcode `0x7e` as “unknown”; only the two fixtures changed to
  unassigned `0x7d`, production `COMMAND_UNSUPPORTED=0x7e` and its decoder were
  unchanged, and the isolated plus full wire tests passed afterward.
- [x] Addon full: `bbs-web-remote\gradlew.bat compileClientJava check build
  --no-daemon --console=plain` — exit `0`, `BUILD SUCCESSFUL` in 1m (51 tasks).
- [x] Web: syntax-check `protocol.js` and `app.js`, then run
  `browser-protocol`, `glyph-protocol`, `session-lifecycle`, `render-fidelity`,
  `mobile-input` and `media-reconnect` Node regressions against the same source
  snapshot — all eight direct commands exited `0`; the addon full gate also ran
  the same checks successfully.
- [x] Final audits: API version is exactly `2.0`, browser/collaboration wire is
  additive `1.1`, `git -C new diff --check` succeeds, `old2.0/bbs-fs` is clean,
  no public API/wire leak is present, and both production jars postdate their
  included production sources/resources.

Current final clean gate after the direct-preview/quality repair:

- [x] Core clean: `new\gradlew.bat clean testUiSurfacePlacement build
  --no-daemon --console=plain` — exit `0` in 15m19s.
- [x] Addon clean: `bbs-web-remote\gradlew.bat clean check build --no-daemon
  --console=plain` — exit `0`, `BUILD SUCCESSFUL`, 52 tasks in 7m38s.
- [x] The current Core and addon jars postdate their included production
  sources/resources. API remains exactly `2.0`; browser/collaboration wires
  remain additive `1.1`.
- [x] Post-repair incremental release gate: `new\gradlew.bat build --no-daemon
  --console=plain` exited `0` in 1m23s; `bbs-web-remote\gradlew.bat build
  --no-daemon --console=plain` exited `0` in 1m54s, including all 52 addon
  tasks and the new floating-toolbar browser regression.

Current custom-quality/30–120 FPS gate — 2026-07-17:

- [x] Core targeted: `new\gradlew.bat testUiSurfacePlacement compileClientJava
  --no-daemon --console=plain` — exit `0`; it covers public 1..120 compatibility,
  121 rejection, 144/165 Hz pacing at 30/60/120, no stall burst and newest PBO
  sequence selection.
- [x] Core final: `new\gradlew.bat testUiSurfacePlacement build --no-daemon
  --console=plain` — exit `0`, `BUILD SUCCESSFUL`, 41 tasks in 3m09s.
- [x] Addon targeted: `bbs-web-remote\gradlew.bat testWireProtocol
  testServerSmoke verifyWeb compileClientJava --no-daemon --console=plain` —
  exit `0`, `BUILD SUCCESSFUL`, 40 tasks in 2m23s.
- [x] Addon clean: `bbs-web-remote\gradlew.bat clean check build --no-daemon
  --console=plain` — exit `0`, `BUILD SUCCESSFUL`, 53 tasks in 9m21s. Wire,
  server lifecycle, collaboration/co-control and all Web regressions passed.
- [x] Final audits: Core Addon/API remains exactly `2.0`; browser and
  collaboration wires remain additive `1.1`; `git -C new diff --check` exits
  `0`; `old2.0/bbs-fs` is clean; both jars postdate the newest included source.

Current production artifacts:

- `new/build/libs/bbs-2.3.1.jar` — 6,835,765 bytes, mtime
  `2026-07-17 15:22:25.895 +08:00`, SHA-256
  `8A9545BF10A4A2D8683AEEB6C8A7C6F7DD4C10E903F5F13C13098A87EBD91B41`.
- `bbs-web-remote/build/libs/bbs-web-remote-0.1.0-all.jar` — 2,958,040 bytes,
  superseded by the startup-compatibility build below. The pre-repair artifact
  had mtime `2026-07-17 15:31:04.199 +08:00` and SHA-256
  `AF64DF362FC3ADD773336ABBE56B6317D3279C3F16CE437BFDAD3AAA02E5BED1`.
  Its processed manifest declares `modId=bbswebremote`, version `0.1.0`, Java
  21, NeoForge `[21.1,)`, Minecraft `[1.21.1]`, and required BBS `[2.3.1,)`.

Startup-crash compatibility gate — 2026-07-17:

- [x] Root-cause evidence: `debug.log` lines 2342–2368 show addon construction
  failing with `IllegalArgumentException: surface frame rate is out of range`
  from `BBSRenderSurfaceDemand` while `FsrRenderSurfaceAdapter` initializes its
  120 FPS preset. The later sound-manager failure is teardown fallout after BBS
  initialization is already broken.
- [x] Workspace Core audit: `new/build/libs/bbs-2.3.1.jar` remains the 15:22
  latest artifact, SHA-256
  `8A9545BF10A4A2D8683AEEB6C8A7C6F7DD4C10E903F5F13C13098A87EBD91B41`;
  direct classfile parsing reports
  `BBSRenderSurfaceDemand.MAX_FRAMES_PER_SECOND == 120`.
- [x] Same-name installation audit: a separate installed instance at
  `D:\1.21\versions\1.21.1-NeoForge_21.1.223\mods\bbs-2.3.1.jar` is 6,832,733
  bytes, SHA-256
  `D7324AE4D7D269B1786EB84F5AF961D4A55E7B56C6AB7FF23B3DDFDE3588897F`,
  and direct classfile parsing reports a 30 FPS ceiling. This is not the
  crash-report's now-unavailable `.235` instance, but proves that multiple
  same-version Core jars with different capabilities coexist on this machine.
- [x] Targeted addon gate:
  `bbs-web-remote\gradlew.bat testWireProtocol compileClientJava --no-daemon
  --console=plain` — exit `0`, `BUILD SUCCESSFUL` in 1m12s. Regression coverage
  asserts legacy 60/120 -> 30 clamping, current 120 preservation, invalid limit
  rejection and successful construction of a 30 FPS demand.
- [x] Final clean addon gate:
  `bbs-web-remote\gradlew.bat clean check build --no-daemon --console=plain` —
  exit `0`, `BUILD SUCCESSFUL`, 53 tasks in 6m15s. Protocol, server, browser,
  collaboration and co-control tests passed. The two compiler warnings are
  pre-existing upstream Jetty removal warnings.
- [x] Final addon artifact:
  `bbs-web-remote/build/libs/bbs-web-remote-0.1.0-all.jar` — 2,958,792 bytes,
  mtime `2026-07-17 18:05:22.066 +08:00`, SHA-256
  `6A3B3BDF96F55433429E66408E53E0A152A2A461AD3DE3AE3F67DE1C85FFAFF7`.
  The packaged adapter class contains the reflective field name and both
  compatibility diagnostics.

Runtime port control — 2026-07-17:

- [x] Add local client commands `start [port]`, `stop`, `restart [port]` and
  `status`, with command ports bounded to `1024..65535` and no implicit LAN
  exposure changes.
- [x] Split runtime transport stop from final client teardown so stop/start
  retains the native UI mirror session and reusable control coordinators.
- [x] Start a different-port replacement before stopping the existing server;
  report bind failure while keeping the existing port online.
- [x] Retain current-session immutable asset publications and prime replacement
  servers so an already-open BBS screen does not lose textures after a port
  change.
- [x] Run targeted protocol/server/compile gates and the final clean addon gate.

Runtime port verification:

- [x] Targeted gate: `bbs-web-remote\gradlew.bat testWireProtocol
  testServerSmoke compileClientJava --no-daemon --console=plain` — exit `0`;
  resource replay/discard, occupied-port retention, server security and client
  command compilation passed.
- [x] Independent Agent review found and the implementation repaired asset
  replay/swap serialization, bounded retained bytes, command-port revalidation
  and render-thread lifecycle blocking before the final gate.
- [x] Final clean addon gate: `bbs-web-remote\gradlew.bat clean check build
  --no-daemon --console=plain` — exit `0`, `BUILD SUCCESSFUL`, 53 tasks in
  10m59s. Protocol, server, co-control, collaboration and all Web regressions
  passed; the two Jetty removal warnings are pre-existing.
- [x] Final addon artifact:
  `bbs-web-remote/build/libs/bbs-web-remote-0.1.0-all.jar` — 2,963,993 bytes,
  mtime `2026-07-17 22:07:19.405 +08:00`, SHA-256
  `D1E4F70DCE9DB0307C984BB3395A984DF3726072D518DD643EB8A2ADBE5B7645`.

Native FSR settings and desktop input repair — 2026-07-18:

- [x] Register a separate native FSR Web Remote settings module with localized
  labels and persisted auto-start, port, LAN, bind-host and Host-allowlist values.
- [x] Inject live status plus asynchronous Apply/start, Stop and Pair controls
  into the module without blocking the Minecraft client thread.
- [x] Keep loopback as the default and retain fail-closed exact LAN Host validation.
- [x] Forward focused-canvas printable desktop keys through the existing TEXT
  wire event while preserving physical KEY state and nonduplicating IME input.
- [x] Cover punctuation/keypad mapping and moved drag held-state in Java/Node tests.
- [x] Run targeted checks and the final clean addon build; record the replacement
  artifact metadata below.

Native settings/input verification:

- [x] Targeted: `testWireProtocol testCoControl verifyWeb compileClientJava` —
  exit `0`, 40 tasks; settings registration, GLFW mapping, drag held-state,
  desktop KEY+TEXT and all seven Web regressions passed.
- [x] Final clean: `clean check build` — exit `0`, `BUILD SUCCESSFUL`, 53 tasks
  in 6m03s. The two compiler warnings are pre-existing Jetty removal warnings.
- [x] Final addon artifact:
  `bbs-web-remote/build/libs/bbs-web-remote-0.1.0-all.jar` — 2,973,325 bytes,
  mtime `2026-07-18 10:46:28.218 +08:00`, SHA-256
  `4F87238B304B6801409314E6636FB3352D3BA8CCF48C5352EF89975BCC8B4F8B`.
  Jar inspection confirms both language files and the settings/client UI classes.

Connection telemetry, upstream codec study and FRP — 2026-07-18:

- [x] Expand the browser Connection details panel with sanitized URL/channel,
  split UI/media FPS and application bitrate, dimensions, cumulative bytes,
  decode/drop and reconnect state without changing wire `1.1`.
- [x] Add persisted exact `public_origin` validation for FRP custom ports and
  HTTPS/WSS; retain fail-closed LAN allowlist validation and ignore forwarded headers.
- [x] Set `Secure` on pairing cookies for an exact configured HTTPS public Origin.
- [x] Shallow-clone and pin Sunshine/Moonlight, RustDesk, Guacamole and noVNC;
  record applicable compression/feedback techniques and license boundaries in
  `research/remote-codec-upstream.md`.
- [x] Record FRP TCP, HTTP and HTTPS/WSS examples in `research/frp-websocket.md`.
- [x] Run targeted Web/server/protocol checks and the final clean addon build;
  record the replacement artifact below.

Connection telemetry/FRP verification:

- [x] Targeted: `testWireProtocol testServerSmoke testCoControl verifyWeb
  compileClientJava` — exit `0`, 42 tasks in 2m18s. Public-origin validation,
  HTTPS Secure cookie, settings registration, both WebSocket lifecycles and all
  seven Web regressions passed.
- [x] Final clean: `clean check build` — exit `0`, `BUILD SUCCESSFUL`, 53 tasks
  in 7m52s. The two compiler warnings are pre-existing Jetty removal warnings.
- [x] Final addon artifact:
  `bbs-web-remote/build/libs/bbs-web-remote-0.1.0-all.jar` — 2,976,746 bytes,
  mtime `2026-07-18 12:20:27.318 +08:00`, SHA-256
  `B8D363CF625F1DDB8BED12696C800CAB40A4177197B7F98625F6F8924290F29E`.
  Jar inspection confirms both language files, connection-detail Web resources,
  settings classes and public-origin validator classes.

Painted telemetry, delivery diagnostics and adaptive quality - 2026-07-18:

- [x] Add bounded browser receive/decode/apply/paint metrics without retaining
  JPEG or decoded image references.
- [x] Add viewer-safe `LINK_PING/PONG`, exact post-rAF `PAINT_ACK` and bounded
  server capture-to-paint/RTT statistics without changing binary wire `1.1`.
- [x] Add constant-space UI and media delivery counters, saturating media long
  counters, and expose optional `delivery.ui` plus server-wide
  `delivery.mediaAggregate` through `LINK_STATS` at no more than 1 Hz.
- [x] Add conservative auto-only quality adaptation with 3-second windows,
  two-level severe drop, two-window bad drop, 15-second cooldown and 30-second
  healthy recovery; suspend it for hidden/no-surface/disconnected/intentional
  reconnect states.
- [x] Repair future-clock-sample selection, session reset ordering, reset token
  buckets/counters and mixed-paint-time ACK batching found during security review.
- [x] Repair multi-surface adaptive accounting so one Canvas draw counts every
  painted surface token rather than one rAF callback; cover mixed-rate surfaces.
- [x] Verify nested delivery field parity across Java and Web, old-message
  compatibility, healthy 60 FPS under a 120 FPS request, multi-viewer aggregate
  labeling, and absence of network-loss/GPU-scanout claims.
- [x] Close the final review gap: count every painted Surface token in a single
  Canvas draw and feed per-handle sequence skips into adaptive drop pressure;
  normal same-epoch Surface restarts remain excluded.

Painted telemetry targeted verification:

- [x] `testWireProtocol testServerSmoke testCoControl verifyWeb
  compileClientJava` - exit `0`, `BUILD SUCCESSFUL`, 42 tasks in 53 seconds.
- [x] Final `clean check build` - exit `0`, `BUILD SUCCESSFUL`, 53 tasks in
  5m32s. Only the two pre-existing Jetty removal warnings were emitted.
- [x] Final addon artifact:
  `bbs-web-remote/build/libs/bbs-web-remote-0.1.0-all.jar` - 3,017,986 bytes,
  mtime `2026-07-18 16:56:40.901 +08:00`, SHA-256
  `C649D366119EC262E4ED4B8E6F6578D26FE48ADCCA30060B6A9ADA4FA4D53196`.
  Packaged Web resources contain the nested delivery parser, painted-token
  accounting and sequence-gap adaptive pressure; telemetry/statistics classes
  and both language resources are present.

## Phase I - Hybrid WebRTC/P2P media

- [x] Pin `dev.onvoid.webrtc:webrtc-java:0.14.0`, include Apache/BSD notices,
  package supported native resources explicitly, and fail safely to WS/JPEG
  when the OS/architecture/native loader is unavailable.
- [x] Add a bounded client-lifetime WebRTC coordinator with at most four peers,
  one source/track per active physical handle, latest-only JPEG-to-I420 bridge,
  deterministic disposal and no render-thread/native backpressure.
- [x] Extend authenticated `/ws/ui` JSON with bounded RTC offer/answer/trickle
  ICE/close/error/track-bind messages bound to participant, session epoch and
  RTC generation; keep browser/collaboration binary wire `1.1` unchanged.
- [x] Implement browser `HybridMediaTransport`: negotiate WebRTC in parallel
  with JPEG fallback, map track MID to physical handle, switch only after a
  drawable frame, and fall back once on failed/timeout/disconnected state.
- [x] Add unordered zero-retry DataChannel for pointer/presence/held snapshots
  only; retain reliable input transitions, text, wheel, lease and settings on WS.
- [x] Add `AUTO / RELAY_ONLY / WS_ONLY`, bounded STUN/TURN URLs and host-only
  TURN REST secret/TTL settings. Generate no-store short-lived coturn credentials
  only for authenticated sessions and never log secrets/SDP/candidates/IPs.
- [ ] Add diagnostics for signaling/ICE/path/codec/tracks/WebRTC stats with IP
  redaction, plus deterministic Java/Node tests for negotiation, limits,
  lifecycle, multi-track binding, fallback and permission isolation.
- [x] Run native Windows x64 factory/peer smoke, targeted protocol/server/Web
  gates and final clean build; inspect the packaged native resources and JAR.

Phase I implementation verification - 2026-07-18:

- [x] Hybrid transport is additive: Addon/API remains exactly `2.0`, browser
  binary wire remains `1.1`, and RTC signaling uses separate bounded JSON `v:1`.
- [x] Targeted matrix:
  `testWebRtcCore testWebRtcNativeSmoke testWebRtcConfiguration
  testWireProtocol testServerSmoke testCoControl verifyWeb compileClientJava`
  exited `0`. It covers native factory creation/disposal, four-peer/four-track
  bounds, candidate/SDP limits, TURN REST validation, 63 KiB UI signaling,
  viewer/controller isolation, independent DataChannel sequence admission,
  MID binding, first-drawable switch, 12-second watchdog and one-shot fallback.
- [x] Security/lifecycle reviews repaired pure-relay operation without TURN,
  repeated offers, pre-parse DataChannel limits, Viewer response amplification,
  cross-transport reliable sequence corruption, RTC peer leaks, missing
  `RTC_STOP`, negotiation timeout and retained-Surface ownership.
- [x] Final review hardening added a monotonic cross-transport state-snapshot
  watermark (without changing reliable event admission) and generation-fenced
  native `FAILED/CLOSED` peer cleanup, covering delayed held snapshots and
  half-open WSS sessions.
- [x] Final addon clean gate:
  `bbs-web-remote\gradlew.bat clean check build --no-daemon --console=plain`
  exited `0`, `BUILD SUCCESSFUL`, 57 tasks in 4m50s. The two compile warnings
  are the existing Jetty removal warnings.
- [x] Final Windows x64 addon artifact:
  `bbs-web-remote/build/libs/bbs-web-remote-0.1.0-all.jar` - 11,462,375 bytes,
  mtime `2026-07-18 20:52:25.035 +08:00`, SHA-256
  `CF7C830ECAEAB7979395DA1CD254E6F59256009942F0ABB2EEC1D65D634DAAB2`.
  Inspection confirms root `webrtc-java-windows-x86_64.dll`, nested
  `webrtc-java-0.14.0.jar`, Web assets, RTC/TURN classes and
  `META-INF/NOTICE-webrtc.txt`.
- [ ] Detailed periodic WebRTC `getStats()` path/codec/bitrate/loss telemetry is
  still pending. Current connection details expose sanitized RTC mode/state,
  track/binding counts and DataChannel state without IPs or candidates.

## Phase G — Browser co-control

- [x] Replace the single global controller identity with authenticated controller participants plus a server-owned gesture/text-focus lease.
- [x] Track held keys/buttons, pointer state, rate limits and release-all independently per participant.
- [x] Broadcast participant presence and colored cursors while preserving one native UI focus/drag owner.
- [x] Add deterministic lease timeout, controller replacement, screen/session-change and disconnect tests.

Validation gate:

- two browsers can watch concurrently and alternate click/drag/text operations without stuck input
- a second browser cannot interrupt an active drag/text focus until release or timeout
- disconnecting one participant does not release another participant's held state

## Phase H — NeoForge semantic collaboration

Core/server groundwork:

- [x] Use the explicit `JoinResponse.readyWithoutSnapshot` contract so a revision-0 late joiner never becomes ready before resync.
- [x] Track server-side resync-pending membership and reject mutation/checkpoint submission until snapshot application is acknowledged.
- [x] Keep snapshot SHA-256 semantics identical on server and client (hash the encoded wire body before bounded worker decompression).
- [x] Keep mutation/checkpoint ordering separate from repeatable 30 Hz presence watermarks.

- [x] Add common/server networking bootstrap while keeping HTTP/WebSocket and render APIs client-only.
- [x] Define versioned payloads for session join/leave, presence, semantic mutation, snapshot request/response, ACK/gap and resync.
- [x] Add server-owned membership, permission checks, monotonic sequence assignment, bounded queues and explicit disconnect cleanup.
- [x] Capture normalized Film/Replay value-path mutations, coalesce redundant ancestor/child updates, and suppress echo during remote apply.
- [x] Complete remote client-thread apply and targeted refresh across snapshot catch-up and Film-close lifecycle paths; reserve full Film rebuild for join/manual save/resync.
- [x] Complete the shared presence-codec/client integration around the implemented Together-informed 30 Hz presence and 100 ms semantic coalescing.
- [x] Keep local FSR CAS revision separate from the server collaboration revision and test the core provider-semantic sequence mapping.
- [x] Encode each local commit as one atomic mutation batch and use a revision-CAS GZIP checkpoint for oversized structural subtrees.
- [x] Add explicit create/join/leave/status commands and permission-level-2 defaults; never auto-join a Film.

Client bridge and final protocol verification:

- [x] Complete `CLIENT_READY`/`CLIENT_NOT_READY` pending kinds, missing helpers and all terminal-state cleanup so `compileClientJava` succeeds.
- [x] Start the first-chunk deadline from the RESYNC marker, bind snapshot ACKs to transfer id and snapshot base, and retain pending membership when no Film is open.
- [x] Apply snapshot N, then queued deltas N+1…current while local submission is disabled; only then acknowledge `CLIENT_READY(current)`.
- [x] Flush the final collaboration window in `onSessionClosing`, defer leave until the reliable tail is sent, and prove close/disconnect cannot drop a mutation or checkpoint.
- [x] Run protocol regressions for target-preserving snapshot retry, stale-snapshot delta catch-up, duplicate selections, `<=256` presence selections, validator restart and same-session membership switch.

Validation gate:

- two NeoForge clients join the same Film session and exchange replay/keyframe/property changes
- cursor/editor/replay selection presence is isolated to the matching semantic context
- remote apply produces no echo loop and sequence gaps recover through snapshot/resync
- server rejects unauthorized, oversized, stale-session and invalid-path payloads
- client disconnect immediately removes presence and releases session-owned state

## Risky files / conflict points

- `new/src/client/java/mchorse/bbs_mod/ui/framework/UIScreen.java`
- `new/src/client/java/mchorse/bbs_mod/ui/framework/UIRenderingContext.java`
- `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/Batcher2D.java`
- `new/src/client/java/mchorse/bbs_mod/api/client/BBSClientApi.java`

Before modifying each value/signature, search all usages. Preserve unrelated untracked migration files in `new/` exactly as found.
