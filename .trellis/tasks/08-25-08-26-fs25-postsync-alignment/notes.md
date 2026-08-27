# Verification Notes

## Correct Target

- The implementation target is `new-fs25-sync/`, the current FSR worktree that
  corresponds to the latest FS migration. `new/` was not used for this result.

## Completed

- The target already contained the first pass of the migrated translucent queue,
  isolated vanilla particle preview, and body-attachment selection logic, but the
  2.5 follow-up still had three regressions: colour-alpha fades used the wrong
  pass partition, vanilla preview particles inherited stale shader/light state,
  and the body-part editor remained in a fixed middle split. The camera button's
  right-click menu also incorrectly reused the six-mode picker.
- Migrated settings keys to `camera`, `viewport`, `timeline`, `workspace`, and
  `misc` across the language resources, preserving existing translations and
  adding the three missing 2.5 setting labels/tooltips in every locale. Added a
  compatibility migration for the brief workspace registration of the preview,
  scrub, and restart options.
- Removed duplicate JSON keys introduced by overlapping old/new resource entries.
- Updated the source regression to assert texture-alpha pass ordering, particle
  render-state restoration, bottom-anchored attachment settings, and the
  three-action-only camera context menu.
- All 19 language JSON files parse strictly and contain no duplicate keys.
- `compileJava compileClientJava` passed.
- `testFilmEditorMigration`, `testParticleRuntimeConsistency`, and
  `testPlatformResourceConsistency` passed.
- Full `check` passed: 77 actionable tasks completed successfully.
- `assemble` passed and produced `new-fs25-sync/build/libs/BBS_FSR_beta_v0.0.11.jar`.
- `mod_version=0.0.11` remains unchanged.
- Commit created in `new-fs25-sync/`: `f0aa738d fix(settings): restore categorized translations`.

## Manual Visual Checks Still Recommended

- Color Pose alpha with Iris disabled and with an active shader pack.
- `bbs:vanilla_particles` block/item material preview and no world residue after
  switching or closing the editor.
- Five settings categories in English and Chinese with an old settings file.
- Repeated body attachment adds and the three-entry camera context menu; verify
  the six-mode picker remains available from its separate left-click path.
