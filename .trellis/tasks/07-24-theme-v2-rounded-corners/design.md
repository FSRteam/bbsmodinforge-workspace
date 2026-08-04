# rounded-corners 技术设计

依赖阅读:父任务 `design.md` 研究报告 §3.1(坐标与机制)。参考源:`temp/bbs-refreshed-2.0-public-1.20.1-decompiled/src/mchorse/bbs_mod/ui/framework/elements/utils/Batcher2D.java:129-299`。

## 1. 映射转译对照表(参考源 1.20.1 Fabric intermediary → 本仓库 NeoForge 1.21.1 官方映射)

| 参考源(intermediary) | 本仓库写法(以现有 `Batcher2D.fillRect` 为准) |
|---|---|
| `class_287` | `com.mojang.blaze3d.vertex.BufferBuilder` |
| `class_289.method_1348().method_1349()` + `method_1328(mode, format)` | `Tesselator.getInstance().begin(VertexFormat.Mode.X, DefaultVertexFormat.Y)` |
| `builder.method_22918(matrix, x, y, z)` | `builder.addVertex(matrix4f, x, y, z)` |
| `.method_39415(color)` | `.setColor(color)` |
| `.method_1344()`(endVertex) | 1.21 链式 API 无需调用,删 |
| `class_286.method_43433(builder.method_1326())` | `BufferUploader.drawWithShader(builder.buildOrThrow())`(对照本仓库 box() 现行收尾写法) |
| `class_293.class_5596.field_27382 / field_27379` | `VertexFormat.Mode.QUADS / TRIANGLES` |
| `class_290.field_1576 / field_1575` | `DefaultVertexFormat.POSITION_COLOR / POSITION_TEX_COLOR`(注意:参考用 POSITION_TEX + shader color,本仓库若用 POSITION_TEX_COLOR 则颜色进顶点,取本仓库 texturedBox 现行格式为准) |
| `RenderSystem.setShader(class_757::method_34540 / method_34543)` | `CoreShaders.POSITION_COLOR / POSITION_TEX_COLOR` 对应设置(取本仓库现行写法) |
| `context.method_51448()...` | 本仓库 `this.context` 的 pose 获取现行写法 |
| `context.method_51452()` | `this.context.flush()` 等价物(本仓库 `flushBeforeTesselator()` 惯例:裸 Tesselator 前必须先 flush GuiGraphics 缓冲 —— **参考源没有这个约束,本仓库必须加**) |

原则:**几何逻辑照抄参考源,顶点/shader API 全部以本仓库 Batcher2D 既有方法(fillRect/dropShadow/texturedBox)的写法为准**,不引入新渲染惯例。

## 2. 原语规格

```java
public void roundedBox(float x, float y, float w, float h, float radius, int color)
public void roundedFrame(float x, float y, float w, float h, float radius, float inset, int borderColor, int fillColor)
public void filledCircle(float cx, float cy, float radius, int color, int segments)
```

- 结构(参考源逐行对应):`clampRoundedRectRadius`(钳 `min(r, w/2, h/2)`);`r < 0.5` → 直接走既有 `box()`(**半径 0 逐位等价的结构保证**);中心+四边条 = 纯色 QUADS/TRIANGLES;四角 = mask 纹理三角扇(GL_LINEAR 采样,POSITION_TEX 系格式);mask 加载失败 → 纯几何三角扇退化(不崩、无抗锯齿)。
- mask UV 约定照参考源 `fillRoundedCornerFanMask` 的四角 corner 索引展开(0=左上,顺时针),扇心在圆角圆心。
- `filledCircle` 参考源实现在同文件(UIToggle.java:144 调用形态 `filledCircle(cx, cy, 5F, color, 28)`),同样 mask(`filled_circle_mask`)+ 几何退化双路径。

## 3. mask 资源

直接拷贝参考包(不重制):

- `temp/.../resources/assets/bbs/assets/textures/gui/rounded_rect_mask.png` → `new/src/client/resources/assets/bbs/assets/textures/gui/rounded_rect_mask.png`
- 同目录 `filled_circle_mask.png` 同拷。
- 资源常量放 `new/src/client/java/mchorse/bbs_mod/ui/utils/UIArt.java`(新建,照参考源 `ui/utils/UIArt.java:16-17`);纹理获取走本仓库纹理管理器现行 API,校验失败返回 null(触发几何退化)。
- 主题可用同名 assets 覆盖(External 先于 Internal 的 v1 语义天然支持,无需代码)。

## 4. 主题参数

- `UITheme` 增加 `cornerChrome / cornerPanel / cornerWidget`(int px,全部缺省 0);theme.json:

```json
"style": { "corner_radius": { "chrome": 8, "panel": 6, "widget": 4 } }
```

- ThemeParser:嵌套对象解析,坏值/缺失回退 0,钳 [0, 16];继承链正常参与(Builder 拷贝)。
- 取用函数进 `BBSSettings` 语义函数族(如 `cornerChrome()` 直读 `ThemeManager.current()`,与 v1 取色函数同模式)。

## 5. 首批控件接入(全部 `new/src/client/java/mchorse/bbs_mod/ui/` 下)

| 文件 | 接入点 | 半径级 |
|---|---|---|
| `framework/elements/overlay/UIOverlayPanel.java` | 面板背景填充(现为 box)→ `roundedFrame` | chrome |
| `framework/elements/context/UIContextMenu.java` | 菜单背景 → `roundedFrame` | chrome |
| `framework/elements/buttons/UIButton.java` | 背景填充 → `roundedBox` | widget |
| `framework/elements/buttons/UIIcon.java` | hover 背景 → `roundedBox` | widget |
| `framework/elements/input/text/UITextbox.java`(及 `UIBaseTextbox` 共用绘制处) | 边框+填充 → `roundedFrame` | widget |
| `framework/elements/buttons/UIToggle.java` | widget 半径>0 时切换为 macOS 样式:圆角轨道 `roundedBox` + `filledCircle` 滑块(样板:参考源 UIToggle.java:120-148);半径 0 保持 v1 方块样式 | widget |
| `framework/elements/input/list/UIList.java` | 选中态高亮 → `roundedBox` | panel |
| `framework/elements/buttons/UICirculate.java` | 按钮背景;半径 0 保留 bevel | widget |
| `framework/elements/input/UIKeybind.java` | 普通/录入态背景 + 禁用遮罩 | widget |
| `framework/elements/input/UITrackpad.java` | 背景、scrub 填充、左右增减区 | widget |
| `framework/elements/input/UISliderTrackpad.java` | 背景、进度、marker、handle | widget |
| `framework/elements/input/UIColor.java` | 边框、颜色填充、hover 层 | widget |
| `framework/elements/UIElement.java` | 共用禁用遮罩 | widget |

接入模式统一为:`int r = BBSSettings.cornerX(); if (r > 0) roundedBox(...) else 原 box 调用`,或直接调 roundedBox(其内部 r<0.5 走 box)——**优先后者**(零分支污染),前者仅用于 UIToggle 这种整体样式切换。

## 6. 等价性与风险

- 半径 0:roundedBox 内部 `r<0.5 → box()` 结构保证逐位等价;UIToggle 样式切换有显式 `r>0` 分支。
- 每控件一个提交批次,独立回滚。
- 性能:扇顶点数固定(参考源约每角 ~8-16 三角形),每帧弹层数量级 <10 个圆角面,无热路径担忧;禁止在绘制中分配(顶点直接写 builder)。
