# Implementation Plan

## Order

1. Curve editor fixes
   - Add a small viewport transform to `UICurve`.
   - Route all curve screen/world coordinate conversions through that transform.
   - Add middle-mouse pan and wheel zoom.
   - Fix curve removal to clear variable state and refresh emitter runtime bindings.
   - Remove Bezier Ctrl+left-click add behavior in the same file while preserving normal drag/edit.

2. Textbox key repeat
   - Allow `GLFW_REPEAT` through `UIScreen` only when the focused element is a textbox/text editor, or add an explicit repeat opt-in interface for focused controls.
   - Verify non-text hotkeys still behave as single press.

3. Particle camera-facing parity
   - Compare `ParticleEmitter.setupCameraProperties(Camera)` and `setupCameraProperties(net.minecraft.client.Camera)`.
   - Make both overloads produce equivalent yaw/pitch/position semantics.
   - Verify `lookat_xyz`, `lookat_y`, `rotate_xyz`, `rotate_y`, and direction modes against the editor preview target.

4. Full quality pass
   - Run `git -C new diff --check`.
   - Run `.\gradlew.bat build` from `new`.
   - Review changed files for accidental `old` / addon edits.

## Validation

- Manual editor checks:
  - textbox hold Backspace/Delete/Left/Right
  - curve wheel zoom and middle drag pan
  - curve delete then re-add
  - Bezier Ctrl+left-click no longer adds keyframes
  - representative camera-facing particle inside editor and outside editor
- Commands:
  - `git -c safe.directory=D:/bbsinneoforge/bbsmodinforge/bbsmodinforge/new -C new diff --check`
  - `.\gradlew.bat build`

## Risk Areas

- `UIScreen.keyPressed()` is global, so repeat routing must avoid changing shortcuts broadly.
- `UICurve` has separate paths for linear, Hermite, Bezier, and Bezier chain; transforms must cover all paths consistently.
- Particle camera parity may expose existing behavior differences in specific facing modes; test multiple modes before declaring done.
