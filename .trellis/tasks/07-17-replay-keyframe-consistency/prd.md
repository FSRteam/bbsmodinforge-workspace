# Replay引用与关键帧交互修复

## Goal

修复 Replay 删除后的索引引用和关键帧导航快捷键边界。

## Requirements

- 删除、排序和批量变化共享同一 Replay 索引重映射，覆盖 anchor 与 EntityClip selector。
- `[`/`]`、Ctrl/Shift 导航在无 sheet、无 selection、轨道外时安全且作用域明确。
- Delete 快捷键与右键删除执行相同引用修复。

## Acceptance Criteria

- [ ] A/B/C/D 删除任意 Replay 后所有剩余引用指向原逻辑对象或明确失效。
- [ ] 关键帧空白区快捷键不抛异常、不误操作其他组件。
- [ ] 定向测试、编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
