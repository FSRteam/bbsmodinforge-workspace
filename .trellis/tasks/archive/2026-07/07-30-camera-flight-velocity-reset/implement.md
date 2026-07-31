# 飞行模式速度残留 - 实现计划

## 步骤

### Step 1: OrbitFilmCameraController.java - 新增 resetVelocity()
- 文件: `new/src/client/java/mchorse/bbs_mod/ui/film/controller/OrbitFilmCameraController.java`
- 在 `reset()` 方法附近新增方法
```java
public void resetVelocity() {
    this.velocityPosition.set(0, 0, 0);
}
```

### Step 2: UIFilmPanel.java - 调用 resetVelocity()
- 文件: `new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`
- `setFlight(false)` 分支中第 3149-3170 行范围，在 `this.dashboard.orbitUI.setControl(flight)` 之前添加：
```java
if (!flight) {
    this.controller.orbit.resetVelocity();  // 新增：退出飞行时清零速度
    this.persistFlightFov();
    // ... 原有代码
}
```

### 验证命令
```bash
cd new && ./gradlew compileClientJava
# 手动测试：开飞行→WASD→关飞行→开飞行→检查摄像机是否静止
```
