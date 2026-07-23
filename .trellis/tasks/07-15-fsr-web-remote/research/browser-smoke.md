# Browser smoke verification — 2026-07-15

## Result

Status: **partial; the offline desktop/mobile browser fixture passed, while real Minecraft round-trip acceptance remains open**.

No production source file was changed. The sub-agent could not obtain a browser
backend, but the main session subsequently attached the Codex in-app browser to the
same loopback demo and completed the desktop/mobile checks recorded below.

## Main-session visual and interaction follow-up

The production `?demo=1` bundle was exercised in the in-app browser:

- Desktop 1280 x 720: Canvas filled the 1280 x 662 stage; wire `1.1` commands,
  English/CJK/emoji text, the animated upright JPEG surface, painter-ordered
  timeline and toolbar rendered correctly.
- Phone landscape 844 x 390: the canvas occupied the full 844 x 340 content area;
  L/R/M, Shift/Ctrl/Alt and keyboard controls were visible within the viewport.
- Zoom-in changed the authoritative toolbar status from 100% to 120%; `适应`
  restored 100%.
- The `键盘` action focused the hidden `向 BBS 输入文字` textarea. Typing
  `中文🙂` was consumed and cleared by the production demo input path.
- Tapping virtual Shift changed `aria-pressed` to `true`.
- No console/page error was observed during these checks.

This proves the browser decoder/renderer and responsive control shell, not the
Minecraft WebSocket/input destination or real Film/Replay capture alignment.

## Demo and automated evidence

The existing web bundle was served read-only from:

```text
bbs-web-remote/src/client/resources/assets/bbswebremote/web
http://127.0.0.1:18765/?demo=1
```

The built-in `?demo=1` path is preferable to a synthetic page: it enters
`DemoFixture`, feeds versioned binary envelopes through the production
`FrameAssembler`, decodes resources/surfaces through the production caches, and
replays the resulting command blocks through `CanvasRenderer`.

HTTP checks while the temporary server was running:

| Resource | Status | Content type | Bytes |
| --- | ---: | --- | ---: |
| `/?demo=1` | 200 | `text/html` | 9,880 |
| `/styles.css` | 200 | `text/css` | 18,096 |
| `/protocol.js?wire=1.1` | 200 | `application/javascript` | 45,065 |
| `/app.js?wire=1.1` | 200 | `application/javascript` | 113,967 |

Executed checks:

```text
node --check src/client/resources/assets/bbswebremote/web/protocol.js  PASS
node --check src/client/resources/assets/bbswebremote/web/app.js       PASS
node src/webTest/browser-protocol.test.js                              PASS
node src/webTest/glyph-protocol.test.js                                PASS
```

The protocol test confirms surface-frame validation, pointer-button encoding,
Unicode text encoding, missing-chunk recovery, and chunk-redefinition rejection.
The glyph test confirms wire `1.1`, authoritative glyph bounds, shadow state,
English, Chinese, and emoji UTF-8 round trips, the exact 4,096-byte boundary, and
malformed UTF-8 rejection.

## What the existing fixture exercises

These are source- and automated-test-backed facts, not substitutes for visual
browser verification:

| Area | Existing fixture/code path |
| --- | --- |
| Canvas replay | A 960 × 540 keyframe is assembled from stable and animated chunks containing painter-ordered quads, clip push/pop, glyph runs, texture quad, surface quad, and cursor commands. |
| Readable text | The fixture carries `BBS Web Remote`, `影片与场景`, `Camera 🎬`, and `时间轴 · Timeline ✨` as wire `1.1` UTF-8 glyph runs with authoritative bounding boxes. The renderer scales the system-font fallback to the authoritative width and height. |
| Texture | A generated PNG resource is content-hashed, registered through `RESOURCE_DEF`, decoded by `ResourceCache`, and composed as texture handle 9. |
| Surface | Surface handle 7 cycles four bottom-up 640 × 360 JPEG frames every 250 ms. `SurfaceCache` asynchronously decodes and normalizes the OpenGL-style vertical orientation before painter-order composition into a 718 × 288 command rectangle. Missing resources/surfaces have an explicit striped placeholder. |
| Zoom/pan | Canvas fit scaling is independent from user zoom. Toolbar zoom is clamped to 0.5–5×, reset returns to fit, and two-touch input selects pinch zoom, pan while zoomed, or a wheel gesture at fit scale. Pointer mapping uses the inverse renderer transform. |
| Input | Mouse click/drag, wheel, physical keyboard, virtual modifiers/buttons, IME composition/text, focus-loss release, and held-state snapshots use the production encoders. Demo mode still encodes the envelopes and accounts their bytes, but intentionally does not send them to Minecraft. |
| Mobile layout | Coarse-pointer or ≤900 px CSS enables 44 px virtual buttons and safe-area-aware bottom controls. The portrait-only notice recommends landscape. This declaration was inspected, but computed layout was not browser-verified. |
| Diagnostics | The live panel exposes wire version, FPS, traffic, buffered bytes, logical size, chunks/misses, command/unsupported counts, resources, surfaces, media source, participants, lease, DPR, and zoom. |

## Browser matrix

| Scenario | Status | Evidence / remaining check |
| --- | --- | --- |
| Desktop viewport (1280 × 720 verified) | **Passed for offline fixture** | Canvas/text/JPEG surface and toolbar zoom/reset were visually exercised in the in-app browser. Native Minecraft input remains separate. |
| Phone landscape viewport (844 × 390) | **Passed for offline fixture** | Full-width canvas, virtual buttons/modifiers, zoom/reset and hidden Unicode input were exercised. Multi-touch pinch and non-zero safe-area insets remain device checks. |
| Real FSR/BBS screen | **Not exercised here** | The offline fixture proves the browser pipeline only. A running Minecraft client is required to prove the actual UI frame producer and WebSocket transport. |
| Film preview / world Replay | **Not exercised here** | The fixture proves JPEG surface decoding, flip, animation, placeholder, and ordered composition. It is not evidence that real Film/Replay capture bounds and frames are correct. |
| Native input round trip | **Not exercised here** | Demo mode encodes and counts input but has no Minecraft destination. Actual click, timeline drag, list scroll, and text mutation must be observed in native BBS. |

## Reproduction steps once a Browser backend is available

### Desktop offline fixture

1. Serve the web directory on loopback and open
   `http://127.0.0.1:18765/?demo=1` at 1440 × 900.
2. Confirm the status reads `离线演示`, the connection card collapses after the
   first frame, and all four English/Chinese/emoji labels above are readable.
3. Confirm the surface changes roughly four times per second, is upright, and
   replaces the striped `SURFACE 7` placeholder after asynchronous decode.
4. Open diagnostics. Require wire `1.1`, logical size `960 × 540`, non-zero
   FPS/chunk/command/resource/surface counts, media `fixture`, zero missing
   chunks, and zero unsupported/clip errors.
5. Click zoom in/out and reset. Require the label/diagnostic zoom to change and
   reset to 100%, with pointer coordinates still targeting the same logical
   content after each transform.
6. Click, drag, and wheel over the canvas. In demo mode the upstream byte count
   must increase and no error toast may appear.
7. Press `键盘`, type `中文 🎬`, exercise composition and Backspace, then blur
   the page. Require the IME field to open, upstream bytes to increase, and all
   held states to be released without an error.

### Phone landscape fixture

1. Repeat at 844 × 390 with coarse pointer/touch emulation (and, if available,
   non-zero safe-area insets).
2. Require the canvas to remain usable without horizontal page overflow and the
   L/R/M, Shift/Ctrl/Alt, and keyboard controls to remain visible and tappable.
3. Verify one-finger tap/drag emits the selected mouse button.
4. Verify two-finger spread/pinch changes zoom, then two-finger movement pans
   while zoomed. At fit scale it must produce a scroll gesture instead.
5. Toggle R, Shift, Ctrl, and Alt and confirm `aria-pressed` changes correctly;
   open the mobile keyboard and enter Chinese plus emoji.

## Real-client acceptance still required

With Minecraft/FSR and the addon running, repeat the desktop and phone steps
against the addon's authenticated URL, then verify:

- an actual BBS `UIScreen` opens/closes/switches session correctly;
- Film preview and world Replay pixels align with their command bounds while
  native controls continue to operate;
- click, timeline drag, list scroll, wheel, key, modifier, and committed IME text
  mutate the native BBS UI on the Minecraft client thread;
- two authorized browsers alternate gesture/text ownership without stuck held
  state, including timeout, disconnect, screen change, and world unload;
- diagnostics remain bounded under slow media/UI delivery and report no missing
  chunks or unsupported commands for the chosen acceptance screen.

Until those browser and real-client checks are performed, the manual browser
acceptance boxes in `implement.md` and `prd.md` should remain open.
