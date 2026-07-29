# S7 技术设计：回放编辑器与关键帧扩展

## 1. 交付边界

S7 在 `bbs-snow-addon/src/client` 增加四组实现：

```text
mchorse/bbs_snow/
|-- ui/film/UIReplayKeyframeEmbedHelper.java
|-- ui/film/replays/
|   |-- ExportUIKeys.java
|   |-- ReplayUIKeys.java
|   |-- FilmIKSpatialTargetSampler.java
|   `-- overlays/UIExportAnimationOverlayPanel.java
|-- utils/
|   |-- AnimationFileOperations.java
|   |-- AnimationInterpInheritance.java
|   |-- BonePriority.java
|   |-- IKAnimationExporter.java
|   `-- PoseAnimationExporter.java
`-- mixin/client/                         # 22 个 Mixin/Accessor
```

S2 已迁移的 client API 接口与 common Actions 数据层直接复用；S3 的 `UIBonePBRKeyframeFactory` 和 S6 的 `IMiniWindowDockHostRef` 是既有依赖，不创建平行契约。

## 2. 功能和所有权

### 2.1 Actions 时间轴

`ActionTimelineConfig` 是起点/终点标记的唯一数据契约。UI Mixin 只负责建立编辑上下文和把用户编辑同步到配对端点，播放/插值继续由 S2 的 evaluator 和 playback Mixin 负责。

```text
Actions keyframe factory
  -> UIActionsConfigEditor receives keyframe/editor/form context
  -> user selects animation
  -> create or replace paired endpoint in the same channel
  -> option edit synchronizes loop/speed/fade/tick to the pair
  -> sheet removal cleans only addon-owned paired state
```

当前 `UIActionsConfigEditor` 的合成 lambda 编号与源分支并不完全相同。实现优先寻找稳定的命名方法和控件回调边界；无法绕开合成方法时，必须使用当前 jar 的完整描述符与字段/调用锚点，并由严格客户端作为版本门禁，禁止 `require=0` 静默降级。

### 2.2 回放、姿态标签与骨骼层级

FSR 的 `UIKeyframeDopeSheet` 已拥有 pose tab roots/depths/expanded 状态。S7 在其上维护每个骨骼行的祖先链和折叠集合：

- `configurePoseTabs` 重建 O(1) 查询索引；
- `isVisible` 只在祖先被折叠时追加隐藏；
- `getSheetIndent` 和 `renderSheetLabel` 只为有子骨骼的行保留/绘制箭头；
- `UIKeyframes.subMouseClicked` 在现有图表处理前委托 duck 接口命中箭头。

现有 pose tab、IK/物理/声音/PBR sheet 不被替换。骨骼优先只在目标 sheet 存在且可见时覆盖 FSR 的默认选择，否则原逻辑继续执行。

### 2.3 Inspector 重挂

`UIReplayKeyframeEmbedHelper` 是 S7 与 S6 的单一桥：

```text
replay channels rebuild HEAD -> remember/release current inspector ownership
replay channels rebuild TAIL -> reattach selected replay/keyframe inspector
clip pick TAIL              -> refresh clip inspector host
keyframe pick TAIL          -> refresh keyframe inspector host
```

helper 通过 `IMiniWindowDockHostRef` 请求宿主刷新，不直接操作 S6 的窗口内部状态。普通停靠模式下仍使用 FSR 原有 embed 流程。

### 2.4 姿态、纹理和 pivot

普通 `UIPoseEditor` 与 `UIPoseKeyframeFactory.UIPoseFactoryEditor` 使用 S2 的 accessor 区分数据所有者。纹理/分级修改先写正确的 `ValuePose` 或 keyframe value，再调用目标已有刷新/回调路径。

`UITransformMixin` 创建 pivot 行并实现填充值的公共接口；`UIPropTransformMixin` 是唯一写回 `Transform.pivot` 的实现。这样抽象基类不会假装拥有持久数据。

### 2.5 动画文件与导出

`AnimationFileOperations` 统一拥有 JSON 文件边界：读取整个根对象、只修改目标动画节点、临时文件写入后原子替换，并保留未知字段与其他动画。导出器只组装 Bedrock animation JSON，不各自重复文件发布逻辑。

导出面板负责输入校验、冲突确认、模型目录解析和可见状态；pose 与 bone tracks 分别进入 `PoseAnimationExporter`，IK bake 进入 `IKAnimationExporter`。`FilmIKSpatialTargetSampler` 拥有临时 Film controller 和采样生命周期：它在同一 tick 推进所有 Replay，读取目标模型的 IK target/pole anchor 轨道，并按 FSR `ModelFormRenderer.applyIK` 的语义把世界空间目标逆变换到模型局部空间。导出完成或失败时必须释放临时 controller 并恢复 IK 状态。异常必须转换为明确 UI 消息，不吞掉 IO/解析错误。

## 3. Source-to-target 适配

- 包名/成员前缀统一为 `mchorse.bbs_snow` / `bbs_snow$`。
- Yarn `net.minecraft.client.render.BufferBuilder` 改为 Mojmap `com.mojang.blaze3d.vertex.BufferBuilder`；资源继续使用 BBS `Link`，命名空间改为 `bbs_snow`。
- 源端只追加能力的 Mixin 保留；与当前 FSR 相同或更旧的实现不覆盖目标方法。
- `UIDataContextMenuMixin` 纳入，因为 S2 已迁移其数据操作 owner；`UIFilmPreviewMixin` 与 `UIReplaysEditorUtilsMixin` 成对纳入，完整交付 BonePriority。
- `AnimationInterpInheritance.registerBezierEasing()` 在客户端初始化时注册一次；不得从 Mixin 静态初始化触发 BBS 全局状态。

## 4. 本地化和资源

使用窄范围 `ExportUIKeys` 和 `ReplayUIKeys` 分别拥有导出与 Replay 扩展文案；其他局部入口直接持有本模块所需 `IKey`。不迁移混合音频/PBR/miniwindow 的旧 `SnowUIKeys`。补齐：

- `bbs_snow.ui.actions.molang_shared*`
- `bbs_snow.ui.bone_priority.*`
- `bbs_snow.ui.export_animation.*`
- `bbs_snow.ui.film.replays.actions_overlay*`
- `bbs.ui.pose.pick_bone_texture`
- `bbs.ui.transforms.pivot.title`

折叠箭头纹理随 addon 打包到 `assets/bbs_snow/textures/collapse.png`。

## 5. 验证设计

- 构建门禁检查 BBS jar 新鲜度、编译、资源和禁止内嵌类。
- runtime verifier 新增不依赖真实鼠标的确定性场景：Actions 配对生命周期、骨骼层级状态、mini-window inspector 重挂、pivot 写回和动画文件 round-trip。
- 严格客户端统计新增 Mixin 名称与 target 类，构造关键 UI 并验证本地化键。
- 真正的鼠标拖拽、复杂项目导出结果和视觉手感记录到 S10，而不是以自动测试替代。

## 6. 回滚

S7 只新增/修改 addon client 文件、client mixin config、客户端初始化和本地化/纹理资源。回滚可移除这批文件及 config 条目，不需要修改 BBS FSR、S2 数据层或 S6 窗口实现。
