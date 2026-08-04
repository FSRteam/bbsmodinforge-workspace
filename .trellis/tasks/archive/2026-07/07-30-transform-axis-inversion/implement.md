# 3D预览 G/S/R 轴方向反转 - 实现计划

## 步骤

### Step 1: 分析 GL 状态恢复完整性
- 检查 `FormUtilsClient.renderUI()` → `finishRenderUI()` 的 GL 状态恢复
- 检查 `UISelectorList.renderElementPart()` 的 viewport 保存/恢复

### Step 2: 后备路径防御（UIPropTransform.render()）
- 文件: `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/UIPropTransform.java`
- 约 line 1707-1743 的后备路径
- Y 轴增加 `-dy` 分量，Z 轴增加 `dy` 分量

### Step 3: UIPropTransform - applyTranslateDelta 修复
- 文件: 同上
- `applyTranslateDelta()` 中 Z 轴 `-= units` → `+= units`（对齐 X/Y）

### 验证命令
```bash
cd new && ./gradlew compileClientJava
```
