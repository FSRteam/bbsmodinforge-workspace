# 技术设计

## 1. 外部录制生命周期

`UIFilmController.startRecording(["outside"])` 在关闭界面前创建不可变的录制启动快照，至少包含 Film 身份、Replay 身份/索引、游标和所需 Form。启动逻辑只消费快照，不再从已关闭的 `UIFilmPanel` 读取状态。

录制器仍由 `Films` 作为唯一运行时所有者。开始、停止、取消、断线和世界卸载沿用幂等终止路径；身份校验使用对象/稳定标识，不使用值相等替代选中项身份。

## 2. 仪表盘同项重入

将“当前面板变更”和“当前面板重新激活”视为两个不同事件。`UIDashboard.onOpen()` 在 `UIDashboardPanels.open()` 完成面板 `appear()` 后，显式重新同步：

- 根级 `UIOrbitCamera.control` 与 `IFlightSupported`；
- FOV/roll 支持；
- 当前世界相机快照；
- 失效 UI 焦点和手势状态。

右键模型方块的网络入口在打开仪表盘、激活模型方块面板并选中实体后，再以目标方块中心建立轨道相机焦点。该操作只属于明确目标的右键入口，普通 `0` 键重开不强制改变用户视角。

## 3. Form 预览渲染边界

保留世界渲染的 `FormTranslucentQueue`，但预览渲染类型始终即时提交。队列激活、排序原点和延迟消费者必须限定在世界阶段，预览路径不得继承上一世界帧的队列状态。

对照 BBSFS 核对 `FormRenderer`、`MobFormRenderer`、`CustomVertexConsumerProvider` 和预览 renderer 的状态边界：shader、blend、depth test/write、cull、light、overlay、model-view matrix 以及 provider flush/cleanup。任何修复都在调用边界保存并恢复状态，避免用实体白名单修补。

预览阴影使用与 BBSFS 相同的 Form 变换和渲染空间；发光层保持自身 full-bright 语义，普通层不被全局强制成发光。

## 4. 动画预览性能

先用调用路径和轻量计数测试确认重复工作，再在预览实例范围内缓存。缓存键包含会改变结果的全部输入：预览实例/渲染类型、动画时间与 transition、Form/pose 版本、armature 矩阵、IK/物理状态和拾取 stencil 世代。

允许同一帧、相同输入复用：

- 动画/armature 最终矩阵；
- CPU 蒙皮后的顶点和法线；
- 未变化几何的 GPU buffer；
- 普通预览与同帧拾取之间可共享的计算结果。

动画时间、姿势、模型、IK、物理、渲染空间或拾取世代变化时立即失效。缓存归预览 renderer/实例所有，不能挂在全局模型资源上，以免不同实体共享模拟状态。

## 5. 兼容性与回滚

- 不修改现有公开方法描述符；必要的新入口采用附加重载或内部值对象。
- 不改变 NeoForge 1.21.1 映射、网络包格式或存档格式。
- 每一问题保持独立提交前 diff 边界；若视觉修复引入世界排序回归，可单独回滚预览状态隔离改动。
- 性能优化必须先通过结果一致性测试；无法证明一致的缓存不合入。
