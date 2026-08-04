# 死亡关键帧轨道 — 实施计划

## 状态
- 任务已 start（in_progress），死亡通道基础实现已完成（death 通道/record/apply/IEntity/MCEntity/StubEntity）。
- **用户测试反馈（2026-08-02）**：死亡红闪有了，但**没有倒下效果**（跟 damage 关键帧差不多）。
- **待修复（本轮）**：死亡倒下渲染。

## 排查结论（死亡无倒下根因）
- `applyDeath` 只设置了 `entity.setDeath(...)`（MCEntity → `living.deathTime`），form 渲染路径（`ModelFormRenderer.render3D` 等）从**不读取死亡进度做倒下旋转**。
- `ActorEntityRenderer.setupTransforms`（`ActorEntityRenderer.java:84-89`）有 `deathTime` 倒下旋转，但只在 **MC 实体 actor 渲染**路径生效；普通 form 预览/回放（StubEntity）不做倒下。
- `applyActorReplay` 只 apply `GROUP_POSITION`，actor 模式也没把 death 通道应用到 ActorEntity。

## 修复方案（死亡倒下）
### 方案思路
把"倒下旋转"做成 form 渲染路径可复用的能力：
1. `ReplayKeyframes.applyDeath` 已把 0..1 映射到 0..20 deathTime（ActorEntityRenderer 公式）并 `setDeath`。
2. **form 渲染路径**：在 `ModelFormRenderer.render3D`（以及 `MobFormRenderer` 复用实体渲染时的 `deathTime`）应用基于 `entity.getDeath()` 的 Z 轴倒下旋转。
   - 现有 `ActorEntityRenderer` 公式：`deathAngle = (deathTime + tickDelta - 1) / 20 * 1.6; rot = min(sqrt(deathAngle),1) * 90°`（Z 轴）。
   - form 渲染时以 `context.entity.getDeath()` 驱动同样旋转。
3. **actor 路径**：`applyActorReplay` 补 apply death（`List.of(GROUP_POSITION, "death")` 或仅 `death`），使 ActorEntity.deathTime 被关键帧驱动 → `ActorEntityRenderer` 倒下自动生效。

### 改动文件
- `ModelFormRenderer.java`（render3D 应用死亡旋转）
- `MobFormRenderer.java`（准备实体渲染时设 deathTime）
- `BaseFilmController.applyActorReplay`（apply death）
- 可能的公共工具：抽一个 `DeathPose`/`DeathAnimation` 辅助，统一 form 与 actor 的倒下公式。

## 实施顺序
### Step 1：公共死亡旋转工具
- 新增静态方法计算 `deathAngle`/旋转角（复用 `ActorEntityRenderer` 公式），供 form 与 actor 共用。

### Step 2：form 渲染应用死亡旋转
- `ModelFormRenderer.render3D`：`context.stack.mulPose(Axis.ZP.rotationDegrees(angleFrom(context.entity.getDeath())))`。
- `MobFormRenderer`（若走实体渲染）：把 `context.entity.getDeath()` 映射到实体 `deathTime`。
- **已实施（2026-08-02）**：
  - `ModelFormRenderer.render3D`（约 907 行）：`context.stack` 在 `ROTATE_Y_180` 后按 `context.entity.getDeath()` 应用 Z 轴倒下（公式同 `ActorEntityRenderer`），新增 `Mth` import。
  - `MobFormRenderer.java`（约 375 行）：`context.entity.getDeath() > 0F` 时把 `entity.deathTime` 设为该值，vanilla 实体渲染器自动应用到地旋转。
  - `BaseFilmController.applyActorReplay`（约 1124 行）：追加 `replay.keyframes.applyDeath(ticks, actorEntity)`，驱动 `ActorEntity.deathTime` → `ActorEntityRenderer` 自动倒下。

### Step 3：actor 路径 apply death
- `applyActorReplay` 追加死亡应用。**已实施（见上）。**

### Step 4：编译与测试
- `new/` 编译通过（`compareJava compareClientJava`，2026-08-02 已通过）；剩余为运行时冒烟。

## 死亡旋转 — 待验证
- [ ] 运行时冒烟：form 预览、MobForm（生物）、actor 三个路径都应在死亡时变红+倒下。
- [ ] BBS 自建模型（ModelForm）的 Z 轴倒下方向与 actor/v`nilla 一致（可能与模型朝向有关，若方向相反需翻转）。

## 待修复二：死亡时停止录制（用户测试反馈 2）
- 现象：右 Alt 在仪表盘外录制当前回放，玩家死亡时录制直接停止（而非继续录制死亡过程）。
- **研究结论（research/death-recording-stop.md，2026-08-02）**：
  - 死亡瞬间不触发任何 BBS/NeoForge 事件，`Minecraft.player` 引用不变，**死亡动画期间录制仍在继续**。
  - 停止发生在**重生时刻**：收到 `ClientboundRespawnPacket` → `ClientPlayerNetworkEvent.Clone` → `BBSModClient.onClientPlayerClone` → `films.reset()` → `stopRecordingForClientLifecycle`。次级兜底：`Films.update()` 的 `!isInCurrentLevel()`（重生后 player 引用变化）。
  - 用户感知"死亡即停"= 死亡→重生间隔太短，20 tick 死亡动画没录完就被清理。
- **修复方案（Option 2，已实施 2026-08-02）**：
  1. `Recorder` 新增 `cloneSurvived` 字段 + `surviveRespawn()` setter；`isInCurrentLevel()` 加析取 `(client.player == this.initialPlayer || this.cloneSurvived)`（保留原标记文本）。
  2. `BBSModClient.onClientPlayerClone`：仅当「无活跃 recorder 或 非死亡重生（`!oldPlayer.isDeadOrDying()`）」时才 `films.reset()` + `films = new Films()`；死亡重生且有活跃 recorder 时保留 Films 并 `activeRecorder.surviveRespawn()`。
  3. 网络安全测试契约（`ClientTransportIdentitySourceTest` / `ActionRuntimeAuthorityWiringTest`）：文本标记保留，零修改应可通过。
- **需要验证**：`networkSecurityTest` 运行通过。
- **已知降级点（冒烟确认）**：重生后的新 LocalPlayer 是全新空 morph（form=null），跨重生继续录制的帧会录成原版外观（数字通道正常）。若服务端会重生后重下 form 则无感。

## 验收复核清单（评审用二）
- [ ] 死亡时录制**继续**（死亡动作被录进回放），重生后仍能停止/合并。
- [ ] 真正断线/切维度仍停止录制。
