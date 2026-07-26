# 技术设计：盔甲跟随骨骼弯曲

## 问题模型

弯曲模型的 `armor_left_arm` 等挂点跟随上段骨骼，而 `left_elbow` / `left_knee` / `torso_lower` 是参与弯曲的兄弟关节。盔甲若只乘挂点矩阵会保持整块刚体。

首版双骨骼 LBS 仍不够：原版 `ModelPart.Cube` 的一个侧面只有四个角点。只有上下端权重时，GPU 最终只能画出两端之间的一张平面；而线性混合两个旋转后的坐标还会在大角度下压缩截面，正是截图里的内陷弓弦。

## 坐标空间

上段完整矩阵：

```text
upper = renderBase * armorAttachment * armorTransform * rotateX180
```

关节相对增量：

```text
bendDelta = jointMatrix * inverse(jointOrigin)
lower = renderBase * bendDelta * armorAttachment * armorTransform * rotateX180
```

`bendDelta` 必须位于外层渲染变换与盔甲挂点之间。禁止 `bendDelta * upper`，否则摄像机相对平移也会被关节旋转。

## 几何捕获与细分

`Cube.compile()` 是 public。给 `ModelPart.render()` 传单位 `PoseStack` 后，包装的 `VertexConsumer` 收到盔甲局部坐标、UV、颜色、overlay、light 和局部法线，无需 mixin/accessor。

`SkinningVertexConsumer` 每四个顶点缓冲一个 quad：

1. 找出具有两条水平边的纵向侧面。
2. 按侧面 Y 高度细分为约每模型像素一条，最多 16 条。
3. 对位置、UV 和法线做边插值，保留颜色、overlay、light 与原绕序。
4. 顶/底水平面无需细分，直接按其高度权重绘制。

这样中间权重处拥有真实顶点，最终才能形成连续曲面。

## 刚体关节插值

在盔甲局部空间计算：

```text
relative = inverse(upper) * lower
```

若 `relative` 的线性部分是无缩放、无剪切的刚体旋转：

- 提取相对四元数和旋转轴。
- 从 `relative` 的平移恢复旋转枢轴。由于旋转轴方向是欠约束自由度，用过渡区中心作为轴向提示，求解：

```text
(I - R + axis*axis^T) * pivot
  = (translation - axis*dot(axis, translation))
    + axis*dot(axis, hint)
```

- 中间权重对单位四元数和相对四元数做 slerp，并围绕该枢轴旋转局部点。
- 权重 0 和 1 不走近似，分别严格应用 `upper` 与 `lower`。

这个方案让每个高度截面接受一个刚体旋转，避免 LBS 的横截面收缩。若相对矩阵含缩放或剪切，则保留精确端点并回退 LBS，因为单个刚体四元数无法表达该变换。

法线始终使用对应位置矩阵的逆转置；奇异矩阵回退单位法线矩阵。

## 三条材质路径

`ArmorRenderer.renderPart()` 是唯一几何入口，材质层、trim 与 `armorEntityGlint()` 全部调用它。因此三者共享同一细分和蒙皮结果，不会发生贴图、纹饰、附魔彼此错位。

## 配置与模组盔甲

- `ArmorSlot` 可显式指定 `lower_group`、`bend_start`、`bend_end`。
- 未指定下段时由 `ArmorType` 推断 elbow/knee/torso_lower；目标骨骼不存在则完整退回旧刚体路径。
- 默认过渡区：手臂 2..6 像素，腿/躯干 4..8 像素。
- `IClientItemExtensions#getHumanoidArmorModel` 提供的自定义几何继续支持渲染，但默认不套用原版几何标定的弯曲区间，避免错误撕裂。

## 验证

- 编译与全量 build 验证所有 API 和三条材质调用路径。
- 入游戏分别检查 30°、60°、90° 的手肘、膝盖和躯干。
- 静止模型并移动/旋转摄像机，盔甲形变不得变化。
- 同时检查材质、trim、原版 glint 对齐，以及非 bends 模型与模组自定义盔甲。
