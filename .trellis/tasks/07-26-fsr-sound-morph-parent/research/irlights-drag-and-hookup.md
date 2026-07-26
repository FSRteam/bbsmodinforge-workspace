# IRLights Addon 拖拽编辑 & 渲染器挂钩研究

## 任务 A：拖拽编辑系统 (SpotGuideDrag)

### 1. 拖拽手柄定义与位置

**文件**: `src/client/java/qualet/irlite/client/forms/SpotGuideDrag.java` (L54-57)

```java
public static final String HANDLE_RADIUS = "radius";
public static final String HANDLE_INNER = "inner_radius";
public static final String HANDLE_RANGE = "range";
```

**手柄数量与位置**：
- **HANDLE_RADIUS** (外圆环): 位于圆锥帽部(cap plane), 表示外圆锥的半径对应的圆形手柄
- **HANDLE_INNER** (内圆环): 同样在帽部, 表示内圆锥的半径
- **HANDLE_RANGE** (轴向盘): 位于圆锥顶点到帽部的Z轴上，用于沿轴方向调整范围(range)

这三个手柄通过 stencil picking 框架向 BBS 注册，由 `SpotlightFormRenderer.renderStencilHandles()` 绘制。

---

### 2. 鼠标射线转换成锥体参数 —— 完整数学实现

**关键约定**: `LightGuideRenderer.spotRingZ(range)` 计算帽部Z坐标，是全局唯一的来源（L49-51）:

```java
// LightGuideRenderer.java L49-51
static float spotRingZ(float range)
{
    return Math.max(range, 0.05F);
}
```

拖拽系统通过鼠标射线投影实现参数更新。具体过程：

#### 2.1 坐标系转换 (L264-276)

```java
// SpotGuideDrag.java L265-276
Camera camera = dragCamera;
Area area = dragViewport;

/* Guide-local -> camera-relative "world" of the preview scene. */
Matrix4f localToWorld = new Matrix4f(camera.view).invert().mul(guide);

if (Math.abs(localToWorld.determinant()) < 1e-12F)
{
    return;
}

Matrix4f worldToLocal = new Matrix4f(localToWorld).invert();
```

捕获的 `guide` 矩阵（L96）将导出的 MatrixStack 变换为 Matrix4f：
```java
public static void captureGuideMatrix(SpotlightForm form, MatrixStack stack)
{
    GUIDE_MATRICES.put(form, new Matrix4f(stack.peek().getPositionMatrix()));
}
```

#### 2.2 鼠标射线生成 (L278-286)

```java
/* Mouse ray: origin = camera (0,0,0 camera-relative), direction from the projection. */
Vector3f dirWorld = CameraUtils.getMouseDirection(
    camera.projection, camera.view,
    context.mouseX, context.mouseY,
    area.x, area.y, area.w, area.h
);

Vector3f o = worldToLocal.transformPosition(new Vector3f(0F, 0F, 0F));
Vector3f d = worldToLocal.transformDirection(new Vector3f(dirWorld));
```

射线原点是摄像机(0,0,0), 方向由投影矩阵反解得出, 然后转换到导轨局部空间。

#### 2.3 Range 手柄: Z轴投影 (L314-331)

```java
private static void updateRange(Vector3f o, Vector3f d)
{
    float a = d.lengthSquared();
    float b = d.z;
    float denom = a - b * b;

    /* Ray (anti)parallel to the axis — no stable closest point. */
    if (denom < EPSILON)
    {
        return;
    }

    float oDotD = o.dot(d);
    float axisZ = (a * o.z - b * oDotD) / denom;
    float capSign = Math.signum(LightGuideRenderer.spotRingZ(1F));
    float range = clamp(capSign * axisZ, 0.1F, 128F);

    writeValue(range, dragForm.range);
}
```

**数学原理**:
- 鼠标射线: P(t) = o + d*t
- Z轴（导轨轴）平行于+Z方向
- 求射线上离Z轴最近的点，其Z坐标即为新的 range
- 分子 `a * o.z - b * oDotD`: 分别用参数射线长度平方和Z分量来投影
- `denom = a - b*b` 避免射线平行于Z轴时的除零

#### 2.4 Radius/InnerRadius 手柄: 圆锥角投影 (L339-369)

```java
private static void updateAngle(Vector3f o, Vector3f d, boolean inner)
{
    float range = Math.max(dragForm.range.get(), 0.05F);
    float capZ = LightGuideRenderer.spotRingZ(range);

    if (Math.abs(d.z) < EPSILON)
    {
        return;
    }

    float t = (capZ - o.z) / d.z;

    if (t <= 0F)
    {
        return;
    }

    float hx = o.x + d.x * t;
    float hy = o.y + d.y * t;
    float radial = (float) Math.sqrt(hx * hx + hy * hy);
    float angle = (float) Math.toDegrees(2D * Math.atan2(radial, Math.abs(capZ)));

    if (inner)
    {
        writeValue(clamp(angle, 1F, dragForm.radius.get()), dragForm.innerRadius);
    }
    else
    {
        writeValue(clamp(angle, 1F, 179F), dragForm.radius);
    }
}
```

**数学原理**:
1. 射线与帽部平面的交点:
   - 帽部Z = `spotRingZ(range)` (常数)
   - 射线方程: Z = o.z + d.z * t
   - 交点参数: `t = (capZ - o.z) / d.z`

2. 交点的极坐标半径:
   - 交点: (hx, hy, capZ) = (o.x + d.x*t, o.y + d.y*t, capZ)
   - 半径: `radial = sqrt(hx² + hy²)`

3. 圆锥角（半角）计算:
   - 角度 = 2 * arctan(radial / capZ)  （度数）
   - 对应锥的几何: 如果从顶点看向Z方向，距离为 capZ，半径为 radial 的圆形，则半角为 arctan(radial/capZ)
   - 乘以2是因为 radius 和 innerRadius 字段存储的是完整圆锥角（顶点处的全角）

4. 约束:
   - 内角 ≤ 外角 (inner ≤ radius)
   - 范围: 1° ~ 179° (避免退化的平面或反向锥)

---

### 3. 拖拽手柄 ↔ 字段映射

**文件**: `src/client/java/qualet/irlite/client/forms/SpotGuideDrag.java` (L169-174, L293-300)

```java
// L169-174: 从手柄字符串映射到 Form 的 ValueFloat 属性
BaseValue property = switch (dragHandle)
{
    case HANDLE_RANGE -> dragForm.range;
    case HANDLE_INNER -> dragForm.innerRadius;
    default -> dragForm.radius;
};
```

```java
// L293-300: 在 update 中分派到对应的计算函数
if (HANDLE_RANGE.equals(dragHandle))
{
    updateRange(o, d);
}
else
{
    updateAngle(o, d, HANDLE_INNER.equals(dragHandle));
}
```

**映射表**:
| 手柄名称 | Form 字段 | 更新函数 | 操作 |
|---------|----------|---------|------|
| `HANDLE_RANGE` | `range` | `updateRange()` | 沿Z轴调整范围(距离) |
| `HANDLE_RADIUS` | `radius` | `updateAngle()` (inner=false) | 调整外圆锥角度 |
| `HANDLE_INNER` | `innerRadius` | `updateAngle()` (inner=true) | 调整内圆锥角度 |

---

### 4. 与 LightGuideRenderer.spotRingZ() 的共用几何

**设计原则**: `spotRingZ(range)` 是**单一真实来源** (single source of truth)，所有依赖帽部Z坐标的代码都调用它。

**调用位置**:

1. **可视化绘制** — `LightGuideRenderer.java` L56, L66:
   ```java
   // L54-74: renderSpotlight()
   float r = spotRingZ(range);  // 帽部Z坐标
   // ... 后续根据 r 绘制环和锥体
   ringAtZ(builder, stack, r, outerR, t, color, WIRE_ALPHA);  // 外圆环
   if (inner < outer) {
       ringAtZ(builder, stack, r, innerR, t, color, ...);  // 内圆环
   }
   ```

2. **拾取手柄** — `LightGuideRenderer.java` L197, L212:
   ```java
   // L195-206: renderSpotlightGrabRing()
   float r = spotRingZ(range);
   // 在该Z处渲染 stencil 拾取环
   
   // L210-222: renderSpotlightGrabCap()
   float r = spotRingZ(range);
   // 在该Z处渲染 stencil 拾取盘
   ```

3. **拖拽计算** — `SpotGuideDrag.java` L342, L328:
   ```java
   // L342: updateAngle()
   float capZ = LightGuideRenderer.spotRingZ(range);
   
   // L328: updateRange()
   float capSign = Math.signum(LightGuideRenderer.spotRingZ(1F));
   ```

**保障机制**:
- 若要调整帽部Z的定义，只需修改 `spotRingZ()` 一处
- 所有使用方都自动获得新定义，避免几何不一致

---

### 5. 拾取(Pick)机制：Stencil Framebuffer

**文件**: `SpotGuideDrag.java` (L189-215), `LightGuideRenderer.java` (L185-238)

#### 5.1 Stencil 拾取的原理

Minecraft 使用 stencil framebuffer 实现鼠标拾取。BBS 的框架：
- 在拾取pass中，用**像素颜色** (RGBA) 编码拾取ID
- 鼠标移动到屏幕坐标(x, y)时，读取该像素的颜色 = 拾取结果

#### 5.2 渲染阶段：注册手柄

**文件**: `LightGuideRenderer.java` L195-223

```java
// L195-206: renderSpotlightGrabRing() — 外/内半径手柄
static void renderSpotlightGrabRing(MatrixStack stack, float range, float angleDeg, int stencilIndex)
{
    float r = spotRingZ(range);
    float ringR = coneRadius(r, Math.max(angleDeg, 1F));

    renderStencilTriangles((builder) ->
    {
        stack.push();
        stack.translate(0, 0, r);
        renderCircle(builder, stack, Axis.Z, ringR, grabThickness(r), stencilColor(stencilIndex), 1F);
        stack.pop();
    });
}

// L210-222: renderSpotlightGrabCap() — range 手柄
static void renderSpotlightGrabCap(MatrixStack stack, float range, int stencilIndex)
{
    float r = spotRingZ(range);
    float discR = Math.max(r * 0.10F, 0.05F);

    renderStencilTriangles((builder) ->
    {
        stack.push();
        stack.translate(0, 0, r);
        /* renderCircle with thickness == diameter degenerates into a full disc (rIn = 0). */
        renderCircle(builder, stack, Axis.Z, discR * 0.5F, discR, stencilColor(stencilIndex), 1F);
        stack.pop();
    });
}
```

拾取ID编码 (L231-237):
```java
/** Stencil ID encoded the way StencilFormFramebuffer.pick() decodes it: r | g<<8 | b<<16. */
private static Color stencilColor(int index)
{
    return new Color(
        (index & 0xFF) / 255F,
        ((index >> 8) & 0xFF) / 255F,
        ((index >> 16) & 0xFF) / 255F
    );
}
```

#### 5.3 点击检测与拖拽启动

**文件**: `SpotGuideDrag.java` (L189-215)

```java
private static boolean tryStartWith(Object host, StencilFormFramebuffer stencil, Camera camera, Area viewport, UIContext context)
{
    if (context.mouseButton != 0 || !stencil.hasPicked())
    {
        return false;
    }

    Pair<Form, String> pair = stencil.getPicked();

    if (pair == null || !(pair.a instanceof SpotlightForm spot) || pair.b == null)
    {
        return false;
    }

    if (!isHandle(pair.b) || !GUIDE_MATRICES.containsKey(spot))
    {
        return false;
    }

    dragForm = spot;
    dragHandle = pair.b;
    dragHost = host;
    dragCamera = camera;
    dragViewport = viewport;

    return true;
}
```

**步骤**:
1. 检查左键点击 + stencil buffer 有拾取结果
2. 从 stencil 解析出 `Pair<Form, String>` (Form对象 + 手柄名)
3. 验证手柄名是否为有效的三个手柄之一 (L481-483)
4. 验证该 Form 的导出矩阵已捕获 (GUIDE_MATRICES 中存在)
5. 如果通过，设置拖拽状态并返回 true，消耗点击事件

#### 5.4 渲染设置（无深度测试，透过几何）

**文件**: `LightGuideRenderer.java` (L246-264)

```java
private static void renderStencilTriangles(Consumer<BufferBuilder> consumer)
{
    BufferBuilder builder = Tessellator.getInstance().getBuffer();

    RenderSystem.disableBlend();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);
    RenderSystem.disableDepthTest();
    RenderSystem.setShader(GameRenderer::getPositionColorProgram);

    builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
    consumer.accept(builder);

    BufferRenderer.drawWithGlobalProgram(builder.end());

    RenderSystem.enableDepthTest();
    RenderSystem.depthMask(true);
    RenderSystem.enableCull();
    RenderSystem.enableBlend();
}
```

关键特性:
- `disableDepthTest()`: 拾取手柄永不被遮挡（对Z值敏感的选择）
- `disableCull()`: 无论面朝向，都能拾取（双面）
- 不混合 (disableBlend): 颜色直接是ID

---

## 任务 B：渲染器挂钩 & 选中判定

### 1. FormRenderer 的世界绘制挂钩

#### 1.1 重写方法签名

**文件**: `SpotlightFormRenderer.java` (继承 `AbstractLightFormRenderer`)

```java
public class SpotlightFormRenderer extends AbstractLightFormRenderer<SpotlightForm>
{
    // ... 基本字段 ...

    @Override
    protected void renderGuide(FormRenderingContext context, Color color)
    { /* 见下方 */ }

    @Override
    protected void renderStencilHandles(FormRenderingContext context)
    { /* 见下方 */ }

    @Override
    protected void registerLight(FormRenderingContext context)
    { /* 见下方 */ }
}
```

`AbstractLightFormRenderer<T extends Form>` 定义的三个虚方法签名：

```java
// AbstractLightFormRenderer.java
protected abstract Color lightColor();
protected abstract Icon icon();
protected abstract void renderGuide(FormRenderingContext context, Color color);
protected abstract void renderStencilHandles(FormRenderingContext context);
protected abstract void registerLight(FormRenderingContext context);
```

#### 1.2 在世界中绘制导轨的完整流程

**文件**: `AbstractLightFormRenderer.java` (L47-145, 主方法 `render3D()`)

```java
@Override
protected void render3D(FormRenderingContext context)
{
    boolean editorPreview = context.type == FormRenderType.PREVIEW || context.modelRenderer || context.ui;

    // 1. 不在阴影烘烤时绘制
    if (ShadowBakeState.isBaking())
    {
        return;
    }

    // 2. 库存图标渲染
    if (context.type == FormRenderType.ITEM_INVENTORY && !context.isPicking())
    {
        this.renderItemIcon(context);
        return;
    }

    // 3. 世界路径: 注册灯光 + 绘制导轨
    if (!context.isPicking() && !context.ui && !BBSRendering.isIrisShadowPass()
        && (context.type == FormRenderType.MODEL_BLOCK || context.type == FormRenderType.ENTITY))
    {
        boolean boneAttached = this.form.getParent() instanceof BodyPart part
            && !part.bone.get().isEmpty();

        if (boneAttached || !LightCollector.isHandledByScanner(context))
        {
            this.registerLight(context);
        }

        /* 绘制导轨条件: 全局设置 OR 胶片编辑器选中此 Form */
        if (IrliteConfig.showGuides() || SpotGuideDrag.isFilmSelected(this.form))
        {
            this.renderGuide(context, this.tintedColor(context));
        }

        return;
    }

    // 4. 编辑器预览
    if (editorPreview && !context.isPicking())
    {
        this.renderGuide(context, this.tintedColor(context));
        return;
    }

    // 5. 如果不是拾取pass，到这里就return
    if (!context.isPicking())
    {
        return;
    }

    // 6. 拾取pass: 注册拖拽手柄
    boolean entityPick = context.type == FormRenderType.ENTITY;
    boolean filmPick = entityPick && context.stencilMap.increment && SpotGuideDrag.isReplayEditorActive();

    if (filmPick)
    {
        SpotGuideDrag.markFilmSelected(this.form);
    }

    if (filmPick || (context.modelRenderer && context.stencilMap.increment))
    {
        this.renderStencilHandles(context);
    }

    this.renderPickBox(context);
}
```

#### 1.3 各上下文类型的行为

| 上下文类型 | isPicking | 操作 |
|----------|-----------|------|
| `MODEL_BLOCK` (世界/编辑器块) | false | 注册灯光 + 条件绘制导轨 |
| `ENTITY` (实体) | false | 同上 |
| `PREVIEW` (编辑器预览窗口) | false | 绘制导轨 |
| `ITEM_INVENTORY` (库存图标) | false | 绘制图标 |
| 任何 | true | 绘制 stencil 手柄 + 拾取盒 |

---

### 2. "编辑器选中" 判定：Form 在屏幕上呈现时才显示导轨

#### 2.1 Film Editor 选中标记

**文件**: `SpotGuideDrag.java` (L402-429)

```java
private static final Map<Form, Long> FILM_SELECTED = new WeakHashMap<>();
private static final long FILM_MARK_TTL_MS = 300L;

public static void markFilmSelected(Form form)
{
    Form root = FormUtils.getRoot(form);

    if (root != null)
    {
        FILM_SELECTED.put(root, System.currentTimeMillis());
    }
}

public static boolean isFilmSelected(Form form)
{
    /* Guides only in the replay editor — hide them the instant the user
     * switches to the camera editor or the replay actions timeline, even
     * while the mark is still fresh. */
    if (!isReplayEditorActive())
    {
        return false;
    }

    Form root = FormUtils.getRoot(form);
    Long mark = root == null ? null : FILM_SELECTED.get(root);

    return mark != null && System.currentTimeMillis() - mark < FILM_MARK_TTL_MS;
}
```

**选中判定逻辑**:
1. BBS 的 film picking (stencil pass) **只拾取当前选中的回放** (per-bone picking with `increment=true`)
2. 每帧拾取时，调用 `markFilmSelected(form)` 记录时间戳
3. `isFilmSelected()` 检查:
   - 胶片编辑器必须处于**回放编辑器** (不是相机编辑器/动作时间线)
   - Form 标记必须在最近 300ms 内 (TTL)

#### 2.2 显示导轨的条件

**文件**: `AbstractLightFormRenderer.java` (L94-96)

```java
if (IrliteConfig.showGuides() || SpotGuideDrag.isFilmSelected(this.form))
{
    this.renderGuide(context, this.tintedColor(context));
}
```

**两种显示模式**:
1. **全局开启**: 设置中 `show_guides = true`（始终显示所有灯光导轨）
2. **Film 编辑器**: 只有当前选中的 replay 的灯光 Form 显示导轨

#### 2.3 导轨矩阵捕获

**文件**: `SpotlightFormRenderer.java` (L38-47)

```java
@Override
protected void renderGuide(FormRenderingContext context, Color color)
{
    /* Editor preview and in-world film actors both host draggable handles —
     * capture the guide's local->view matrix wherever the guide is drawn. */
    if (context.modelRenderer || context.type == FormRenderType.ENTITY)
    {
        SpotGuideDrag.captureGuideMatrix(this.form, context.stack);
    }

    LightGuideRenderer.renderSpotlight(context.stack, color, this.form.range.get(), this.form.radius.get(), this.form.innerRadius.get());
}
```

只要导轨被绘制（无论是编辑器预览还是 film actor），就捕获当前的 MatrixStack，供后续拖拽计算使用。

---

### 3. Mixin 的 BBS 挂钩点

#### 3.1 UIFormEditorMixin

**文件**: `src/client/java/qualet/irlite/mixin/client/UIFormEditorMixin.java`

```java
@Mixin(UIFormEditor.class)
public class UIFormEditorMixin
{
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void irlite$registerUi(CallbackInfo ci)
    {
        UIFormEditor.register(PointLightForm.class, UIPointLightForm::new);
        UIFormEditor.register(SpotlightForm.class, UISpotlightForm::new);
    }
}
```

**功能**: 
- **目标**: BBS 的 `UIFormEditor` 类的静态初始化块 (`<clinit>`)
- **时机**: 末尾 (`@At("TAIL")`)
- **操作**: 调用 `UIFormEditor.register()` 将两个新 Form 类型注册到 UI 编辑器的工厂
- **为什么需要**: BBS 的 Form 编辑器在打开时通过工厂反射创建对应 Form 类型的 UI 面板，外部 addon 的 Form 类型需要显式注册才能被识别

#### 3.2 UIPickableFormRendererMixin

**文件**: `src/client/java/qualet/irlite/mixin/client/UIPickableFormRendererMixin.java`

```java
@Mixin(UIPickableFormRenderer.class)
public abstract class UIPickableFormRendererMixin
{
    // 点击时尝试启动拖拽
    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void irlite$grabGuideHandle(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UIPickableFormRenderer self = (UIPickableFormRenderer) (Object) this;

        if (SpotGuideDrag.tryStart(self, context))
        {
            cir.setReturnValue(true);  // 消耗点击事件，阻止 BBS 的 gizmo 处理
        }
    }

    // 释放时结束拖拽
    @Inject(method = "subMouseReleased", at = @At("HEAD"), cancellable = true)
    private void irlite$releaseGuideHandle(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (SpotGuideDrag.mouseReleased())
        {
            cir.setReturnValue(true);  // 消耗释放事件
        }
    }

    // 每帧更新拖拽状态
    @Inject(method = "renderUserModel", at = @At("TAIL"))
    private void irlite$updateGuideDrag(UIContext context, CallbackInfo ci)
    {
        UIPickableFormRenderer self = (UIPickableFormRenderer) (Object) this;

        SpotGuideDrag.update(self, context);
    }
}
```

**三处挂钩**:

| 挂钩点 | 时机 | 目的 | 可消耗 |
|------|------|------|--------|
| `subMouseClicked` (HEAD) | 点击事件开始 | 检测手柄点击，启动拖拽 | 是 (cir.setReturnValue(true)) |
| `subMouseReleased` (HEAD) | 释放事件开始 | 检测拖拽释放，清除状态 | 是 |
| `renderUserModel` (TAIL) | 用户模型渲染末尾 | 每帧计算拖拽的矩阵和鼠标射线 | 否 (CallbackInfo) |

**为什么需要**:
- BBS 的 `UIPickableFormRenderer` 是标准的 Form 编辑器预览渲染器
- 拖拽手柄是额外的交互目标（不是 BBS 的 gizmo/bone），需要在 BBS 的拾取流程**之前**处理
- Mixin 在 `subMouseClicked` 的开头插入，可以抢先消耗点击事件，防止 BBS 处理

---

### 4. 辅助图形的显示范围

#### 4.1 何时显示导轨

**文件**: `AbstractLightFormRenderer.java` (L69-97)

```java
// 世界路径: 条件性绘制导轨
if (!context.isPicking() && !context.ui && !BBSRendering.isIrisShadowPass()
    && (context.type == FormRenderType.MODEL_BLOCK || context.type == FormRenderType.ENTITY))
{
    // 条件: 全局开启 OR film 编辑器选中
    if (IrliteConfig.showGuides() || SpotGuideDrag.isFilmSelected(this.form))
    {
        this.renderGuide(context, this.tintedColor(context));
    }
    return;
}

// 编辑器预览: 无条件绘制
if (editorPreview && !context.isPicking())
{
    this.renderGuide(context, this.tintedColor(context));
    return;
}
```

**显示场景**:
1. **编辑器预览窗口** (UIFormEditor 的 3D 窗口): 始终显示
2. **世界中的 MODEL_BLOCK 和 ENTITY** (实际动画播放/film 编辑器):
   - 如果 `IrliteConfig.showGuides() == true`: 始终显示所有灯光导轨
   - 如果 `showGuides() == false` 但在 film 编辑器: 只显示当前选中 replay 的灯光

#### 4.2 控制开关

**文件**: `IrlightsAddon.java` (L43, 设置注册)

```java
builder.category("presets", Icons.GEAR);
IrliteConfig.showGuides = builder.getBoolean("show_guides", old.getBool("show_guides", false));
```

全局开关存储在 `config/bbs/settings/irlights.json`:
```json
{
  "presets": {
    "show_guides": true,
    ...
  }
}
```

还有额外的 film 编辑器逻辑 (L471-479):
```java
public static boolean isReplayEditorActive()
{
    UIFilmPanel film = filmPanel();

    return film != null
        && film.replayEditor != null
        && film.replayEditor.isVisible()
        && !film.replayEditor.isActionsMode();
}
```

只有在 film 编辑器的回放编辑器处于活跃状态（不是相机编辑器或动作时间线），才允许显示选中 form 的导轨。

---

## 对声音 Form 的移植指导

IRLights addon 的设计完全适用于声音 (Sound) Form 的参数化编辑：

### 对应关系

| IRLights 概念 | 声音 Form 适用性 |
|-------------|-----------------|
| 圆锥角 (radius/innerRadius) | 声音波前扩散角 |
| 范围 (range) | 声音衰减距离/最大半径 |
| 帽部平面几何 | 波前截面投影 |
| 三轴拖拽手柄 | 可直接复用 |
| Stencil 拾取 | 可直接复用 |
| 矩阵捕获 | 完全相同 |

### 可复用的代码结构

1. **形状渲染器** (类似 `LightGuideRenderer`):
   - 将 `renderSpotlight()` 改为 `renderSoundWave()`
   - 参数：中心点、衰减半径、波前角度等
   - 使用相同的 `renderCircle()` 三角形生成

2. **拖拽逻辑** (完全复用 `SpotGuideDrag` 模式):
   - 三个手柄的数学原理相同
   - 射线投影平面只需改变帽部Z的计算方式

3. **UI 面板** (参考 `UISpotlightFormPanel`):
   - 相同的 UITrackpad 和 UIColor 控件
   - 相同的 IrliteFormSections 分组逻辑

4. **Mixin 注册** (相同的 `UIFormEditorMixin` 模式):
   - 一个 @Inject 在 UIFormEditor.<clinit> 末尾
   - 调用 `UIFormEditor.register(SoundForm.class, UISoundForm::new)`

---

## 移植注意事项

该 addon 是 **Fabric + 外部 addon + Mixin 形态**，而 BBS 声音系统是 **NeoForge 版 BBS 主仓内新增 Form**。需要调整的地方：

### 1. 无需 Mixin

**当前做法** (IRLights):
- `UIFormEditorMixin`: 在 BBS 的 UIFormEditor.<clinit> 中注册自定义 UI
- `UIPickableFormRendererMixin`: 在 BBS 的点击/渲染流程中插入拖拽逻辑

**NeoForge 版 BBS 内新增**:
- Form 类型直接在 BBS 源码中定义
- UI 类型直接在 BBS 源码中注册 (无需 Mixin)
- 点击/渲染流程: 在 BBS 的 FormRenderer 子类中重写虚方法，无需 Mixin

**迁移方案**:
```java
// 不需要 Mixin，直接在 BBS 源码的 Form 注册表中添加
FormRegistry.register(SoundForm.class, () -> new SoundFormRenderer(form));

// 不需要 Mixin 的拖拽挂钩，在 BBS 的 UIPickableFormRenderer 中直接调用
// (或在 SoundFormRenderer 的 render3D() 中处理)
```

### 2. 坐标系与渲染 API

IRLights 使用:
- `BufferBuilder` + `Tessellator` (底层 Minecraft 渲染)
- `RenderSystem` 状态设置
- MatrixStack (已弃用，但 Fabric 1.20.1 仍支持)

NeoForge 1.21.1:
- **MatrixStack 已完全移除**，改用 `PoseStack` 或 JOML 矩阵
- `VertexConsumer` 的 API 可能略有变化
- `RenderSystem` 调用方式相同

**迁移工作**:
```java
// Fabric 1.20.1
void render(MatrixStack stack) {
    builder.vertex(matrix, x, y, z).color(...).next();
}

// NeoForge 1.21.1
void render(PoseStack poseStack) {
    builder.vertex(matrix, x, y, z).color(...).next();
    // 或使用 JOML Vector3f / Matrix4f 直接构建
}
```

### 3. 配置系统与持久化

IRLights:
- 使用 BBS 的 `SettingsBuilder` 注册配置项
- 配置存储在 `bbs/settings/irlights.json`

NeoForge 版 BBS 声音:
- 同样使用 BBS 的 SettingsBuilder (相同的系统)
- 配置项存储位置相同

**无需改动**

### 4. Form 基类继承

IRLights:
```java
public class SpotlightForm extends Form {
    public final ValueFloat range = new ValueFloat(...);
    // ...
}
```

NeoForge 版 BBS:
- 完全相同的继承关系和字段定义方式

**无需改动**

### 5. Mixin 目标的主仓化

由于声音 Form 在 BBS 主仓内，不需要外部 Mixin，可以直接在:
- `FormRegistry` 中注册 Form 类型 (如有)
- `UIFormEditor` 中注册 UI 类型 (如有)
- `AbstractLightFormRenderer` 的子类中实现渲染逻辑

这样**完全避免了 Mixin 的复杂性和脆弱性**。

### 6. 关键文件清单（需新建或改写）

对应 IRLights 的文件，在 BBS 主仓中的新路径：

| IRLights 路径 | BBS 主仓路径 | 是否可复用 |
|-------------|------------|----------|
| `forms/SpotlightForm.java` | `src/main/java/mchorse/bbs_mod/forms/forms/SoundForm.java` | 改 |
| `forms/PointLightForm.java` | (不需要单独的点声音) | × |
| `client/forms/LightGuideRenderer.java` | `src/client/java/mchorse/bbs_mod/client/forms/SoundGuideRenderer.java` | 改 |
| `client/forms/SpotGuideDrag.java` | `src/client/java/mchorse/bbs_mod/client/forms/SoundGuideDrag.java` | **80% 可复用** |
| `client/forms/SpotlightFormRenderer.java` | `src/client/java/mchorse/bbs_mod/client/forms/SoundFormRenderer.java` | 改 |
| `client/forms/AbstractLightFormRenderer.java` | 既有基类，无需重复 | × |
| `client/ui/forms/.../UISpotlightFormPanel.java` | `src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/UISoundFormPanel.java` | 改 |
| `mixin/...Mixin.java` | (无需 Mixin) | × |

**可复用百分比**: ~75% (矩阵数学、拖拽逻辑、UI 模式框架完全通用，只需调整参数名和圆锥→波前的几何定义)

