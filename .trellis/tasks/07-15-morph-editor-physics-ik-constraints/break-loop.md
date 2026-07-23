# Bug Analysis: Model simulation ownership, hot-path allocation, and IK singularity regressions

## 1. Root Cause Category

- **Category B — Cross-Layer Contract**: render matrices, semantic simulation space, compiled topology, renderer scratch, and cross-frame solver history did not have explicit ownership boundaries.
- **Category C — Change Propagation Failure**: the Blender-style IK rewrite retained exact reach and single-frame rest fallbacks but dropped the earlier per-chain hysteresis history.
- **Category D — Test Coverage Gap**: checks covered compilation and isolated features, not multi-instance ownership, IK+constraints composition, warm allocation, exact reach, or straight-chain crossings.
- **Category E — Implicit Assumption**: code treated a shared model resource, the current camera/player transform, and repeatable topology work as if they were safe instance state.

## 2. Why Earlier Fixes Did Not Hold

1. Symptom-level matrix fixes did not define render space versus simulation space, so another render path could reintroduce camera/player motion.
2. A global mutable runtime cache isolated data by model id but not by renderer and simulation owner, so different placements still shared history.
3. Moving generic constraints without an explicit solver-order contract allowed a later pass to erase IK quaternions.
4. `REACH_LIMIT = 1` allowed exact extension mathematically, but the independent default `softness = 0.05` still prevented it in normal data.
5. The Blender IK refactor used rest/live bend information within one frame but removed the previous cross-frame normal, so singularity stability was lost even though ordinary poses looked correct.
6. Performance work focused on solver math while chain sorting, wanted sets, frame maps, frame objects, and scratch arrays were rebuilt around the solver every render.

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | Keep semantic `world` separate from render/view matrices | DONE |
| P0 | Architecture | Own mutable physics and IK history by renderer + simulation owner + model/chain | DONE |
| P0 | Architecture | Cache only immutable topology/config derivatives globally | DONE |
| P0 | Runtime design | Reuse frame maps, frame values, chain lists, target conversion maps, stiffness arrays, and math scratch after warm-up | DONE |
| P0 | Solver contract | FK/animation -> generic constraints -> IK -> physics | DONE |
| P0 | IK semantics | Omitted softness is 0; explicit softness remains compatible | DONE |
| P0 | IK stability | Predict target bend angle; enter rest recovery at 3 degrees and leave at 20 degrees | DONE |
| P1 | Verification | Add multi-instance, exact-reach, singularity-crossing, and equal-quality performance tests to the manual matrix | DONE |
| P1 | Documentation | Extend backend code-spec and cross-layer guide with topology/scratch/history ownership | DONE |

## 4. Systematic Expansion

- **Similar issues**: particles, trails, cloth, camera-facing effects, animation caches, editor thumbnails, and model-block previews can make the same observer-state or shared-resource mistake.
- **Design improvement**: every stateful renderer should explicitly separate immutable compiled data, owner-local scratch, and owner-local history.
- **Process improvement**: performance reviews must inspect the full render-to-solver data flow after warm-up, not only the numerical algorithm; quality comparisons must keep simulation settings identical.
- **Knowledge gap closed**: exact solver caps, default configuration values, serialized omission behavior, and UI defaults must be reviewed together.

## 5. Knowledge Capture

- [x] Updated `.trellis/spec/new/backend/quality-guidelines.md`.
- [x] Updated `.trellis/spec/guides/cross-layer-thinking-guide.md`.
- [x] Updated the task's `manual-test.md` with reach, hysteresis, ownership, and performance checks.
- [ ] Commit code and spec updates after the required Trellis file-scope confirmation.

## Follow-up Analysis: Straight line still crossing the authored joint angle

### 1. Root Cause Category

- **Category E — Implicit Assumption**: a stable bend-plane normal was assumed to imply a zero bend angle at the straight singularity.
- **Category D — Test Coverage Gap**: the previous numeric check proved exact maximum reach and normal continuity, but did not probe local joint angle/sign at 0°/2°/3°.

### 2. Why the Previous Fix Was Incomplete

1. Setting default softness to zero removed the permanent reach deficit, but targets just inside full reach still generated a very thin analytic triangle.
2. The 3°/20° state selected a stable side for that triangle; it did not force `sinA` to zero.
3. Near a zero-degree joint limit, that tiny triangle could reconstruct as a small angle on the forbidden/opposite side even when the center line looked straight.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Solver invariant | At predicted bend <= 3°, emit an exactly collinear two-bone solution | DONE |
| P0 | Single source | Own 3° straight restore and 20° side hysteresis constants in `IKSolver` | DONE |
| P0 | Stretch boundary | Apply stretch only when the target is outside the solved tip radius | DONE |
| P1 | Regression probe | Verify 0°/2°/3° are straight and 4° bends | DONE |
| P1 | Review checklist | Check bend magnitude separately from bend-plane direction | DONE |

### 4. Systematic Expansion

- Any degenerate geometric solver must define both orientation/normal continuity and scalar magnitude behavior at the singularity.
- A visually straight center line is insufficient evidence; local joint angle and constraint-side sign must be inspected.
- Dead zones intentionally trade a small positional error for a stable authored pose; downstream stretch or correction stages must not undo that contract.

## Follow-up Analysis: Position line is straight but rendered joint still over-rotates

### 1. Root Cause Category

- **Category B — Cross-Layer Contract**: the position solver checked limits using a direction-only reconstructed Euler, while the renderer consumed a different full swing+roll quaternion.
- **Category D — Test Coverage Gap**: the numeric probe asserted joint positions and predicted bend magnitude, but never asserted the final `orient` Euler written to the model.

### 2. Why the Collinear Fix Still Failed

1. Exact collinearity fixed the triangle singularity only.
2. `orientMirroredX` subsequently added a roll frame from the bend normal.
3. The earlier limit search never evaluated that final quaternion, so the bone line could remain straight while the rendered mesh crossed the zero/min limit.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Final-value invariant | Clamp the exact local quaternion assigned to cubic/BOBJ `orient` | DONE |
| P0 | Parent-frame consistency | Advance descendants with the clamped orientation | DONE |
| P1 | Regression check | Assert both solved positions and final rendered local Euler | MANUAL PENDING |
| P1 | Documentation | Record that position constraints do not imply render-orientation constraints | DONE |

### 4. Systematic Expansion

- Any solver that separates positional reconstruction from render orientation must validate constraints at the final representation boundary.
- Debug lines derived from joint positions cannot prove skinned/cubic mesh rotations are legal.

## Superseded Hypothesis: Model shader failed to produce global picking IDs

### 1. Root Cause Category

- **Status — Rejected after direct user observation and BBSFS comparison**: the `Target + localIndex` model contract matches BBSFS and was not the distinguishing migration defect.
- **Useful finding retained**: the previous check verified only event dispatch after `StencilFormFramebuffer` had resolved a bone and did not enumerate raw-ID consumers.

### 2. Why the Previous Fix Failed

1. The panel-first `pickBoneInList` change was downstream of raw-ID dispatch and therefore could not repair a gizmo collision.
2. Looking only at the six entries in `StencilMap.indexMap` hid the fact that `GizmoInteraction` reserves IDs through 19.
3. The speculative shader/VAO rewrite was reverted once the actual range collision was proven.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | Preserve the BBSFS `Target + localIndex` contract | DONE |
| P0 | Scope control | Revert the unproven shader/VAO rewrite | DONE |
| P1 | Integration verification | Test bound/unbound bones with debug off/on across all three model paths | MANUAL PENDING |
| P1 | Review checklist | Trace pixel ID → framebuffer decode → map lookup → active panel | DONE |

### 4. Systematic Expansion

- Debug overlays must be treated as appended pick surfaces, never evidence that the underlying model picker works.
- UI dispatch tests should start with a known decoded `(form, bone)` pair, while rendering integration tests must independently enumerate every raw-ID consumer before lookup.

## Correction: Expanded gizmo range collides with the first thirteen bones

### 1. Root Cause Category

- **Category C — Change Propagation Failure**: `Gizmo` expanded from six move handles to nineteen move/scale/rotate/sphere/view/screen handles, but `StencilMap.setup()` still started forms at the legacy constant 7 and seeded only IDs 1–6.
- **Category D — Test Coverage Gap**: prior analysis traced model ID construction but never enumerated every consumer of the decoded integer. `GizmoInteraction` consumes all IDs through `STENCIL_MAX` before form selection runs.

### 2. Why the Previous Fix Was Still Incomplete

1. Making model IDs globally explicit improved the GPU contract but preserved the wrong base value, so bones 7–19 became even more deterministically mapped to gizmo handles.
2. The panel-dispatch fix was downstream and could not run because `UIPickableFormRenderer.subMouseClicked()` gives gizmo handles first priority.
3. The earlier review treated the six entries seeded in `StencilMap.indexMap` as the full gizmo range instead of checking `Gizmo.Handle.values()` and `Gizmo.STENCIL_MAX`.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Single source | Start form IDs at `Gizmo.STENCIL_MAX + 1` | DONE |
| P0 | Change propagation | Seed every `Gizmo.Handle` instead of six hand-written entries | DONE |
| P1 | Review | Enumerate all decoded-ID consumers and their priority order | DONE |
| P1 | Integration test | Click at least the first 20 bones and every gizmo operation | MANUAL PENDING |

### 4. Systematic Expansion

- Reserved ID ranges must derive from the owner’s maximum constant, never a copied numeric boundary.
- A correct map lookup is insufficient when another consumer handles the raw ID first.
- When an enum expands, search for manual initialization tables, range checks, offsets, framebuffer decoders and priority dispatchers.
