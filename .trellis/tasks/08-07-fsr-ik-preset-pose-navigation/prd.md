# FSR 1.21.1 IK、预设与姿势轨道修复

## Goal

Bring four IK/preset/pose-selection interactions in `new/` (BBS FSR NeoForge
1.21.1) back in line with the FS `origin/1.21.1` reference without replacing
FSR-specific editor, renderer, or runtime behavior.

## Confirmed Facts

- The writable target is only `new/`; `temp/bbs-fs-2.3` at
  `origin/1.21.1` (`a982f1dc121185b04dbb26387f4cfa110b0a4c26`) is read-only reference.
- `new/` is already dirty with unrelated work, including shared runtime/editor
  files. Existing changes must be preserved.
- FSR already contains the selection/undo gesture pieces from FS commit
  `4c1742b5`; the remaining defect came from FSR-only persistent
  `ChainWorkspace` state interpreting restored target/pole transforms relative
  to their first binding instead of solving the restored current frame as FS
  does.
- FS preset recovery uses lenient recursive `BaseType` comparison because JSON
  round trips change numeric wrapper types.
- FS bone picking falls back from an empty per-bone sheet to a preferred pose
  sheet, and treats numbered pose overlays as overlay sheets.

## Requirements

- Undo/redo and history jumps after editing IK target or pole transforms must
  reproduce the restored frame directly from current keyframe data, without an
  IK-specific rebind, entity-preservation, or preview-refresh path.
- Bones whose rotation is owned by an enabled IK chain must refuse FK rotation
  gizmo/hotkey gestures in both the model form editor and film pose editor;
  translation, scale, and numeric FK fields remain available as in FS.
- IK, physics, and constraint preset menus must restore the matching saved
  preset after reopening, including float/double-equivalent data. Reset must
  apply an empty value to the currently edited form and must not mutate saved
  preset storage.
- Clicking a model bone must prefer an existing pose sheet/keyframe when the
  per-bone sheet is empty. The current pose sheet wins first, then the last pose
  sheet, then the first matching sheet; pose overlay sheets are recognized and
  preferred according to the graph's existing ordering/selection.
- Preserve FSR-only nested-form, generalized hierarchy, collaboration, remote
  input, renderer-cache, and UI behavior.

## Acceptance Criteria

- [ ] IK target and pole edits return to their prior visible positions after
      Ctrl+Z, and redo restores the edited positions.
- [ ] An enabled IK chain blocks rotation gestures for each owned chain bone;
      zero-weight/disabled chains and a tip without `tipRotation` remain FK
      rotatable.
- [ ] Reopening an IK/physics/constraint preset menu selects and scrolls to the
      structurally equal preset despite numeric type differences.
- [ ] Reset clears the active form data only; saved preset content is unchanged.
- [ ] Bone picking ignores an empty per-bone sheet when a matching pose or pose
      overlay sheet contains usable keyframes, and does not create duplicates.
- [ ] Focused regression tests, `compileJava`, `compileClientJava`, relevant
      migration/IK test tasks, and `git diff --check` pass.

## Out Of Scope

- Replacing FSR files wholesale with FS versions.
- Changing artifact/version identity, particles, audio, themes, or unrelated
  editor/runtime behavior.
- Deleting or rewriting existing saved presets.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
