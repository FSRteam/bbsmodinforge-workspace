# Implementation plan: sync FS 27a6866c Dock delta to FSR

## 1. Freeze boundaries

- [x] Validate source ancestry and target/version locks.
- [x] Create `codex/sync-fs-27a6866c-to-fsr` at `156d0bff` in a dedicated worktree.
- [x] Record worktree path, source SHAs, and `fsr_initial_commit` in Trellis.
- [x] Record all three upstream commit dispositions.

## 2. Regression safety and lifecycle fixes

- [x] Add focused regression assertions for detached clip/keyframe property panels,
      layout-settled callback wiring, strict undo overlays, and clip width/height calls.
- [x] Port the `UIClipsPanel` and `UIKeyframeEditor` teardown fixes without removing
      FSR deferred hierarchy protections.
- [x] Confirm the seven `0cecc8c9` clip sites are already equivalent or fix only
      remaining coordinate/size misuse.
- [x] Compile and run the focused test task.

## 3. Dock interaction and persistence

- [x] Port immutable-tree operations for swapping, hiding/restoring and minimum sizing.
- [x] Port tab dragging, hover activation, insertion caret, Shift swap and cancellation.
- [x] Port maximize/restore, bounded layout undo, panel menu and persistent lock/hidden state.
- [x] Generalize splitter completion into `onLayoutSettled` and wire film preview sizing.
- [x] Preserve FSR layout animations, per-editor binding and theme renderer.

## 4. Localization and integration

- [x] Add en/ru/zh labels and tooltips without dropping existing languages.
- [x] Add/extend focused Dock and film editor regression coverage.
- [x] Review every changed shared file against `origin/master`.

## 5. Verification and commits

- [x] Run focused tests and `compileJava compileClientJava`.
- [x] Run `check`, `build`, and `git diff --check` serially.
- [x] Verify versions, artifact name, particle/translucency exclusions and ancestry.
- [x] Commit only migration-owned files in the independent worktree.
- [x] Update Trellis migration baseline and this task with the real terminal SHA.
- [x] Commit the Trellis terminal record separately without staging unrelated root changes.

## Risky files

- `UIDockLayout`, `EditorLayoutNode`, `ValueEditorLayout`, `UIFilmPanel`,
  `UIKeyframeEditor`, `UIClipsPanel`, undo overlays and localization assets.
- Do not replace these files wholesale. Inspect FSR-only changes before each patch.
