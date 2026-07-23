# Unify Particle Editor Interactions

## Goal

Make particle editing behavior consistent and predictable across the BBS particle editor, in-world particle rendering, text input controls, and the curve editor.

## User Value

Particle authors should be able to trust that what they see in the particle editor matches what appears outside the editor, and that common editing controls behave like familiar desktop/web inputs.

## Confirmed Facts

- `UIScreen.keyPressed()` currently drops every `GLFW_REPEAT` event before it reaches UI controls, so focused textboxes cannot repeat navigation or deletion keys while held.
- `UITextbox` delegates focused key handling to `Textbox.keyPressed()`, and `Textbox` already treats arrow/backspace/delete as held actions via `context.isHeld(...)`; the global repeat filter prevents those paths from running repeatedly.
- `UIParticleSchemeRenderer` renders editor preview particles with the internal BBS `Camera` and calls `ParticleEmitter.setupCameraProperties(Camera)`.
- `ParticleFormRenderer` renders particles outside the particle editor with Minecraft's main camera and calls `ParticleEmitter.setupCameraProperties(net.minecraft.client.Camera)`.
- `ParticleComponentAppearanceBillboard.renderUI()` is a separate 2D UI thumbnail path and is not the same as the 3D particle editor preview.
- `UICurve` currently uses graph coordinates directly from node index/time/value and has no persistent pan/zoom state for the particle curve editor.
- `UICanvas` and `Scale` already provide local patterns for middle-mouse panning and wheel zooming.
- `UICurveEditor.remove()` removes a curve from `scheme.curves` but does not remove or reset the parser variable or rebuild the emitter, so expressions referencing the removed curve can keep stale values.
- Bezier curve Ctrl+left-click point creation lives in `UICurve.bezierMouseClicked(...)`.

## Requirements

- Parent task tracks four independently verifiable child tasks:
  - `05-31-curve-editor-fixes`: add curve viewport pan/zoom and fix curve removal state.
  - `05-31-ui-textbox-key-repeat`: allow long-press key repeat for focused textboxes.
  - `05-31-particle-camera-facing-parity`: make particle editor 3D preview and outside-editor camera-facing modes match.
  - `05-31-remove-bezier-ctrl-add-points`: remove Ctrl+left-click keyframe creation for Bezier curves.
- Keep work scoped to `new`; do not modify `old` or `bbs-premiere-export`.
- Preserve existing uncommitted local changes and do not revert user files.
- Existing Bezier chain compatibility fixes remain intact.

## Acceptance Criteria

- [ ] Holding Backspace/Delete/Left/Right inside a focused BBS textbox repeats after the platform repeat delay; global non-text shortcuts are not unintentionally spammed.
- [ ] Mouse wheel over the curve graph zooms the curve view around the cursor.
- [ ] Middle mouse drag over the curve graph pans the curve view.
- [ ] Curve point hit testing, dragging, and rendering respect the same pan/zoom transform.
- [ ] Removing a curve clears or neutralizes its parser variable state and refreshes the preview emitter so removed curve values no longer affect particle size or Bezier behavior.
- [ ] Re-adding a curve after removal restores normal curve behavior without requiring editor restart.
- [ ] Bezier curves no longer add points with Ctrl+left-click.
- [ ] Particle camera-facing modes render consistently between the particle editor preview and outside-editor particle forms, using the editor preview as the target visual.
- [ ] `git -C new diff --check` passes.
- [ ] `.\gradlew.bat build` passes from `new`.

## Out of Scope

- Reworking the whole particle editor layout.
- Changing particle JSON format beyond compatibility-preserving fixes.
- Editing `old` or `bbs-premiere-export`.
