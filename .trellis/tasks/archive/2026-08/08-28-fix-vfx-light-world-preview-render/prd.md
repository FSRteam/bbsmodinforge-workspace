# 修复 VFX Light 世界与模型编辑预览实际渲染

## Goal

修复 VFX Light 在 BBSFSR 世界渲染和模型编辑预览中完全不可见的问题。必须基于 `TEMP/VFXLIGHTS` 对比确认实际断点，不以编译成功代替运行链验证。

## Requirements

- 世界中放置/播放四种灯时，必须能观察到灯光消费效果或明确的诊断日志。
- 模型编辑器的表单预览必须绘制灯源/形状/光池或光束，不应只显示空白。
- fallback shader 必须从 `assets/vfxlights/shaders/core` 正确加载，不能默认查找 `minecraft` 命名空间。
- 保留 `VFX Light` 显示名和 `VFX_Light_fsr.jar` 产物名。
- 不修改 `new-fs25-sync` 主工程，不复制未验证的 Fabric/Iris Mixin。

## Acceptance Criteria

- [ ] ShaderInstance 资源解析明确映射到 `vfxlights` namespace，并在资源缺失时记录可诊断错误。
- [ ] LightFormRenderer 在 UI/modelRenderer/preview 上绘制可见的灯光预览。
- [ ] 世界回调在主渲染目标上执行 fallback，并正确恢复 GL/FBO 状态。
- [ ] `clean build`、`check` 和 JAR 审计通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
