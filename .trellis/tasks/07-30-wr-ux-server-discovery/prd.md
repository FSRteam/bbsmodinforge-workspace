# P0 服务端：URL 与配对码可发现性

## Goal

让游戏内用户无需翻日志、无需猜测，就能知道**该在浏览器里打开哪个地址**、**该输入哪个配对码**。当前所有状态只回显 `bindHost:port`（LAN 下还是 `0.0.0.0`），一次性配对码在自动启动时只写进 `LOGGER.warn`。本任务把 URL 与配对码变成游戏内可见、可复制、稳定的一等信息。

## 依据（审计 file:line）

- URL 从不展示：`BBSWebRemoteClient.java:322/362/744`、`WebRemoteSettingsClient.java:31-56`
- LAN 显示 `0.0.0.0`：`BBSWebRemoteClient.java:322/363`
- 自动启动码只进日志：`BBSWebRemoteClient.java:201/208-224/727-746`
- 配对码存进可被覆盖的临时状态串：`BBSWebRemoteClient.java:418-435/391`
- 设置栏无 URL、无持久码字段：`WebRemoteSettingsClient.java:31-56`
- 自动启动同步阻塞客户端回调线程：`BBSWebRemoteClient.java:201→217`
- 自动启动失败只写日志：`BBSWebRemoteClient.java:222`

## Requirements

### 展示可用 URL
- 服务运行时，设置栏与 `/bbswebremote status` 必须展示**完整可用 URL**：`http://<host>:<port>`（HTTPS/public_origin 配置时按其 scheme/host/port）。
- Loopback 模式展示 `http://127.0.0.1:<port>`。
- LAN 模式**不得**展示 `0.0.0.0`；需解析并展示本机实际 LAN IPv4（可能多个网卡，展示全部候选或主候选 + 提示）。解析失败时明确提示"无法自动确定 LAN 地址，请手动查看本机 IP"，而非回退 `0.0.0.0`。
- URL 文本可整体复制（设置栏提供可复制字段/marker-value）。

### 配对码可见可复制且稳定
- 生成的一次性配对码在设置栏有**独立、持久**字段展示，直到过期或被使用，不与 `lastOperationMessage`（`Working...`/apply/stop 结果）共用同一 volatile 串。
- 自动启动生成的码必须进入游戏内可见通道（设置栏字段 + 可选聊天/toast），不再仅 `LOGGER.warn`。
- 码字段展示剩余有效期（或"约 N 秒后过期"），过期后清空并提示重新生成。
- 码��身**不写入普通日志**（保持现有安全约束）；日志可写"已生成配对码（见设置栏）"这类不含码的行。

### 线程与失败反馈
- 自动启动的 Jetty `start()` 必须走 `lifecycle` 线程池/异步，不在 `onClientStarted` 回调线程同步执行。
- 自动启动失败必须有游戏内可见提示（设置栏状态 + 聊天/toast），不再仅 `LOGGER.error`。

## Acceptance Criteria

- [ ] 服务运行时，设置栏与 `/bbswebremote status` 显示完整 `http://host:port`；loopback 显示 `127.0.0.1`，LAN 显示真实本机 IPv4 而非 `0.0.0.0`。
- [ ] LAN IP 无法解析时给出明确文案，不显示 `0.0.0.0`。
- [ ] 配对码在设置栏独立字段可见、可复制，展示剩余有效期，过期自动清空。
- [ ] 自动启动生成的码在游戏内可见（不需翻 latest.log）。
- [ ] 配对码明文不出现在 latest.log/debug.log。
- [ ] 自动启动不阻塞客户端回调线程（端口慢/占用时客户端启动不卡顿）。
- [ ] 自动启动失败有游戏内可见提示。
- [ ] 现有 loopback 默认、LAN 显式开启、fail-closed、token 不入日志等安全行为不回归。
- [ ] `bbs-web-remote/` 编译通过并通过相关单测。

## 约束

- 复用父任务边界：不改 wire/API 版本，不引入新框架。
- URL/码展示走 FSR 原生 settings registry 的 `UIValueMap` marker-value 适配（现有机制，见 `WebRemoteSettingsClient`），不得阻塞 UI 线程。

## Out of Scope

- 二维码/深链本身（属 `wr-ux-browser-onboarding`；本任务只需保证 URL 与码可被那边复用，接口在 design.md 约定）。
- 错误串本地化的全面覆盖（属 `wr-ux-error-i18n`；本任务新增的可见文案需补 en/zh key，但不负责历史遗留串）。
