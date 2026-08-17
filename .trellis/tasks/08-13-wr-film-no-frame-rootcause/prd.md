# webremote 打开影片后无 UI 帧的根因治理与静默失败消除

## Goal

彻底修复「webremote 打开影片后不再返回 UI 帧，且切换到其他界面也不恢复」。此前多轮修复（surface 容错、重连宽限、帧回放、canvas 逐命令隔离）均未命中，根本障碍是**帧管线每一层的失败都不留痕**，无法定位真实闩锁点。因此本任务的一等交付物是端到端可观测性，其次是基于证据的根因修复。

## 现象（用户报告）

- 打开影片前：远程画面正常。
- 打开影片后：不再有 UI 帧返回。
- 进入过影片再切回其他界面：**仍然无帧**（不可自愈）。
- 已复现多次，跨多轮修复依然存在。

## 已验证事实（2026-08-13 实测）

- 运行实例为 PCL 启动的正式客户端（非 `runs/client`），gameDir `D:\1.21\versions\1.21.1-NeoForge_21.1.223`，加载 `bbs-web-remote-0.1.0-all.jar`，14:52 启动。
- 服务端处于 **LAN 模式**（`ServerAccessConfig.requiresAuthentication()` 仅 `lanEnabled` 为真时成立），故外部无凭据探针被 `401` 拒绝；浏览器凭已有 `bbs_remote_session` cookie 免码进入。
- 活体日志 `logs/latest.log`（4.5 MB）中：
  - `bbs-client-ui-mirror` / `bbs-client-ui-lifecycle` logger **零输出**；
  - `Dropping an invalid …` 丢帧告警 **0 条**；
  - `at mchorse` 堆栈 **0 条**；
  - 故障时段仅有协作层 `film_mutation … reason=tick_budget` 拒绝与 GLFW 剪贴板 GL ERROR 噪声，均与镜像无关。
- 由上可排除：镜像回调异常熔断、慢回调熔断、回调挂死（三者都会打 `[bbs-client-ui-mirror] quarantined` WARN）、以及服务端编码抛错丢帧。
- 结论：闩锁位于某条**完全无日志**的路径上。

## 根因（2026-08-13 由线程转储确证，证据见 `research/jstack-hang-2026-08-13.txt`）

**WebRTC 原生调用在持锁状态下挂死，经由锁纪律缺陷放大为主锁雪崩，导致捕获需求门永远开不了。**

锁链终点：

```
bbs-web-remote-rtc-signaling #129  RUNNABLE  elapsed=12819s
  dev.onvoid.webrtc.RTCPeerConnection.addIceCandidate  (Native Method)  ← 永不返回
  - locked <0x000000071b53fcd8>   ← 死抱 WebRtcCoordinator.lock
```

放大路径：

1. `WebRtcCoordinator.addIceCandidate`（约 158–170 行）在 `synchronized (this.lock)` 内直接调用原生 `backend.addIceCandidate(...)`。原生挂死 → 该锁永不释放。同类问题存在于 `endIceCandidates` / `resetSession` / `disconnect` / `close`，均在锁内调 backend。
2. `BBSWebRemoteClient.onClose`（2185 行）在**持有主锁** `<0x71b53fb58>` 的情况下调 `disconnectRtcPeer` → `WebRtcCoordinator.disconnect` → 等待已死的 RTC 锁 → **主锁被永久占用**。

主锁失守后的连锁（同一转储中实测到的阻塞线程）：

| 阻塞线程 | 阻塞点 | 外部症状 |
|---|---|---|
| `bbs-web-remote-121` / `-133` | `onOpen:1303` 等主锁 | `setUiMirrorDemand(true)` 永不执行 → `hasActiveDemand()` 恒假 → `beginFrame` 恒返回 false → **无 UI 帧、换界面不恢复、刷新无效** |
| `bbs-web-remote-lifecycle` | `startRuntimeServer:685` 等主锁 | **设置面板「应用」永久卡在处理中** |
| `Scheduler-*` | `onClose:2177` 等主锁 | socket 清理堆积 |
| `bbs-surface-jpeg-encoder` | `publishSurface:187` 等 RTC 锁 | 媒体帧同时停摆 |
| `Thread-23` | `isCurrent:252` 等 RTC 锁 | RTC 协商全停 |

**为何影片是触发器**：打开影片启用世界回放 → 产生媒体面 → 推动 RTC 协商与 ICE 候选交换 → 撞上原生 `addIceCandidate` 挂死。

**为何历次修复未命中**：病灶在 WebRTC 锁纪律，而此前修复集中在 canvas 渲染隔离、重连宽限、帧回放——全部位于故障下游。

**临时规避**：`transport_mode = WS_ONLY` 或关闭 `webrtc_enabled`，走 JPEG over WebSocket，完全绕开该路径。

## 前期候选假设（已被上述证据取代，保留备查）

1. **同一 `UIScreen` 内会话不重置**：仪表盘与影片编辑器共用一个 `UIScreen`，镜像会话与其 `UiFrameSession` / `ChunkStore` 在切换面板时不重建；任何被污染的分块/确认状态会跨界面持续，契合「切走也没用」。
2. **浏览器分块缓存硬失败**：`pruneFor` 在缓存被活跃帧占满时抛 `CHUNK_CACHE_FULL`；单帧上限 4096 分块 × 16 KiB 恰为缓存上限 64 MiB，影片巨帧足以令 `currentFrame` + `pendingFrame` 吃满缓存，此后每条 CHUNK_DEF 均抛错。该异常经 `reportError(error, true)` **断连**，故可由前端显示状态判别。
3. **CHUNK_MISS 反馈被限流**：MISS 令牌桶为 burst 64 / 8 per second；若每帧都触发 miss，桶被抽干后服务端不再补发定义，帧永不组齐。
4. **帧分发器闩锁**：`LatestUiFrameDispatcher` 的 `Completion` 若一次不回调，`active` 恒真，此后 `offerLatest` 只替换 `pending`，不报错、不断连、永不发帧。
5. **捕获需求门关闭**：`BBSUiFrameRecorder.beginFrame` 依赖全局 `hasActiveDemand()` 与 `ACTIVE_FRAME` ThreadLocal；两者皆为静态状态，任一被卡住即全局静默停帧且不随换界面恢复。

## 已定位的独立缺陷（无论主根因为何都应修）

- `BBSUiMirrorRegistry.deliver()` 缺 `finally`：若 `delivery.invoke()` 抛出非 `LinkageError` 的 `Error`，`callbackStartedNanos` 永不归零 → `captureEnabled()` 永假 → 全局停帧；worker 线程同时死亡且 `drainScheduled` 悬挂为真。
- `BBSUiFrameRecorder.publishStandaloneWorldReplayFrame()` 的 `beginFrame` / `endFrame` 之间无 `try/finally`：中途抛错即泄漏 `ACTIVE_FRAME`，此后渲染线程上 `beginFrame` 永远返回 false。
- `ClientState.dispatchFrame()` 把编码失败当作 `completion.succeeded()`：分块超限等错误被降级为静默丢帧，仅留一条 WARN。

## Requirements

### 根因修复（首要）

- R1 **锁外调用原生**：`WebRtcCoordinator` 不得在持 `this.lock` 时调用任何 `backend` 原生方法。状态变更在锁内完成，原生调用移到锁外执行。覆盖 `addIceCandidate`、`endIceCandidates`、`resetSession`、`disconnect`、`close` 全部现存调用点。
- R2 **主锁不跨 RTC**：`BBSWebRemoteClient.onClose` 及其他持主锁路径不得调用可能阻塞的 RTC 方法。RTC 断连改为锁外或异步执行。
- R3 **原生调用可放弃**：libwebrtc 调用须有超时与放弃机制，单个 peer 的原生挂死不得传导为全局停摆；挂死须留痕告警。
- R4 **自愈**：任一 RTC peer 挂死后，UI 帧管线必须继续工作（可降级到 WS_ONLY），且切换界面／重连后恢复。

### 可观测性（防复发）

- R5 帧管线每一处**丢弃、跳过、闩锁**可计数可读出，覆盖 FSR 捕获层（需求门、`ACTIVE_FRAME`、会话比对）、webremote 编码层（编码失败、生成号失配、分发在飞/挂起）、浏览器组帧层（缺块、缓存淘汰与占满、会话门丢弃）。
- R6 不可静默：任何导致「连接正常但无帧」的状态必须在客户端日志留痕，并可经诊断面板（`diagDeliveryUi` / `diagChunks` / `diagMissing`）读出。
- R7 **锁持有时长看护**：主锁与 RTC 锁的异常长持有须能被检测并告警，使同类问题下次一眼可见而非靠线程转储考古。

### 附带缺陷

- R8 修掉三个已定位的独立缺陷（`deliver()` 缺 finally、`publishStandaloneWorldReplayFrame` 缺 finally、`dispatchFrame` 静默降级），并补回归测试。
- R9 不破坏现有行为：wire 协议向后兼容，`namespace.WebTest` 导出只增不删，现有 9 个 webTest 与 `check` 全绿。

## Acceptance Criteria

- [ ] AC1 复现路径固定：给出「打开影片 → 无帧」的确定性复现步骤，并记录复现时 `diagDeliveryUi` / `diagChunks` / `diagMissing` / 会话 epoch 的实测读数。
- [ ] AC2 病灶层定位有实证：依据服务端 `offeredFrames` / `dispatchSucceededFrames` 与浏览器组帧读数，判定断点在捕获层、编码发送层还是组帧层，并在任务 research/ 留档。
- [ ] AC3 打开影片后 UI 帧持续返回，帧率与打开前同量级。
- [ ] AC4 进入影片再切回其他界面，UI 帧正常（验证 R4 自愈）。
- [ ] AC5 R1 计数器在故障注入下能准确指出丢弃位置（含单元测试覆盖）。
- [ ] AC6 三个已定位缺陷各有一个失败前置的回归测试，修复后转绿。
- [ ] AC7 `gradlew :bbs-web-remote:check` 与 FSR 侧 `uiMirrorDemandTest` 全绿，且按红线要求确认真实 exit code（不得以管道尾部输出判定）。
- [ ] AC8 人工冒烟：打开影片、影片内播放/拖动时间轴、退出影片、重连浏览器，四个场景均有画面。

## Constraints

- 客户端为 LAN 模式，外部诊断需 `bbs_remote_session` cookie 或重新配对；探针脚本 `temp/ui-probe.mjs`（只读，原始 HTTP 升级）已就绪。
- 修改跨两个仓库区域：`new/`（FSR 捕获层，多会话并发编辑，提交需按文件挑选）与 `bbs-web-remote/`。
- 浏览器端新增模块须三处同步（`index.html`、`build.gradle` 的 `webRuntimeScripts`、webTest require）。
- 构建验证禁用管道吞退出码的写法，须真实 exit code + 日志双确认。
