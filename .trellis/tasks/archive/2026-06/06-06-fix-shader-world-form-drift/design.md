# Fix shader-dependent world form drifting after migration

## Technical Design

The bug likely sits at the boundary between Minecraft/NeoForge world render
matrices, BBS form rendering, and the Iris shader-specific render hook path.
The investigation will treat shader-disabled and shader-enabled rendering as
separate entry paths that must converge on the same coordinate-space contract.

## Boundaries

- Source edits are limited to `new/`.
- `old/` is read-only reference for Fabric 1.20.4 behavior.
- `docs/` is read-only evidence, including user-supplied shader material.
- `temp/` is the only location for any cloned external source if local code and
  official/docs research are insufficient.
- Git commits are blocked until the user confirms after validation.

## Rendering Contract To Preserve

- World-space forms/entities must be transformed exactly once by the active
  world view/model-view matrix.
- Shader-enabled and shader-disabled paths must not apply different effective
  camera transforms to BBS film/form render output.
- Buffer flushing must happen after the intended render batch is complete and
  must not mask an incorrect matrix space.
- Film replay targets and ordinary world forms must use the same coordinate
  convention unless a renderer explicitly documents a different one.

## Expected Investigation Areas

- `BBSRendering.renderCoolStuff(...)`, `onRenderChunkLayer(...)`, and related
  world render hooks.
- `MorphRenderer.renderForm(...)` and call sites that replaced
  `FormUtilsClient.render(...)`.
- `BaseFilmController.render(...)`, `renderEntity(...)`, and
  `FilmControllerContext`.
- `FormRenderingContext.camera(...)`, `ParticleEmitter.setupCameraProperties(...)`,
  and particle/billboard camera-facing code.
- NeoForge 1.21.1 render event/context behavior and Iris shader pipeline hooks.

## Compatibility Notes

The Fabric 1.20.4 `old/` implementation is the migration baseline, but the fix
must match NeoForge 1.21.1 APIs rather than reintroduce Fabric-only assumptions.

## Root Cause

NeoForge 1.21.1's `RenderLevelStageEvent.AFTER_ENTITIES` receives the vanilla
entity `PoseStack` separately from the render-level model-view matrix. Vanilla
entity rendering keeps entity coordinates camera-relative in the pose stack and
applies the view matrix through `RenderSystem` / shader uniforms.

The migration changed BBS world contexts to multiply the model-view matrix into
the `PoseStack` and then reset `RenderSystem`'s model-view matrix to identity
while rendering film/world overlays. A later helper applied the same bake/reset
pattern to vanilla entity form rendering. This created two incompatible
contracts:

- shader-disabled vanilla entity hooks can receive an already valid
  camera-relative pose stack, then have the view matrix baked into it again;
- shader-enabled Iris rendering already captures the gbuffer model-view matrix,
  so BBS film overlays that also bake the model-view into their pose stack can
  apply the effective view transform twice.

The fix should restore one contract for BBS world form rendering: keep
`PoseStack` camera-relative/local and apply the active model-view matrix through
`RenderSystem` / the shader pipeline exactly once.

## Rollback Shape

Keep edits small and grouped around a single coordinate-space contract. If a
candidate fix worsens either shader mode, revert only that candidate before
trying another path.
