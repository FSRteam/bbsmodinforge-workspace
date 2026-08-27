# 阶段三:多视口世界渲染管线(FSR 主工程改造)

> 独立任务。与 bbs-camera-addon 插件并行推进。本任务改的是 `new/` 主工程,不是插件。
> 创建:2026-08-23。来源:bbs-camera-addon 插件阶段一/二的调研结论。

## 1. 为什么要单独开

bbs-camera-addon 插件已能离屏渲染多台摄像机的实时画面(每台独立 `renderLevel` 进独立
`TextureTarget`),但这只是"浮层"——监视器画在影片编辑器**上方**,没有整合进编辑器布局,
也没有 BBS 原生的那排编辑器控件(录制视频/回放/洋葱皮/运动路径等)。

用户要的是:影片编辑器布局里多开几个跟主预览一模一样的视口(`UIFilmPreview`),每个绑定一台
摄像机,各自带完整按钮排。调研确认**BBS 现有架构是单视口的**,做不到原样复制,必须改主工程。

## 2. 单视口管线的三处硬限制(改造点)

### 2.1 BBSRendering:单全局帧缓冲 + 单全局纹理

- `BBSRendering` 有**一个**静态 `framebuffer`(私有 MainTarget)和**一个**静态 `texture`。
- 生命周期:`onWorldRenderBegin()`(382 行)→ `toggleFramebuffer(true)`(413 行)把
  `mc.mainRenderTarget` 字段替换成私有 framebuffer → 整个世界渲染进它 →
  `onWorldRenderEnd()`(431 行)标记 `FILM_PREVIEW` surface + `BBSRenderSurfaceRuntime.capture` →
  `onRenderBeforeScreen()`(504 行)用 `glCopyTexSubImage2D` 把 framebuffer 拷进**那一张**纹理。
- **所有 `UIFilmPreview` 都调 `BBSRendering.getTexture()` 读同一张纹理**。多视口会显示同一画面。
- 改造方向:把单 framebuffer/texture 泛化成**一组视图描述符**(view descriptors),每个有独立
  framebuffer + texture。生命周期钩子改成遍历视图列表,每帧渲染 N 次(或单次多目标)。

### 2.2 CameraController:单全局当前控制器

- `BBSModClient.getCameraController()` 返回**一个**静态 `CameraController`,其 `getCurrent()`
  按优先级返回单个 `ICameraController`(影片编辑器里是 `RunnerCameraController`)。
- 世界渲染用这**一个**相机的视角。没有"每视口一个相机"机制。
- `RunnerCameraController.setup`(103 行):用影片 clip 插值位置设相机 →
  `UIFilmController.handleCamera`(1201 行)覆盖轨道/第一/第三人称。
- 改造方向:支持每视图独立 `Camera` + 控制器(或一个支持多租户的控制器)。
  `RunnerCameraController.apply()` 循环可照抄,但用**局部** CameraClipContext/Position(不碰全局栈)。
  注意:**不要**在副轨道跑 `AudioClientClip.manageSounds`(音频管理是单例,会冲突)。

### 2.3 UIFilmPreview:绑定单 panel 的单控制器/runner/camera

- 构造器 `UIFilmPreview(UIFilmPanel panel)`,私有 `panel` 字段。
- 所有按钮调 `this.panel.getController()`(单 `UIFilmController`)、`this.panel.togglePlayback()`
  (单 `runner`)、`this.panel.getCamera()`(单 `Camera`)。
- 鼠标手势转发给那个单控制器;代码注释(UIFilmPreview:441)明确假设"一个 preview 拥有按下、
  兄弟 controller 持有手势状态"。多 preview 共用会互抢。
- `getViewport()`(385 行)用 `BBSRendering.getVideoWidth()/getVideoHeight()` 全局分辨率 letterbox。
- 改造方向:`UIFilmPreview` 需要自己的 camera/controller 引用,而非 owning panel 的单例。
  按钮排改成操作本视图的控制器。每视图独立手势/缩放/拾取状态。

## 3. 布局系统(已支持扩展,无需大改)

这部分**已经能注入**,不是瓶颈:

- `UIDockLayout.addPanel(id, UIElement, icon, label)` 是**公开**方法,接受任意面板。
- `EditorLayoutNode` 是不可变树(PanelNode/StackNode/SplitterNode),`copyWithInsertSplitAt` 等纯函数。
  树里只存 id 字符串,映射 id→UIElement 在 dock 里。
- 阻碍:`UIFilmPanel.dock` 和 `panelById` 是**私有**无访问器 → 注入需 reflection 或 mixin accessor。
- `getDockPanelIcon`/`getDockPanelLabel` 是硬编码 switch,未知 id 优雅降级(Icons.FILE/IKey.EMPTY)。
- `pruneUnknownPanels` 会删未注册 id,`ensureRegisteredPanels` 会补缺失面板。

结论:布局注入可行(需加 accessor mixin),真正的硬骨头是 §2 的渲染管线。

## 4. 关键文件清单

| 文件 | 角色 | 改造范围 |
|---|---|---|
| `new/src/client/.../client/BBSRendering.java` | 单 framebuffer/texture 生命周期 | 泛化成多视图 |
| `new/src/client/.../BBSModClient.java:138` | 静态单 `CameraController` | 多控制器/多租户 |
| `new/src/client/.../camera/controller/CameraController.java` | `getCurrent()` 单 current | 支持多视图 |
| `new/src/client/.../camera/controller/RunnerCameraController.java` | 影片 clip 插值 → 相机 | 每视图独立实例 |
| `new/src/client/.../ui/film/UIFilmPanel.java` | 单 controller/runner/camera/preview | 多 preview 支持 |
| `new/src/client/.../ui/film/UIFilmPreview.java` | 读单 texture + 按钮调单 panel | 自有 controller/texture |
| `new/src/client/.../ui/film/controller/UIFilmController.java` | 单 pov/orbit/录制/手势 | 每视图独立状态 |
| `new/src/client/.../ui/framework/elements/layout/UIDockLayout.java` | `addPanel` 公开 | 加 accessor |
| `new/src/main/.../settings/values/ui/EditorLayoutNode.java` | 不可变布局树 | 无需改 |
| `new/src/client/.../client/render/surface/BBSRenderSurfaceRuntime.java` | 单 target capture | 多 target capture |

## 5. 建议的实施顺序(分步可验证)

1. **最小改动验证多通道渲染**:先不改 UI,只改 `BBSRendering` 让它每帧渲染两次世界
   (主画面 + 一台虚拟相机),各自 framebuffer/texture。用一个临时 UI 把第二张纹理画出来,
   确认双通道渲染不崩、画面正确。这是最高风险点,先单独验证。

2. **每视图相机模型**:抽出"视图描述符"(camera + controller + texture + surface kind),
   让 `RunnerCameraController` 能对局部 Camera 实例求值,不碰全局控制器栈。

3. **UIFilmPreview 多实例化**:给它加 camera/controller 引用,按钮排操作本视图。
   解决手势/缩放/拾取状态的多租户问题。

4. **布局注入**:加 `UIFilmPanel.dock`/`panelById` 的 accessor mixin,注册多 preview 面板,
   用 `EditorLayoutNode` 树排布。带完整按钮排。

5. **每台摄像机镜头轨道**:`CameraUnitEntity.trackData`(NBT 字符串)存 FSR `Clips`。
   播放头=`panel.getRunner().ticks` 全监视器同步。

## 6. 踩坑提醒(来自插件阶段一/二)

- `RenderSystem.getProjectionMatrix()` 返回**可变引用**,保存要用 `new Matrix4f(...)` 拷贝。
- `renderLevel` 内部多次调 `getMainRenderTarget().bindWrite()` —— 离屏渲染必须替换
  `mc.mainRenderTarget` 字段(用 `@Accessor` mixin),不能只 `bindWrite`。1.21.1 该字段 public 非 final。
- `RenderSystem.clear(256, Minecraft.ON_OSX)` = 清深度(256 = GL_DEPTH_BUFFER_BIT),vanilla 写法。
- 2D 覆盖绘制前要重建 GUI 状态:`setOrtho` + `enableBlend` + `disableDepthTest` + `setShaderColor(1,1,1,1)` + `FogRenderer.setupNoFog()`。
- 逐台光影不可行:Iris 一帧一条管线。字段 shaderMode 仅存储。
- 不要用 PowerShell Set-Content/Get-Content 改含中文文件(系统 GBK 会毁文件)。
