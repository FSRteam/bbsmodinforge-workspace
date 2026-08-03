# Implement — MobForm 骨骼列表适配 2.5 层级树显示样式

目标仓库：`new/`（嵌套 repo，FSRteam/bbs-FSR，分支 master）。路径均相对 `new/`。

## 实施清单（有序）

1. **`src/client/java/mchorse/bbs_mod/ui/utils/bones/UIBoneTreeList.java`**
   - 新增 `setHierarchy(BoneHierarchy hierarchy, Predicate<String> hidden, Map<String, String> labels)` 重载：
     按 parentId 分组（保持 boneIds 序）→ 根 = parentId 空/不在集合 → 递归 `emit()`；
     `treeLabel = fullLabel = labels[id]`（缺省回退 `bone.id()`）。复用现有 `emit(Node…)`。
2. **`src/client/java/mchorse/bbs_mod/ui/utils/pose/UIPoseEditor.java`**
   - `fillGroups(BoneHierarchy, reset)`：labels 计算一次 → `groups.list.setHierarchy(hierarchy, null, labels)`
     （替换 `setHierarchy(null, null)`）→ `setSource(boneIds, labels, false)` → `filter(reset)`。
   - 删除 3 参 `fillGroups(BoneHierarchy, reset, boolean hierarchicalLabels)` 变体（确认无外部调用后）。
   - 同步删除 `getLabels(...)` 调用中的 boolean 实参（若签名改）与 `fillInGroups(BoneHierarchy…)` 残留。
3. **`src/client/java/mchorse/bbs_mod/forms/renderers/BoneHierarchy.java`**
   - `getLabels(boolean indent)` → `getLabels()`，删除 indent 分支与 `"  ".repeat(depth)`。
4. **`src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory.java`**
   - MobForm 分支：`List<String> bones = renderer.getBones()` → `BoneHierarchy hierarchy = FormUtilsClient.getBoneHierarchy(mobForm)`；`fillGroups(bones, false)` → `fillGroups(hierarchy, false)`。
5. **`src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIGlintKeyframeFactory.java`**
   - `setSource(boneIds, labels, false)` 前加 `bones.list.setHierarchy(this.hierarchy, null, labels)`。
6. **`getLabels()` 签名机械更新**：FormUtilsClient、UIReplaysEditorUtils（3 处）、UIGlintKeyframeFactory、
   UIPoseEditor 内部（搜索 `getLabels(` 全覆盖）。
7. **测试**：检查 `PoseFormRegressionSourceTest` 及 bone/pose 相关测试是否需要适配（`getLabels` 签名变更
   若被测试引用则同步改）。

## 验证命令

- 迭代编译（快）：`cd new && .\gradlew.bat --no-daemon compileClientJava`
- 全量（提交前）：`cd new && .\gradlew.bat --no-daemon build`（test-strict，失败即整体失败）
- 运行时（手动）：MobForm 面板骨骼列表树形显示；film pose 关键帧编辑器 MobForm 骨骼树形 + 层级序；
  glint 关键帧骨骼树形；搜索平铺/恢复；UIModelFormPanel、IK/物理/约束面板回归目检。

## 风险文件 / 回滚点

- `UIBoneTreeList.java`（共享组件：只新增重载，不改既有方法 → 低风险）
- `UIPoseEditor.java`（fillGroups 签名清理：删除前 grep 确认无外部 3 参调用）
- `BoneHierarchy.getLabels` 签名变更（机械替换，编译期兜底）
- 每步完成后可独立编译；整包改动为单一提交，可整体 revert。

## 提交前检查

- [ ] client 编译通过；`build`（含测试）通过
- [ ] 无 `getLabels(true)` / `hierarchicalLabels` 残留
- [ ] IModel 路径（model 面板、IK/物理/约束、拾取弹窗）未改动文件确认（git diff 复查）
- [ ] 删除工作区 `.tmp/upstream-*` 参考克隆（或确认其被 .gitignore 覆盖）
