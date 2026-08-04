# bbs-web-remote UX 人性化（父任务）

## Goal

修复 `bbs-web-remote` 面向真实用户的"非人性设计"。当前实现工程完整度高，但把大量工程约束直接暴露给了用户：连不上（不知道 URL / 配对码）、上不了手（角色选错要断线重配）、静默失败（WebRTC 降级、LAN 回退无提示）、信息过载（40+ 诊断字段、开发者英文错误串）。

本父任务持有**完整需求集、子任务地图与跨子验收标准**，本身不做实现（除非集成层需要）。所有实现落在子任务。

## 边界与约束（对全部子任务生效）

- **不破坏协议兼容**：wire `1.1`、Addon/API 版本 `2.0`、`/ws/ui`、`/ws/media`、参与者身份、held 输入语义均不得改变。所有新增消息/字段必须是**增量、可选、向后兼容**的；旧服务端/旧浏览器组合必须继续工作。
- **不破坏安全模型**：默认 loopback；LAN 显式开启 + 一次性配对码 + 短期 token；token 不入普通日志；Host/Origin 校验与 `allowed_hosts` fail-closed 语义不变。二维码/深链**不得**把长期 HMAC secret 或可复用凭据序列化进静态资源。
- **不阻塞线程**：UI/渲染/客户端回调线程绝不同步等待 Jetty 启停或网络。
- **遵循现有代码风格**：匹配现有 import、命名、格式；不引入新框架/新构建工具。
- **每个子任务独立可验证**：拥有各自可测的验收标准，可独立实现、检查、归档。

## 子任务地图

| 子任务 | slug | 优先级 | 边界 | 依赖 |
|---|---|---|---|---|
| P0 服务端：URL 与配对码可发现性 | `wr-ux-server-discovery` | P0 | Java 服务端 + 设置栏 | 无 |
| P0 浏览器：二维码/深链 + 连接后角色升级 | `wr-ux-browser-onboarding` | P0 | 服务端签发深链/二维码 + 前端消费 + 角色升级消息 | 建议在 server-discovery 之后（复用 URL 解析），但可并行开发，接口先约定 |
| P1 传输与画质反馈 | `wr-ux-transport-feedback` | P1 | 纯前端 app.js | 无 |
| P1 错误人话化与本地化 | `wr-ux-error-i18n` | P1 | 前端串映射 + 服务端拒绝原因 + lang json | 无（与其它并行） |
| P2 交互打磨 | `wr-ux-interaction-polish` | P2 | 纯前端 app.js + css + html | 无 |

> 依赖以子任务 `prd.md`/`implement.md` 内文字为准，不由树位置隐含。角色升级与深链的 C2S/S2C 消息形状由 `wr-ux-browser-onboarding` 与 `wr-ux-server-discovery` 在各自 design.md 中约定为共享契约。

## 需求集（问题→子任务映射）

P0（阻断连接/上手）：
1. 游戏内从不展示可用 URL；LAN 下反显 `0.0.0.0` → `wr-ux-server-discovery`
2. 自动启动配对码只写日志，游戏内零提示 → `wr-ux-server-discovery`
3. 配对码手输、无二维码/深链，2 分钟 TTL 极易过期 → `wr-ux-browser-onboarding`（+ server 签发）
4. 角色不能连接后升级，选错只能断线重配 → `wr-ux-browser-onboarding`

P1（高频困惑/静默失败）：
5. WebRTC↔JPEG 降级完全静默 + 闪屏 → `wr-ux-transport-feedback`
6. 自动画质每次改档重连 media + toast 刷屏 → `wr-ux-transport-feedback`
7. toast 单条 3.2s 覆盖、无历史 → `wr-ux-transport-feedback`
8. 错误英文开发者串塞进中文 UI → `wr-ux-error-i18n`
9. LAN 空白名单静默回退 loopback → `wr-ux-error-i18n`
10. redeem 失败 / Host 拒绝无法区分原因 → `wr-ux-error-i18n`

P2（心智负担/可发现性）：
11. permission/role/lease 三层概念 + 多变按钮文案 → `wr-ux-interaction-polish`
12. 诊断面板 40+ 字段无分级 → `wr-ux-interaction-polish`
13. 触摸右/中键、修饰锁只在工具栏、折叠即消失、粘滞易忘 → `wr-ux-interaction-polish`
14. 单指/多指模型隐式，2 指行为随 zoom 变化 → `wr-ux-interaction-polish`
15. 焦点提示藏 sr-only、demo 无退出、file:// 静默进 demo → `wr-ux-interaction-polish`
16. 本地化仅覆盖配置标签，运行时/命令/码/错误全无 key → `wr-ux-error-i18n`
17. 自动启动同步阻塞客户端回调线程 → `wr-ux-server-discovery`
18. 空状态引导 viewer 去点没权限的按钮 → `wr-ux-interaction-polish`

## 跨子任务验收标准

- [ ] `new/` 与 `bbs-web-remote/` 均编译通过；wire `1.1` / API `2.0` 对外不变；旧 facade/capability 不回归。
- [ ] 全新用户零文档路径可完成：看到完整 URL → 扫码或点深链 → 浏览器自动填充地址+码 → 连接 → 看到画面。桌面与手机各一次人工冒烟记录。
- [ ] 选错"仅观看"的用户可在**不断线**的前提下升级为协控（服务端策略允许时）。
- [ ] WebRTC 降级、LAN 回退、配对失败等此前静默的路径，均有面向用户的可见、可读（本地化）反馈。
- [ ] 旧服务端 + 新浏览器、新服务端 + 旧浏览器两种组合仍能连接工作（增量兼容回归）。
- [ ] 每个子任务各自 `check.jsonl` 通过；父任务做最终集成 review。

## Out of Scope

- 重排 BBS 密集桌面 UI 为响应式 DOM。
- 新增音频、公网 TURN/SFU、云账户。
- 浏览器端重实现 BBS 控件。
- 协议语义变更（仅允许增量可选扩展）。

## Notes

- 本父任务由三方 UX 审计合并而来（人工初审 + 两个 opus 只读审计 agent：浏览器端 / 服务端）。findings 全部有 `file:line` 依据，已归入上表。
- 用户已批准创建任务树并进入规划；实现仍需各子任务 `task.py start` 后进行。
