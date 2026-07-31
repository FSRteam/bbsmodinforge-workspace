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
