# 技术设计：盔甲跟随骨骼弯曲

## 核心思路

双骨骼线性混合蒙皮（LBS）。盔甲顶点按其在盔甲局部空间的 Y 高度分配权重，分别用**上段骨骼矩阵**和**下段骨骼矩阵**变换后按权重插值位置：

```
p' = (1-w) * (M_upper * p) + w * (M_lower * p)
```

插值**变换后的位置**而非插值矩阵 —— 简单可靠，关节处表面连续不断开。

## 关键洞察：无需 mixin

原版 `ModelPart` 的顶点数据不可从外部访问：

| 成员 | 访问级别 | 结论 |
|---|---|---|
| `ModelPart.cubes` | `private final List<Cube>` | 不可访问 |
| `Cube.polygons` | `private final Polygon[]` | 不可访问 |
| `ModelPart.Polygon` | **package-private** `static class` | 外部包连类型都无法引用 |
| `ModelPart.Vertex` | **package-private** `static class` | 同上 |
| `Cube.compile(Pose, VertexConsumer, int, int, int)` | **public** | ✅ 可用 |
| `Cube.minX/minY/.../maxZ` | **public final** | ✅ 可用 |

`Cube.compile()` 内部逻辑（反编译确认）：

```java
Matrix4f matrix4f = pose.pose();
for (Polygon polygon : this.polygons) {
    Vector3f n = pose.transformNormal(polygon.normal, vector3f);
    for (Vertex vertex : polygon.vertices) {
        float f3 = vertex.pos.x() / 16.0F;   // 局部坐标，已除以 16
        float f4 = vertex.pos.y() / 16.0F;
        float f5 = vertex.pos.z() / 16.0F;
        Vector3f p = matrix4f.transformPosition(f3, f4, f5, vector3f);
        consumer.addVertex(p.x(), p.y(), p.z(), color, u, v, overlay, light, n.x(), n.y(), n.z());
    }
}
```

**所以**：只要传入**单位矩阵**的 `PoseStack`，`addVertex` 收到的坐标就是**盔甲局部空间坐标**（单位为 1/16 方块），法线也保持未变换状态。用一个包装 `VertexConsumer` 拦截 `addVertex`，即可拿到局部坐标做蒙皮，再输出最终坐标。

**零 mixin、零 accessor、零 AT。**

## 数据结构

### `ArmorSlot` 扩展

现状（`cubic/model/ArmorSlot.java`，全文 37 行）：

```java
public String group = "";
public final Transform transform = new Transform();
```

新增可选字段：下段骨骼名、过渡区间起止（单位：模型像素，与 `Cube.minY/maxY` 同尺度）。`fromData` 用带默认值读取，旧 config 读不到 → 下段为空 → 退化为现有单骨骼路径。

`fromData` 已有「纯字符串 or map」双形态解析（`:23-35`），新字段只加在 map 分支，字符串形态自动走推断。

### 命名约定推断

`ArmorType` → 默认下段骨骼候选：

| ArmorType | 默认下段 |
|---|---|
| `LEFT_ARM` / `RIGHT_ARM` | `left_elbow` / `right_elbow` |
| `LEFT_LEG` / `LEFT_BOOT` | `left_knee` |
| `RIGHT_LEG` / `RIGHT_BOOT` | `right_knee` |
| `CHEST` / `LEGGINGS` | `torso_lower` |
| `HELMET` | 无（头不弯） |

**探测放在渲染侧而非 `ModelConfig.rebuild()`** —— `ModelConfig` 拿不到模型骨骼表，而 `ModelFormRenderer` 有现成的 `MatrixCache bones`（`:82`）。`MatrixCache.get(path)` 对未知 key 返回静态 `EMPTY`（matrix 为 `null`）且**无副作用、不写入 map**，因此探测是安全的：

```java
Matrix4f lower = this.bones.get(lowerName).matrix();
if (lower != null) { 走蒙皮 } else { 走原路径 }
```

这比在 `rebuild()` 里推断更简单，且自动适配任何自定义模型。

## 渲染流程改造

### `ModelFormRenderer.renderArmor()`（`:587-615`）

当前取单根骨骼矩阵：

```java
Matrix4f matrix = this.bones.get(armorSlot.group).matrix();
...
MatrixStackUtils.multiply(stack, matrix);
MatrixStackUtils.applyTransform(stack, armorSlot.transform);
stack.mulPose(ROTATE_X_180);
ActorEntityRenderer.armorRenderer.renderArmorSlot(stack, consumers, target, type.slot, type, light);
```

改为：额外解析下段矩阵，若存在则把「上段 stack」与「下段 stack」两套完整变换传给 `ArmorRenderer`。两套 stack 各自独立应用 `armorSlot.transform` 与 `ROTATE_X_180`，保证与现有单骨骼路径的坐标系完全一致。

关节增量由 `jointMatrix * inverse(jointOrigin)` 得到，属于模型局部空间，必须插在外层渲染变换与盔甲挂点变换之间：

```text
upper = renderBase * armorAttachment * armorTransform * rotateX180
lower = renderBase * jointDelta * armorAttachment * armorTransform * rotateX180
```

禁止写成 `lower = jointDelta * upper`。`upper` 已含实体或预览的摄像机相对渲染基矩阵，左乘模型局部增量会旋转其中的平移分量，使盔甲随摄像机转动，并把下半段拉向错误方向。

### `ArmorRenderer` 蒙皮路径

`renderArmorPart` / `renderTrim` / `renderGlint`（`:108-126`）三者都调 `part.render(matrices, consumer, ...)`。统一改为：存在下段矩阵时走蒙皮渲染，否则保持原调用（零回归）。

蒙皮渲染：用单位 `PoseStack` 调 `part.render(identityStack, skinningConsumer, light, overlay, color)`，`skinningConsumer` 在 `addVertex` 中：

1. 收到局部坐标 `(x, y, z)`（已 /16）与未变换法线 `(nx, ny, nz)`
2. 权重 `w = clamp((y*16 - bendStart) / (bendEnd - bendStart), 0, 1)`
3. `pUpper = M_upper * p`，`pLower = M_lower * p`，`pOut = lerp(pUpper, pLower, w)`
4. 法线同法混合后归一化
5. 转发给真实 `VertexConsumer`

> 注意 Y 方向：`ModelFormRenderer` 在传入前已 `mulPose(ROTATE_X_180)`，盔甲处于原版模型坐标系（Y 向下为正），故 Y 增大 = 远离躯干 = 下段权重高。

**三处渲染共用同一 `skinningConsumer` 包装**，保证材质层、trim、glint 的顶点变换完全一致，不会错位。

### 模组自定义盔甲

`ArmorRenderer.renderArmorSlot()`（`:41-79`）目前硬走 `this.innerModel`/`this.outerModel`（`:30-31`）。接入 `IClientItemExtensions#getHumanoidArmorModel` 取模组提供的 `HumanoidModel`，取到则替代内置模型，取不到则保持原样。后续的层贴图 / trim / glint / 蒙皮流程不变。

> NeoForge 1.21.1 的确切 API 签名实现时核实，不凭记忆。全仓已确认目前无任何消费点（`IClientItemExtensions` 仅在 `client/BBSClientNeoEvents.java:101/110` 用于注册本 mod 自己的物品）。

## 兼容性

| 场景 | 行为 |
|---|---|
| 旧 config（无新字段） | 走命名约定推断；模型无 `*_elbow`/`*_knee` 则退化单骨骼 |
| 用户 fork 的旧 config | 同上，不报错 |
| 非 bends 模型（`steve`/`alex`/`*_3d`） | 无弯曲骨骼 → 下段探测返回 null → 完全走原路径 |
| `items_main`/`fp_*` 等共用 `ArmorSlot` | 不读新字段，行为不变 |
| 模组盔甲 | 新增支持；无 client extension 的盔甲走原路径 |

## 风险

1. **过渡区间默认值需调试** —— 手臂/腿的 `bendStart`/`bendEnd` 像素值是经验值，首版给保守默认，四个 bends 模型的 config 里显式写入便于用户微调
2. **`addVertex` 重载签名** —— 1.21.1 的 `VertexConsumer` 有多个重载，包装类需正确覆盖 `Cube.compile` 实际调用的那个 11 参版本，其余方法委托
3. **法线混合** —— 非等比缩放下法线应走逆转置矩阵；本场景骨骼矩阵通常是刚体变换，直接用矩阵变换法线再归一化即可，若出现光照异常再改
