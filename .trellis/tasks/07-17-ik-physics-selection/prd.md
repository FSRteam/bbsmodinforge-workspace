# IK物理约束与选骨所有权修复

## Goal

完成 IK/physics 最终约束、绑定、owner/space、性能与视口选骨合约。

## Requirements

- tipRotation、physics swing+twist 和短链均对最终 renderer quaternion 夹限并推进正确 parent frame。
- pole false→true、enable/re-enable 和配置变化正确重绑。
- IKControl 缺省 softness 与 ModelIKConfig 的 0 一致，缺键插值不偷换为 soft IK。
- nested body part、preview、world、first-person arm 使用稳定且隔离的 simulation owner/space。
- solver scratch 预热后复用，不降低迭代/碰撞/固定步长。
- Ctrl 选骨验证 Form owner，隐藏 body-part editor 不消费 stale selection。

## Acceptance Criteria

- [ ] cubic/BOBJ tip、physics twist、短链和 limits 数值回归通过。
- [ ] pole/softness/owner/first-person 场景满足父任务及 quality spec。
- [ ] 普通/Ctrl 选骨始终修改可见且正确的 Form owner。
- [ ] 编译、IK 数值测试、build 和手测矩阵通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
