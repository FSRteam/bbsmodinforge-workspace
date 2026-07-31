# MobForm 重复渲染与 Gizmo 跟随 - 实现计划

## 步骤

### Step 1: 检查 UISelectorList GL 状态恢复
- `new/src/client/java/mchorse/bbs_mod/ui/selectors/UISelectorList.java`
- `renderElementPart()` 中的 viewport/depth/lighting 保存恢复

### Step 2: 检查 UIPickableFormRenderer stencil 渲染隔离
- `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/utils/UIPickableFormRenderer.java`
- stencil pass 中 viewport 和 projection 的隔离性

### Step 3: Gizmo 矩阵隔离
- `Gizmo.java` captureRenderMatrix 是否应在不同 UIPickableFormRenderer 实例间隔离

### 验证命令
```bash
cd new && ./gradlew compileClientJava
```
