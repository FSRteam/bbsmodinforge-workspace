# 粒子事件与世界渲染修复

## Goal

修复粒子事件、JSON 往返、输入焦点以及 Particle/Trail 世界渲染空间和 GL 状态。

## Requirements

- 根 ParticleForm 首次定位先建立真实位置基线，静止放置不产生 travel distance。
- collision guard 在独立 crossing 后重新武装；timeline key 0 与 raw sibling 按规范保留。
- 文本框聚焦时 Space/Ctrl+Z/Y 不触发全局粒子快捷键。
- FormRenderingContext 明示 camera-relative/entity-local/UI-local，Particle/Trail 不猜全局状态。
- Chroma/Iris 每帧只做一次 BBS world composite，并精确恢复 depth 等 GL 状态。
- 保持 legacy FSR 粒子政策，不引入 archive 新粒子系统。

## Acceptance Criteria

- [ ] 远坐标静止 emitter 不触发事件风暴；多次碰撞按 crossing 触发。
- [ ] 混合合法/未知 JSON 编辑后未知 sibling 仍存在。
- [ ] Actor/ModelBlock Particle/Trail 不随相机漂移。
- [ ] 粒子/渲染定向测试、编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
