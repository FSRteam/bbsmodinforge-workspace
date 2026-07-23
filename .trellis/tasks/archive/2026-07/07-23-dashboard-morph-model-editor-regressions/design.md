# 技术设计：仪表盘生命周期、预览队列与模型编辑回归

## 架构边界

修复分为三条边界：

1. `UIDashboard`/`UIDashboardPanels` 负责 panel 的 open/close 与 appear/disappear 成对生命周期。
2. `FormRenderer`/`FormTranslucentQueue` 负责区分真实世界渲染和 UI/预览渲染；MobForm 只在世界阶段参与延迟透明排序。
3. `UIModelBlockPanel`/`UIPropTransform` 负责动态子菜单所有权和快捷键状态；模型动画性能优化放在 `UIPickableFormRenderer` 与 `ModelFormRenderer` 的预览边界。

## 生命周期契约

- `UIDashboardPanels.open()` 只处理长期 panel 状态；每次 dashboard 从屏幕重新打开时，当前 panel 必须显式重新 `appear()` 并恢复 placement。
- 关闭时动态 palette、camera controller、gizmo 和 key owner 必须在 panel close/disappear 中清理；同一 panel 再次进入不能依赖旧 parent 状态。
- 模型方块 selection 失效时先移除 palette，再清空 `modelBlock`，避免回调闭包访问已移除实体。

## 渲染契约

- `FormTranslucentQueue.ensureStarted()` 的调用条件是 `BBSRendering.isRenderingWorld()`，并且不是 UI、picking、Iris shadow 或独立 framebuffer 预览。
- UI/影片预览沿用立即 `MultiBufferSource` 路径；`context.modelRenderer()`/`FormRenderSpace.UI_LOCAL` 不得启动世界透明队列。
- MobForm 的 `try/finally` 必须恢复 pose、world、blend/depth/cull、sort origin 和 hijack callback；shadow pass 由 `shaderShadow` 单一开关控制。

## 快捷键契约

- `enableMode(1)` 与 `scaleHotkeyOrder` 共享同一循环逻辑；统一缩放只由 `enableUniformScale` 的非 hotkey/gizmo 中心句柄路径触发。
- R 的 view/sphere 模式只在存在有效 ray/gizmo 上下文时加入循环；切换 gizmo 显示模式的第一次按键保持现有语义，第二次进入操作。
- 每次轴切换恢复本次手势缓存，不把上一个轴的预览值误认为新轴的基础值。

## 动画性能边界

- 动画配置只在模型或配置真正变化时重建 animator。
- normal/stencil 预览共享同一帧姿态求值结果；stencil 只更新拾取所需的 GPU 数据，不重新推进动作时间或重复执行昂贵的 IK/物理/蒙皮计算。
- 不降低动作更新频率、迭代次数或约束正确性来换取帧率。

## 兼容与回滚

- 不改变表单序列化、模型资源或 API。
- 代码集中在 dashboard/panel、render queue、MobForm、transform input 和 preview renderer；若队列修复引发世界回归，可独立回滚其 gating，不影响生命周期与快捷键修复。
