# 技术设计：按骨骼与 Form 附魔效果

## 模式与兼容目标

模式定义统一为：

| 值 | 模式 | 渲染状态 |
|---|---|---|
| 0 | 关闭 | 不绘制附魔 pass |
| 1 | 全表面 | `RenderType.energySwirl(ENCHANTED_GLINT_ENTITY, u, v)` |
| 2 | 仅边缘 | 同上，CPU 将 Fresnel 强度写入顶点 alpha |
| 3 | 原版 | `RenderType.armorEntityGlint()` |

旧的 `model_glint` core shader 已删除。原因不是 shader 本身无法绘制，而是 Iris/光影包只会可靠识别和替换 Minecraft 已知的 RenderType shader；BBS 独立 shader 的 draw call 经常不进入光影包的 entity gbuffer。

`GlintRenderState` 是唯一状态入口：先 `setupRenderState()`，再取 `RenderSystem.getShader()` 绘制 VAO 或 Mesh，最后 `clearRenderState()`。附魔是装饰性二次 pass，统一 `depthMask(false)`；可变换覆层使用 `LEQUAL`，因为原版盔甲附魔的 `EQUAL` 只适用于与本体完全重合的几何。

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
| Cubic VAO | 已烘焙 VAO | 全表面/原版直接重画；边缘模式为保留逐顶点 Fresnel，重建当前骨骼的 CPU Mesh |
| Cubic immediate/hybrid | `CubicGlintCubeRenderer` | 按模式分批；边缘 Fresnel 写顶点 alpha；hybrid 只处理回退 CPU 的骨骼 |
| Extruded | `ModelVAO` | 复用 VAO 进入同一状态入口 |
| Billboard | 重建双面 quad | 在局部 stack 应用附魔变换后绘制 Mesh |
| Block/Item | `GlintCaptureBufferSource` | 再运行一次 vanilla 几何生成，汇入 `NEW_ENTITY` Mesh；丢弃物品自身的 vanilla glint 分桶，避免重复顶点 |
| BOBJ | 不支持 | 该格式没有逐骨骼颜色/效果数据通路 |

所有路径在 picking/stencil 时跳过附魔 pass。材质、trim 和物品自身的原版附魔仍由原有路径绘制。

## 透明与颜色

`energySwirl` 使用原版加性透明状态，暗纹不会像旧的不透明自定义 pass 一样把后方模型覆盖成黑色。颜色和不透明度来自 `NEW_ENTITY` 顶点颜色；原版 glint 的 `POSITION_TEX` 格式没有颜色属性，因此通过 `RenderSystem.setShaderColor` 提交 tint/alpha，并在 draw 后恢复白色。

## 已知限制

1. BOBJ 不支持逐骨骼附魔。
2. CPU 即时路径按模式合批，速度取该批第一根骨骼；VAO 主路径仍是逐骨骼速度。
3. 边缘 Fresnel 对 Cubic 与捕获的 Block/Item 几何最精确；Billboard 是平面，泛用 VAO 若没有 CPU 顶点重建则只使用其标准 energy-swirl 覆层。
4. 编译只能验证 API 与资源引用；Iris gbuffer 路由及不同光影包表现必须入游戏验收。
