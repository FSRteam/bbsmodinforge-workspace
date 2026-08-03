# MobForm 骨骼列表适配 2.5 层级树显示样式

## Goal

本项目（FSR bbs-FSR NeoForge 移植版）在 b66871f5 迁移了上游 bbs-fs 2.5 代码。2.5 将骨骼列表显示从"平铺 label 列表"改为"层级树列表"（`UIBoneTreeList`：连接线、按深度缩进、搜索时平铺、层级序排列）。迁移时 IModel 路径（模型表单面板、IK/物理/约束面板、骨骼拾取弹窗、film 模型骨骼关键帧）已同步为树形，但 **MobForm 的骨骼列表路径未同步**，仍是旧平铺样式。本任务让 MobForm 骨骼列表（及同类 BoneHierarchy 列表）与 2.5 显示样式一致。

## Confirmed Facts（代码勘察结论）

- 2.5 显示样式：`UIPoseBoneStringList` 基类由 `UIBoneHierarchyList`（平铺）换成 `UIBoneTreeList`（树），`UIPoseEditor.fillGroups(IModel…)` 通过 `setHierarchy(model, …)` 喂入层级元数据（`UIPoseEditor.java:368`）；骨骼按层级序展示，不字母排序。
- 迁移提交 `b66871f5` 未触碰 `UIMobFormPanel` / `UIPoseFormPanel` / `BoneHierarchy`。
- MobForm 未同步的三处：
  1. `UIPoseEditor.fillGroups(BoneHierarchy…)`（`new/src/.../ui/utils/pose/UIPoseEditor.java:335-348`）调用 `groups.list.setHierarchy(null, null)` 清空树元数据 → 平铺（MobForm 面板 `UIMobFormPanel.java:134` 走此路径）。
  2. film 关键帧 `UIPoseKeyframeFactory.java:63`（MobForm 分支）走 `fillGroups(bones, false)` → 平铺 + **字母序排序**。
  3. `UIGlintKeyframeFactory.java:61` 平铺（`setSource(boneIds, labels(false), false)`）。
- `UIBoneTreeList.setHierarchy` 目前只有 `IModel` 重载（`UIBoneTreeList.java:98`），无 BoneHierarchy 重载。
- `BoneHierarchy.Bone` 已含 `id/name/parentId/depth/layerId/primary`（`BoneHierarchy.java:348`），`boneIds` 为跨 layer 层级前序（`VanillaBoneHierarchy` 递归构建、children 自然序）。
- 上游 2.5 的 `MobForm` 为纯 `Form`（无 pose），骨骼编辑器整体是移植版自有（`UIMobFormPanel`/`BoneHierarchy`/`VanillaBoneHierarchy`），"同步 2.5 样式"= 移植版自有编辑器采用 2.5 树形展示约定。
- `hierarchicalLabels`（`getLabels(true)` 的 "  " 缩进）当前无任何调用方传 true，为死代码路径。
- `UIBoneList.filter` 已内建搜索时 `flat(true)` 平铺逻辑，树组件搜索行为无需额外处理。
- 上游参考代码已克隆于工作区 `.tmp/upstream-2.5/` 与 `.tmp/upstream-master/`（任务完成后清理）。

## Requirements

- MobForm 骨骼列表（表单面板 pose 编辑器）按 2.5 样式显示为层级树：连接线、深度缩进、层级序、树标签。
- film 中 MobForm 骨骼关键帧编辑器（`UIPoseKeyframeFactory` MobForm 分支）同样为树形 + 层级序（消除与面板的不一致）。
- `UIGlintKeyframeFactory` 的骨骼列表同样为树形（MobForm/pose 表单）。
- 搜索时行为保持平铺（复用现有 `flat()` 逻辑）。
- 标签沿用现有 `getLabels(false)` 稳定 ID 展示名，不改变骨骼 ID/数据模型。

## Acceptance Criteria

- [ ] 打开 MobForm 表单面板：骨骼列表显示树形连接线与缩进，顺序为层级序（layer 根 → 子骨骼），标签与现状一致。
- [ ] film 中为 MobForm 添加 pose 关键帧：骨骼列表为树形 + 层级序（不再是字母序平铺）。
- [ ] glint 关键帧工厂的骨骼列表为树形。
- [ ] 骨骼搜索：输入关键字后列表平铺显示匹配项，清除后恢复树形。
- [ ] 多选/镜像/翻转/变换等既有编辑功能在树形列表下行为不变（回归：UIModelFormPanel、IK/物理/约束面板、film 模型骨骼路径）。
- [ ] 编译通过（`.\gradlew.bat --no-daemon build`，或至少 client 编译 + 相关测试）。

## Out of Scope

- 不改 `fillGroups(Collection)`（非骨骼的普通字符串列表路径，上游 2.5 亦为平铺+排序）。
- 不改骨骼 ID / 数据模型 / 序列化。
- 不做 2.5 之外的新 UI 特性。

## Open Questions

- 是否顺手移除 `hierarchicalLabels` 死代码参数（`getLabels(boolean indent)` 的 indent 分支）？
- 上游参考克隆 `.tmp/` 的清理时机。
