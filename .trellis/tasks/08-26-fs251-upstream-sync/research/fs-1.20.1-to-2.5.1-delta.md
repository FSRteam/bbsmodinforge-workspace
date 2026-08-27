# FS 1.20.1 to 2.5.1 Delta and FSR Migration Recommendation

Research date: 2026-08-26.

## Baseline and source verification

- Local reference directory: `temp/bbs-fs-2.3`.
- The checkout itself is `codex/fsr-sync-20260729@5848363c`; it is a master/FS 2.4-era
  snapshot, not the current 1.20.1 branch tip.
- Local 1.20.1 reference: `origin/1.20.1@4cdee4ace033f12c450976bb8f9707f3cfa77092`.
- Remote 1.20.1 tip verified through Exa MCP/GitHub API: `165fa591c08acab522c3879a453caf0a2a6c02d6`.
- The remote tip merges master commit `73b8b3e4d1b3266eec801104924c80ab776267ef`, whose parent
  is `6fc9e37ee53e3c1fc837250e8eaedbcdd96f6f24` (`2.5.1` version bump).
- Comparing local 1.20.1 `4cdee4ac` with remote `165fa591` reports 15 commits and 19 changed files.
  Three are branch merge commits and one is the upstream version-only change. The functional delta
  is the 11 commits listed below; `8d99d77f` is the same zero-countdown recording fix already
  migrated into FSR during the previous sync.

## Functional delta

| Commit | Upstream change | FSR status in `new-fs25-sync` | Recommendation |
|---|---|---|---|
| `8d99d77f` | Start recording playback when countdown is zero; guard against toggling an already-running film. | Already present in `UIFilmController.startPlayback()` and `handleRecording`. | Skip; record as already migrated. |
| `ba3e65b8` | Restrict Ctrl+Shift+V preset bindings in `UIKeyframes` and `UIClips` with `inside()`, so transform paste keeps Shift-flip semantics. | Missing. Both FSR bindings are global to the widget subtree. | Migrate; low risk. |
| `7885dccb` | Rebind the main framebuffer before Fabulous! translucent flush; sort flat quads by distance to their plane rather than their centre. | FSR queue has custom captured shader/light state and extra pass modes, but no Fabulous! rebind or plane-distance sort. | Adapt manually; medium/high risk, requires shader and Fabulous! smoke tests. |
| `86bd302e` | Release the recording mark as soon as teleport lands, including during countdown; keep a separate timeout fallback. | `Recorder.update()` returns during countdown and only checks `reachedMark()` afterwards. | Migrate; fixes client/server recording timeline skew. |
| `19c5bac3` | Add keyframe-hover preview and form-translucency queue settings. | No `BBSSettings.keyframePreview` or queue toggle. `editorPreviewIconsAutoHide` is a different option. | Migrate both settings and gates. |
| `e3dd4861` | Keep missing-texture links in a `failed` set; never store the shared error texture as an owned map value; clear failure on delete/watchdog/create. | `TextureManager` stores `getError()` in `textures` and has no failed-link set. | Migrate; low runtime risk and prevents shared GL texture deletion/invalid bind spam. |
| `233093d9` | Change the new translucency queue default to disabled while preserving the opt-in mechanism. | Queue is always activated by `begin()`. | Migrate with `translucencyQueue=false` as the upstream-compatible default, but verify FSR shader/Iris scenes first because FSR has additional queue passes. |
| `a8fcfece` | Show keystroke visualization even while a text field is focused. | `InputRenderer.keyPressed()` is gated by `context.activeElement == null`. | Migrate; low risk. |
| `b2003053` | Add a numeric-input gesture setter that updates the focused textbox immediately for arrows, wheel and negate gestures. | No base gesture setter; arrow/wheel/negate paths call `setValueAndNotify()` directly. | Migrate; add focused-field regression coverage. |
| `9c438959` | Clear focused input when a left click is handled by an unrelated element or empty timeline area. | `UIBaseMenu.mouseClicked()` dispatches without the global clicked-away focus rule. | Migrate manually; medium risk because FSR has custom pointer dispatch and descendant handling. |
| `73b8b3e4` | Rename the slider drag helper so it does not override the new numeric-input gesture method. | FSR has `UISliderTrackpad.applyValue()` and no base method yet. | Migrate together with `b2003053`; preserve drag-only semantics under a distinct name. |

The `6fc9e37e` commit only changes upstream `mod_version` to `2.5.1` and must not be copied. FSR
must remain `mod_version=0.0.11` with artifact identity `BBS_FSR_beta_v0.0.11.jar`.

## Suggested migration batches

1. **Editor input batch (low risk):** `ba3e65b8`, `a8fcfece`, `b2003053`, `73b8b3e4`, and the
   `9c438959` focus rule. Run `compileClientJava` plus focused UI source tests.
2. **Settings/resource batch:** `19c5bac3`, `233093d9`, and `e3dd4861`. Add en/zh strings, retain
   existing FSR settings migration, and test missing-texture reload/delete behavior. Decide the
   queue default only after the visual smoke matrix; the toggle itself should always be retained.
3. **Recording batch:** `86bd302e`. Run the recording lifecycle tests and verify zero/short countdown,
   landed teleport, player movement during countdown, timeout, and server action alignment.
4. **Rendering batch:** `7885dccb`. Port the plane-normal sort and Fabulous! framebuffer bind around
   FSR's existing captured-state queue. Do not replace FSR's shader-color, lightmap/overlay, Iris
   layout, or multi-pass behavior with the Fabric implementation. Build and run Iris/Fabulous! smoke
   checks before changing the default.

## Items explicitly not to copy

- Upstream `gradle.properties` version bump (`2.5.1`).
- Fabric mappings, mixin descriptors, or wholesale upstream files.
- The three 1.20.1 merge commits as independent code changes.
- Any behavior already covered by the prior FSR sync, especially the zero-countdown
  `UIFilmController` fix.

## Migration gate and commit markers

No source migration was started during this research pass. When approved, implementation must use
the existing independent worktree `new-fs25-sync` (never `new/`). Before the first edit, record the
FS source start marker and FSR worktree initial commit in the task PRD. After the final build, record
the remote FS terminal marker (`165fa591` for the 1.20.1 branch, with master functional tip
`73b8b3e4`) and the FSR terminal commit. Each logical batch should be committed and built before
the next batch; the final command is `new-fs25-sync\gradlew.bat build --no-daemon --console=plain`.
