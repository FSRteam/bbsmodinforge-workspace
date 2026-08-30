# Implementation Plan

## 1. Particle hot paths

- [x] Split emitter variable assignment from curve evaluation.
- [x] Add a render-only particle variable path that does not run `particle_update_expression`.
- [x] Replace particle and child-emitter iterator removal with stable linear compaction.
- [x] Add the zero-displacement collision fast path.
- [x] Add particle runtime regressions for stable compaction and update-expression ownership.

## 2. Morph replay hot paths

- [x] Skip controller-side Actor Gizmo preparation when edit axes are disabled.
- [x] Build one frustum per controller render and conservatively cull eligible direct replays.
- [x] Add change-only Mob equipment synchronization.
- [x] Add source/logic regressions for the culling guard and Actor/Mob fast paths.

## 3. Validation

- [x] Run `./gradlew.bat testParticleRuntimeConsistency`.
- [x] Run `./gradlew.bat testFilmEditorMigration`.
- [x] Run `./gradlew.bat compileClientJava`.
- [x] Inspect the final diff for particle ordering, render-state and export-precision regressions.
- [x] Run `./gradlew.bat build` (78 tasks, successful; strict media probe skipped because FFmpeg/FFprobe are not installed).

## Risk And Rollback Points

- Particle callbacks may append particles during update; compaction must retain those appended entries without updating them twice.
- Molang expressions may mutate variables; render sampling must restore emitter variables per particle before the single curve evaluation.
- Frustum culling must fail open whenever bounds or placement are uncertain.
- Item comparison must include stack count as well as item/components.
