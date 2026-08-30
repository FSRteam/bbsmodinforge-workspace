# 技术设计：BFX Light 客户端投影与效果链

## 边界

上游实现位于 `bfx-light/TEMP/VFXLIGHTS`，当前可发布插件位于 `bfx-light/src`。`new-fs25-sync` 只提供宿主 API 和最新 FSR JAR，不修改其源码。

## 分层修复

1. **UI 资源层**：复制上游 `LightKeys`、`LightIcons`、`UILightSection`、`UILightPanelBase` 和四种专用面板，使用 FSR 当前 `UIFormPanel`/`UI`/`UIColor`/`UITrackpad` API；资源包提供图标贴图和语言文件。
2. **预览层**：迁移上游 `LightFormRenderer` 及四个形状 renderer，优先复用 FSR 的 `FormRenderer.renderInUI` 和 `FormRenderingContext`，将 Yarn 包名、BufferBuilder/Tesselator、PoseStack、Minecraft 客户端调用按当前 Mojmap 编译错误逐项修正。
3. **数据消费层**：迁移 `FormLightCollector`，从真实 `FormRenderingContext` 的 `stack`、`entity`、`type` 和变换矩阵解析世界坐标，将当前表单值转换为 `Light` 并提交 `LightRegistry`；每帧去重由现有 `LampKey`/`LightRegistry` 保持。
4. **效果层**：先复用上游的 BFX Light 渲染 pass 中与当前 FSR/Mojmap 可验证的部分；任何依赖 Iris IncludeGraph、Fabric WorldRenderEvents 或旧版 GL 类的代码先隔离。若完整 pass 暂时不可迁移，提供明确的 vanilla fallback，并禁止静默 no-op。
5. **注册层**：将 `VfxLightsClient` 的注册改为四种具体 renderer/editor，并在客户端启动后注册 source pack、语言、Extra 表单；保持 common 入口通过反射隔离 dedicated server。

## 兼容与映射

- 按 Fabric 官方迁移映射文档的原则执行：工具/命名映射只能作为起点，Mixin、渲染 API 和重载必须人工复核。
- 重点 Mojmap 变更包括 `net.minecraft.util.math.*` → `net.minecraft.core.*` / `net.minecraft.world.phys.*`、`MinecraftClient` → `Minecraft`、`MatrixStack` → `PoseStack`、`Identifier` → `ResourceLocation`、`net.minecraft.client.render.*` → `net.minecraft.client.renderer.*`。
- BBSFSR 的 `FormRenderer` 已在 1.21.1 使用 `PoseStack` 和 `FormRenderingContext.canDeferWorldTranslucency()`；不直接套用上游 1.20 `WorldRenderContext`。

## 数据流

表单值/关键帧 → 专用 UI 控件 → BBS Form renderer → `FormLightCollector` → `LightRegistry` → fallback/compositor 或未来 Iris backend。UI 预览只绘制图标/形状，不应把 UI 矩阵误当成世界矩阵提交光照。

## 回滚

保留当前已构建 JAR；若某个渲染 pass 迁移失败，可以只回滚该 pass，保留 UI、预览和 `LightRegistry` 提交链。`TEMP/VFXLIGHTS` 永不改写。
