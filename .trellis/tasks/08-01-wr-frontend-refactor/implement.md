# 前端架构重构：app.js 模块化拆分 + UX 痛点修复 — 实施计划（修订版）

有序、可勾选执行计划。每步小、可验证、可回滚。改动限于 `bbs-web-remote/src/client/resources/assets/bbswebremote/web/`、`index.html`、`build.gradle`、`src/webTest/`。

构建口径：`cd bbs-web-remote && ./gradlew <task>`。**内存红线：绝不信任管道退出码**——每次单独检查真实 exit code + 日志双确认（禁 `gradlew ... | tail`）。

DoD = `prd.md` AC 全部满足。

**核心策略（design.md 修订）**：搬类不搬方法。App 方法留在 App（webTest 直接测），独立类 + 工具函数（app.js 行 1-3757）按职责域搬入新文件。webTest require 行改为按序 require 全部文件，**用例体不动**。

---

## Step 0 — 基线绿 + 备份（回滚锚点）✅ 已做

- [x] `backup/app.js.orig`/`index.html.orig`/`styles.css.orig`/`build.gradle.orig` 已建。
- [x] `./gradlew verifyWeb` 基线全绿（exit 0，BUILD SUCCESSFUL）。

## Step 1 — error.js（错误人话化模块）+ R4 扩展

- [x] 新建 `web/error.js` IIFE：移入 `currentLocale`/`ERROR_MESSAGES`/`humanizeError` 到 `ns.Error`；`ERROR_MESSAGES` 扩展网络/HTTP 失败映射（R4）。
- [x] app.js：顶部 `const { currentLocale, ERROR_MESSAGES, humanizeError } = ns.Error;`，**删除 app.js 内对应定义**（34-211 行附近），其余引用不变。
- [x] build.gradle `webRuntimeScripts` 加 error.js；index.html `<script>` 加 error.js（在 app.js 前）。
- [x] 8 个 webTest require 行补 error.js（置于 protocol.js 后、app.js 前）。
- [x] 验证：`./gradlew verifyWeb` 真实 exit 0 + 8 测试全过。**回滚点 R1**。
- [ ] 后续：R4 的 `ERROR_MESSAGES` 扩展 + `reportError` 人话化留到实现 UX 时做（Step 7/8 前）。

## Step 2 — style.js（颜色/几何工具 + TintedImageCache）

- [x] 新建 `web/style.js` IIFE：移入 `rgba`/`colorsEqual`/`colorCss`/`averageColor`/`midpointVertex`/`triangleArea`/`colorSpread`/`affineFromTriangles`/`createWorkCanvas` + `TintedImageCache` → `ns.Style`。
- [x] app.js 顶部 `const { ... } = ns.Style;`，删 app.js 内对应定义（原 251-426 行）。
- [x] build.gradle：`webRuntimeScripts` 加 style.js；新增 `verifyWebErrorSyntax`/`verifyWebStyleSyntax` 逐一 `--check`；index.html/webTest 补 style.js。
- [x] 验证：`./gradlew verifyWeb` 真实 exit 0 + 8 测试全过。**回滚点 R2**。
- [x] **教训**：子代理曾改服务端 `ClasspathStaticHandler.java`（加 error.js/style.js 到 FIXED_RESOURCE_PATHS，声称会 400）——**已撤销**。核实：FIXED_RESOURCE_PATHS 是缓存优化非白名单，`getClasspathResource` 对非列表路径走 `loadClasspathResource` 正常加载（classpathRoot=assets/bbswebremote/web），不会 400/404。**本任务严格纯前端，禁碰 src/client/java/**。

## Step 3 — media.js（媒体渲染/传输类簇）

- [x] 新建 `web/media.js` IIFE（2148 行）：`CanvasRenderer`/`SurfaceCache`/`RtcSurfaceCache`/`ResourceCache`/`MediaMetrics`/`LinkFeedback`/`AdaptiveQualityController`/`HybridMediaTransport` + `flipDecodedVertically`/`disposeDecodedImage`/`boundedRtcIceServers`/`rtcUint32` + 依赖 `clamp`/`requestedMediaFps`/`normalizeCustomMediaSettings`/`CUSTOM_MEDIA_LIMITS`/`DEFAULT_CUSTOM_MEDIA`/`boundedText`。
- [x] app.js：删除 media 类区（原 437-2519）+ 上述依赖函数/常量；顶部解构取回（含 CUSTOM_MEDIA_LIMITS/DEFAULT_CUSTOM_MEDIA/boundedText 因 App 也引用）。app.js 5490→3378 行。
- [x] build.gradle：`webRuntimeScripts` 加 media.js + 新增 `verifyWebMediaSyntax` + verifyWebAppSyntax 索引改 [4]；index.html/webTest 补 media.js。
- [x] 验证：`verifyWeb` 真实 exit 0 + 8 测试全过（media-reconnect 曾因 DEFAULT_CUSTOM_MEDIA 缺失、webrtc-hybrid 曾因 boundedText 缺失失败，均已补解构/导出修复）。
- [ ] 注意：App 侧 media 编排方法（switchToRtcMedia/fallbackToJpegMedia 等）仍在 App，未搬（webTest 直接测，符合「搬类不搬方法」）。

## Step 4 — input.js（RemoteInput）

- [x] 新建 `web/input.js` IIFE（637 行）：`RemoteInput` 类 + `TOUCH_MODE`/`TOUCH_DRAG_THRESHOLD_PX` 常量 → `ns.Input`。依赖：Protocol/C/n.Protocol、clamp/ns.Media、`$`（getElementById，input 内自定义）。
- [x] app.js：删除 RemoteInput 区（原 408-1019）；顶部 `const { RemoteInput } = namespace.Input;`。app.js 3378→2767 行。
- [x] build.gradle：`webRuntimeScripts` 加 input.js + `verifyWebInputSyntax` + verifyWebAppSyntax 索引 [5]；index.html/webTest 补 input.js。
- [x] 验证：`verifyWeb` 真实 exit 0 + 8 测试全过（mobile-input 曾因缺 Protocol 引用失败，已补 `const Protocol = ns.Protocol;`）。
- [ ] 注意：App 侧输入编排方法（sendInputPayload/sendEphemeralInput 等）仍在 App；RemoteInput 经 `this.app` 调用它们。

## Step 5 — ui.js（FloatingToolbar + toast 队列 R2）

- [x] 新建 `web/ui.js` IIFE（162 行）：`FloatingToolbar` + `readToolbarState`/`writeToolbarState` → `ns.UI`；依赖 `TOOLBAR_STORAGE_KEY`、`clamp`(ns.Media)。
- [x] app.js：删除 readToolbarState/writeToolbarState(原 186-208) + FloatingToolbar(原 291-408)；顶部 `const { readToolbarState, writeToolbarState, FloatingToolbar } = namespace.UI;`；删除残留 catch 碎片。app.js 2767→2624 行。
- [x] build.gradle：`webRuntimeScripts` 加 ui.js + `verifyWebUiSyntax` + verifyWebAppSyntax 索引 [6]；index.html/webTest 补 ui.js。
- [x] 验证：`verifyWeb` 真实 exit 0 + 8 测试全过（floating-toolbar 曾因 app.js 残留 `} catch (ignored) {}` 语法错误失败，已清理）。
- [x] **R2 toast 队列/去重/历史已在 App 实现**（note 队列化 + count 合并 ×N + toastHistory 保留 50 条 + renderToastHistory + index.html 历史 DOM），无需新增，拆分后经 verifyWeb 守护。

## Step 6 — control.js（控制权文案 R5）

- [x] 新建 `web/control.js` IIFE：新增 `controlStateLabel()` 纯函数（permission/role/lease → 观看中/可控制/操作中）→ `ns.Control`。
- [x] App `updateControlUi` 调用 `controlStateLabel` 统一 roleStatus 文案 + tone（R5）。
- [x] app.js 解构取回；WebTest 导出补 `controlStateLabel`。
- [x] build.gradle：`webRuntimeScripts` 加 control.js + `verifyWebControlSyntax` + verifyWebAppSyntax 索引 [7]；index.html/webTest 补 control.js。
- [x] 新增 `webTest/control-state-label.test.js`（四态文案收敛，7 断言），注册 `testWebControlStateLabel`。
- [x] 验证：`verifyWeb` 真实 exit 0 + 9 测试全过。
- [ ] *注：`diagRole` 诊断文案保留原有详细描述（"协控 · 持有操作权"），session-lifecycle 锚定，与面向用户三态主线不冲突。

## Step 7 — R3 传输降级可见 + 诊断分级

- [x] **诊断分级**：index.html 诊断面板拆「基础」12 项 + 「高级」折叠区（内嵌 `<details class="diagnostics-advanced">`），所有 diag id 保持；styles.css 加高级区分隔样式。
- [x] **诊断偏好 localStorage**：`readDiagnosticsPreference`/`writeDiagnosticsPreference` + `bindDiagnosticsPreference`（App 方法）恢复/记住高级区展开态，外层收起时联动收起内层。
- [x] 验证：`verifyWeb` 真实 exit 0 + 9 测试全过。
- [x] **R3 传输降级可见**：核对既有实现已全覆盖——`setTransportOverlay` 支持 negotiating/switching/stalled/autoplay 四种可见文案 + 超时兜底（RTC_NEGOTIATION_TIMEOUT_MS/stall）；`fallbackToJpegMedia`/`switchToRtcMedia` 均有 note 提示（key="media-transport" 去重合并）；autoplay 被拒有「点击画面以启用视频」overlay + pointerdown 重试绑定；降级保留上一帧（fallback 不清 renderer 帧）；自动换档 `applyAdaptiveProfile` 防抖（ADAPTIVE_APPLY_DEBOUNCE_MS）+ `flushAdaptiveProfile` 同档短路不重连 + note key="adaptive-quality" 节流合并。经 media-reconnect/webrtc-hybrid 测试守护，无改动。
- [x] 验证：`verifyWeb` 真实 exit 0 + 9 测试全过。**回滚点 R7**。

## Step 8 — R5 触摸/手势/demo/空状态/聚焦

- [x] 聚焦提示可见：`canvasHint` sr-only → 可见 `.canvas-hint` 胶囊条，onFrame 隐藏、session reset 显示、文案按 demo/权限更新。
- [x] demo 退出入口：form-actions 加「退出演示」按钮（demo 时显隐）；`updateDemoUi()` 统一管理 demoButton/exitButton/emptyStateText/canvasHint；stopDemo 恢复连接态。
- [x] 空状态按权限引导：`emptyStateText` 按 controllerPermission 分支（可控制 vs 仅观看）；updateControlUi 联动 updateDemoUi。
- [x] file:// 标识：构造 file:// 或 ?demo=1 自动 startDemo → 状态条「离线演示」+ 连接面板 demo 态（既有 + updateDemoUi 补全）。
- [x] 修饰锁一键释放：mobileControls 修饰组加「清除」按钮，`refreshModifierClear`/`clearVirtualModifiers`（input.js）+ releaseAll 同步。
- [x] 首次触摸一次性手势说明：`touchHint` overlay + localStorage 关闭（TOUCH_HINT_STORAGE_KEY）+ 5s 自动隐藏 + pointerenter 钉住；`bindTouchHint`（App）。
- [x] 轻点不跨坐标微拖拽：既有 `SINGLE_PENDING` + `TOUCH_DRAG_THRESHOLD_PX`(6px) 阈值实现，核对保留。
- [x] 验证：`verifyWeb` 真实 exit 0 + 9 测试全过。**回滚点 R8**。

## Step 9 — App 瘦身核对 + WebTest 形状 + 全量验证

- [x] app.js 核对：2729 行（原 5852），App 类 2130 行 / 126 方法；非 App 代码 = 工具函数区 + ByteWriter/命令编码 + DemoFixture/demo 函数簇（design §2 明示留 app.js）。
- [x] `namespace.WebTest` 导出：与拆分前形状一致，补 `controlStateLabel`（新增，向后兼容）；逐项比对无缺失。
- [x] `./gradlew check` 真实 exit 0 + 日志 BUILD SUCCESSFUL（1m46s，禁管道吞码）；testCoControl/testRemoteAccessInfo/verifyWeb 全绿（9 webTest 含新增）。
- [ ] 人工冒烟清单（交用户浏览器验证）：连接→协控文案三态、诊断面板折叠+偏好、demo 进出、触摸手势说明、修饰锁清除。

---

## Review Gates（映射 prd AC）

| prd AC | 由步骤保证 |
|---|---|
| 模块化拆分、App 明显瘦身 | Step 1-6, 9 |
| WebTest 导出完备、现有 webTest 全绿 | Step 1-6 逐步 + 9 |
| 新增模块测试（toast/控制文案） | Step 5, 6 |
| toast 排队/去重/历史 | Step 5 |
| 传输降级可见、无静默黑屏 | Step 7 |
| 错误人话化、兜底 | Step 1 |
| 控制权文案、诊断分级、触摸、demo/空状态 | Step 6, 7, 8 |
| check 全绿、wire/API 不变 | Step 9 + 全程不碰协议 |

## Rollback Points

R0（备份）→ R8，每步独立。回滚 = `backup/` 恢复 + 删新文件 + 还原 build.gradle/index.html/webTest。服务端与协议零改动。
