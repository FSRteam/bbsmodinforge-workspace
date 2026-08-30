# 技术设计：VFX Light 分类、命名与渲染修复

## UI 边界

- `UILightForm` 只负责创建分类外壳和注册默认 BBS 面板。
- `UILightFormPanel` 负责颜色、强度、范围、阴影、温度、闪烁、分组和具体灯形状。
- `UILightAirPanel`、`UILightStylePanel` 分别负责 Air 与 Style/Toon/Flare。
- `UILightStructKeyframeFactory` 通过 `LightStructFactory` 编辑 Air、Style、Toon、Barn 关键帧。

四种灯仍使用四个独立的 `UIFormEditor.register` supplier。`AmbientLightForm` 不注册 Air/Style 面板，避免显示无效控制。

## 资源与命名

NeoForge `modId` 继续使用稳定的 `vfxlights`，避免破坏已保存表单数据；显示名、Gradle archive name 和 JAR 文件名改为 `VFX Light` / `VFX_Light_fsr`。自有 source pack 使用 `vfxlights` 前缀和 `assets/vfxlights` 根目录，图标位于 `bbs_override/textures/`，语言链接为 `vfxlights:strings/en_us.json`、`vfxlights:strings/zh_cn.json`。

## 渲染数据流

`FormRenderingContext.world` 是 ENTITY/MODEL_BLOCK 的绝对语义矩阵。收集器从该矩阵提交 `LightRegistry`，客户端 tick 开始清理帧，AFTER_LEVEL 使用当前投影矩阵消费灯光。Fallback shader 使用 addon 自有资源，不覆盖 BBS 的模型 shader；Iris 阴影图和 shaderpack SSBO 仍单独标记为未迁移。

## 验证

Gradle 自定义 `verifyVfxLightPort` 检查分类注册、关键字段、翻译资源、命名、JAR 内容和禁止的 Fabric/BBS 核心类；`check` 和 `build` 都依赖该检查。
