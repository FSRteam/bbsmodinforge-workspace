# 技术设计：BFX Light 的 BBSFSR 兼容层

## 边界

`bfx-light/TEMP/VFXLIGHTS/` 是不可修改的上游参考快照；`bfx-light/` 是独立 NeoForge addon。BBSFSR 由最新 `new-fs25-sync/` 产出的 JAR 提供，插件不复制宿主类。

## 方案

1. 以现有 `bbs-photon-addon` / `bbs-irlights-addon` 的 NeoForge 工程布局为模板。
2. 迁移上游与 BBS、Minecraft 无关的核心类：`forms/**`、`light/**`，仅修正 1.20 Yarn 的 `BlockPos` 包名。
3. 新增 NeoForge 构造入口，在 BBS 外部注册窗口关闭前注册 common addon；`RegisterFormsEvent` 注册四种表单。
4. 新增轻量 client bootstrap，注册 BBS 的表单选择器和可用的渲染/编辑入口；本阶段不启用上游 Fabric WorldRenderEvents、Fabric Mixin、旧 Iris IncludeGraph 注入。
5. 资源包使用 BBSFSR 的 `InternalAssetsSourcePack` 约定，翻译只声明实际存在的语言。

## 兼容策略

- BBSFSR API 2.0 的表单能力可以承载上游表单模型，因此表单数据和关键帧值优先迁移。
- 上游 Iris/渲染管线依赖 Fabric/Yarn 1.20.x 类名和注入点，当前不做未经验证的替换；在 `PORT_STATUS.md` 明确列出。
- 未来扩展渲染时，应为每个 NeoForge Mixin 目标建立运行时验证，而不是把 `require` 降为 0。

## 数据流

表单/关键帧值 → BBSFSR `RegisterFormsEvent` / `KeyframeFactories` → client editor registration → `LightRegistry` 逐帧 API。渲染后端暂由 BBSFSR/其他光照插件负责，BFX Light 不替换宿主 shader。

## 回滚

删除 `bfx-light/` 即可回滚；`new/` 无修改。上游快照保留在 `TEMP` 供后续逐项移植。
