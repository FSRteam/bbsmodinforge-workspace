# Technical Design

## Workspace Boundary

The implementation source is the clean snapshot at:

`bbs-vfx-neoforge-1.21.1-codex-ghproxy/BBSVFX-port-work`

The existing `new/` BBSFSR project is read-only input for API and build contracts. The
other BBSVFX work directories are out of scope. Source changes stay in the independent
BBSVFX project; Trellis artifacts stay in this task directory.

## Target Architecture

1. **NeoForge bootstrap**
   - Common `@Mod` constructor registers the BBS API 2.0 descriptor and the NeoForge-owned
     item/interaction registrations.
   - A client-only bootstrap is loaded behind `Dist.CLIENT` and owns client registration,
     shaders, render hooks and client mixins.
   - Java 21 and split main/client source sets are retained.

2. **BBS API 2.0 integration**
   - `BBSAddon` replaces the final `BBSAddonMod` implementation.
   - `BBSAddonDescriptor` uses `apiVersion("2.0")`, `API2_ONLY`, required mod `bbs`, and only
     capabilities actually used.
   - `context.forms()`, `context.settings()`, `context.clips()`, `context.resources()` and
     `context.events()` replace the registration-oriented BBS Mixins.
   - Registration results are checked and diagnostics are available through `BBSApi`.
   - Client render/lifecycle callbacks use `BBSClientApi`; APIs that do not expose a descriptor
     remain compatibility hooks and are isolated in the client bootstrap.

3. **Mojmap and NeoForge rendering**
   - First convert the Fabric snapshot's Yarn names with Loom `migrateMappings` in a temporary
     mapping workspace, then manually repair Mixin descriptors and 1.21.1 API changes.
   - Final source uses Mojmap names and NeoForge classes: `Minecraft`, `PoseStack`, `MultiBufferSource`,
     `RenderType`, `RenderTarget`, `ShaderInstance`, `DefaultVertexFormat`.
   - Shader registration uses `RegisterShadersEvent`; each of the 18 shader JSON/GLSL pairs gets
     a vertex-format and attribute-order audit.
   - `BbsVfxRenderCompat` becomes a fixed 1.21.1 direct adapter; Fabric Mapping Resolver and
     intermediary reflection are removed.

4. **Mixin layering**
   - Remove registration Mixins covered by API 2.0.
   - Keep BBS internal renderer/UI/keyframe Mixins in a dedicated client/common configuration,
     updating every target descriptor against BBSFSR source.
   - Keep Minecraft and optional-mod Mixins separate. Physics Mod integration is conditional and
     must not be required for the base addon to load.
   - Every required Mixin is validated by a real NeoForge client launch, not compilation alone.

5. **Native and resource behavior**
   - PhysX JNI remains a runtime dependency and is loaded only on supported environments.
   - BBSVFX source pack is registered first through the V2 resource facade so its overrides win.
   - No BBS core classes are bundled into the addon artifact.

## Data And Lifecycle Flow

`NeoForge constructor -> BBSApi.registerAddon -> BBS API 2.0 register callback ->
forms/settings/clips/resources/events registration -> client bootstrap -> shader registration ->
BBSClientApi render hooks -> Mixin-enhanced BBS renderers`

Registration windows are startup-only. Runtime callbacks may render or update state but must not
mutate structural registries after their freeze points.

## Compatibility And Rollback

- Each migration phase keeps the original gh-proxy snapshot untouched; rollback is a directory
  replacement or a clean copy, never a destructive reset of the shared repository.
- API 2.0 registration is preferred for final code. Legacy API may be temporarily retained only
  while a specific missing V2 extension is being ported, and must be removed before acceptance.
- When a V2 facade cannot express an existing behavior (form renderer, UI panel, keyframe factory),
  retain the narrowest Mixin/static extension and document the reason in the task.
- If a client launch fails due to Mixin target drift, isolate the offending config instead of
  lowering `require` or disabling the whole mixin set.
