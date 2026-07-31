# MobForm Health 0 实时死亡动画

## Goal

MobForm NBT 调整为 {Health:0} 时，预览实时进入死亡动画。

## Requirements

- Compare MobForm NBT application and preview entity lifecycle with `bbs-fs-master`.
- Applying NBT containing `{Health:0}` in the MobForm editor must update the preview entity's death state immediately.
- The death animation must be driven by the normal entity/render state expected by the project, not an editor-only visual approximation.
- Positive-health and unrelated NBT edits must retain their existing live-preview behavior.
- Stop before commit and wait for the user's build and runtime confirmation.

## Acceptance Criteria

- [ ] Applying `{Health:0}` to a MobForm causes its preview to enter the death animation without closing and reopening the editor.
- [ ] Reapplying positive health restores the expected live preview state where supported by the existing editor workflow.
- [ ] NBT edits unrelated to health do not regress.
- [ ] Automated or focused code-level checks cover the identified state synchronization boundary where practical.
- [ ] The user confirms the fix in a local build and runtime test before commit.

## Out of Scope

- Redesigning entity death animation behavior outside MobForm editing and preview.
- Changing normal game health or damage mechanics.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
