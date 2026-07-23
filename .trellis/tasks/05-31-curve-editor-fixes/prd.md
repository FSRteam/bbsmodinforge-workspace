# Curve Editor Viewport And Deletion Fixes

## Goal

Make the particle curve graph behave like a normal editable graph view and prevent curve removal from leaving stale runtime state.

## Requirements

- Add mouse wheel zoom inside `UICurve`.
- Add middle-mouse drag panning inside `UICurve`.
- Ensure rendering, hit testing, adding, and dragging all use the same transformed curve-space coordinates.
- When removing a curve, clear or neutralize its parser variable state and refresh emitter bindings/runtime state.
- Keep existing linear, Catmull-Rom, Bezier, and Bezier chain editing behavior unless explicitly changed by the Bezier Ctrl+click child task.

## Acceptance Criteria

- [ ] Mouse wheel over the curve graph zooms around the cursor.
- [ ] Middle mouse drag pans the graph without moving curve points.
- [ ] Curve points remain clickable and draggable after pan/zoom.
- [ ] Ctrl+click add for Bezier chain still adds nodes in the intended curve-space position.
- [ ] Removing a curve no longer causes particle size to collapse due to stale curve values.
- [ ] Re-adding a curve after removal works without restarting the editor.
