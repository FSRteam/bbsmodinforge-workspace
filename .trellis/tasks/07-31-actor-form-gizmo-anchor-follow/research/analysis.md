# Analysis: Actor-mode film replays fail to place transform/pose gizmos at limb anchors

Date: 2026-07-31 (main-session analysis; sub-agent dispatch failed with a platform `reasoning_text` API error)

## What was established

- Film gizmos sample through the shared placement API: `UIReplaysEditorUtils.buildFilmGizmoDrag` / `sampleFilmBoneMatrix` -> `BaseFilmController.getBoneCompositeMatrix` -> `sampleBonePlacement` (workspace new/). Both the pose-bone gizmo and the transform gizmo derive from the editor entity (`UIFilmController.getCurrentEntity()` -> the StubEntity in `BaseFilmController.entities`).
- The editor entity is updated every tick by `applyReplay` regardless of the actor toggle (`BaseFilmController.updateEntities`), so the gizmo source data itself is present when `replay.actor` is on.
- With `replay.actor` on, the rendered actor switches from the editor entity to the world `ActorEntity` (`BaseFilmController.renderEntity` skips the editor render; `ActorEntityRenderer` -> `MorphRenderer.renderForm`), and the actor is only fed `GROUP_POSITION` per frame (`applyActorReplay`).

## Open question (needs repro detail)

The exact failure mechanism is not yet pinned down. Candidates that all share the "actor on" toggle:

1. The world actor's form copy and the editor entity's form copy diverge (different anchor/limb matrices), while the gizmo keeps sampling the editor entity.
2. The actor's equipment is empty for unprivileged playback, changing the bone hierarchy between what is drawn and what the gizmo samples.
3. The pose/transform anchors are resolved against a different Form instance when the actor mode changes the rendering path.

Before implementing, confirm: does the gizmo appear at the wrong position, or not appear at all; and is the repro in the Film editor while playing, or in the dashboard/in-game playback?

Evidence files (workspace new/): film/BaseFilmController.java, ui/film/replays/UIReplaysEditorUtils.java, ui/film/replays/FilmBoneWorldProvider.java, ui/film/controller/UIFilmController.java, client/renderer/entity/ActorEntityRenderer.java, film/FilmActorTimeline.java.
