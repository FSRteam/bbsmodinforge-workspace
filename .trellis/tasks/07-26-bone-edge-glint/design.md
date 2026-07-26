# 技术设计：按骨骼附魔效果

## 数据流

沿用已有的 per-bone 属性通路，不新增任何机制：

```
UIPoseEditor（编辑）→ PoseTransform（存储 + 关键帧插值）
                        ↓ Model.applyPose() 每帧搬运
                     ModelGroup（运行时）
                        ↓ 逐骨骼读取
                  CubicVAORenderer / CubicGlintCubeRenderer → model_glint shader
```

## 数据层

`utils/pose/PoseTransform.java` 新增三个字段，与已有的 `fix` / `color` / `lighting` 并列：

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `glintMode` | `float` | `GLINT_OFF`(0) | 三态：0=关闭 / 1=全表面 / 2=仅边缘 |
| `glintColor` | `Color` | 白 | alpha 通道兼作不透明度 |
| `glintSpeed` | `float` | `1F` | 动画滚动速度，可负（反向）、可零（静止） |

模式用 `float` 而非 `int`，是为了直接搭上 `Transform` 既有的关键帧管线，不必新增一条并行的插值分支。

### 离散值的插值

模式是离散状态，线性插值会在 0→2 的过程中途经 1，闪出错误的效果。两个 `lerp` 重载都改为**阈值阶跃**：

```java
// 2 点混合
this.glintMode = a < 0.5F ? this.glintMode : pose.glintMode;
// 4 点关键帧插值 —— 不进 interp，缓动曲线对离散量无意义
this.glintMode = x < 0.5F ? a1.glintMode : b1.glintMode;
```

因此消费端拿到的永远是精确的 0/1/2，`Model.applyPose()` 直接 `(int)` 强转即可，无需 round。

### 叠加语义（`add()`）

`add()` 服务于影片编辑器的逐骨骼动画覆盖轨道（`FormProperties`），它叠加的是**整份** `PoseTransform` 快照 —— 即便动画师从未碰过附魔设置，快照里也带着 `glintSpeed` 的静息值。

`glintSpeed` 的静息值是 **1 而非 0**，不是加法幺元。直接 `+=` 会让任何一条无关的骨骼动画轨道凭空把附魔加速：

```java
this.glintSpeed += pose.glintSpeed - DEFAULT_GLINT_SPEED;  // 叠加"相对静息值的偏移"
```

`glintMode` 无此问题 —— 它的关闭态恰好是 0，同时兼任加法幺元。

### 旧存档兼容

`fromData` 全部使用带默认值的读取。**`glintSpeed` 必须显式传 `DEFAULT_GLINT_SPEED`（1F）** —— 若照抄 `fix` 用无默认值的 `getFloat`，旧存档会读成 0，附魔动画静止不动。

`isDefault()` 无需改动：三个新字段的默认值就是"未设置"语义，`equals(DEFAULT)` 哨兵自动覆盖，`Pose.toData` 仍只序列化非默认骨骼。

## Shader

新增 `assets/bbs/shaders/core/model_glint.{json,vsh,fsh}`，注册于 `BBSShaders`，顶点格式与 `model` 同为 `NEW_ENTITY` —— **同一份已烘焙的 VAO 可直接被它二次绘制，不复制任何几何数据**。

### 时间源

声明名为 `GameTime` 的 uniform 即可，**无需任何 Java 代码** —— vanilla `ShaderInstance.apply()` 按名字自动填充 `RenderSystem` 的内建状态，与 `model.json` 里 `FogStart`/`ColorModulator` 从无 Java setter 却能生效是同一机制。

`GameTime` 是 24000 tick 游戏日的归一化分数，乘 1200 换算为秒，使滚动速率与这个任意周期解耦。

### 两种模式

单个 `GlintMode` uniform 分支。`vsh` 自行计算视图空间法线与位置：

```glsl
vec4 view = ModelViewMat * vec4(Position, 1.0);
viewPosition = view.xyz;
viewNormal = normalize(NormalMat * Normal);
```

⚠ **不能复用 `model.vsh` 的 `normal` varying** —— 它是 `ProjMat * ModelViewMat * vec4(Normal, 0.0)`，已被投影，丢失了做 Fresnel 所需的几何关系。

- **全表面**：对角条纹随时间滚动，`pow` 锐化成明显高光
- **仅边缘**：额外乘 `pow(1 - |dot(n, viewDir)|, 2.5)`，背向摄像机的表面发亮，形成轮廓感

### 混合与共存

`blend` 为纯加性 `one/one`，不透明度在 fragment 内预乘进颜色（加性混合忽略输出 alpha）。

跟随本体贴图的 cutout（`baseAlpha < 0.1` → `discard`），避免在透明像素处悬空发光。

与已有 glint 天然不冲突：盔甲 glint 走 vanilla `RenderType.armorEntityGlint()`、手持物品走 vanilla `ItemRenderer`，两者都在 vanilla 管线内，与 BBS 自绘模型不共享状态。

## 渲染路径

| 路径 | 接入点 | 机制 |
|---|---|---|
| **VAO**（主路径） | `CubicVAORenderer.renderGroup()` 末尾 | 每骨骼一次 draw call，模式与速度**逐骨骼**设 uniform |
| **CPU 即时**（无 VAO 模型） | `ModelInstance.renderGlintImmediate(..., null)` | 整模型单缓冲，按模式跑两趟；速度取该趟首个骨骼的值 |
| **Hybrid**（部分骨骼焊接/无 VAO） | `renderHybrid()` 末尾，`renderGlintImmediate(..., cpuGroups)` | 仅覆盖回退到 CPU 的那批骨骼 |
| **BOBJ** | — | **不支持**，见下 |

> ⚠ Hybrid 路径易漏：`CubicVAORenderer.renderGroup()` 对焊接中或无 VAO 的骨骼会**提前 return** 到父类，绕过 glint pass。若不在 `renderHybrid()` 末尾补一趟，这些骨骼的附魔会完全不显示。补的这趟必须用 `collectCpuGroups()` 限定范围，否则已随 VAO 画过 glint 的骨骼会被重复绘制。

`ModelVAORenderer.render()` 新增一个可选的 `Consumer<ShaderInstance>` 参数，在标准 uniform 就位后、shader apply 之前设置自定义 uniform —— 这是唯一能让 per-draw uniform 生效的时机。

CPU 路径的 `CubicGlintCubeRenderer` 只输出匹配模式的骨骼，并在写顶点期间临时把 `group.color` 置白后复原，使附魔色不被骨骼本身的染色冲淡（模型串行渲染，无并发观察者）。

### 拾取路径必须跳过

三条路径统一以 `stencilMap != null` 为门槛。拾取 pass 把骨骼索引编码进缓冲，多一次绘制会导致点选到错误的骨骼。

## 已知限制

1. **BOBJ 模型不支持骨骼附魔**。`BOBJModel.applyPose()` 带有原作者注释「lighting/color transform attributes are not supported in this branch」—— 该格式**从来就没有**任何 per-bone 颜色/光照通路，`BOBJModelVAO` 的颜色是整个 mesh 单值。补上需要新建一套 per-bone uniform 数组 + 顶点骨骼索引的数据通路，是独立且量级更大的工作，不在本任务内。
2. **CPU 即时路径的速度按模式共享**，非逐骨骼。该路径只服务无法烘焙 VAO 的模型（shape key / weld），绝大多数模型走 VAO 路径不受影响。
3. **关键帧模式切换是阶跃的**，在两个关键帧正中间跳变，不随缓动曲线渐变 —— 这是离散量的固有性质，也是刻意选择。
