# 摄像机编辑器飞行模式速度残留

## Goal

关闭飞行模式时清空 WASD 方向缓存；再次开启飞行时摄像机静止，不再沿上次方向自动飞行。

## Root Cause

按键事件仅在飞行开启时转发到轨道控制器。持握 WASD 时关闭飞行，release 事件不会到达 `getFactor()`，`velocityPosition` 保持锁存，下次开启飞行时立即恢复漂移。

## Fix

关闭飞行时主动清空速度：
- `UIFilmPanel.setFlight(false)` → `controller.orbit.resetVelocity()`
- `OrbitFilmCameraController.resetVelocity()` 新增
- `UIOrbitCamera.setControl/setEnabled(false)` → `orbit.reset()`

## Acceptance Criteria

- [x] 持握 WASD 关闭飞行后，再次开启飞行摄像机静止
- [x] 松开 WASD 后正常控制

## Commit

- `dbd65905` fix(camera): clear latched flight velocity when flight turns off
