# 本地远程输入与断线生命周期修复

## Goal

统一本地/远程 UI 输入所有权，并保证换屏、断线、保存和 HUD 状态成对结束。

## Requirements

- remote lease 活动时禁止本地与远端坐标、按钮、modifier 拼成混合手势。
- 多事件 batch 在每个事件后验证 current screen/session；旧 screen 不接收剩余事件。
- UIDraggable/UITrackpad 只由匹配按钮完成/取消并正确消费 release。
- Film session 固定 repository；断线保存/恢复先于 handshake reset。
- UIScreen 恢复原 hideGui，而非固定写 false。

## Acceptance Criteria

- [ ] 本地/远程冲突、换屏 batch、不同按钮 release、clear/disconnect 均有确定性测试。
- [ ] 远程影片断线不写本地同名库且不丢未提交修改。
- [ ] F1 隐藏 HUD 前后状态保持。
- [ ] `testUiInputMailbox`、编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
