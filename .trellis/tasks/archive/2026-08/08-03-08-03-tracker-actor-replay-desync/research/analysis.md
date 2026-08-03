# Analysis: tracker desync during actor-mode replay playback (recorded, not fixed)

Date: 2026-08-03. Record-only task — no fix in this round.

## Symptom

摄像机编辑器中使用 TrackerClip 追踪开启了演员模式（`replay.actor`）的回放时，回放运动期间追踪器跟踪不同步、有延迟。

## Verified code path

### Playback controllers

| 场景 | 实体更新 | 摄像机评估 |
|---|---|---|
| 世界回放 | `FirstPersonFilmController`（继承 `WorldFilmController.update`：每 tick `tick += 1` 后 `updateEntities(C+1)`） | `PlayCameraController.setup` 每帧 `apply(camera, ticks, transition)` |
| 摄像机编辑器 | `FilmEditorController.updateEntities`：`ticks = getTick() + (isRunning ? 1 : 0)`（`FilmEditorController.java:50-57`） | `RunnerCameraController.setup` 每帧 `apply(camera, ticks, playing ? transition : 0F)` |

两条路径同一结构：**实体在影片 tick `C+1` 处被 keyframes 应用（`kf(C+1)`，prev = `kf(C)`），摄像机剪辑在 `ticks=C+1, transition=p` 评估**。

### Tracker sampling

- `TrackerClientClip.applyClip`（`camera/clips/misc/TrackerClientClip.java:41-97`）：
  - `getEntities(context)` 从 `cameraClipContext.entities` 取**编辑器 StubEntity**（`EntityClip.java:41-54`；编辑器经 `UIFilmController.createEntities` 共享同一批实体，`UIFilmController.java:446-450`）。
  - `TrackerFrame.resolve(..., context.transition)`（`TrackerFrame.java:68-95`）：`getMatrixForRenderWithRotation(entity, ..., transition)` 用 `Lerps.lerp(prev, current, transition)`（`BaseFilmController.java:671-685`），骨骼用 `collectMatrices(entity, transition)`。
- 实体采样时间 ≈ `lerp(kf(C), kf(C+1), p) ≈ C+p`；摄像机路径（`KeyframeClip.applyClip` 的 `relativeTick + transition`，`KeyframeClip.java:67`）= `C+1+p`。

### Actor mode visible entity

- `BaseFilmController.renderEntity` 对 `replay.actor` 走 `gizmoOnly(true)`（`BaseFilmController.java:1786-1795`）——可见的是世界 `ActorEntity`，不是编辑器实体。
- 客户端每 tick：`applyActorReplay`（`BaseFilmController.java:1177-1198`）→ `MCEntity.update()` + `replay.keyframes.apply(ticks, actorEntity, [GROUP_POSITION])`（重断言位置与 prev）。
- 服务端同 tick：`ActionPlayer.applySafely`（`actions/ActionPlayer.java:620-719`）`move(MoverType.SELF, ...)` + `setPos`（vanilla `setPos` 会把 `xOld = x`）。
- 客户端 vanilla tick：`ActorEntity.tick` → `super.tick()`（`baseTick`：`xOld = x` 快照 + `lerpSteps` 网络插值移动，`ActorEntity.java:433-470`）+ `tickActorVisualState`（`form.update`）。

## Root causes (candidates, all verifiable in code)

### A. Camera time base leads the tracked subject by exactly one tick

- Entities interpolate at `C+p`; camera clips evaluate at `C+1+p` — a constant 1-tick offset between the camera's time base and the entity's time base.
- Pure position tracking (relative off) is exactly synced because the tracker replaces the camera position with the entity sample.
- **Relative mode** (`frame.relative`, `TrackerFrame.java:117-133`) and the editor's inverse solving (`UITrackerClip` uses `getUnderneath()`) measure the path travel against the entity at different time bases → speed-proportional deviation while the path/actor moves.

### B. Multi-writer race on the client actor position

- Writers per tick: server `ActionPlayer` (network move packets → vanilla `lerpSteps` 3-tick interpolation), vanilla `baseTick` (`xOld = x` snapshot + lerp movement), client `applyActorReplay` (keyframe reassert).
- When the client reasserts every tick, the render is clean. Any missed reassert — stale `actors` map after actor retirement/recreation (entity id changes, `ActionPlayer.tryUpdateReplayEntities` / reconciliation), pause, or server-vs-client film tick phase mismatch — leaves the visible actor at a network-lagged position while the tracker keeps sampling the clean editor entity → **visible actor trails the tracker during motion**.
- Consistent with prior analysis: `08-01-07-31-actor-fp-replay-invisible-render/research/analysis.md` Problem 4 (client `applyActorReplay` fights vanilla tick / server-driven state).

### C. Bone pose sampled from a different source than the visible model

- Tracker samples the editor `StubEntity`'s bone pose; the user sees the world `ActorEntity`. Both are updated per tick (`form.update` on film tick vs `ActorEntity.tickActorVisualState` on vanilla tick) with per-frame properties at the same `tick + delta` — same cadence, different sources → pose phase mismatch during motion (e.g., walk-cycle swing), so the tracked point does not coincide exactly with the visible model's bone.

## Suggested verification (to determine which candidate dominates)

1. In the camera editor, play an actor-mode replay moving in a straight line fast with a pure position tracker (relative off): still lagging → B/C dominate; synced → issue concentrates in relative/path stacking (A).
2. Compare with server-side disconnected (single-player) playback: network factor in B should vanish.
3. Log `actor.x` vs `editorEntity.x` per tick in `applyActorReplay` to measure the residual.

## Related

- Deferred task `08-01-07-31-actor-fp-replay-invisible-render` — same actor replay area; its `research/analysis.md` Problem 4 documents the `applyActorReplay` conflict.
- `git de10a7a4` "Fix tracker clip being one frame behind" (2025-01-25) — previously fixed a one-frame-behind tracker issue by moving property application to per-frame (`startRenderFrame`); the `ticks + transition` arithmetic itself was kept, and the one-tick time-base offset (A) is not addressed by it.
