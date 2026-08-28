# 补齐 VFX Light 光源视觉、辅助线与编辑器功能

## Goal

继续对比上游 VFX Light 与当前 FSR 移植，补齐世界/模型编辑器视觉和交互缺口，消除当前四种灯的灰色全屏覆盖、辅助线缺失、模型方块光源延迟清理、数值控件类型错误和 Air/Style 不生效问题。

## Requirements

- Point/Area 的世界效果保持真实光源形态，Ambient 必须是有范围的光球/体积，不得覆盖全世界灰色材质。
- 所有灯使用表单颜色和强度，默认白色光，颜色修改能改变画面。
- 模型编辑器显示光源辅助线、光球、聚光锥和区域光形状。
- 模型方块破坏后光源立即或短时间内消失；编辑器预览不能让世界光源无故过期。
- 有上限的数值使用滑条控件，不使用普通输入框。
- Air、Style、Toon、Barn 选项在四种表单的对应面板可用，并能写回真实/运行时关键帧值。
- 继续保持 `VFX Light` 和 `VFX_Light_fsr.jar` 命名及中英文翻译。

## Acceptance Criteria

- [ ] Ambient 世界效果按球体/盒体范围衰减，默认不覆盖整屏；四种灯颜色可见地影响效果。
- [ ] 模型编辑器辅助线在 modelRenderer/preview 路径可见，世界 guide 不依赖未调用的死方法。
- [ ] 模型方块光源清理有 source block 关联和短 TTL 兜底。
- [ ] intensity/range/temperature 等有限值使用 slider trackpad。
- [ ] Air/Style 面板读写 `get()` 的运行时关键帧值，拖动期间效果立即刷新。
- [ ] `clean build`、`check`、JAR 审计通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
