# Research: 死亡时录制停止 bug 调查（death-recording-stop）

- **Query**: 玩家死亡时，右 Alt（`keyRecordReplay`）录制的当前回放直接停止，期望死亡动作被录进回放并继续录制
- **Scope**: 内部（源代码）+ 外部（MC 1.21.1 / NeoForge 21.1 反编译源码）
- **Date**: 2026-08-02
- **环境**: MC 1.21.1, NeoForge `neo_version=21.1.222`（gradle.properties），工作目录 `new/`

---

## 结论先行（TL;DR）

| 问题 | 结论 |
|---|---|
| A 死亡触发链 | 死亡瞬间**不触发任何** BBS/NeoForge 事件；客户端在重生（收到 `ClientboundRespawnPacket`）时才触发 `ClientPlayerNetworkEvent.Clone` |
| B 停止路径 | 实际命中 `BBSModClient.onClientPlayerClone`(BBSModClient.java:666) → `films.reset()` → `Films.reset()`(Films.java:552) → `stopRecordingForClientLifecycle`。`Films.update()` 的 `isInCurrentLevel()` 是**次级兜底**（Films.java:430-433）。两条都在**重生时刻**触发，不在死亡瞬间 |
| C 死亡时 morph | 死亡时 morph 仍在（`PlayerEntityMorphMixin` 每 Player 持 non-null `morph`）；本次代码库**无任何 death 事件/自动 demorph 钩子**。但重生的新 LocalPlayer 是**全新空 morph（form=null）** |
| D 安全契约 | 两条 source-text 测试把 `isInCurrentLevel()` 的"精确 player 身份检查"和 `onClientPlayerClone` 里的 `films.reset()/films = new Films()` 钉死为文本标记；改法必须保留这些标记文本（可加条件/析取），否则测试挂 |
| E 修复方案 | 推荐 Option 2：`onClientPlayerClone` 内当 `oldPlayer.isDeadOrDying()` 且有活跃 recorder 时跳过 films 拆解 + 给 recorder 加 survive 标记 + `isInCurrentLevel()` 加析取。断线/切维度仍停止 |

---

## A. 玩家死亡的完整触发链（MC 1.21.1 客户端）

反编译证据源：`new/.gradle/codex-sound-jar-build/neoForm/neoFormJoined1.21.1-20240808.144430/steps/decompile/outputs.jar`（vanilla）与 `~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*_output.jar`（NeoForge 补丁后）。行号对应 1.21.1（20240808.144430 的 neoForm 反编译）。

### 死亡瞬间（tick T）

1. 服务端确认死亡 → 发 `ClientboundPlayerCombatKillPacket`。
2. `ClientPacketListener.handlePlayerCombatKill`（vanilla ClientPacketListener.java:1634-1643）：
   - `if (entity == this.minecraft.player)` 且 `player.shouldShowDeathScreen()` → `minecraft.setScreen(new DeathScreen(...))`；否则直接 `player.respawn()`。
   - **`minecraft.player` 引用不变**——仍是死亡的 `LocalPlayer` 实例。
3. `DeathScreen.isPauseScreen()` 返回 **false**（vanilla DeathScreen.java:155-157）→ 死亡界面**不暂停游戏**，客户端/tick 继续。
   - 单机也同理：`Minecraft.runTick` 里 `pause = singleplayerServer != null && screen.isPauseScreen() && ...`（patched Minecraft.java ~1228-1233），DeathScreen 不是 pause screen → `this.pause = false`。
4. 死亡动画由 `LivingEntity.tickDeath()` 驱动：`LocalPlayer.tickDeath()`（vanilla LocalPlayer.java:835-841）每 tick `deathTime++`；到 **deathTime==20** 时 `this.remove(Entity.RemovalReason.KILLED)`（尸体移出世界）。但字段 `Minecraft.player` **仍指向该实例**，level 也不变。

   所以从死亡到重生之间，`Recorder.isInCurrentLevel()` 里的 `client.player == this.initialPlayer` 和 `client.level == this.initialLevel` **一直成立** —— **这段死亡动画期间录制是继续的**。

### 重生（用户点 Respawn，tick T+R）

5. 用户点 Respawn → `DeathScreen` 按钮回调 `this.minecraft.player.respawn()`（vanilla DeathScreen.java:41）→ `LocalPlayer.respawn()`（vanilla LocalPlayer.java:312-315）发送 `ServerboundClientCommandPacket(Action.PERFORM_RESPAWN)`。
6. 服务端回应 `ClientboundRespawnPacket` → 客户端 `ClientPacketListener.handleRespawn`（vanilla ClientPacketListener.java:1098+，NeoForge 补丁版)：
   - 创建新 `LocalPlayer`；若该维度变化会先 `minecraft.setLevel(newLevel)`（vanilla ~1128-1133）；
   - 新版 patched ~1149：`this.minecraft.player = localplayer1;`
   - **patched ~1171**：`net.neoforged.neoforge.client.ClientHooks.firePlayerRespawn(...)` → `ClientHooks.java:730-731` `NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.Clone(pc, oldPlayer, newPlayer, ...))`；
   - patched ~1179-1180：若 screen 是 DeathScreen → `minecraft.setScreen(null)`。
7. NeoForge `Clone` 事件文档（neoforge sources `ClientPlayerNetworkEvent.java:128`）："Fired when the client player **respawns, creating a new player instance** to replace the old player instance." —— 明确是**重生/换维度**时触发（`handleRespawn` 在死亡重生与维度变化两条路径都会走，`flag = resourcekey != resourcekey1` 区分，但 Clone 事件两条都发）。
8. 真正的断线/离开：`Minecraft.disconnect(Screen,boolean)`（patched Minecraft.java:2134）→ ~2152 `ClientHooks.firePlayerLogout(...)` → `ClientPlayerNetworkEvent.LoggingOut` → `LoggingOut` 是唯一与死亡无关的"出门"信号。

### BBS 侧挂接点

- `BBSClientNeoEvents.register` 里注册了三个相关监听（BBSClientNeoEvents.java:76-78）：
  - `onPlayerClone` → `ClientPlayerNetworkEvent.Clone`（BBSClientNeoEvents.java:218-225）
  - `onLoggingOut` → `ClientPlayerNetworkEvent.LoggingOut`（BBSClientNeoEvents.java:213-216 = `BBSModClient.onClientDisconnect`）
  - `onGameShuttingDown`
- **没有** `LivingDeathEvent`/`PlayerDeathEvent` 之类的监听（全仓库 grep 仅 `ActorEntity`/`MCEntity` 用 `isDeadOrDying()`，无死亡事件处理）。

### tick 内先后顺序（决定 B 谁先触发）

`Minecraft.tick()`（patched）顺序：
1. ~1787 `ClientHooks.fireClientTickPre()`（ClientTickEvent.Pre）
2. ~1805 `this.gameMode.tick()` → `MultiPlayerGameMode.tick()`（MultiPlayerGameMode.java:269-277）→ `connection.getConnection().tick()` **在这里处理包**（`handleRespawn` → Clone 事件 → `BBSModClient.onClientPlayerClone`）
3. ~1882 `this.level.tick()`
4. ~1915 `ClientHooks.fireClientTickPost()`（ClientTickEvent.Post）→ `BBSClientNeoEvents.onClientTickPost`（198-201）→ `BBSModClient.onClientTickPost` → `films.update()`（BBSModClient.java:730）

→ **同一 tick 内 Clone 处理器先于 `films.update()` 执行**。

---

## B. 录制停止的确切路径

### 主路径（重生时必命中的一行）

```
ClientboundRespawnPacket
 → handleRespawn
   → minecraft.player = newPlayer            (patched ClientPacketListener ~1149)
   → ClientHooks.firePlayerRespawn           (~1171)
     → ClientPlayerNetworkEvent.Clone
       → BBSClientNeoEvents.onPlayerClone    (BBSClientNeoEvents.java:218-225)
         → BBSModClient.onClientPlayerClone  (BBSModClient.java:654-678)
           → runClientLifecycleStep("reset Film controller state ...", ...)
               → films.reset()               (BBSModClient.java:666)   ★ 命中点
                 → Films.reset()             (Films.java:538-573)
                   → this.stopRecordingForClientLifecycle(recorder)
                                             (Films.java:552)
                     → stopRecording(recorder, false, false)   (Films.java:215-218 → 220-291)
                       → this.recorder = null; recorder.shutdown(false)
           → films = new Films()             (BBSModClient.java:669)
```

- `recorder.shutdown(false)`（Recorder.java:286-345）`restorePlayer=false`：**不会**传送玩家回 `lastPosition`，也不会恢复 `lastForm`。
- 到这一步录制已死（`Films.recorder=null`），后续 `films.update()` 看到的 recorder 为 null。

### 次级兜底路径（若 recorder 活着跨过克隆）

`Films.update()`（Films.java:428-435）：
```java
Recorder recorder = this.recorder;

if (recorder != null && !recorder.isInCurrentLevel())
{
    this.stopRecordingForClientLifecycle(recorder);   // Films.java:432

    return;
}
```
`Recorder.isInCurrentLevel()`（Recorder.java:170-178）：
```java
Minecraft client = Minecraft.getInstance();

return this.initialLevel != null
    && this.initialPlayer != null
    && client.level == this.initialLevel
    && client.player == this.initialPlayer;
```
重生后 `client.player` 已是新 LocalPlayer → `client.player != initialPlayer` → false。所以即使 `onClientPlayerClone` 不杀录制，**下一个 `ClientTickEvent.Post` 的 `films.update()` 也会杀**。

### 结论：两者都触发，主路径先于兜底

- 由于 tick 顺序（A 第 7 步），实际先命中主路径 `BBSModClient.java:666 films.reset()`；兜底 `Films.java:430-432` 在死亡场景**没有机会**运行（recorder 已 null）。
- 兜底的价值在于捕获**不经过 Clone 事件**的 player/level 变化（例如断线、`minecraft.player` 变 null），是"最后一公里"保险。
- **用户感知"死亡即停"的原因**：死亡→重生的间隔通常 <1 秒，死亡动画（20 tick ≈ 1 秒）大部分还没录完就被重生清理。若用户在死亡界面停留超过 20 tick，死亡倒下动画其实已录进回放，但一旦点 Respawn 就全停。

### 断线/退出仍能停（现状已满足）

`Minecraft.disconnect` → `LoggingOut` → `BBSModClient.onClientDisconnect`（BBSModClient.java:620-652）同样 `films.reset()`（641）+ `films = new Films()`（642）。这条路径与死亡无关，修复时必须保持。

---

## C. Morph / 玩家 form 在死亡时的行为

- `PlayerEntityMorphMixin`（`@Mixin(Player.class)`）给每个 Player 加了字段：
  ```java
  public Morph morph = new Morph(this);      // PlayerEntityMorphMixin.java:14
  @Override public Morph getMorph() { return this.morph; }   // :22-25
  ```
  → `Morph.getMorph(localPlayer)`（Morph.java:61-69）**永不返回 null**（只要 player 对象存在）。
- **死亡不会 demorph**：全仓库无 death 事件监听、无"死亡清 form"逻辑。demorph 只有三个入口：`<period>` 快捷键（BBSModClient.java:755）、`/bbs demorph`（BBSCommands.java:425）、网络 form 包。
- 死亡动画期间 `Recorder.update()`（Recorder.java:199, 217-219）：
  ```java
  LocalPlayer player = Minecraft.getInstance().player;   // 仍是老实例
  Morph morph = Morph.getMorph(player);                  // non-null
  this.keyframes.record(this.tick, morph.entity, null);  // 不 NPE
  ```
  `record` 里 `death` 通道走 `MCEntity.isDead()`（MCEntity.java:215-218）= `living.isDeadOrDying()` = `getHealth() <= 0`（vanilla LivingEntity.java:1078-1080）= true → `death: 1`（ReplayKeyframes.java:182）。20 tick 后尸体被 remove，但实体对象还在（字段冻结），读取不炸。
- **重生后**：新 `LocalPlayer` 的 mixin 字段是全新 `new Morph(this)`，**form = null**（PlayerEntityMorphMixin.java:14 的初始化发生在构造时）。`BBSMorph` NBT 的写入/读取在 `PlayerEntityMixin.onAddAdditionalSaveData/onReadAdditionalSaveData`（PlayerEntityMixin.java:61-80），只在实体 save/load 时发生（服务端路径）；客户端新 LocalPlayer **不会**重建 form。form 同步靠 mod 自己的包（`ClientNetwork.sendPlayerForm`，如 `Films.startRecording` 里 Films.java:157）。
  - 含义：若录制的 recorder 跨过重生继续，**重生后的帧是"无 form 的原版外观"**，数字通道（位置/rotation/death=0）正常；回放最终用的 form 是 film replay 自带的 form（`Films.startRecording` Films.java:136-140）和 `Recorder.lastForm`（Recorder.java:57,131，录制开始时快照，`shutdown(true)` 时会 `sendPlayerForm(lastForm)` 恢复）。
  - 这是"死亡继续录制"方案的一个已知降级点，冒烟时需确认新 player 是否会被服务器重新下发 form（`handlePlayerFormPacket` 路径）。

---

## D. 安全契约约束（两条 source-text 测试逐条）

测试通过 `section(type) + assertOrdered(markers)` 读生产**源码文本**断言。要改且能过，必须保住标记字符串及其相对顺序。

### D1. `ClientTransportIdentitySourceTest.cloneLifecycleRetiresScopeAndPlayback`（ClientTransportIdentitySourceTest.java:280-355）

| # | 行号 | 断言类型 | 内容 | 对修复的影响 |
|---|---|---|---|---|
| 1 | 306-307 | check | events 源码含 `NeoForge.EVENT_BUS.addListener(BBSClientNeoEvents::onPlayerClone)` | 不动 |
| 2 | 308-312 | assertOrdered | cloneHandler 段含 `BBSModClient.onClientPlayerClone(` → `event.getConnection()` → `event.getOldPlayer()` → `event.getNewPlayer()` | 不动（BBSClientNeoEvents 不改） |
| 3 | 313-319 | assertOrdered | networkClone 段（ClientNetwork.onClientPlayerClone 至 isBBSModOnServer）含 `connectionGate.replacePlayer(` → `getCurrentTransport(client)` → `client.player` → `replacement == null` → `clearRetiredTransfer(replacement)` → `callbacks.reset()` | **不能跳过 `ClientNetwork.onClientPlayerClone` 调用**（网络传输 scope 轮换必须保留） |
| 4 | 320-324 | assertOrdered | lifecycle 段（BBSModClient.onClientPlayerClone 至 resetCameraControllersForLifecycle）含 `ClientNetwork.onClientPlayerClone(connection, oldPlayer, newPlayer)` → **`films.reset()`** → **`films = new Films()`** → `resetCameraControllersForLifecycle("client-player clone")` | **修复后 `films.reset()`/`films = new Films()` 的**文字**必须仍在段内、顺序不变**（可包条件） |
| 5 | 325-331 | assertOrdered | cameraRemoval（CameraController.removeAll）顺序 | 不动 |
| 6 | 332-333 | check | bbsClient 含 `cameraController.removeAll(PlayCameraController.class)` | 不动（相机清理保留） |
| 7 | 334-336 | assertOrdered | cameraLifecycle：`cameraController.removeAll(...)` → `cameraController.reset()` | 不动 |
| 8 | 337-338 | check | cameraLifecycle **不含** `play.getContext().shutdown()` | 不动 |
| 9 | 339-340 | check | bbsClient 含 `resetCameraControllersForLifecycle("disconnect")`（disconnect 与 clone 共用清理） | 不动 |
| 10 | 341-346 | assertOrdered | cameraReset（CameraController.reset）顺序 | 不动 |
| 11 | 348-354 | check | c5（handleStopFilmPacket 段）：仍含 `executeIfCurrent(client, scope, false` 且**不含** `ClientLevel level = client.level` | 不动 |

### D2. `ActionRuntimeAuthorityWiringTest.verifyExactLocalRecordingStop`（ActionRuntimeAuthorityWiringTest.java:483-765）

| 段 | 行号 | 断言 | 对修复的影响 |
|---|---|---|---|
| recorderConstructor | 640-646 | ctor 含 `this.initialLevel = Minecraft.getInstance().level`、`this.initialPlayer = Minecraft.getInstance().player`；且 `this.recordingFilmId=...` → `recordingReplayId` → `recordingTick` 顺序 | **Recorder ctor 不能改成从别处取 initialPlayer/Level；网络 tuple 顺序保留** |
| recorderLevel | 649-654 | `isInCurrentLevel()` 段内按序含 `Minecraft client = Minecraft.getInstance()` → `this.initialLevel != null` → `this.initialPlayer != null` → `client.level == this.initialLevel` → **`client.player == this.initialPlayer`** | **这是修复的最大约束**：不能删掉 `client.player == this.initialPlayer` 这个文本，否则 assertOrdered 挂。只能加析取/条件保住文本 |
| filmsUpdate | 655-660 | `Films.update()` 段顺序：`Recorder recorder = this.recorder` → `recorder != null && !recorder.isInCurrentLevel()` → `this.stopRecordingForClientLifecycle(recorder)` → `return` → `recorder.update()` | Films.update 不改即过 |
| recordedFrame | 661-662 | `hasRecordedFrame()` 含 `return this.lastPosition != null` | 不动 |
| recorderShutdown | 663-667 | `shutdown(boolean)` 段：`restorePlayer && this.isInCurrentLevel() && pos != null` → `PlayerUtils.teleport` → `ClientNetwork.sendPlayerForm` → `super.shutdown()` | 文本保留即过；语义见 E 的风险点 |
| lifeCycleTeardown | 593-594 | `stopRecordingForClientLifecycle` = `stopRecording(recorder,false,false)` | 不动 |
| panelOpen / keyRecording(disconnect 段内) | 757-764 / 809-816 | `keyRecordReplay`/`UIFilmPanel.open` 段内 `recorder.isInCurrentLevel()` 文本 | 保留方法签名即过 |
| disconnectTearsDownOldFilmsLocally | 790-792 | `onClientDisconnect` 段按序含 `films.reset()` → `films = new Films()` | **disconnect 路径保持全量 reset（不许条件化）** |
| reset 段 | 793-800 | `Films.reset` 含 `stopRecordingForClientLifecycle(recorder)`、`controller.shutdown()`、`pendingRecordingTerminals.clear()`，不含 `ClientNetwork.sendActionRecording` | Films.reset 不改即过 |
| stopFilmRecordingForLifecycle | 801-808 | 段内顺序含 `recorder.isInCurrentLevel()` | 不动 |

### 关键结论（D）

- 只要 `onClientPlayerClone` 里**保留** `films.reset()` + `films = new Films()` 的**字面文本**（包 `if` 条件），`Recorder` ctor 不改 `initialPlayer/initialLevel` 赋值，`isInCurrentLevel()` 里**保留五个标记文本**（加析取），`onClientDisconnect` 不动 —— 两条测试**可以原样通过**。
- 但这是"文本层"通过。两条测试的**语义意图**（clone 必须 teardown、录制必须绑定精确 player 身份）被新的"死亡重生例外"削弱。评审应决定是否把例外写进测试（例如在 `cloneLifecycleRetiresScopeAndPlayback` 增加 `oldPlayer.isDeadOrDying()` 分支断言），否则将来重构可能踩掉该保护而不自知。

---

## E. 修复方案评估

统一背景：套用后 t=重生的 tick 顺序 =（handleRespawn 换 player → Clone 事件）→ **同 tick 稍后** `films.update()`。所以"标记 survive"必须在 Clone 事件回调里完成，`Films.update()` 的下一次检查就会放过。

### Option 1（implement.md 现行方案：有 recorder 就跳过 reset + 放宽 isInCurrentLevel）

做法：
1. `onClientPlayerClone`：`Recorder r = films.getRecorder(); if (r == null) { films.reset(); films = new Films(); }`（保留文本标记）。
2. `isInCurrentLevel()` 放宽到"level 身份相同 且 当前 player 非空"。

风险/评估：
- 安全：断线（LoggingOut→onClientDisconnect 全量 reset）与切维度（level 身份变 → isInCurrentLevel false → 兜底停）仍停。**达标**。
- 测试：需要保住 `client.player == this.initialPlayer` 文本（如 `(client.player == this.initialPlayer || client.player != null)` 这类别扭写法），否则 D2-649 挂。
- 复杂度：低（1 个方法 + 1 个 if）。
- 缺点：无"死亡"判定，纯 level 身份决定存活；对"同 level 但并非死亡重生"的克隆（极罕见）也会误放行。

### Option 2（推荐：以 `oldPlayer.isDeadOrDying()` 唯一门控 + recorder survive 标记）

做法：
1. `onClientPlayerClone`：
   ```java
   Recorder active = films.getRecorder();
   boolean deathRespawn = oldPlayer.isDeadOrDying();       // 死亡重生 = 老 player 血量<=0
   if (active == null || !deathRespawn) {
       films.reset();                                       // 文本保留
       films = new Films();                                 // 文本保留
   }
   if (active != null && deathRespawn) {
       active.surviveRespawn();                             // 置 cloneSurvived=true
   }
   // ClientNetwork.onClientPlayerClone / resetCameraControllersForLifecycle 不变
   ```
2. `Recorder` 新增 `private boolean cloneSurvived;` + `void surviveRespawn() { this.cloneSurvived = true; }`；ctor 不动。
3. `isInCurrentLevel()`（保住五个标记文本）：
   ```java
   return this.initialLevel != null
       && this.initialPlayer != null
       && client.level == this.initialLevel
       && (client.player == this.initialPlayer || this.cloneSurvived);
   ```
4. `Films.update()` / `Films.reset()` / `onClientDisconnect` 全不动。

评估：
- 安全：与 Level-identity 联合，语义精确到"只有死亡重生才续录"。切维度（portal）→ 新 level → 停；断线 → LoggingOut → 全量 reset；死亡界面推出 → 同样 LoggingOut。
- 测试：D1 的所有文本标记都保留（`films.reset()`/`films = new Films()` 仍在、顺序不变；`ClientNetwork.onClientPlayerClone` 调用保留；camera 部分不动）；D2 的 isInCurrentLevel 五标记按原序出现（析取追加在 `client.player == this.initialPlayer` 之后），ctor 不改。两条测试**零修改可通过**。
- 复杂度：中（Recorder 一个字段 + 一个 setter + 一个析取；BBSModClient 一个 if）。
- 风险点（冒烟需验证）：
  - **停止时的 restore**：`Recorder.shutdown(true)`（Recorder.java:291）现在 `isInCurrentLevel()` 为 true → 会把重生后的玩家 `PlayerUtils.teleport` 回 `lastPosition` 并 `sendPlayerForm(lastForm)`。通常 lastPosition 就在重生附近、且 re-morph 是用户友好的，但需要人工确认体验。
  - 重生后新 player 无 form：录到的帧是原版外观（见 C）。如果服务端会在重生后重新下发 form（`handlePlayerFormPacket`），则无感；否则死亡到下次 morph 之间的回放是"裸替"。属已知降级，可与用户确认。
  - `pendingRecordingTerminals` 不随 reset 清空——因为没 reset——文件保留；对 `Films.update` 无影响。

### Option 3（纯 level 身份，去掉 player 身份主判据）

`isInCurrentLevel()` 只比较 level。需要靠 `(client.player == this.initialPlayer || client.player != null)` 文本技巧保测试。
- 语义弱：player 身份区分被移除，任何"同 level 但 player 换了"都会被放行；D2 的"孤立 player 身份"保护名存实亡。
- 不建议。

### 方案对比

| | 安全性（断线停） | 切维度停 | 死亡续录 | 测试影响 | 复杂度 |
|---|---|---|---|---|---|
| Option 1 | 停 | 停（level 变） | 续（无死亡判定） | 需保文本，零改可过 | 低 |
| Option 2 | 停 | 停（level 变） | 续（精确死亡判定） | 需保文本，零改可过 | 中 |
| Option 3 | 停 | 停 | 续（语义最弱） | 需保文本 | 低但语义差 |

**推荐 Option 2**，并在实施时按 D1/D2 逐条核对标记文本，必要时更新两条测试以显式编码"死亡重生例外"这一新不变式。

---

## Caveats / Not Found

- 反编译 jar 行号基于 neoForm 1.21.1-20240808.144430 + NeoForge 21.1.222 patches，可能与最终运行版有微小偏移；涉及 MC/NeoForge 的行号均标注"vanilla/patched"以防混淆。
- 未在运行环境实测"重生后服务端是否重新下发 form"（C 的降级点）与"死亡界面停留 >20 tick 再点重生的录制内容完整性"。
- 未验证 DeathScreen 上按 Alt 的行为（`keyRecordReplay` 需 dashboard 面板存在，BBSModClient.java:1104-1107；死亡界面下开始/停止录制的 UX 不在本次范围）。
- NeoForge `ClientPlayerNetworkEvent.Clone` 只有 old/new player 与 connection，**没有**暴露 `isRespawn()` 之类的死亡标记（与 Forge 服务端 `PlayerRespawnEvent` 不同）——所以死亡判定只能靠 `oldPlayer.isDeadOrDying()` 或 level 身份，已在 Option 2/1 处理。