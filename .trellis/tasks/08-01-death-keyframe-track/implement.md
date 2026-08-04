# 死亡关键帧轨道 — 实施计划

## 前置
- 任务已进入 planning，等待 `task.py start`（需 review 通过）。
- 相关代码位置已确认（见 design.md 主要改动文件）。

## 实施顺序

### Step 1：`IEntity` 接口扩展
- `IEntity.java` 新增 `isDead()`、`getDeath()`、`setDeath(float)` 三个方法（default 或抽象）。

### Step 2：`ReplayKeyframes` 新增 `death` 通道
- 新增字段 `public final KeyframeChannel<Double> death = new KeyframeChannel<>("death", KeyframeFactories.DOUBLE);`
- 加入构造器 `this.add(this.death)`。
- 加入 `CURATED_CHANNELS`。
- `record()`：`this.death.insert(tick, entity.isDead() ? 1D : 0D);`
- `apply()`：从通道读值写回实体（`entity.setDeath(...)`，死亡时顺带 `setHurtTimer` 保持红闪）。

### Step 3：实体实现
- `MCEntity`：`isDead()` → 取 mcEntity 死亡态；`getDeath/setDeath` → `deathTime`。
- `StubEntity`：新增 `death` 字段 + 接口实现。

### Step 4：渲染驱动
- `BaseFilmController.java:160` overlay 计算加入 `entity.isDead()`。
- `applyActorReplay`（`BaseFilmController.java:1100`）补 apply `death` 到 `ActorEntity`。

### Step 5：编译与测试
- `new/` 编译：`./gradlew compileJava compileClientJava`（或项目既定命令）。
- 裸 JVM 测试：补充/验证 `ReplayKeyframes` death 往返。

## 验证命令
- 编译命令参考 `new/` 既有构建（见 `build.gradle` / 历史任务）。先跑编译。
- 运行既有回放相关单元测试（`test`/`replay` 相关 source set）。

## 验收复核清单（评审用）
- death 通道是否在 `CURATED_CHANNELS` 且可录制。
- 无 death 关键帧时行为不变（backward compatible）。
- overlay 红闪在 form 预览与 actor 路径都对。

## 回滚点
- 每步均为增量改动，未引入新依赖，可逐步回退。
- 关键回滚：`IEntity` 接口新增方法若影响编译，先回退到只加 default 方法。
