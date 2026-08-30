# 实现状态与接续指南(bbs-camera-addon)

> 交接文档。读完这份就能继续写代码。更新:2026-08-23,最新 jar 第五轮重构后。

## 1. 代码地图(全部相对 `bbs-camera-addon/`)

| 文件 | 职责 |
|---|---|
| `src/main/java/mchorse/bbs_camera/BBSCameraMod.java` | @Mod 入口;Dist.CLIENT guard 调 BBSCameraClient.init |
| `src/main/java/mchorse/bbs_camera/Registration.java` | DeferredRegister:实体 `bbs_camera:camera_unit`(0.5×1.7,MISC) |
| `src/main/java/mchorse/bbs_camera/CameraUnitEntity.java` | 实体:label/fov(SyncedEntityData)+ trackData(NBT 预留字段,给轨道用);无物理 |
| `src/main/java/mchorse/bbs_camera/CameraCommands.java` | `/camunit spawn [label] / list / remove <label|all>`(权限 2;放置朝向玩家) |
| `src/client/java/mchorse/bbs_camera/client/BBSCameraClient.java` | 客户端引导:实体渲染器注册、F8 键位、ClientTick 轮询(MonitorInput.init 已移除) |
| `src/client/java/mchorse/bbs_camera/client/UIMonitorPanel.java` | **当前核心**。UIElement 子类,dock 内的监视器面板:`surfaceBox(FILM_PREVIEW)` 渲染世界纹理 + 标题栏 |
| `src/client/java/mchorse/bbs_camera/client/PiPManager.java` | 旧浮层管线(已停用,死代码)。`isEnabled`/`toggle`/`currentFilmPanel` 仍被引用 |
| `src/client/java/mchorse/bbs_camera/client/Monitors.java` | Monitor 模型+切割布局+GSON 持久化(供旧浮层用) |
| `src/client/java/mchorse/bbs_camera/client/MonitorInput.java` | 旧浮层鼠标交互(已停用,死代码) |
| `src/client/java/mchorse/bbs_camera/client/UIMonitorConfigOverlayPanel.java` | 配置面板(UIMessageBarOverlayPanel);影片菜单「监视器配置」打开 |
| `src/client/java/mchorse/bbs_camera/mixin/client/UIFilmPanelDockAccessor.java` | @Accessor("dock") 暴露 UIFilmPanel 的 private final UIDockLayout |
| `src/client/java/mchorse/bbs_camera/mixin/client/UIFilmPanelMixin.java` | @Inject(<init> RETURN) 注册监视器面板进 dock + add + setupFlex;@Inject(fillFilmContextMenu TAIL) 加「监视器配置」菜单 |
| `src/main/resources/bbs_camera.mixins.json` | 两个 client mixin(UIFilmPanelDockAccessor + UIFilmPanelMixin) |

## 2. 核心机制(改代码前必读)

### PiPManager
- **入口** `afterGameRender`:gate = enabled && level/player 非空 && `currentFilmPanel()!=null` && 渲染线程。
  gate 与 MonitorInput 的拦截条件**必须保持一致**(曾因不同步出现"看不见面板但点击被吞")。
  gate 打开时若 `loggedGateBlock==false` 打一条 info 日志(诊断用)。
- **保险丝**:try/catch(Throwable);连续失败计数,第 1 次和每 200 次打 error 堆栈;≥600 次连续失败才 `enabled=false` 自停;成功即清零。**不要改回"一次失败永久停用"**。
- **渲染**:每台监视器一个 `TextureTarget`(keyed by monitor.id);`sizedTarget()` 固定 16:9(宽=面板宽 clamp 160..1280,高=宽×9/16)→ 面板形状变化不引起拉伸。
- 合成相机:`new Camera()` → **必须先调公开的 `setup(mc.level, anchor, false,false,0)` 绑实体和世界**(否则 NeoForge 雾效钩子 `ClientHooks.onFogRender` NPE)→ accessor 覆盖位姿 →
  view = `new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()))`;
  projection = `perspective(fovRad, targetW/targetH, 0.05, max(renderDist*16,256))`;
  调 `mc.levelRenderer.renderLevel(deltaTracker, false, cam, gameRenderer, gameRenderer.lightTexture(), viewMatrix, projection)`(参数顺序照抄 1.21.1 原版,前 Matrix4f 是 view 后是 projection,别换)。
- **逐台独立洋葱皮/运动路径**:渲染单台前把 `BBSSettings.editorOnionSkin.enabled` / `editorMotionPath.enabled` 翻成本台的值,finally 恢复(全局是共享单例,靠渲染线程串行实现"独立")。
- **绘制**:finally 里恢复主 FBO+viewport+projection+colorMask/blend/shaderColor(漏了会黑屏/污染主画面);ortho 投影 `setOrtho(0,w,h,0,-1000,1000)` 左上原点;文字 `font.drawInBatch(...)+buffers.endBatch()`(**不是 flush()**);`blitFitted` 做 letterbox(fitMode=1,默认)/cover(fitMode=0);每台常驻 `drawTitleBar`(TITLE_BAR_HEIGHT=14,"#几/共几台 名字",选中黄色)+选中黄描边 `outline`;`drawChipStrip` 四格(来源40%/path20%/onion20%/name20%)。
- **数据访问链**(全公开,无需 accessor):
  面板:`UIScreen.getCurrentMenu()` → `UIDashboard` → `getPanels().panel instanceof UIFilmPanel`;
  选中伪装名:`panel.replayEditor.getReplay().getName()`;
  选中音频:`panel.cameraEditor.clips.getLastSelectedClip()`(否则扫 `panel.getData().camera.getClips(runner.ticks)`);
  波形:`BBSModClient.getSounds().get(clip.audio.get(), true).getWaveform()`(`average[]`、`getPixelsPerSecond()`、`isCreated()`;秒= ticks/20)。

### Monitors
- `Monitor` 字段:id(UUID)、x/y/w/h(0..1 归一化)、cameraUuid(空=未绑)、viewMode(0固定/1跟随主画面)、motionPath、onionSkin、buttonsVisible、showName、audioViz、shaderMode(预留)、**fitMode(1=黑边默认/0=裁切)**。
- `addSplittingSelected`:沿选中台的长边对半切,新台占远半并成为选中;`setCount`/`removeAt`/`resetToDefault`;每次变更 `save()`。
- 持久化:`BBSMod.getGameFolder()/config/bbs_camera/monitors.json`(GSON 数组)。用户实测 4 台数据正确。
- `findBoundCamera`:ClientLevel **没有** getEntity(UUID),遍历 `entitiesForRendering()` 比对。

### MonitorInput
- 拦截条件与 PiPManager gate 完全一致(+isAutoMode 排除自动模式)。
- 命中区:**仅** 标题栏(y..y+14,选中+开始拖)和按钮条(y+h+2 .. +2+14+2,四格动作);**面板主体必须穿透**(曾吞掉整个右中区域的 BBS 点击,含上层弹窗)。
- 拖动:按下记 `MouseHandler.xpos/ypos`,每帧 `updateDrag`(在 afterGameRender 头部调用)用 `xpos/窗口宽` 归一化增量 `monitor.move(dx,dy)`;松手 `notifyChanged()`。
- 来源循环 cycleSource:摄像机列表顺序循环,末尾进 viewMode=1(跟随),再点回第一台。

### 配置面板
- `UIButton.label` 是**公开 IKey 字段**可直接改文字(选中态 "▶ 监视器N"+`color(0xFFE6B84C)`);`refresh()` 在开面板和增删后调用。
- 中文文本用 `IKey.constant("...")`;Icons 在 `mchorse.bbs_mod.ui.utils.icons.Icons`(不是 ui.utils.Icons)。

## 3. 当前卡点(接手第一件事)

**已解决(2026-08-23)**:监视器画面/标题栏不显示的根因已定位并修复。用户提供 `bbs-camera-addon/log/latest.log`
+`monitors.json` 后确认:整局游戏只有一条 `monitor passes idle: film editor panel not detected
on screen`,之后再无任何 `[bbs_camera]` 日志——gate 全程未开。但用户**配置面板能用、monitors.json 有 4 台正确数据**,
说明影片编辑器确实开着,纯是 `currentFilmPanel()` 检测链在用户环境(装了 bbs_snow/bbs_together/irlite/bbsvfx 等
会改 UI 容器的插件)下失效。

**修复(已在 PiPManager.java)**:
1. `currentFilmPanel()` 保留 `UIScreen→UIDashboard→panels.panel` 快速路径,**新增 UI 树遍历兜底**:
   `menu.getRoot().getChildren(UIFilmPanel.class)`(递归,0.0.11 二进制已核实 `getChildren(Class)` 存在且递归)。
   这样无论影片面板被哪个 addon 的容器包住都能找到。
2. 诊断日志从"只打一次永久闭嘴"改成**状态变化式**(`GateState` 枚举 + `lastLoggedGate`):DISABLED/NO_WORLD/
   WRONG_THREAD/NO_FILM_PANEL/RENDERING 每次切换各打一条,打开/关闭影片编辑器都能看到 `rendering started`/
   `panel not detected` 翻转。`setEnabled` 切换时重置 `lastLoggedGate` 以便下次能再打。

**待用户验证**:换上新 jar 后打开影片编辑器,日志应出现 `[bbs_camera] monitor rendering started (film editor
detected)` 且画面/标题栏显示。若仍 `NO_FILM_PANEL`,说明连 UI 树遍历都找不到 UIFilmPanel(极端情况),需进一步
看用户的界面形态。

### 第三轮重构(2026-08-23 16:25):渲染/绘制两阶段分离 + 渲染状态完整管理

第二轮的 `setOrtho` 补丁只是治标。经三路源码调研(参考 mod cameramod、FSR BBSRendering、
vanilla GameRenderer/LevelRenderer 反编译),定位了**四个连锁根因**,统一重构解决:

1. **离屏渲染画错地方(最致命)**:`renderOne`/`renderFollowMain` 只调 `target.bindWrite(true)` 绑离屏
   target,但没替换 `mc.mainRenderTarget` 字段。`LevelRenderer.renderLevel` 内部多次调
   `getMainRenderTarget().bindWrite(false)`(透明度链/实体轮廓/天气)——全指回真实窗口 FBO,
   离屏画面残缺。**修复**:新增 `MinecraftMainTargetAccessor` mixin(`@Accessor("mainRenderTarget")` setter,
   1.21.1 该字段 public 非 final 无需 @Mutable),抽出 `renderToTarget()` 辅助方法:
   swap 字段 → bindWrite → 设 perspective 投影 → renderLevel → finally 恢复字段 + bindWrite main。

2. **投影矩阵残留**:`drawOverlays` 在 try 块内执行,投影还是最后一台 monitor 的 perspective。
   **修复**:抽出 `prepareOverlayState()` 在 Phase A→B 之间显式重建 GUI 状态(照搬 vanilla
   GameRenderer.render 的 HUD 设置序列):bindWrite main + clear depth + setOrtho + enableBlend +
   disableDepthTest + setShaderColor + setupNoFog。

3. **投影矩阵保存的是活引用**:`getProjectionMatrix()` 返回可变引用。**修复**:用
   `new Matrix4f(RenderSystem.getProjectionMatrix())` 拷贝。

4. **2D 绘制假设未恢复状态**:blend(disabled)、shaderColor(可能染色)、fog(地形雾)、depth(脏缓冲)。
   **修复**:`prepareOverlayState` 统一处理;finally 补 `FogRenderer.setupNoFog()`。

同时移除第二轮的临时诊断(红块、OverlayStats record、drawOverlays 外壳/内核拆分)。
`GateState` 状态变化式日志保留(长期诊断价值)。`currentFilmPanel()` 的 UI 树遍历兜底保留。

新增 mixin:`mchorse.bbs_camera.mixin.client.MinecraftMainTargetAccessor`(已注册进 bbs_camera.mixins.json)。
新增辅助方法:`renderToTarget(Minecraft, TextureTarget, Matrix4f, Runnable)`、
`prepareOverlayState(RenderTarget, int, int)`。

### 第四轮重构(2026-08-23 19:41):改用 gameRenderer.renderLevel(DeltaTracker) 替代 levelRenderer.renderLevel(7参数)

第三轮后画面显示出来了(覆盖层 OK),但监视器内显示四色渐变垃圾——离屏世界没画进去。

经仔细分析参考 mod cameramod(1.21.8)+ exa 搜索 + 1.21.1 API 核实:
- cameramod 是 **1.21.8 的**,`renderWorld`/`SimpleFramebuffer`/`beginWrite`/`getColorAttachment` 在 1.21.1 **完全不存在**。
- 1.21.1 正确离屏方式 = vanilla `renderZoomed`(GameRenderer:920)模式:`setRenderHand(false)` + `setRenderBlockOutline(false)` + 调 **public** `gameRenderer.renderLevel(DeltaTracker)`。
- 原 `levelRenderer.renderLevel(7参数)` 在 **Sodium 环境下**不画进我们的 target——Sodium 接管地形渲染,不尊重 `mc.mainRenderTarget` 字段替换。

**修复**:重写 `renderOne`/`renderFollowMain` → 统一走新方法 `renderOffscreenPass`:
1. 保存 cameraEntity + renderHand + renderBlockOutline
2. `mc.setCameraEntity(cameraUnit)`(让 vanilla camera.setup 用我们的摄像机)
3. `setRenderHand(false)` + `setRenderBlockOutline(false)`
4. 字段替换 `mc.mainRenderTarget` → target + `target.bindWrite(true)` + `target.clear()`
5. 设 `renderingOffscreen=true` flag + offscreen 宽高/FOV
6. 调 `gameRenderer.renderLevel(deltaTracker)`(完整 vanilla 管线,Sodium 有正确 hook)
7. finally 恢复全部

新增 mixin:
- `GameRendererProjectionMixin`:`@Inject` 在 `renderLevel` 调 `resetProjectionMatrix` **之后**,
  用 `renderingOffscreen` flag 门控,替换全局投影为离屏 target 宽高比(vanilla 用窗口宽高比会变形)。
- `GameRendererFlagsAccessor`:`@Accessor` 暴露 private `renderHand`/`renderBlockOutline` 字段。

移除:`renderToTarget` 辅助方法(洋红诊断一并清除)、texId 诊断日志。
保留:`MinecraftMainTargetAccessor`(renderLevel 内部仍会调 getMainRenderTarget().bindWrite)、
`CameraAccessor`(暂未使用,renderLevel 用 mainCamera 但视角由 setCameraEntity 控制,后续视角修正可能需要)。

### 第五轮重构(2026-08-23 14:46):废弃浮层 + 真正融入 dock 布局 + 修复四色垃圾

第三/四轮一直在折腾离屏渲染(`gameRenderer.renderLevel` / 字段替换 / 投影 mixin),四色垃圾始终没解决,
且整套浮层管线导致仪表盘黑屏、暂停界面错位、窗口缩放错位等回归。这一轮彻底换思路:

**四色垃圾根因**:旧的浮层在 `afterGameRender`(每帧最后)用 vanilla `getPositionTexShader` + 手搓四边形
画 BBS 世界纹理,shader 和渲染状态全错。而 BBS 原生 `UIFilmPreview.render` 是用 BBS 自己的 UI 批处理器
`context.batcher.surfaceBox(BBSRenderSurfaceKind.FILM_PREVIEW, texture.id, ...)` 画同一张纹理的——它走
`getPositionTexColorShader` + 正确的 UI blend 状态。shader 错、状态错、渲染时机错 → 四色渐变。

**融入布局做法**:不再做浮层,改成在 `UIFilmPanel` 构造器 RETURN 注入,新建 `UIMonitorPanel extends UIElement`,
注册进影片编辑器的 dock(`UIDockLayout`),和原生预览/编辑区面板同一套体系:

新文件:
- `src/client/java/mchorse/bbs_camera/client/UIMonitorPanel.java`:`UIElement` 子类,`render(UIContext)` 里用
  `context.batcher.surfaceBox(BBSRenderSurfaceKind.FILM_PREVIEW, ...)` 画世界纹理(和原生预览同路径,画面一致不再垃圾),
  顶部标题栏,画面按纹理比例 letterbox 不拉伸,顶部留 `UIFilmPanel.EDIT_PANEL_TOP_OFFSET_PX`(20px)dock 拖动条空间。
- `src/client/java/mchorse/bbs_camera/mixin/client/UIFilmPanelDockAccessor.java`:`@Accessor("dock")` 暴露
  `UIFilmPanel` 的 private final `UIDockLayout dock` 字段。

改动:
- `UIFilmPanelMixin`:新增 `@Inject(method="<init>", at=@At("RETURN"))` 注册逻辑:
  `dock.addPanel("bbsMonitor", panel, Icons.VIDEO_CAMERA, IKey.constant("监视器"))` + **`dock.add(monitor)`** +
  `dock.setupFlex(true)`。
  **关键坑(曾导致面板注册了却不显示)**:`addPanel` 只把面板记进 `panelById` 注册表,**不**把它 add 为 dock 的
  UI 树子节点;`mount()`(构造器 L276,早于我的 RETURN 注入)才负责把当时注册表里的面板 attach 成 child。
  所以 RETURN 注入时 `mount()` 已跑过,我的面板进了 map 但没进 children,UI 树遍历不到它 → `render` 永远不调 →
  看不到面板。**修复**:注册后手动 `dock.add(monitor)` 补上 attach,再 `setupFlex(true)`(它跑
  `ensureRegisteredPanels` 把面板追加为右侧 25% 根列,`applyPanelBoundsFromStacks` 设 flex,`resize()` 算 area)。
- `bbs_camera.mixins.json`:移除 `GameRendererTailMixin`,新增 `UIFilmPanelDockAccessor`(现剩这俩 + UIFilmPanelMixin)。
- 删除 `GameRendererTailMixin.java`(不再需要每帧 hook)。
- `BBSCameraClient`:移除 `MonitorInput.init()`(它驱动旧浮层的拖拽/点击,面板现由 dock 管理)。

用户实测确认:打开影片编辑器后右侧出现"监视器"面板切割,`render` 被调用,area 有值(w=127 h=194),visible=true。

**保留但已停用的死代码**(后续清理):
- `PiPManager`:`afterGameRender`/`renderMonitorFrame`/`drawOverlays` 及所有 quad/draw 辅助方法不再被调用
  (GameRendererTailMixin 已删)。`isEnabled`/`toggle`/`currentFilmPanel`/`computePanelRects` 仍被 BBSCameraClient
  (F8 键)和 MonitorInput 引用。
- `MonitorInput`:`init()` 不再被调,但文件保留(编译通过)。

## 4. 未完成工作(按优先级)

1. **监视器面板下方的操作按钮排**(用户原需求):选机位/录制视频/录制回放/洋葱皮/运动路径等。方案=在
   `UIMonitorPanel` 里加一个 `UIElement` 按钮行(照搬 `UIFilmPreview` 的 `icons` row 构造),或直接复用面板的
   `UIFilmPreview.icons`。注意:第二个 `UIFilmPreview` 会共享 panel 的单例 controller,点 play 两边都动(同一影片)。
2. **每台摄像机自己的镜头轨道**:`CameraUnitEntity.trackData`(NBT 字符串)已预留;方案=存 FSR `Clips`,求值照抄
   `CameraWorkCameraController.apply()` 但用局部 Context;播放头=`panel.getRunner().ticks`。
3. 切镜标记(Blender marker 式主画面切换)。
4. 清理死代码:`PiPManager` 的 overlay 方法 + `MonitorInput`(按钮排做完后一并清理或重建交互)。
5. 阶段三:整合进 `new/` 主工程(已单独写任务书 `.trellis/tasks/08-23-fsr-multiview-render-pipeline/prd.md`)。

## 5. 踩坑清单(务必遵守)

- **不要用 PowerShell 的 Set-Content/Get-Content 改含中文的源文件**(系统 GBK,会毁文件+加 BOM);一律用 Write/Edit 工具;
- `BufferSource` 刷新是 `endBatch()`;`RenderSystem.viewport`(无 set 前缀);`mc.mouseHandler` 是字段;`AABB.infinite()` 不存在(用大包围盒);`ServerLevel.getEntitiesOfClass` 要 AABB;`RegisterCommandsEvent` 在 `net.neoforged.neoforge.event`;`Icons` 在 `...ui.utils.icons`;
- ScreenEvent 鼠标坐标是 GUI 缩放坐标(×`window.getGuiScale()`=帧缓冲像素);拖动增量用 MouseHandler 原始像素/窗口尺寸归一化;
- 逐台光影不可行:Iris 一帧一条管线(字段 shaderMode 仅存储);
- 用户是非程序员:汇报用大白话,给"换哪个文件、按什么键、看到什么"级别的操作指引;报错让他发 `logs` 里最新错误报告/`latest.log`。
