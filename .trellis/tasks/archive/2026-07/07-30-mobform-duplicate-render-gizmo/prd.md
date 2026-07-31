# 实体选择器 MobForm 重复渲染与 Gizmo 跟随

## Goal

实体选择器添加同 ID MobForm 时：无额外重复渲染；拾取命中正常；Gizmo 正常跟随所选肢体。

## Root Cause

`MorphRenderer.renderLivingEntity()` 仅以 `FormUtilsClient.getCurrentForm()` 作为重入守卫。`MobFormRenderer` 的三个渲染入口（renderInUI / render3D / 世界渲染）均通过 `EntityRenderDispatcher` 渲染实体并处于 `MobRenderContext` 作用域内，但该路径不推入 currentForm 栈——因此守卫失效，匹配的实体选择器把自身 form 以世界主摄像机额外渲染了一遍（固定底部视角、位于上半部分、无法拾取）。

## Fix

`MorphRenderer.renderLivingEntity()` 增加 `MobRenderContext.isActive()` 守卫，覆盖所有 MobForm 实体渲染路径。

## Acceptance Criteria

- [x] 同 ID MobForm 无重复渲染
- [x] 拾取高亮/骨骼选择正常
- [x] Gizmo 跟随所选肢体（连带修复）

## Commit

- `84591d17` fix(forms): stop selector forms duplicating a MobForm entity render
