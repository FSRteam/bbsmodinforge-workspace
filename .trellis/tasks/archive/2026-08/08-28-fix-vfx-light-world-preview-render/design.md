# 技术设计

## 诊断顺序

1. 检查 `ShaderInstance` 构造的默认资源命名空间；自有 shader 必须把 `shaders/core/*` 请求映射到 `vfxlights`。
2. 检查 `LightFormRenderer.render3D` 是否在 modelRenderer/UI 路径提前返回；这些路径没有世界深度，但必须绘制上游的可见 guide/effect。
3. 检查 `BBSClientApi.onAfterLevel` 是否已注册、主 FBO 是否被强制绑定、shader 是否非空。

## 实现边界

- 预览效果使用 position-color 几何，避免把世界 framebuffer 误用于 UI 预览。
- 世界 fallback 使用深度纹理重建表面位置；shader 资源通过 addon namespace provider 加载。
- 所有诊断日志只报告初始化/资源缺失，不在每帧刷屏。
