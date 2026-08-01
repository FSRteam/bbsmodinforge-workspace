# Implementation plan: sync FS 5848363c to FSR

## 1. Freeze boundaries

- [x] Revalidate all source tips, target status, ancestry, and version locks.
- [x] Create `codex/sync-fs-5848363-to-fsr` at `0544eada` in a dedicated worktree.
- [x] Record the branch, worktree path, source SHAs, and `fsr_initial_commit` in Trellis.
- [x] Produce the complete upstream commit disposition matrix.

## 2. Standalone fixes

- [x] Reset `bone2/local2` in `FilmControllerContext` and add regression coverage.
- [x] Insert unknown `ValueOrder` tokens at their declared/default position.
- [x] Capture complete replay ground probes and sprint state for vanilla steps/particles.
- [x] Compile and run the focused tests before committing this cluster.

## 3. Core transform and IK

- [x] Port quaternion transform storage/evaluation and compatible serialization.
- [x] Port `TransformSpace`, drag-strategy split, numeric input, gizmo and editor wiring.
- [x] Port DLS IK tree, multi-target solving, per-bone DoF, classic limb mode,
      stretch/orient integration and configuration/UI changes.
- [x] Port weld parent share, twist, angle behavior, seam diagnostics and render wiring.
- [x] Compile and run transform/IK/model regression tests after each subcluster.

## 4. Editor and film features

- [x] Port bone tree, search popup, pickers and stencil eyedropper while preserving
      FSR selection/ownership behavior.
- [x] Port orbit navigation sphere, orthographic preview and tracker flight/orbit editing.
- [x] Port frozen last film frame, keyframe hover preview and section rebuild state.
- [x] Consolidate Dock implementation and support docking to the full editor edge.
- [x] Compile and run UI/film/replay tests after each subcluster.

## 5. Render cache and integration

- [x] Port `FormFrameCache` and dependent renderer changes without weakening
      `FormTranslucentQueue`.
- [x] Merge only required settings, icons and localization; retain FSR theme behavior.
- [x] Complete the per-commit disposition matrix and verify excluded commits.

## 6. Final verification and commits

- [x] Run focused migration tests and `compileJava compileClientJava`.
- [x] Run `check`, `build`, and `git diff --check` serially.
- [x] Verify version/artifact, particle, translucency, branch and ancestry invariants.
- [x] Review changed files and commit only migration-owned work in coherent batches.
- [x] Update the migration baseline and Trellis task with the real terminal SHA.
- [x] Confirm the original `new` worktree has the same pre-existing untracked files.

## Risky files and rollback points

- `UIPropTransform`, `Gizmo`, `BaseFilmController`, `UIReplaysEditor`,
  `ModelFormRenderer`, `UIModelIKFormPanel`, `UIDockLayout` and localization assets
  are shared hotspots. Inspect FSR-only code before every replacement.
- A cluster that fails compile or focused tests is reverted as a unit before the next
  cluster starts; no version bump is used to hide an incomplete migration.
