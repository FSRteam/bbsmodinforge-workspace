# S2 核心数据层与 API 契约

父任务：`.trellis/tasks/07-26-bbs-snow-neoforge-port`

## Goal

在已通过 Gate 1 的 `bbs-snow-addon/` 骨架上建立后续 S3-S9 共用的数据与 duck-typing
契约：迁移 23 个 API 接口、12 个 common Mixin、5 个 client accessor Mixin、设置模型和
这些 Mixin 的最小数据依赖，并在真实 NeoForge 客户端中证明它们全部 apply、设置分类可见。

S2 只建立数据/序列化/访问器地基，不交付 PBR 渲染、迷你窗口、粒子、回放 UI、剪辑或流体
模拟等最终功能。

## Confirmed Facts

- S1 已归档，独立 addon 的 Mixin 注入、外部 addon 注册、事件桥接和
  `defaultRequire: 1` 正负向验证均通过。
- 源插件 `api/` 共 23 个接口：common 10 个、client 13 个。
- 源插件 common config 含 12 个 Mixin；client API config 含 5 个 accessor Mixin。
- 17 个目标类在当前 BBS FSR 中均存在。除 `Transform.autoLerp` 与
  `PoseTransform.autoLerp` 已从 FSR 删除外，S2 使用的注入方法和 accessor 字段/方法仍存在。
- 两个 PBR common Mixin 直接依赖 `pbr/` 下 5 个数据/关键帧工厂类；这些类必须作为 S2
  数据层迁入，S3 只继续客户端渲染和 UI。
- 当前源插件的设置实现是一个 `bbs_snow` 分类、20 个设置值和两个 `ValueSectionHeader`，
  不是父任务早期记录的 8 个分类。S2 以当前源代码为等价基线。

## Requirements

### R1 API 契约

- R1.1 迁移 common API 10 个：`ActionsOverlayProvider`、`ActionTimelineConfig`、
  `BoneTextureHolder`、`GroupTextureGradeHolder`、`GroupTextureHolder`、
  `MolangSharedProvider`、`PBRModelFormAccess`、`PivotHolder`、`TextureGradeHolder`、
  `TextureGradeProvider`。
- R1.2 迁移 client API 13 个：`ActionsTimelineEditorContext`、`BoneCollapseHandler`、
  `FilmsControllerAccessor`、`FloatLayerAccess`、`ParticlePlusParticle`、
  `PickTextureButtonHolder`、`PoseTabStateProvider`、`PoseTextureGradeEditorHolder`、
  `TransformPivotEditor`、`UIClipsAccessor`、`UIClipsPanelAccessor`、
  `UIDockLayoutAccessor`、`UIFilmPanelLayoutAccessor`。
- R1.3 所有包名改为 `mchorse.bbs_snow.*`，所有注入成员/duck 方法统一使用
  `bbs_snow$` 前缀；不得残留 `bbs_fslovecml$`。
- R1.4 删除 `ParticlePlusParticle` 的 Fabric `@Environment`/`EnvType`，靠 client source tree
  与 NeoForge dist 入口隔离，不引入等价的运行时 Fabric 注解层。

### R2 common 数据层

- R2.1 迁移 5 个 common PBR 数据/工厂类：`PBRData`、`BonePBRData`、`PBRChannel`、
  `PBRKeyframeFactory`、`BonePBRKeyframeFactory`，供 common Mixin 编译与后续 S3 复用。
- R2.2 迁移 `ActionTimelineEvaluator` 与 `PresetDataOperations`，保持 action timeline 计算和
  preset remove/rename 的现有语义。
- R2.3 持久化、copy、equals、identity、default 与插值路径必须覆盖新增字段，不能只添加
  getter/setter 造成保存或复制后数据丢失。
- R2.4 新写出的内部数据键/transition 前缀使用 `bbs_snow` 命名；本任务不增加读取旧
  `bbs_fslovecml` 键的兼容别名。

### R3 Mixin 契约

- R3.1 迁移 common Mixin 12 个：`ActionConfigMixin`、`ActionPlaybackMixin`、
  `ActionsConfigKeyframeFactoryMixin`、`BBSSettingsMixin`、`DataManagerAccessor`、
  `FormPropertiesPBRMixin`、`KeyframeSegmentMixin`、`ModelFormMixin`、`ModelGroupMixin`、
  `ModelMixin`、`PoseTransformMixin`、`TransformMixin`。
- R3.2 迁移 client accessor Mixin 5 个：`FilmsAccessor`、`UIClipsInvoker`、
  `UIClipsPanelAccess`、`UIDockLayoutAccess`、`UIFilmPanelLayoutAccess`，物理路径与包声明统一
  放在 `mixin/client/accessor/`。
- R3.3 删除 S1 的 `UIBaseMenuProbeMixin` 及 config 声明；common/accessor config 分别列出
  本任务的 12/5 个真实 Mixin，其他 client 与 Iris config 保持空壳。
- R3.4 FSR 已删除的两个 `autoLerp` 注入不得保留为 `require=0` 假装兼容；应删除这两个
  注入，并确认现有 `lerp(..., IInterp, float)` 路径仍承载同类插值数据。
- R3.5 Mixin package 下只允许 Mixin/Accessor，不放普通 helper；四份 config 均保持
  `JAVA_21`、无 refmap、`defaultRequire: 1`。

### R4 设置与客户端桥接

- R4.1 迁移 `CMLSettings` 与 `ValueSectionHeader`，注入一个 `bbs_snow` 分类，保留当前源插件
  的 20 个设置值、默认值、范围、隐藏状态与两个标题行。
- R4.2 `BBSSnowModClient` 在构造期以独立 addon id `bbs_snow_client` 注册 client
  `BBSAddonMod`，避免与 common `bbs_snow` id 去重冲突。
- R4.3 通过 BBS 的 client lifecycle/resource API 注册 addon source pack 与 l10n provider，
  资源命名空间统一为 `bbs_snow`；source pack 就绪后显式 reload l10n。
- R4.4 为 `ValueSectionHeader` 注册只读居中标签 UI 工厂；不在 S2 注册后续模块的 UI。
- R4.5 提供 S2 设置项所需的 `en_us`/`zh_cn` strings，其他模块翻译留给各自子任务/S10。

## Constraints

- 不修改 `new/`、`old/` 或源插件仓库的源码。
- 不移植 S3-S9 的最终渲染器、编辑器、粒子、剪辑或模拟实现。
- 不引入 Fabric 运行时依赖，不保留 `fabric.mod.json` 或 access widener。
- 不降低任一 config 的 `defaultRequire`，也不以 `require=0` 掩盖 BBS 目标漂移。
- 裸 JVM 检查不得触发 `BBSMod` 静态初始化；Mixin 正确性以实际 `runClient` 为准。

## Acceptance Criteria

- [x] AC1 `./gradlew build --console=plain` 真实 exit code 为 0，且强制 BBS jar 校验通过。
- [x] AC2 23 个 API 接口全部存在并编译，源码中无 `bbs_fslovecml$`、`net.fabricmc` 引用。
- [x] AC3 jar 内含 12 个 common Mixin、5 个 client accessor Mixin及更新后的 config，且不再含 S1 探针。
- [x] AC4 `runClient` 日志显示 17 个 S2 Mixin 全部 apply，无 critical injection、accessor 或 class-load 错误。
- [x] AC5 游戏内 BBS 设置出现单一 `bbs_snow` 分类，20 个设置值和两个分区标题可见，英文/中文键可解析。
- [x] AC6 action timeline、pivot、pose texture/grade、ModelForm PBR/action overlay 数据在保存/复制/插值/重载路径不丢失。
- [x] AC7 common `bbs_snow` 与 client `bbs_snow_client` addon 均被收集并桥接，客户端类不会在服务端侧提前加载。
- [x] AC8 `git diff --stat new/` 为空，addon 摘除后 BBS FSR 无任何源代码或行为改动。

## Out of Scope

- PBR shader/renderer/UI、Iris 注入（S3）。
- Cubic 渲染与动画消费侧（S4）。
- 粒子、迷你窗口、回放编辑器、剪辑、流体功能（S5-S9）。
- Premiere 差异与全量本地化（S10）。
- 旧 `bbs_fslovecml` mod id、资源路径或持久化键的兼容别名。

## Open Questions

无。父任务中“8 个设置分类”的旧记录已按当前源插件代码纠正为一个 `bbs_snow` 分类。
