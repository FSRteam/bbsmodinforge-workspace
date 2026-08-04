# MobForm 加色法混合 - 实现计划

## 步骤

### Step 1: 确认行为
- 检查 `MobFormRenderer.render3D()` 中是否在 OpenGL blend func 级别检查 `additiveColor`
- 检查 `ModelFormRenderer`、`BillboardFormRenderer` 是否有 blend func 切换逻辑

### Step 2: 如需 GPU 级别混合模式
- 在 `MobFormRenderer.render3D()` 中添加：
```java
if (this.form.additiveColor.get()) {
    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
}
```
- 渲染后恢复：`RenderSystem.defaultBlendFunc()`

### 验证命令
```bash
cd new && ./gradlew compileClientJava
```
