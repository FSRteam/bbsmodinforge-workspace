# Research: 闪烁根因 —— codex 丢弃了参考的 InverseView 矩阵体系

> **⚠️ 2026-07-03 晚间修订：本文档的核心结论已被新证据大幅降级，勿直接采信。**
> 用户提供决定性信息：①本仓库 commit `ebe75984`（cnb/main 远程版，2026-06-08）**无闪烁**——而它同样没有 InverseView 体系、同样用 camera-relative 方案 → **InverseView 缺失不是闪烁根因**（仅 `ViewRotationMat`/Iris 阴影线索仍可能与 bug #4 相关，须以 FS2 1.20.1 主线为基准验证）。②FS2 的 fabric **1.21.1 分支可能自带 bug，以 1.20.1 主线为准**——本文档基于 1.21.1 分支的"对齐移植"方案已废弃。
> 新策略与排查结论见下方《好坏版本 diff 围猎（2026-07-03 晚）》一节。历史正文保留仅供参考。

## 好坏版本 diff 围猎（2026-07-03 晚，当前有效结论）

基准：`ebe75984`（好，无闪烁）→ `257ec5f2`（仅 Iris WorldRendererMixin 9 行）→ `ae39717b`（codex 全部 FS2 迁移工作 + 6/21 修复轮一起提交，闪烁在此已存在）→ 工作区（6/28-29 加固 + 日志，仍闪）。

**6 个 bug 全部由 codex 的 FS2 迁移改动引入**（该批改动以未提交状态存在至 ae39717b 才入库；ebe75984 里连 `flushCustomFramebuffer` 都不存在）。

渲染链逐项排查（好坏等价，已排除）：StencilFormFramebuffer、Framebuffer、StencilMap、Texture、BBSRendering 拷贝路径（工作区已恢复好版本形态：无条件 toggle + copyTexSubImage、appear=setCustomSize(true)、无 no-film 分支）、renderPanelBackground、MatrixStackUtils（仅零值跳过优化）、BaseFilmController（无 GL 操作差异）、BBSClientNeoEvents（等价重构）、UIFilmPreview（仅菜单）、GizmoInteraction.update/Gizmo.setViewport（纯逻辑）、深度函数流（当前版所有路径均有恢复）、HUD 渲染序列。

**唯一确证的"好→坏"GL 改动**：`renderPickingPreview` 投影排序 `DISTANCE_TO_ORIGIN`→`ORTHOGRAPHIC_Z`（ae39717b 误改，当时以为对齐 master；**已于 2026-07-03 恢复为 DISTANCE_TO_ORIGIN**，编译通过，jar 交用户验证中）。

未细查的剩余嫌疑（若排序恢复后仍闪）：UIFilmPanel FS2 dock 布局 939 行中的状态振荡、Gizmo 985 行非 sphereHighlight 部分、ae39717b 引入的其他每帧渲染路径（IK/物理/ModelInstance/item renderer）、257ec5f2（仅 Iris 下生效）。下一步定位手段：工作区 [BBS-DIAG] 日志（已加采样节流，FBO 不匹配时无条件报警）——用户测试后取 latest.log 分析黑帧时刻数据。

---

（以下为 2026-07-03 上午的历史分析，核心结论已被上方修订取代）


- **Date**: 2026-07-03
- **结论置信度**: 高（结构性证据完整；最终以游戏内验证为准）
- **参考**: `temp/bbs-fs-1.21.1-ui/`（FS2 官方 Fabric 1.21.1 分支，行为正确）

## 核心发现

MC 1.21.1 移除了 `RenderSystem.setInverseViewRotationMatrix` 全局。FS2 官方 1.21.1 分支为此自制了一套替代体系；**codex 迁移时没有移植这套体系，而是发明了自己的 "camera-relative PoseStack" 方案**（`new/BBSRendering.java:569` 注释自证：`BBS world renderers use camera-relative PoseStacks; the view matrix stays in RenderSystem`）。两套方案在普通世界渲染 pass 下都能被修到大致工作，但在 **stencil 选取 pass**（从 UI/Screen 渲染上下文发起的离屏世界渲染，鼠标在预览视口内且场景有 actor 时每帧执行）交界处爆发——这正是闪烁"只在放置回放 actor 后 + 鼠标在视口内"出现的原因。

## 参考体系的组成（new 中全部缺失）

1. **`graphics/InverseView.java`**（new 中不存在）：静态 Matrix3f holder。javadoc：*"mirroring the global that RenderSystem maintained before 1.21.1 removed it. The world render pass feeds it from the game camera, while UI model previews override it with their own camera before rendering."*
2. **`BBSRendering.camera`**（`public static final Matrix4f camera`，参考:71，new 中不存在）：世界渲染实际 view 矩阵的缓存。
3. **喂值点 A** — `WorldRendererMixin.onSetupFrustum`（参考 WorldRendererMixin:80-84，new 的 WorldRendererMixin 无此注入）：
   ```java
   @Inject(method = "setupFrustum", at = @At("HEAD"))  // Mojmap 名待查：LevelRenderer.prepareCullFrustum
   BBSRendering.camera.set(positionMatrix);
   ```
4. **喂值点 B** — `BBSRendering.onWorldRender`（参考:478-481）：世界 pass 每帧
   ```java
   InverseView.set(new Matrix3f().rotation(worldRenderContext.camera().getRotation()));
   ```
5. **喂值点 C** — `UIFilmController.renderPickingPreview`（参考:1229-1242）：stencil pass 前
   ```java
   RenderSystem.setProjectionMatrix(this.panel.lastProjection, VertexSorter.BY_Z);
   InverseView.set(new Matrix3f(BBSRendering.camera).invert());
   worldStack.push(); worldStack.loadIdentity();
   MatrixStackUtils.multiply(worldStack, BBSRendering.camera);   // ← new 错用 this.panel.lastView
   this.renderStencil(...); worldStack.pop();
   MatrixStackUtils.restoreMatrices();
   ```
6. **消费方**（完整清单，均需对照参考对齐矩阵逻辑）：
   - `cubic/render/vao/ModelVAORenderer.java` — `shader.getUniform("ViewRotationMat")` ← `InverseView.get()`（Iris shader 的视图旋转 uniform；**new 缺失此设置 = bug #4 阴影/受光在 Iris 光影下错误的根因**；new 的 model.vsh `fixNormal`/`NormalMat` 本身已恢复，非 Iris 路径已对）
   - `forms/renderers/ParticleFormRenderer.java`（new 现为 isCameraRelativeWorld/context.camera.view 方案）
   - `forms/renderers/TrailFormRenderer.java`
   - `forms/renderers/VanillaParticleFormRenderer.java`
   - `ui/framework/elements/utils/UIModelRenderer.java`（UI 模型预览喂/还原 InverseView）
   - `ui/particles/UIParticleSchemeRenderer.java`
   - `utils/MatrixStackUtils.java`（cache/restore 与 InverseView 的关系待逐行比对）

## 已排除的方向（避免再绕路）

- **mixin 注入点/时机**：new 与参考的 `GameRendererMixin`（renderWorld/renderLevel HEAD+RETURN、hudHidden/hideGui FIELD）与 `InGameHudMixin`（HEAD cancel + TAIL → onRenderBeforeScreen）结构等价，仅命名映射差异。唯一差异：参考 hudHidden 注入带 `require = 0`，new 没有（无行为差异）。
- **OrbitCamera**：与参考完全对齐（平滑系统 targetPosition/syncTarget 是参考自带，非 codex 发明——旧研究文档 orbit-camera-regression.md 此结论有误）。
- **model.vsh**：`fixNormal = normalize(NormalMat * Normal)` 已恢复；参考多出的 `uniform mat3 IViewRotMat` 是死声明（Java 端与 vsh main 均未使用）。
- 工作区已做的加固（无条件 toggle、glCopyTexSubImage2D、GlStateManager 化、batcher.flush、try/finally）方向无害但未命中根因；**参考在 renderStencil 收尾没有 batcher.flush 也不闪**。

## 其他相关差异（顺带记录）

- `BBSRendering.flushCustomFramebuffer()`（new:208）已无任何调用方，死代码，清理时删除。
- 参考 renderStencil 开头：viewport 不在内 → `this.stencil.clearPicking()` 后 return（工作区把 clearPicking 加在了 entity==null 分支——位置需精确对齐参考）。
- 参考 renderStencil 收尾：`stencil.pick → stencil.unbind → getFramebuffer().beginWrite(true)`（= `getMainRenderTarget().bindWrite(true)`），无 try/finally——对齐时可保留 try/finally 加固但语序须一致。
- 参考 `renderPickingPreview` 后半段用 `BBSShaders.getPickerPreviewProgram()` 的 `Target`/`HighlightColor` uniform + `context.batcher.texturedBox(getPickerPreviewProgram, ...)` 绘制高亮预览；gizmo 只有 `this.gizmo.update(context)`，**没有** renderSphereHighlight（工作区禁用它正确）。
- `UIFilmPanel.lastView` 更新源：参考 `context.positionMatrix()`（3095）vs new `context.modelViewMatrix()`（2997）——renderPickingPreview 改用 BBSRendering.camera 后，lastView 的剩余用途需检查并对齐。

## 修复方案（成套移植，不可混搭）

以参考为基准把整套 InverseView 体系移植进 new（保留 Mojmap/NeoForge API 映射），同时移除 codex 的 camera-relative 方案分支。涉及：新增 InverseView 类、BBSRendering.camera 字段 + 两处喂值 + WorldRendererMixin 注入（Mojmap 方法名 `prepareCullFrustum`，以编译为准）、renderPickingPreview/renderStencil 对齐、6 个消费文件对齐。风险：codex 方案下已游戏内验证过的粒子朝向/世界 form 矩阵（commit f75b72b5、258a814a、257ec5f2）会被替换为参考实现——参考实现本身自洽正确，但替换后这些场景需要重新游戏内验证。
