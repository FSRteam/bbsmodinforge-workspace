# 前端架构重构：app.js 模块化拆分 + UX 痛点修复

## Goal

把 `bbs-web-remote/` 浏览器端 5852 行单体 `app.js` 拆成按职责域的模块化文件，引入清晰的状态管理分层，保持**行为、协议、DOM 结构语义不变**。重构过程中顺手实现 `wr-ux-transport-feedback` / `wr-ux-error-i18n` / `wr-ux-interaction-polish` 三个**纯前端** UX 痛点的修复（toast 队列历史、传输降级可见、错误人话化、诊断分级、触摸可发现、demo/空状态引导）。Node 回归（`namespace.WebTest`）必须全绿。

本任务**不重写服务端**，不改 wire `1.1`/Addon/API `2.0`，不改协议编码。服务端已有的配对/角色/媒体逻辑保持原样，只做浏览器端重组与展示层 UX 改进。

## 背景（现状调研摘要）

- `app.js`：单 IIFE，16 个类，`App` 主类 ~2050 行（3758→文件尾），直接引用 ~83 个 DOM 元素 ID，状态扁平混合在 `constructor`。
- 已相对独立的类（高内聚、Node 可测）：`FloatingToolbar` / `TintedImageCache` / `MediaMetrics` / `LinkFeedback` / `AdaptiveQualityController` / `ByteWriter` / `RemoteInput` / `ResourceCache` / `SurfaceCache` / `RtcSurfaceCache` / `HybridMediaTransport` / `DemoFixture`。
- 深耦合待拆：`App`（巨类）、`CanvasRenderer`（~900 行，命令解析+绘制交织）。
- 测试：`webTest/*.test.js` 为无依赖 Node 脚本，`globalThis.window = globalThis` + FakeElement 模拟 DOM，经 `namespace.WebTest` 导出（第 5807-5841 行）加载类。**保持此机制可用是硬约束**。
- `protocol.js` 已独立（47KB），`namespace.BBSWebRemote.Protocol`。

## Requirements

### R1 模块化拆分（重构主体）
- 把 `App` 按功能域拆为独立模块文件，每个文件一个职责域，通过注入/回调解耦，不直接 `getElementById` 满天飞。
- 建议模块域（以 design.md 为准）：连接/传输（socket、epoch、重连）、媒体渲染（renderer/surface/hybrid 的 App 侧编排）、协控（role/lease/participants/dashboard）、输入（App 侧 RemoteInput 编排）、UI/toast（note、队列、历史、诊断面板更新）、demo。
- `App` 瘦身为**状态编排器**：持有子系统实例、编排消息路由与生命周期，具体逻辑下沉到各模块。
- 保持 `namespace.WebTest` 导出完备（现有测试依赖的类/方法不得消失或改名）；`global.BBSWebRemote.app` 启动方式不变。
- **行为不变**：同一 DOM id、同一事件语义、同一协议消息形状。重构只移动代码位置，不改变逻辑（除 UX 修复项）。

### R2 toast 队列 / 历史（transport-feedback 基建）
- `note()` 队列化：多条消息依次展示，同一时刻至多一条可见，其余排队；相同消息短窗口内去重/合并（`×N`）。
- 可展开的历史列表：最近 N 条（时间戳、级别 info/warn/error），事后可回看。
- 保留 `#liveStatus` aria-live 播报最新一条。

### R3 传输降级可见且平滑（transport-feedback）
- WebRTC↔JPEG 回退/升级均有面向用户的可见提示（非内部 state 术语）；降级期间保留上一帧或显示"正在切换视频通道…"占位，避免静默黑屏。
- WebRTC 协商中/停滞/首帧超时/autoplay 被拒 各有对应可见状态或兜底，不留静默黑屏。
- 自动画质换档不再每次重连 media socket 造成闪屏；换档 toast 节流/合并。

### R4 错误人话化（error-i18n 前端部分）
- 建立错误码→本地化消息映射表：`ProtocolError` code 与网络/HTTP 失败映射为中文人话（en locale 英文）。
- `reportError`/`note` 展示映射后人话，原始技术串仅进 console/诊断，不进用户 toast；未知 code 有兜底文案。

### R5 交互打磨（interaction-polish）
- 控制权文案收敛：permission/role/lease 三层向用户收敛为"观看中 / 可控制 / 操作中"，按钮/roleStatus/lease pill/summary 表述一致。
- 诊断面板分级：默认只显示少数关键指标，完整 40+ 字段收进"高级"折叠区，记住展开偏好（localStorage）。
- 触摸可发现 + 防粘滞：工具栏折叠时右/中键与修饰锁仍可达；修饰锁常驻可见提示 + 一键释放；首次触摸一次性手势说明（可关闭）；轻点不产生跨坐标微拖拽。
- demo 与空状态：离线 demo 有可见退出入口；file:// 或 `?demo=1` 有"离线演示"标识；空状态按当前权限正确引导；桌面"需聚焦画布"提示从 sr-only 提升为可见。

### R6 保持测试与兼容
- `verifyWebAppSyntax`（Node 语法检查）与全部 `webTest/*.test.js` 通过。
- `check`（含 JavaExec 与 Node）全绿；wire/API 版本不变。

## Acceptance Criteria

- [ ] `app.js` 按职责域拆分为多个模块文件；`App` 明显瘦身（<800 行）；模块间通过注入/回调解耦，DOM 访问集中。
- [ ] `namespace.WebTest` 导出完备，现有 webTest 全部通过；新增模块级测试覆盖 toast 队列/去重、错误映射、诊断分级、控制权文案收敛。
- [ ] 行为不回归：`browser-protocol`/`session-lifecycle`/`webrtc-hybrid`/`mobile-input`/`render-fidelity`/`floating-toolbar`/`media-reconnect`/`glyph-protocol` 全绿。
- [ ] toast 逐条排队不覆盖、相同消息合并计数、历史可展开回看。
- [ ] WebRTC↔JPEG 切换有可见提示且无长时间纯黑屏；协商中/停滞/首帧超时/autoplay 被拒有可见状态或兜底。
- [ ] 错误显示人话（en/zh），原始串仅 console/诊断，未知 code 有兜底。
- [ ] 控制权文案一致（观看/可控制/操作中）；诊断面板默认精简、高级折叠 + 记住偏好。
- [ ] 触摸端工具栏折叠时右/中键与修饰锁仍可达；修饰锁常驻提示可一键释放；首次触摸手势说明可关闭；轻点无跨坐标微拖拽。
- [ ] 离线 demo 有退出入口 + 可见标识；空状态按权限正确引导；聚焦提示可见。
- [ ] `./gradlew check` 真实 exit code 0 + 日志 BUILD SUCCESSFUL（禁管道吞码）。

## 约束

- 纯前端 `web/`（app.js 拆分 + styles.css + index.html）。不碰服务端 Java，不改 wire/协议。
- 不引入新框架/新构建工具/新 npm 依赖；保持无构建步骤的纯 `<script>` 加载（多文件按序 `<script src>`）。
- 复用现有类结构（`MediaMetrics`/`AdaptiveQualityController`/`HybridMediaTransport` 等），增量改造勿重写。
- 保持 `namespace.WebTest` 导出与 Node 无依赖测试机制（该机制是现有测试安全的根基）。

## Out of Scope

- 服务端错误串本地化、`PairingAuthService`/`AuthenticatedHttpHandler` 的 reason code（属 `wr-ux-error-i18n` 服务端部分，本任务只做前端映射表）。
- 协议语义变更、新 wire 消息、QR 浏览器内生成（`wr-ux-browser-onboarding` 可选部分）。
- 重排 BBS 密集桌面 UI 为响应式 DOM（父任务 Out of Scope）。

## Notes

- 本任务把三个 UX 子任务的**纯前端部分**收拢进来统一在重构中落地；服务端部分仍留在各自子任务（`wr-ux-server-discovery` 已实现；`error-i18n` 服务端、`transport-feedback` 无服务端）。
- 父任务 `07-30-web-remote-ux-humanization` 的跨子任务验收标准（wire 不变、新旧兼容、各 check.jsonl）对本任务生效。
