# P2 交互打磨 — Implementation Plan

> 只改三文件：`bbs-web-remote/src/client/resources/assets/bbswebremote/web/` 下的 `app.js` / `index.html` / `styles.css`。不改协议、lease 服务端语义、输入编码。每步小到可 `node --check` 通过。

验证基线（每步后可跑）：
- 语法：`node --check` app.js / protocol.js。
- Node 回归：gradle 任务 `verifyWeb`（聚合 `verifyWebAppSyntax` + 各 `testWeb*`，见 `build.gradle:362-390`）。真实入口是 `namespace.WebTest`（`app.js:5235-5266`）。
- 命令：`cd bbs-web-remote && ../gradlew verifyWeb`（Windows 用 `..\gradlew.bat verifyWeb`）。**绝不信任管道 exit code**——检查真实进程 exit + BUILD SUCCESSFUL 日志（参考项目红线：`gradlew | tail` 会假绿）。
- 需要 Node 在 PATH 或 `NODE_BINARY`（`build.gradle:306-343`）。

---

## 阶段 A — 展示层状态模型（PRD 控制权收敛 / AC-1）

- [ ] **A1** `app.js`：新增方法 `resolveControlState()`（读 `this.connected/demoMode/sessionActive/controllerPermission/coControlMode/authorizedRole/ownsLease/leaseState`，返回 `{state, roleText, roleTone, buttonText, buttonDisabled, buttonTitle, reason}`）。纯逻辑、不碰 DOM。放在 `updateControlUi`（`app.js:4920`）上方。含 `default` 兜底 = 观看中。
- [ ] **A2** `app.js`：改 `updateControlUi()`（`app.js:4920-4948`）消费 A1 返回值写 `roleStatus`/`controlButton`（文案/disabled/title）；保留末尾 `updateLeaseUi()` + `updateDashboardOpenUi()` 调用（`app.js:4946-4947`）与 `remoteCanvas` aria-label 逻辑（`app.js:4940-4945`）。
- [ ] **A3** `app.js`：改 `updateLeaseUi()`（`app.js:4950-4990`）的 summary/detail/pill 措辞与 A1 的 state 对齐（operating/peerOperating/controllable/yielding）；lease 倒计时与 `leaseState` 读法不动。
- [ ] **A4** `app.js`：`namespace.WebTest`（`app.js:5235-5266`）导出 `resolveControlState`（或通过已导出的 `App` 访问）。
- [ ] **A5** `index.html`：如需常驻 reason，给 `#leaseDetail`（`index.html:139`）承载，或在 `controlButton`（`index.html:113`）旁加内联 `#controlHint`。无新协议依赖。
- 验证：`node --check app.js`；`verifyWeb`（覆盖 session-lifecycle）；新增断言见 F1。

## 阶段 B — 诊断分级（AC-2）

- [ ] **B1** `index.html`：`#diagnosticsPanel`（`index.html:161-214`）内，精简项（连接/UI+媒体 FPS `diagActualFps`/媒体分辨率 `diagMediaResolution`/上下行码率/操作权 `diagLease`）留默认可见；其余 `<div>`（`index.html:164-212`）包进内层 `<details id="diagAdvanced">`。
- [ ] **B2** `styles.css`：为 `#diagAdvanced` 加折叠样式，复用 `.diagnostics`（`styles.css:714-771`）网格视觉；移动端 media query（`styles.css:932+`）同步。
- [ ] **B3** `app.js`：加 `readDiagnosticsPref`/`writeDiagnosticsPref`（key `bbs-web-remote.diagnostics`，try/catch 对齐 `app.js:207-231`）；构造期恢复展开态；`#diagAdvanced` 的 `toggle` 事件写回。
- [ ] **B4** `app.js`：`updateDiagnostics()`（`app.js:5073-5211`）顶部按高级区展开态分流——收起时高级字段用 `lastAdvancedTick`（~2s）门控降频，精简字段与 `updateLeaseUi()`（`app.js:5076`）仍每 tick。不新增 timer（沿用 `app.js:3673` 的 `diagnosticTimer`）。
- 验证：`node --check`；手动确认 `verifyWeb` 不回归（诊断字段仍被写）。

## 阶段 C — 触摸可发现 + 防粘滞（AC-3/4/5）

- [ ] **C1** `app.js`：`touchUp()`（`app.js:2944-2954`）轻点分支——PRESS 与 RELEASE 都用 `primary.startX/startY`，删除中途 `updatePointer(event.clientX, clientY)`。过阈值拖拽分支不动。
- [ ] **C2** `index.html`：`#mobileControls`（`index.html:146-158`）内加 `#modifierLockIndicator` + `#releaseAllButton`；确保该区不受 `FloatingToolbar` 折叠影响（本就独立，见 `app.js:569-575`）���
- [ ] **C3** `app.js`：加 `renderModifierLocks()`（读 `this.virtualModifiers` → 写指示器 + 同步 `[data-modifier]` aria-pressed）；在 `[data-modifier]` click（`app.js:2753-2760`）后、`releaseAll`（`app.js:3249-3260`）后调用。
- [ ] **C4** `app.js`：`#releaseAllButton` 绑定 → `this.input.releaseAll(true)` + `renderModifierLocks()` + 同步 `[data-mouse-button]`/`[data-modifier]` aria-pressed。绑定处对齐 `bindUi`（`app.js:3686-3701`）。
- [ ] **C5** `app.js`+`index.html`：`#gestureHint` 一次性提示；首个 `touchDown()`（`app.js:2861`）展示，关闭/首触后写 localStorage `bbs-web-remote.gesture-hint`。
- [ ] **C6** `styles.css`：`#modifierLockIndicator`（有锁高亮常驻）、`#releaseAllButton`、`#gestureHint` 样式，挂在 coarse-pointer media query（`styles.css:887+`）。
- 验证：`node --check`；`testWebMobileInput`（`mobile-input.test.js`）加断言（F2）。

## 阶段 D — demo 退出 + 离线徽标（AC-6）

- [ ] **D1** `index.html`：加 `#offlineDemoBadge`（canvas 角，默认 hidden）「离线演示 · 非真实连接」。
- [ ] **D2** `app.js`：`startDemo()`（`app.js:5028-5043`）显徽标、`#demoButton`（`index.html:70`）切「退出离线演示」；`stopDemo()`（`app.js:5045-5055`）隐徽标、按钮复位 + `updateConnection("idle","未连接")`。`#demoButton` handler（`app.js:3686`）按 `this.demoMode` 分流 start/stop。
- [ ] **D3** `app.js`：`startDemo` 的 `note`（`app.js:5042`）与 `updateConnection`（`app.js:5039`）去「fixture」，统一「离线演示」。
- 验证：`node --check`；`verifyWeb`。

## 阶段 E — 空状态权限感知 + 焦点提示（AC-7/8）

- [ ] **E1** `app.js`：加 `updateEmptyState()`，按 `resolveControlState()` state 写 `#emptyState`（`index.html:119-123`）文案（viewing 不引导「打开 BBS」）。在 `setPermissionRole`（`app.js:4801`）、lease 变化、session reset（`app.js:4465`）后调用；不改 `onFrame` 隐藏（`app.js:4546`）。
- [ ] **E2** `index.html`+`app.js`+`styles.css`：加视觉可见 `#focusHint`，监听 `#remoteCanvas`（`index.html:118`）focus/blur 切换 hidden；仅 `sessionActive` 且非 coarse-pointer 显示。保留 `#canvasHint` sr-only（`index.html:124`）。
- 验证：`node --check`；`verifyWeb`。

## 阶段 F — 本地化收口 + 回归断言

- [ ] **F1** `app.js`：新增用户文案集中到顶部 `COPY` 表，key 对齐 `bbswebremote.remote.*`（`wr-ux-error-i18n` 约定），en/zh 双值占位。不建完整 i18n 运行时。
- [ ] **F2** `src/webTest/session-lifecycle.test.js` / `mobile-input.test.js`：加断言——(a) 各字段组合下 `resolveControlState()` 无空文案且 button/roleStatus/pill 口径一致；(b) 轻点 press/release 同坐标；(c) `releaseAll` 后 `virtualModifiers===0` 且指示器清空。测试保持 dependency-free（对齐现有 FakeElement 模式 `mobile-input.test.js:5-47`）。
- 验证：`cd bbs-web-remote && ../gradlew verifyWeb` —— 检查真实 exit + BUILD SUCCESSFUL；失败读 gradle 日志定位。

---

## Review Gates

- G1（A 后）：control state 四处口径一致、lease 协议行为无回归（对照 `setPermissionRole` 降级 `releaseAll` 仍触发）。
- G2（C 后）：折叠工具栏时右/中键+修饰锁仍可达；修饰锁常驻可见 + 一键释放；轻点无跨坐标；过阈值拖拽/滚动不回归。
- G3（全部后）：`verifyWeb` 全绿（真实 exit）；无 wire/协议 diff（`git diff` 只含三前端文件 + 两 webTest）。

## Rollback

- 按阶段提交，问题阶段 `git revert` 单独回滚（A/B/C/D/E 相互解耦；仅 E 空状态依赖 A 的 `resolveControlState`，回滚 A 需先回滚 E1/E 的调用点）。
- localStorage 新 key 向后兼容：删除代码即回默认，无残留副作用。

## AC 映射

| PRD AC | 阶段 |
|---|---|
| 控制权四处口径一致（AC-1） | A |
| 诊断精简+高级+记忆偏好（AC-2） | B |
| 折叠仍可切键+修饰锁常驻+一键释���（AC-3） | C2/C3/C4 |
| 首触一次性手势说明（AC-4） | C5 |
| 轻点不跨坐标（AC-5） | C1 |
| demo 退出入口 + file://离线标识（AC-6） | D |
| 空状态权限感知（AC-7） | E1 |
| 桌面未聚焦可见提示（AC-8） | E2 |
| Node 回归通过、无协议改动（AC-9） | F |
