# P2 交互打磨 — Design

> 纯前端（`app.js` + `index.html` + `styles.css`），只改展示/交互层。不改协议 wire `1.1`、lease 服务端语义、输入编码。所有底层判定字段（`controllerPermission` / `coControlMode` / `authorizedRole` / `leaseState` / `ownsLease` / `sessionActive`）语义保持不变，本设计只在其上叠加一层「面向���户的展示状态」。

审计锚点均以 `src/client/resources/assets/bbswebremote/web/` 下现网文件的真实行号为准。

---

## 1. 控制权文案收敛（display-layer state model）

### 现状问题
- `updateControlUi()`（`app.js:4920-4948`）里，按钮文案由 `permission` + `coControlMode` + `authorizedRole` 三元嵌套三目直接拼出（`app.js:4929-4933`）；`roleStatus` 另用一套词（`app.js:4924-4926`「协控 · 操作中 / 协控者 / 控制者 / 观看者」）；`leaseStatusPill` / `leaseSummary` / `leaseDetail` 又在 `updateLeaseUi()`（`app.js:4950-4990`）里各算一套（「操作权空闲 / 正在交出操作权 / 你正在操作 / …正在操作」）。四处词汇口径不一致，用户难以把「permission=能不能控」「lease=此刻谁在操作」分开理解。
- 观看者点被拒操作时只有一次性 toast（`app.js:5019` 的 `note(...)`），关掉就没了「为什么不可用」的常驻说明。

### 设计
引入**单一纯函数** `resolveControlState()` 作为展示层唯一真相源，输入现有底层字段、输出一个有限状态 + 一组已本地化文案，不触碰任何协议字段：

```
resolveControlState() -> {
  state,        // 'offline' | 'viewing' | 'controllable' | 'operating' | 'peerOperating' | 'yielding'
  roleText,     // 顶栏 roleStatus 文案
  roleTone,     // roleStatus data-tone
  buttonText,   // controlButton 文案
  buttonDisabled,
  buttonTitle,
  reason        // viewer 不可控时的常驻解释（写入 leaseDetail / 新增 controlHint）
}
```

状态判定（只读现有字段，顺序即优先级）：

| state | 判定（复用现有字段） | 含义 |
|---|---|---|
| `offline` | `!connected && !demoMode` | 未连接 |
| `viewing` | 连接/ demo 中，`coControlMode && authorizedRole === VIEWER && !controllerPermission` | 只授予观看，无法取得操作权 |
| `controllable` | `controllerPermission && (!ownsLease)` 且 lease ��闲或他人未持有 | 可控制，等待/可取得操作权 |
| `operating` | `controllerPermission && ownsLease && leaseState.phase` 为活跃 | 我正在操作 |
| `peerOperating` | `coControlMode && lease 被他人持有` | 他人操作中 |
| `yielding` | `leaseState.phase === 'RELEASING'` | 操作权移交中 |

统一文案（三档主线，对齐 PRD「观看中 / 可控制（等待操作权）/ 操作中」）：

- 顶栏 `roleStatus`（`index.html:27`）：观看中 / 可控制 / 操作中（他人操作中显示「他人操作中」）。
- `controlButton`（`index.html:113`）：请求控制 / 释放控制（协控模式下：请求协控 / 退出协控；只观看权限时保持 disabled + 文案「仅可观看」）。
- `leaseStatusPill` + `leaseSummary`/`leaseDetail`（`index.html:28-31/138-139`）：延续 `updateLeaseUi()` 的「此刻谁在操作 + 倒计时」，但**措辞与 state 对齐**（operating→「你正在操作」，peerOperating→「{name} 正在操作」，controllable→「可取得操作权 · 点击/滚动/输入即接管」）。
- **一句解释**：每个 state 配一句 `reason`，写入常驻位置（`leaseDetail` 或新增内联 `#controlHint`），使「为何不可用」不再依赖一次性 toast。

改造点：`updateControlUi()` 与 `updateLeaseUi()` 改为**消费 `resolveControlState()` 的返回值**，不再各自内联拼词。底层 `setPermissionRole()`（`app.js:4801-4831`，含降级时 `releaseAll(true)`）、lease 收发、`canAttemptControl()`（`app.js:4797-4799`）逻辑一律不动。

### 可测性
`resolveControlState()` 做成不依赖 DOM 的方法（读 `this.*` 字段，返回对象），导出进 `namespace.WebTest`（`app.js:5235-5266`），供 Node 回归断言「同一组字段 → 同一组文案，且 button/roleStatus/pill/summary 口径一致」。

---

## 2. 诊断分级

### 现状
`#diagnosticsPanel`（`index.html:161-214`）是单个 `<details>`，内含 ~24 个 `<div>`（`diagAccessUrl` … `diagScale`，`index.html:164-212`）；`updateDiagnostics()`（`app.js:5073-5211`）在构造函数里被 `setInterval(…, 500)` 全量刷新（`app.js:3673`）——无分级、收起仍全算。

### 设计
- **默认精简区**：连接状态、UI/媒体 FPS（`diagActualFps`）、媒体分辨率（`diagMediaResolution`）、码率（下行/上行）、操作权（`diagLease`）。这些留在默认可见的 summary 下。
- **高级折叠区**：其余字段包进内层 `<details id="diagAdvanced">`（或给现有 `<div>` 打 `data-tier="advanced"` + CSS 折叠）。默认收起。
- **偏好记忆**：新增 localStorage key `bbs-web-remote.diagnostics`（与既有 `bbs-web-remote.canvas-toolbar`（`app.js:14`）/`quality` 键同风格），存 `{ advanced: bool }`，读写各封一个 `readDiagnosticsPref`/`writeDiagnosticsPref`（对齐 `readToolbarState`/`writeToolbarState` `app.js:207-231` 的 try/catch 风格）。`diagAdvanced` 的 `toggle` 事件写回偏好。
- **收起降频**：`updateDiagnostics()` 顶部按「高级区是否展开」分流——展开时维持 500ms 全量；收起时只刷精简字段，高级字段降到 ~2s 刷新一次（用一个 `lastAdvancedTick` 时间戳门控，不新增第二个 timer，避免与既有 `diagnosticTimer` 竞态）。`updateLeaseUi()`（在 `updateDiagnostics()` 内 `app.js:5076` 被调）保持每 tick 调用，操作权是精简项。

---

## 3. 触摸可发现 + 防粘滞

### 现状
- 右/中键切换 `[data-mouse-button]`（`app.js:2746-2751`）与修饰锁 `[data-modifier]`（`app.js:2753-2760`）绑定在 `#mobileControls`（`index.html:146-158`），该区仅在 coarse-pointer 媒体查询下显示（`styles.css:776-778` 默认 `display:none`，`styles.css:887+` media query 打开）。修饰锁是 `virtualModifiers` 位域异或（`app.js:2756`），一旦置位无常驻显眼提示，易忘（PRD `app.js:2753-2758/3260`）。
- 轻点跨坐标微拖拽：`touchUp()`（`app.js:2940-2954`）在 `SINGLE_PENDING` 抬手时，先 `updatePointer(primary.startX, startY)` 发 PRESS，再 `updatePointer(event.clientX, clientY)` 发 RELEASE——低于阈值 `TOUCH_DRAG_THRESHOLD_PX`（`app.js:2681`）的抖动也会 press/release 落在不同坐标。

### 设计
1. **折叠不失控件**：`#mobileControls` 的鼠标键 + 修饰锁本就独立于 `FloatingToolbar.setCollapsed()`（`app.js:569-575` 只切 `#canvasToolbarControls`）。为满足 PRD「折叠时仍可达」，保证 `#mobileControls` 不随工具栏折叠而隐藏，并在其上补一个**常驻紧凑条**（`#touchQuickBar`）承载：当前选中键（L/R/M）、已锁修饰、一键释放。定位为 canvas 内固定角标，coarse-pointer 恒显。
2. **修饰锁常驻提示**：新增 `#modifierLockIndicator`，随 `virtualModifiers` 变化实时渲染已按下的 Shift/Ctrl/Alt（在 `[data-modifier]` 的 click handler `app.js:2753-2760` 里追加一次 `renderModifierLocks()` 调用；同处 `sendKey`/`releaseAll` 后也刷新）。有锁时高亮常驻。
3. **一键全部释放**：`#releaseAllButton` → 调既有 `releaseAll(true)`（`app.js:3249-3260`，已发 focus-lost + 清 buttons/heldKeys/virtualModifiers + 释放指针捕获），随后 `renderModifierLocks()` + 同步 `[data-mouse-button]`/`[data-modifier]` 的 `aria-pressed`。不新增释放语义，只暴露入口。
4. **首触手势提示**：`#gestureHint`（一次性、可关闭），文案「单指=指针/点击，双指=缩放/平移/滚动」。首个 `touchDown()`（`app.js:2861`）触发展示；关闭或首次触摸后写 localStorage `bbs-web-remote.gesture-hint = dismissed`，之后不再出现。读写复用 try/catch 存储封装。
5. **同坐标轻点**：`touchUp()`（`app.js:2944-2954`）在 `!primary.eligible`（即位移未过阈值、判定为轻点）时，PRESS 与 RELEASE **都用同一坐标**（`primary.startX/startY`），不再中途 `updatePointer` 到抬手坐标。过阈值的拖拽路径不变（仍走既有 drag 分支），��保滚动/拖动不回归。

### 可测性
`renderModifierLocks` 逻辑做成读 `this.virtualModifiers` 的纯查询 + DOM 写分离；`RemoteInput` 已在 `WebTest`（`app.js:5245`）导出，`mobile-input.test.js` 可加断言：轻点 press/release 同坐标；`releaseAll` 后修饰位清零。

---

## 4. demo 退出入口 + file:// 标识

### 现状
`startDemo()`（`app.js:5028-5043`）无退出入口；`note("离线 fixture 正通过 wire 1.1 …")` 用了工程术语「fixture」；`file:` 或 `?demo=1` 自动进 demo（`app.js:3675-3678`）无可见标识；`stopDemo()`（`app.js:5045-5055`）已存在但无 UI 触发。

### 设计
- **可见退出**：demo 期间把 `#demoButton`（`index.html:70`，现绑 `startDemo` `app.js:3686`）切换为「退出离线演示」态并改绑 `stopDemo()` + 回到未连接 UI（复用 `updateConnection("idle","未连接")` `app.js:3671`）；或新增 `#demoExitButton`。以 `this.demoMode` 驱动显隐。
- **离线徽标**：新增 `#offlineDemoBadge`（canvas 角，恒显于 demo 期），文案「离线演示 · 非真实连接」。`startDemo`/`stopDemo` 切其 `hidden`。
- **术语人话化**：`startDemo` 的 `note` 与 `updateConnection("online","离线演示")`（`app.js:5039`）措辞统一为「离线演示」，去掉「fixture」。

---

## 5. 空状态权限感知文案

### 现状
`#emptyState`（`index.html:119-123`）静态文案引导所有人「使用上方『打开 BBS』」，但 `canRequestDashboardOpen()`（`app.js:4589-4596`）要求 `controllerPermission`——无权限 viewer 点了会被拒。`emptyState` 显隐在 `app.js:4465`（显）/ `4546`（`onFrame` 隐）。

### 设计
新增 `updateEmptyState()`，按 `resolveControlState()` 的 state 写文案：
- `viewing`（只观看）：「等待协控者操作，或在 Minecraft 中打开面板」——不引导点会被拒的「打开 BBS」。
- `controllable`（有权限）：保留「可使用『���开 BBS』或在 Minecraft 中打开面板 / 启动离线演示」。
- `offline`：引导连接 / 离线演示。

在 `setPermissionRole` / lease 变化 / `onSessionReset`（`app.js:4465` 附近）后调用。不改 `onFrame` 的隐藏逻辑。

---

## 6. 焦点提示提升出 sr-only

### 现状
`#canvasHint`（`index.html:124`）是 `.sr-only`，视力用户看不到「画布获得焦点后才能用鼠标键盘」。

### 设计
新增视觉可见的 canvas 内提示 `#focusHint`（桌面 / 非触摸、且 canvas 未聚焦时显示），监听 `#remoteCanvas`（`index.html:118`）的 `focus`/`blur` 切换 `hidden`。保留 `#canvasHint` 的 sr-only 供读屏（无障碍不回归）。仅在 `sessionActive` 且非 coarse-pointer 时出现，避免噪声。

---

## 7. 可本地化约定

本任务所有**新增用户文案**集中到 app.js 顶部一个字符串表（如 `const COPY = { ... }`），key 命名对齐 `wr-ux-error-i18n` 的 `bbswebremote.remote.*` lang key 约定，并同时提供 en/zh 两值占位，便于 error-i18n 任务后续接入统一 locale 装配。**本任务不自建完整 i18n 运行时**（属 `wr-ux-error-i18n` 范围），只保证新文案是「可被抽取的键」而非散落硬编码串。现有硬编码中文串不在本任务批量迁移之列（避免越界）。

---

## 兼容性说明

- 不新增/改动任何 wire 消息、字段、编码；`sendWire`/`sendEncoded`（`app.js:4749-4769`）路径零改动。
- 所有新 DOM 元素为增量新增，旧服务端 + 新前端、新前端 + 旧服务端均不受影响（无协议协商依赖）。
- 新 localStorage key 全部 try/catch 容错（对齐 `app.js:207-231`），隐私模式/禁用存储时降级为默认值，不抛错。
- `resolveControlState()` 是既有字段的**只读投影**，不改变 `canAttemptControl()` / lease 收发 / `setPermissionRole` 降级 `releaseAll` 行为。

## Risks

1. **轻点同坐标改动**误伤滚动/拖拽：以 `TOUCH_DRAG_THRESHOLD_PX`（`app.js:2681`）为界，只改「未过阈值」分支；`mobile-input.test.js` 增回归覆盖过阈值路径。
2. **诊断降频**引入的时间门控与既有 `diagnosticTimer`（`app.js:3673`）竞态：不新增 timer，只在单一 tick 内按展开态分流，降低竞态面。
3. **状态机收敛**遗漏某组合导致文案空白：`resolveControlState()` 提供 `default` 兜底 = 现有「观看中」；Node 回归枚举字段组合断言无空文案。
4. **`#mobileControls` 与新 quick bar 视觉/层级冲突**：复用既有 coarse-pointer 媒体查询与 z-index 梯度（`styles.css:776+/887+`），不引入新层级体系。
5. 焦点提示在触摸端误显：以 `pointer: coarse` 媒体查询 + `sessionActive` 双门控。
