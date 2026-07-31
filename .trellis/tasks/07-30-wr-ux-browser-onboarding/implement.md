# P0 浏览器：配对二维码/深链 + 连接���角色升级 — 实施计划

> 构建口径：前端回归走 `namespace.WebTest` 的 Node 用例；整包构建 `cd bbs-web-remote && ./gradlew ...`。
> **红线：绝不信任管道退出码**（`... | tail` 会吞真实 exit code）。每步查真实 exit code + 读日志确认 `BUILD SUCCESSFUL`。
> DoD = prd.md 的 Acceptance Criteria 全绿。

## Step 0 — 基线（回滚点 R0）
- [ ] 读 `app.js` 的 `connectFromForm`(~3881)、`setDefaultServer`(~3873)、`handleJsonMessage`(~4166/4206)、`setPermissionRole`(~4801)、`setAuthorizedRole`(~4827)、`toggleControl`(~5009)、`updateControlUi`(~4920)、构造尾 demo 判定(~3675)。
- [ ] 确认 server-discovery 的 `RemoteAccessInfo` 契约已定稿（深链格式 §2 依赖它）。
- [ ] 跑一次现有前端回归建立绿基线：`cd bbs-web-remote && ./gradlew <web 回归 task>`，查真实 exit + 日志。
- 回滚点 R0：未改动任何源码。

## Step 1 — 深链解析 + 自动填充 + 擦除（AC1/AC2/AC3）
- [ ] `app.js` 新增 `consumeDeeplink()`：解析 `location.hash` 的 `pair`/`role`/`name`/`autoconnect`；`code` 走 `boundedText`+正则 `^[0-9]{4,12}$` 白名单，非法忽略。
- [ ] 命中时填 `$("pairCode")` / `$("serverUrl")=location.origin` / 预选 role / 预填 name。
- [ ] `history.replaceState(null,"",location.pathname+location.search)` 擦除 fragment。
- [ ] 在构造末尾（demo 判定同处）调用 `consumeDeeplink()`；`autoconnect=1` 时才 `connectFromForm()`（默认关）。
- [ ] Node 回归：新增用例覆盖 合法 `#pair=123456`、含 role、畸形码被忽略、擦除后 `location.hash` 为空。
- 验证：`./gradlew <web 回归>` 真实 exit + 日志。回滚点 R1。

## Step 2 — 角色升级 C2S 发送端（AC4/AC5）
- [ ] `app.js` 新增 `requestRoleUpgrade(code?)`：经 `sendJsonMessage({type:"ROLE_UPGRADE_REQUEST",role:"CONTROLLER",code})`。
- [ ] 改 `toggleControl`(5018)/`updateControlUi`(4934)：`authorizedRole===VIEWER` 时按钮不再死禁用，改为触发升级；需要码时弹入口（复用配对码字段/轻量 prompt）收集 code。
- [ ] 发送后设按钮 pending（"正在申请协控…"），记录 pending 状态与时间戳。
- [ ] Node 回归：VIEWER 点击发出 `ROLE_UPGRADE_REQUEST`；pending 态 UI 断言。
- 验证同上。回滚点 R2。

## Step 3 — 角色升级 S2C 生效（复用 ROLE/WELCOME）（AC4）
- [ ] `handleJsonMessage` 的 `case "ROLE"/"CONTROL"`(~4206) 收到升级后 role 变化时，复用 `setPermissionRole`/`setAuthorizedRole` 生效并清 pending，**不重连**。
- [ ] `updateControlUi` 复位按钮文案/状态，与 P2 交互文案约定一致（软对齐 `wr-ux-interaction-polish`）。
- [ ] Node 回归：模拟服务端回 `ROLE=CONTROLLER` → 前端进入可控制态且未触发 socket 重建。
- 验证同上。回滚点 R3。

## Step 4 — 旧服务端优雅降级（AC7）
- [ ] 升级请求发出后启动 3s 超时；超时未见 role 变化 → 降级提示"该服务端不支持连接后升级，请重新配对"，按钮复位可重试。
- [ ] 收到 role 变化则取消超时。
- [ ] Node 回归：发送后无响应 → 超时降级文案；有响应 → 不触发降级。
- 验证同上。回滚点 R4。

## Step 5 — 服务端升级裁决分支（AC4/AC5/AC6）
- [ ] UI socket 文本消息处理新增 `ROLE_UPGRADE_REQUEST` 分支：按现有权限策略（含可选 CONTROLLER 配对码校验，走 `PairingAuthService` 一次性/constant-time）裁决。
- [ ] 批准 → 经既有 `ROLE`/`WELCOME` 下发新 role/authorizedRole；拒绝 → 回 `ROLE`(仍 VIEWER)+可读 `message`（本地化 key 走 `wr-ux-error-i18n` 约定）。
- [ ] 未知/畸形字段安全忽略；不改 wire、不新增二进制 opcode。
- [ ] 服务端裸 main 风格断言测试（仿 `*TestMain`），挂 `check` 的 JavaExec。
- 验证：`cd bbs-web-remote && ./gradlew compileClientJava` 单步；再 `check`��真实 exit + 日志。回滚点 R5。

## Step 6 — 二维码（主路径固定格式 + 可选浏览器内 QR）（AC1）
- [ ] 固定深链字符串格式（§2），与 server-discovery 设置栏 QR 展示位对齐（服务端渲染 QR 给手机扫）。
- [ ] （可选/可 defer）浏览器内 `qr.js` 内联 SVG，无重依赖、不联网，仅用于"分享到另一设备"。若时间不足则登记 defer，不阻塞验收。
- 验证：手机扫码→自动填充 手动冒烟。回滚点 R6。

## Step 7 — 本地化 key（AC 支撑）
- [ ] 新增用户可见文案到 `en_us.json`/`zh_cn.json`，key 遵循 `bbswebremote.<domain>.<name>`（对齐 `wr-ux-error-i18n`）。
- [ ] 无裸英文串残留于本任务触及路径。
- 验证：加载 en/zh 两 locale 目视无缺 key。回滚点 R7。

## Step 8 — 全量验证 + 兼容矩阵
- [ ] `cd bbs-web-remote && ./gradlew build`（或既定 verify task）——查真实 exit code + 日志 `BUILD SUCCESSFUL`，**不用管道判绿**。
- [ ] 兼容矩阵手动冒烟：新前端+新服务端（扫码→连接→VIEWER 升级）；新前端+旧服务端（升级降级提示不崩）；旧前端+新服务端（正常观看/配对）。
- [ ] 桌面 + 手机各一次全流程记录，写入 `research/`。

## Review Gates（AC 映射）
| prd AC | 步骤 |
|---|---|
| 扫码/深链自动填充一点即连 | Step 1, 6 |
| 码从地址栏擦除不残留 | Step 1 |
| 只含一次性短期码 | Step 2/5/6, design §8 |
| VIEWER 不断线升级 CONTROLLER | Step 2, 3 |
| 需 CONTROLLER 配对时不断线补码 | Step 2, 5 |
| 升级被拒可读原因可重试 | Step 3, 5, 7 |
| 兼容矩阵（新旧组合） | Step 4, 5, 8 |
| wire/身份/held 不变 | Step 5, 8 |
| 桌面+手机冒烟记录 | Step 8 |

## 回滚点汇总
R0 基线 · R1 深链 · R2 升级发送 · R3 升级生效 · R4 降级 · R5 服务端裁决 · R6 QR · R7 i18n。每步独立可回退，服务端改动（R5）与纯前端���动解耦。
