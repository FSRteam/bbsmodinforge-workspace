# P0 服务端:URL 与配对码可发现性 — 实施计划

有序、可勾选执行计划。每步小、可编译、可验证。全部改动限于 `bbs-web-remote/`。
构建口径:`cd bbs-web-remote && ./gradlew <task>`。**内存红线:绝不信任管道退出码**——每次构建单独检查真实 exit code + 读日志双确认(禁 `gradlew ... | tail` 之类假绿)。

DoD = `prd.md` Acceptance Criteria 全部满足(逐条在 §Review Gates 映射)。

---

## Step 0 — 基线绿(回滚锚点)

- [ ] `cd bbs-web-remote && ./gradlew compileClientJava`,记录真实 exit code。
- [ ] `./gradlew check`(现有 `testServerSmoke`/`testWireProtocol`/... 全绿)作为改动前基线。
- [ ] `git status` 确认工作区干净。**回滚点 R0**:任何后续步骤失败可 `git checkout -- <file>` 回到此。

## Step 1 — 新增 `RemoteAccessInfo` 契约(纯 record,先可编译)

- [ ] 新建 `src/client/java/mchorse/bbs_web_remote/client/server/RemoteAccessInfo.java`。
- [ ] 字段按 design §4:`scheme, host, port, url, lanEnabled, List<String> lanCandidates, code, Instant codeExpiresAt`。
- [ ] 加静态工厂 `of(ServerAccessConfig access, int boundPort, List<String> lanCandidates, Optional<PairingChallenge> challenge)`——纯函数,组装 scheme/host/port/url(design §3),host 解析失败置 `null`(url 也 `null`)。scheme 默认端口省略 `:port`。
- [ ] 加一个 `stopped()` 或空态静态实例供未运行时使用。
- [ ] 验证:`./gradlew compileClientJava`,查真实 exit code。**回滚点 R1**。

## Step 2 — 新增 `LanAddressResolver`

- [ ] 新建 `src/client/java/mchorse/bbs_web_remote/client/server/LanAddressResolver.java`。
- [ ] `static List<String> resolveSiteLocalIpv4()`:枚举 `NetworkInterface.getNetworkInterfaces()`,过滤 `!isUp()||isLoopback()||isVirtual()`,保留 `Inet4Address && isSiteLocalAddress() && !isLinkLocalAddress()`,按 nic index 稳定排序,返回点分十进制列表;`SocketException`/空 → 空列表(不抛、不回退 `0.0.0.0`)。
- [ ] 验证:`./gradlew compileClientJava`,查真实 exit code。**回滚点 R2**。

## Step 3 — 单测:URL 组装 + LAN 解析 + TTL(挂进 check)

- [ ] 新建可执行测试 main,风格对齐现有 `src/serverTest/.../ServerSmokeTestMain.java`(裸 `main` + 断言 + 非零退出即失败),例如 `RemoteAccessInfoTestMain.java`:
  - loopback → `http://127.0.0.1:<port>`,code/url-host 行为正确。
  - publicOrigin=`https://x.example:443` → scheme=https、port=443、`:port` 省略。
  - LAN 多候选 → url 用主候选、lanCandidates 全含。
  - LAN 解析失败(候选空)→ host/url 为 null(unresolved 分支)。
  - TTL 剩余秒纯计算:未过期>0、过期<=0。
- [ ] `build.gradle`:仿照 `testServerSmoke` 注册 `tasks.register('testRemoteAccessInfo', JavaExec)`(`dependsOn serverTestClasses`,classpath 同款),并加入 `tasks.named('check') { dependsOn ... }` 列表。
- [ ] 验证:`./gradlew testRemoteAccessInfo`,查真实 exit code + 读输出确认断言真跑。**回滚点 R3**。

## Step 4 — client 访问态缓存 + supplier 文本

- [ ] `BBSWebRemoteClient.java`:新增 `private volatile RemoteAccessInfo accessInfo`(或 `AtomicReference`),初值空态。
- [ ] 新增 `settingsUrlText()`:读 `accessInfo.url()`,空/未运行/unresolved 各自文案 key。
- [ ] 新增 `settingsPairingText()`:基于 `accessInfo.code()`/`codeExpiresAt()` 与 `Clock.systemUTC()` **纯算**剩余秒;无码/过期各自文案。**不读 `lastOperationMessage`**。
- [ ] 新增 `settingsUrlValue()`/`settingsPairingCodeValue()` 供复制按钮取原始串(可空)。
- [ ] 抽一个 `private void publishAccessInfo(server, access, boundPort)`:在 lifecycle 线程解析 LAN(仅 `lanEnabled`)、取/发码、`RemoteAccessInfo.of(...)`、写 `accessInfo`。
- [ ] 验证:`./gradlew compileClientJava`,查真实 exit code。**回滚点 R4**。

## Step 5 — 自动启动异步化 + 成功/失败游戏内反馈

- [ ] `startServer()`:把 `startRuntimeServer(this.requestedPort, false)` 包进 `this.lifecycle.execute(() -> {...})`;回调线程立即返回(满足"不阻塞客户端回调线程")。
- [ ] 成功分支:调 `publishAccessInfo(...)`,再 `Minecraft.getInstance().execute(() -> gui.getChat().addMessage(...))` 推送"已启动,URL/码见设置面板"(不含明文码)。
- [ ] 失败分支:写 `lastOperationMessage`=`conciseMessage`,清空/标记 `accessInfo`,同样切渲染线程推送可见失败提示;保留 `LOGGER.error`(不含码)。
- [ ] 手动 `applyConfiguredSettings()` 成功收尾复用同一 `publishAccessInfo(...)`,避免双逻辑。
- [ ] 验证:`./gradlew compileClientJava`,查真实 exit code。**回滚点 R5**。

## Step 6 — 移除日志明文码 + status 命令展示完整 URL

- [ ] `logStarted()`:删掉 `challenge.code()` 占位符,改 `LOGGER.info("...pairing code generated (see settings panel)", host, boundPort)`(design §7)。
- [ ] `reportStatus()` / `settingsStatusText()`:改用 `accessInfo` 展示完整 `scheme://host:port`;LAN 列全部���选;loopback 显示 `127.0.0.1`;解析失败显 unresolved 文案——**任何路径不再输出 `0.0.0.0`**。
- [ ] 全仓 grep 确认:`grep -rn "code()" src/client/.../BBSWebRemoteClient.java` 无任何码进 `LOGGER`/`Component.literal` 到聊天历史/日志。
- [ ] 验证:`./gradlew compileClientJava`,查真实 exit code。**回滚点 R6**。

## Step 7 — 设置栏 UI:URL 字段 + 独立码字段 + 复制按钮

- [ ] `WebRemoteSettingsClient.java` 工厂内,在现有 `status/message/actions` 之外追加:
  - URL 行:`UI.label((IKey) BBSWebRemoteClient::settingsUrlText, 20)` + `UIButton(copy_url)` → `Window.setClipboard(BBSWebRemoteClient.settingsUrlValue())`(值空时按钮 disable/no-op)。
  - 码行:`UI.label((IKey) BBSWebRemoteClient::settingsPairingText, 20)` + `UIButton(copy_code)` → `Window.setClipboard(...pairingCodeValue())`。
  - 复制按钮 `tooltip(L10n.lang(...))`。
- [ ] 调整 `content` 高度(现 `h(68)`)容纳新增两行,`labelAnchor(0,0.5F)` 对齐既有风格。
- [ ] 验证:`./gradlew compileClientJava`,查真实 exit code。**回滚点 R7**。

## Step 8 — i18n(en/zh 同步)

- [ ] `en_us.json` + `zh_cn.json` 新增 key(两文件键集必须一致):
  - `bbswebremote.discovery.url`, `...url.copy`, `...url.stopped`, `...lan.unresolved`
  - `bbswebremote.discovery.pairing`(含 `%s` 码 + `%s`/`%d` 倒计时占位), `...pairing.copy`, `...pairing.expired`, `...pairing.none`
  - `bbswebremote.discovery.started_chat`(游戏内提示,不含码)
- [ ] 用现有 `L10n.lang(key).format(...)` 口径填占位。
- [ ] 验证:`grep -c '"bbswebremote.discovery' en_us.json zh_cn.json` 两边计数相等。

## Step 9 — 全量验证

- [ ] `cd bbs-web-remote && ./gradlew check`,**读真实 exit code + 通读日志**确认 `testRemoteAccessInfo` 与既有 server/wire/collaboration 测试全绿(内存红线:不看管道尾巴)。
- [ ] `./gradlew compileClientJava compileMainJava` 干净。
- [ ] 人工冒烟清单(交用户在游戏内):loopback 显 `127.0.0.1:port` 可复制;LAN 显真实 IPv4(非 `0.0.0.0`);自动启动码现设置栏且客户端启动不卡;倒计时走动、过期清空;`latest.log`/`debug.log` grep 不到明文码;端口占用时有游戏内失败提示。

---

## Review Gates(映射 prd Acceptance Criteria)

| AC(prd) | 由步骤保证 |
|---|---|
| 完整 `http://host:port`;loopback `127.0.0.1`;LAN 真实 IPv4 | Step 1/3/6/7 |
| LAN 无法解析给明确文案、不显 `0.0.0.0` | Step 2/3/6/8 |
| 码独立字段可见可复制、显剩余有效期、过期清空 | Step 4/7/8 |
| 自动启动码游戏内可见(不翻 latest.log) | Step 5/7 |
| 明文码不入 latest.log/debug.log | Step 6/9(grep) |
| 自动启动不阻塞客户端回调线程 | Step 5 |
| 自动启动失败游戏内可见 | Step 5 |
| loopback/LAN 显式/fail-closed/token 不入日志不回归 | design §9 + Step 0 基线对比 + Step 9 |
| `bbs-web-remote/` 编译 + 相关单测通过 | Step 3/9 |

## Rollback Points

R0(基线)→ R7,每步独立可 `git checkout`。新类(`RemoteAccessInfo`/`LanAddressResolver`)可直接删除;两处 UI/client 文件可整文件还原;i18n key 追加式,删除即回滚。服务端与协议层零改动,故回滚无跨层风险。
