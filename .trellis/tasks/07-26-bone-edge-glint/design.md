# 技术设计：按骨骼与 Form 附魔效果

## 模式与兼容目标

模式定义统一为：

| 值 | 模式 | 渲染状态 |
|---|---|---|
| 0 | 关闭 | 不绘制附魔 pass |
| 1 | 全表面 | 无光影用 `energySwirl`；Iris 世界光影用保持原版纹理矩阵的 `armorEntityGlint` |
| 2 | 仅边缘 | 同上，CPU 将 Fresnel 强度写入顶点 alpha |
| 3 | 原版 | `RenderType.armorEntityGlint()` |

旧的 `model_glint` core shader 已删除。原因不是 shader 本身无法绘制，而是 Iris/光影包只会可靠识别和替换 Minecraft 已知的 RenderType shader；BBS 独立 shader 的 draw call 经常不进入光影包的 entity gbuffer。Photon 与 IterationRP 的实际包内容进一步证明，附魔专用行为集中在 `gbuffers_armor_glint`，不能假设普通 `energy_swirl` 实体路由具有相同混合语义。

`GlintRenderState` 是唯一状态入口：先保存调用方完整渲染状态并执行 `setupRenderState()`，再取 `RenderSystem.getShader()` 绘制 VAO 或 Mesh，最后 `clearRenderState()` 并恢复保存状态。附魔是装饰性二次 pass，统一 `depthMask(false)`；未变换覆层使用 `EQUAL`，把可见范围限制为本体实际写入深度的像素，只有独立 XYZ 变换后的覆层使用 `LEQUAL`。

附魔不再由 `CubicVAORenderer.renderGroup()` 立即绘制。该渲染器只收集每根骨骼的 VAO/边缘 Mesh 快照，`ModelInstance` 在 VAO 与 CPU 本体都完成后统一提交。世界渲染时这些命令进入 `FormTranslucentQueue`，稳定排在此前收集的半透明本体命令之后。

## 数据流

```text
Form 编辑器 -> Form.glint* -> Billboard/Extruded/Block/Item renderer
Pose 编辑器 -> PoseTransform.glint* -> Model.applyPose -> ModelGroup -> Cubic renderer
                                                       \-> GlintRenderState -> vanilla RenderType
```

- Form 级字段：`glintMode`、`glintColor`、`glintSpeed`、`glintTransform`。
- 骨骼级字段：`PoseTransform` 中对应四项，每帧复制到 `ModelGroup`。
- 模式是离散值，两个 `lerp` 都按 0.5 阈值阶跃；颜色、速度、变换照常插值。
- `glintSpeed` 的静息值为 1，`add()` 叠加的是 `speed - 1`，避免无关 pose 轨道凭空加速附魔。

## 变换空间

VAO 顶点保持骨骼局部坐标直到 draw，因此使用：

```text
ModelView * GlintTransform * localVertex
```

即时、Billboard 和 vanilla 捕获顶点会先经 `PoseStack` 烘焙，故必须在发射顶点前把 `glintTransform` 乘入局部 stack，提交给 `GlintRenderState` 时不再追加变换。禁止把局部附魔变换左乘到已含摄像机相对位移的完成矩阵。

非等比缩放时，VAO 法线使用附魔变换线性部分的逆转置；奇异矩阵回退原法线。

## 渲染路径

| 路径 | 几何来源 | 处理 |
|---|---|---|
| Cubic VAO | 已烘焙 VAO | 本体遍历时只收集；完整本体之后提交。全表面/原版重画 VAO；边缘模式重建当前骨骼的 CPU Mesh |
| Cubic immediate/hybrid | `CubicGlintCubeRenderer` | 完整 CPU 本体之后按模式及恒等/已变换深度策略分批；hybrid 只处理回退 CPU 的骨骼 |
| Extruded | `ModelVAO` | 复用 VAO 进入同一状态入口 |
| Billboard | 重建双面 quad | 在局部 stack 应用附魔变换后绘制 Mesh |
| Block/Item | `GlintCaptureBufferSource` | 再运行一次 vanilla 几何生成，汇入 `NEW_ENTITY` Mesh；丢弃物品自身的 vanilla glint 分桶，避免重复顶点 |
| BOBJ | 不支持 | 该格式没有逐骨骼颜色/效果数据通路 |

所有路径在 picking/stencil 时跳过附魔 pass。材质、trim 和物品自身的原版附魔仍由原有路径绘制。

## 透明与颜色

`energySwirl` 使用原版加性透明状态。颜色和不透明度来自 `NEW_ENTITY` 顶点颜色；原版 glint 的 `POSITION_TEX` 格式没有颜色属性，因此通过 `RenderSystem.setShaderColor` 提交 tint/alpha。Iris 光影下也设置 shader color，并保留 BBS 顶点颜色供会读取 `gl_Color` 的包使用；Photon 的 armor-glint 程序完全忽略颜色输入，因而自定义 tint/alpha/Fresnel 只能降级为 Photon 的原生附魔外观。绘制后恢复进入附魔前的真实 shader color，而不是假定白色；纹理槽、纹理矩阵、shader、混合、深度与剔除状态也同样恢复。

边缘 Fresnel 使用同一 emitted-vertex 空间的位置、法线与视点。视点通过绘制时剩余 ModelView 的逆矩阵取得：VAO 边缘网格在骨骼局部空间生成，使用 `inverse(global * bone) * viewOrigin`；即时与捕获网格已经 PoseStack 烘焙，使用 `inverse(global) * viewOrigin`。这覆盖摄像机相对世界栈、UI 相机、手部与 view-bobbing 等不同上下文，不再假定视点固定为零。

VAO 边缘网格必须保持与本体 VAO 相同的局部顶点边界，并使用同一个 `global * bone` ModelView。虽然 `global * (bone * vertex)` 与 `(global * bone) * vertex` 数学等价，但浮点运算顺序不同，`GL_EQUAL` 会在部分观察方向产生不同的深度位并形成半圆缺失。

Iris 光影路径保留 `armorEntityGlint()` 自己建立的原版纹理矩阵。自定义速度只在 `energySwirl` 路径保证；不得在专用 armor-glint 程序上覆盖未经逐包像素验证的矩阵。Photon/IterationRP 下可见性与透明合成优先于自定义滚动速度。

## 已知限制

1. BOBJ 不支持逐骨骼附魔。
2. CPU 即时路径按模式合批，速度取该批第一根骨骼；VAO 主路径仍是逐骨骼速度。
3. 边缘 Fresnel 对 Cubic 与捕获的 Block/Item 几何最精确；Billboard 是平面，泛用 VAO 若没有 CPU 顶点重建则只使用其标准 energy-swirl 覆层。
4. 编译只能验证 API 与资源引用；Iris gbuffer 路由及不同光影包表现必须入游戏验收。
5. 光影包若在 `gbuffers_armor_glint` 中忽略 `gl_Color`，自定义颜色、透明度和边缘渐变无法由 BBS 从外部强制恢复；兼容目标退化为使用该包原生附魔外观而不是完全消失或产生黑底。
