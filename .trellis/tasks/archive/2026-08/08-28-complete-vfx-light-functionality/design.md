# 技术设计

## 视觉效果

fallback shader 使用深度重建位置。Point 采用球形距离衰减，Spot 采用距离与锥角衰减，Area 采用有限面板/球体范围，Ambient 使用以光源位置为中心的 sphere/box 体积衰减。所有通道读取 `LightColor`，加法混合只叠加正贡献，不覆盖原图。

## 编辑器

`LightFormRenderer.render3D` 在 UI/modelRenderer/preview 直接画辅助几何；`UILightForm` 用 `UISliderTrackpad` 表示有上下限的数值，并通过 `get()` 读取运行时关键帧值。分组修改复制 struct 后 `set()`，确保通知链。

## 生命周期

收集器为 MODEL_BLOCK 记录可解析的 source block；若宿主没有公开破坏事件，registry 对已停止收集的模型方块光源使用较短兜底 TTL，编辑器预览只刷新真实存在的快照而不永久续期。
