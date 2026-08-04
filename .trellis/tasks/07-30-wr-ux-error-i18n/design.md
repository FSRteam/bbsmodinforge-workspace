# Design — P1 错误人话化与本地化

> 归属父任务 `07-30-web-remote-ux-humanization`。严格遵守父任务边界：wire `1.1` / Addon·API `2.0` / `/ws/ui` / `/ws/media` / 参与者身份 / held 输入语义均不变；所有新增字段**增量、可选、向后兼容**；不阻塞 UI/渲染/客户端回调线程；token/配对码明文不入普通日志。
>
> 行号锚点以当前工作树为准：`app.js`(5277 行)、`protocol.js`(1012 行)、`BBSWebRemoteClient.java`(2173 行)、`AuthenticatedHttpHandler.java`(199 行)、`PairingAuthService.java`(421 行)、`ServerAccessConfig.java`(218 行)。

---

## 现状（审计确认）

**前端**
- `protocol.js:148-158` `class ProtocolError extends Error { code; }` + `fail(code, message)`；所有解码错误带**稳定英文 code**（如 `ERROR_LENGTH`/`RESOURCE_BODY_LENGTH`/`SURFACE_FRAME_*`/`TRANSPORT_EPOCH`）。
- `protocol.js:643-651` `decodeError(payload)`：把服务端 ERROR 帧解成 UTF-8 **原样字符串**返回，无 code。
- `app.js:5222-5230` `reportError(error, disconnect)`：直接 `this.note(error.message, true)`——英文技术串进 toast；`console.error(error)` 已在（调试信息不丢）。
- `app.js:3907-3908` 配对失败：`throw new Error(response.status === 401 ? "配对码无效或已过期" : "配对失败（HTTP " + status + "）")`——已中文但把所有 4xx 折叠，不区分原因。
- `app.js` 前端目前**硬编码 zh-CN**（`index.html:2` `<html lang="zh-CN">`），JS 无任何 i18n/locale 机制（grep `lang|locale|i18n|navigator.language` 无命中）。
- web `Constants`(`protocol.js:18-83`) 已 `deepFreeze`，含 `ENDPOINTS`；是放置 code→消息表的自然位置之一，但表更适合放 `app.js`（消息属 UI 层，`protocol.js` 保持纯协议）。

**服���端**
- `AuthenticatedHttpHandler.java` 响应体为机器码字符串：`origin_rejected`(104)、`json_required`(113)、`request_too_large`(118/126)、`rate_limited`(152)、`pairing_failed`(157)、`invalid_request`(182)；Host 拒绝 `56` 直接 `writeError(...,400)`（**无 body、与 Origin 拒绝无法区分**）。
- `PairingAuthService.redeem`(`74-117`) 三态 `RedeemStatus{SUCCESS,INVALID,RATE_LIMITED}`（`359-363`）；**码错误 / 已过期 / 无挑战**全部折叠成 `INVALID`(87-94)。
- `ServerAccessConfig.serverAccessConfig`(`437-491`)：LAN 开启但白名单非法/为空 → `catch` 里 `LOGGER.warn` + `resetNetworkExposureToLoopbackDefaults()` + `return loopback(port)`（`477-490`），**游戏内无提示**；非法 public_origin 同样只 `LOGGER.warn`(`461-475`)。空白名单其实在 `parseAllowedHosts`(`493-515`) 抛 `IllegalArgumentException` 被同一 catch 吞掉。
- 运行时/命令文本硬编码英文：`settingsStatusText`(`352-365`) `"STOPPED - ..."` / `"RUNNING - ..."`；`lastOperationMessage`(`143` 初值 `"Ready"`、`391` `"Working..."`、`212`/`414` 等)；命令 `reportStatus`(`307-350`)、`pair`(`313/339/345`)、`startFromCommand`(`255-258`) 均 `Component.literal` 英文。
- L10n 资产 `strings/{en_us,zh_cn}.json` 仅 `bbswebremote.config.*`(2-33)。

**基础设施可复用**
- L10n：`mchorse.bbs_mod.l10n.L10n.lang(String key)` → `IKey`；`IKey.get()` 取当前 locale 文案，`IKey.format(args)` 走 `FormatKey`(`String.format`)。`WebRemoteSettingsClient.java:36-44` 已用 `L10n.lang("bbswebremote.config.actions.*")`——**服务端 UI 已具备 L10n 通路**，命令/状态改造照抄即可。
- Node 回归：`src/webTest/*.test.js` 直接 `require(".../protocol.js")` 与 `namespace.WebTest`(`app.js:5235`)，`build.gradle:295-303` 注册 `testWeb*` 任务。新映射表须经 `namespace.WebTest` 暴露以可测。

---

## 1. 前端 错误码 → 本地化消息 映射

### 1.1 位置与形状
- 映射表放 **`app.js`**（UI 层），命名 `ERROR_MESSAGES`，紧邻 `reportError`（`app.js:5222` 之前）声明；`protocol.js` 保持纯协议、不引入文案。
- 结构：`code -> { zh, en }`。运行时按当前 locale 选择（见 1.4）。
  ```js
  const ERROR_MESSAGES = {
      SHA256_UNAVAILABLE: { zh: "当前浏览器无法校验资源完整性，请换用现代浏览器", en: "This browser cannot verify asset integrity" },
      RESOURCE_BODY_LENGTH: { zh: "资源数据长度异常，连接已中断", en: "Resource body length mismatch" },
      TRANSPORT_EPOCH: { zh: "会话已失效，请重新连接", en: "Session epoch invalid; reconnect" },
      ERROR_LENGTH: { zh: "服务端错误信息过长", en: "Server error payload too large" },
      // …每个 ProtocolError code 一条；清单见 1.5
  };
  ```
- 表**只覆盖会冒泡到 `reportError` 的用户可见 code**；纯内部断言 code（编解码不变量）给统一兜底即可，不必逐条翻译。

### 1.2 映射函数
新增纯函数 `humanizeError(error)`（放 `ERROR_MESSAGES` 后），供 `reportError` 与 `namespace.WebTest`：
```js
function humanizeError(error) {
    const locale = currentLocale();               // 见 1.4
    const code = error && error.code ? String(error.code) : null;
    const raw = error && error.message ? error.message : String(error);
    if (code && ERROR_MESSAGES[code]) {
        return { text: ERROR_MESSAGES[code][locale] || ERROR_MESSAGES[code].zh, code, raw };
    }
    if (code) {                                    // 已知有 code 但无文案 → 兜底带 code
        return { text: (locale === "en" ? "An unknown error occurred (" : "发生未知错误（") + code + (locale === "en" ? ")" : "）"), code, raw };
    }
    return { text: raw, code: null, raw };          // 无 code（原生 Error）：沿用其 message（多为已中文文案）
}
```
- **关键决策**：仅当 error 带 `code` 且无映射时才套“未知错误（code）”兜底；无 code 的原生 `Error`（前端自造的、已经是中文人话，如 `app.js:3908`「配对码无效或已过期」、`4759`「发送缓冲超过 4 MiB…」）**原样透传**，避免把已有中文文案反被英文兜底覆盖。

### 1.3 `reportError` 改造
`app.js:5222-5230` 改为：
```js
reportError(error, disconnect) {
    const { text, raw } = humanizeError(error);
    console.error(error);                 // 原始技术串 + code 全保留给开发者
    this.note(text, true, { level: "error" });   // options 兼容 transport-feedback 的 note() 新签名
    if (disconnect) { /* 原逻辑不动 */ }
}
```
- `note` 第三参 `options` 与 `wr-ux-transport-feedback` A3 的新签名共用；若该任务尚未落地，`note(text, true)` 两参调用仍兼容（transport 任务保证向后兼容）。这里**不**依赖其先落地——退化为两参即可。

### 1.4 前端 locale 判定
- 新增 `currentLocale()`：读 `document.documentElement.lang`（`index.html:2` 当前 `zh-CN`）→ 前缀 `en` 返回 `"en"`，否则 `"zh"`。
- **兼容**：现状恒为 zh，行为不变；未来若 Java 侧按 MC 语言注入 `<html lang>`（超出本任务范围，Out of Scope 标注）即自动生效。本任务只保证映射表 en/zh 双份就绪 + 判定函数存在。

### 1.5 需覆盖的 ProtocolError code 清单（来源：`app.js`/`protocol.js` 实际 throw）
会冒泡到 `reportError` 的：`SHA256_UNAVAILABLE`(app 1389)、`RESOURCE_BODY_LENGTH`(1401/1416/1425)、`RESOURCE_REDEFINE`(1467)、`RESOURCE_CACHE_COUNT`(1472)、`RESOURCE_HASH_METADATA`(1486)、`TRANSPORT_EPOCH`(4575)、以及 `protocol.js` 解码族里可能经 socket ERROR/解码路径上抛的 `ERROR_LENGTH`/`ERROR_UTF8`/`CHUNK_*`/`SURFACE_FRAME_*`/`GLYPH_*`/`INPUT_*`。实现时以 `rg "new .*ProtocolError\(\"|fail\(\"" protocol.js app.js` ��量导出 code 列表，对**用户可见路径**逐条给 zh/en；其余归兜底。

---

## 2. 服务端 可区分拒绝原因（增量可选 reason code）

### 2.1 协议增量约定
- HTTP 错误响应体从 `{"error":"<machine>"}` 扩展为 `{"error":"<machine>","reason":"<stable_reason_code>"}`。`error` 字段**保持原值不变**（旧前端只读 `response.status`，两者都忽略 body，故完全兼容）；`reason` 为**新增可选**字段。
- 稳定 reason code（供前端映射到 `bbswebremote.error.*` key）：
  | 场景 | HTTP | error（不变） | 新增 reason |
  |---|---|---|---|
  | Host 不在白名单 | 400 | *(现无 body)* → 补 `{"error":"host_rejected","reason":"host_not_allowed"}` | `host_not_allowed` |
  | Origin 拒绝 | 403 | `origin_rejected` | `origin_not_allowed` |
  | 配对码错误 | 401 | `pairing_failed` | `code_invalid` |
  | 配对码已过期 | 401 | `pairing_failed` | `code_expired` |
  | 无挑战/未生成 | 401 | `pairing_failed` | `no_challenge` |
  | 限流 | 429 | `rate_limited` | `rate_limited` |
  | 需 JSON | 415 | `json_required` | `json_required` |
  | 体过大 | 413 | `request_too_large` | `request_too_large` |
  | 非法请求 | 400 | `invalid_request` | `invalid_request` |

### 2.2 `PairingAuthService.redeem` 细化状态
- `RedeemStatus`(`359-363`) 增补：`SUCCESS, CODE_INVALID, CODE_EXPIRED, NO_CHALLENGE, RATE_LIMITED`（保留 `SUCCESS`/`RATE_LIMITED` 名不变；`INVALID` 拆三态）。**这是内部枚举**，不直接上线。
- `redeem`(`85-95`) 判定拆开：`current == null` → `NO_CHALLENGE`；`!now.isBefore(expiresAt)` → `CODE_EXPIRED`（保留原 `newChallenge` 轮换）；`!constantTimeEquals(...)` → `CODE_INVALID`。**`constantTimeEquals` 常量时间比较保持不变**，不新增基于存在性的提前返回（防时序侧信道，见 2.4）。

### 2.3 双层暴露（安全 vs 可用性权衡）
**核心决��**：细分原因对**本机游戏内 host**完全可见，对**远程浏览器**收敛。
- **对远程浏览器（HTTP 响应 reason）**：配对失败统一回 `reason:"code_invalid_or_expired"`（把 `CODE_INVALID`/`CODE_EXPIRED`/`NO_CHALLENGE` 合并成一个对外码），前端文案「配对码无效或已过期，请在游戏内重新生成」。**不向未认证远程方泄露“该码是否曾存在/是否刚过期”**——否则构成配对码枚举 + 存活性侧信道。`rate_limited`/`json_required`/`host_not_allowed`/`origin_not_allowed` 可安全细分（不涉及秘密存在性）。
- **对本机游戏内 host（日志 + 命令/设置反馈）**：`BBSWebRemoteServer`/回调把**细分 `RedeemStatus`** 透给客户端侧，`BBSWebRemoteClient` 以本地化文案在游戏内显示真实原因（「配对码已过期」「尚未生成配对码」）。这条路径只对已在主机前的人可见，无侧信道风险。
- 实现层面：`AuthenticatedHttpHandler.redeem` 分支（`149-159`）读取细分状态 → 选 HTTP reason（对外合并）；同时（可选、非阻塞）经现有 `this` 通路记一条本机可见事件。**不得**把细分原因写进给浏览器的 body。

### 2.4 侧信道注意
- 保持 `constantTimeEquals`（`PairingAuthService`）用于码比较。
- 过期/无挑战/码错三分支的**响应时间不得有可测差异**：三者都走同一 `writeJson(401, code_invalid_or_expired)` 出口，且都在 `allowPairAttempt` 限流计数之后（`80-83`），保证失败路径耗时同构。
- reason 合并对外后，远程侧无法区分“错码”与“过期”，消除枚举。

---

## 3. LAN 空白名单回退显式化

- 现 `serverAccessConfig`(`437-491`) 回退 loopback 只 `LOGGER.warn`。改为：回退发生时，把一条**结构化提示**写入 `lastOperationMessage`（游戏内设置面板 `settingsMessageText` `367-370` 显示）+ 命令 `sendFailure/sendSuccess` 反馈。
- 因 `serverAccessConfig` 是 `static` 且在 `startRuntimeServer`(`552`) 内被调用，最干净的做法：`serverAccessConfig` 返回值携带“是否发生降级 + 原因”，或用一个 `record ResolvedAccess(ServerAccessConfig config, IKey notice)`。给 `startRuntimeServer` 在成功消息里追加 notice。**保持不阻塞**：仅拼字符串，无网络/IO。
- 三种降级各一条本地化提示：
  - 空/非法白名单 → `bbswebremote.notice.lan_fallback_empty_hosts`「已开启局域网但 Host 白名单为空或非法，已保持仅本机访问」。
  - 非法 public_origin 清除 → `bbswebremote.notice.public_origin_cleared`「公开访问地址非法，已清除并保持直连」。
- **兼容**：不改变实际网络行为（仍 fail-closed 回 loopback），只是把已发生的降级**说出来**；`LOGGER.warn` 保留。

---

## 4. 本地化 key 方案（其它子任务共用的约定）

### 4.1 命名约定
`bbswebremote.<domain>.<name>`，与现有 `bbswebremote.config.*` 同前缀、同风格（点分、小写下划线名）。domain：
- `runtime.*` — 运行时状态（STOPPED/RUNNING/…）
- `command.*` — 斜杠命令输出
- `pairing.*` — 配对码提示
- `notice.*` — 降级/清洗等主动提示
- `error.*` — 面向用户的错误原因（含服务端 reason code 映射 + 前端 code 映射）
- （`config.*` 已存在，保留）

占位符统一用 `%s`（走 `IKey.format` → `String.format`，见 `FormatKey.java:21`），顺序占位；含多参用 `%1$s` 显式序号。

### 4.2 前端映射键
前端 `ERROR_MESSAGES` 的兜底/远程 reason 也复用 `error.*` 语义命名（前端表 key 用 ProtocolError code 原文作 JS 对象键；对外 reason → 文案的对应关系在 2.3）。前端与 Java 各持一份文案（前端 zh/en 内联，Java 走 strings json）——因为 web UI 无 L10n 运行时；**约定统一的是命名语义与 en/zh 双份要求**，不是共享同一存储。

### 4.3 需新增到 `en_us.json` / `zh_cn.json` 的具体 key
```
# runtime
bbswebremote.runtime.stopped                 en: "Stopped — configured port %s"          zh: "已停止 — 配置端口 %s"
bbswebremote.runtime.running                 en: "Running — %1$s — %2$s:%3$s — %4$s browser(s)"  zh: "运行中 — %1$s — %2$s:%3$s — %4$s 个浏览器"
bbswebremote.runtime.mode_lan                en: "LAN"                                    zh: "局域网"
bbswebremote.runtime.mode_local              en: "Local only"                             zh: "仅本机"
bbswebremote.runtime.ready                   en: "Ready"                                  zh: "就绪"
bbswebremote.runtime.working                 en: "Working…"                               zh: "处理中…"
bbswebremote.runtime.auto_start_disabled     en: "Automatic start is disabled"            zh: "自动启动已关闭"
bbswebremote.runtime.shutting_down           en: "BBS Web Remote is shutting down"        zh: "网页远程控制正在关闭"
# command
bbswebremote.command.stopped_next_port       en: "Stopped (next port: %s)"                zh: "已停止（下次端口：%s）"
bbswebremote.command.port_range              en: "Ports must be between %1$s and %2$s; specify an explicit port"  zh: "端口须在 %1$s 到 %2$s 之间；请指定明确端口"
bbswebremote.command.not_running             en: "BBS Web Remote is not running"          zh: "网页远程控制未运行"
bbswebremote.command.started                 en: "Running on port %1$s%2$s"               zh: "已在端口 %1$s 运行%2$s"
bbswebremote.command.suffix_lan              en: " (LAN mode; run /bbswebremote pair per person)"  zh: "（局域网模式；每人执行 /bbswebremote pair）"
bbswebremote.command.suffix_loopback         en: " (loopback only)"                       zh: "（仅本机）"
bbswebremote.command.already_running         en: "Already running on port %1$s; use /bbswebremote restart <port>"  zh: "已在端口 %1$s 运行；请用 /bbswebremote restart <port> 更改"
# pairing
bbswebremote.pairing.lan_only                en: "Pairing codes are only used in LAN mode"  zh: "配对码仅在局域网模式下使用"
bbswebremote.pairing.start_first             en: "Start Web Remote before creating a pairing code"  zh: "生成配对码前请先启动网页远程控制"
bbswebremote.pairing.code                    en: "One-time pairing code: %1$s (expires %2$s)"  zh: "一次性配对码：%1$s（%2$s 过期）"
# notice
bbswebremote.notice.lan_fallback_empty_hosts en: "LAN enabled but the allowed-host list is empty or invalid; kept loopback-only access"  zh: "已开启局域网，但 Host 白名单为空或非法，已保持仅本机访问"
bbswebremote.notice.public_origin_cleared    en: "Public origin was invalid and has been cleared; keeping direct access"  zh: "公开访问地址非法，已清除并保持直连"
# error (面向用户的服务端 reason / 前端兜底语义)
bbswebremote.error.host_not_allowed          en: "This address is not in the LAN allow-list"  zh: "该地址不在局域网白名单内"
bbswebremote.error.origin_not_allowed        en: "Request origin is not permitted"        zh: "请求来源不被允许"
bbswebremote.error.code_invalid_or_expired   en: "Pairing code is invalid or expired; generate a new one in-game"  zh: "配对码无效或已过期，请在游戏内重新生成"
bbswebremote.error.rate_limited              en: "Too many attempts; wait a moment and retry"  zh: "尝试过于频繁，请稍候重试"
bbswebremote.error.json_required             en: "Request must be JSON"                   zh: "请求必须为 JSON 格式"
bbswebremote.error.request_too_large         en: "Request too large"                      zh: "请求体过大"
bbswebremote.error.unknown                   en: "An unknown error occurred (%s)"          zh: "发生未知错误（%s）"
```
（本机游戏内可见的细分配对原因，另加 `bbswebremote.pairing.code_expired`/`code_invalid`/`no_challenge`，仅用于日志/游戏内反馈，不入 HTTP body。）

---

## 兼容与风险

**兼容**
- wire/协议/API 版本零改动；HTTP body 仅**追加**可选 `reason` 字段，旧前端忽略；`error` 原值保留。
- 前端 `note()` 退化为两参兼容；`humanizeError` 对无 code 的原生 Error 原样透传，不回归已有中文文案。
- Java 侧文案改 `Component.translatable`/`L10n.lang`，key 缺失时 BBS L10n 回落为 key 字符串（不崩溃）——但验收要求补齐 en/zh 双份。
- 新增 lang key 为纯增量，旧 config key 不动。

**风险**
- R1 前端无 L10n 运行时：en/zh 双份内联进 JS，locale 判定靠 `<html lang>`（现恒 zh）。真正的英文切换依赖上游注入 lang（Out of Scope）——本任务只保证“不硬塞英文串、就绪双份”。
- R2 `RedeemStatus` 拆分触及 `PairingAuthService` 核心与其单测：须同步更新引用 `INVALID` 的测试/分支（`AuthenticatedHttpHandler:155`、任何 Java 测试）。
- R3 侧信道：对外合并三态是**安全红线**，实现评审必须确认远程 body 不含细分原因、三分支同出口同耗时。
- R4 `serverAccessConfig` 是 static 且被 `startRuntimeServer` 调用；引入 `ResolvedAccess` 需小心不改变 fail-closed 网络语义，仅附带 notice。
- R5 `settingsStatusText/MessageText` 返回 `String`（被 `UILabel`(IKey) 适配，见 `WebRemoteSettingsClient:33-34` 用 method-ref 转 IKey）——改本地化时保持返回已解析 `String`（`IKey.get()`）或改签名为 IKey，二选一并保证 label 适配不变。

## 验收映射
- AC1/AC2 → §1（映射表 + `humanizeError` + `reportError`，console 保留原串）
- AC3 → §2.2 + §2.3（游戏内细分；对外合并）
- AC4 → §2.1（`host_not_allowed` vs `origin_not_allowed` vs 服务故障不同 reason）
- AC5 → §3
- AC6 → §4.3（runtime/command/pairing 全本地化）
- AC7 → §2.4 + §3（token/码不入日志，降级提示不含秘密）
- AC8 → 见 implement.md 验证（`new/` + `bbs-web-remote/` 编译，版本不变）
