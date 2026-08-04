# 物品实体交互与动作录制修复

## Goal

使枪、方块、攻击和实体交互的实际执行、录制与回放遵循 NeoForge/原版分派语义。

## Requirements

- 枪成功开火消费当前 hand，设置 projectile owner，并保留伤害归属。
- 只录制原版最终实际执行的方块交互分支，避免 InteractBlock/UseBlockItem 重复回放。
- 攻击记录保留目标/意图，横扫不会把多目标伤害集中到最近实体。
- 支持并验证实体 interact/interactAt/mobInteract 的 hand、目标、距离和结果。
- ModelBlock 使用正确 sided result，未授权时不打开编辑界面。

## Acceptance Criteria

- [ ] 双枪/主枪副手物品只执行被消费的 hand；弹丸不自撞且 owner 可用。
- [ ] SKIP/PASS/default block interaction 代表场景录制回放等价。
- [ ] 横扫和实体右键目标/菜单语义可复现。
- [ ] 编译、动作回归和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
