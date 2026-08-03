# 追踪器附件选择器适配 2.5 树形与显示名

## Goal

追踪器剪辑（TrackerClientClip）的附件选择器在目标为 MobForm（或任何 PoseForm）时，骨骼列表仍为 2.5 迁移前的旧样式：平铺 + 字母序 + 显示内部持久化 id（矩阵键），而非 9fe77292 修正的显示名。根因：`UIBoneTreeList.fillAttachments` 的 `formNodes` 树遍历只识别 ModelForm 子树，MobForm 原版骨骼全部落入平铺安全网（原始键、无 label）。本任务让附件选择器对 PoseForm 骨骼显示 2.5 树形 + 显示名。

## Confirmed Facts

- 追踪器附件选择器链路：`UITrackerClip` → `UIAnchorKeyframeFactory.displayAttachments`（`UITrackerClip.java:55`）→ `UIBonePickerContextMenu.attachments(form, keys)` → `UIBoneTreeList.fillAttachments(form, keys)`（keys = `collectMatrices().keySet()` 原始矩阵键）。
- `fillAttachments` 的树遍历（`formNodes`/`formBoneNodes`）只处理 `ModelForm`（IModel 骨骼、track name 标签）；其他形式（MobForm/PoseForm）的骨骼 key 不命中 → 落入 `missed` 平铺安全网：`list.addAll(missed)` 原始键 + `String::compareToIgnoreCase` 字母序，且无 label 映射 → 两个症状（旧样式 + 原始 id）。
- 2.5 迁移提交 b66871f5 改过 `UIAnchorKeyframeFactory`（105 行），用新组件替换了 9fe77292（BoneHierarchy 为主源 + getLabels(false) 显示名 + resolveId 兜底）的实现 → 显示名回归。
- MobForm 矩阵键 == 骨骼稳定 id：`MobRenderContext.completeMatrices` 以 `canonicalBone.getId()` 写入（`MobRenderContext.java:250-271`）；`TrackerFrame.resolve` 直接 `map.get(group)` 查矩阵键 → 选择器值语义不变，树节点 key 用 `StringUtils.combinePaths(formPath, bone.id())` 即可命中 keySet。
- `FormUtilsClient.getBoneHierarchy(Form)`（FormUtilsClient.java:223）对 ModelForm/MobForm/PoseForm 均返回 BoneHierarchy（ModelForm 的来自 IModel，Mob 的来自 VanillaBoneHierarchy）。
- `ModelForm implements PoseForm`——分支顺序必须 ModelForm 优先，再 `else if (PoseForm)`。
- 本次会话已完成的前置能力：`UIBoneTreeList.setHierarchy(BoneHierarchy, hidden, labels)` 与 parentId 分组构建（前序不变量已验证）。

## Requirements

- `UIBoneTreeList.formNodes` 在 ModelForm 分支后增加 PoseForm 分支：从 `FormUtilsClient.getBoneHierarchy(form)` 构建骨骼子树，节点 key = `combinePaths(formPath, bone.id())`，仅当 key 在矩阵键集合中才保留（否则子节点上提，与 `formBoneNodes` 同构），标签 = `getLabels()` 显示名（与 pose 编辑器 / filter track 一致，即 9fe77292 语义）。
- 安全网兜底键显示"最后路径段"而非完整内部键（非骨骼附件点可读性）。
- 值语义不变（选择器仍回传矩阵键），`TrackerFrame.resolve` 与运行时解析不受影响。

## Acceptance Criteria

- [ ] 摄像机编辑器追踪器剪辑指向 MobForm：附件选择器骨骼按层级树显示（连接线 + 缩进），显示名（如 `inner_armor_rightArm`）而非内部 id，顺序为层级序。
- [ ] 搜索时平铺显示匹配项（复用现有 flat 逻辑）。
- [ ] ModelForm 附件选择器行为不变（track name 标签 + 树形，回归目检）。
- [ ] 子表单（body part 内嵌 MobForm/PoseForm）的骨骼在父表单路径下嵌套显示。
- [ ] 编译通过（`compileClientJava`），全量 `build`（test-strict）通过。
- [ ] `TrackerFrame.resolve` 运行时查键不受影响（值仍为矩阵键）。

## Out of Scope

- 不改 `UIAnchorKeyframeFactory.displayAttachments` 的调用结构（fix 在共享组件 `fillAttachments`，对所有附件选择器统一生效）。
- 不改 ModelForm 的 `formBoneNodes` 路径与 track name 标签。
- 不做新的选择器交互特性。
