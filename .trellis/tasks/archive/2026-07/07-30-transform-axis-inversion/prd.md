# 3D 预览 G/S/R 变换 Y/Z 轴方向反转

## 结论：已由重复渲染修复连带解决，无需独立修复

### 复现条件

实体选择器存在与编辑中 MobForm 相同 mobID 的选择器时，3D 预览出现重复渲染（MorphRenderer 以世界主摄像机额外绘制一遍），G/S/R 的 ray drag 因 GizmoDrag 状态被干扰而降级到后备路径，产生 Y/Z 轴反转的观感。

### 实际根因（修正）

`MorphRenderer.renderLivingEntity()` 的重入守卫仅依赖 `FormUtilsClient.getCurrentForm()` 栈，而 MobForm 的实体渲染路径（`renderInUI`/`render3D`/世界渲染）不推入该栈，守卫失效。匹配的实体选择器把自身 form 以世界相机重复渲染，干扰了 3D 预览的拾取与 Gizmo 状态。

（初版分析误判为 40×40 小预览 GL 状态泄漏；禁用小预览实测仍复现，且 GL viewport 保护无效。最终根因见上。）

### 修复（属 MobForm 重复渲染任务，commit 84591d17）

`MorphRenderer.renderLivingEntity()` 增加 `MobRenderContext.isActive()` 守卫。

### 验收

- [x] 实体选择器同 ID MobForm 场景下 G/S/R 各轴方向正确
- [x] 不再出现 Y/Z 反转
- [x] 经实测确认不再复现（2026-07-31）
