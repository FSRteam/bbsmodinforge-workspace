# 死亡关键帧轨道 — 技术设计

## 背景与现状

BBS 回放系统里，实体的死亡/受击呈现依赖两条不同渲染路径：

- **form 预览路径**（`BaseFilmController` 渲染 `FormUtilsClient.render`）：实体为 `StubEntity`。
  - 死亡红闪：`BaseFilmController.java:160` 计算 overlay = `OverlayTexture.pack(u(0), v(entity.getHurtTimer() > 0))`，即红闪由 `hurtTimer > 0` 触发。
  - 倒下角度：form 预览不做倒下（`ModelFormRenderer`/`MobFormRenderer` 无 deathTime 旋转）。
- **actor 实体路径**（`ActorEntityRenderer` 渲染 `ActorEntity`）：
  - 死亡红闪+倒下：`ActorEntityRenderer` 读 `livingEntity.deathTime > 0` 计算 Z 轴倒下角（`ActorEntityRenderer.java:84-89`），overlay 用 `LivingEntityRenderer.getOverlayCoords`。
  - 由 `ActorEntity` 的 MC tick（`tickDeath`）驱动，与关键帧无关。

现有 `ReplayKeyframes` 已有 `damage` 通道（受击 `hurtTime` 红闪），但没有"死亡"通道。

## 目标

新增 `death` 关键帧通道，让死亡状态（变红 + 倒下）可被录制/回放控制，覆盖玩家/生物相关轨道（用户决策：玩家/生物轨道）。

## 设计：`death` 通道

### 1. 数据通道

`ReplayKeyframes` 新增（`ReplayKeyframes.java`）：

```java
public final KeyframeChannel<Double> death = new KeyframeChannel<>("death", KeyframeFactories.DOUBLE);
```

- 加入构造器 `this.add(this.death)`。
- 加入 `CURATED_CHANNELS` 列表（`ReplayKeyframes.java:31`）。
- 语义：0 = 存活，>0 = 死亡（沿用布尔态约定，与 `sneaking/sprinting/grounded` 一致）。

### 2. 录制 `record()`

在 `record()`（`ReplayKeyframes.java:151`）中采集死亡状态：

```java
// 位置/rotation 组之外始终记录（与 sneaking/hurtTimer 同级）
this.death.insert(tick, entity.isDead() ? 1D : 0D);
```

`IEntity` 需要新增死亡状态读取。

### 3. 接口扩展 `IEntity`

`IEntity`（`forms/entities/IEntity.java`）新增：

```java
public boolean isDead();
public void setDeath(float deathTime);  // 死亡进度，0=存活，>0=死亡进行中
public float getDeath();
```

三个实现：
- `MCEntity`：`isDead()` → `livingEntity.isDeadOrDying()`；`get/setDeath` → `livingEntity.deathTime`。
- `StubEntity`：存储独立字段 `death`（float），避免依赖 MC 实体。
- `ActorEntity` 的 `getEntity()` 返回 `MCEntity`，直接复用。

### 4. 回放 `apply()`

在 `apply()`（`ReplayKeyframes.java:241`）中写入死亡状态：

```java
float d = this.death.interpolate(tick).floatValue();
entity.setDeath(d);
// 死亡时维持红闪（供 form 预览 overlay 读取）
if (d > 0) entity.setHurtTimer(Math.max(entity.getHurtTimer(), 1));
```

### 5. 渲染驱动

- **form 预览红闪**：`BaseFilmController.java:160` 改为同时看死亡：

  ```java
  int overlay = OverlayTexture.pack(
      OverlayTexture.u(0F),
      OverlayTexture.v(entity.getHurtTimer() > 0 || entity.isDead())
  );
  ```

- **actor 倒下+红闪**：`ActorEntityRenderer` 已是 MC tick 驱动。要让 keyframes 驱动，`applyActorReplay`（`BaseFilmController.java:1100-1121`）需要在 apply 后把死亡同步到 `ActorEntity`：

  由于 `applyActorReplay` 只 apply `GROUP_POSITION`，需扩展现有 apply 逻辑——把死亡值应用到 `ActorEntity`（设置 `actor.deathTime` 与 `dead` 标记），从而 `ActorEntityRenderer` 的 `deathTime` 旋转 + `getOverlayCoords` 红闪自动生效。

  > **决定**：为控制改动面，`applyActorReplay` 将额外 apply `death`（仅死亡组），在 `GROUP_POSITION` 列表外补 apply `List.of("death")`，然后由 `IEntity.setDeath` 落到 `ActorEntity.deathTime`。这样复用现有 `ActorEntityRenderer` 的死亡动画逻辑。

### 6. 编辑器 UI 显示

`death` 通道加入 `CURATED_CHANNELS` 后，UI 轨道列表自动显示（与既有通道一致）。

## 兼容性

- 无 `death` 关键帧时，`interpolate` 返回通道默认值（0），行为与现状完全一致。
- 新增接口方法均为新增（不破坏既有实现）。
- `copyOver`/`shift`/`getChannels` 通过 `getAll()` 自动覆盖新通道，无需额外改动。

## 测试

- 裸 JVM 测试：验证 `ReplayKeyframes` 上新增 `death` 通道的 record/apply 往返（StubEntity 承载）。
- 若有现有 `ReplayKeyframes` 相关测试，补充 `death` 用例。
- 运行时冒烟（人工）：录制一段玩家击杀，确认 death 通道被记录、回放时变红+倒下。

## 主要改动文件

- `new/src/main/java/mchorse/bbs_mod/film/replays/ReplayKeyframes.java`
- `new/src/main/java/mchorse/bbs_mod/forms/entities/IEntity.java`
- `new/src/main/java/mchorse/bbs_mod/forms/entities/MCEntity.java`
- `new/src/main/java/mchorse/bbs_mod/forms/entities/StubEntity.java`
- `new/src/client/java/mchorse/bbs_mod/film/BaseFilmController.java`
