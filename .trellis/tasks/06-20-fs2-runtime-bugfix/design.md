# 技术设计：回放/影片6个运行时bug修复

策略：**以 FS2 1.21.1（`temp/bbs-fs-1.21.1-ui`）为准重新对齐**被 codex 改坏的逻辑，逐文件保留 new 已有的正确 NeoForge API 映射。

NeoForge↔Fabric 映射速查（移植参考代码时统一套用）：
`MinecraftClient→Minecraft`、`MatrixStack→PoseStack`、`VertexConsumerProvider→MultiBufferSource`、`Identifier→ResourceLocation`、`ShaderProgram→ShaderInstance`、`VertexFormats→DefaultVertexFormat`、`WorldView→LevelReader`、`entity.getWorld()→entity.level()`、`getRenderTickCounter().getLastFrameDuration()→getTimer().getRealtimeDeltaTicks()`、`Identifier.of(ns,path)→ResourceLocation.fromNamespaceAndPath(ns,path)`。

---

## 簇 A：闪烁 / 黑屏 / ESC 卡死 / 影片丢失（bug #1 #2 #3）

依据：`research/framebuffer-flicker-chain.md`。

### A1. BBSRendering framebuffer 状态机对齐（核心）
- 文件：`new/src/client/java/mchorse/bbs_mod/client/BBSRendering.java`
- 现状：codex 引入了 `wasFramebufferActive` 条件式 toggle（约 459、541-544 行）+ `exportFramebuffer` 复杂 FBO/blit 管理（520-539 行 `glBlitFramebuffer`），与参考"无条件 `toggleFramebuffer(false)` + 简单纹理拷贝（`glCopyTexSubImage2D`）"不一致 → framebuffer on/off 状态失同步，UI 事件触发 `flushCustomFramebuffer()` 时状态错乱 → 快速 on/off 形成闪烁；退出路径状态未复位 → 主渲染目标绑死在失效 FBO → 黑屏。
- 设计：对照参考 `BBSRendering` 的 `setCustomSize` / `toggleFramebuffer` / `onRenderBeforeScreen`(worldBegin/worldEnd) / `copyPreview` 流程，移除 `wasFramebufferActive` 条件分支，恢复无条件 toggle-off 与简单拷贝路径。保留 new 中正确的 NeoForge `RenderTarget`/`getMainRenderTarget` 调用形态。
- 注意：`exportFramebuffer`/`glBlitFramebuffer` 属 migration-status 标记的"待确认 master-only"项——本次以"恢复 1.21.1 行为修复闪烁"为先；若导出缩放确需 blit，则只在导出路径保留、与预览路径解耦。

### A2. 退出影片/关闭面板的渲染状态复位（黑屏 + ESC 卡死）
- 文件：`new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`
- 现状：close/disappear 路径在 `setCustomSize(false)` 之前调用 `flushCustomFramebuffer()`，状态失同步时强制 blit。
- 设计：对照参考 UIFilmPanel 关闭/隐藏路径，确保退出时按正确顺序 `setCustomSize(false)` 复位渲染状态、不在退出路径强制 flush。`applyPreviewSizeToBBS`（约 2009 行）的调用频率与触发条件对齐参考，避免放置回放目标时反复 `setCustomSize(true/false)`。

### A3. 影片持久化（影片丢失）
- 文件：`new/src/client/java/mchorse/bbs_mod/film/Films.java` + UIFilmPanel/数据面板保存路径。
- 现状：退出/卡死导致未正常 shutdown，影片 json 未写盘。
- 设计：A1/A2 修复后退出流程恢复正常即可正常保存；额外核对参考在切换/关闭影片时是否有显式 save/markDirty 调用，new 缺则补齐。

### A4. 空白影片列表
- 文件：影片列表 UI（`ui/film` 下列表 panel）。
- 设计：对照参考列表数据源加载（`fillData`/`setData`/加载影片）初始化时机，修正 new 在"未创建影片"时的列表构建。

### 簇 A 验证：放置回放目标不闪、退出不黑屏、ESC 不卡、重进影片在、空列表正常。

---

## 簇 B：阴影 / 模型受光（bug #4）

依据：shadow 证据收集。掉落阴影 `renderShadow`（`ModelBlockEntityRenderer` + `EntityRendererDispatcherInvoker.bbs$renderShadow`）链路完好；真正差异在顶点着色器法线处理。

- 文件：`new/src/main/resources/assets/bbs/shaders/core/model.vsh`
- 现状（new）：`minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color)` —— 直接用原始 `Normal`。
- 参考（1.21.1）：
  ```glsl
  uniform mat3 IViewRotMat;            // ← new 被删
  ...
  vec3 fixNormal = normalize(NormalMat * Normal);            // ← new 被删
  vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, Color);
  ```
- 设计：恢复参考的 `IViewRotMat` uniform 声明与 `fixNormal = normalize(NormalMat * Normal)`，把 `fixNormal` 传入光照。
- 校验点：确认 NeoForge 下 `NormalMat`/`IViewRotMat` uniform 可用（对照 `BBSShaders.java` 的 `ShaderInstance` 定义与 `model.json` shader 定义里 uniform 列表）；若 NeoForge shader json 未声明 `IViewRotMat`，需同步在 shader 定义补上或采用 NeoForge 等价 uniform。
- 验证：模型受光方向/明暗与 FS2 1.21.1 一致（侧光下模型不再"扁平/错误受光"）。

---

## 簇 C：轨道相机模式（bug #5）

依据：`research/orbit-camera-regression.md`。

- 文件：`new/src/client/java/mchorse/bbs_mod/camera/OrbitCamera.java`、film controller（`BaseFilmController`/orbit controller）、`new/src/client/java/mchorse/bbs_mod/ui/Keys.java`。
- 现状：
  1. 相机平滑 `applySmoothing()` 新增后，进入轨道模式未同步 `targetPosition`/`targetRotation`，首帧插值跳变/失效。
  2. `Keys.java` 多出孤立的 `FLIGHT_ORBIT=Z`，与 `FILM_CONTROLLER_TELEPORT_ORBIT=Z` 撞键。
- 设计：
  - 进入轨道模式时显式同步目标状态（对照参考调用 `orbit.setup(camera)` 之类），保证 `getReplayPivot()`/pivot 有效后再插值。
  - 核对 `FILM_CONTROLLER_TELEPORT_ORBIT`（Z 传送到录制点）逻辑完整、未被新增键抢占。
  - 移除/纠正未使用且冲突的 `FLIGHT_ORBIT`（及确认 `FILM_CONTROLLER_ATTACH_ORBIT` 是否需要保留并完整接线）。
- 注意：此簇置信度中等，须先用 `git diff` 精确定位黑屏修复改动的 camera/film hunk，再最小化对齐，避免误删有效功能。验证以游戏内为准。

---

## 簇 D：行为编辑器(Action Editor) 按钮恢复（bug #6）

依据：参考 `UIFilmPanel` 有完整 `openActionEditor` 接线，new 全缺。

- 文件：`new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`
- 参考关键行（移植目标）：
  - 字段：`public UIIcon openActionEditor;`（参考 132）
  - 创建：`this.openActionEditor = new UIIcon(Icons.ACTION, (b) -> this.showPanel(this.actionEditor));`（参考 413）
  - 尺寸/tooltip：`.wh(FILM_TOP_BAR_BUTTON_SIZE, ...).tooltip(UIKeys.FILM_OPEN_ACTION_EDITOR, Direction.BOTTOM);`（参考 422）
  - 加入顶栏：`this.topBarActions.add(..., this.openCameraEditor, this.openReplayEditor, this.openActionEditor, ...)`（参考 428）
  - 启用态：`this.openActionEditor.setEnabled(data != null);`（参考 2626）
  - 渲染：`this.renderTopBarButton(context, this.openActionEditor, this.actionEditor.isVisible());`（参考 2821）
- 设计：将以上 6 处按 new 现有顶栏结构补回（`actionEditor` 面板在 new 已存在：字段 125、创建 387、attach 389、setUndoId 494 等），保持与 new 顶栏其余按钮一致的命名/布局。校验 `Icons.ACTION`、`UIKeys.FILM_OPEN_ACTION_EDITOR`、`showPanel` 在 new 存在。
- 验证：影片顶栏出现行为编辑器按钮，点击打开 action 面板，编辑回放 actions 正常。

---

## 簇 E：日志清理（在 A–D 验证通过后）

- 删除 `new/src/client/java/mchorse/bbs_mod/client/BBSFlickerDiagnostics.java`。
- 移除全部 `BBSFlickerDiagnostics.ENABLED` 分支与 `[BBS-FLICKER]` 日志调用（BBSRendering ~11、UIFilmPanel ~15、UIReplaysEditor ~20、UIReplaysListPanel、UIDataDashboardPanel、UIReplayList 等，共 ~55 处），以及为诊断引入的辅助字段/方法（如 `lastCustomSizeSignature`、`stackSummary`、`framebufferInfo`、selectionSignature 等若仅诊断用）。
- 注意：移除时确保不连带删掉真实功能逻辑（很多插桩包在 `if (BBSFlickerDiagnostics.ENABLED){...}` 内，删 if 块即可）。

## 簇 F：F4 世界录制的编码与窗口呈现

- F4/F6 世界录制把当前世界画进 FSR 私有 framebuffer；导出纹理由 `onRenderBeforeScreen()` 在叠加录制提示前复制，编码器继续读取该纹理。
- 切回 vanilla `clientFramebuffer` 后，若 `customSize` 仍启用且没有 BBS `UIScreen`，必须把私有 framebuffer blit 到窗口大小，使玩家看到当前帧。否则编码正确但窗口保留录制前旧帧。
- BBS 影片/伪装编辑界面自己在局部区域绘制预览纹理；存在 BBS menu 时禁止全屏 blit，避免覆盖 UI。
- 录制提示在纹理复制后、窗口 blit 前绘制，因此只供操作者看，不进入视频。

## 兼容/回滚

- 每簇独立成 commit（用户确认后），便于回滚。
- 重新对齐 BBSRendering/UIFilmPanel 风险最高 → 先做、单独验证再进入下一簇。
- 保留 `research/` 与本设计作为回归依据。
