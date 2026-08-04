# P1 错误人话化与本地化

## Goal

消除\"英文开发者串塞进中文 UI\"与\"失败无原因\"两类问题。前端把协议/网络错误映射为面向用户的人话；服务端把配对失败、origin/host 拒绝、限流等**折叠成模糊码**的路径拆成可区分、可读原因；LAN 空白名单静默回退 loopback 改为显式提示；补全运行时状态、命令输出、配对码、错误消息的本地化 key（en/zh）。

## 依据（审计 file:line）

- 前端错误原样透传英文串：`app.js:5222-5225`、`protocol.js:643-651/526/604`
- 配对/校验错误为机器码：`AuthenticatedHttpHandler.java:157/104/152/113`（`pairing_failed`/`origin_rejected`/`rate_limited`/`json_required`）
- Origin/Host 拒绝与故障无法区分：`AuthenticatedHttpHandler.java:56/104`
- redeem 失败折叠为单一 INVALID：`PairingAuthService.java:87-94`
- LAN 空白名单静默硬失败回退：`ServerAccessConfig.java:43` 被 `BBSWebRemoteClient.java:477-490` 吞掉
- 运行时状态/消息硬编码英文：`BBSWebRemoteClient.java:313-325/334-347/255-258/272/286/352-370`
- 本地化只覆盖 config 标签：`strings/en_us.json:2-33`、`zh_cn.json:2-33`；实际路径 `bbs-web-remote/src/main/resources/assets/bbswebremote/assets/strings/`

## Requirements

### 前端错误人话化
- 建立**错误码→本地化消息**映射表：`protocol.js` 的 `ProtocolError` code（如 `SURFACE_FRAME_JPEG_LENGTH`）与网络/HTTP 失败，映射为中文人话（英文 locale 保留英文）。
- `reportError`/`note` 展示映射后的人话，而非 `error.message` 原始英文；保留原始技术串到 `console.error` 与诊断（供开发者），不进用户 toast。
- 远程错误（服务端经 decodeError 透传）同样走映射；未知 code 有合理兜底文案（\"发生未知错误（<code>）\"）。

### 服务端拒绝原因可区分
- `PairingAuthService.redeem` 失败区分至少：**码错误** / **码已过期** / **无挑战/未生成** / **限流**，返回可区分的结构化原因（不泄露是否存在过某码的时序信息——安全与可用性平衡在 design.md 说明）。
- `AuthenticatedHttpHandler` 的 `pairing_failed`/`origin_rejected`/`rate_limited`/`json_required` 等在响应体带**可本地化的原因标识**（稳定的 reason code + 可选人类可读消息），前端据此展示人话（如\"该地址不在 LAN 白名单\"\"配对码已过期，请在游戏内重新生成\"）。
- Origin/Host 拒绝要能与\"服务故障\"区分（不同 reason code）。

### LAN 回退显式化
- LAN 开启但 `allowed_hosts` 为空导致的 fail-closed 回退 loopback，不再被 `BBSWebRemoteClient.java:477-490` 静默吞成日志；必须给游戏内可见提示：\"LAN 未启用：Host 白名单为空，已保持仅本机访问\"。
- 非法 public_origin/LAN host 被清洗时同样给可见提示，而非静默丢失。

### 本地化补全
- 为运行时状态（`STOPPED`/`RUNNING`/`Ready`/`Working...`）、命令输出、配对码提示、新增的所有用户可见文案补 `en_us.json` 与 `zh_cn.json` key。
- 服务端面向用户文本改用 `Component.translatable` + key，替换裸 `Component.literal`/字面串（限本任务触及的用户可见路径）。

## Acceptance Criteria

- [ ] 前端不再出现英文开发者串直接进 toast；协议/网络/远程错误显示中文人话（en locale 显示英文），未知 code 有兜底文案。
- [ ] 原始技术串仍可在 console/诊断中查到（不丢失调试信息）。
- [ ] 配对失败能区分\"码错误 / 已过期 / 未生成 / 限流\"并给对应提示。
- [ ] Host/Origin 拒绝显示\"不在白名单\"类原因，且与服务故障可区分。
- [ ] LAN 空白名单回退 loopback 时有游戏内可见提示，不再静默。
- [ ] 运行时状态、命令输出、配对码提示均本地化（en/zh），无裸英文残留于触及路径。
- [ ] token/配对码明文不因本地化改动而进入日志。
- [ ] `new/`（若触及）与 `bbs-web-remote/` 编译通过；wire/API 版本不变���

## 约束

- 协议仅增量：响应体新增 reason code 字段为可选，旧前端忽略仍工作。
- 安全：区分失败原因时不得引入可被利用的用户枚举/时序侧信道（在 design.md 权衡；必要时对外统一\"码无效或已过期\"但对**本机游戏内**用户可给更细原因）。
- 与 `wr-ux-server-discovery` / `wr-ux-browser-onboarding` / `wr-ux-transport-feedback` 新增的用户文案共用同一套 lang key 约定（本任务提供 key 规范）。

## Out of Scope

- 新增语言（仅维护现有 en/zh 两 locale）。
- 交互文案收敛（控制权 permission/role/lease 文案属 `wr-ux-interaction-polish`，本任务只保证其可本地化）。
