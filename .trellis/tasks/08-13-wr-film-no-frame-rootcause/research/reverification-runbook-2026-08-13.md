# 复验手册：影片无帧修复后的真机验收（AC1 / AC2 / AC4 / AC8）

> 代码修复与自动化测试可在开发机完成；**AC1/AC2/AC4/AC8 必须在真实客户端 + 浏览器上跑**。
> 本手册给出确定性步骤与判读口径，避免下次又靠线程转储考古。
>
> 前置：修复后的构建已部署（见「0. 部署」），故障机为 PCL 启动的正式客户端，
> gameDir `D:\1.21\versions\1.21.1-NeoForge_21.1.223`。

---

## 0. 部署（开发机 → 游戏目录）

FSR 侧改动只有重建 jar 才会进入运行时；`bbs-web-remote` 也依赖磁盘上的这个 jar。

```powershell
cd D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new
& .\gradlew.bat jar --console=plain 2>&1 | Select-Object -Last 10; Write-Host "EXIT=$LASTEXITCODE"

cd D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\bbs-web-remote
& .\gradlew.bat build --console=plain 2>&1 | Select-Object -Last 10; Write-Host "EXIT=$LASTEXITCODE"
```

把 `new/build/libs/BBS_FSR_beta_v0.0.11.jar` 与 `bbs-web-remote` 的
`bbs-web-remote-0.1.0-all.jar` 覆盖到游戏 mods 目录后**完全重启客户端**
（本修复涉及静态状态与线程模型，热重载不算数）。

## 1. 探针取数前置：关掉 LAN 鉴权

服务端处于 LAN 模式时 `ServerAccessConfig.requiresAuthentication()` 为真，无凭据探针被 401 拒绝。

游戏内 → BBS 设置 → Web Remote → 关闭 `lan_enabled` → 应用。

> 顺带验证：设置面板「应用」按钮**必须正常返回**，不再卡在处理中。
> 卡住 = `bbs-web-remote-lifecycle` 线程又在等主锁 = 主锁雪崩复发，立即停止验收并抓
> `jstack`（见第 5 节）。

## 2. 三段读数（AC1 / AC2）

探针：`temp/ui-probe.mjs`（只读，原始 HTTP 升级，端口 27165，自动打印判决）。

```powershell
cd D:\bbsinneoforge\bbsmodinforge\bbsmodinforge
node temp/ui-probe.mjs 10
```

| 段 | 客户端处于什么界面 | 期望 |
|---|---|---|
| A 基线 | 仪表盘/普通 BBS 界面 | `UI_FRAME` > 0，`offeredFrames` 与 `dispatchSucceededFrames` 同步增长 |
| B 影片 | **打开影片编辑器后**立刻跑 | 同 A，帧率同量级（AC3） |
| C 切回 | 从影片切回其他界面后 | 同 A（AC4 自愈） |

三段读数记入 `research/frame-stall-measurement-2026-08-13.md`，字段至少含：
`UI_FRAME` / `CHUNK_DEF` / `offeredFrames` / `dispatchSucceededFrames` /
`failedFrames` / `discardedFrames` / `inFlight` / session epoch。

**判读口径**（探针已内置，人工复核）：

- `offeredFrames == 0` → 断在 FSR 捕获层（需求门 / `ACTIVE_FRAME` / 会话）。此时读
  `BBSUiFrameRecorder.diagnostics()` 与 `BBSUiMirrorRegistry.diagnostics()` 的透传字段
  定位具体原因，**不要再猜**。
- `offeredFrames` 增长但 `dispatchSucceededFrames == 0` → 断在编码发送层，看
  `encodeFailures` 与 `inFlightAgeMs`（看门狗应已断连并触发浏览器重连）。
- 两者都增长但浏览器无画面 → 断在组帧/canvas 层，看 `diagChunks` 的
  `evictedChunks` / `cacheFullEvents`。

> 探针的 `----- 服务端 UI 投递遥测 -----` 段目前只打印原有字段。webremote 侧新增的
> `encodeFailures` / `inFlightAgeMs` / `rtcSignalingStuck` 落地后，需同步扩展探针打印，
> 否则新计数只能从浏览器诊断面板读。

## 3. 人工冒烟四场景（AC8 / G3）

浏览器打开远程页面，逐项确认**画面持续更新**（不是定格）：

1. 打开影片编辑器 → 有画面
2. 影片内播放 / 拖动时间轴 → 有画面且跟随
3. 退出影片回到其他界面 → 有画面
4. 关闭浏览器标签后重新连接 → 有画面

任一项失败即记录当时的 `diagDeliveryUi` / `diagChunks` / `diagMissing` 三组读数再报告。

## 4. WebRTC 路径的针对性验证

根因在 RTC 信令，务必确认 RTC 真的启用着才算验到：

- 设置里 `webrtc_enabled` 为真且 `transport_mode` **不是** `WS_ONLY`
  （`WS_ONLY` 会完全绕开出问题的代码路径，等于没验）。
- 影片场景会产生媒体面并推动 ICE 交换——这正是原触发器，必须覆盖到。
- 若浏览器诊断出现 `rtcSignalingStuck`：说明原生又挂死了，但**这次 UI 帧必须照常**
  （R3/R4 的验收点）。这属于「已知 libwebrtc 缺陷 + 已降级」，不是回归。

## 5. 复发时的取证（不要重复上次的弯路）

症状再现时**先抓线程转储再重启**，否则证据丢失：

```powershell
jps -l                       # 找 Minecraft 客户端 pid
jstack <pid> > temp\jstack-<时间戳>.txt
```

判读锚点（对照 `research/jstack-hang-2026-08-13.txt`）：

- 搜 `WebRtcCoordinator` — 是否有线程 `locked` 住那个 lock 且卡在 `Native Method`。
- 搜 `BBSWebRemoteClient.onOpen` — 是否 `BLOCKED` 在等 `a mchorse.bbs_web_remote.client.BBSWebRemoteClient`。
- 搜 `bbs-web-remote-rtc-signaling` — 是否卡在原生调用（本次修复后这**只应**损失该线程，
  不应再有其他线程被它阻塞）。

若发现任何线程在**持有**协调器锁或客户端监视器的同时卡在 `Native Method`，即为锁纪律回归，
对照 `.trellis/spec/frontend/webremote-rtc-lock-discipline.md` 第 7 节修正。
