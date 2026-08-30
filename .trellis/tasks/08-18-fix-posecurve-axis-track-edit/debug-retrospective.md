# Bug Analysis: PoseCurve axis edit loses the live keyframe

## 1. Root Cause Category

- **Category**: E - Implicit Assumption
- **Specific Cause**: The curve editor assumed that a mounted property panel could keep
  using its original `Keyframe` and `Transform` objects. `submitKeyframes()` serializes
  the channel and calls `fromData()`, which creates replacement objects while the panel
  remains mounted. Axis lookup then misses the live keyframe, and later rotation deltas
  are calculated from the stale transform.
- **Secondary categories**: C - Change Propagation Failure, because the channel rebuild
  was not propagated to the panel and its child controls; D - Test Coverage Gap, because
  earlier logic tests did not model object replacement.

## 2. Why A Narrow Fix Would Fail

1. Fixing only per-axis selection lookup would let the graph find an index, but the
   property panel would still calculate the next delta from an obsolete `Transform`.
2. Rebinding only the panel's `Keyframe` would leave controls that cache `Transform`,
   pose fix, color, or lighting values out of sync.
3. Matching by tick alone would select the wrong keyframe when another entry shares the
   same tick; the keyframe id must participate in the fallback identity.

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | Rebind the mounted panel to the live keyframe and refresh all cached controls after channel rebuilds. | DONE |
| P0 | Test Coverage | Add a resolver regression with distinct old/live objects, changed values, and a same-tick decoy id. | DONE |
| P1 | Documentation | Record the channel-rebuild ownership rule in the frontend component guidelines. | DONE |
| P1 | Runtime | Keep strict Mixin loading and require the final runtime-verification marker. | DONE |

## 4. Systematic Expansion

- **Similar Issues**: Keyframe property factories, undo/redo callbacks, recording helpers,
  and any editor control that captures a mutable keyframe value in its constructor.
- **Design Improvement**: Treat the sheet selection index plus stable keyframe metadata as
  the editor's live reference; treat Java object identity as an optimization only.
- **Process Improvement**: Every regression involving `fromData()`, undo, or submit must
  include a rebuilt-object fixture and verify both the graph model and mounted controls.

## 5. Knowledge Capture

- [x] Updated `.trellis/spec/frontend/component-guidelines.md`.
- [x] Added a focused logic regression test.
- [x] Verified strict Mixin loading and the final runtime marker.
- [x] Recorded the remaining manual UI smoke-test boundary in the task handoff.

## Bug Analysis: Empty curve overlays crash during buffer submission

### 1. Root Cause Category

- **Category**: E - Implicit Assumption
- **Specific Cause**: PoseCurve assumed that every curve-overlay render pass emitted at
  least one vertex. Empty tracks, fully hidden pose components, or ordinary keyframes
  without automatic Bezier handles left the builder empty, while `buildOrThrow()` treated
  that valid UI state as an exception.
- **Secondary category**: D - Test Coverage Gap. Startup verification loaded the Mixin
  targets but did not exercise a zero-geometry render state.

### 2. Why The Two Reports Looked Unrelated

1. Opening a pose edit track crashed in `UIPoseTransformKeyframeGraph.renderGraph` when
   its component-point pass emitted no vertices.
2. Opening BBSVFX follow-curve progress keyframes crashed in the PoseCurve injection on
   `UIKeyframeGraph.render`, because two ordinary keyframes emitted no automatic handles.
3. VFX registered and reached READY before the crash; it exposed the second PoseCurve
   path but did not own the failing buffer submission.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | Route all three optional PoseCurve batches through one null-safe submission helper. | DONE |
| P0 | Runtime | Submit an empty buffer during client verification and require a no-draw result. | DONE |
| P1 | Verification | Inspect exported Mixin bytecode for the safe helper call in both graph targets. | DONE |
| P1 | Documentation | Record the optional GPU batch contract in frontend component guidelines. | DONE |

### 4. Systematic Expansion

- **Similar Issues**: selection outlines, optional handles, hidden-channel overlays, debug
  guides, and addon render passes whose loops can legally execute zero times.
- **Design Improvement**: centralize finalization so callers cannot accidentally choose a
  throwing submission mode for optional geometry.
- **Process Improvement**: crash reports from combined-addon environments must be assigned
  by the first project-owned frame, not by the UI feature that happened to trigger it.

### 5. Knowledge Capture

- [x] Updated `.trellis/spec/frontend/component-guidelines.md`.
- [x] Added a render-thread empty-buffer runtime probe.
- [x] Recorded both user crash reports and their distinct trigger paths.
