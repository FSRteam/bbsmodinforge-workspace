# 技术设计：webremote 影片无帧根因治理

## 1. 管线全景与静默点清单

UI 帧从 FSR 渲染线程流到浏览器画布，跨三个进程内边界、两个线程交接、一个 WebSocket：

```
渲染线程                            镜像 worker 线程            encoder 线程                浏览器
UIScreen.render
  └ ensureMirrorSession
  └ beginFrame ──┐
  └ 绘制命令记录  │
  └ endFrame ────┴→ BBSUiMirrorRegistry.publish
                      └ ListenerEntry.offerFrame
                          └ pending 队列 ──→ drain → deliver → onFrame
                                                        └ BBSWebRemoteClient.offerFrame
                                                            └ latestFrame.set + drainFrames
                                                                └ toWireInput
                                                                └ ClientState.offerFrame
                                                                    └ LatestUiFrameDispatcher
                                                                        └ dispatchFrame → encode
                                                                            └ UiOutboundFlow
                                                                                └ socket ──→ consumeBinary
                                                                                              └ assembler.accept
                                                                                                └ CanvasRenderer.draw
```

**每一处静默丢弃**（当前无日志、无计数、无告警）：

| # | 位置 | 条件 | 是否可自愈 |
|---|------|------|-----------|
| S1 | `UIScreen.render` | `isRenderSessionCurrent` 为假即 return | 取决于 `mc.screen == this` |
| S2 | `BBSUiFrameRecorder.beginFrame` | `hasActiveDemand()` 为假 | 需求恢复才自愈 |
| S3 | `BBSUiFrameRecorder.beginFrame` | `ACTIVE_FRAME` ThreadLocal 非空（泄漏） | **不可自愈** |
| S4 | `ListenerEntry.captureEnabled` | `callbackStartedNanos` 未归零 | **不可自愈**（Error 逃逸时） |
| S5 | `ListenerEntry.drain` | `drainScheduled` 悬挂为真 | **不可自愈**（Error 逃逸时） |
| S6 | `BBSWebRemoteClient.drainFrames` | `session.sessionId() != frame.sessionId()` | 取决于会话回调配平 |
| S7 | `ClientState.offerFrame` | `!sessionOpen` | 需 SESSION 重开 |
| S8 | `ClientState.dispatchFrame` | `generation` 失配 → 当成功完成 | 单帧级，自愈 |
| S9 | `LatestUiFrameDispatcher` | `active` 恒真（Completion 不回调） | **不可自愈** |
| S10 | 浏览器 `consumeBinary` | `sessionOpenRequired` 或 epoch 失配 | 需 SESSION |

已由实测排除的分支：镜像层熔断（会打 `[bbs-client-ui-mirror] quarantined`）、服务端编码丢帧（会打 `Dropping an invalid …`）、组帧协议错误（会 `reportError(_, true)` 断连，而用户实测为「已连接」）。

**用户症状（已连接 + 画面定格 + 换界面不恢复）与「不可自愈」四项 S3/S4/S5/S9 强吻合。**

## 2. 定位策略：先测量，后修

不再猜。用服务端既有遥测把断点一次切开：

- `offeredFrames == 0` → 断在 S1–S5（FSR 捕获层／镜像交接层）
- `offeredFrames` 增长但 `dispatchSucceededFrames == 0` → 断在 S6–S9（webremote 编码发送层）
- 两者都增长但浏览器无帧 → 断在 S10 或 canvas 层

取数手段：`temp/ui-probe.mjs`（只读探针，原始 HTTP 升级 + `LINK_PING` 触发 `LINK_STATS`）。需服务端处于回环模式免鉴权，或提供 `bbs_remote_session` cookie。

## 3. 契约变更：消灭静默

### 3.1 FSR 侧（`new/`）

`BBSUiMirrorRegistry.ListenerEntry`：

- `deliver()` 改为 `try { invoke } catch (Throwable) { failure = t } finally { callbackStartedNanos = 0; callbackPhase = null }`。捕获 `Throwable` 而非 `Exception | LinkageError`：Error 同样必须被记账并进入退避，而不是把全局捕获门永久焊死。若为不可恢复的 Error（`OutOfMemoryError` / `StackOverflowError`），记账后重抛，但**状态必须已在 finally 中复位**。
- `drain()` 外层包 `try/finally`，finally 中在锁内复位 `drainScheduled = false` 并 `notifyAll()`；若 `pending` 非空则重新 `scheduleDrainLocked()`，保证 worker 死亡后仍可复活。
- 新增诊断计数（常数空间）：`framesSkippedNoDemand`、`framesSkippedQuarantined`、`callbackErrors`、`workerRestarts`，经 `BBSUiMirrorRegistry` 静态只读快照暴露。

`BBSUiFrameRecorder`：

- `publishStandaloneWorldReplayFrame()` 的 `beginFrame` 成功分支包 `try/finally`，finally 中若帧仍活跃则 `abortFrame()`，杜绝 `ACTIVE_FRAME` 泄漏。
- `beginFrame` 返回 false 时按原因累加计数（`noDemand` / `frameAlreadyActive` / `sessionMissing`）。`frameAlreadyActive` 属于不该发生的状态，除计数外按节流打 WARN——这是 S3 的唯一可见信号。
- 提供 `Diagnostics` 只读快照（含 `activeFrameLeaked` 布尔）供 addon 侧读取与测试断言。

### 3.2 webremote 侧

`LatestUiFrameDispatcher`：

- 引入**在飞看门狗**：记录 `dispatchStartedNanos`，`statistics()` 暴露 `inFlightAgeNanos`。当在飞时长超阈值（建议 5 s，取值写入常量并可注入以便测试），判定 Completion 丢失，走 `fail()` 关闭该 socket 而非无声悬挂。这把 S9 从「永久静默」降级为「断连 + 浏览器自动重连」。
- `close()` / `fail()` 路径保持幂等，不改变现有 `Statistics` 字段（只增字段，遵守向后兼容）。

`ClientState.dispatchFrame`：

- 编码失败除现有 WARN 外累加 `encodeFailures` 计数并入遥测，使「分块超限」这类结构性问题在诊断面板可见，而非只能靠翻日志。

`LinkTelemetryCoordinator.UiDelivery`：

- 追加字段（只增不删）：`encodeFailures`、`inFlightAgeMs`，以及 FSR 侧快照透传的 `captureSkippedNoDemand`、`captureFrameAlreadyActive`。浏览器 `diagDeliveryUi` 相应扩展展示。

### 3.3 浏览器侧

- `pruneFor` 抛 `CHUNK_CACHE_FULL` 前，先按 `lastUsed` 淘汰**非当前帧**的活跃候选并记账；仅当确实无可淘汰时才抛错，且错误文案带上 `chunkCount` / `chunkBytes` / `activeCount`，让日志可直接定位。
- `assembler` 暴露 `evictedChunks`、`cacheFullEvents` 计数，接入诊断面板。

## 4. 兼容性与回滚

- wire 协议：不新增消息类型，不改信封布局；遥测 JSON 仅**追加**字段，旧浏览器忽略未知字段（`handleJsonMessage` 的 default 分支已保证前向兼容）。
- `namespace.WebTest` 导出只增字段，绝不删除既有项。
- FSR 侧新增的诊断快照是纯读接口，不改 `BBSUiMirrorListener` 契约，故不影响其他 addon。
- 回滚点：三组改动（FSR 错误纪律 / webremote 看门狗 / 浏览器缓存记账）彼此独立，可单独 revert。看门狗阈值若引发误断连，可单独调大或关闭而不影响其余修复。

## 5. 风险

- **看门狗误判**：慢帧（影片巨帧编码耗时）可能被误认作 Completion 丢失。缓解：阈值取 5 s（远超正常编码耗时），且只在在飞期间无任何进展时触发。
- **捕获 `Throwable` 掩盖致命错误**：`OutOfMemoryError` 必须重抛，否则会把 JVM 濒死状态伪装成正常。设计中已明确「记账 + 状态复位 + 重抛」。
- **跨仓改动并发**：`new/` 同时被多会话编辑，提交须按文件挑选，编译失败先排除他人改动。
