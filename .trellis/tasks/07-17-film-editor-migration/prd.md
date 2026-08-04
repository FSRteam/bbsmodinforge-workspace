# 影片编辑器迁移回归修复

## Goal

补齐 FS 1.21.1 已验证但 `new` 漏移植的影片编辑器交互和布局修复。

## Requirements

- Ctrl+滚轮仅在时间轴区域移动游标；预览 Orbit 保留缩放。
- Form anchor 轨道显示并拾取整 Form Gizmo。
- 每部 Film 分别保存/恢复 camera/action/replay 时间轴滚动。
- 打开 Film 后按实际层范围延迟居中；clip 属性面板在 resize/fill 后恢复滚动。
- 剪辑时间线在鼠标释放被预览/飞行控制消费或 UI 层级变更取消时，必须结束拖拽状态。
- 活动的剪辑拖拽可由后续鼠标点击取消；右键打开菜单不得让时间线继续跟随指针。
- 单击剪辑后无需先移动鼠标，时间线后续点击、右键菜单和飞行视角输入仍立即可用。
- 对照 `65bb8c93` 迁移意图，不覆盖 NeoForge 特有渲染适配。

## Acceptance Criteria

- [ ] 五项迁移回归均有自动或明确手动验证。
- [ ] 相机六模式、右键菜单、保存/退出和现有 Web Remote hooks 不回归。
- [ ] 剪辑拖拽正常释放，取消/漏释放不残留，首次移动前的点击交互正常。
- [ ] 编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
