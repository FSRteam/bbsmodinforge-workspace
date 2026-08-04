# Research: Camera Flight Velocity Reset

- **Query**: 飞行模式速度残留 -- 退出飞行模式后 velocityPosition 未清零，再次进入时摄像机自动沿旧方向飞行
- **Scope**: internal
- **Date**: 2026-07-30

## Findings

### Key Files

| File Path | Description |
|---|---|
| `new\src\client\java\mchorse\bbs_mod\ui\film\controller\OrbitFilmCameraController.java` | 核心飞行速度逻辑：velocityPosition 定义、keyPressed 更新、update 应用 |
| `new\src\client\java\mchorse\bbs_mod\ui\film\UIFilmPanel.java` | setFlight() / toggleFlight() / isFlying() 飞行开关方法 |
| `new\src\client\java\mchorse\bbs_mod\ui\film\controller\UIFilmController.java` | 轨道控制器管理，orbit 实例持有 |
| `new\src\main\java\mchorse\bbs_mod\BBSSettings.java` | editorOrbitMovementRequiresFlight 设置定义与默认值 |

### Confirmed Findings

#### 1. `setFlight(false)` 不清零 `velocityPosition`

`UIFilmPanel.setFlight(boolean flight)` (line 3142-3171): 当 `flight = false` 时，仅执行 `persistFlightFov()`、标记 undo、调用 `runner.setManual(null)` 和 `dashboard.orbitUI.setControl(false)`。**没有任何代码清理 `this.controller.orbit.velocityPosition`**。

```java
public void setFlight(boolean flight)
{
    if (flight) { this.controller.stopGizmoInteraction(); }

    if (!this.isRunning() || !flight)
    {
        if (!flight)
        {
            this.persistFlightFov();
            // ... mark undo ...
        }
        else { this.lastPosition.set(Position.ZERO); }

        this.runner.setManual(flight ? this.position : null);
        this.dashboard.orbitUI.setControl(flight);  // <--- 仅开关 orbitUI，不清 velocity
    }
}
```

#### 2. `editorOrbitMovementRequiresFlight` 默认值为 `true`

`BBSSettings.java` line 842:
```java
editorOrbitMovementRequiresFlight = builder.getBoolean("orbit_movement_requires_flight", true);
```

#### 3. Bug 触发路径（默认设置下）

1. 进入飞行模式 → `setFlight(true)` → `velocityPosition` 保持为零（初始状态）
2. 按 WASD → `keyPressed()` 因 `isFlying() == true` 通过守卫检查 → `getFactor()` 设置非零值到 `velocityPosition`
3. 退出飞行模式 → `setFlight(false)` → **velocityPosition 未清零**，仍为 `(x, y, z)` 非零值
4. 再次进入飞行模式 → `setFlight(true)` → `velocityPosition` 仍非零
5. `update()` 运行 → 此时 `isFlying() == true`，所以条件 `!this.controller.panel.isFlying()` 为 false → **velocityPosition 未被清零**
6. `velocityPosition.lengthSquared() > 0` → 直接使用旧速度移动摄像机 → 自动飞行
7. 用户必须再按一次 WASD 触发 `keyPressed()` → `getFactor()` 检测到按键释放才将对应分量归零

### 守卫检查的详细逻辑

`OrbitFilmCameraController.keyPressed()` (line 169-193):
```java
if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
{
    return false;  // 未飞行时阻止键盘更新速度
}
```
当设置开启(默认)，未飞行时不处理 WASD 输入，velocityPosition 保持之前值。

`OrbitFilmCameraController.update()` (line 258-289):
```java
if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
{
    this.velocityPosition.set(0, 0, 0);  // 未飞行时清零速度
    return false;
}
```
此清零仅在设置开启时执行，且要求 `!isFlying() == true`。一次进入飞行模式后，此清零路径不再进入。

### `getFactor()` 归零逻辑

`getFactor()` (line 196-212) 仅在以下情况将速度分量设为 0：
- 正方向按键被释放(`isReleased`) **且** 当前值 `> 0` → 返回 0
- 负方向按键被释放(`isReleased`) **且** 当前值 `< 0` → 返回 0
- 否则返回当前值不变

由于退出/重入飞行模式时没有按键事件触发，`getFactor()` 返回原值，velocityPosition 保留。

### Related Specs

- `.trellis/spec/new/ui/film/controller.md` — controller 层规范（如存在）

## Fix Options Analysis

| Option | Location | Approach | Pros | Cons |
|---|---|---|---|---|
| A | `UIFilmPanel.setFlight(false)` | 在 `if (!flight)` 块中调用 `this.controller.orbit.resetVelocity()` 或直接清零 `velocityPosition` | 最接近根因；飞行结束时清空飞行状态 | 需在 `OrbitFilmCameraController` 中新增 public 方法暴露重置 |
| B | `UIFilmPanel.setFlight(true)` | 在 `if (flight)` 块中开头清零 | 进入飞行时保证干净状态 | 不解决退出时残留的概念问题 |
| C | `OrbitFilmCameraController.update()` | 将条件改为 `if (!this.controller.panel.isFlying())` 无条件清零 | 代码改动最小 | 破坏 `editorOrbitMovementRequiresFlight = false` 的预期行为（该模式下用户希望在非飞行时也用 WASD 移动视角） |

**推荐：Option A** — 在 `setFlight(false)` 中添加 velocity 重置。具体：
1. 在 `OrbitFilmCameraController` 中添加 `public void resetVelocity() { this.velocityPosition.set(0, 0, 0); }`
2. 在 `UIFilmPanel.setFlight(false)` 的 `if (!flight)` 分支中调用 `this.controller.orbit.resetVelocity()`

## Caveats / Not Found

- `reset()` 方法（line 519-533）虽然会清零 `velocityPosition`，但 `reset()` 也会重置 pivot、rotation、distance 等全部轨道参数，不适合仅清除速度的场景。
- `velocityPosition` 声明为 `protected`（line 73），`UIFilmPanel` 在 `mchorse.bbs_mod.ui.film` 包中，而 `OrbitFilmCameraController` 在 `mchorse.bbs_mod.ui.film.controller` 包中——不同包无法直接访问 `protected` 字段，需通过 public 方法桥接。
- 当前 bug 与 `editorOrbitMovementRequiresFlight` 设置无关：默认值 `true` 下同样会发生，因为飞行退出/重入过程绕过了 update() 的清零守卫。
