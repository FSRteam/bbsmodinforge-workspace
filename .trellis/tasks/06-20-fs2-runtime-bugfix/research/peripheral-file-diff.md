# 研究：外围渲染文件逻辑差异比对

- **查询**: 比对 4 组文件的控制流差异（new NeoForge 1.21.1 vs Fabric 1.21.1 参考）
- **范围**: 内部（文件读取 & 逻辑分析）
- **日期**: 2026-07-03

## 文件 1: UIFilmPreview.java

### render() 方法差异

| new 行号 | 参考行号 | 差异描述 | 疑似影响 |
|---------|---------|--------|--------|
| 358-363 | 无 | **new 版添加诊断日志**: `LOGGER.info("[BBS-DIAG] preview.render: texture={} ({}x{}), drawFbo={}, customSize={}")` — 记录纹理 ID、分辨率、帧缓冲绑定点、自定义大小标志 | 日志信息，不影响渲染逻辑，有助于诊断闪烁问题 |
| 356 | 348 | **context.batcher.flush() 位置一致** | 无实质差异 |
| 367 | 352 | **texturedBox() 参数完全相同** | 已对齐 |

### renderCursor() 方法差异（关键）

| new 行号 | 参考行号 | 差异描述 | 疑似影响 |
|---------|---------|--------|--------|
| 476 | 461 | API 映射差异: `Minecraft.getInstance().gameRenderer.getMainCamera()` vs `MinecraftClient.getInstance().gameRenderer.getCamera()` | 已排除（机械 API 映射）|
| 477 | 462 | 矩阵栈类型: `Matrix4fStack` (NeoForge JOML 原生) vs `org.joml.Matrix4fStack` (参考写法) — **两版本实际类型相同**，只是导入不同 | 已对齐 |
| 481 | 466 | **模型视图矩阵获取差异** | |
| | | new: `stack.mul(context.batcher.getContext().pose().last().pose())` | new 版使用 NeoForge pose() API |
| | | 参考: `stack.mul(context.batcher.getContext().getMatrices().peek().getPositionMatrix())` | 参考使用 Fabric getMatrices() API |
| | | **结果**: 两版都将当前 pose 矩阵乘入 stack — 逻辑一致 | 已对齐 |
| 483-484 | 468-469 | **旋转轴与方法差异** | |
| | | new: `Axis.XN.rotationDegrees() / Axis.YP.rotationDegrees()` | NeoForge Axis API |
| | | 参考: `RotationAxis.NEGATIVE_X.rotationDegrees() / RotationAxis.POSITIVE_Y.rotationDegrees()` | Fabric RotationAxis API |
| | | **结果**: 逻辑完全相同，只是 API 名称不同 | 已对齐 |
| 483 | 468 | **摄像机角度获取** | |
| | | new: `mcCamera.getXRot()` (NeoForge) | 已映射 |
| | | 参考: `mcCamera.getPitch()` (Fabric) | 已映射 |
| 484 | 469 | new: `mcCamera.getYRot()` vs 参考: `mcCamera.getYaw()` | 已映射 |

**结论**: renderCursor() 的所有差异都是机械 API 映射，控制流完全一致（都是取摄像机旋转 → 矩阵变换 → 十字线渲染）。

---

## 文件 2: Framebuffer.java

### bind() / unbind() 方法差异

| new 行号 | 参考行号 | 差异描述 | 疑似影响 |
|---------|---------|--------|--------|
| 135 | 134 | new: `GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.id)` | 使用 NeoForge GlStateManager 包装 |
| | | 参考: `GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.id)` | 直接 OpenGL 调用 |
| | | **函数效果相同**: 都绑定帧缓冲到 GL_FRAMEBUFFER 目标，但 new 版通过状态管理器（可能有状态缓存）| 可能影响（见下）|
| 140 | 139 | unbind() 方法同上 | 相同潜在影响 |

### apply() 方法差异（关键）

| new 行号 | 参考行号 | 差异描述 | 疑似影响 |
|---------|---------|--------|--------|
| 100 | 99 | **视口设置差异** | |
| | | new: `GlStateManager._viewport(0, 0, texture.width, texture.height)` | NeoForge 状态管理器 |
| | | 参考: `GL11.glViewport(0, 0, texture.width, texture.height)` | 直接 OpenGL 调用 |
| | | **差异**: GlStateManager._viewport() 可能涉及状态追踪，但最终都设置视口矩形 | **潜在差异**: new 版可能跳过或延迟视口设置如果状态已缓存 |

**注**: GlStateManager 通常缓存状态以避免冗余 GL 调用，但在帧缓冲变换时，如果缓存不同步，视口可能不正确。

### clear() 方法

| 行号 | 差异 |
|------|------|
| 105-131 | **已对齐** — 两版本 clear() 逻辑完全一致（advancedClearing 分支、glClearBufferfv 调用序列相同）|

---

## 文件 3: Texture.java

### 全文比对

| 行号范围 | 结果 |
|---------|------|
| 整个文件 | **已对齐** — new 和参考版本 Texture.java 完全相同（包括 bind/unbind/setSize/uploadTexture/generateMipmap 等所有方法）|

---

## 文件 4: UIDataDashboardPanel.java

### requestData() 方法差异（关键）

| new 行号 | 参考行号 | 差异描述 | 疑似影响 |
|---------|---------|--------|--------|
| 588-614 | 534-537 | **数据加载逻辑大幅不同** | |
| new | new | 添加版本跟踪和编辑器禁用逻辑: | |
| 590-591 | 无 | `int requestVersion = ++this.dataRequestVersion;` + `DataTab requestTab = ...` | 版本管理，防止多请求冲突 |
| 593 | 无 | `this.markDataLoading(requestTab, id);` | 更新 UI 标签页状态 |
| 594 | 无 | `this.editor.setEnabled(false);` | **禁用编辑器** | **关键**: new 版在加载中禁用编辑，防止用户编辑未加载的数据 |
| 596-613 | 535-536 | 回调中添加条件检查: | |
| 598-608 | 无 | 版本检查 + 标签页检查 + 编辑器重新启用 | 防止陈旧数据触发 fill() |
| 610 | 无 | 回调成功时 `this.editor.setEnabled(true);` | 加载完成后重新启用编辑 |
| 612 | 536 | `this.fill((T) data);` | 数据填充（位置相同但被版本检查保护）|

**参考版本** (534-537): 简单的单行实现，直接回调 fill()，无任何加载状态管理。

| 行号 | 差异描述 | 影响 |
|-----|--------|------|
| **fill() 方法** | **两版本相同** (631 vs 541) | 已对齐 |
| 658 vs 568 | `this.savingTimer.mark(...)` 位置/内容相同 | 已对齐 |

### open() 方法

| new 行号 | 参考行号 | 结果 |
|---------|---------|------|
| 744-754 | 654-... | **已对齐** — 两版本 open() 逻辑相同（初始化 savingTimer）|

---

## 文件 5: UIReplaysListPanel.java

### 全文比对

| 行号范围 | 结果 |
|---------|------|
| 1-93 | **已对齐** — new 和参考版本 UIReplaysListPanel.java 完全相同 |
| | 唯一差异: 参考版第 14 行导入 `Colors`，new 版无此导入（但 new 版也无此导入使用，故无影响）|
| | **构造函数、render()、updateButtonsState() 全部相同** |

### UIReplayList.java 差异

- **参考版本**: 存在 `UIReplayList.java` (~96 行开始的大型文件，处理列表项管理、选择、右键菜单等)
- **new 版本**: 在 new 工作区中 **找不到 UIReplayList.java 源文件**（只有编译后的 .class 文件）

**结论**: UIReplayList.java 在 new 版本中可能：
1. 被重构为内联代码
2. 被合并到其他文件
3. 本体未同步

---

## 总结: 与预览闪烁相关的差异

### 直接相关
1. **Framebuffer.apply() 中的视口设置**: new 版使用 `GlStateManager._viewport()` 而非直接 `GL11.glViewport()`
   - 风险: 如果 GlStateManager 的状态缓存与实际 GL 状态不同步，视口矩形可能不正确
   - 症状: 预览纹理显示范围错误 → 闪烁或黑屏
   - **建议**: 检查 Framebuffer.apply() 调用频率，或在 apply() 前强制状态同步

2. **Framebuffer.bind/unbind() 使用状态管理器**: 类似风险
   - 如果帧缓冲绑定状态缓存错误，后续渲染无法写入正确的帧缓冲
   - **建议**: 验证 GlStateManager 是否正确跟踪了帧缓冲绑定

### 间接相关
3. **UIFilmPreview.render() 中新增诊断日志**: 帮助定位但不是根因
   - 建议: 启用此日志，观察 drawFbo 和 texture.id 的值变化

4. **UIDataDashboardPanel.requestData() 添加了数据加载管理**: 
   - new 版禁用编辑器直到加载完成，参考版本无此保护
   - 风险: 如果加载回调延迟或失败，编辑器可能永久禁用 → 列表空白（见下）

---

## 总结: 与空白影片列表相关的差异

### 直接相关
1. **UIDataDashboardPanel.requestData() 的数据加载版本管理**:
   - new 版: 添加了 `dataRequestVersion` 跟踪，在版本不匹配时提前返回
   - 参考版: 无此保护，所有回调都会执行 fill()
   - 风险: **如果版本检查过于严格，可能导致 fill() 永不被调用** → 列表显示为空
   - 症状: 
     - 用户点击影片列表中的影片
     - requestData() 被调用，editor.setEnabled(false)
     - 加载回调返回数据，但版本检查失败 (603 行条件: requestTab 不匹配)
     - fill() 不被执行
     - editor 永久禁用，列表项无法加载
   - **建议**: 检查 switchTab() 和 requestData() 的调用时序，确保 requestTab 与 currentTab 同步

2. **UIReplayList.java 在 new 版本中缺失**:
   - 如果 UIReplayList 包含列表项加载和选择回调的逻辑，缺失或重构错误会导致列表无法显示
   - **建议**: 检查 UIReplaysListPanel 的 replays 字段初始化 (40 行: `new UIReplayList(...)`)，确保列表实现完整

---

## 关键建议

### 优先级 1: 立即检查
1. **Framebuffer.apply() 中的视口状态**:
   ```java
   // new 版 (100 行)
   GlStateManager._viewport(0, 0, texture.width, texture.height);
   this.bind();
   
   // 建议改为显式状态同步:
   GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.id);
   GL11.glViewport(0, 0, texture.width, texture.height);  // 直接调用
   ```

2. **UIDataDashboardPanel.requestData() 的版本检查逻辑**:
   - 添加日志验证 requestVersion 何时被自增
   - 检查 603 行条件是否过于严格导致 fill() 被跳过

### 优先级 2: 验证
1. 编译参考版本（Fabric），对比渲染纹理 ID 和视口大小日志
2. 检查 UIReplayList 在 new 版本中的完整实现

