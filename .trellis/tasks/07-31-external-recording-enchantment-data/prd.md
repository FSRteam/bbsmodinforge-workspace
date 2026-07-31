# 外部录制保留附魔装备数据

## Goal

外部录制与回放穿戴或手持附魔装备时，保留所有附魔相关内容。

## Requirements

- Compare the workspace recording and playback data path with `bbs-fs-master`.
- Preserve enchantment-related serialized item data for equipped armor and held items during external recording and playback.
- Determine the actual loss point before choosing a fix; NBT clearing is a hypothesis, not an accepted root cause.
- Preserve existing behavior for unenchanted items and unrelated recorded actor data.
- Stop before commit and wait for the user's build and runtime confirmation.

## Acceptance Criteria

- [ ] An externally recorded actor wearing enchanted armor replays with the enchantment data and visual effects intact.
- [ ] An externally recorded actor holding an enchanted item replays with the enchantment data and visual effects intact.
- [ ] Unenchanted equipment continues to record and replay correctly.
- [ ] Automated or focused code-level checks cover the identified serialization boundary where practical.
- [ ] The user confirms the fix in a local build and runtime test before commit.

## Out of Scope

- Changes to enchantment mechanics or rendering unrelated to recorded equipment data.
- Broad changes to item serialization without evidence from the affected recording path.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
