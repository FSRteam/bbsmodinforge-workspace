# 参考实现:bbs-irlights-addon(IRLite)

- **仓库**:https://github.com/quaIett/bbs-irlights-addon —— **公开可访问**,默认分支 **`master`**(用 `main` 会 404)
- **抓取方式**:`gh api repos/quaIett/bbs-irlights-addon/contents/<path>?ref=master --jq '.content' | base64 -d`
- **形态**:Fabric / MC 1.20.1–1.21.1 的**外部 addon**,MIT 许可,靠 Mixin 挂进 BBS
- **本文内容全部为实抓源码**,非推断。拖拽编辑与渲染器挂钩部分见 `irlights-drag-and-hookup.md`

> 首轮曾有调研 agent 误判"仓库不可访问"并据 Modrinth 页面推断了一份类结构,那份内容已作废删除。

## 为什么这个 addon 是高价值参考

它做的事和我们高度同构:给 BBS 加两个带**形状参数**的 Form —— 点光源(球)与聚光灯(**圆锥**),都要在世界里可视化形状、在面板里编辑参数、参数能 K 帧。把"光"换成"声",结构几乎一一对应。

| irlights | 我们 |
|---|---|
| `PointLightForm`(球,radius) | 圆形声音 Form(球,radius) |
| `SpotlightForm`(锥,range + 内外角) | 圆锥声音 Form(锥,锥高 + 内外角) |
| `LightGuideRenderer`(球/锥线框) | 声音传播范围可视化 |
| `SpotGuideDrag`(锥体拖拽编辑) | 锥体拖拽编辑 |
| `UISpotlightFormPanel` | 声音参数面板 |

## 1. 形状参数建模

### SpotlightForm(圆锥)—— 最重要的参考

`src/main/java/qualet/irlite/forms/SpotlightForm.java`

```java
public class SpotlightForm extends Form
{
    public static final Link FORM_ID = new Link("irlite", "spotlight");

    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueFloat intensity = new ValueFloat("intensity", 1F, 0F, 20F);
    public final ValueFloat range = new ValueFloat("range", 12F, 0.1F, 128F);
    public final ValueFloat radius = new ValueFloat("radius", 35F, 1F, 179F);
    public final ValueFloat innerRadius = new ValueFloat("inner_radius", 25F, 1F, 179F);
    public final ValueFloat beamStrength = new ValueFloat("beam_strength", 1F, 0F, 50F);
    public final ValueFloat anisotropy = new ValueFloat("anisotropy", 0.4F, -0.95F, 0.95F);
    /* ... vlDensity / bulbSize / entitiesOnly / blocksOnly / shadows ... */

    public SpotlightForm()
    {
        this.add(this.color);
        this.add(this.intensity);
        this.add(this.range);
        /* ... 每个字段都 add ... */
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Spotlight";
    }
}
```

**锥体三参数**(直接对应我们的需求):

| 字段 | 语义 | 默认 | 范围 |
|---|---|---|---|
| `range` | **锥高 = 最远距离** | 12 | 0.1 – 128 |
| `radius` | **外锥角(度)** | 35 | 1 – 179 |
| `inner_radius` | **内锥角(度)** | 25 | 1 – 179 |

**没有 direction 字段** —— 朝向复用 `Form` 基类的 `ValueTransform transform`。

### PointLightForm(球)

`src/main/java/qualet/irlite/forms/PointLightForm.java`

```java
public static final Link FORM_ID = new Link("irlite", "point_light");
public final ValueFloat radius = new ValueFloat("radius", 6F, 0.1F, 64F);
```

球形只多一个 `radius`,其余字段与聚光灯共享 —— 印证了「共享基类 + 形状差异」的拆分是合理的。

### 关键印证:Value* 字段自动可 K 帧

`SpotlightForm` 中 gobo 字段的原文注释:

> *"All four are BBS Value\* so they keyframe in the film editor (e.g. spin the gobo by animating the rotation)."*

这独立印证了我们核验出的结论(`ValueBoolean` / `ValueLink` / `BaseValueNumber` 均继承 `BaseKeyframeFactoryValue`):**声明字段即获得关键帧轨道,无需任何额外注册**。

## 2. 世界内可视化(几何算法可直接照搬)

`src/client/java/qualet/irlite/client/forms/LightGuideRenderer.java`

```java
private static final int CIRCLE_SEGMENTS = 48;
private static final float WIRE_ALPHA = 0.92F;
private static final float AXIS_ALPHA = 0.75F;

/* 球:三个正交平面的圆 + 三条轴线 */
public static void renderPointLight(MatrixStack stack, Color color, float radius)
{
    float r = Math.max(radius, 0.05F);
    float t = clamp(r * 0.0025F, 0.002F, 0.009F);   // 线宽随尺寸自适应

    renderTriangles((builder) ->
    {
        renderCircle(builder, stack, Axis.X, r, t, color, WIRE_ALPHA);
        renderCircle(builder, stack, Axis.Y, r, t, color, WIRE_ALPHA);
        renderCircle(builder, stack, Axis.Z, r, t, color, WIRE_ALPHA);

        line(builder, stack, -r, 0, 0, r, 0, 0, t, color, AXIS_ALPHA);
        line(builder, stack, 0, -r, 0, 0, r, 0, t, color, AXIS_ALPHA);
        line(builder, stack, 0, 0, -r, 0, 0, r, t, color, AXIS_ALPHA);
    });
}

/**
 * Z of the spotlight cap plane — the single source of truth shared by the
 * visible rings, the pick handles and the drag math in SpotGuideDrag.
 * Flip the sign here and everything follows.
 */
static float spotRingZ(float range)
{
    return Math.max(range, 0.05F);
}

/* 锥:中轴线 + 4 条母线 + 外锥底环 +(可选)内锥环 */
public static void renderSpotlight(MatrixStack stack, Color color, float range, float outerAngle, float innerAngle)
{
    float r = spotRingZ(range);
    float outer = Math.max(outerAngle, 1F);
    float inner = clamp(innerAngle, 1F, outer);      // 内锥不得超过外锥
    float outerR = coneRadius(r, outer);
    float t = clamp(r * 0.0020F, 0.002F, 0.009F);

    renderTriangles((builder) ->
    {
        line(builder, stack, 0, 0, 0, 0, 0, -r, t, color, AXIS_ALPHA);   // 中轴
        coneWire(builder, stack, -r, outerR, t, color, WIRE_ALPHA);       // 4 条母线
        ringAtZ(builder, stack, r, outerR, t, color, WIRE_ALPHA);         // 外锥底环

        if (inner < outer)
        {
            float innerR = coneRadius(r, inner);
            ringAtZ(builder, stack, r, innerR, t, color, AXIS_ALPHA * 0.7F);
        }
    });
}

/* 4 条从顶点射向底沿的母线 */
private static void coneWire(BufferBuilder builder, MatrixStack stack, float capZ, float radius, float t, Color color, float alpha)
{
    line(builder, stack, 0, 0, 0,  radius, 0, capZ, t, color, alpha);
    line(builder, stack, 0, 0, 0, -radius, 0, capZ, t, color, alpha);
    line(builder, stack, 0, 0, 0, 0,  radius, capZ, t, color, alpha);
    line(builder, stack, 0, 0, 0, 0, -radius, capZ, t, color, alpha);
}
```

**渲染状态**(辅助图形穿墙可见的正确做法):

```java
RenderSystem.enableBlend();
RenderSystem.defaultBlendFunc();
RenderSystem.disableCull();
RenderSystem.depthMask(false);
RenderSystem.disableDepthTest();
RenderSystem.setShader(GameRenderer::getPositionColorProgram);
builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
/* ... 绘制 ... */
BufferRenderer.drawWithGlobalProgram(builder.end());
RenderSystem.enableDepthTest();
RenderSystem.depthMask(true);
RenderSystem.enableCull();
```

注意线是用 **TRIANGLES 画的带宽度的四边形**(`line(..., t, ...)` 的 `t` 是线宽),不是 `GL_LINES` —— 这样线宽可控、远近一致。

## 3. 编辑面板

`src/client/java/qualet/irlite/client/ui/forms/editors/panels/UISpotlightFormPanel.java`

```java
public class UISpotlightFormPanel extends UIFormPanel<SpotlightForm>
{
    public UIColor color;
    public UITrackpad intensity, range, radius, innerRadius, ...;
    public UIToggle entitiesOnly, blocksOnly, shadows;
    public UIButton cookiePick;

    public UISpotlightFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.intensity   = IrliteTrackpads.create((v) -> this.form.intensity.set(v.floatValue())).limit(0, 20);
        this.range       = IrliteTrackpads.create((v) -> this.form.range.set(v.floatValue())).limit(0.1, 128);
        this.radius      = IrliteTrackpads.create((v) -> this.form.radius.set(v.floatValue())).limit(1, 179);
        this.innerRadius = IrliteTrackpads.create((v) -> this.form.innerRadius.set(v.floatValue())).limit(1, 179);

        /* 互斥开关:打开一个就关掉另一个,同时同步 UI 状态 */
        this.entitiesOnly = new UIToggle(IKey.constant("Entities only"), (b) -> {
            this.form.entitiesOnly.set(b.getValue());
            if (b.getValue())
            {
                this.form.blocksOnly.set(false);
                this.blocksOnly.setValue(false);
            }
        });
        /* blocksOnly 对称处理 */
    }
}
```

用到的 BBS UI 控件(全部来自本仓库,可直接复用):

| 控件 | 包 | 用途 |
|---|---|---|
| `UITrackpad` | `ui.framework.elements.input` | 数值输入,`.limit(min, max)` 限幅 |
| `UIToggle` | `ui.framework.elements.buttons` | 布尔开关 |
| `UIColor` | `ui.framework.elements.input` | 颜色,`.withAlpha()` 带透明度 |
| `UIButton` | `ui.framework.elements.buttons` | 触发资源选择器 |
| `UITexturePicker` | `ui.framework.elements.input` | 纹理选择(我们对应用 `UISoundOverlayPanel` 选音频) |

**`IrliteTrackpads.create(callback).limit(min, max)`** 是它自己封的 trackpad 工厂,统一了步进/精度等设置。我们可以照做一个 `SoundTrackpads`,避免每个参数重复配置。

## 4. 对声音 Form 的可借鉴点

| # | 借鉴点 | 落地到 |
|---|---|---|
| B1 | 锥体用 `range`(锥高)+ `radius`(外角)+ `inner_radius`(内角)三参数建模,角度用**度**存储 | 子任务 3 字段设计;命名对齐可降低用户迁移成本 |
| B2 | 朝向不设独立字段,复用 `Form.transform` 的旋转 | 父任务 `design.md` §5(已据此修正原设计) |
| B3 | 球用「三正交圆 + 三轴线」而非经纬线球 —— 更轻、视觉更清晰 | 子任务 3 可视化 |
| B4 | 锥用「中轴 + 4 母线 + 底环 + 内锥环」 | 子任务 3 可视化 |
| B5 | **单一真相源**:`spotRingZ(range)` 同时供给可视化 / 拾取 / 拖拽,"改一处全部跟随" | 我们让**可视化与声学换算共用同一几何函数**,保证"看到的"与"听到的"一致 |
| B6 | 线宽随尺寸自适应 `clamp(r * 0.002, 0.002, 0.009)` | 大范围声源不会线太粗 |
| B7 | 辅助图形渲染态:`disableCull` + `depthMask(false)` + `disableDepthTest` | 穿墙可见 |
| B8 | 内锥角自动 clamp 到不超过外锥角 | 避免非法参数组合,声学换算里同样需要 |
| B9 | trackpad 工厂统一配置 | 声音面板参数多,更需要 |
| B10 | 互斥开关在回调里双向同步 UI 与数据 | 我们的衰减模型选择等可参考 |

## 5. 移植注意事项(不能直接照搬的部分)

| 差异 | 影响 |
|---|---|
| **Fabric → NeoForge** | 它在 `fabric.mod.json` 声明 `bbs-addon` 入口;我们是**主仓内直接新增 Form**,走 `BBSMod.java` 的 `FormArchitect.register(...)`,不需要 addon 入口 |
| **外部 addon → 主仓内建** | 它用 `UIFormEditorMixin` / `UIPickableFormRendererMixin` 等 Mixin 挂进 BBS 编辑器;我们**直接改 `UIFormEditor.java:133-144` 的注册块即可,不需要任何 Mixin** |
| **MC 1.20.1 → 1.21.1 渲染 API** | 几何算法照搬,渲染 API 须逐一映射(见下) |
| **命名空间** | 它用 `new Link("irlite", "spotlight")`;我们用 `Link.bbs("sound_cone")` |

渲染 API 映射表:

| 1.20.1(参考实现) | 1.21.1(本仓库,以 `Draw.java` 为准) |
|---|---|
| `MatrixStack` | `PoseStack` |
| `Tessellator.getInstance().getBuffer()` + `builder.begin(mode, fmt)` | `Tesselator.getInstance().begin(mode, fmt)` |
| `VertexFormats.POSITION_COLOR` | `DefaultVertexFormat.POSITION_COLOR` |
| `VertexFormat.DrawMode.TRIANGLES` | `VertexFormat.Mode.TRIANGLES` |
| `BufferRenderer.drawWithGlobalProgram(builder.end())` | `BufferUploader.drawWithShader(builder.buildOrThrow())` |
| `GameRenderer::getPositionColorProgram` | `GameRenderer::getPositionColorShader` |
| `stack.peek().getPositionMatrix()` | `stack.last().pose()` |
| `builder.vertex(m, x, y, z).color(...)` | `builder.addVertex(m, x, y, z).setColor(...)` |
