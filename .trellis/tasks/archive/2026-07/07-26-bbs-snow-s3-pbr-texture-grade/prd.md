# S3 PBR 与纹理分级

父任务：`.trellis/tasks/07-26-bbs-snow-neoforge-port`

## Goal

在 S2 已完成的 PBR 数据、ModelForm 属性和纹理分级 duck API 之上，移植 bbs_snow 的客户端
PBR 渲染、骨骼/材质编辑 UI、模型纹理调色 shader、全屏色彩分级引擎与 Iris 纹理包装兼容，
并在 NeoForge 1.21.1 客户端证明资源、Mixin、注册和 GL 路径可用。

S3 交付渲染与编辑基础设施，不提前移植 S4 的动画/Cubic 扩展、S7 的通用回放工具或 S8 的
Hotbar/Cinematic/Color clip 类型。

## Confirmed Facts

- S2 已归档；addon 基线提交为 `1d73747`，包含 5 个 common PBR 数据/工厂类、ModelForm PBR
  属性、pose/model 纹理分级数据和 17 个已验证 Mixin。
- 源插件剩余 PBR 客户端代码为 3 个 render helper、2 个 UI factory、3 个 `client/screen`
  类、4 个专用 client Mixin 和 1 个 Iris Mixin；另有 PBR 逻辑混在
  `BBSRenderingMixin`、`CubicVAORendererMixin`、`ModelFormRendererMixin` 中。
- 源入口在 common 侧注册 `pbr` / `bone_pbr` factory，在 client 侧注册对应 UI factory；
  S2 尚未迁移这两组注册，必须由 S3 补齐。
- BBS FSR 的 `ModelInstance.render`、`CubicVAORenderer.renderGroup`、
  `ModelFormRenderer.render3D`、`UIReplaysEditor.flushForm`、`UIKeyframeSheet` 七参构造器和
  `BBSShaders.setup` 均存在，但 Minecraft 类型已从 Yarn 1.20.1 映射迁到 Mojmap 1.21.1。
- FSR 当前模型 shader 新增 `PassMode` 与透明命令队列，且 vertex shader 已删除
  `IViewRotMat`。S3 shader 必须以当前 FSR `model` shader 为基线叠加调色，不能原样复制旧
  shader，否则会破坏透明材质分段渲染。
- `IrisTextureWrapper` 的方法在目标版本叫 `getId()`，不是源插件的 Yarn 名 `getGlId()`；
  Iris config 已由 `neoforge.mods.toml` 用 `requiredMods=["iris"]` 隔离。
- 源 `UIBonePBRKeyframeFactory` 的当前工作快照已把六个裸英文标签改为 l10n key，但这些 key
  暂存在 S9 的 `SnowUIKeys`。S3 必须拥有自己的 PBR UI key，不能依赖尚未实施的 S9。

## Requirements

### R1 工厂与 UI 注册

- R1.1 common 入口幂等注册 `pbr` 与 `bone_pbr` 两个 keyframe factory，不覆盖其他 addon
  已注册的非 bbs_snow 实现而不留诊断。
- R1.2 client 入口在对应 factory 存在时注册 `UIPBRKeyframeFactory` 与
  `UIBonePBRKeyframeFactory`，重复构造不得重复产生可观察副作用。
- R1.3 材质 PBR UI 保留 sR/sG/sB/sA/n 范围；骨骼 PBR UI 保留骨骼列表、六个通道及
  emission multiplier，标签使用 `bbs_snow` l10n key。
- R1.4 回放编辑器为 ModelForm 暴露材质 PBR、bone PBR、texture tint 与 texture whiten
  轨道，并保持当前 FSR 的 path/channel 规则。

### R2 PBR 渲染上下文

- R2.1 在 `ModelFormRenderer.render3D` 范围内设置/清理 ModelForm material 与 bone PBR
  overrides；清理必须覆盖正常返回和延迟渲染命令。
- R2.2 在 `CubicVAORenderer.renderGroup` 范围内设置当前骨骼，应用每骨骼调色，并优先解析
  S2 的骨骼纹理覆盖；不得移植同一源 Mixin 中属于 S4 的 Cubic 动画/几何功能。
- R2.3 Iris 的 `_s.png` / `_n.png` wrapper 按 material/bone overrides 生成修改纹理；
  addon 摘除或 Iris 不在场时 BBS 原路径完全不变。
- R2.4 生成纹理缓存必须在资源/shader reload 时释放，且延迟透明命令必须捕获创建时的 PBR
  context 与骨骼 id，不能在 `render3D` 返回后读取已清空的 ThreadLocal。

### R3 模型纹理分级 shader

- R3.1 `ModelTextureGradeShader` 使用 1.21.1 的 `ShaderInstance`、`DefaultVertexFormat.NEW_ENTITY`
  和 addon namespace resource provider 加载。
- R3.2 shader 保留 FSR 当前 `model` shader 的 `PassMode`、fog、normal、overlay、lightmap 与
  alpha discard 行为，只叠加 hue/saturation 替换和 whiten uniform。
- R3.3 `ModelInstance` 只在 tint/whiten 有效且不是 picker/Iris world shader 时选择调色
  shader；动态 Geo/CPU 合并模型保留源插件的整模 fallback。
- R3.4 VAO/BOBJ 延迟透明命令捕获并在 draw 时恢复自己的 tint/whiten，避免共享 shader
  uniform 被后续骨骼覆盖。

### R4 全屏色彩分级基础设施

- R4.1 迁移 `ColorGradeRenderer`、`ScreenEffectRenderer` 与 source-equivalent effect 数据，
  将 Minecraft framebuffer API 映射到 1.21.1 `Minecraft` / `RenderTarget`。
- R4.2 屏幕效果队列由 S3 按 `ClipContext` 提供稳定入口；S8 的 Color/Cinematic clip 后续只
  负责生产 effect，不重复实现 renderer。
- R4.3 从源 `BBSRenderingMixin` 只拆出 screen-effect 消费分支，不提前带入 Hotbar clip/UI；
  渲染后清空当帧队列并恢复 framebuffer、viewport、projection、depth/blend 状态。

### R5 配置、资源与命名

- R5.1 regular client config 只列 S3 的非 Iris Mixin；Iris Mixin 物理路径、package 与
  `bbs_snow.client.iris.mixins.json` 一致，并继续由 `requiredMods=["iris"]` 软隔离。
- R5.2 全部新增 package、duck 成员、shader namespace、clipData key 与 l10n key 使用
  `bbs_snow`；不得新增 `bbs_fslovecml`、Fabric API、refmap 或 `require=0`。
- R5.3 shader 三件套打入 `assets/bbs_snow/shaders/core/`；en_us/zh_cn 增加且保持相同的 S3
  key 集合。

## Constraints

- 不修改、清理或还原 `new/`、源插件仓库或其他任务的并行改动。
- 不复制旧 shader 覆盖 FSR 的 `PassMode`/透明渲染契约。
- 不把 S4 动画器、S7 通用回放工具、S8 clip 或 S9 form/UI 类提前并入 S3。
- 所有 config 保持 `defaultRequire: 1`、`JAVA_21`、无 refmap；无法匹配的注入点必须修正或
  明确阻塞，不能降级为可选注入。
- 裸 JVM 检查不得触发 `BBSMod` 静态初始化；GL、Mixin 与 shader 正确性以真实客户端为准。

## Acceptance Criteria

- [x] AC1 `gradlew build --console=plain` 真实 exit code 为 0，BBS core jar 严格时效校验通过。
- [x] AC2 `pbr` / `bone_pbr` common factory 与两个 UI factory 均注册，PBR/bone PBR 编辑器可
  创建、选择骨骼和写回全部通道。
- [x] AC3 所有 S3 regular client Mixin 在 `runClient` 中 apply；无 injection、class-load、
  shader/resource 或 GL critical error。
- [x] AC4 模型级 tint/whiten、骨骼级 tint/whiten/texture override 和 material/bone PBR context
  在 opaque 与透明延迟路径都不串骨骼、不跨模型泄漏。
- [x] AC5 模型调色 shader 成功加载，包含 `TextureTint`、`TextureWhiten`、`PassMode`，并保持
  picker 与 Iris world fallback。
- [x] AC6 无 Iris 的客户端干净跳过 Iris config；Iris Mixin 静态目标为 `getId()`，配置和 jar
  边界正确。若本地无 Iris，正向 Iris 视觉冒烟明确留给 S10。
- [x] AC7 全屏 color-grade shader 可编译/初始化，effect 队列单帧消费并恢复 GL 状态；S8 尚未
  迁入时不注册任何 clip 类型。
- [x] AC8 jar 含 S3 client 类、Mixin、shader 与成对翻译键，不含 BBS core class；
  `git diff --stat new/` 为空，摘掉 addon 后 BBS FSR 行为不变。

## Out of Scope

- Cubic axis/cube/matrix/VAO 几何扩展、additive/procedural/Geo 动画（S4）。
- 通用回放快捷操作、bone priority、pose editor 扩展（S7）。
- Hotbar/Cinematic/Color clip 类型及其 inspector/UI（S8）。
- Fluid/Snow UI key 容器及 form 编辑器（S9）。
- 安装 Iris 依赖包与全量集成视觉回归（S10；本机已有依赖时可提前冒烟）。

## Open Questions

无。范围与兼容取舍由父任务的等价移植要求、当前源快照和 FSR 1.21.1 shader/透明队列契约确定。
