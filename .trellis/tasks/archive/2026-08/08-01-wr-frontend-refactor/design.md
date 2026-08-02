# 前端架构重构：app.js 模块化拆分 + UX 痛点修复 — 技术设计（修订版）

## 1. 关键约束（本次修订的核心认识）

**webTest 直接测 App 方法**：`session-lifecycle`/`media-reconnect` 用 `Object.create(App.prototype)` 构造 App 再调 `app.acceptSessionOpen`/`app.onSessionReset`/`app.consumeBinary` 等方法断言。**因此 App 类的方法不能移出**，否则这些测试全挂。

**真正可拆分的是「非 App 的独立类 + 独立工具函数」**（app.js 行 1-3757，不挂在 App.prototype 上）：
- 工具函数区（~40+ 个）：`currentLocale`/`humanizeError`/`normalizeQualityPreference`/`readQualityPreference`/`applyMediaRequestToUrl`/`affineFromTriangles` 等
- 独立类区：`TintedImageCache`/`FloatingToolbar`/`CanvasRenderer`/`ResourceCache`/`MediaMetrics`/`LinkFeedback`/`AdaptiveQualityController`/`SurfaceCache`/`RtcSurfaceCache`/`HybridMediaTransport`/`RemoteInput`/`ByteWriter`/`DemoFixture`

webTest 对独立类的测试方式是 `new X`（`new RemoteInput`/`new CanvasRenderer`/`new HybridMediaTransport`/`new FloatingToolbar` 等），**不依赖这些类定义在哪个文件**——只要从 `WebTest` 能取到类引用即可。这给了拆分的安全空间。

## 2. 拆分策略（修正）

**核心理念：「搬类不搬方法」。** App 方法留在 App（测试安全），类与工具函数按职责域搬入新文件。App 瘦身来自：非 App 代码全部移出，App 仅剩方法定义（约 2050 行）。**最终 app.js ≈ 2100 行（App 类 + WebTest 导出 + 启动），原 5852 行中的 3700 行逻辑分布到各模块文件**。这已是真实的大拆分（文件级模块化 + 职责域清晰），且不破坏测试。

新文件结构（每文件独立 IIFE，注册到 `global.BBSWebRemote`）：

| 新文件 | 移入内容（来自 app.js） |
|---|---|
| `web/style.js` | 颜色/几何工具函数：`rgba`/`colorsEqual`/`colorCss`/`averageColor`/`midpointVertex`/`triangleArea`/`colorSpread`/`affineFromTriangles`/`createWorkCanvas` + `TintedImageCache` 类 |
| `web/media.js` | `CanvasRenderer`/`SurfaceCache`/`RtcSurfaceCache`/`ResourceCache`/`MediaMetrics`/`LinkFeedback`/`AdaptiveQualityController`/`HybridMediaTransport` 类 + 相关工具（`detectMediaProfile`/`readQualityPreference`/`writeQualityPreference`/`normalizeCustomMediaSettings`/`readCustomMediaSettings`/`writeCustomMediaSettings`/`applyMediaRequestToUrl`/`qualityLabel`/`mediaQualitySummary`/`requestedMediaFps`/`networkInformation`/`formatBitrate`/`formatBytes`等） |
| `web/input.js` | `RemoteInput` 类 + 相关常量 |
| `web/control.js` | 协控相关工具函数 + 纯函数（如 `controlStateLabel`，本任务 R5 新增；参与协调 App 的协控方法委托的数据） |
| `web/ui.js` | toast 队列/历史逻辑（本任务 R2 新增的 `ToastQueue` 类）+ `FloatingToolbar` 类 + `readToolbarState`/`writeToolbarState` + DOM 集中访问 |
| `web/error.js` | `ERROR_MESSAGES`/`humanizeError`/`currentLocale`（本任务 R4 扩展） |
| `web/app.js` | 保留 App 类（方法不动）+ `bindUi` + `WebTest` 导出 + 启动 |

> `FloatingToolbar` 放 ui.js（DOM 相关）；`ByteWriter` 留 app.js（仅 App 内部用）或放 media.js。归属在实现时按「测试解构点最少改动」微调，**以 WebTest 导出形状不变为准**。

## 3. 命名空间与加载契约

每文件 IIFE 注册：

```js
(function (global) {
    "use strict";
    const ns = global.BBSWebRemote;
    ns.Media = { CanvasRenderer, SurfaceCache, HybridMediaTransport, ... };
    // 或直接挂到 ns 的扁平对象，app.js 里取
})(window);
```

关键：**WebTest 导出块（app.js 5807-5841）保持不变**——它从当前作用域取类引用。由于拆分后这些类在各模块文件里定义，需要让 app.js 能引用到它们。**方案**：各模块文件把类挂到 `ns`（如 `ns.Media.CanvasRenderer = CanvasRenderer`），app.js 的 IIFE 开头从 `ns.Media` 等取回并 `const CanvasRenderer = ...`，保持后续代码（WebTest 导出）零改动。

```js
/* app.js 顶部（IIFE 内）： */
const { CanvasRenderer, SurfaceCache, ... } = ns.Media;
const { RemoteInput } = ns.Input;
const { FloatingToolbar } = ns.UI;
const { ERROR_MESSAGES, humanizeError, currentLocale } = ns.Error;
// ... 其余代码原样
```

**加载顺序**（index.html + Node 测试一致）：
```
protocol.js → error.js → style.js → media.js → input.js → control.js → ui.js → app.js
```
依赖单向无环：error/style 无依赖；media 依赖 style（颜色）；input/control/ui 依赖 error；app 依赖全部。

## 4. App 瘦身目标（现实版）

App 从文件角度**大幅瘦身**（5852 行文件 → app.js 约 2100 行，其余 3700 行在 6 个模块文件）。App 类本身方法数不变（100 个），但**方法体中的可抽逻辑若遇到「纯逻辑 + 可无 DOM 测试」的片段，抽成模块里的纯函数/类**（如 `controlStateLabel`、toast 队列），App 方法委托。此为**渐进**，只抽安全项，不强行抽出测试依赖的 App 方法体。

## 5. UX 痛点修复（配合重构落地）

### 5.1 toast 队列/历史（R2）— ui.js
`note()` 改造为队列化（同原设计 §5.1，但**`note` 方法留在 App**，逻辑委托 `ns.UI.ToastQueue` 实例）。实现点：
- `ToastQueue` 类（ui.js）：`queue`/`enqueue`/`pump`/去重合并 `×N`/`history` 保留 N 条/`render`。
- App 的 `note` 委托 `this.toastQueue.enqueue(...)`；`#liveStatus` aria-live 仍播报。
- 历史列表 DOM 加到 index.html。

### 5.2 传输降级可见（R3）— media.js + app.js
- App 的 `fallbackToJpegMedia`/`switchToRtcMedia` 加 `note()` 提示（方法在 App，提示走 toast）。
- 降级保留上一帧/占位；协商中/停滞/首帧超时/autoplay 被拒可见状态（复用 `transportOverlay`）。

### 5.3 错误人话化（R4）— error.js
- `ERROR_MESSAGES`/`humanizeError`/`currentLocale` 移入 error.js 并**扩展**映射（网络/HTTP 失败）。
- App `reportError` 用人话（`humanizeError`）；原始串仅 console。

### 5.4 交互打磨（R5）— app.js 方法 + index.html
- `controlStateLabel()` 纯函数（可在 control.js），App 的 `updateControlUi`/`updateLeaseUi` 调用。
- 诊断分级：index.html 分基础/高级折叠 + localStorage。
- 触摸可发现/防粘滞/手势说明/轻点修正：App/RemoteInput 方法内改（RemoteInput 在 input.js）。

## 6. 构建与测试适配

- `webRuntimeScripts` 扩为 `[protocol.js, error.js, style.js, media.js, input.js, control.js, ui.js, app.js]`。
- `verifyWebAppSyntax` 改/新增逐一 `--check` 所有 web 文件。
- index.html `<script>` 按 §3 顺序。
- **8 个 webTest require 行**：从 `require(".../app.js")` 改为按序 require 全部 web 文件。这是唯一需要改测试文件的地方（每文件 require 行变更，不改用例体）。

## 7. 兼容性与回滚

- **行为不变**：DOM id、事件、协议消息、WebTest 导出形状全部保持。
- 备份 `backup/app.js.orig` 已建（不等 git）。每拆一步跑 `verifyWeb`。
- **回滚**：`backup/` 恢复 + 删新文件 + 还原 build.gradle/index.html/webTest。

## 8. 风险

- **测试 require 依赖**：8 文件 + build.gradle + index.html 三处同步，漏一处全挂。`verifyWeb` 护栏。
- **CanvasRenderer 搬移**（~900 行）：`render-fidelity` 守护。
- **名字遮蔽**：app.js IIFE 里 `const { X } = ns.Media` 解构后，若模块文件未加载 `ns.Media` undefined → 解构错 → 启动挂。靠加载序 + verifyWeb 捕获。
- **UX 修复与重构交织**：DOM/HTML 改动需配 index.html，`check` 护栏兜底。

## 9. 数据流（重构后）

```
[加载] index.html script 按序 → 各模块注册 ns → app.js 解构取类 → new App()
[连接/消息] App.consumeBinary/handleJsonMessage（留在 App）→ 调 renderer/media/hybrid 等模块实例（App 持有）
[渲染] media.js CanvasRenderer → onFrame → App.updateDiagnostics（方法在 App）→ DOM
[输入] input.js RemoteInput → App.sendInputPayload → sendWire（App 方法）
[toast] App.note → ui.js ToastQueue → #toast + #liveStatus + 历史
```
