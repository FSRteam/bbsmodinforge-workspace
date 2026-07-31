# BBS-FS 回放缺陷修复批次

## Goal

对照 bbs-fs-master 参考源码分析三个回放缺陷，统一分析后逐项修复；每项在用户构建运行验证通过后才提交。

## Requirements

- Scope contains exactly three defects: enchanted item data in external recordings, MobForm death animation after live NBT edits, and actor-mode Form gizmo anchors.
- Analyze all three defects against `bbs-fs-master` before implementing any fix.
- Keep each defect independently implementable, verifiable, and committable.
- Fix defects in the listed child-task order unless analysis establishes a technical dependency.
- Before each commit, stop and wait for the user to build, run, and confirm the corresponding fix.
- Do not include the withdrawn camera keyframe/fly-mode report.

## Acceptance Criteria

- [ ] All three defects have evidence-backed root-cause analyses and proposed solutions.
- [ ] Each child fix passes repository checks appropriate to its affected modules.
- [ ] Each child fix is confirmed by the user's runtime verification before its commit is created.
- [ ] Each child fix is committed separately with no unrelated changes.
- [ ] The camera keyframe/fly-mode report is absent from planning and implementation scope.

## Delivery Order

1. External recording enchanted item data
2. MobForm `Health:0` live death animation
3. Actor-mode Form gizmo anchor following

## Out of Scope

- Camera editor keyframe behavior after toggling fly mode.
- Unrelated editor, recording, rendering, or UI refactors.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
