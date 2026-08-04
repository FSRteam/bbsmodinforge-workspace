# P0 服务端:URL 与配对码可发现性 — 技术设计

## 1. Boundaries(边界:改哪些类/方法)

本子任务只动 `bbs-web-remote/`,不碰 `new/`(FSR core),不改 wire/API 版本、不引新框架。触及面:

| 文件 | 现状 | 本任务改动 |
|---|---|---|
| `client/server/ServerAccessConfig.java` | 不可变网络策略 record,已持有 `publicOrigin` | 只读复用;新增 `advertisedScheme()`/`advertisedPort()` 便捷派生方法(纯函数,基于已有字段),不改构造与校验 |
| `client/server/LanAddressResolver.java`(新) | 无 | 新增:枚举 `NetworkInterface`,解析站点内 IPv4 候选 |
| `client/server/RemoteAccessInfo.java`(新) | 无 | 新增:跨子任务复用的规范"访问信息"形状(scheme/host/port/url/code/expiry) |
| `client/BBSWebRemoteServer.java` | `accessConfig()`/`boundPort()`/`currentPairingCode()`/`issuePairingCode()` 已存在 | 只读复用,不改;URL/码组装留在 client 层 |
| `client/BBSWebRemoteClient.java` | `startServer()` 同步启动、失败只 `LOGGER.error`;`logStarted()` 明文码进 `LOGGER.warn`;`settingsStatusText()` 回显 `bindHost:port`(LAN 为 `0.0.0.0`);配对码塞进 volatile `lastOperationMessage` | 核心改动:自动启动移到 `lifecycle` 异步;新增持久访问态(URL + 配对码 + 到期);新增 supplier 文本方法;`logStarted` 去除明文码;新增游戏内可见通道 |
| `client/settings/WebRemoteSettingsClient.java` | marker-value 工厂只渲染 status+message+3 按钮 | 新增可复制 URL 字段 + 独立配对码字段(带 TTL 倒计时) + 复制按钮 |
| `settings/WebRemoteSettings.java` | `RuntimeValue extends ValueString` 单一 marker | 复用同一 `RuntimeValue`;不新增持久化字段(码/URL 是运行态,不落盘) |
| `resources/assets/bbswebremote/assets/strings/en_us.json` + `zh_cn.json` | 现有 config 文案 | 新增本任务可见文案 key(en/zh 同步) |
| `src/serverTest/.../*TestMain.java` + `build.gradle` | `check` 已挂 `testServerSmoke` 等 JavaExec | 新增 `LanAddressResolver` / URL 组装 / TTL 的可执行断言测试,挂进 `check` |

## 2. LAN IPv4 解析(`LanAddressResolver`)

目标:LAN 模式绝不展示 `0.0.0.0`,而是真实本机 IPv4。

算法(纯 JDK,无新依赖):

```text
for NetworkInterface nic in NetworkInterface.getNetworkInterfaces():
    skip if !nic.isUp() || nic.isLoopback() || nic.isVirtual()
    for InetAddress addr in nic.getInterfaceAddresses():
        keep if addr instanceof Inet4Address
              && addr.isSiteLocalAddress()   // 10/8, 172.16/12, 192.168/16
              && !addr.isLoopbackAddress()
              && !addr.isLinkLocalAddress()  // 排除 169.254/16
    收集为候选,按 nic index 稳定排序
return List<String>(候选点分十进制)
```

多网卡:返回**全部候选**的有序列表;主候选取第一个(稳定排序保证跨次一致)��设置栏展示主候选,若 >1 则附"(另有 N 个候选)"提示,`/bbswebremote status` 列全部。

失败(列表为空 / `SocketException`):返回空列表。上层据此展示明确文案 `bbswebremote.discovery.lan.unresolved`("无法自动确定 LAN 地址,请手动查看本机 IP"),**绝不回退 `0.0.0.0`**。

解析在 `lifecycle` 线程完成(启动成功回调里),结果缓存进访问态;UI 线程只读缓存,不触发网卡枚举。

## 3. 完整 URL 组装

以 `ServerAccessConfig` + `boundPort` + 解析出的 LAN IP 为输入,派生规范 URL:

- **scheme**:`publicOrigin != null` → `publicOrigin.getScheme()`(可能 `https`);否则 `http`。复用 `ServerRequestValidator.effectivePort` 的 scheme 默认口径。
- **host**:
  - `publicOrigin != null` → `publicOrigin.getHost()`
  - 否则 loopback(`!lanEnabled`)→ `127.0.0.1`
  - 否则 LAN → `LanAddressResolver` 主候选;解析失败 → host 缺省,URL 字段改显 unresolved 文案
- **port**:`publicOrigin != null` → `effectivePort(publicOrigin)`;否则 `server.boundPort()`(真实绑定端口,非 requested)。
- **拼装**:`scheme://host:port`。当 port 等于 scheme 默认端口(http=80/https=443)时省略 `:port`,与浏览器地址栏一致。

组装逻辑集中在一个纯函数 `RemoteAccessInfo.of(ServerAccessConfig, boundPort, List<String> lanCandidates, Optional<PairingChallenge>)`,便于单测且被 `status` 命令、设置栏、后续 onboarding 子任务共用。

## 4. 规范"访问信息"契约(`RemoteAccessInfo`)

供本任务与 `wr-ux-browser-onboarding`(二维码/深链)复用的唯一形状。不可变 record:

```java
public record RemoteAccessInfo(
    String scheme,          // "http" | "https"
    String host,            // 127.0.0.1 | LAN IPv4 | public host;解析失败为 null
    int port,               // effective port
    String url,             // 组装好的完整 URL;host 为 null 时为 null
    boolean lanEnabled,
    java.util.List<String> lanCandidates, // 全部 LAN IPv4 候选(可空)
    String code,            // 6 位配对码;无则 null(loopback / 未生成)
    java.time.Instant codeExpiresAt       // 码到期;无则 null
) {}
```

约定:
- **只承载可公开信息**。`code` 是一次性短期配对码(非长期 HMAC secret / 可复用凭据),符合父任务安全边界;onboarding 子任务序列化进二维码/深链的是本 record,而非鉴权 secret。
- host/url 为 `null` 表示"LAN 未解析",消费方须走 unresolved 文案分支,不得自造 `0.0.0.0`。
- 该 record 由 client 层在 `lifecycle` 线程构造并缓存;跨线程只读传递。

## 5. marker-value 展示(不阻塞 UI 线程)

现有机制:`WebRemoteSettings.RuntimeValue`(`ValueString` 子类)在 `WebRemoteSettingsClient.install()` 里经 `UIValueMap.register(...)` 绑定一个工厂,工厂用 `UI.label((IKey) BBSWebRemoteClient::settingsStatusText, 20)` 这种 **supplier 式 IKey**——每帧渲染时回调取值。这是天然的"轮询只读"通道。

沿用同一模式新增只读 supplier(全部读缓存的 `volatile`/`Atomic` 字段,零阻塞、零网络、零网卡枚举):

- `settingsUrlText()` — 返回缓存 `RemoteAccessInfo.url()`,或 unresolved / stopped 文案。
- `settingsPairingText()` — 返回配对码 + TTL 倒计时,基于缓存的 `code`/`codeExpiresAt` 与 `Clock` now **纯计算**得剩余秒;过期返回"已过期,请重新生成"。**与 `lastOperationMessage`(`Working...`/apply/stop 结果)彻底分离**,满足"独立、持久字段"要求。

设置栏布局(在现有 status/message/actions 之上追加):
- 一行 URL:`UILabel(settingsUrlText)` + `UIButton(copy_url)` 调 `Window.setClipboard(url)`(仅 URL 非空时可点)。
- 一行配对码:`UILabel(settingsPairingText)` + `UIButton(copy_code)` 调 `Window.setClipboard(code)`(仅有效码时可点)。
- 复制按钮加 `tooltip(...)` 说明。

TTL 倒计时靠 supplier ��帧重算实现,无需定时器、无需 UI 线程等待。过期后 supplier 自然显示过期文案;`Clock` 用 `Clock.systemUTC()` 与服务端 `PairingChallenge.expiresAt()` 同源。

## 6. 自动启动异步化 + 游戏内反馈

现状:`onClientStarted` 回调(客户端线程)里 `startServer()` 同步调 `startRuntimeServer(...)`,端口慢/占用会卡客户端启动;失败只 `LOGGER.error`。

改动:
- `startServer()` 里把 `startRuntimeServer(requestedPort, false)` 包进 `this.lifecycle.execute(() -> {...})`(已有单线程 `bbs-web-remote-lifecycle` executor,`submitSettingsOperation` 同款用法),回调线程立即返回。
- 成功:在 lifecycle 线程解析 LAN、组装 `RemoteAccessInfo`、写缓存;再经 `Minecraft.getInstance().execute(...)` 切回渲染线程推送游戏内可见反馈(见 §7)。
- 失败:写缓存态(设置栏 status 显示失败原因,复用 `conciseMessage`),同样切渲染线程推送可见提示;`LOGGER.error` 保留但不含码。

兼容:手动 `applyConfiguredSettings()` 已在 lifecycle 上,复用同一"成功→缓存+反馈"收尾路径,避免两套逻辑。

## 7. 游戏内可见配对码(绝不记明文日志)

`logStarted()` 当前 `LOGGER.warn("...pairing code {}...", challenge.code())` 明文进 latest.log —— 违反安全约束,移除码占位符,改为:`LOGGER.info("BBS Web Remote LAN active on {}:{}; one-time pairing code generated (see settings panel)", host, boundPort)`(不含码)。

游戏内可见通道(自动启动无 `CommandSourceStack`):经 `Minecraft.getInstance().gui.getChat().addMessage(Component.literal(...))` 在渲染线程推送"Web Remote 已启动,URL/配对码见设置面板"提示(提示本身不含明文码,引导去设置栏读;或按 PRD 可选直接在聊天显示码——默认走"引导去设置栏",把明文码限制在本地 UI 面板与剪贴板,不进任何日志/聊天历史文件)。配对码明文的唯一出口是设置栏字段 + 用户主动点"复制"。

## 8. 数据流

```text
[启动/apply] lifecycle 线程
  server.start() -> boundPort
  LanAddressResolver.resolve() -> List<String>(仅 LAN 模式)
  server.currentPairingCode()/issuePairingCode() -> PairingChallenge(LAN)
  RemoteAccessInfo.of(access, boundPort, lan, challenge)
  -> 写入 volatile accessInfo 缓存
  -> Minecraft.execute(推送聊天提示 / 设置栏态刷新)

[设置栏渲染] UI 线程(每帧,纯读)
  settingsUrlText()     -> accessInfo.url() 或 unresolved 文案
  settingsPairingText() -> code + (expiresAt - now) 倒计时 或 过期/无码文案
  copy 按钮 -> Window.setClipboard(url / code)

[/bbswebremote status] 命令线程
  读同一 accessInfo 缓存 -> sendSuccess(完整 URL + LAN 候选列表)
```

## 9. 兼容性与回滚

- **协议/版本**:不动 wire `1.1`、Addon/API `2.0`、`/ws/*`、身份与 held 语义。`RemoteAccessInfo` 是纯客户端内部形状,不入 wire。旧/新服务端与浏览器组合不受影响。
- **安全不回归**:loopback 默认、LAN 显式开启 + 一次性码 + 短期 token、`allowed_hosts` fail-closed、Host/Origin 校验、token 不入日志——全部保留;本任务额外**移除**了原先明文码进日志这一既有泄漏点(净收紧)。
- **持久化**:不新增落盘设置字段(URL/码是运行态);`RuntimeValue("runtime")` 注册与既有配置读写不变,老配置文件照常加载。
- **回滚**:改动集中在 client 展示层 + 一个异步 `execute` 包裹 + 两个新类。回滚 = 还原 `BBSWebRemoteClient`/`WebRemoteSettingsClient` 两文件并删除 `LanAddressResolver`/`RemoteAccessInfo` 及新 i18n key,不影响服务端与协议。

## 10. Risks

- **网卡枚举误选**:VPN/Docker/虚拟网卡可能给出站点内 IPv4。缓解:过滤 `isVirtual()` + 稳定排序 + 展示全部候选让用户挑;解析不确定时宁可多列候选,不猜单一值。
- **多 NIC 主候选漂移**:靠 nic index 稳定排序固定顺序,避免每次启动 URL 跳变。
- **publicOrigin 端口口径**:必须与 `ServerRequestValidator.effectivePort` 一致(https 默认 443/http 默认 80),否则展示 URL 与鉴权校验不符。复用同一 helper 口径。
- **线程可见性**:accessInfo 缓存须 `volatile`(或 `AtomicReference`),lifecycle 写 / UI 读跨线程;倒计时用同源 `Clock` 防时钟漂移显示负数。
- **聊天推送线程**:`gui.getChat().addMessage` 必须在渲染线程,务必经 `Minecraft.getInstance().execute(...)`,不得在 lifecycle 线程直呼。
- **码泄漏面**:确保码只经设置栏 supplier + 剪贴板出现,`toString`/日志/异常消息一律不带码。
