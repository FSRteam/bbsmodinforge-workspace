# 演员模式 Form Gizmo 锚点跟随

## Goal

影片中所有 Form 回放启用演员后，transform 与 pose gizmo 正确跟随对应锚点；覆盖 bbs-fs 与 bbs-FSR 共有路径。

## Requirements

- Compare the film editor's actor-mode Form rendering and gizmo anchor path in the workspace and `bbs-fs-master`.
- Cover the behavior shared by bbs-fs and bbs-FSR rather than patching only one visible caller.
- When a film replay enables `actor`, transform gizmos must follow the replayed Form transform anchor.
- Pose gizmos must follow the selected pose/body-part anchor for every supported Form type.
- Preserve correct gizmo behavior when `actor` is disabled.
- Stop before commit and wait for the user's build and runtime confirmation.

## Acceptance Criteria

- [ ] With `actor` enabled, transform gizmos follow the active replay Form's transform anchor.
- [ ] With `actor` enabled, pose gizmos follow the selected body-part anchor.
- [ ] The fix applies to every supported Form type through the shared bbs-fs/bbs-FSR path.
- [ ] Existing gizmo placement with `actor` disabled does not regress.
- [ ] Automated or focused code-level checks cover the shared anchor calculation where practical.
- [ ] The user confirms the fix in a local build and runtime test before commit.

## Out of Scope

- Gizmo visual redesign or unrelated transform/pose editing behavior.
- Form-specific special cases unless analysis proves the shared path cannot express the required anchor.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
