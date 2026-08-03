# 追踪器剪辑追踪演员模式回放在运动时跟踪不同步（记录分析，不修复）

## Goal

摄像机编辑器（影片面板摄像机剪辑）中，TrackerClip 追踪一个开启了演员模式（`replay.actor`）的回放时，回放运动期间追踪器与演员跟踪不同步、有明显延迟。**本轮只把分析结论记录进 trellis，不实施修复。**

## Confirmed Facts（分析结论）

### 关键代码路径

- 世界回放：`FirstPersonFilmController`（`WorldFilmController`）+ `PlayCameraController`；编辑器回放：`FilmEditorController`（每 tick 用 `cursor + 1` 前瞻一拍应用 keyframes）+ `RunnerCameraController`。
- 追踪器采样：`TrackerClientClip.applyClip` → `TrackerFrame.resolve`，采样**编辑器 StubEntity**（`cameraClipContext.entities`），位置用 `Lerps.lerp(prev, current, transition)`，骨骼矩阵用 `collectMatrices(entity, transition)`。
- 演员模式可见实体：世界 `ActorEntity`（`BaseFilmController.renderEntity` 对 actor 回放走 gizmo-only，可见的是世界实体）；其客户端位置被 `BaseFilmController.applyActorReplay`（`replay.keyframes.apply` GROUP_POSITION）每 tick 覆盖。

### 根因候选（三个可验证的结构性问题）

**A. 摄像机时间基准比被追踪对象领先 1 tick**
- 每 tick：控制器 `tick += 1`（`WorldFilmController.java:67`），实体随之落在 `kf(C+1)/prev kf(C)`；每帧渲染：摄像机剪辑在 `ticks=C+1, transition=p` 评估（`WorldFilmController.render`），路径位置 = `C+1+p`（`KeyframeClip.applyClip` 用 `relativeTick + transition`）。
- 实体/追踪器采样处于 `lerp(kf(C), kf(C+1), p) ≈ C+p`。
- 后果：纯位置追踪（非 relative）恰好同步；**relative 模式**（`frame.relative` 的路径行程分量）与编辑器反向求解（`UITrackerClip` 的 `getUnderneath()`）时，路径行程比被追踪骨骼超前一个 tick 的路径速度 → 运动时出现与速度成正比的位置偏差。

**B. 演员位置多写者竞争**
- 客户端 ActorEntity 每 tick 被三方写入：服务端 `ActionPlayer.applySafely`（`move + setPos`，网络 move 包 → vanilla `lerpSteps` 3-tick 插值）、vanilla `baseTick`（`xOld = x` 快照 + lerp 移动）、客户端 `applyActorReplay`（keyframes 重新断言）。
- 只要客户端每 tick 都重新断言，渲染是干净的；但任何一次断言失败（actor 退役/重建后 `actors` 映射过期指向旧实体 id、暂停、服务端与客户端影片 tick 相位差）都会让可见演员掉到网络滞后位置，而追踪器采样的编辑器实体保持干净 → **运动时演员明显落后于追踪器**。
- 与既有分析一致：`08-01-07-31-actor-fp-replay-invisible-render/research/analysis.md` Problem 4（`applyActorReplay` 与 vanilla tick / 服务端驱动冲突）。

**C. 骨骼采样源差异**
- 追踪器采样编辑器 `StubEntity` 的骨骼（`TrackerFrame.java:77`），用户看到的是世界 `ActorEntity`（`BaseFilmController.java:1786` gizmo-only）。两者形态状态同频不同源（一个走影片 tick 的 `form.update`，一个走 vanilla tick 的 `ActorEntity.tickActorVisualState`）→ 运动时（走路摆动等）姿态相位存在细微差，追踪点与可见模型骨骼不完全重合。

### 建议的验证步骤（区分 A/B/C 主导）

1. 摄像机编辑器里播放"纯追踪（不勾 relative）+ 快速直线运动"的演员回放：仍延迟 → B/C 主导；同步 → 问题集中在 relative/路径叠加（A）。
2. 服务端断开（单机播放）对比：B 的网络因素应消失。
3. 在 `applyActorReplay` 每 tick 记录 `actor.x` 与 `editorEntity.x` 差值。

## Requirements

- 无（记录型任务，本轮不修复）。

## Acceptance Criteria

- [x] 分析结论已完整记录（本 prd + `research/analysis.md`），含代码引用与根因候选。
- [x] 与既有延期任务 `08-01-07-31-actor-fp-replay-invisible-render` 的关联已注明。
