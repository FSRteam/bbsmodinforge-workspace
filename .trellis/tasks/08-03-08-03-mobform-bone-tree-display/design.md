# Design — MobForm 骨骼列表适配 2.5 层级树显示样式

## 架构与边界

改动全部在 client 侧 UI 层，不触碰数据模型/序列化：

```
UIMobFormPanel ──► UIPoseEditor.fillGroups(BoneHierarchy) ──► UIBoneList ──► UIPoseBoneStringList (extends UIBoneTreeList)
UIPoseKeyframeFactory (film, MobForm 分支) ──► fillGroups(…)              ──► 同列表组件
UIGlintKeyframeFactory ──► bones.setSource(…)                            ──► 同列表组件
```

核心：`UIBoneTreeList` 目前只有 `setHierarchy(IModel, Predicate)` 重载；BoneHierarchy 路径在
`UIPoseEditor.fillGroups(BoneHierarchy…)` 里用 `setHierarchy(null, null)` 显式清树 → 平铺。补一个
BoneHierarchy 重载即可让三条 MobForm 路径复用 2.5 树形渲染，不动 IModel 路径。

## 数据流与契约

- **`UIBoneTreeList.setHierarchy(BoneHierarchy, Predicate<String> hidden, Map<String,String> labels)`**
  —— 从 `BoneHierarchy` 构建 Node 树：按 `parentId` 分组（保持 `boneIds` 列表序），根 = parentId 为空
  或 parentId 不在集合中（孤儿容错，与 `fillAttachments` 的 safety-net 哲学一致），递归 `emit()`。
  因 `boneIds` 是跨 layer 层级前序（`VanillaBoneHierarchy` 递归构建 + children 自然序），按此构建
  复现同一前序。多 layer（main / inner_armor / outer_armor…）自然成为多个根，`emit()` 已支持多根。
  `treeLabel = fullLabel = labels[id]`（`getLabels(false)` 的稳定 ID 展示名，如 `inner_armor_rightArm`）。
- **`UIPoseEditor.fillGroups(BoneHierarchy, reset)`** —— 原 3 参变体（`hierarchicalLabels`）删除；
  labels 计算一次（`getLabels()`），同时喂给 `setHierarchy` 与 `setSource`；`sort=false` 保持层级序。
- **`UIPoseKeyframeFactory` MobForm 分支** —— `fillGroups(bones, false)` → `fillGroups(hierarchy, false)`
  （`hierarchy = FormUtilsClient.getBoneHierarchy(mobForm)`）。行为变化：字母序 → 层级序（与面板一致，
  即 2.5 样式）。顺带获得与 model 路径一致的 `flippedParts`（film MobForm 镜像编辑行为对齐）。
- **`UIGlintKeyframeFactory`** —— `setSource(...)` 前追加 `bones.list.setHierarchy(hierarchy, null, labels)`。
- **搜索平铺** —— `UIBoneList.filter` 已调用 `list.flat(!query.isEmpty())`，平铺时走 `labels` map
  （`elementToString` 的 `meta==null` 回退），无需改动。
- **死代码移除** —— `BoneHierarchy.getLabels(boolean)` 的 indent 分支无调用方：改签名为 `getLabels()`，
  机械更新全部调用点（FormUtilsClient:240、UIReplaysEditorUtils:181/1563/1593、UIGlintKeyframeFactory:61、
  UIPoseEditor、UIModelFormPanel 经 UIPoseEditor 内部路径）。

## 兼容性

- 不改骨骼 ID、Pose 结构、序列化格式；旧工程数据不受影响。
- `UIBoneTreeList.setHierarchy(IModel, …)` 及 `fillBones`/`fillAttachments`/`fillFlat` 原样保留，
  IK/物理/约束面板、骨骼拾取弹窗、model 表单面板零改动。
- 上游 2.5 参考（`.tmp/upstream-2.5/`）：上游本身无 MobForm 骨骼编辑器，故"同步"= 移植版自有编辑器
  采用 2.5 树形约定；film 路径改动是对上游 `fillGroups(bones, false)` 的**有意偏离**（用户已确认范围）。

## 权衡

- 标签方案：树形下 treeLabel 直接用显示名（而非 IModel 路径的原始 key），因为 MobForm 骨骼 ID 是
  路径式（`main#/root/body`），直接展示不可读；显示名同时保证搜索平铺与树形一致。
- 不引入"展开/折叠"交互（2.5 树为纯展示，无折叠状态），保持与 IModel 路径同构。

## 回滚

UI-only 改动，无数据迁移。回滚 = revert 对应提交；风险最高文件为 `UIBoneTreeList.java`（共享组件，
但仅新增重载，不改既有方法）。
