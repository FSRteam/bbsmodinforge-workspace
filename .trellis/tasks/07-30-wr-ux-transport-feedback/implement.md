# Implement — P1 传输与画质反馈

> 只改前端：`bbs-web-remote/src/client/resources/assets/bbswebremote/web/{app.js,index.html,styles.css}`。
> 无 wire/协议/服务端改动。每步小而可编译；Node 回归 = `namespace.WebTest`；构建 = `./gradlew`（**绝不信任管道退出码**，见 §验证）。
> 参见同目录 `design.md`；行号锚点以当前 `app.js`（5277 行）为准。

---

## Phase A — Toast 队列 / 去重 / 历史（基础设施，先做）

映射验收：prd AC-1（排队不覆盖 + 合并计数）、AC-2（可回看历史）。

- [ ] **A1. 常量**：在 `app.js:428` 附近（`MAX_MEDIA_RECONNECT_ATTEMPTS` 声明区）新增 `TOAST_HISTORY_MAX=50`、`TOAST_DEDUPE_WINDOW_MS=4000`、`TOAST_VISIBLE_MS=3200`、`TOAST_MIN_GAP_MS=180`。仅加常量，不动逻辑 → 可编译。
- [ ] **A2. 状态字段**：在 `App` 构造函数 `app.js:3608`（`this.toastTimer = null;`）处补 `this.toastQueue=[]; this.toastHistory=[]; this.toastActive=null;`。`toastTimer` 复用。
- [ ] **A3. 重写 `note()`**（`app.js:5213-5220`）：新签名 `note(message, announce, options)`，`options={level:"info", key}` 可选，默认 `level:"info"`、`key=level+"|"+text`。实现 design §1.2：去重合并（同 key 且窗口内 → `count++` 刷新可见文本 `text + " ×N"` + 更新对应 history），否则入队 + `pushHistory`。`announce` 仍写 `#liveStatus`。末尾调 `pumpToast()`。保留旧两参调用全部兼容。
- [ ] **A4. 新增 `pumpToast()`**（紧随 `note`）：实现 design §1.3。单一 `toastTimer`：显示 `toastActive` → `TOAST_VISIBLE_MS` 后 `visible="false"` → `TOAST_MIN_GAP_MS` 后清 `toastActive` 递归 pump 下一条。写 `toast.dataset.level`。所有 `$("toast")` 判空守卫。
- [ ] **A5. 新增 `pushHistory(entry)`**：维护 `toastHistory`（超 `TOAST_HISTORY_MAX` `shift`），重绘 `#toastHistoryList`（倒序 `<li data-level><time>…</time><span>…</span><em>×N</em></li>`）、更新 `#toastHistoryCount`。DOM 缺失静默返回（`if (!$("toastHistoryList")) return;`）。
- [ ] **A6. DOM**（`index.html:219` toast 之后）：加 `<details class="toast-history" id="toastHistory"><summary>消息记录 <span id="toastHistoryCount">0</span></summary><ol id="toastHistoryList" aria-live="off"></ol></details>`。`#liveStatus`（220）保持不动。
- [ ] **A7. CSS**（`styles.css:825` `.toast[data-visible]` 之后）：`.toast[data-level="warn"|"error"]` 配色；`.toast-history` 定位于 toast 上方、折叠单行、展开 `max-height` 滚动；沿用 `styles.css:839` coarse 断点收宽。
- [ ] **A8. 回归**：跑全部 webTest；重点 `media-reconnect` / `session-lifecycle`（它们 stub `app.note`，见 `media-reconnect.test.js:294`、`session-lifecycle.test.js:174`）。确认 stub 覆盖仍生效（stub 直接替换实例方法，不受内部重写影响）。

**Review gate G-A**：`note` 旧签名零回归；队列/去重/历史三行为可由新单测或手动验证；无 DOM 环境不抛。**Rollback point**：Phase A 自成一体，可独立提交/回滚。

---

## Phase B — 传输降级可见 + 无黑屏

映射验收：prd AC-3（切换可见 + 无长黑屏）、AC-4（协商/停滞/首帧超时/autoplay 各有兜底）。依赖 Phase A（用队列化 note）。

- [ ] **B1. 切换可见 note**：`switchToRtcMedia()` `app.js:4525-4531` 成功路径末尾加 `note("已启用低延迟视频", true, {level:"info", key:"media-transport"})`；`fallbackToJpegMedia()` `4533-4541` 成功路径末尾加 `note("已切换到兼容视频通道", true, {level:"warn", key:"media-transport"})`。共用 key 合并抖动。
- [ ] **B2. 遮罩 DOM/CSS**：`index.html:117` `#canvasStage` 内加 `<div class="transport-overlay" id="transportOverlay" hidden role="status"></div>`（在 `#emptyState` 之外，独立）。CSS：半透明覆盖、`z-index` < `.toast`(120) 且 > 画布、居中文案、不设背景纯黑（保旧帧可见）。
- [ ] **B3. `setTransportOverlay(kind)`**（App 新方法）：`kind∈{none,negotiating,switching,stalled,autoplay}` → 切 `#transportOverlay.hidden` + 文案。带最长存活兜底定时器 `this.transportOverlayTimer`（≤ `RTC_NEGOTIATION_TIMEOUT_MS`）强制清除（design Risk 2）。`$` 判空守卫。
- [ ] **B4. 切换进入/清除接线**：`switchToRtcMedia`/`fallbackToJpegMedia` 进入置 `setTransportOverlay("switching")`；升级清除由 transport `onSwitch`→`switchToRtcMedia` 首帧已 drawable（`firstDrawable` `app.js:2586-2592`）即清；降级清除挂到首个 JPEG 帧成功处（`consumeMediaBinary` 成功分支，`app.js:4034` 附近）→ `setTransportOverlay("none")`。
- [ ] **B5. `onPhase` 回调（transport→App）**：`HybridMediaTransport` 构造 `app.js:2453-2454` 加 `this.onPhase = options.onPhase || function(){};`。在 `configure()` 进协商 `app.js:2483`（置 `state="协商中"` 后）调 `this.onPhase("negotiating", generation)`；看门狗 `app.js:2490-2495` 内、fallback 前若仍无首帧可加中间 `stalled` 相位（新增一个较短中间定时器，或复用看门狗前半程）。App 端 `app.js:3631-3636` 绑定 `onPhase:(p)=>this.onMediaPhase(p)`。
- [ ] **B6. `onMediaPhase(phase)`**（App 新方法）：`negotiating→setTransportOverlay("negotiating")` 文案"正在建立低延迟视频…"，**不弹 toast**；`stalled→setTransportOverlay("stalled")` 文案"视频链路较慢，正在重试…"；`autoplay→setTransportOverlay("autoplay")` 文案"点击画面以启用视频"+挂一次性 `pointerdown`。
- [ ] **B7. 首帧超时兜底**：**不改** `RTC_NEGOTIATION_TIMEOUT_MS` 看门狗 `app.js:2490-2495` 的 fallback 逻辑（既有兜底）。仅确认 fallback→`onFallback`→`fallbackToJpegMedia`→B1 的 note 会告知用户，无静默黑块。
- [ ] **B8. autoplay 上报**：`RtcSurfaceCache` 构造 `app.js:2341-2344` 加可选 `onAutoplayRejected`（缺省空函数）；`app.js:2391-2392` 改 `play().catch(err=>this.onAutoplayRejected(err))`。`HybridMediaTransport` 建 cache `app.js:2465` 透传，冒泡到 `onPhase("autoplay")`→B6。
- [ ] **B9. 回归**：`webrtc-hybrid.test.js`（`onSwitch/onFallback` 数组契约 `:89-96`、看门狗 `:179`、FakeVideo）、`media-reconnect.test.js`。确认新可选回调不传时空函数、FakeVideo.play resolved 不触发 autoplay 路径。

**Review gate G-B**：切换有可见非术语提示；切换/协商/停滞/autoplay 均无长黑屏（遮罩半透覆旧帧）；看门狗兜底未动；所有新回调向后兼容。**Rollback point**：B1 可独立于 B2-B8 提交（仅加 note）；遮罩+相位作为第二可回滚单元。

---

## Phase C — 自动画质防抖

映射验收：prd AC-5（抖动不逐次闪屏+弹窗，提示节流合并）、AC-6（既有护栏不回归）。依赖 Phase A。

- [ ] **C1. 常量 + 字段**：`ADAPTIVE_APPLY_DEBOUNCE_MS=2500`（A1 常量区）；`App` 构造加 `this.pendingAdaptiveProfile=null; this.adaptiveApplyTimer=null;`。
- [ ] **C2. 改 `applyAdaptiveProfile(profile)`**（`app.js:3851-3858`）：不再直接 `reconnectMediaForQuality()`。记 `pendingAdaptiveProfile=profile`、`refreshQualityUi()`（即时文本），起/重置 `adaptiveApplyTimer`（debounce 模式仿 `app.js:3791-3797` 的 `customQualityTimer`）。**例外**：`SEVERE` 大幅降档（`changeTier(-2)` 路径，`app.js:2115`）绕过 debounce 立即执行——需 controller 把"是否 SEVERE/多级"信息透出（见 C3）。
- [ ] **C3. 透出降档幅度（不改判定）**：`AdaptiveQualityController.changeTier` `app.js:2135-2147` 已知 `delta`；`onProfileChanged` 目前只传 `profile`（`app.js:2147`）。加第二可选参 `onProfileChanged(profile, {immediate:boolean})`，`immediate = |delta|>=2`（SEVERE 多级）。App 侧 `app.js:3615` 绑定处接收；`immediate` 时 C2 走立即路径。**护栏判定（15s/2×BAD/健康窗）完全不动**（`app.js:2109/2116-2120/2126`）。
- [ ] **C4. 计时器 fire 逻辑**：`adaptiveApplyTimer` 到期 → 若 `pendingAdaptiveProfile===this.mediaProfile`（净变化 0，抖动抵消）→ 只 `refreshQualityUi()`，**不重连不弹 toast**。否则 `this.mediaProfile=pending`、`reconnectMediaForQuality()`、`note("画质已自动调整为"+qualityLabel(pending), false, {level:"info", key:"adaptive-quality"})`（固定 key 合并）。清 `pendingAdaptiveProfile/adaptiveApplyTimer`。
- [ ] **C5. 手动路径不变**：`setQualityPreference` `app.js:3732-3749`、自定义 `3791-3797` 保持即时重连+即时 toast，不进 C2 防抖（用户主动操作即时反馈）。
- [ ] **C6. 回归（关键）**：`media-reconnect.test.js:289-338`——(a) `:337` "环境刷新未 spam 自动画质 toast" 断言仍成立（防抖 + 净变化 0 不弹）；(b) `:289-296` 画质变更只换 media socket、不动 UI socket 仍成立；(c) `:295` `setQualityPreference` 手动路径即时性不回归。若断言按��串 `"自动画质"` 匹配而新文案为"画质已自动调整"，同步核对/更新断言匹配串（design Risk 4，测试维护非协议变更）。计时器需在测试的 fake runtime/`pendingTimers()`（`media-reconnect.test.js:287`）下可推进——用与 `customQualityTimer` 同源的 `setTimeout`（App 走 `global`/注入 runtime），确保测试能 flush。

**Review gate G-C**：护栏判定字节级未改；抖动往返净零不重连不弹；换档 toast 合并；手动即时性保留；`media-reconnect` 全绿。**Rollback point**：Phase C 独立，回滚只需还原 `applyAdaptiveProfile` + 移除 debounce 字段/常量 + `changeTier` 第二参（可选参，还原无碍）。

---

## 验证（每 Phase 结束执行）

### Node 回归（快、先跑）
- [ ] 逐个：`cd bbs-web-remote && node src/webTest/media-reconnect.test.js`（同理 `webrtc-hybrid` / `session-lifecycle` / 其余 6 个）。测试 `require` 直接加载，断言失败即非零退出。
- [ ] 或经 Gradle 任务（`build.gradle:295-303` 注册的 `testWebMediaReconnect` 等，聚合任务 `build.gradle:388` 描述 "browser syntax checks and all dependency-free Node.js regression tests"）。

### 构建（**不信任管道退出码** — 项目红线）
- [ ] **禁止** `./gradlew ... | tail`/`| head` 之类判绿——管道会吞真实 exit code（见项目记忆 `gradle-pipe-exitcode-trap`）。
- [ ] 正确姿势：`./gradlew :bbs-web-remote:<webTest聚合任务> --console=plain > /tmp/build.log 2>&1; echo "EXIT=$?"` 然后 `Read` 日志 + 显式核对 `EXIT=0`。exit code 与日志**双确认**方可判绿。
- [ ] 前端资源改动需确认打包同步（`src/client/resources/...` → `build/resources/...`，见两份 app.js 路径），必要时 `:bbs-web-remote:processResources` 或对应 web 任务。

### 手动冒烟（可选，UI 类无法纯 Node 覆盖的项）
- [ ] 连续触发多提示：排队不覆盖、同提示 ×N 合并、历史面��可展开含时间/级别（AC-1/AC-2）。
- [ ] 制造 WebRTC↔JPEG 切换：有非术语提示、切换期无长黑屏（旧帧/遮罩可见）（AC-3）。
- [ ] autoplay 受限场景：出现"点击画面以启用视频"且点击生效（AC-4）。
- [ ] 链路抖动：不逐次闪屏 + 提示合并（AC-5）；手动画质/隐藏页/无 surface/主动重连行为不回归（AC-6）。

---

## 验收映射总表

| prd AC | Phase / 步骤 |
|---|---|
| AC-1 排队不覆盖+合并计数 | A3/A4 |
| AC-2 历史可回看(时间/级别) | A5/A6/A7 |
| AC-3 切换可见+无长黑屏 | B1/B2/B3/B4 |
| AC-4 协商/停滞/首帧超时/autoplay 兜底 | B5/B6/B7/B8 |
| AC-5 抖动不闪屏+提示节流合并 | C2/C4 |
| AC-6 既有护栏不回归 | C3(判定不动)/C5/C6 |
| AC-7 Node 回归通过、无 wire 改动 | 全程 §验证 + 兼容性(design §4) |

---

## 执行顺序 & 全局回滚

A → B → C（B/C 均依赖 A 的队列化 note）。三 Phase 各自成独立提交与回滚点。任一 Phase 回归失败即在该 Phase 内 `stop-and-reassess`（≤3 次尝试），不带病进入下一 Phase。全部完成后跑一次全量 webTest + 一次 `./gradlew`（显式 EXIT 核对）作最终门。
