# Design: Replay Actor Shader Drift

## Correction

The user clarified during implementation that the failing path is actor mode
disabled; actor mode is normal. Treat the older actor-mode hypotheses below as
superseded. The active fix targets BBS world replay rendering under Iris:
`WorldRendererMixin.renderSectionLayer(...)` must pass the callback's matrix
argument into `BBSRendering.onRenderChunkLayer(...)` instead of reading
`RenderSystem.getModelViewMatrix()` from a potentially different render scope.

## Architecture And Boundaries

The fix should stay inside the `new` client rendering/film boundary unless evidence shows the server actor spawn/update path is wrong.

Relevant paths:

- `src/client/java/mchorse/bbs_mod/film/BaseFilmController.java`
  - Owns editor replay entities, actor update synchronization, and non-actor replay rendering.
- `src/client/java/mchorse/bbs_mod/client/renderer/entity/ActorEntityRenderer.java`
  - Renders real `ActorEntity` forms when replay actor mode is enabled.
- `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`
  - Owns shader-specific BBS world rendering entry points and RenderSystem model-view/projection setup.
- `src/client/java/mchorse/bbs_mod/client/BBSClientNeoEvents.java`
  - Converts NeoForge render events into BBS world render contexts.
- `src/client/java/mchorse/bbs_mod/forms/renderers/*`
  - Form renderers assume a specific relationship between `FormRenderingContext.stack`, `context.camera`, and RenderSystem model-view.
- `src/client/java/mchorse/bbs_mod/cubic/render/vao/*`
  - Model VAO rendering derives shader uniforms from RenderSystem model-view multiplied by the form stack.

## Current Data Flow

Non-actor replay preview:

1. `BaseFilmController.render()` iterates enabled replay editor entities.
2. `renderEntity(context, replay, entity)` skips only when `replay.actor.get()` is true.
3. `BaseFilmController.renderEntity(FilmControllerContext)` computes a camera-relative matrix using entity replay position minus camera position.
4. BBS form renderers draw through `FormUtilsClient.render()`.
5. With shaders, BBS world rendering is driven by `BBSRendering.onRenderChunkLayer()` and `renderCoolStuff()`.

Actor replay mode:

1. Actor IDs arrive through the film actor map.
2. `BaseFilmController.update()` applies non-position replay state to the real `ActorEntity`; server/entity interpolation owns actor world position.
3. `BaseFilmController.startRenderFrame()` applies form properties to the real actor form.
4. `BaseFilmController.renderEntity()` does not render the editor entity because `replay.actor.get()` is true.
5. Minecraft's entity renderer invokes `ActorEntityRenderer.render()`, which renders the actor form with the entity render `PoseStack`.

## Key Constraint

There are two valid render-space contracts:

- BBS world renderers use camera-relative PoseStacks and keep the view matrix in RenderSystem.
- Minecraft entity renderers pass an entity-local PoseStack that has already been positioned by the entity dispatcher relative to the camera.

The fix must keep the BBS world replay contract under Iris: replay form matrices are camera-relative, and `RenderSystem` must be loaded with the exact world model-view matrix from the current render callback.

This should be treated as a migration normalization issue. The NeoForge/Iris migration introduced or exposed several render entry points that all reuse form renderers, but the code still relies on implicit context such as `BBSRendering.isRenderingWorld()`, `context.modelRenderer`, `context.ui`, or the current RenderSystem model-view. That is fragile because shader-sensitive code needs to know exactly whether the current stack is:

- `BBS_WORLD_CAMERA_RELATIVE`: BBS world render context, stack positions are already camera-relative and RenderSystem model-view supplies view/projection state.
- `MC_ENTITY`: Minecraft entity renderer context, stack is entity-local/entity-dispatcher-relative and must not be treated as a BBS world stack.
- `UI_OR_PREVIEW`: UI, model block, inventory, or editor preview context where camera/view data is synthetic or already baked for preview.

The critical contract for this bug is the matrix source at the render callback boundary. Shared renderers should not infer stack semantics from global render phase, but this task should not introduce broad form-context migration when the failing path is the chunk-layer matrix source.

## Leading Hypotheses

1. `WorldRendererMixin.renderSectionLayer(...)` receives the active layer matrix but current code reads `RenderSystem.getModelViewMatrix()` instead; under Iris that global matrix can be stale or from another render scope.
2. BBS non-actor replay matrices are camera-relative, so pairing them with the wrong model-view matrix makes the form appear to drift as the camera moves.
3. Current uncommitted matrix optimization may still expose related issues in particle/trail rendering, but the first fix should be the callback matrix source.

## Normalization Plan

Preferred implementation shape:

1. Leave actor rendering unchanged.
2. In `WorldRendererMixin`, pass `frustumMatrix` from `renderSectionLayer(...)` into `BBSRendering.onRenderChunkLayer(...)`.
3. Add a local comment at that call site explaining why `RenderSystem.getModelViewMatrix()` is easy to misuse there.
4. Keep `BBSRendering.renderCoolStuff()` responsible for loading that matrix into `RenderSystem` while BBS world replay forms render.

The fix should be narrow; future render-space normalization can be planned separately if another shared renderer proves ambiguous.

## Non-Goals

- Do not solve by disabling shaders or bypassing Iris.
- Do not make actor mode fall back to editor/non-actor rendering unless it is only a temporary diagnostic.
- Do not revert broad render commits before a precise cause is identified.

## Compatibility Notes

- Any fix must preserve Iris and non-Iris behavior.
- Form renderers can be used in UI, model block previews, BBS world rendering, player morph rendering, and actor entity rendering; a global condition such as `isRenderingWorld()` can be too coarse.
- If a new render-space marker is needed, it should live in `FormRenderingContext` or the BBS world render context rather than being inferred from the current screen or global render phase.
- Treat `BBSRendering.isRenderingWorld()` as a coarse lifecycle flag only. It should not decide whether a specific `FormRenderingContext.stack` is camera-relative.

## Rollback Considerations

Small, localized fixes are preferred:

- first try correcting `ActorEntityRenderer` context/matrix setup;
- then try adding an explicit form render-space flag;
- only then touch shared VAO/model matrix math.

If a candidate fix alters shared form rendering, verify non-actor replay, morph rendering, model block rendering, and particle forms before accepting it.
