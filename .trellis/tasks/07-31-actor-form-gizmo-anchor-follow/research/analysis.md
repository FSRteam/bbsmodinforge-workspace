# Analysis: Actor-mode film replays fail to place transform/pose gizmos at limb anchors

Date: 2026-07-31 (main-session analysis; sub-agent dispatch failed with a platform `reasoning_text` API error)

## Confirmed root cause (user repro: gizmo fixed on screen, does not follow the view)

- Film gizmos sample through the shared placement API: `UIReplaysEditorUtils.buildFilmGizmoDrag` / `sampleFilmBoneMatrix` -> `BaseFilmController.getBoneCompositeMatrix` -> `sampleBonePlacement` (workspace new/). Both the pose-bone gizmo and the transform gizmo derive from the editor entity (`UIFilmController.getCurrentEntity()` -> the StubEntity in `BaseFilmController.entities`).
- The editor entity is updated every tick by `applyReplay` regardless of the actor toggle (`BaseFilmController.updateEntities`), so the gizmo source data itself is present when `replay.actor` is on.
- The gizmo's on-screen placement is NOT sampled on demand: the visual pass captures it per frame into `Gizmo.INSTANCE.lastRenderMatrix` via `renderAxes` / `renderAnchorGizmo` -> `Gizmo.INSTANCE.captureVisual(stack)`, and `GizmoDrag.fromRenderedGizmo` -> `Gizmo.computeWorldOrigin` reads that captured matrix. The gizmo handles then draw at that placement.
- `BaseFilmController.renderEntity(IBbsWorldRenderContext, Replay, IEntity)` skipped actor replays entirely (`if (!replay.actor.get())`), because the world `ActorEntity` (rendered by `ActorEntityRenderer` -> `MorphRenderer.renderForm`) replaces the editor form. That skip also dropped the gizmo placement capture: `captureVisual` never ran for actor replays, so `lastRenderMatrix` went stale and the gizmo stayed fixed on screen instead of following the replayed form's anchor.
- The editor entity and the world actor both get `FormUtils.copy(replay.form)` and the same keyframe position, so capturing the editor entity's anchor is the correct placement source; the divergence was purely the skipped capture.

## Fix (uncommitted, pending user runtime confirmation)

- `FilmControllerContext` gained a `gizmoOnly` flag (reset in `setup`).
- `BaseFilmController.renderEntity(IBbsWorldRenderContext, Replay, IEntity)` now always builds the film context; when `replay.actor` is on it sets `gizmoOnly(true)` instead of skipping.
- `BaseFilmController.renderEntity(FilmControllerContext)` skips the visible editor form render (and its shadow, name tag, and axes-preview overlay) when `gizmoOnly`, but keeps `renderAxes` / `renderAnchorGizmo` -> `Gizmo.INSTANCE.captureVisual`, so transform/pose/anchor gizmo placement keeps following the anchor each frame.
- Behavior with `actor` off is unchanged (gizmoOnly stays false).
- Source-level regression check added to `PoseFormRegressionSourceTest` (`actorReplaysKeepCapturingGizmoPlacement`).

## Follow-up defect: gizmo stays fixed after disabling the replay

- User report: after turning the replay's `enabled` toggle off, the gizmo does not disappear and stays fixed on screen; re-enabling updates it again.
- Cause: the render pass `continue`s on disabled replays, so the gizmo placement capture (`Gizmo.INSTANCE.captureVisual`) stops updating and `lastRenderMatrix` goes stale, while `UIFilmController.canShowGizmo()` did not check `replay.enabled`.
- Fix: `canShowGizmo()` now also requires the current replay to be enabled, so a disabled replay hides the gizmo instead of leaving it on the last captured matrix. Source-level check added (`disabledReplayHidesGizmo`).
- File changed additionally: `src/client/java/mchorse/bbs_mod/ui/film/controller/UIFilmController.java`.

## Files changed (workspace new/, branch work/mcaxolotl-editor-defect-batch)

- `src/client/java/mchorse/bbs_mod/film/BaseFilmController.java`
- `src/client/java/mchorse/bbs_mod/film/FilmControllerContext.java`
- `src/client/java/mchorse/bbs_mod/ui/film/controller/UIFilmController.java`
- `src/uiInputMailboxTest/java/mchorse/bbs_mod/client/ui/mirror/PoseFormRegressionSourceTest.java`

## Verification so far

- `compileJava` / `compileClientJava` pass.
- `testReplayKeyframeConsistency`, `testUiInputMailbox` (incl. new checks), `testFilmEditorMigration`, `testIkPhysicsConsistency`, `testFilmCollaborationApi`, `testBinaryCompatibility`, `testMatrixUtils`, `testUiMotions`, `testParticleRuntimeConsistency` pass.
- Do not commit until the user builds and confirms in the film editor that, with `actor` enabled, transform and pose gizmos follow the replayed form's anchor; that a disabled replay hides the gizmo; and that `actor`-disabled placement does not regress.

---

# Note: MobForm additive blending leak vs ModelForm (incidental analysis + fix)

## MobForm vs ModelForm additive blending

- `MobFormRenderer.applyAdditiveBlend()` switches the blend function to (GL_SRC_ALPHA, GL_ONE) via the layer-preparation hooks when `form.additiveColor` is on (`7e201b70`). The glow materialises because the blend actually ADDS.
- `ModelFormRenderer` only runs `FormColorBlend.blend(..., additive)` = `BlendMode.BRIGHTEN` (base *= 1 + r*a*8). Byte vertex formats clamp at 255, so the brighten can never exceed white and no true additive glow appears. ModelForm renders through a custom VAO path (`renderModel` -> `RenderSystem.enableBlend()`), so it never received the MobForm-style blend-function switch.
- Parity decision: if ModelForm should glow like MobForm, it needs the same blend-func switch (SRC_ALPHA/ONE) when `additiveColor` is on, placed in its `renderModel`/`render3D` draw where it already calls `RenderSystem.enableBlend()`. That is a feature change, not a bug; left to the user.

## Pollution root cause (fixed)

- User report: morphing into an additive MobForm in third person, when the player's height exceeds nearby MobForm model blocks, the nearby blocks get additive-tinted too.
- Mechanism:
  1. `createWorldLayerPreparation` runs `RenderSystem.enableBlend()` + `applyAdditiveBlend()` on EVERY layer of the MobForm, including opaque (NO_TRANSPARENCY) layers.
  2. Opaque layers' `clearRenderState()` is a no-op for blend, so the additive (SRC_ALPHA, ONE) function leaks into the process-wide RenderSystem state after the additive form draws an opaque layer.
  3. A later non-additive MobForm's preparation calls `RenderSystem.enableBlend()` unconditionally, which re-enables blending with the still-leaked additive function -> the block draws additive.
  4. Height correlates with render order (taller player's layers precede nearby blocks in the frame), which is why the user sees the pollution mainly when the player is taller.
- Fix (MobFormRenderer):
  - `applyAdditiveBlend()` now resets to `RenderSystem.defaultBlendFunc()` for non-additive forms, so each form draws with its own blend rather than the previous form's leaked function.
  - The render `finally` (alongside `clearRunnables`) restores `RenderSystem.defaultBlendFunc()`, so an additive form cannot leak into any later form in the frame. Deferred translucent queue draws re-apply their own captured preparation, so they are unaffected.
- Source-level check added (`additiveBlendDoesNotLeak`).
