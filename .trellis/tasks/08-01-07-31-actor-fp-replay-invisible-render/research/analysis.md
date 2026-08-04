# Analysis: actor + fp replay issues (DEFERRED - fix in a future session)

Date: 2026-08-01 (analysis complete; fix deferred per user decision)

## Decision

All actor + fp replay changes were REVERTED. The problems below are recorded for
a future session. Only the morph-menu 3D preview frame-rate fix (UIScreen paused
transition) was kept from this task's work.

## Problem 1: actor + fp replay renders invisible in the film editor

- `BaseFilmController.renderEntity` sets `gizmoOnly(true)` when `replay.actor` is
  on (skips the editor form render, expecting the world ActorEntity to replace it).
- For a first-person replay (`replay.fp`), the server registers the PLAYER (or
  nothing) as the actor - NOT an ActorEntity - so nothing replaces the editor
  form -> invisible. The gizmo still works (it samples the editor StubEntity).
- Candidate fix (not applied): `gizmoOnly(replay.actor.get() && !replay.fp.get())`.

## Problem 2: fp + external recording playback stuck between tick 1-2

- fp replay actor = server player (mapped only when the first-person lease is
  held). Position is server-driven via `ActionPlayer.applySafely`.
- Open question: whether the recording tick alignment (recorder starts at the
  film cursor; `copyOver(recorder.keyframes, 0)` leaves a gap at the playback
  start) explains the stuck playback. Needs in-game confirmation.

## Problem 3: actor-off + external recording playback fails

- `tryUpdateReplayEntities`: `isActor = replay.actor.get() || replay.fp.get()` -
  with both off, no server actor is spawned; the editor entity renders from
  keyframes. Needs in-game confirmation whether the recorded absolute positions
  line up with the film camera.

## Problem 4: actor-on + recording playback loses body/head rotation + flickers

- Reference (bbs-fs-1.21.1) syncs ONLY the actor's angles
  (`setYaw/setHeadYaw/setBodyYaw/setPitch` from keyframes) + client actions.
- The workspace `applyActorReplay` (xue snow 2026-06-06) added
  `actorEntity.update()` + `replay.keyframes.apply(ticks, actorEntity,
  [GROUP_POSITION])` (rotation + equipment + extras) + a paused prev-sync block.
- The actor is a real world entity: its vanilla tick owns physics/motion-derived
  rotation and the server drives position via applySafely. Applying the whole
  keyframe set on the client re-drives state the vanilla tick already owns ->
  recorded playback jitters and drops the recorded rotation.
- Candidate fix (not applied): reference-parity angle sync + keep equipment
  application, drop the keyframes.apply machinery.

## What was kept (frame-rate fix, verified)

- `UIScreen.resolveTransition`: when the game is paused, derive an advancing
  partial tick from wall clock (`(now - lastTickMillis) / 50`, clamped to 1) so
  UI preview animations (form editor 3D preview) interpolate smoothly. The
  morphing panel pauses the game (`canPause() = true`) while the film editor
  does not (`UIFilmPanel.canPause() = false`), which is why the morph menu
  preview stepped at 20 TPS.

## Files (workspace new/)

- Reverted: `film/BaseFilmController.java` (actor+fp changes).
- Kept: `ui/framework/UIScreen.java` + `PoseFormRegressionSourceTest` paused check.

## Addendum (2026-08-01, NBT 兼容性审查中发现的 actor+fp 相关渲染问题)

来源：NBT 全面审查（代理 B 实体 NBT 路径审计 + 主会话验证），与 actor+fp 渲染直接相关，未修复（与任务决策一致：延期）。

### A. `MobFormRenderer.synchronizeEntity` 无条件覆盖 NBT 装备

- `src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java:909-917`
- 每 tick 无条件用 `source.getEquipmentStack(slot)` 覆盖渲染实体全部 6 个装备槽。
- 后果：手填的 MobForm NBT 装备（含附魔）在编辑器 UI 预览可见，但作为 morph/回放 actor 挂到 source 上时被清空（回放 actor 客户端实例装备 map 恒空，见 B）。
- 候选修复：仅当 source 槽非空时覆盖（`stack.isEmpty()` 时保留 NBT 加载的槽位值），或加开关（如 `MobForm.ignoreEquipment`）。

### B. `ActorEntity` 装备客户端不同步

- `src/main/java/mchorse/bbs_mod/entity/ActorEntity.java:380-390`
- `setItemSlot`/`getItemBySlot` 走内存 map，绕过 vanilla `SynchedEntityData` 装备同步；`defineSynchedData` 只声明 `PLAYBACK_WORLD_ISOLATED`。
- 全仓无 `ClientboundSetEquipmentPacket`；装备通过 `CLIENT_ENTITY_FORM` 包以 form 形式发给客户端（`ServerNetwork.java:2832/2840`），渲染依赖 form 路径（`ActorEntityRenderer` → `MorphRenderer.renderForm(form)`）。
- 数据层附魔保留完好（`ItemStackKeyframeFactory` registry-aware 已修），断链在渲染同步层。
- 对照：影片编辑器预览用 `StubEntity`（`BaseFilmController.createEntities` 走 `replay.keyframes.apply`），装备能显示——说明意图是装备应渲染，世界 actor 路径缺同步。
- 服务端装备应用受授权门控：`ActionPlayer.shouldApplyServerActorEquipment` = `!isolated && (!actor || authorized)`（`ActionPlayer.java:716-723`）。
- 候选修复：对 ActorEntity 走 vanilla `setItemSlot`（不重写 map）以启用 entity-data 同步，或发自定义装备包给目标观众。

### C. `FilmActorTimeline` 只携带 FormProperties

- `src/client/java/mchorse/bbs_mod/film/FilmActorTimeline.java`
- `apply` 只应用 `context.timeline(properties, tick, playing)`，不应用装备；actor 装备渲染完全依赖 form 路径（B）。
