# Fix shader-dependent world form drifting after migration

## Goal

Diagnose and fix the migration regression where forms/entities drift with camera differently with and without shaders in the NeoForge 1.21.1 new repository, using docs, old/new diff, and external research. Do not commit until user confirmation.

## Requirements

- Work only against the `new` NeoForge 1.21.1 repository for source changes.
- Use `old` Fabric 1.20.4 only as migration reference.
- Read all relevant material under `docs/`, including the shader files supplied by the user.
- Use external research for NeoForge 1.21.1, Minecraft rendering matrix changes, and Iris/shader render pipeline behavior.
- Diagnose the shader-dependent camera-drift regression:
  - Without shaders: all rendered creatures/forms appear to drift with the camera.
  - With shaders: ordinary creatures/forms stop drifting, but film replay targets drift with the camera.
- Preserve behavior across both shader-disabled and shader-enabled paths.
- Do not create a local Git commit until the user explicitly confirms after validation.
- If a commit is later requested, commit only `new` repository content.

## Acceptance Criteria

- [ ] `docs/` contents are inventoried and relevant shader/material notes are reviewed.
- [ ] `old` and `new` rendering pipeline differences are compared around world render, form render, film render, matrix stack, model-view, and buffer flush behavior.
- [ ] External sources are checked for the relevant 1.20.4 -> 1.21.1 / NeoForge / Iris rendering behavior.
- [ ] Root cause is documented before code changes.
- [ ] The fix is confined to `new` source files unless a Trellis spec update is warranted.
- [ ] `new` compiles after the change.
- [ ] Final status reports changed files, verification, remaining untracked files, and that no commit was created without user confirmation.

## Notes

- Existing local untracked output in `new` such as `.claude/` and `runs/` is outside the task unless investigation proves otherwise.
- Existing local untracked material in `old` such as `src/client/java/snowstorm-master/` is reference-only and must not be committed.
