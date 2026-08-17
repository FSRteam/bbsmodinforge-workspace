# 执行计划：webremote 影片无帧根因治理

> 2026-08-13 21:15 更新：阶段 0 取证已由 `research/jstack-hang-2026-08-13.txt` 线程转储完成并确证根因（原生 `addIceCandidate` 持 RTC 锁挂死 → `onClose` 持主锁等 RTC 锁 → 主锁雪崩 → 捕获需求门永闭）。上一会话（18:32–18:38）已完成根因修复的核心两步（R1/R2），未留交接记录，本文件已按实际代码状态对账。

## 阶段 0 · 取证 ✅（已由线程转储取代）

- [x] 根因判决：闩锁点 = `dev.onvoid.webrtc.RTCPeerConnection.addIceCandidate` 原生挂死；放大器 = WebRtcCoordinator 锁内调原生 + BBSWebRemoteClient 主锁内调 RTC。证据 `research/jstack-hang-2026-08-13.txt`（18:28 抓取）。
- [ ] 0.R 复验读数（修复构建部署后，**需真机**）：步骤已固化为 `research/reverification-runbook-2026-08-13.md`（含部署、关 LAN 鉴权、三段探针取数、四场景冒烟、RTC 路径确认、复发取证）。读数写入 `research/frame-stall-measurement-2026-08-13.md`。对应 AC1/AC2/AC4/AC8。
- [ ] 0.R2 探针扩展：`temp/ui-probe.mjs` 的遥测打印段需补 `encodeFailures` / `inFlightAgeMs` / `rtcSignalingStuck`，否则新计数只能从浏览器面板读。

## 阶段 2 · 根因修复（提前，部分已完成）

### 已完成（上一会话 18:32–18:38，已实测存在于代码）

- [x] 2.1 R1 `WebRtcCoordinator`：全部原生 `backend.*` 调用移出 `synchronized (this.lock)`（connectPeer / acceptOffer / addIceCandidate / endIceCandidates / resetSession / disconnect / close），状态变更留在锁内。
- [x] 2.2 R2 `BBSWebRemoteClient.submitRtcWork`：RTC 调用改经单线程 `rtcSignaling` 执行器异步执行；`disconnectRtcPeer` / `resetWebRtcLifecycle` 已走此通道；executor 关停后回退 inline。
- [x] 2.3 R2 `closeWebRtcWithinTimeout`：coordinator close 放到一次性 daemon 线程 + 限时 join，挂死只泄漏该线程不再卡关停。

### 待完成

- [ ] 2.4 审计 `BBSWebRemoteClient` 中残余的 inline `this.webRtc.*` 调用点（如 `RTC_ICE_END` → `endIceCandidates`、`acceptRtcOffer` → `acceptOffer`、`acceptRtcCandidate` → `addIceCandidate`）：确认全部只在 rtcSignaling 线程上执行且不持本类监视器；有例外则改走 `submitRtcWork`。
- [ ] 2.5 R3 信令线程看护：单线程 rtcSignaling 被一次原生挂死永久吞掉后，后续任务silent堆积。加在飞记录（操作名 + startedNanos）＋看护（调度器周期检查，超阈值节流 WARN 并标记 `rtcSignalingStuck`），挂死状态入遥测可读出。不强杀线程，但必须留痕并可经诊断面板判别。
- [ ] 2.6 R4 降级自愈：rtcSignalingStuck 或 RTC 连接失败时 UI 帧管线不受影响（JPEG over WS 继续）；浏览器侧 RTC 失败回退 WS 路径的行为复核（已有 transport_mode 逻辑，只验证不重写）。

## 阶段 1 · 消灭静默（未动工）

### 1A FSR 错误纪律（`new/src/client/java/mchorse/bbs_mod/client/ui/mirror/`）✅ 2026-08-13 21:47

- [x] 1A.1 `BBSUiMirrorRegistry.deliver()`：`catch (Throwable)` + `finally` 复位 `callbackStartedNanos` / `callbackPhase`；OOM/SOE 记账复位后重抛；notifyAll 在 finally 中必然发生。
- [x] 1A.2 `drain()` 拆为外壳 + `drainLoop()` + `recoverAfterWorkerFailure()`：异常逃逸时 `workerRestarts`++、锁内复位 `drainScheduled`、`pending` 非空则重新调度（worker 复活）。
- [x] 1A.3 计数 + 只读快照：`BBSUiMirrorRegistry.diagnostics()` → `Diagnostics(framesSkippedNoDemand, framesSkippedQuarantined, callbackErrors, workerRestarts)`，静态 AtomicLong，热路径无锁无分配。
- [x] 1A.4 `publishStandaloneWorldReplayFrame()`：包私有重载注入 `frameBody`，`beginFrame` 成功分支 `try/finally` + `abortFrame()` 兜底。
- [x] 1A.5 `beginFrame` false 分支分类计数（noDemand / frameAlreadyActive / sessionMissing）；`frameAlreadyActive` CAS 节流 WARN（5s，logger `bbs-client-ui-mirror`）。
- [x] 1A.6 `BBSUiFrameRecorder.diagnostics()` → `Diagnostics(noDemand, frameAlreadyActive, sessionMissing, activeFrameLeaked)`。

> webremote 透传对接：`captureSkippedNoDemand` ← `BBSUiFrameRecorder.diagnostics().noDemand()`；`captureFrameAlreadyActive` ← `frameAlreadyActive()`。两方法无锁只读，遥测线程任意频率可调。

### 1B webremote 看门狗与记账

- [ ] 1B.1 `LatestUiFrameDispatcher`：记录 `dispatchStartedNanos`，`Statistics` 追加 `inFlightAgeNanos`（现仅有 `inFlight` 布尔）。
- [ ] 1B.2 在飞看门狗：超阈值（常量 5 s，可注入）判定 Completion 丢失 → `fail()` 关闭 socket，替代无声悬挂。
- [ ] 1B.3 `ClientState.dispatchFrame`（约 2357–2401 行）：编码失败现被当 `completion.succeeded()`＋WARN，改为累加 `encodeFailures` 计数并入遥测（行为仍是丢帧续传，但可观测）。
- [ ] 1B.4 `LinkTelemetryCoordinator.UiDelivery` 追加（只增不删）：`encodeFailures`、`inFlightAgeMs`、`captureSkippedNoDemand`、`captureFrameAlreadyActive`、`rtcSignalingStuck`。

### 1C 浏览器侧记账

- [ ] 1C.1 `protocol.js` `pruneFor`：抛 `CHUNK_CACHE_FULL` 前先按 `lastUsed` 淘汰非当前帧候选并记账；错误文案带 `chunkCount` / `chunkBytes` / `activeCount`。
- [ ] 1C.2 `assembler.stats()` 追加 `evictedChunks`、`cacheFullEvents`。
- [ ] 1C.3 `app.js` `diagDeliveryUi` / `diagChunks` 展示新字段；`index.html` 如需同步新增 `<dd>`。
- [ ] 1C.4 新增 JS 模块需三处同步（`index.html`、`build.gradle` `webRuntimeScripts`、webTest require）。预期不新增模块。

## 阶段 3 · 回归测试

- [x] 3.1 `BBSUiMirrorFailureRecoveryTest.bareErrorDoesNotLatchCaptureGate` + `fatalErrorRethrowsAndWorkerRevives`：红（EXIT=1）→绿（EXIT=0），断言门先关后复、callbackErrors/workerRestarts≥1、worker 线程名变化、后续帧照常投递。
- [x] 3.2 `standaloneReplayFailureDoesNotLeakActiveFrame`：红→绿，断言异常传播、`activeFrameLeaked=false`、后续 standalone 帧真实发布。
- [ ] 3.3 `bbs-web-remote` JavaExec：Completion 永不回调用例——断言看门狗超阈值后关 socket。修复前必须失败。
- [ ] 3.4 webTest：`pruneFor` 缓存占满先淘汰再抛错且计数正确。
- [ ] 3.5 webrtcTest：验证 R1 锁纪律（可用假 backend 阻塞注入断言：backend 调用期间协调器锁可被其他线程获取）。

## 验证命令（PowerShell，真实 exit code 双确认）

> 2026-08-13 23:45 实测：`check` 目前**被网络阻断**，非代码问题。它依赖
> `neoFormJoined1.21.1-...DownloadAssets`，在线拉不到 `piston-meta.mojang.com`，
> 离线又无缓存。绕开办法是直接跑测试任务集（下方第二条），已实测 EXIT=0。
> 另注意：从 `bbs-web-remote/` 目录内运行时它就是**根项目**，任务名是 `check`，
> 写 `:bbs-web-remote:check` 会报 project not found。

```powershell
# webremote 全量（网络恢复后再用）
cd D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\bbs-web-remote
& .\gradlew.bat check --console=plain 2>&1 | Select-Object -Last 50; Write-Host "EXIT=$LASTEXITCODE"

# webremote 免网络测试集（当前可用，等价覆盖除 MC 资源下载外的全部验证）
& .\gradlew.bat verifyWeb testWireProtocol testCoControl testWebRtcCore `
    testRemoteAccessInfo testWebRtcConfiguration testServerSmoke `
    testCollaborationProtocol testCollaborationClient testCollaborationPresence `
    testCollaborationSnapshot --offline --console=plain 2>&1 | Select-Object -Last 50
Write-Host "EXIT=$LASTEXITCODE"

# FSR 镜像需求测试（注意：任务名必须是 testUiMirrorDemand！
# 写 uiMirrorDemandTest 会被 Gradle 缩写匹配到 uiMirrorDemandTestClasses，只编译不执行）
cd D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new
& .\gradlew.bat testUiMirrorDemand --console=plain 2>&1 | Select-Object -Last 40; Write-Host "EXIT=$LASTEXITCODE"
```

> cmd 的 `& echo EXIT=%ERRORLEVEL%` 在解析期展开恒为 0，禁用；用 PowerShell `$LASTEXITCODE`。

裸 JVM 测试禁止触发 `BBSMod` 的 `<clinit>`。

## 评审门

- G1 ✅ 判决已入 research/，与用户症状一致。
- G2 ✅ 2026-08-13 23:45 实测：FSR `testUiMirrorDemand` EXIT=0；webremote 免网络测试集
  EXIT=0（56 tasks，26 executed，含 11 个 webTest 与全部 JavaExec）。唯一未跑的是
  `check` 里需要 Mojang 资源下载的 `DownloadAssets`，属环境网络阻断，非代码问题。
- G3（阶段 3 后）：人工冒烟四场景（打开影片 / 播放拖时间轴 / 退出影片 / 浏览器重连）全部有画面。需真实客户端，留给用户执行。

## 回滚点

- R1/R2 已在工作区（bbs-web-remote 不受 git 管理，无提交历史）——继续动工前先把当前半成品状态跑绿存档。
- 1A / 1B / 1C 三组独立，可单独 revert。
- 看门狗（1B.2 / 2.5）若误报，先调大阈值常量，不回滚其余修复。
