# FSR 迁移兼容性核对（第一轮：mixin 目标类存在性）

调查日期：2026-08-13
调查者：主控（研究子代理遇速率限制，本轮由主控直接检索完成）

对照对象：
- 插件模组 `D:\编译任务\bbs_FSloveCML`（Fabric 1.20.1 / Yarn / BBS FS 2.4）
- FSR 1.20.1：`D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\.worktrees\fsr-neoforge-1.20.1\new`（NeoForge 47.1.106 / Java 17 / Mojmap / v0.0.11）
- FSR 1.21.1：`D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new`（NeoForge 21.1.222 / Java 21 / Mojmap / v0.0.11）

## 结论摘要

核对了 73 个 BBS 内部目标类（覆盖全部 86 个 mixin 的注入目标，多个 mixin 共享目标类）：

| 分类 | 数量 | 说明 |
|---|---|---|
| A. 两版都存在且**包路径完全一致** | 72 | 直接沿用 `@Mixin(target)` 的 import，无需改动 |
| B. 存在但**类名变化** | 1 | `UIModelPoseEditor` → `UIPoseFormEditor` |
| C. 缺失 | 0 | —— |

**这是迁移可行性的关键结论**：BBS 内部类树在 Fabric 版与两个 FSR 版之间几乎零漂移，
mixin 的"目标定位"层面成本极低；迁移成本集中在**方法签名中的 MC 类型**（Yarn→Mojmap）
与 MC 版本差异（1.20.1→1.21.1），而非 BBS 自身结构。

## A 类：包路径一致（两版同）

main 侧（`src/main/java/mchorse/bbs_mod/**`）：

| 类 | 包 |
|---|---|
| ActionConfig / ActionPlayback / Animator / ProceduralAnimator | `cubic.animation` |
| CubicModelAnimator | `cubic` |
| Model / ModelGroup | `cubic.data.model` |
| GeoAnimationParser | `cubic.geo` |
| ModelForm / Form | `forms.forms` |
| FormProperties / Replay | `film.replays` |
| KeyframeSegment | `utils.keyframes` |
| ActionsConfigKeyframeFactory | `utils.keyframes.factories` |
| Transform / PoseTransform | `utils.pose` |
| DataManager | `utils.presets` |
| MathBuilder | `math` |
| BBSSettings | （根包 `mchorse.bbs_mod`） |

client 侧（`src/client/java/mchorse/bbs_mod/**`）：

| 类 | 包 |
|---|---|
| BBSRendering / BBSShaders | `client` |
| ModelInstance | `cubic` |
| CubicAxisRenderer / CubicCubeRenderer / CubicMatrixRenderer / CubicVAORenderer | `cubic.render` |
| BOBJModelAnimator | `cubic.model.bobj` |
| BaseFilmController / WorldFilmController / Films | `film` |
| ModelFormRenderer | `forms.renderers` |
| ExtraFormSection | `forms.sections` |
| Particle / ParticleEmitter | `particles.emitter` |
| ParticleComponentAppearanceBillboard | `particles.components.appearance` |
| ParticleComponentMotionCollision | `particles.components.motion` |
| IrisTextureWrapper | `utils.iris` |
| MatrixStackUtils | `utils` |
| UIScreen / UIBaseMenu | `ui.framework` |
| UISection | `ui.framework.elements` |
| UIDockLayout | `ui.framework.elements.layout` |
| UIOverlayPanel / UISoundOverlayPanel | `ui.framework.elements.overlay` |
| UITransform / UIPropTransform | `ui.framework.elements.input` |
| UIVanillaSoundList | `ui.framework.elements.input.list` |
| UIKeyframes / UIKeyframeSheet / UIKeyframeEditor | `ui.framework.elements.input.keyframes` |
| UIKeyframeDopeSheet | `ui.framework.elements.input.keyframes.graphs` |
| UIActionsConfigKeyframeFactory / UIPoseKeyframeFactory / UIPoseTransformKeyframeFactory | `ui.framework.elements.input.keyframes.factories` |
| UIFilmPanel / UIFilmPreview / UIClips / UIClipsPanel | `ui.film` |
| UIFilmController / OrbitFilmCameraController | `ui.film.controller` |
| UIReplaysEditor / UIReplaysEditorUtils | `ui.film.replays` |
| UIFormEditor | `ui.forms.editors` |
| UIActionsFormPanel | `ui.forms.editors.panels` |
| UIAnimationStateEditor | `ui.forms.editors.states.keyframes` |
| UIPoseEditor / UIActionsConfigEditor | `ui.utils.pose` |
| UIDataContextMenu | `ui.utils.presets` |
| UIDataPathList | `ui.dashboard.list` |
| UIDashboardPanel | `ui.dashboard.panels` |
| UIParticleSchemePanel | `ui.particles` |
| UIParticleSchemeGeneralSection | `ui.particles.sections` |

`MatrixStackUtils` 类名在 FSR 中**保留未改**（Mojmap 下参数类型是 `PoseStack`，
但工具类本身没有跟着改名），这一点对 pivot 域的迁移是好消息。

## B 类：类名变化（1 项）

| Fabric（BBS 2.4） | FSR（两版一致） | 影响的 mixin |
|---|---|---|
| `mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor` | `mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIPoseFormEditor` | `UIModelPoseEditorAccessor`（accessor）、`UIPoseEditorMixin`（`instanceof` 判定 + 取 `valuePose`） |

包路径不变，且 FSR 侧 `UIPoseFormEditor extends UIPoseEditor`、仍持有
`private ValuePose valuePose`（见 `.../widgets/UIPoseFormEditor.java:15,17`），
accessor 模式可原样沿用，仅需换目标类名与 import。

## 第三方目标（@Pseudo）

`compat/TogetherClientNetworkReplaySelectionMixin` 注入 `bbs_together` 的
`TogetherClientNetwork`。FSR 1.20.1 worktree 下存在独立的 `bbs-together-addon/`
（含自己的 build.gradle / settings.gradle / src），说明 Together 在 FSR 侧是
独立 addon 工程——迁移该 compat mixin 前需要单独核对它的类名与包路径，
不能假设与 Fabric 版 bbs_together 一致。

## 待办（下一轮调研）

本轮只完成"目标类是否存在"。仍需核对：

1. **被注入方法的签名**（重点 20 个高风险 mixin：@Overwrite 的
   `CubicMatrixRenderer.applyGroupTransformations`、`ModelFormRenderer` 私有 `applyPose`、
   7 处 @Redirect 的调用点、`UIVanillaSoundList` 的私有内部类字段名等）
2. **MC 类型触点清单**：Yarn→Mojmap 的具体替换表（`MatrixStack`→`PoseStack`、
   `Identifier`→`ResourceLocation`、`Text`→`Component` 等）在本模组中的出现位置
3. **API 替代面**在两个 FSR 版本的差异（Addon API 2.0 facade、`BBSClientApi`、
   `Register*Event`、public 注册表 `UIClip.register` / `UIClipRenderers.register` /
   `UIKeyframeFactory.register` / `KeyframeFactories.FACTORIES`）
4. **平台接入**：两版 mods.toml 依赖键差异（1.20.1 `forge` vs 1.21.1 `neoforge`）、
   compileOnly 应选 `BBS_FSR_beta_v0.0.11.jar` 还是 `-slim.jar`
