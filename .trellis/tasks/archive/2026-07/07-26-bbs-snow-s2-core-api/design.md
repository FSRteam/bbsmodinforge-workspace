# S2 设计：核心数据层与 API 契约

## 1. 边界与交付物

S2 把源插件中被多个后续模块共同依赖的契约一次落地。目录边界如下：

```text
bbs-snow-addon/src/
├── main/java/mchorse/bbs_snow/
│   ├── api/                    # 10 个 common duck 接口
│   ├── mixin/                  # 12 个 common Mixin
│   ├── pbr/                    # 5 个 common 数据/关键帧工厂类
│   ├── settings/               # CMLSettings + ValueSectionHeader
│   └── utils/                  # ActionTimelineEvaluator + PresetDataOperations
├── client/java/mchorse/bbs_snow/
│   ├── api/                    # 13 个 client duck 接口
│   ├── mixin/client/accessor/  # 5 个 accessor Mixin
│   ├── BBSSnowClientAddon.java
│   └── BBSSnowModClient.java   # 扩展 S1 入口
└── client/resources/assets/bbs_snow/strings/
    ├── en_us.json
    └── zh_cn.json
```

`pbr/` 的 5 个 common 类在目录上属于 PBR，但它们是 `FormPropertiesPBRMixin` 与
`ModelFormMixin` 的编译/数据契约，必须在 S2 迁入。S3 接管客户端 renderer、UI、shader 与
Iris Mixin，不重复定义这些类。

## 2. API 与 Mixin 分层

### common API（10）

- action timeline：`ActionTimelineConfig`、`ActionsOverlayProvider`
- pose/model texture：`BoneTextureHolder`、`GroupTextureHolder`、
  `TextureGradeHolder`、`GroupTextureGradeHolder`、`TextureGradeProvider`
- model capabilities：`MolangSharedProvider`、`PBRModelFormAccess`、`PivotHolder`

### client API（13）

这些接口只定义后续模块的类型边界，不在 S2 实现最终 UI 行为。5 个含私有字段/方法访问需求的
接口由同名 accessor Mixin 实现；其余接口由 S3-S9 的 client Mixin 实现。

### Mixin config

- `bbs_snow.mixins.json`：本任务的 12 个 common Mixin。
- `bbs_snow.client.api.mixins.json`：5 个 accessor Mixin。
- `bbs_snow.client.mixins.json`：删除 S1 probe 后暂时为空。
- `bbs_snow.client.iris.mixins.json`：保持空壳，由 S3 填充。

Accessor 的物理目录必须与声明包一致，使用
`mchorse/bbs_snow/mixin/client/accessor/`。源仓库虽然文件放在 `mixin/client/`，其 package 已经
是 `.accessor`；移植时把路径也理顺，避免后续清单工具误判。

## 3. 数据流与不变量

### 3.1 Action timeline

```text
ActionConfigMixin 挂载 clip/frame/overlay/weight
    -> toData/fromData/copy/equals/isDefault 保持状态完整
    -> ActionsConfigKeyframeFactoryMixin 插值
    -> ActionTimelineEvaluator 解析片段
    -> ActionPlaybackMixin.getTick 按 timeline frame seek
```

四个字段只要任一被 timeline 驱动，`isDefault` 就必须为 false，确保 BBS 序列化不会省略数据。
`copy` 与 `equals` 必须包含全部字段，不能让编辑器复制后产生幽灵差异。

### 3.2 Transform / pose / model

```text
TransformMixin.PivotHolder
    -> identity/copy/add/lerp/equals/isDefault/toData/fromData
    -> setupMatrix 在旋转/缩放中心应用 pivot

PoseTransformMixin
    -> bone texture + tint/whiten
    -> copy/identity/lerp/add/serialization
    -> ModelMixin.applyPose
    -> ModelGroupMixin transient texture/grade state
```

FSR 的 `Transform`/`PoseTransform` 不再提供 `autoLerp`。源 Mixin 中对应的两个注入删除，不改成
可选注入；当前 FSR 的 `lerp(..., IInterp, float)` 已接收实际插值器，新增字段必须沿此路径插值。

### 3.3 ModelForm PBR/action properties

`ModelFormMixin` 在无参构造器中追加 action overlays、Molang shared、texture grade 和 PBR
properties。插入点继续使用 `actions` 之后的 `add(BaseValue)` 调用，随后 TAIL 补其余属性。
`FormPropertiesPBRMixin` 把这些 value 映射为 keyframe channels；S3 渲染端只消费结果。

### 3.4 Preset access

`DataManagerAccessor` 只暴露 private `data` 与 protected `getFile(String)`；
`PresetDataOperations` 负责 remove/rename 后整组写回。不要在 accessor package 中放 helper。

## 4. 目标签名兼容矩阵

| Mixin | BBS FSR 目标 | S2 处理 |
|---|---|---|
| `ActionConfigMixin` | `toData/fromData/copy/equals/isDefault` 均存在 | 保留并改名前缀/数据键 |
| `ActionPlaybackMixin` | `getTick(float)` 存在 | 保留 |
| `ActionsConfigKeyframeFactoryMixin` | 六参数 `interpolate(...)` 存在 | 保留 |
| `BBSSettingsMixin` | `BBSSettings.register(SettingsBuilder)` 存在 | TAIL 注入 |
| `DataManagerAccessor` | private `data`、protected `getFile(String)` 存在 | 保留 accessor/invoker |
| `FormPropertiesPBRMixin` | `getOrCreate`、private `applyProperty` 存在 | 保留，核对 descriptor |
| `KeyframeSegmentMixin` | `createInterpolated()` 存在 | 保留 |
| `ModelFormMixin` | 无参构造器与 actions 后的 `add` 调用存在 | 保留精确注入点 |
| `ModelGroupMixin` | `reset()` 存在 | 保留 |
| `ModelMixin` | `applyPose(Pose)` 存在 | 保留 |
| `PoseTransformMixin` | 除 `autoLerp` 外均存在 | 删除 `autoLerp` 注入 |
| `TransformMixin` | 除 `autoLerp` 外均存在 | 删除 `autoLerp` 注入 |
| 5 个 client accessor | 目标字段/私有方法均存在 | 保留，统一 `bbs_snow$` |

`UIClips` 已有 public `getFactory()`，但 private `factory` 字段仍存在。S2 暂保源接口形态，避免
后续 `AudioDropImporter` 等调用方出现双轨；是否改用 public API 留给对应功能子任务。

## 5. 命名与持久化

- Java package、成员前缀、资源 namespace 全部使用 `bbs_snow`。
- `@Unique` 常量与字段使用 `BBS_SNOW_*` / `bbs_snow$*`。
- 新写出的 MapType key 与 transition prefix 使用 `bbs_snow_*`。
- 不写旧 `bbs_fslovecml` 键的双读/别名。父任务已定义这是首次 NeoForge 移植，无存量 NeoForge
  addon 需要兼容；机械保留旧键会让新命名永久双轨。
- BBS 原生 value id（如 `actions_overlay`、`texture_tint`、`pbr_s_r`）保持不变，因为它们是
  功能数据 schema，不是旧 mod namespace。

## 6. 设置与客户端加载

common `BBSSnowAddon` 继续证明 common event bridge。client 侧新增独立对象：

```java
BBSMod.registerAddon("bbs_snow_client", BBSSnowClientAddon::new);
```

不能复用 `bbs_snow` id，`BBSAddonCollector` 会按 id 去重。`BBSSnowClientAddon` 订阅
`RegisterClientSettingsEvent` / `RegisterL10nEvent`；l10n provider 指向
`assets/bbs_snow/strings/<lang>.json`。

`BBSSnowModClient` 在构造期注册 `ValueSectionHeader` 的 `UIValueMap` factory，并用
`BBSClientApi.onClientStarted` 注册 `InternalAssetsSourcePack`。source pack 注册完成后调用
`BBSModClient.getL10n().reload()`，因为 l10n 事件可能早于 addon source pack 到位。

设置结构严格跟当前源提交：一个 `bbs_snow` category、20 个 `builder.get*` value、两个只读标题
value。早期提交曾使用多个 `cml_*` category，但那不是本次迁移的源行为。

## 7. 验证与回滚

### 静态/构建

- `./gradlew build --console=plain`，不用管道，检查真实 exit code。
- 搜索 `bbs_fslovecml`、`net.fabricmc`、`refmap`；目标源码/配置中均应为空。
- 解包 jar，核对 23 个 API、17 个 Mixin、四份 config、strings 资源，且无
  `mchorse/bbs_mod/**` 被打包。
- `git diff --stat new/` 必须为空。

### 运行

- `runClient` 后确认 common/client addon id 都被收集和 bridge。
- 日志确认 12 common + 5 accessor 全部 apply，无 FATAL。
- 打开 BBS 设置，检查 category、20 个值、两个标题和中英文文本。
- 用实际 BBS 数据编辑/保存/重载覆盖 action timeline、pivot、pose texture/grade 与 ModelForm
  properties；构建成功不能替代这一步。

### 回滚

S2 只新增 `bbs-snow-addon` 文件并替换 S1 probe/config。若某个注入点失败，回退该 Mixin 到未列入
config 的状态并保留其他已验证批次；不得降低全局 `defaultRequire`。若 BBS 目标已无法从 addon
实现，按父任务 R5.3 停下并交由用户决定，不修改 `new/`。
