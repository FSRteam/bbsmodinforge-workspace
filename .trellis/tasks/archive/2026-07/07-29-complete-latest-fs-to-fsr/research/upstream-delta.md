# Upstream delta disposition

## Baselines

- FS master: `116ed3973163a09439c21f027b3d4307b8d893a9`
- FS 1.21.1 reference: `31bd255e528cd620218ced82bef5ec7f9eab6b32`
- FS 1.20.1 reference: `c3d1a47732ea85b804a249460c183bbf7a08190e`
- Audited merge base: `1ab0a887bada3d6a4749314cbb009d9c90e260eb`
- FSR before/after: `e11ca91b` -> `4143499b`
- Local nightly tag: `nightly-20260729-131928-r8-a1-4143499`

## Migrated

- Model editor creation flow, unified lists, duplicate/preset actions, selection banner,
  and bevel removal.
- Gizmo/background/scroll defaults, scale target cycling, uniform-scale drag safety,
  text cursor behavior, stale keyframe protection, and texture undo safety.
- Pose/PoseTransform recording at the active tick.
- Framebuffer state restoration, Iris relative replay matrices, Mob Form RenderType hook
  timing, and translucent queue ownership.
- Iris/Sodium development runtime dependencies, MatrixUtils verification, and NeoForge
  metadata parity.

## Excluded

- `origin/2.5` and the FS particle system: outside the approved migration scope.
- FS `31884112` translucency rollback: FSR keeps its stronger translucent queue.
- Existing FSR theme, plugin, glint, audio, armor, and flight-restore worktree changes.
- Two `UIPoseKeyframeFactory` glint layout hunks found by independent review were removed
  from the migration commit and left uncommitted in the original worktree.

## Verification

- Focused compile and migration tests passed.
- Clean `4143499b` worktree: `check -x testNetworkSecurity` and
  `build -x testNetworkSecurity` passed serially.
- Full `check` stops at the pre-existing `testNetworkSecurity` source-boundary assertion;
  network source and test paths are identical between `e11ca91b` and `4143499b`.
- `git diff --check e11ca91b..4143499b` passed; particle paths and FSR identity are unchanged.
- Manual runtime checks remain pending as listed in `implement.md`.
