# Implement — P1 错误人话化与本地化

> 参见同目录 `design.md`。严守父任务边界：wire `1.1` / API `2.0` 不变；HTTP body 仅追加可选 `reason`；不阻塞 UI/渲染/回调线程；token/配对码明文不入日志。
> 触及文件：
> - 前端：`bbs-web-remote/src/client/resources/assets/bbswebremote/web/app.js`（5277 行）
> - 服务端：`bbs-web-remote/src/client/java/mchorse/bbs_web_remote/client/BBSWebRemoteClient.java`、`server/AuthenticatedHttpHandler.java`、`server/PairingAuthService.java`、`server/ServerAccessConfig.java`
> - 资产：`bbs-web-remote/src/main/resources/assets/bbswebremote/assets/strings/{en_us,zh_cn}.json`
> 行号锚点以当前工作树为准；每步小而可编译。

---

## Phase A — 本地化 key 就位（先补 json，后续步骤引用）

映射验收：AC6。

- [ ] **A1. 补 key**：向 `strings/en_us.json` 与 `strings/zh_cn.json` 追加 design §4.3 全部 key（`runtime.*` / `command.*` / `pairing.*` / `notice.*` / `error.*`）+ 本机细分 `pairing.code_expired`/`code_invalid`/`no_challenge`。两文件 key 集合必须一致，占位符 `%s` / `%1$s` 对齐。仅加 key → 不影响编译。
- [ ] **A2. 校验 JSON**：`node -e "JSON.parse(require('fs').readFileSync(process.argv[1],'utf8'))" <file>` 两文件各跑一次，确认无语法错、无重复 key、en/zh key 对称。

## Phase B — 服务端拒绝原因可区分（reason code）

映射验收：AC3、AC4、AC7。

- [ ] **B1. 拆 `RedeemStatus`**：`PairingAuthService.java:359-363` 把 `INVALID` 拆为 `CODE_INVALID, CODE_EXPIRED, NO_CHALLENGE`（保留 `SUCCESS`/`RATE_LIMITED`）。
- [ ] **B2. `redeem` 分支**：`PairingAuthService.java:85-95` 依 design §2.2 三分支返回细分状态：`current==null` → `NO_CHALLENGE`；`!now.isBefore(current.expiresAt())` → `CODE_EXPIRED`（保留 `newChallenge` 轮换）；`!constantTimeEquals(...)` → `CODE_INVALID`。**`constantTimeEquals` 与限流顺序（80-83 先于判定）不变**。
- [ ] **B3. 更新 redeem 调用点**：`AuthenticatedHttpHandler.java:155` 现 `!= SUCCESS` 判断保持能编译（枚举拆分后仍成立）。
- [ ] **B4. HTTP reason（对外合并）**：`AuthenticatedHttpHandler.java` 各 `writeJson` body 追加可选 `reason`（design §2.1 表）。配对失败 `157` 一律 `{"error":"pairing_failed","reason":"code_invalid_or_expired"}`——**三态对外合并**（防枚举/时序，§2.4）。`origin_rejected`(104)→加 `"reason":"origin_not_allowed"`；`rate_limited`(152)→`"rate_limited"`；`json_required`(113)→`"json_required"`；`request_too_large`(118/126)、`invalid_request`(182) 同名 reason。
- [ ] **B5. Host 拒绝带 body**：`AuthenticatedHttpHandler.java:56` 现 `Response.writeError(...,400)` 无 body 且与 Origin 不可分。改为 `writeJson(response, callback, 400, "{\"error\":\"host_rejected\",\"reason\":\"host_not_allowed\"}")`。确认 `writeJson`(189) 可在该点调用（同类静态方法，参数齐备）。
- [ ] **B6.（可选）本机细分透出**：若 `redeem` 调用侧能拿到 host 回调通路，则在配对失败时把细分 `RedeemStatus` 以 design §2.3 方式记为**本机游戏内可见**事件（日志级别不变，token/码不入串）。**不得**写进浏览器 body。无干净通路则跳过，保留日志现状（不阻塞 AC3 的游戏内诉求由 Phase D 命令反馈兜底）。

## Phase C — 前端 错误码→人话 映射

映射验收：AC1、AC2。

- [ ] **C1. locale 判定**：`app.js` 在 `reportError`(5222) 之前加 `function currentLocale()`：读 `document.documentElement.lang`，前缀 `en` → `"en"`，否则 `"zh"`。
- [ ] **C2. 映射表**：加 `const ERROR_MESSAGES = { … }`，覆盖 design §1.5 用户可见 ProtocolError code（zh/en 双份）+ 服务端 reason（`host_not_allowed`/`origin_not_allowed`/`code_invalid_or_expired`/`rate_limited`/`json_required`/`request_too_large`）。用 `rg "new .*ProtocolError\(\"|fail\(\"" protocol.js app.js` 导出全量 code 核对清单。
- [ ] **C3. `humanizeError(error)`**：按 design §1.2 实现——有 `code` 且命中表 → 本地化文案；有 `code` 未命中 → 「发生未知错误（code）」/`unknown (code)`；无 code → 原样 `error.message`（保护 `app.js:3908/4759` 等既有中文）。返回 `{text, code, raw}`。
- [ ] **C4. 改 `reportError`**：`app.js:5222-5230` 用 `humanizeError` 结果的 `text` 传 `note`，`console.error(error)` 保留（原始串 + code 不丢）。`note(text, true, {level:"error"})`；若 transport-feedback 新 `note` 签名未落地则退化 `note(text, true)`。disconnect 分支逻辑不动。
- [ ] **C5. 配对失败读 reason**：`app.js:3907-3908` 现按 `status===401` 折叠。改为：`!response.ok` 时尽力 `await response.json().catch(()=>null)` 取 `reason`，`new ProtocolError(reason || "pairing_failed", <fallback>)` 交给 `humanizeError`；解析失败退回原「配对失败（HTTP xxx）」。保持 `throw` 流程与 `catch`(3916) 不变。
- [ ] **C6. 暴露测试点**：确认 `humanizeError`/`ERROR_MESSAGES` 可经 `namespace.WebTest`(`app.js:5235`) 访问（加入导出对象），供 Node 回归。

## Phase D — 服务端运行时/命令/配对 文案本地化

映射验收：AC6、AC7。

- [ ] **D1. 运行时状态**：`BBSWebRemoteClient.java:352-365` `settingsStatusText` 用 `L10n.lang("bbswebremote.runtime.*").format(...)` 组装（stopped/running + mode_lan/mode_local）。保持返回 `String`（`IKey.get()`），使 `WebRemoteSettingsClient:33` 的 method-ref→IKey 适配不变。
- [ ] **D2. lastOperationMessage**：`143`(`"Ready"`)、`391`(`"Working..."`)、`212`(`auto disabled`)、`414`(`shutting down`) 及 `startRuntimeServer`/`stop` 内 `ServerOperation.success/failure` 英文串（`533/540/586-589/660/683` 等）改走 `bbswebremote.runtime.*`/`command.*` key 的 `IKey.get()`。逐个替换、每次保持可编译。
- [ ] **D3. 命令输出**：`reportStatus`(307-350)、`pair`(313/339/345-347)、`startFromCommand`(255-258) 的 `Component.literal(<英文>)` 改 `Component.translatable("bbswebremote.command.*"/"pairing.*", args)`。**配对码提示**用 `pairing.code`——确认参数里是 challenge.code()/expiresAt，且该串**仅进游戏内命令反馈，不进 LOGGER**（核对无 `LOGGER.*code`）。
- [ ] **D4. 配对码本地化不泄漏**：grep 确认改动后 `challenge.code()` 不出现在任何 `LOGGER.` 调用参数中（AC7）。

## Phase E — LAN 回退显式化

映射验收：AC5。

- [ ] **E1. 携带降级原因**：`BBSWebRemoteClient.serverAccessConfig`(437-491) 引入 `record ResolvedAccess(ServerAccessConfig config, IKey notice)`（notice 可空），或等价返回结构。空/非法白名单 catch(477) → notice=`bbswebremote.notice.lan_fallback_empty_hosts`；非法 public_origin catch(461) → `notice.public_origin_cleared`。**网络行为不变**（仍 return loopback / 清 origin）；`LOGGER.warn` 保留。
- [ ] **E2. 透出到游戏内**：`startRuntimeServer`(552 调用点) 接收 notice，成功消息（586-589）末尾追加 notice 文案，经 `lastOperationMessage`→设置面板 + 命令反馈可见。仅字符串拼接，**不阻塞线程**。
- [ ] **E3. 静态方法可行性**：`serverAccessConfig` 为 static；`ResolvedAccess` 亦 static record，`IKey` 已 import 到 client 包（`WebRemoteSettingsClient` 已用）。确认 `BBSWebRemoteClient` 能 import `mchorse.bbs_mod.l10n.L10n`/`IKey`。

## 验证（不信任管道退出码）

- [ ] **V1. 前端 Node 回归**：
  ```bash
  cd bbs-web-remote
  node src/webTest/session-lifecycle.test.js; echo "EXIT=$?"
  node src/webTest/browser-protocol.test.js; echo "EXIT=$?"
  ```
  每条**单独看 `EXIT=`**，非 0 即失败。新增一条 `error-i18n` 冒烟（可临时脚本，不入库除非需要）：`require` app.js 后断言 `WebTest.humanizeError({code:"TRANSPORT_EPOCH"}).text` 命中中文、`{code:"NOPE"}` 命中兜底含 `NOPE`、`new Error("配对码无效或已过期")` 原样透传。
- [ ] **V2. 若新增常驻 web 测试**：仿 `build.gradle:295-303` 注册 `testWebErrorI18n`；否则仅临时验证。
- [ ] **V3. Java 编译**（bbs-web-remote）：
  ```bash
  ./gradlew :bbs-web-remote:compileClientJava > /tmp/wr-build.log 2>&1; echo "EXIT=$?"
  tail -40 /tmp/wr-build.log
  ```
  **同时**看 `EXIT=` 与日志尾部 `BUILD SUCCESSFUL/FAILED`——两者一致才算过（管道 tee 会吞码，故分离）。
- [ ] **V4. `new/` 若触及**：本任务预期**不改** `new/`（L10n/IKey 只读引用）。若确需改，另跑 `./gradlew compileJava`（同 V3 双确认）。核对 wire/API 版本常量未变。
- [ ] **V5. 兼容回归**：确认旧前端（不读 reason）+ 新服务端 → 仅 `response.status` 生效仍工作；新前端 + 旧服务端（无 reason 字段）→ `humanizeError` 走 HTTP 兜底文案。

## Review Gates
- [ ] G1（安全红线）：远程 HTTP body 对配对失败**只**含合并 `code_invalid_or_expired`；三分支同 `writeJson(401,...)` 出口、同处于限流之后（§2.4）。评审逐字确认无细分原因外泄。
- [ ] G2：`console.error` 与日志仍保留原始技术串/code；token/配对码明文不入任何 `LOGGER.`（grep 证据）。
- [ ] G3：en/zh key 集合对称，无裸英文残留于本任务触及的用户可见路径（`rg 'Component.literal\("' 触及方法` 归零）。
- [ ] G4：`note()` 两参调用全兼容；`<html lang>` 恒 zh 时行为与改前一致。

## Rollback
- 分阶段提交（A→E 各独立可编译）。任一阶段回归失败：`git checkout -- <该阶段文件>` 回退单阶段，不影响其它。
- 前端与服务端解耦：前端 C 阶段可独立回滚（reason 字段旧前端本就忽略）；服务端 B 阶段回滚后前端 `humanizeError` 自动走兜底，不崩。
