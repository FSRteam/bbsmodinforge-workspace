# FSR 网页预览与远程控制

## Goal

为 NeoForge 1.21.1 的 FSR/BBS 提供一个独立客户端 addon，使用户能在电脑或手机浏览器中低带宽预览并控制正在运行的 BBS 界面。网页应重放 FSR 实际产生的绘制结果与输入链，而不是重新实现一套 BBS 业务 UI。

## Confirmed Facts

- FSR 核心位于 `new/`，当前仍在同步最新 FS；实现以开始开发时的 `new` 提交与工作树为基线，不回写或同步 `old2.0/bbs-fs`。
- 插件位于独立目录 `bbs-web-remote/`，不并入 `new` 的单模块构建。
- Addon/API 版本保持精确的 `2.0`，不改版本号；扩展必须是增量兼容的。
- FSR 已预留 `CLIENT_UI` 和 `CLIENT_RENDER` 能力，但没有公开 UI 帧镜像接口。
- `UIScreen`/`UIBaseMenu` 是原生界面与输入入口，`Batcher2D` 是大多数 2D 绘制的集中点。
- Film/Replay、模型与粒子等复杂内容不能完整转换为 2D 指令，需要作为独立栅格 surface 传输。

## Requirements

### Product scope

- 首版依赖 Minecraft/FSR 客户端正在运行，只镜像当前活动的 BBS `UIScreen`；不创建无头第二套 UI 生命周期。
- 浏览器可只读观看，也可作为具名协控参与者。允许多个已授权浏览器连接，但同一时刻只有一个手势/文本焦点所有者；指针展示与观看不互斥。
- 电脑与手机浏览器共用同一画布协议。手机优先横屏，支持缩放、平移、触控拖动、滚轮手势、虚拟鼠标键和 Shift/Ctrl/Alt。
- 网页可请求打开 BBS 界面，但所有实际输入必须回灌到 FSR 原生输入路径，并在 Minecraft 客户端线程执行。
- Film 面板预览和世界 Replay 回放画面属于 MVP；其播放、暂停、时间轴等操作仍通过原生 BBS UI 控制。
- MVP 不包含音频，不包含浏览器端重新实现 BBS 控件，不包含公网中继、文件上传或多人同时编辑。

### Multi-user collaboration

- 插件新增可选的 NeoForge 协作层：多个安装 FSR 与本插件的 Minecraft 客户端可在同一服务器上进入同一 Film 协作会话，每个参与者保留自己的本地 BBS UI。
- 协作同步以 Film/Replay 的语义化细粒度变更为主，包含参与者、编辑器类型、Replay 选择、时间轴、光标和选区；完整 Film 快照只用于加入、显式保存和缺失重同步。
- 服务器拥有会话成员、权限、单调序号和快照来源的最终决定权。客户端远端应用必须在 Minecraft 线程执行，并使用嵌套安全的 echo-suppression 防止回传循环。
- 网页同屏协控与 Minecraft 语义协作是两层协议：网页仍传输绘制结果和输入；Minecraft 客户端之间同步 Film 语义数据，不把浏览器像素输入伪装成 Together 数据包。
- 每个浏览器参与者独立维护 held keys/buttons。手势所有权释放、WebSocket 断开、超时、换屏和会话退出都只释放该参与者状态，并立即允许下一位协控者接管。
- 默认关闭跨客户端协作；创建/加入协作会话必须显式进行。首版不承诺无冲突 CRDT，同一语义路径的并发写入由服务器序号决定最后写入者，并触发必要的重同步。

### FSR API 2.0 extension

- `new/` 只暴露客户端专用、平台稳定的 UI Mirror API，复用现有 `CLIENT_UI`/`CLIENT_RENDER` capability。
- 公共 API 不得暴露 `UIElement`、`UIBaseMenu`、`GuiGraphics`、OpenGL texture id、本地文件路径或可变 manager。
- 镜像包含 session 生命周期、逻辑尺寸、单调帧序号、不可变绘制命令、资源引用、栅格 surface 引用以及输入提交结果。
- 首版绘制命令至少覆盖裁剪、四色矩形、纹理矩形、文字/glyph、2D mesh 和栅格 surface。
- 无订阅者时不捕获帧；addon 回调异常与慢消费者不得破坏渲染线程。
- 旧 API 2.0 addon、v1 addon 和 dedicated server 行为保持不变。

### Transport and bandwidth

- 插件使用单端口 HTTP(S) + WebSocket 作为页面、配对、可靠 UI/输入和 WebRTC
  信令平面；动态 surface 优先使用标准 WebRTC VideoTrack，现有 `/ws/media`
  JPEG 保留为兼容和失败回退。
- WebRTC 使用 ICE 直连优先、外部 STUN 探测、外部 TURN 中继保底。插件不承诺
  P2P 一定直连或消除跨运营商 QoS；UDP/直连失败必须能回退 TURN/TCP/TLS 443，
  WebRTC 整体失败后必须有限次、无循环地回退 WS/JPEG。
- 每个活动物理 surface lane 使用独立 VideoTrack 并通过可靠 `mid -> handle`
  绑定继续参与现有 `SURFACE_QUAD` painter order。首版无音频；最多 4 个 P2P
  viewer，更多观看者继续使用 WS/JPEG 或后续 SFU。
- DataChannel 只承载可丢弃的 pointer/presence/held-state 快照；KEY、BUTTON
  down/up、TEXT、滚轮、租约、配对和设置继续走可靠 WebSocket。
- 线上使用版本化二进制协议；握手、错误与诊断可使用小型 JSON。
- 绘制资源按内容哈希只发送一次，并允许浏览器缓存。
- UI 帧采用关键帧 + 分块差量；连续帧积压时丢弃中间帧，只保留最新帧。
- 协商浏览器原生支持的 WebSocket 压缩，不要求浏览器加载大型自定义解压器。
- 指针移动与 held-state 可合并，按键、按钮按下/释放和文本输入不可静默丢弃。
- 动态 surface 帧率统一限制为 30–120 FPS，不再提供低于 30 FPS 的档位。自动模式手机默认 540p@30，普通桌面默认 720p@60，高性能桌面可选 1080p@120；实际发送率仍受 Minecraft 渲染率、GPU readback、JPEG encoder 与网络 backpressure 约束。
- 浏览器提供可持久化的自定义媒体配置：分辨率上限、JPEG quality 与 30–120 FPS。设置变更只重连媒体 WebSocket，不得重置 UI/control 会话；服务端必须在不可信查询入口验证并限制所有数值。
- 网页提供可展开的连接详情，至少区分 UI 与 JPEG 媒体的应用层码率、接收 FPS、
  分辨率、累计字节、解码/丢弃/重连状态，并明确这些不是 TCP/TLS 线路字节或网络丢包。
- 连接详情还要区分接收、解码、应用到 renderer 与实际 Canvas 绘制，提供有界的
  解码/捕获到绘制延迟、应用 RTT、服务端 UI 投递和媒体全局发送/合并统计；发送完成
  不得冒充浏览器绘制，序号间隔不得冒充网络丢包。
- 自动画质只能在自动模式生效，按独立短窗口保守降档和长时间健康恢复；隐藏页面、
  无活动 surface、媒体断连和主动画质重连不得触发误判。请求 120 FPS 时，正常受
  Minecraft 渲染率限制的 60 FPS 源不得仅因未生成 120 FPS 而自动降档。
- 动态 surface 与 UI 指令是独立流，可分别限帧和降质。媒体 JPEG 不再进行 WebSocket deflate，编码与每客户端发送均采用有界 latest-wins，慢客户端不得反压渲染线程或可靠输入流。
- 性能目标：无视频时平均 UI 增量帧不超过 48 KiB；捕获、哈希与差量平均不超过 4 ms；渲染线程不得同步等待网络或图片编码。

### Security and lifecycle

- 默认只监听 loopback。局域网访问必须显式开启，并使用一次性配对码换取短期会话令牌。
- 本地客户端命令可在游戏运行中查询、停止、恢复服务，并将监听端口改为
  `1024..65535`；端口切换不得隐式开启 LAN、放宽 Host allowlist，失败时旧服务
  必须继续可用。
- 插件必须通过 FSR 原生 settings registry 注册独立的 Web Remote 设置栏，持久化
  自动启动、端口、LAN、绑定地址和精确 Host 白名单，并在同一栏显示实时运行状态，
  提供显式应用/启动、停止和生成一次性配对码操作。设置操作不得阻塞客户端线程。
- FRP/反向代理使用一个可选、精确的公网 Origin（scheme/host/port）授权自定义公网
  端口和 HTTPS/WSS；不得信任任意 `X-Forwarded-*`。公网入口不替代 LAN 配对和
  非空直连 Host 白名单。
- 校验 Origin、会话权限、消息长度、速率与序号；令牌不得写入普通日志。
- 断连、换屏、世界卸载或客户端停止时，控制租约、held 输入状态、surface 资源和网络服务必须幂等释放。
- 所有网络队列有明确上限；慢客户端不得无限占用内存。

## Acceptance Criteria

- [ ] `new/` 编译通过，Addon/API 对外版本仍为 `2.0`，旧 facade 与 capability 行为不回归。
- [ ] `bbs-web-remote/` 可独立构建为 NeoForge 1.21.1 客户端 mod，并声明对 FSR/BBS 的必需依赖。
- [ ] 打开任意 BBS `UIScreen` 后，桌面浏览器能连接并看到由绘制命令重放的核心 2D UI；关闭/换屏后收到正确 session 变更。
- [ ] Film preview 与 world Replay 的可见画面能作为 surface 合成到对应 UI 区域，不以整页 DOM 重写替代。
- [ ] 浏览器的移动、点击、拖拽、滚轮、键盘、文本输入和 held modifier 能沿原生 BBS 输入链生效。
- [ ] 画布获得焦点时，桌面可打印按键同时产生物理 KEY 与原生 TEXT 输入；Ctrl/Alt/Meta
  快捷键和隐藏 IME/composition 路径不重复产生 TEXT。
- [ ] 手机横屏可缩放/平移画布并完成至少：点击按钮、拖动时间轴、滚动列表、输入文字。
- [ ] 首次资源传输后，未变化的资源不重复发送；未变化绘制块不重复发送；慢客户端只接收最新可用帧。
- [ ] 自动/流畅/高清/原画和自定义媒体设置均不低于 30 FPS；自定义 FPS 被严格限制在 30–120，分辨率/JPEG quality 被限制在 Core 允许范围，刷新页面后设置仍保留。
- [ ] 连接详情可展开查看脱敏入口、两条 WebSocket 状态、UI/媒体接收 FPS、应用码率、
  分辨率、累计帧/字节、媒体解码丢弃和重连计数，不泄露 Cookie、配对码或令牌。
- [ ] 详情区显示 receive/decode/apply/paint、解码 P50/P95、捕获到绘制 EWMA/P95、
  RTT/时钟不确定度以及服务端 UI/媒体投递统计，并清楚标明媒体统计是全局聚合、
  latest-wins 合并不是网络丢包、发送完成不是浏览器绘制。
- [ ] 自动画质覆盖严重立即降两档、连续异常降一档、15 秒冷却、30 秒健康恢复、
  隐藏/无 surface/主动重连暂停、手动模式禁用和最低档保持；正常 60 FPS 源在
  120 FPS 请求下不会因请求值本身被误判。
- [ ] 协议测试覆盖关键帧、差量重建、缺帧重同步、消息上限、非法输入和资源去重。
- [ ] 默认监听地址为 loopback；LAN 模式未经配对无法观看或控制；多个网页控制者具有独立身份，但手势/文本焦点所有权互斥且可超时回收。
- [ ] `/bbswebremote start [port]|stop|restart [port]|status` 可在游戏内管理 Web
  服务；运行时停止不会销毁 UI 镜像，换端口可恢复当前会话资源，目标端口冲突时
  原服务保持在线。
- [ ] FSR 设置界面包含独立 Web Remote 模块；保存值后仅在显式应用时重启传输，
  默认仍为 loopback，LAN 白名单为空时失败关闭，状态和操作结果可在该模块查看。
- [ ] 配置精确公网 Origin 后，FRP 异端口 HTTP 与 HTTPS/WSS Host/Origin 可通过校验；
  错误 scheme/host/port/path 失败关闭，HTTPS 配对 Cookie 带 `Secure`。
- [ ] 支持 `AUTO / RELAY_ONLY / WS_ONLY` 媒体策略；AUTO 在可达时建立 P2P
  WebRTC VideoTrack，受限 NAT 可使用 TURN，ICE 失败或超时后保持 UI/控制在线并
  原子回退 `/ws/media`，不出现黑屏或重连循环。
- [ ] SDP/ICE 信令绑定已认证 UI participant/session/generation；限制 SDP、candidate
  数量/长度和协商频率，不记录候选 IP、完整 SDP、TURN 密钥或临时凭据。
- [ ] WebRTC 详情显示 signaling/ICE/selected path、codec、track/FPS/bitrate/RTT，
  区分 direct 与 relay；不会把 surface 序号 gap 冒充 WebRTC `packetsLost`。
- [ ] Viewer 不能通过 DataChannel 注入可靠输入或绕过协控租约；DataChannel 失败
  透明回 WS，session/token/server stop 会幂等关闭 PeerConnection 和所有 track。
- [ ] 两个 Minecraft/FSR 客户端能通过服务器加入同一协作会话；细粒度 Film/Replay 更新、远端光标/选区、加入快照、断线清场和缺失重同步可验证。
- [ ] 远端语义更新不会回声广播；并发更新具有服务器单调序号，同一路径冲突结果可预测。
- [ ] `new/` 与插件均通过构建和相关单元测试；完成桌面与手机浏览器的人工冒烟验证记录。

## Out of Scope for MVP

- 音频及音视频同步。
- 插件自带的公网 TURN/SFU/云账户服务；用户可配置独立 coturn/STUN/TURN。
- 浏览器端无 Minecraft 客户端的独立 BBS 编辑器。
- 将 BBS 密集桌面 UI 重排为响应式 DOM。
- 无服务器协调的多客户端编辑、无冲突 CRDT、资源上传和任意本地文件访问。

## Notes

- 用户已在方案讨论后明确要求开始实现；上述 active-screen 与无音频边界作为 MVP 默认值，后续可增量扩展。
