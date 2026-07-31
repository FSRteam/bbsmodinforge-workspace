# P0 浏览器：配对二维码/深链 + 连接后角色升级 — 技术设计

## 1. Boundaries（改哪些文件）

纯 `bbs-web-remote/` 客户端；协议仅**增量可选**扩展，不改 wire `1.1`/API `2.0`。

| 文件 | 现状 | 本任务改动 |
|---|---|---|
| `resources/assets/bbswebremote/web/app.js` | `connectFromForm`(~3881)、`setDefaultServer`(~3873)、`handleJsonMessage`(~4166)、`setPermissionRole`(~4801)、`setAuthorizedRole`(~4827)、`toggleControl`(~5009)、`updateControlUi`(~4920) | 新增深链解析+自动填充+URL 擦除；新增 `requestRoleUpgrade()`；`toggleControl`/`updateControlUi` 支持不断线升级；`handleJsonMessage` 处理升级结果（复用 ROLE/WELCOME 通道） |
| `resources/assets/bbswebremote/web/index.html` | 连接表单(~46-76)、`<script>`(~222) | 可选：QR 展示位（若前端渲染）；升级提示 DOM |
| `resources/assets/bbswebremote/web/qr.js`（新，可选） | 无 | 轻量内联 QR 生成（仅当选"前端渲染"路线） |
| 服务端 `client/control/*` 或 UI socket 消息处理 | 现有 ROLE/CONTROL 处理 | 新增可选 C2S `ROLE_UPGRADE_REQUEST` 裁决，复用既有 `ROLE`/`WELCOME` S2C 下发 |
| `strings/en_us.json`+`zh_cn.json` | — | 新增本任务可见文案 key（对齐 `wr-ux-error-i18n` 的 `bbswebremote.<domain>.<name>` 约定） |

> QR 生成归属：**首选由本任务在服务端把 `RemoteAccessInfo` 渲染成深链后，前端用轻量内联 SVG QR 展示**；服务端 QR 图片渲染由 `wr-ux-server-discovery` 的设置栏承载（游戏内展示二维码给手机扫）。两边共用同一深链字符串格式（见 §2）。本设计以"服务端设置栏展示 QR（手机扫）+ 浏览器消费深链参数"为主路径；浏览器内 QR 仅用于"把本页分享给另一台设备"的次要场景，可延后。

## 2. 深链 URL 格式

```
http://<host>:<port>/#pair=<code>[&role=CONTROLLER][&name=<n>]
```

- **fragment（`#`）而非 query**：fragment 不进服务端访问日志、不随 `Referer` 泄漏、不进代理日志，最小化一次性码的暴露面。
- host:port 天然来自页面同源（深链就指向页面自身），无需用户手填地址。
- `code`：一次性、短期（复用 `PairingChallenge`，默认 2min TTL、单次使用）。**绝不携带长期 HMAC secret 或可复用 token**。
- `role`（可选）：预选"请求协控"，仅预填单选框，不绕过服务端裁决。
- 深链字符串由服务端从 §契约 `RemoteAccessInfo`（来自 `wr-ux-server-discovery`）组装：`info.url() + "/#pair=" + info.code()`。host 为 null（LAN 未解析）时不产出深链，设置栏改显 unresolved 文案。

## 3. 浏览器解析 + 自动填充 + 擦除

在 `App` 构造末尾（现有 `location.search` demo 判定 ~3675 附近）新增 `consumeDeeplink()`：

```
parse location.hash -> URLSearchParams(hash.slice(1))
if has pair:
  code = boundedText(pair, 12) 且 /^[0-9]{4,12}$/ 校验；非法则忽略
  $("pairCode").value = code
  $("serverUrl").value = location.origin        // 同源，天然带 host:port
  role = params.get("role") -> 命中 CONTROLLER 才勾选，否则默认 VIEWER
  name = boundedText(params.get("name"), MAX_PARTICIPANT_NAME) -> 预填
  history.replaceState(null, "", location.pathname + location.search)  // 擦除 #pair
  note("已从二维码/链接填入配对码，请点击连接", ...)   // 经队列化 toast（依赖 transport-feedback，缺失则退化为旧 note）
  if params.get("autoconnect")==="1": connectFromForm()   // 默认关
```

- **擦除**：`history.replaceState` 立刻清掉 fragment，避免刷新/分享/浏览器历史残留一次性码。
- **不自动提交**（除非显式 `autoconnect=1`），保留用户可见确认这一步。
- 畸形/缺失参数一律忽略并按默认流程，不抛错。

## 4. 连接后角色升级（不断线）

### C2S（新增，增量可选）
```json
{ "type": "ROLE_UPGRADE_REQUEST", "role": "CONTROLLER", "code": "<optional pairing code>" }
```
经现有 `sendJsonMessage`（`/ws/ui` 文本通道，~4772）发送，复用 `outSeq` 语义无需新二进制 opcode。

### S2C（复用现有通道，零新增解析路径）
服务端裁决后，通过**既有** `ROLE` / `WELCOME` 消息下发 `role` / `authorizedRole` / 可选 `message`；前端 `handleJsonMessage`(~4206 `case "ROLE"/"CONTROL"`) 已有分支，复用 `setPermissionRole`/`setAuthorizedRole` 即时生效，**不重连**。拒绝时服务端回 `ROLE`（仍 VIEWER）+ 可读 `message`（本地化协同 `wr-ux-error-i18n`）。

### 前端交互（`toggleControl`/`updateControlUi`）
- 现状：`authorizedRole===VIEWER` 时"请求协控"按钮被 `disabled`（4934）且 `toggleControl`（5018）只弹"请重新配对"。
- 改为：按钮**可点**，点击走 `requestRoleUpgrade()`：
  - 若服务端策略需 CONTROLLER 级配对码，前端弹入口让用户输码（复用配对码字段或轻量 prompt），带 `code` 发升级请求，**仍不断线**。
  - 发送后按钮进入"正在申请协控…"pending 态；收到 `ROLE` 结果后由 `updateControlUi` 复位。
- `authorizedRole` 语义保留；仅把"死路提示"改成"可发起升级"。

### 旧服务端优雅降级
- 旧服务端不认识 `ROLE_UPGRADE_REQUEST` → 按现有 `handleJsonMessage` default 分支静默忽略（~4235 已是"未知消息忽略"），前端**不会**收到 `ROLE` 升级响应。
- 前端发送升级请求后启动一个短超时（如 3s）：超时未见 role 变化 → 回退提示"当前服务端不支持连接后升级，请断开并以‘请求协控’重新配对"，按钮复位。以此在新前端 + 旧服务端组合下不卡死、不误导。

## 5. 共享契约（与 wr-ux-server-discovery 对齐）

直接采用 server-discovery 的 `RemoteAccessInfo`（scheme/host/port/url/lanEnabled/lanCandidates/code/codeExpiresAt）。本任务**只消费**：
- 深链字符串 = `RemoteAccessInfo.url()` + `"/#pair="` + `code()`；`url()`/`code()` 为 null 时不产深链。
- QR 内容 = 深链字符串。
- 两任务在各自 design 固定该格式，避免一方改 host 组装导致另一方深链失效。

## 6. 二维码生成方案

评估：
- **服务端设置栏渲染 QR（主路径）**：游戏内把深链渲染成 QR 图给手机扫。QR 编码用轻量算法（数字+有限 ASCII，深链短、纠错 M 足够）。归 `wr-ux-server-discovery` 的设置栏展示位承载；本任务只固定深链格式。
- **浏览器内 QR（次要，可延后）**：若要"把已连接页面分享到另一台设备"，用内联 SVG QR（约 <10KB 无依赖实现，如 `qrcode-svg` 级别的最小移植），不引入重型 npm 依赖、不联网。
- 决策：**本任务不引入重依赖**；主路径依赖服务端展示 QR，浏览器内 QR 标记为可选增强，若时间不足则 defer（登记，不阻塞验收）。

## 7. 兼容性与回滚

| 组合 | 行为 |
|---|---|
| 新前端 + 新服务端 | 扫码/深链自动填充；VIEWER 可不断线升级 |
| 新前端 + 旧服务端 | 深链自动填充照常（纯前端）；升级请求被忽略 → 3s 后降级提示"请重新配对"，不崩溃 |
| 旧前端 + 新服务端 | 无深链解析（照常手输）；升级消息不发；正常观看/配对 |

- wire `1.1`、`/ws/ui`、参与者身份、held 输入语义不变；`ROLE_UPGRADE_REQUEST` 是文本 JSON 增量消息，未知即忽略。
- 回滚 = 还原 app.js 的 `consumeDeeplink`/`requestRoleUpgrade`/`toggleControl`/`updateControlUi` 改动 + 服务端升级裁决分支 + 新 i18n key + 可选 qr.js；不影响协议与其它子任务。

## 8. 安全

- 深链/QR 只含一次性短期单次配对码，无长期凭据。
- fragment 承载敏感参数，减少日志/Referer 暴露；页面加载即 `history.replaceState` 擦除。
- 升级用的配对码仍走既有 `PairingAuthService` 一次性、短期、constant-time 校验路径；服务端裁决为唯一权威，前端预选/预填不授权。
- `code` 校验 `boundedText` + 正则白名单，畸形忽略。

## 9. Risks

- **深链码在 fragment 仍可能被浏览器历史/书签短暂留存** → 加载即 `replaceState` 擦除；码本身短期单次，过期即废。
- **升级降级超时误判**：3s 超时在极慢链路可能误报"不支持" → 超时文案措辞为"未收到响应，可重试或重新配对"，并允许再次点击。
- **服务端升级策略与 CONTROLLER 配对耦合**：需与 server 端权限策略一致；design 固定"需要码时前端提示补码"，具体策略以服务端 `RemoteUiOpenCoordinator`/权限层为准。
- **QR 依赖膨胀**：坚持无重依赖；浏览器内 QR 可 defer。
- **与 transport-feedback 的队列化 toast 依赖**：若该任务未先合入，`note()` 退化为旧单条行为仍可用（软依赖，不阻塞）。
