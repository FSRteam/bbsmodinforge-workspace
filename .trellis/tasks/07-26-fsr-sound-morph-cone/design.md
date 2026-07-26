# 技术设计:圆锥声音 Form 与可视化

> 2026-07-27 回归扩展：共享的复合轨道、试听所有权、方块/实体反射和穿透矩阵由兄弟任务 `07-26-fsr-sound-morph-sphere/design.md` §6 定义。圆锥形状快照使用现有几何真相源并继续强制 `innerAngle <= outerAngle`；反射虚拟声源仍须经过圆锥方向增益过滤。

前置阅读:父任务 `design.md` §3.2/§6、子任务 1 `design.md`(基类)、子任务 2 `design.md`(注册与面板模式)、`research/irlights-addon-reference.md` §2、`research/irlights-drag-and-hookup.md`。

## 1. 圆锥 Form

```java
public class SoundConeForm extends AbstractSoundForm
{
    public final ValueFloat range       = new ValueFloat("range", 12F, 0.1F, 128F);
    public final ValueFloat outerAngle  = new ValueFloat("radius", 35F, 1F, 179F);
    public final ValueFloat innerAngle  = new ValueFloat("inner_radius", 25F, 1F, 179F);
    public final ValueFloat outerGain   = new ValueFloat("outer_gain", 0.2F, 0F, 1F);

    @Override
    public float shapeGain(float dx, float dy, float dz, float distance)
    {
        /* 锥轴取自 Form.transform 的旋转;cosAngle = 轴 · 指向监听者的单位向量 */
        return SoundAcoustics.coneGain(cosAngle, innerDeg, outerDeg, this.outerGain.get());
    }
}
```

字段名与范围对齐参考实现 `SpotlightForm`(`range` 12/0.1–128、`radius` 35/1–179、`inner_radius` 25/1–179),降低迁移成本。**内锥 clamp**(R1.5)在换算与绘制两处都做,与参考实现 `clamp(innerAngle, 1F, outer)` 一致。

`range` 是锥形下 `maxDistance` 的**唯一真相源**(与子任务 2 中 `radius` 的角色对称),保证"锥高 = 最远距离"这条硬语义(R1.3)。

## 2. 几何真相源(R2.3 / R4.3 的实现保证)

参考实现用 `spotRingZ(range)` 作为单一真相源,被可视化、拾取手柄、拖拽换算三处共用,"改一处符号全部跟随"。我们把这个原则**再推进一步**:让**声学换算也用同一份几何**。

```
SoundConeGeometry  (main,纯函数,无渲染依赖)
├── capDistance(range)                  锥底平面距离
├── coneRadius(capDistance, angleDeg)   底面半径
└── cosHalfAngle(angleDeg)              半角余弦
```

- 声学:`SoundAcoustics.coneGain` 与距离截断读它
- 可视化:线框顶点生成读它
- 拖拽:手柄位置与反算读它

这样"看到的边界"与"听到的边界"在代码层面就是同一个数,而不是靠人工对齐 —— 这正是父任务跨子任务验收要验的点。

## 3. 可视化

### 3.1 绘制原语(新增,client)

```
new/src/client/java/mchorse/bbs_mod/forms/renderers/sound/SoundGuideRenderer.java
├── renderSphere(stack, color, radius)          三正交圆 + 三轴线
├── renderCone(stack, color, range, outer, inner)  中轴 + 母线 + 外环 + 内环
├── renderRing(stack, color, z, radius, alpha)
└── renderIntensityBands(stack, form, ...)      强度分层(R3)
```

几何照搬参考实现(`research/irlights-addon-reference.md` §2 有完整代码),**渲染 API 按同文件 §5 的映射表改写为 1.21.1**,以本仓库 `Draw.java` 的写法为准。

要点:
- 线用**带宽度的三角形**画,不用 `GL_LINES`,线宽可控且远近一致;线宽 `clamp(r * 0.002, 0.002, 0.009)` 自适应(R2.4)。
- 圆的顶点用预计算 sin/cos 表(照 `Batcher2D` 的 `ROUNDED_RECT_ARC_SIN/COS`),满足父任务 C3 零分配。
- 渲染态 `disableCull` + `depthMask(false)` + `disableDepthTest`,穿墙可见(R2.4)。

### 3.2 强度分层(R3)

在 `refDistance`、`-6dB`、`-12dB`、`maxDistance` 四处画环(锥形则画锥截面环)。各档距离由**实际衰减公式反解**得到:

```
d(-6dB)  = 使 distanceGain(...) == 0.5  的距离
d(-12dB) = 使 distanceGain(...) == 0.25 的距离
```

三种衰减模型各自反解(反比/线性可解析反解;指数同样可解析)。**禁止**为了省事画等距同心环 —— 那会让可视化与听感脱节(R3.2)。

## 4. 选中判定与渲染挂钩

参考实现的做法(`research/irlights-drag-and-hookup.md` 任务 B):BBS 的 film picking(stencil pass)只拾取**当前选中的 replay**,因此在拾取时给 Form 打时间戳,渲染时查 TTL 即可判定选中:

```java
private static final Map<Form, Long> SELECTED = new WeakHashMap<>();
private static final long MARK_TTL_MS = 300L;
```

并叠加"必须处于回放编辑器"的判断,使用户切到相机编辑器时辅助图形立即隐藏。

绘制入口挂在 `FormRenderer` 子类中,在 `context.modelRenderer || context.type == FormRenderType.ENTITY` 时绘制并捕获矩阵供拖拽使用。

显示条件(R5):

```java
if (globalShowGuides || isSelected(this.form))
{
    renderGuide(context, color);
}
```

**与参考实现的差异**:它靠 `UIFormEditorMixin` / `UIPickableFormRendererMixin` 挂钩;我们在主仓内,**直接改对应类即可,不需要任何 Mixin**。

## 5. 拖拽编辑(R4)

三个手柄,映射照参考实现:

| 手柄 | 字段 | 操作 |
|---|---|---|
| 锥高 | `range` | 沿锥轴拖动 |
| 外锥角 | `outerAngle` | 拖底面外环 |
| 内锥角 | `innerAngle` | 拖底面内环 |

球形只有一个半径手柄。

拾取走 stencil framebuffer(BBS 既有机制,参考实现已验证可用)。拖拽换算需要绘制时捕获的 local→view 矩阵,把鼠标射线反投影到锥体局部坐标系。手柄位置与反算**全部读 §2 的几何真相源**。

双向同步(R4.2):拖拽写 `ValueFloat` → 面板 `startEdit` 路径读同一字段,天然一致;需确认面板在拖拽期间也刷新显示。

## 6. 风险

| 风险 | 应对 |
|---|---|
| 拖拽是本任务最复杂的部分,涉及矩阵反投影 | 先交付可视化 + 面板数值编辑(已满足"可编辑"),拖拽作为独立阶段,失败可单独回滚而不影响其他交付物 |
| 强度分层反解在极端参数下可能无解 | 反解前先判定单调区间,无解时跳过该档而非画错 |
| 1.20.1 → 1.21.1 渲染 API 映射出错 | 每个原语先单独编译验证,再接入 |

## 7. 回滚点

- 可视化、拖拽、Form 本体三者独立:可视化关掉全局开关即隐身;拖拽单独 revert 不影响可视化;Form 注册三行注释掉即完全移除。
