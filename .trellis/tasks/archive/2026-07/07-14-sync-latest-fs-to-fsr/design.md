# 技术设计：同步最新 FS 到 FSR

## Baselines

- 目标：`new/`，NeoForge 1.21.1，当前 `6ab9b2d3`。
- 已迁冻结基线：FS master `9b28a8a4`。
- 最新 FS 意图基线：FS master `395f6927`。
- 最新同 MC 参考：FS 1.21.1 `8b25fbaf`（父提交为旧 1.21.1 `02008b08` 与最新 master `395f6927`）。
- 续作意图基线：FS master `07bcc918`；同 MC 终态参考：FS 1.21.1 `65bb8c93`。
- 续作起点：已推送 FSR `829073d5`，新增迁移范围为 7 个提交。

## Migration method

每个上游提交同时读取 master 提交 diff 和 1.21.1 最终文件。master diff 决定功能边界；1.21.1 文件决定 MC API 形态；`new/` 现有同类代码决定 NeoForge/Mojmap 映射。不得直接复制 Fabric 注册、Yarn 名称或 access widener。

增量按互斥文件所有权分簇实施，避免多个 agent 同时编辑同一高冲突文件。共享入口（`UIKeys`、`Keys`、`BBSSettings`、语言资源）由整合阶段集中补齐；各功能 agent 只报告其所需键值，除非被明确授予所有权。

## Planned clusters

1. 核心模型：程序动画、IK、骨骼物理、局部风、weld、bevel、调试覆盖层。
2. 渲染与导出：framebuffer 修复、F4/F6 会话重构、世界导出分辨率修复。
3. 模型与资源编辑：模型编辑器 undo/redo、背景与 bugfix、纹理管理器重构、item/block picker 修复。
4. 影片编辑：motion path 固定/快捷键、clip 层切换/选中样式/吸附/envelope、action shortcut、replay presets。
5. Gizmo 与多选：新外观、pick tolerance、透明度、2D pie、骨骼多选及统一 picking。
6. 共享 UI/设置/语言整合：标签布局、trackpad 恢复、命令 textarea、所有新增键与设置值。

## Compatibility boundaries

- Fabric events/entrypoints → NeoForge 事件与现有 FSR facade。
- Yarn 类/方法名 → Mojmap/NeoForge 1.21.1 等价 API。
- framebuffer 状态必须通过 Minecraft/RenderSystem/GlStateManager 兼容路径恢复，不能重新引入裸 GL 状态缓存分歧。
- 网络功能继续使用 FSR 固定 broker payload；不得动态注册 addon payload 类型。
- 数据模型变化必须保留旧文件反序列化和 round-trip。
- 粒子系统是明确的 FSR 本地兼容边界：主线仅编译旧粒子实现；`archive/fs-particle-migration-395f6927` 只作隔离参考，不得 cherry-pick 或复制到本批实现。

## 2026-07-15 clusters

1. 可见性与列表预览：`804e8c97`、`07bcc918`。
2. 影片编辑交互：`458d0414`、`0e374c33`。
3. 关键帧默认插值：`55ce67b2`。
4. 导出文件名模板与 F6 世界音频：`3f2435a4`、`453de7ce`。
5. 共享设置、语言键和 UI 入口在功能簇完成后集中整合。

## Validation and rollback

每簇先静态对照再编译；高冲突簇完成后跑 `compileJava compileClientJava`。最终跑 `build`，再做提交覆盖矩阵。工作树所有更改保持未提交，便于按簇审核和回滚；不改写现有 27 个提交。

游戏内验证无法由纯构建替代，最终文档必须列出 framebuffer/导出、IK/物理、模型编辑、motion paths、gizmo picking、多选和旧档兼容 smoke。
