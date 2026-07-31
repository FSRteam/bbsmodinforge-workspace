# P0 浏览器：配对二维码/深链 + 连接后角色升级

## Goal

消除上手阶段两大摩擦：(1) 手输 12 位配对码 + 手填地址端口——改为服务端签发含一次性码的**深链 URL** 与**二维码**，浏览器扫码/点链即自动填充地址与码；(2) 角色一旦选错（选了\"仅观看\"）只能断线重配——改为**连接后可在不断线的前提下请求从 VIEWER 升级到 CONTROLLER**。

## 依据（审计 file:line）

- 配对码手输、无二维码/深链：`index.html:52`、`app.js:3890`
- 角色不能连接后升级：`app.js:4934/5018`（`authorizedRole===VIEWER` 时按钮禁用、toggleControl 仅弹提示）
- 配对码 TTL 仅 2 分钟单次使用（加剧手输问题）：`PairingAuthService.java:113-114`、`ServerAccessConfig.java:21`
- 服务地址需手填端口：`app.js:3873`（`127.0.0.1[8765]`）

## Requirements

### 深链 + 二维码自动填充
- 服务端在生成配对码时，同时产出一个**深链 URL**：指向 Web Remote 页面自身、并在 URL 参数（fragment 优先，避免进日志/Referer）中携带一次性码，形如 `http://<host>:<port>/#pair=<code>`（地址即页面同源，天然带 host:port）。
- 服务端在设置栏 / status 展示该深链对应的**二维码**（由 `wr-ux-server-discovery` 负责生成/展示位；本任务负责前端消费深链参数）。
- 浏览器加载页面时若 URL 含 `#pair=<code>`（及可选 role），自动填充配对码字段与服务地址（同源则地址即 `location.origin`），并**清除 URL 中的敏感参数**（`history.replaceState`）避免残留/分享泄露。
- 自动填充后不自动提交，仍需用户点\"配对并连接\"（保留用户可见确认），除非 URL 显式带 `autoconnect=1`（可选，默认关）。
- 深链/二维码**不得**携带长期凭据；只带一次性、短期、单次使用的配对码（复用现有 `PairingAuthService` 语义）。

### 连接后角色升级
- 已连接的 VIEWER 可在**不断开 UI socket**的情况下，向服务端请求升级为 CONTROLLER（新增**增量、可选**的 C2S 消息，如 `type:"ROLE_UPGRADE_REQUEST"`）。
- 服务端按现有权限策略裁决；批准则通过既有 `ROLE`/`WELCOME` 通道下发新 `role`/`authorizedRole`，前端复用现有 `setPermissionRole`/`setAuthorizedRole` 生效，无需重连。
- 升级可能要求二次配对码（若服务端策略要求 CONTROLLER 级配对）——此时前端提示输入码并走升级请求，仍不断线。
- 服务端拒绝时给出可读原因（本地化协同 `wr-ux-error-i18n`），按钮回到可重试状态。
- 旧服务端不认识升级消息时：前端优雅降级为\"需重新配对\"提示（保留旧行为），不报错崩溃。

### 兼容
- 深链参数解析对缺失/畸形值必须健壮（bound、忽略未知）。
- 新增消息为增量可选；旧浏览器不发升级消息、旧服务端忽略未知消息，两组合仍工作。

## Acceptance Criteria

- [ ] 扫描服务端二维码 / 打开深链后，浏览器自动填入服务地址与配对码，用户点一次即完成连接。
- [ ] ��链中的配对码在页面加载后从地址栏被清除（不残留于 URL/历史/分享）。
- [ ] 深链只含一次性短期码，不含长期凭据；过期码走正常失败提示。
- [ ] 已连接的 VIEWER 能在不断线的情况下请求并（策略允许时）获得 CONTROLLER，`app.js` 控制按钮/角色状态正确更新。
- [ ] 服务端策略要求 CONTROLLER 配对时，前端能在不断线下提示补码并完成升级。
- [ ] 升级被拒时有可读原因且按钮可重试。
- [ ] 旧服务端 + 新浏览器：升级优雅降级为\"重新配对\"提示，不崩溃；新服务端 + 旧浏览器：仍可正常观看/配对。
- [ ] wire `1.1`、`/ws/ui`、参与者身份、held 输入不变。
- [ ] 桌面与手机各一次人工冒烟：扫码→连接→（VIEWER）升级协控 全流程记录。

## 约束

- 复用父任务边界：协议仅增量可选扩展；不改 wire/API 版本。
- 二维码库：优先纯前端轻量内联生成（避免新增重依赖），或由服务端渲染为图片/SVG——具体在 design.md 决定，遵循\"不引入重型新依赖\"。
- 敏感参数走 URL fragment（`#`）而非 query，减少进入服务端日志/Referer 的面。

## Out of Scope

- 服务端 URL/码/二维码的**展示位与生成**（属 `wr-ux-server-discovery`；本任务与其约定共享\"access info\"契约：scheme/host/port/url/code/expiry + 深链格式）。
- 全面错误本地化（属 `wr-ux-error-i18n`；本任务新增文案需补 en/zh key）。
