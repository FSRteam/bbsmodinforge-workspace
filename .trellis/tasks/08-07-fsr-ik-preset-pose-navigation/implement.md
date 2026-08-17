# Implementation Plan

1. Capture the current `new/` dirty baseline and compare every affected method
   with FS `origin/1.21.1`.
2. Add lenient data equality and preset-menu selection restoration; add focused
   comparison/menu source regressions.
3. Port pose/overlay sheet preference and empty per-bone fallback into FSR's
   hierarchy-aware bone-pick path; test selection and insertion contracts.
4. Add IK rotation ownership and wire model/film transforms; adapt gesture
   refusal and dimmed visual feedback to FSR's transform/gizmo implementation.
5. Compare the FS and FSR runtime paths, remove FSR production ownership of
   persistent IK workspaces, and keep current-frame target/pole resolution plus
   FSR bone constraints. Remove the discarded undo-refresh diagnostics and
   temporary regression code.
6. Review all changed shared files against the pre-task baseline to retain FSR
   features and unrelated user changes.
7. Run `testFilmEditorMigration`, `testIkPhysicsConsistency`, `compileJava`,
   `compileClientJava`, relevant focused tests, and `git diff --check`.
8. Run the `trellis-check` quality gate and record any in-game checks that
   remain manual.

## Risky Files

- `UIReplaysEditorUtils.java`: retain FSR generalized bone hierarchy and current
  track/category behavior.
- `UIPropTransform.java` / `Gizmo.java`: retain FSR remote-input, cursor-wrap,
  quaternion, and visual behavior.
- `UIFilmPanel.java`, `BaseFilmController.java`, and `ModelFormRenderer.java`
  already contain unrelated work; avoid them unless the undo trace proves one
  is the correct ownership boundary.

## Validation

```powershell
cd new
.\gradlew.bat --no-daemon testFilmEditorMigration
.\gradlew.bat --no-daemon testIkPhysicsConsistency
.\gradlew.bat --no-daemon compileJava compileClientJava
git diff --check
```

Manual smoke: in Film Pose, move both an IK target and pole point through A -> B
-> C, undo to B, redo to C, then select B in the history panel. At every step,
confirm the control point, Gizmo, and solved limb agree; reopen each preset menu
and reset an applied preset; click bones with empty per-bone, pose, and multiple
pose-overlay tracks.
