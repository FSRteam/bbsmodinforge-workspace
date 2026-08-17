# bbs-web-remote 浏览器端架构规约（`bbs-web-remote/src/client/resources/assets/bbswebremote/web/`）

> 适用范围：bbs-web-remote 的浏览器端 bundle（非 BBS mod UI、非 `new/`）。
> 此文档记录该 bundle 的模块化加载契约、测试机制与重构约束。

## 1. 模块化 IIFE 命名空间加载契约

浏览器端为**无构建步骤**的纯 `<script>` 多文件加载。每个模块是独立 IIFE，挂到
`global.BBSWebRemote`（`ns`）下的职责域子对象，供 app.js 解构取回。

```
protocol.js → i18n.js → error.js → style.js → media.js → input.js → control.js → ui.js → app.js
```

- 加载/发布契约必须同步：`index.html` 的 `<script>` 标签、`build.gradle` 的
  `webRuntimeScripts` 数组、所有 `webTest/*.test.js` 的 `require()` 行、
  `ClasspathStaticHandler.FIXED_RESOURCE_PATHS`，以及 `ServerSmokeTestMain` 的真实
  HTTP resource/cache 断言。
- 依赖单向无环：i18n/error/style 无依赖（i18n 仅校验 ns 存在）；media 依赖
  protocol+style；input/control/ui 依赖 protocol + style（`clamp` 的 canonical 位置是
  `ns.Style`，`ns.Media.clamp` 仅为兼容转发）；app 依赖全部。
- 历史结论修正：08-01 归档中「FIXED_RESOURCE_PATHS 非白名单」的结论已过时——
  现行 `ClasspathStaticHandler.FIXED_RESOURCE_PATHS` 是真白名单，顶层 js 未注册即
  HTTP 400。
- 各模块 `ns.<Domain> = Object.freeze({...})`。模块文件开头必须校验前置模块已加载
  （`if (!ns) throw new Error("protocol.js must load before ...")`）。
- app.js IIFE 顶部从 `ns.*` 解构取回所有类/函数；**禁止模块间互相依赖作用域**，
  一切经 `ns`。

服务端发布边界还包括 `ClasspathStaticHandler` 与 `ServerSmokeTestMain`；
`build.gradle` 只负责语法与 Node 回归，不能证明浏览器能从 HTTP 服务取得模块。

## 2. WebTest 导出与 Node 无依赖测试机制

- `namespace.WebTest`（app.js 末尾 `Object.freeze` 块）导出类与工具函数，供
  `webTest/*.test.js` 无依赖 Node 加载；`globalThis.window = globalThis` + FakeElement
  模拟 DOM (`document.getElementById`/`querySelectorAll`/`addEventListener`)。
- **测试机制是硬约束**：新增模块文件必须同步加入 WebTest 导出才能被测试；
  WebTest 导出形状向后兼容（新增导出可，勿删改既有）。
- **「搬类不搬方法」约束是前提**：`session-lifecycle`/`media-reconnect` 用
  `Object.create(App.prototype)` 构造 App 再调 `app.acceptSessionOpen`/
  `app.consumeBinary` 等方法断言——**App 类的方法不能移出**。可拆的是「非 App 的
  独立类 + 独立工具函数」。App 方法体中的纯逻辑若可无 DOM 测试，抽成模块纯函数/
  类后 App 委托（渐进、只抽安全项）。
- 新增 webTest 后须在 `webTestTasks`（build.gradle）注册对应 `testWeb*` 任务，
  否则不进 `verifyWeb`。

## 3. 前端纯改动边界

- 纯 UI/状态行为改动**禁止触碰** `src/client/java/` 服务端与 wire 协议
  （wire `1.1` / Addon / API `2.0`）。新增或重命名顶层脚本时，必须只同步静态发布
  白名单和 HTTP 冒烟测试，不借机改变 wire 行为。
- 连接表单只收集配对码、角色和显示名称；服务地址必须从
  `location.origin` 推导并保持同源，不展示或读取用户可编辑的 URL。
  `file:` 离线开发页可保留固定 loopback 回退，但生产网页不允许跨源直连。
- `FIXED_RESOURCE_PATHS`（`ClasspathStaticHandler`）既控制固定资源缓存，也参与
  `safeRelativePath` 的顶层路径准入。前端新增顶层 js 文件必须加入该集合；否则请求
  在读取 classpath 前就以 HTTP 400 拒绝。

## 4. Scenario: publish a top-level browser module

### 1. Scope / Trigger

- Trigger: add, rename, split, or remove a top-level file under
  `assets/bbswebremote/web/*.js`.
- Why: Node can load the source file directly even when Jetty rejects its URL, so syntax and
  WebTest success alone do not prove that the real page starts.

### 2. Signatures

- Server allowlist/cache owner:
  `ClasspathStaticHandler.FIXED_RESOURCE_PATHS: Set<String>`.
- HTTP validation owner:
  `ClasspathStaticHandler.safeRelativePath(Request): String | null`.
- Integration assertion owner:
  `ServerSmokeTestMain.assertResource(HttpClient, int, String, String, String)`.

### 3. Contracts

- Every top-level runtime script named by `index.html` must be present in
  `FIXED_RESOURCE_PATHS` and `webRuntimeScripts` in the same dependency order.
- Every WebTest must `require()` the complete runtime chain before `app.js`.
- A browser request such as `/error.js?wire=1.1` must return HTTP 200,
  `text/javascript`, the expected module marker, a strong ETag, and revalidation cache headers.
- Content-addressed media remains under `/assets/<sha256>` and does not belong in the fixed set.

### 4. Validation & Error Matrix

| Condition | Required result |
|---|---|
| Fixed runtime script exists | HTTP 200 + JavaScript content type |
| Top-level script omitted from fixed set | HTTP 400; server smoke must fail |
| Fixed path missing from classpath | HTTP 404; server smoke must fail |
| Unknown top-level path | HTTP 400 |
| Valid `/assets/<sha256>` path | Content-addressed asset handling |

### 5. Good / Base / Bad Cases

- Good: split `input.js`, register it in all five publication/test locations, and assert
  `/input.js?wire=1.1` over the embedded server.
- Base: edit an existing module without changing names; no publication-list change is needed.
- Bad: add the `<script>` tag and Node `require()` only. Tests pass locally, but the browser logs
  `namespace.<Module> is undefined` and form submission falls back to a page reload.

### 6. Tests Required

- `verifyWeb`: syntax plus dependency-chain Node regressions.
- `testServerSmoke`: GET every runtime script and assert status, content type, marker, ETag,
  cache control, CSP, and `nosniff`.
- Browser smoke: load the real Jetty URL and assert there are no startup console errors before
  exercising the connection form.

### 7. Wrong vs Correct

```java
// Wrong: index.html references error.js, but Jetty rejects the top-level path.
Set.of("index.html", "protocol.js", "app.js", "styles.css");

// Correct: every top-level runtime dependency is published explicitly.
Set.of("index.html", "protocol.js", "error.js", "style.js", "media.js",
    "input.js", "control.js", "ui.js", "app.js", "styles.css");
```

## 5. 样式令牌 / 移动收纳 / i18n / 主题约定（2026-08-16 relayout 二次落地）

- **扁平极简皮肤**（08-16 relayout 取代首轮 glass 方案）：styles.css 只保留
  `--bg/--panel/--panel-2/--line/--line-strong/--text/--muted/--accent*` 与
  tone 四色（`--ok/--warn/--danger/--focus`）等扁平令牌；**禁止**
  `backdrop-filter`、渐变叠加、按透明度分档的 `--glass-*` 族。阴影只有
  `--shadow` 一个浮动层令牌；`--z-*` 梯度保留（canvas-overlay 4 → cursor 5 →
  hud 14 → toolbar 15 → diagnostics 16 → gamepad 17 → menu 26 → ime 100 →
  toast 120）。smoke marker `.app-shell`/`.quality-control` 类名不得变。
- **CSS 不使用 id 选择器**（id 保留给 JS）；`[hidden]` 由全局 `display:none !important`
  兜底。
- **HUD 插槽层**：`.hud-layer`（z=14）只承载 connection-card（左上）与
  participants-panel（右上）。participants-panel 默认 `hidden`，仅从
  `#moreMenu` 的「协控参与者」项 `revealParticipantsPanel()` 打开——
  `updateLeaseUi` 不得自动取消 hidden（只更新内容与顶栏 lease pill）。
  画布状态层（transport-overlay/empty-state/touch-hint/canvas-hint/
  portrait-notice）不入槽。
- **移动端收纳**（08-16 relayout）：右缘单个 `.menu-fab`（#mobileMoreButton，
  右侧垂直居中）+ 唯一 `.mobile-menu`（#moreMenu）扁平列表，收录请求控制/
  键盘/虚拟鼠标/游戏模式/L·R·M/修饰键/打开 BBS/参与者/诊断/主题/交出操作权。
  不再有独立 mousePanel 或多按钮 hub；`mobilePanelIds()` 恒等于 `["moreMenu"]`。
  菜单项点击后应 `setMobilePanel(null)` 收起。`#gamepadLayer`（WASD 摇杆 +
  跑/飞/跳/蹲）与菜单互斥：开菜单先 `setGamepadActive(false)`。
- **i18n**：新顶层模块 `i18n.js`（加载序 protocol → **i18n** → error → …）。用户可见文案
  一律 `ns.I18n.t(key, params, fallback)`；静态 HTML 用 `data-i18n`/`data-i18n-attr`
  （`"title:key;aria-label:key"`）+ App 启动 `hydrate(document)` 一次性水合。zh-CN 是
  唯一内建 locale；目录值变更等于文案变更，需跑全量 webTest；HTML 默认文案与目录值
  逐字节一致。`media.rtc.*`/`transport.state.*` 前缀键是运行时拼接，静态搜索不可见。
- **主题**：`html[data-theme="light"]` 令牌覆盖组；顶栏 `#themeToggle` 切换，持久化
  localStorage `bbs-web-remote.theme`（默认 dark）。`applyTheme()` 同步
  `meta[name=theme-color]`/`color-scheme`。
- **trackpad 手势契约**（08-16 relayout 重写，对齐 CRD/RD 共识）：
  `RemoteInput.pointerMode` ∈ direct|trackpad，偏好持久化
  `bbs-web-remote.pointer-mode`。trackpad 模式：**单指滑动只移动光标，绝不按压**
  （回归测试锁定：移动 + `trackpadHoldExpire()` 后 events 必须为空）；
  轻点(<260ms 且位移<12px)=单击；双击=双击；**按住静止 ≥320ms 触发按压**
  （`trackpadHoldTimer`，位移超 12px 取消），随后拖动=按住拖拽；
  **双指轻点=右键、三指轻点=中键**（`maybeTrackpadMultiTap`：手势未
  zoom/pan/scroll 超过 8px 且 <280ms 且每指位移 <18px；最后一指可在
  GESTURE_DRAIN 态判定）。位移一律用「相对落点的位移」而非累计里程
  （累计 movedPx 曾导致移动即连点，禁止回归）。`#virtualCursor`
  `data-pressed` 提供按压视觉反馈。INPUT_STATE 客户端发送上限 60Hz（rAF 合帧
  `scheduleStateFlush`），服务端限流 `EarlyRate(120, 60)` 有余量，不得再上调。
  游戏键/摇杆经 `holdGameKey`/`releaseGameKey` 走 heldKeys + KEY 事件，权限丢失时
  `releaseAll` 清空。

## 6. 状态与 UX 约定

- 控制权三层状态（permission/role/lease）向用户收敛为三词主线：**观看中 → 可控制 →
  操作中**。统一由 `controlStateLabel()`（control.js，`ns.Control`）产出
  `{label, tone}`；诊断面板 `diagRole` 保留详细描述（". 协控 · 持有操作权"）与
  面向用户主线不冲突。
- 诊断面板分「基础」+「高级」两级（高级折叠 + localStorage 记忆展开偏好，
  键 `bbs-web-remote.diagnostics`）。diag 字段 id 必须与 app.js `$("diag...")` 引用
  一一对应。
- 传输降级可见状态（`setTransportOverlay`）：negotiating/switching/stalled/autoplay
  四态 + 超时兜底；toast 用 `key` 参数去重合并（`note(msg, announce, {level, key})`）。
- UX 恒定约束：降级期间保留上一帧或 overlay 占位，不留静默黑屏。
- `/ws/ui` 的文本通道同时承载 WebRTC SDP 信令；`UiSocketImpl.sendText` 的出站限制必须
  与 Jetty 的 `MAX_UI_TEXT_MESSAGE_BYTES` 一致，不能复用普通输入文本的 4 KiB 限制。
  `ServerSmokeTestMain` 必须以大于 4 KiB 的信令同时覆盖浏览器→服务端和服务端→浏览器。
- WebRTC 信令会进入原生调用，服务端连接回调不能持有 `BBSWebRemoteClient` 的全局锁等待
  native 返回；使用独立串行信令执行器，并让 `onOpen`/`onClose` 始终能够及时发布或回收 UI socket。
