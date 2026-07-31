# MobForm 加色法混合模式适配

## Goal

MobForm 常规使用场景下，勾选「Additive color」后应呈现真实的加色发光效果。

## Root Cause

`additiveColor` 开启时只走 `FormColorBlend.BRIGHTEN`（顶点色 RGB × (1 + overlay*alpha*8)），但 vanilla 实体渲染的字节顶点格式会把超过 1.0 的值 clamp 回 255，发光效果实际不生效。所有 form renderer 均未切换 GPU 混合模式。

## Fix

`MobFormRenderer` 两个绘制入口（`createWorldLayerPreparation` 3D/世界路径、`renderInUI` UI 预览路径）在 `additiveColor` 开启时注入 `blendFuncSeparate(SRC_ALPHA, ONE, ONE, ONE_MINUS_SRC_ALPHA)`（与 `ParticleEmitter.ParticleMaterial.ADD` 一致）。

注入时机：layer preparation 钩子在 `RenderType.setupRenderState()` 之后、`drawWithShader` 之前运行，绘制后 `clearRenderState()` 自动恢复默认混合。stencil 拾取路径不注入，颜色编码不受影响。

## Acceptance Criteria

- [x] MobForm 勾选 Additive color 后呈现加色发光效果
- [x] 3D 预览与 UI 小预览均生效
- [x] 拾取/骨骼选择不受影响
- [x] 编译通过

## Commit

- `7e201b70` feat(forms): add true additive blending to MobForm rendering
