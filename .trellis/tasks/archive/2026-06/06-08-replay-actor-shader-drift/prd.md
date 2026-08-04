# Fix non-actor replay drift with shaders

## Goal

Fix the replay rendering regression in `new`: when Iris/shaders are enabled, replay forms rendered without the actor option must not drift relative to the recorded replay path as the camera moves.

## User Value

Creators can preview or record ordinary non-actor replay forms with shaders enabled, without the replay position appearing camera-dependent.

## Confirmed Facts

- The active task is `06-08-replay-actor-shader-drift` and has moved from planning to implementation.
- The target codebase is `new`, not the repository wrapper root.
- The prior fix exists in commit `82714440 fix(film): keep disabled replay actors from drifting`.
- That prior fix is still present in current `BaseFilmController`: disabled replays are skipped, and `applyActorReplay()` applies only `ReplayKeyframes.GROUP_POSITION` to `ActorEntity` before applying client actions.
- Since `82714440`, the relevant committed changes include:
  - `258a814a fix(render): keep world form matrices camera-relative`
  - `ebe75984 fix(particles): match Blockbuster billboard facing`
- Current working tree contains many uncommitted render/matrix changes in `new`, including `BBSClientNeoEvents`, `BBSRendering`, form renderers, VAO renderers, and matrix utilities.
- Non-actor replay forms are rendered by `BaseFilmController.renderEntity()` through BBS world rendering, with explicit camera-relative matrices.
- Actor replay mode skips BBS form rendering and relies on Minecraft's entity rendering through `ActorEntityRenderer`; the user clarified this path is normal.
- The reported regression only appears with shaders enabled, without actor mode, and is tied to camera movement, which points to the BBS world render model-view path rather than replay keyframe data corruption.
- This is likely a NeoForge/Iris migration legacy issue: the migration preserved multiple rendering entry points, but did not make their render-space contracts explicit enough after camera-relative world rendering and shader model-view handling changed.

## Requirements

- Preserve the previous `82714440` behavior: disabled replays and actor-enabled replays must not keep applying full form/keyframe state in a way that causes drift.
- Fix non-actor replay rendering under shaders so the visible replay form is stable in world space while the camera moves.
- Keep non-shader rendering behavior unchanged.
- Keep actor replay behavior unchanged.
- Avoid broad rewrites of particle, VAO, form, or camera systems unless the final root cause proves the shared render contract is wrong.
- Preserve the BBS world render matrix contract: camera-relative replay stacks must be paired with the exact world model-view matrix supplied by the current render callback, not a stale/global RenderSystem matrix from another render scope.
- Do not discard or revert unrelated working-tree changes without explicit user approval.

## Acceptance Criteria

- [ ] With shaders enabled and actor mode disabled, the replay form stays at the recorded world position while orbiting/moving the camera.
- [ ] With shaders disabled, non-actor replay behavior remains unchanged.
- [ ] Disabled replay tracks do not move actor entities or editor replay entities.
- [ ] Actor replay mode remains normal.
- [ ] Actor form rendering still supports model forms, billboards/particles as applicable, name tags/shadows as before.
- [ ] The fix documents the NeoForge/Iris matrix-source rule that prevents this migration bug from returning.

## Root Cause Direction

The old fix addressed update/data drift, and the code still contains it. The new symptom is shader-only and camera-motion-dependent, so implementation should first investigate render-space drift:

- actor mode uses `ActorEntityRenderer` and Minecraft entity render matrices and is not the failing path;
- non-actor mode uses BBS camera-relative world render matrices and is the failing path;
- shader path uses `BBSRendering.onRenderChunkLayer()` / `renderCoolStuff()` and must pass the render callback's active world matrix into BBS rendering;
- current uncommitted matrix/VAO optimizations may have changed assumptions about whether a `PoseStack` already includes camera translation or whether RenderSystem model-view supplies it.

The preferred resolution is not an actor-path workaround. The implementation should correct the BBS world render matrix source used by the Iris chunk-layer callback.

## Out Of Scope

- Reworking replay recording format.
- Replacing the actor spawning/network flow.
- General particle facing fixes beyond interactions needed to keep actor forms stable.
- Broad performance refactors of form/model rendering.

## Open Question

No product-scope question is blocking planning. The remaining uncertainty is technical: whether the regression is in a committed change after `82714440` or in the current uncommitted render/matrix worktree. That should be answered during implementation with targeted comparison or instrumentation.
