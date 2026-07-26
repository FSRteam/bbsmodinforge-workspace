# S3 source inventory and target drift

## Baselines

- Source: `bbs_FSloveCML_1.21.1/` current working snapshot (Fabric 1.20.1/Yarn).
- Target: `bbs-snow-addon/` at addon commit `1d73747`.
- BBS runtime: `new/` NeoForge 1.21.1 / BBS FSR 0.0.10.
- S2 archive: `.trellis/tasks/archive/2026-07/07-26-bbs-snow-s2-core-api/`.
- Source and `new/` both contain unrelated local changes; S3 treats them as read-only evidence.

## Source-owned S3 files

### Already delivered by S2

`PBRData`, `BonePBRData`, `PBRChannel`, `PBRKeyframeFactory`,
`BonePBRKeyframeFactory`, plus the common ModelForm/FormProperties/model/pose Mixins and duck APIs.

### Client PBR render (3)

- `pbr/render/PBRTextureModifier.java`
- `pbr/render/PBRTextureContext.java`
- `pbr/render/BonePBRContext.java`

### Client PBR UI (2)

- `pbr/ui/UIPBRKeyframeFactory.java`
- `pbr/ui/UIBonePBRKeyframeFactory.java`

The current source working snapshot localizes the six bone PBR labels through `SnowUIKeys`. The
target must keep that behavior but move the keys to an S3-owned PBR key holder.

### Client screen engine (3)

- `client/screen/ModelTextureGradeShader.java`
- `client/screen/ColorGradeRenderer.java`
- `client/screen/ScreenEffectRenderer.java`

`ColorGradeRenderer` also needs the small `ColorEffect` DTO currently under `clips/screen/`.
The clip implementations remain S8; S3 owns the DTO/queue consumed by the renderer so S8 does not
duplicate the GL engine.

### Direct S3 Mixins

- `BBSShadersTextureGradeMixin`
- `ModelInstanceTextureGradeMixin`
- `PBRUIReplaysEditorMixin`
- `UIKeyframeSheetTextureGradeMixin`
- `IrisTextureWrapperPBRMixin`

### Mixed source files that require splitting

| Source file | S3 portion | Deferred portion |
|---|---|---|
| `ModelFormRendererMixin` | `render3D` HEAD/RETURN PBR context | action overlays/additive/procedural logic to S4/S7 |
| `CubicVAORendererMixin` | current bone, group grade, bone texture resolver | no PBR-neutral logic is needed in S3 |
| `BBSRenderingMixin` | screen-effect context discovery/consumption | Hotbar state/UI to S8 |
| `UIReplaysEditorUtilsMixin` | only references the bone PBR UI type | whole file remains S7; S3 does not copy it |

FSR's new `FormTranslucentQueue.ModelVAOCommand` and `BOBJCommand` have no source-plugin
counterpart. S3 needs small compatibility Mixins so deferred commands carry the grade/PBR state
that was live when they were queued.

## Target signature audit

| Target | Current member | Port action |
|---|---|---|
| `BBSShaders` | static `setup()` | TAIL injection remains valid |
| `ModelInstance` | `render(PoseStack, Supplier<ShaderInstance>, Color, int, int, StencilMap, ShapeKeys, Function)` | change Yarn `ShaderProgram` to Mojmap `ShaderInstance`; keep arg-0 supplier modification |
| `ModelFormRenderer` | `render3D(FormRenderingContext)` and public `getForm()` via base renderer | split PBR HEAD/RETURN into its own Mixin |
| `CubicVAORenderer` | `renderGroup(BufferBuilder, PoseStack, ModelGroup, Model)` and one `Function.apply` texture resolve | map `MatrixStack` to `PoseStack`; preserve per-material resolver |
| `UIReplaysEditor` | private `flushForm(List, List, Form, Map, Map)` | source signature remains present |
| `UIKeyframeSheet` | seven-argument constructor ending in `boolean isBoneTrack` | source descriptor remains present |
| `IrisTextureWrapper` | public final `texture`; override `getId()` | change source injection from Yarn `getGlId` to `getId` |
| `BBSRendering` | static `onWorldRenderEnd()` | split only screen effects; map Minecraft/GUI/window names |

## Shader drift

The source `model_texture_grade` shader is based on the old 1.20.1 BBS model shader. It cannot be
copied byte-for-byte:

- target Java API uses `ShaderInstance`, `Uniform`, `DefaultVertexFormat.NEW_ENTITY`,
  `ResourceLocation` and `ResourceProvider`;
- target vertex shader no longer declares `IViewRotMat` and computes fog distance from `Position`;
- target fragment shader has `PassMode` and two alpha-discard branches used by
  `FormTranslucentQueue`;
- target model shader's overlay/lightmap/fog order must remain unchanged.

The safe port is the current target `assets/bbs/shaders/core/model.*` plus only the two source
uniforms and hue/whiten fragment operations.

## Deferred rendering risk

The source applies a shared shader's group uniforms immediately. FSR may queue translucent VAO or
BOBJ draws and execute them after `ModelFormRenderer.render3D` returns. Without capture:

1. the shared shader contains the last group's tint/whiten;
2. `PBRTextureContext` and `BonePBRContext` have already been cleared;
3. Iris wrapper texture lookup loses the material/bone override at deferred bind time.

S3 therefore captures a small render-state snapshot at command construction and reapplies it for
`draw()`, then clears/restores state. This is target compatibility, not a new feature.

## Registration gap carried from S2

Source `BBSFSloveCML.onInitialize()` puts `pbr` and `bone_pbr` into
`KeyframeFactories.FACTORIES`. Source client init looks them up and registers the two UI factories.
S2 migrated the factory classes but not these entry-point calls, so S3 must add both common and
client registration before PBR channels/UI can function.

## Iris isolation

The source listed `IrisTextureWrapperPBRMixin` in the ordinary client config. The NeoForge target
already declares `bbs_snow.client.iris.mixins.json` with `requiredMods=["iris"]`; the Mixin must be
moved under `mixin/client/iris/` and listed only there. A no-Iris run proves clean absence. A true
positive visual Iris run requires the external Iris/Connector runtime and can be deferred to S10 if
the dependency is not locally installed.
