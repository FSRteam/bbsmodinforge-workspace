# Design — P1 传输与画质反馈

> Scope: 纯前端 `bbs-web-remote/src/client/resources/assets/bbswebremote/web/` 下的 `app.js` / `index.html` / `styles.css`。
> 不改 wire `1.1`、不改 `/ws/ui` `/ws/media`、不改服务端、不改协议消息/字段。仅增量改造现有 `App.note` / `HybridMediaTransport` / `AdaptiveQualityController` 的**表现层与调度**。

---

## 0. 现状（grounded at file:line）

- `App.note(message, announce)` @ `app.js:5213-5220`：直接 `toast.textContent = message` 覆盖，`clearTimeout(this.toastTimer)` 冲掉上一条，单条 3200ms 消失，无队列、无去重、无历史。`this.toastTimer` 在构造函数 `app.js:3608` 初始化。
- DOM：`index.html:219` `<div class="toast" id="toast" role="status" aria-live="polite" aria-atomic="true">`；`index.html:220` `<div class="sr-only" id="liveStatus" role="status" aria-live="polite">`。样式 `styles.css:803-825`（`.toast` / `.toast[data-visible="true"]`）、`.sr-only` `styles.css:827-837`。既有可展开面板样式模式见 `index.html:161` `<details class="diagnostics" id="diagnosticsPanel">`。
- 传输切换：`App.switchToRtcMedia()` @ `app.js:4525-4531`（升级到 WebRTC，`closeMediaSocket(true,true,true)` 关掉 JPEG media socket，**无任何 note**）；`App.fallbackToJpegMedia()` @ `app.js:4533-4541`（降级，重开 media socket，**无任何 note**）。二者由 `HybridMediaTransport` 构造回调 `onSwitch/onFallback` 绑定 @ `app.js:3634-3635`。
- Transport 内部状态机：`configure()` 设 `this.state="协商中"` @ `2483`；协商失败 `fallback("NEGOTIATION_FAILED")` @ `2528`；看门狗 `RTC_NEGOTIATION_TIMEOUT_MS=12000` @ `2303/2490-2495`（首帧未到则 fallback）；`disconnected` 宽限 `RTC_DISCONNECTED_GRACE_MS=3000` @ `2302/2606-2613`；首帧 `firstDrawable()` @ `2586-2592` 置 `state="WEBRTC"` 并 `onSwitch`；`fallback()` @ `2617-2625` 置 `state="JPEG 回退"` 并 `onFallback(reason, generation)`。
- autoplay：`RtcSurfaceCache` 建 `<video>` 并 `video.play()` @ `app.js:2381-2392`，`play().catch(() => {})` **静默吞掉** autoplay 拒绝。
- 首帧门控：`scheduleFrame` @ `2412-2428` 仅当 `readyState>=2` 且有 handle 才 `onFirstDrawable`；轨道到但无可解码帧时依赖 12s 看门狗兜底。
- 自动画质：`AdaptiveQualityController.evaluateWindow` @ `2099-2132` 含护栏——15s 冷却 `now-this.lastChangeAt<15000` @ `2109`；降档需 2 个 BAD 窗口 @ `2116-2120`；升档需 `healthyWindows>=10 && healthyDurationMs>=30000` @ `2126`；`changeTier` @ `2135-2147`。换档回调 `applyAdaptiveProfile(profile)` @ `app.js:3851-3858`：`reconnectMediaForQuality()` @ `3842-3849` 每次都 `openMediaSocket(...)` 重开 media socket（闪屏根因），成功后 `note("自动画质已调整为"+...)` @ `3856`（逐次弹窗根因）。
- 已有回归测试断言：`media-reconnect.test.js:337-338` 断言"环境刷新未 spam quality toast"、`:289-296` 断言画质变更只换 media socket 不动 UI socket；`webrtc-hybrid.test.js:89-96` 用 `onSwitch/onFallback` 收集数组。这些是必须不回归的契约。

---

## 1. Toast 队列 / 去重 / 历史（基础设施，先做）

### 1.1 数据模型（`App` 构造函数内新增，紧邻 `app.js:3608` 的 `this.toastTimer`）

```
this.toastQueue = [];        // 待展示队列：{ text, level, count, at, key }
this.toastHistory = [];      // 环形历史：最近 TOAST_HISTORY_MAX 条
this.toastActive = null;     // 当前可见条目（用于同 key 合并计数）
this.toastTimer = null;      // 复用现字段：当前条目消失定时器
```

新增模块级常量（与 `MAX_MEDIA_RECONNECT_ATTEMPTS` @ `app.js:428` 同区域声明）：
```
const TOAST_HISTORY_MAX = 50;      // 历史保留上限
const TOAST_DEDUPE_WINDOW_MS = 4000; // 去重/合并窗口
const TOAST_VISIBLE_MS = 3200;     // 保持现有单条时长
const TOAST_MIN_GAP_MS = 180;      // 两条之间的最小切换间隔（读得完）
```

**条目形状**：`{ text:string, level:"info"|"warn"|"error", count:int, at:epochMs, key:string }`。`key` = 调用方可选传入的去重键；缺省用 `level + "|" + text`。

### 1.2 `note()` 重写（`app.js:5213-5220`）——保持签名向后兼容

现有全部调用点都是 `note(message, announce)`（bool）。新签名：
```
note(message, announce, options)   // options = { level="info", key } 可选
```
`announce` 语义不变（true → 写 `#liveStatus`）。旧调用零改动即可工作（默认 `level:"info"`）。

行为：
1. 归一 `text`（`boundedText` 已用于消息裁剪，见 `app.js:4185/4422`，历史入库前复用以防超长）。
2. **去重/合并**：若 `toastActive` 且同 `key` 且 `now-toastActive.at < TOAST_DEDUPE_WINDOW_MS` → `toastActive.count++`，刷新可见文本为 `text + " ×N"`，重置消失定时器，更新对应 history 条目的 count，**不入队**。否则若队尾同 key 且在窗口内 → 队尾 `count++`（同样不新增）。
3. 否则推入 `toastQueue`，并 `pushHistory(entry)`。
4. `announce` → `#liveStatus.textContent = text`（aria-live 始终播报**最新**一条，满足 prd:23）。
5. 调 `pumpToast()`。

### 1.3 `pumpToast()`（新私有方法，紧随 `note`）

- 若 `toastActive` 仍在显示 → 直接返回（由定时器回调再次 pump）。
- 从队首取一条设为 `toastActive`：`toast.textContent = active.count>1 ? text+" ×"+count : text`；`toast.dataset.visible="true"`；`toast.dataset.level = active.level`（驱动配色）。
- `this.toastTimer = setTimeout(...)`：先 `visible="false"`，再 `TOAST_MIN_GAP_MS` 后清 `toastActive` 并递归 `pumpToast()` 取下一条。用**单一** `toastTimer` 字段，`clearTimeout` 语义可控，避免堆叠。

### 1.4 历史面板（DOM / CSS）

- **DOM**（`index.html`，紧邻 `toast` @ 219）：新增 `<details class="toast-history" id="toastHistory"><summary>消息记录 <span id="toastHistoryCount">0</span></summary><ol id="toastHistoryList" aria-live="off"></ol></details>`。`aria-live="off"` 避免与 `#liveStatus` 重复播报（`#liveStatus` 仍是唯一 live region，保 prd:23）。复用 `index.html:161` `<details class="diagnostics">` 的既有折叠面板视觉语言。
- **渲染**：`pushHistory()` 维护 `toastHistory`（超 `TOAST_HISTORY_MAX` 从头弹出），并重绘 `#toastHistoryList`：每条 `<li data-level="..."><time>HH:MM:SS</time> <span>text</span> <em>×N</em></li>`，时间用本地 `toLocaleTimeString`。计数写 `#toastHistoryCount`。列表**倒序**（最新在上）。DOM 缺失（Node 回归环境无 document 该节点）时静默跳过——见 §5 测试友好。
- **CSS**（`styles.css`，紧随 `.toast` 段 803-825）：`.toast[data-level="warn"]` / `[data-level="error"]` 改边框/左条颜色（复用现有 `--line-strong` 及告警色变量，若无则新增局部）；`.toast-history` 固定在 toast 上方，折叠态仅一行 summary，展开滚动列表 `max-height`。移动端沿用 `styles.css:839` 的 `@media (pointer: coarse)` 断点收敛宽度。

---

## 2. 传输降级可见 + 平滑（无黑屏）

### 2.1 让切换可见（经队列化 note，非术语文案）

在 `switchToRtcMedia()` @ `app.js:4525` 成功路径末尾、`fallbackToJpegMedia()` @ `app.js:4533` 成功路径末尾各加一条 `note`：
- 升级：`note("已启用低延迟视频", true, { level:"info", key:"media-transport" })`
- 降级：`note("已切换到兼容视频通道", true, { level:"warn", key:"media-transport" })`

共用 `key:"media-transport"` → 抖动式来回切换被 §1 去重合并，不刷屏。文案不含 `WEBRTC/JPEG/NEGOTIATION` 等内部 state 术语（prd:26）。

### 2.2 切换期不黑屏（保留旧帧 / 占位）

根因：`switchToRtcMedia` 调 `closeMediaSocket(true,true,true)`（`preserveSurfaces=true` @ `app.js:4527`），而 `fallbackToJpegMedia` 走 `openMediaSocket(...,0,true)`（`preserveSurfaces=true` @ `4538`）——surface 层已保留，但**首帧到达前画布可能停在旧帧或空**，用户观感为"卡住/黑"。

设计：引入一个轻量**切换遮罩状态**，纯前端、不碰渲染管线：
- 新增方法 `setTransportOverlay(kind)`，`kind ∈ {none, negotiating, switching, stalled, autoplay}`。它只切换一个覆盖在 `#canvasStage`（`index.html:117`）上的半透明占位层 `#transportOverlay`（新增 DOM，默认 `hidden`），显示对应非术语文案 + 轻动效；**不清除画布**，因此旧帧在遮罩半透明下仍可见（避免纯黑）。
- `switchToRtcMedia`/`fallbackToJpegMedia` 进入时 `setTransportOverlay("switching")`，其成功注入首帧后清除。清除时机复用现有信号：升级成功由 transport `onSwitch`（首帧已 drawable，见 `firstDrawable` @ `2586-2592`）驱动；降级成功由首个 JPEG media 帧驱动（`consumeMediaBinary` 成功路径，`app.js:4034` 附近）。

> 说明：`#emptyState`（`index.html:119`，`app.js:4465/4546` 控制）用于"整会话无帧"，与切换遮罩语义不同，二者独立，不复用以免回归 session 生命周期测试（`session-lifecycle.test.js`）。

### 2.3 协商中 / 停滞进度态

- `configure()` 置 `state="协商中"` @ `app.js:2483` 时，transport 无法直接碰 DOM（它只持 renderer/sendJson）。做法：`HybridMediaTransport` 已有 `onSwitch/onFallback` 回调模式——**新增一个可选回调 `onPhase(phase, generation)`**（`options.onPhase || function(){}`，构造 @ `2453-2454` 同区），在 `configure` 进入协商、看门狗触发前的中间态调用。App 侧在 `app.js:3631-3636` 绑定 `onPhase: (p) => this.onMediaPhase(p)`，映射到 `setTransportOverlay` + 队列 note：
  - `negotiating` → 遮罩"正在建立低延迟视频…"，**不弹 toast**（避免每次协商刷屏；仅遮罩）。
  - 协商超过一个较短��值（复用/略小于 `RTC_NEGOTIATION_TIMEOUT_MS=12000`，例如 6000ms 的中间提示）仍无首帧 → `stalled` → 遮罩改"视频链路较慢，正在重试…"。
- **兼容性**：`onPhase` 是新增可选回调，旧调用方（含 `webrtc-hybrid.test.js:91-96` 只传 `onSwitch/onFallback`）零影响——回调缺省为空函数。

### 2.4 首帧超时兜底

已有 `RTC_NEGOTIATION_TIMEOUT_MS` 看门狗 @ `2490-2495`：轨道到但无 `firstDrawable` 时 `fallback("NEGOTIATION_TIMEOUT")`。**保留该逻辑**（这是既有兜底，满足 prd:29"触发回退"）。仅补：fallback 到 JPEG 时 §2.1 的可见 note 会自然告知用户"已切换到兼容视频通道"，不再静默黑块。无需改看门狗时长。

### 2.5 autoplay 被拒可见

根因：`app.js:2391-2392` `video.play().catch(() => {})` 静默。改为把拒绝**上报**：`RtcSurfaceCache` 构造已接收 `onFirstDrawable` 回调（`app.js:2341-2344`），同样新增一个可选 `onAutoplayRejected` 回调（缺省空函数），`play().catch(err => this.onAutoplayRejected(err))`。`HybridMediaTransport` 建 cache 时 @ `2465` 透传，再经 §2.3 的 `onPhase("autoplay")` 冒泡到 App → `setTransportOverlay("autoplay")` 显示"点击画面以启用视频"，并在 `#canvasStage` 上挂一次性 `pointerdown` 重试 `video.play()`。用户点击后清除遮罩。

> 纯前端、可选回调、不改 wire。Node 回归里 FakeVideo.play 返回 resolved（`webrtc-hybrid.test.js` / `media-reconnect.test.js` 的 FakeVideo），不触发该路径，无回归。

---

## 3. 自动画质防抖

### 3.1 取舍（关键决策，登记于此）

**目标"无缝换档"（同 socket 变更编码参数不重连）需要服务端支持在既有 media socket 上重协商画质** —— 当前 wire `1.1` 无此消息，`openMediaSocket` 的画质由连接建立时决定，服务端不提供"在途改参"。**父任务边界禁止扩协议**（parent prd:11 / 本 prd:56 Out of Scope）。

→ **决策：不追求真无缝，改为"合并抖动 + 显著提高重连门槛"达成"不打扰/不闪屏"**（本 prd:33 明确允许此退路）。真正的"同 socket 无缝换档"登记为后续协议任务，不在本任务实现。

### 3.2 提高重连门槛 + 合并（`applyAdaptiveProfile` @ `app.js:3851-3858`）

保留 `AdaptiveQualityController` 全部既有护栏（15s 冷却 `2109`、降档 2×BAD `2116-2120`、升档健康窗 `2126`）——**不动 controller 判定逻辑**，只改 App 侧的"执行 + 提示"：

1. **换档合并/防抖**：`applyAdaptiveProfile` 不再立即 `reconnectMediaForQuality()`。改为记录目标 profile 到 `this.pendingAdaptiveProfile` 并起一个**合并计时器** `this.adaptiveApplyTimer`（复用 `customQualityTimer` 同款 debounce 模式，见 `app.js:3791-3797`），延迟窗口（如 `ADAPTIVE_APPLY_DEBOUNCE_MS = 2500`）。窗口内多次换档只保留最终 profile，只重连一次。
2. **提高"值得重连"门槛**：合并计时器 fire 时，若 `pendingAdaptiveProfile === this.mediaProfile`（净变化为 0，抖动往返抵消）→ 不重连、不弹 toast、`refreshQualityUi()` 收尾即可。仅当净档位真的变化才 `reconnectMediaForQuality()`。
3. **toast 节流/合并**：重连成功后用**固定 key** 弹一次：`note("画质已自动调整为"+qualityLabel(profile), false, { level:"info", key:"adaptive-quality" })`。共用 key → §1 去重合并"×N"，短时多次只显示一条（满足 prd:34、回归 `media-reconnect.test.js:337`）。

> `refreshQualityUi()`（诊断/下拉标签，`app.js:3818-3826/5202-5206`）仍每次即时更新——那是无副作用的文本刷新，不闪屏，保留即时性。

### 3.3 与手动画质区分

手动 `setQualityPreference` @ `app.js:3732-3749` 与自定义 `app.js:3791-3797` 是**用户主动**操作，保持即时重连 + 即时 toast（`key` 不同或不传），不进 §3.2 的自动合并路径。仅 `applyAdaptiveProfile`（controller 驱动的自动档）走防抖。

---

## 4. 兼容性说明

- **签名兼容**：`note(message, announce)` 旧调用全部无需改（新增第三参可选）。全站 `this.note(...)` 调用点（`app.js:3749/3795/3856/3946/4185/4210/4427/4474/4478/4489/4496/4600/4604/4608/4612/4637/4670/4676/4690/4699/4997/5015/5019/5042/5225`）行为等价或更好（排队而非覆盖）。
- **回调兼容**：`HybridMediaTransport` 新增 `onPhase`、`RtcSurfaceCache` 新增 `onAutoplayRejected` 均为可选、缺省空函数——现有测试构造（`webrtc-hybrid.test.js:91/98`、`media-reconnect.test.js`）不传，零影响。
- **协议兼容**：无任何 wire/字段/消息新增或修改；服务端不变；旧服务端+新前端仍工作（切换/协商提示是纯客户端观测）。
- **无障碍**：`#liveStatus` 仍是唯一 aria-live 播报点，播报最新一条；历史列表 `aria-live="off"` 不重复朗读。
- **DOM 缺失安全**：所有新 DOM 操作前用 `$("id")` 判空（沿用 `app.js:5199/5201` 既有 `if ($(...))` 守卫风格），使无 DOM 的 Node 回归环境不炸。

---

## 5. Risks

1. **队列积压**：错误风暴下队列过长、用户看到滞后旧提示。缓解：去重合并 + `error` 级可插队（可选：`error` 直接抢占为 `toastActive` 并重置队列低优先项）。范围内先做去重，插队作为可选增强标注。
2. **切换遮罩清除时机错配**：若首帧信号漏触发，遮罩可能残留。缓解：遮罩自带最长存活兜底定时器（≤ 看门狗时长），到时强制清除，宁可短暂无提示也不永久糊屏。
3. **防抖延迟感知**：自动换档延迟 2.5s 生效，理论上极端持续劣化时响应变慢。缓解：`SEVERE`（`changeTier(-2)`，`app.js:2115`）路径可绕过合并立即执行（大幅劣化应快速降档），仅 `BAD/HEALTHY` 单步走防抖。需在 implement 里对 SEVERE 单独判定。
4. **测试对 toast 文本断言**：`media-reconnect.test.js:337` 按子串 `"自动画质"` 计数。若改文案为"画质已自动调整"仍含"画质"但不含"自动画质"——需同步核对该断言，保持它"计数为 0"的语义（环境刷新不产生该 toast）仍成立，必要时更新断言匹配串（属测试维护，非协议变更）。
5. **CSS 层级**：`#transportOverlay` z-index 需低于 `.toast`（`styles.css:805` `z-index:120`）但高于画布，避免遮住提示或被工具栏盖住。
