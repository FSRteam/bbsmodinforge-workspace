# 技术设计:多视口世界渲染管线(FSR 主工程改造)

> 配套 `prd.md`。本文件记录改造的架构边界、数据流、契约、兼容性与回滚形态。
> 更新:2026-08-23。行号基线:`new/` 主工程当前 HEAD。

## 0. 设计目标与约束

- **目标**:影片编辑器布局内支持多个 `UIFilmPreview` 视口,每个绑定一台摄像机实体,
  各自带完整按钮排,画面相互独立、同步同一播放头。
- **硬约束(来自插件阶段一/二踩坑)**:
  1. `LevelRenderer.renderLevel` 内部多次调 `getMainRenderTarget().bindWrite()` —— 离屏渲染
     必须替换 `mc.mainRenderTarget` **字段**(mixin `@Accessor` setter),不能只 `bindWrite`。
     1.21.1 该字段 public 非 final,无需 `@Mutable`。
  2. `RenderSystem.getProjectionMatrix()` 返回**可变引用**,保存投影必须 `new Matrix4f(...)` 拷贝。
  3. 2D 覆盖绘制前必须显式重建 GUI 状态:`bindWrite main` + `clear(256, ON_OSX)`(清深度)
     + `setOrtho` + `enableBlend` + `disableDepthTest` + `setShaderColor(1,1,1,1)`
     + `FogRenderer.setupNoFog()`。漏任一会黑屏或污染主画面。
  4. 逐台光影不可行:Iris 一帧一条管线,`shaderMode` 字段仅存储不生效。
  5. 不要在副视图跑 `AudioClientClip.manageSounds` —— 音频管理是单例,多视图并发会冲突。
- **不改的东西**:`EditorLayoutNode` 不可变树(已支持扩展);vanilla `Camera`/`Camera.setPosition`
  等通过已有 mixin `CameraAccessor` 访问(插件已建,主工程复用同名或新建)。

## 1. 现状基线(已核实)

| 组件 | 位置 | 单视口限制 |
|---|---|---|
| `BBSRendering` | `new/src/client/java/mchorse/bbs_mod/client/BBSRendering.java` | 静态 `framebuffer`(L93,`RenderTarget`,运行时为 `MainTarget`)+ 静态 `clientFramebuffer`(L94,save/restore 辅助)+ 静态 `texture`(L95)。`getTexture()`(L235)返回单例。 |
| 生命周期 | 同上 | `onWorldRenderBegin`(L382)→ `toggleFramebuffer(true)`(L413)经 `reassignFramebuffer`(L375,用 `MinecraftAccessor.bbs$setMainRenderTarget`)换字段 → `onWorldRenderEnd`(L431)在 `customSize && filmPanel != null` 时(L441-446)加 `FILM_PREVIEW` surface,L493 `BBSRenderSurfaceRuntime.capture` → `onRenderBeforeScreen`(L504)用 `glCopyTexSubImage2D`(L536)拷进单 texture。 |
| `BBSModClient` | `new/src/client/java/mchorse/bbs_mod/BBSModClient.java` L138 | 静态单例 `cameraController = new CameraController()`,`getCameraController()`(L203)返回它。 |
| `CameraController` | `...camera/controller/CameraController.java` | `getCurrent()`(L62)按优先级返回单个 `ICameraController`;`controllers` 列表 + `add/remove`。 |
| `RunnerCameraController` | `...camera/controller/RunnerCameraController.java` | `setup(Camera, float)`(L102)用 `manual` 或 `context.clips` 插值;`apply()` 在**父类** `CameraWorkCameraController`(L44-73)循环 clips 调 `context.apply(clip, position)`。注册/注销全局栈在 `UIFilmPanel.appear/close/disappear`(L1668/L1695/L1739)。 |
| `UIFilmPanel` | `...ui/film/UIFilmPanel.java` | 单 `runner`(L110)、`controller`(L144)、`camera`(L139)、`preview`(L122,public)。`getController()`(L1316)/`getRunner()`(L1326)/`getCamera()`(L2364)。 |
| `UIFilmPreview` | `...ui/film/UIFilmPreview.java` | 构造 `UIFilmPreview(UIFilmPanel)`(L77)。`render`(L466)调 `BBSRendering.getTexture()` 取同一 texture。`getViewport()`(L385)用 `BBSRendering.getVideoWidth/Height` 全局分辨率。注释 L441:一个 preview 持有按下、兄弟 controller 持有手势状态。按钮调 `this.panel.getController/togglePlayback/getCamera`。 |
| `UIFilmController` | `...ui/film/controller/UIFilmController.java` | 单 `pov`(L158)、`orbit`(L156)、录制块(L141-148)、`gizmo`(L154)。实现 `GizmoViewport`。 |
| `UIDockLayout` | `...ui/framework/elements/layout/UIDockLayout.java` | `addPanel(id, UIElement, Icon)`(L229)与 4 参重载(L234)均 public。`panelById`(L80)私有,只经 `getPanel(id)`(L276)单查。 |
| `UIFilmPanel.dock/panelById` | `...ui/film/UIFilmPanel.java` | `dock`(L120)、`panelById`(L165)均 `private final`,无 accessor。注入需 mixin accessor。 |
| `EditorLayoutNode` | `new/src/main/.../settings/values/ui/EditorLayoutNode.java` | `PanelNode/StackNode/SplitterNode` 子结构 final 不可变;`SplitterNode.ratio` 可变(拖拽)。`copyWithInsertSplitAt`(L209)等纯函数。`pruneUnknownPanels` 删未注册 id。 |
| `BBSRenderSurfaceRuntime` | `...client/render/surface/BBSRenderSurfaceRuntime.java` | 单 `source` 参数 capture(L50);每 lane 单 `encoder/readback/pool`(L26-33),active kind 集变化时 `ensureStarted`(L297)重建。 |

## 2. 改造架构:视图描述符(ViewDescriptor)

核心抽象:**把"全局单 framebuffer/texture/camera/controller"泛化成一组视图描述符**。
每个 `ViewDescriptor` 持有一台摄像机实体绑定所需的全套状态。

```
ViewDescriptor {
  String id;                      // 与布局树 PanelNode.panelId 对应
  CameraUnitEntity camera;        // 绑定的摄像机实体(可空=主画面自由飞行)
  // —— 渲染资源(每视图独立)——
  RenderTarget framebuffer;       // 该视图的 MainTarget(按需创建/复用)
  Texture texture;                // 该视图的输出纹理
  int surfaceKind;                 // BBSRenderSurfaceKind 注册的独立 kind
  // —— 相机/控制器(每视图独立)——
  Camera camera;                   // 局部 Camera 实例
  CameraClipContext context;       // 局部求值上下文(不碰全局栈)
  RunnerCameraController runner;  // 局部 runner(不进 BBSModClient 全局栈)
  // —— UI 状态(每视图独立)——
  UIFilmController controller;    // 局部 pov/orbit/gizmo/录制
  // 视图选项
  int viewMode;                    // 0 固定 / 1 跟随主画面
  boolean motionPath, onionSkin, buttonsVisible, showName, audioViz;
  int fitMode;                     // 1 黑边 / 0 裁切
  int shaderMode;                  // 存储(光影暂不生效)
}
```

### 2.1 资源所有者

- 引入 `MultiViewManager`(客户端单例,挂在 `BBSModClient` 或 `BBSRendering` 旁),
  持有 `Map<String, ViewDescriptor> views`。
- 主画面(无绑定)始终是 `views.get(MAIN_ID)`,复用现有 `BBSRendering.framebuffer/texture`
  作为它的资源,**保持原单视口行为不变**(零回归)。
- 绑定摄像机实体后才创建额外视图;实体移除/解绑时释放其 framebuffer/texture(GL 资源)。

### 2.2 渲染循环(每帧 N 次)

替换 `BBSRendering` 的单次世界渲染为遍历视图:

```
onWorldRenderBegin():
  for view in views:                   // 主画面在前
    swap mc.mainRenderTarget → view.framebuffer   // @Accessor setter
    bindWrite(view.framebuffer, true)
    clear(256, ON_OSX)                 // 清深度
    view.camera = resolveCamera(view)  // 局部 setup,见 §2.3
    setProjection(perspective(fov, w/h, 0.05, max(dist*16,256)))  // 拷贝 Matrix4f
    levelRenderer.renderLevel(delta, false, view.camera, ...)    // 参数顺序照抄 1.21.1
    // view 内 finally:恢复 mc.mainRenderTarget + bindWrite(main)
  // 全局 finally:prepareOverlayState() 重建 GUI 状态(见 §0 约束 3)
```

- **主画面优先**:保证主画面用全局 `CameraController.getCurrent()` 相机(原行为)。
- **副视图不进全局栈**:`view.runner` 是局部实例,只在 `view.camera` 上 `apply()`,
  绝不调 `BBSModClient.getCameraController().add(view.runner)`。
- **逐台洋葱皮/运动路径**:渲染单台前翻 `BBSSettings.editorOnionSkin/editorMotionPath`
  到本视图的值,finally 恢复(渲染线程串行保证"独立",照搬插件 PiPManager)。
- **性能护栏**:视图数上限可配(默认如 4);超限时副视图跳过渲染只画占位。
  每帧渲染 N 次世界是主要成本,需在 §5 验证帧率。
- **光影策略(用户决策 2026-08-24)**:副视口朴素渲染,不跑 Iris 光影后处理。
  原因:Iris 光影一帧只能正确渲染一个相机视角——光影的抗锯齿(TAA)和多数时序效果
  依赖"上一帧同一视角的画面"做时域平滑,一帧内换视角会闪、画面错乱(Immersive Portals
  在 Iris 下也只能降级到兼容模式且首帧闪烁,需延迟渲染)。
  落地:主画面保留原光影路径不变;副视口走原版 `renderLevel`(与 BBS 现有离屏捕获同一路径,
  本就不经 Iris 后处理),零额外成本。各视口几何体仍各画一遍(视角不同看到的几何不同),
  但**不**为副视口触发 Iris 管线。
  后续增强(不在本 MVP):借阴影贴图——光影的 shadow pass 从光源视角算、与玩家相机无关,
  可算一次让所有视口复用,使光照方向/阴影一致。需深入改 Iris 内部,工作量超 MVP,暂不做。

### 2.3 每视图相机求值(局部,不碰全局栈)

- 主画面:`BBSModClient.getCameraController().getCurrent()` + `UIFilmController.handleCamera`
  (原逻辑,不动)。
- 绑定摄像机的副视图:
  ```
  Camera c = new Camera();
  c.setup(mc.level, cameraEntity, false, false, 0);   // 必须先 setup 绑世界,否则雾�� NPE
  if (view.context.clips 非空):
    局部 CameraClipContext ctx = new CameraClipContext(...)   // 不复用全局 context
    for clip in ctx.clips.getClips(runner.ticks): ctx.apply(clip, position)  // 照抄 apply() 循环
    accessor 覆盖 c.position/rotation = position
  else:
    accessor 用 cameraEntity 朝向/位置
  ```
  - `CameraAccessor` mixin(`@Invoker` 打开 protected `setPosition/setRotation`)已在插件验证,
    主工程复用同类 mixin。
  - **不要**调 `manageSounds`:副视图只做视觉,音频归主画面/全局单例。

## 3. 数据流与契约

### 3.1 播放头同步

- 所有视图共用同一播放头 = `UIFilmPanel.getRunner().ticks`(主面板的 runner)。
- 副视图 `runner` 不推进时间,只读主 runner.ticks 去求值自己的 clips。

### 3.2 镜头轨道

- `CameraUnitEntity.trackData`(已预留 NBT 字符串)存 FSR `Clips` 序列化(GSON)。
- 求值时反序列化为 `CameraClipContext`,用主 runner.ticks 取 `getClips(ticks)`。
- 空轨道 = 摄像机实体静止姿态。

### 3.3 渲染 surface 契约

- 每个视图注册独立 `BBSRenderSurfaceKind`(或复用 `FILM_PREVIEW` + 视图 id 区分),
  让 `BBSRenderSurfaceRuntime.capture` 支持多 source。最小改动:为每视图分配独立 texture id,
  `UIFilmPreview` 按 `view.surfaceKind` 取对应 texture(而非全局 `getTexture()`)。
- 若 `BBSRenderSurfaceRuntime` 单 lane 限制使多 source capture 复杂,**退路**:
  跳过 surface runtime,直接在 `onRenderBeforeScreen` 用 `glCopyTexSubImage2D` 把
  各 view.framebuffer 拷进各 view.texture(照搬现有单视口拷贝逻辑,加循环)。

### 3.4 布局注入契约

- 新增 mixin accessor 暴露 `UIFilmPanel.dock`(返回 `UIDockLayout`)和 `panelById`(或经
  `dock.getPanel(id)`)。
- 注册多 preview 面板:`dock.addPanel("film_preview_2", newPreview, icon, label)`。
- `EditorLayoutNode` 树用 `copyWithInsertSplitAt` 排布(纯函数,安全)。
- `getDockPanelIcon/Label` 硬编码 switch 未知 id 优雅降级 → 可扩展或留默认(Icons.FILE/IKey.EMPTY)。
- `pruneUnknownPanels` 会删未注册 id → 注入的面板必须在 `ensureRegisteredPanels` 前注册好。

## 4. 每视口 UI 状态多租户

`UIFilmPreview` 改造为不再 owning 单 panel 的单例,而是持有自己的 `ViewDescriptor` 引用:

| 原行为 | 改造后 |
|---|---|
| 构造 `UIFilmPreview(UIFilmPanel)` | 加 `UIFilmPreview(UIFilmPanel, ViewDescriptor)` 或后置 `bind(ViewDescriptor)` |
| 按钮调 `this.panel.getController()` | 调 `this.view.controller` |
| 按钮调 `this.panel.togglePlayback()` | 仍可转发主面板(播放头共享)或本视图独立 |
| 按钮调 `this.panel.getCamera()` | 调 `this.view.camera` |
| `getViewport()` 用全局分辨率 | 用 `this.view` 分辨率 |
| 手势/缩放/拾取状态在单 controller | 每视图独立 controller 持有,互不抢(解决注释 L441 多租户问题) |
| `render` 读 `BBSRendering.getTexture()` | 读 `this.view.texture`(按 surfaceKind) |

**主预览**(无绑定)的 `ViewDescriptor` 包装现有单例资源,行为零回归。

## 5. 兼容性与迁移

- **零回归优先**:主画面路径完全不动,只在有绑定摄像机实体时启用多视图分支。
  一个 `views.size()==1`(仅主)的配置走原代码路径。
- **配置迁移**:`monitors.json`(插件产物)是插件侧数据,本任务不读它;主工程视图绑定
  存哪待定(候选:影片编辑器布局树本身持久化 panelId→cameraUuid 映射,或新 config 文件)。
  MVP 可先不持久化绑定,运行时由用户在 UI 选摄像机实体。
- **mixin 兼容**:主工程已有 `MinecraftAccessor`(`bbs$setMainRenderTarget`)和
  `CameraAccessor` 体系;新增 accessor 走同一命名空间,避免与插件 mixin 冲突。

## 6. 回滚形态

- 改造分步(见 `implement.md`),每步独立可验证、可单独 revert。
- 引入 `MultiViewManager` 用 feature flag 包裹:关闭时退回单视口原路径。
- 风险点集中在 §2.2 的多通道渲染(最高风险),单独第一步验证;若帧率不可接受,
  副视图降级为低分辨率或限定数量。

## 7. 关键取舍

- **N 次完整世界渲染 vs 单次多目标(MRT)**:选 N 次渲染。原因:`renderLevel` 内部
  多次 `bindWrite` 落点 `mainRenderTarget` 字段,MRT 难以兼容;插件已验证 N 次可行。
  代价是帧率随视图数下降,需护栏。
- **每视图独立 controller vs 多租户单 controller**:选独立。原因:手势/缩放/录制状态
  多租户并发复杂且易抢(注释 L441 已警告);独立实例简单且隔离。
- **surface runtime 多 source vs 直接 glCopy**:MVP 先用直接 glCopy 循环(改动小、
  与现有 `onRenderBeforeScreen` 一致),surface runtime 多 source 作为后续优化。
