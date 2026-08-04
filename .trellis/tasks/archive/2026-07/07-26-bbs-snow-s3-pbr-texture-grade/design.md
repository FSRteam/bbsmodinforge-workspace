# S3 design: PBR and texture grading

## 1. Delivery boundary

```text
bbs-snow-addon/src/
├── main/java/mchorse/bbs_snow/
│   └── BBSSnowMod.java                         # register two common PBR factories
├── client/java/mchorse/bbs_snow/
│   ├── BBSSnowModClient.java                   # register two UI factories
│   ├── pbr/render/                              # texture modifier + scoped render state
│   ├── pbr/ui/                                  # two editors + S3-owned l10n keys
│   ├── client/screen/                           # model shader + full-screen engine/effect DTO
│   ├── mixin/client/                            # regular S3 Mixins
│   └── mixin/client/iris/                       # Iris-only wrapper Mixin
└── client/resources/assets/bbs_snow/
    ├── shaders/core/model_texture_grade.*
    └── strings/{en_us,zh_cn}.json
```

The S2 common PBR classes remain the sole data implementation. S3 does not fork their schema.

## 2. Registration sequence

```text
BBSSnowMod construction
    -> idempotently install pbr / bone_pbr in KeyframeFactories
    -> submit common BBS addon registration

BBSSnowModClient construction
    -> ensure common factories are installed
    -> register UIPBRKeyframeFactory / UIBonePBRKeyframeFactory
    -> keep S2 source-pack/l10n/client-started sequence
```

Registration should accept its own already-installed factory instance. If a different implementation
already owns the same global key, log and preserve it; silently overwriting another addon would make
load order alter saved replay decoding.

## 3. Render-state flow

```text
ModelFormRendererPBRMixin.render3D HEAD
    -> snapshot ModelForm material + bone override maps
    -> PBRTextureContext / BonePBRContext set

ModelInstanceTextureGradeMixin
    -> inspect ModelForm + posed ModelGroup tint/whiten
    -> choose target-compatible model_texture_grade shader or original shader

CubicVAORendererPBRMixin.renderGroup HEAD
    -> set current bone id
    -> apply group tint/whiten
    -> resolve per-bone texture override before material fallback

IrisTextureWrapperPBRMixin.getId RETURN
    -> merge current bone override, else material maximums
    -> return cached modified _s/_n texture id

renderGroup RETURN / render3D RETURN
    -> restore base grade, clear current bone and form contexts
```

All context holders are render-thread scoped and use `ThreadLocal`. Nested calls save and restore the
previous value instead of assuming a single flat render.

## 4. Transparent command compatibility

FSR can defer translucent draws beyond `render3D`. Two command Mixins capture a render snapshot at
constructor return:

- active model-level and group-level tint/whiten;
- material override map;
- bone override map and current bone id.

At `draw()` HEAD the command installs its snapshot and reapplies uniforms; at RETURN it restores the
previous state. The model shader includes `PassMode`, so normal FSR opaque/translucent splitting is
preserved. The same capture covers Iris wrapper lookups that happen during deferred texture binding.

The snapshot holds the current frame's maps by read-only reference. S2 computes them before render
and does not mutate them during queue flush; cloning nested maps per draw would add avoidable frame
allocations.

## 5. Shader contract

`ModelTextureGradeShader` owns one addon `ShaderInstance` and exposes:

- `setup()` / `close()` for BBS shader reload;
- `select(fallback, tint, whiten)` for model-level selection;
- `applyGroup(...)` and base-state restore for VAO groups;
- a small immutable grade snapshot for deferred commands;
- readiness/uniform checks used by runtime verification.

Resource lookup remaps only `/shaders/core/` requests to the `bbs_snow` namespace. The GLSL/JSON is
derived from the current FSR model shader, retaining `PassMode` and all existing lighting/fog behavior.
The only semantic additions are `TextureTint`, `TextureWhiten`, hue/saturation replacement and white
mixing.

PBR generated texture cache entries are GL-owned. `BBSShaders.setup` TAIL closes/reloads the addon
shader and deletes generated textures so resource reload does not retain stale ids.

## 6. PBR and replay UI

The two source UI factories are ported with Mojmap-neutral BBS UI APIs. `PBRUIKeys` owns the six bone
PBR labels under `bbs_snow.ui.bone_pbr.*`; S9's `SnowUIKeys` is not imported.

`PBRUIReplaysEditorMixin` adds material channels and the bone PBR sheet after each ModelForm flush.
`UIKeyframeSheetTextureGradeMixin` relabels/iconizes `texture_tint` and `texture_whiten`. All duck calls
use the S2 `bbs_snow$` interfaces.

## 7. Screen-effect boundary

S3 ports the GL engine but not the S8 clips. A `ColorEffect` DTO and a namespaced per-`ClipContext`
queue live beside `ScreenEffectRenderer`. The screen-only BBSRendering Mixin finds the same playback,
recording or Film preview context as the source and consumes that queue.

S8 later constructs effects through this S3 queue. It does not copy `ColorGradeRenderer` or the
render hook. Until S8 is installed the queue stays empty, so S3 changes no frame output by default.

`ColorGradeRenderer` must save and restore framebuffer binding, viewport, active texture, program,
VAO/VBO, depth/blend state as far as the source path changes them. Shader compilation failure disables
the pass and logs once rather than crashing every frame.

## 8. Mixin split and config

Regular client config receives the source-equivalent PBR Mixins plus split target adapters:

| Mixin | Responsibility |
|---|---|
| `BBSShadersTextureGradeMixin` | reload addon shader/cache |
| `BBSRenderingScreenEffectsMixin` | consume only S3 screen effects |
| `ModelInstanceTextureGradeMixin` | shader selection |
| `ModelFormRendererPBRMixin` | form PBR scope only |
| `CubicVAORendererPBRMixin` | group grade/bone/texture only |
| `PBRUIReplaysEditorMixin` | PBR replay tracks |
| `UIKeyframeSheetTextureGradeMixin` | tint/whiten labels |
| two translucent-command Mixins | capture/restore deferred state |

`IrisTextureWrapperPBRMixin` is the only entry in the Iris config and targets `getId()`. Both configs
retain `defaultRequire: 1`, no refmap and `JAVA_21`.

## 9. Verification and rollback

Static/build checks verify naming, JSON, jar contents and source isolation. Client verification forces
regular target classes to load, checks both factory pairs, shader uniforms/readiness, l10n keys and
render-state capture/restore. A tiny GL texture exercise validates channel modification/cache cleanup
without relying only on compilation.

No-Iris `runClient` must show the optional config skipped without error. If Iris is locally available,
run a positive `_s`/`_n` visual smoke; otherwise record it for S10.

Rollback groups:

1. registration + UI;
2. model shader + opaque render Mixins;
3. transparent/PBR context capture;
4. screen-effect engine/hook;
5. Iris config/Mixin.

Any target drift is fixed within the relevant group. Do not lower injection requirements or modify
`new/` to make the addon pass.
