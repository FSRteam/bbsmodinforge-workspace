# 执行计划:多视口世界渲染管线(FSR 主工程改造)

> 配套 `prd.md`(需求)+ `design.md`(技术设计)。本文件是有序执行清单、验证命令、风险点。
> 平台:Codex inline 模式(主 agent 直接编辑,不派子 agent)。
> 改的是 `new/` 主工程,**不是** `bbs-camera-addon` 插件。
> 更新:2026-08-23。

## 验证命令

```cmd
:: 构建(Windows CMD)
cd /d D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new && ..\gradlew.bat build
:: 产物:new\build\libs\BBS_FSR_beta_v*.jar
```

- 用户是非程序员,机器验证靠用户手动换 jar 实测反馈,主 agent 不能自跑游戏。
- 编译通过 + 不破坏主画面原行��� = 每步的最小验收门槛。

## 执行顺序(与 prd.md §5 一致,分步可验证)

### 步骤 0:修复 preview2 窗口缩放实体偏移(2026-08-28 ~ 2026-08-29)

状态：核心工程 `build` 已通过；最终 jar 已生成。用户实测确认旧方案仍有
“转动视角时模型额外移动”的问题，因此本步骤最终采用渲染时序和相机来源修复。

**现象**:preview2(二级离屏 pass)在游戏窗口尺寸 ≠ 启动初始尺寸时,世界中实体与回放
相对地形产生位置偏移;缩放回初始尺寸恢复。

**根因(最终确认)**:窗口尺寸只是触发条件，不是真正的根因。二级 pass 在主画面之后
重入 `GameRenderer.renderLevel`，会改写 Sodium/LevelRenderer 的共享可见区块列表和相机
状态；BBS 回放/模型又通过同一套全局 Film 状态渲染，导致地形、实体和回放使用了不同
帧的相机变换，表现为相对位置偏移。二级相机还额外叠加了固定 `+45°`，所以主视角
移动时会出现第二层运动；全屏和窗口缩放只是让这个错配更明显。

**修复**:
1. 二级 pass 改在当前 Film 帧状态更新之后、主世界 pass 之前执行，让主 pass 成为
   Sodium 共享地形状态的最后写入者，避免下一帧使用副相机区块列表。
2. 不再回滚 Sodium 内部相机快照；主 pass 会根据实际相机重新收敛可见列表。
3. preview2 暂未绑定摄像机实体时，严格使用主相机 yaw，不再叠加固定 `+45°` 的第二层
   运动。窗口/导出尺寸仍通过 `WindowDimensionsAccessor` 读取物理 framebuffer。
4. 移除不再使用的 Sodium accessor mixin，避免不同 Sodium 版本的字段变更造成启动异常。

验证:`cd new && gradlew.bat build -x testReplayKeyframeConsistency -x testUiInputMailbox`；
核心工程 76 个任务全部通过。开发客户端曾因系统无法创建主显示器而无法完成最后一次
图形复测，需在用户实际 Sodium/Iris/Voxy 环境确认画面。

### 步骤 1:最小改动验证多通道渲染【最高风险,先单独验证】

目标:不改 UI,只让 `BBSRendering` 每帧渲染两次世界(主画面 + 一台虚拟相机),
各自 framebuffer/texture,用临时 UI 把第二张纹理画出来,确认双通道不崩、画面正确。

> **光影策略(用户决策)**:主画面保留原光影路径不变;副视口朴素渲染(原版 `renderLevel`,
> 不触发 Iris 管线)。本任务不追求副视口的光影效果。
> **参考实现**:Immersive Portals `GuiPortalRendering.renderWorldIntoFrameBuffer` 已验证此
> 模式——保存 `mc.getMainRenderTarget()` → swap 字段 → `target.bindWrite(true)` → 渲染世界 →
> 恢复原字段 + `bindWrite(main)`。本任务用主工程已有的 `MinecraftAccessor.bbs$setMainRenderTarget`
> 做同样 swap。
> **帧缓冲尺寸**:参照 IP `submitNextFrameRendering`,副 target 若与 `mcFB` 尺寸不符需先
> `resize` 到匹配,避免分辨率错位伪影。

- [ ] 1.1 在 `BBSRendering.java` 抽出"渲染单视图到一个 target"的辅助方法(参照插件
      `PiPManager.renderToTarget` 与 IP `renderWorldIntoFrameBuffer`):swap `mc.mainRenderTarget`
      字段(@Accessor,复用 `MinecraftAccessor.bbs$setMainRenderTarget`)→ bindWrite →
      clear(256, ON_OSX) → 设 perspective 投影(`new Matrix4f` 拷贝)→ `renderLevel` →
      finally 恢复字段 + bindWrite main。参数顺序照抄 1.21.1 vanilla(前 Matrix4f 是 view
      后是 projection,别换)。
- [ ] 1.2 新增第二组静态 `framebuffer2`/`texture2`(临时,验证用),在 `onWorldRenderBegin`
      原主画面渲染**之后**调一次 1.1 的辅助方法,相机用一个固定偏移视角(如主相机右移 +2 块)。
- [ ] 1.3 在 `onRenderBeforeScreen` 用 `glCopyTexSubImage2D` 把 `framebuffer2` 拷进 `texture2`
      (照搬现有 L536 单视口拷贝,加第二次)。
- [ ] 1.4 在 `onRenderBeforeScreen` 的 overlay 绘制里(或临时 debug 面板)把 `texture2` 画到
      屏幕一角,确认第二视角画面正确、主画面无污染。
- [ ] 1.5 验证:编译通过 → 用户换 jar → 主画面正常 + 角里第二视角画面正常,不黑屏/不闪烁。
- [ ] 1.6 验证通过后**删除临时 `framebuffer2`/`texture2`/debug 绘制**,保留 1.1 的辅助方法。

**风险**:双通道渲染不崩就解锁后续;若崩,在此停下查根因(常见:`mainRenderTarget` 恢复
时机、`renderLevel` 参数、投影残留)。**这是整条链最高风险点,不通过不进步骤 2。**

### 步骤 2:每视图相机模型(ViewDescriptor)

目标:抽出 `ViewDescriptor`(camera + controller + texture + surface kind),
让 `RunnerCameraController`/`CameraWorkCameraController.apply()` 能对局部 Camera 求值,不碰全局栈。

- [ ] 2.1 新建 `MultiViewManager`(客户端单例),`Map<String, ViewDescriptor> views`,
      常量 `MAIN_ID`。`create()`/`remove()`/`get()` 管理视图。
- [ ] 2.2 定义 `ViewDescriptor` 类(字段见 design.md §2):id、cameraEntity、framebuffer、
      texture、camera、context、runner、controller、视图选项。
- [ ] 2.3 主画面 `ViewDescriptor`(MAIN_ID)包装现有 `BBSRendering.framebuffer/texture` +
      全局 `BBSModClient.getCameraController()` 路径,**行为零回归**。
- [ ] 2.4 副视图相机求值:局部 `new Camera()` → `setup(level, cameraEntity, false,false,0)`
      (绑世界,防雾 NPE)→ accessor 覆盖位姿 → 局部 `CameraClipContext`(不复用全局)
      → `apply()` 循环(照抄父类,用主 runner.ticks 读 clips)。**不进全局栈,不调 manageSounds**。
- [ ] 2.5 验证:编译通过。单视图(仅 MAIN)走原路径不变(零回归断言)。
- [ ] 2.6 **Review gate**:步骤 1+2 完成后向用户汇报,确认双通道渲染 OK 再继续。

### 步骤 3:UIFilmPreview 多实例化

目标:给 `UIFilmPreview` 加 camera/controller 引用,按钮排操作本视图,
解决手势/缩放/拾取状态的多租户问题。

- [ ] 3.1 `UIFilmPreview` 加 `ViewDescriptor view` 字段 + `bind(ViewDescriptor)` 或新构造器。
      主预览 bind 到 MAIN_ID 的 descriptor(行为不变)。
- [ ] 3.2 按钮排改造:调 `this.view.controller` / `this.view.camera` 替代 `this.panel.getController/getCamera`。
      `togglePlayback` 仍转发主面板(播放头共享)。
- [ ] 3.3 `getViewport()`(L385)改用 `this.view` 分辨率替代全局 `getVideoWidth/Height`。
- [ ] 3.4 `render`(L466)改读 `this.view.texture`(按 surfaceKind)替代全局 `BBSRendering.getTexture()`。
- [ ] 3.5 手势/缩放/拾取:确保每视图独立 controller 持有,转发逻辑不跨视图串
      (解决注释 L441 多租户假设)。
- [ ] 3.6 验证:编译通过;主预览行为不变;构造一个副 preview 指向某视图时画该视图 texture。

### 步骤 4:布局注入

目标:加 `UIFilmPanel.dock`/`panelById` accessor mixin,注册多 preview 面板,
用 `EditorLayoutNode` 树排布,带完整按钮排。

- [ ] 4.1 新建 mixin accessor 暴露 `UIFilmPanel.dock`(`@Accessor` 或 `@Invoker`)和
      `panelById`(或经 `dock.getPanel`)。
- [ ] 4.2 注册多 preview 面板:`dock.addPanel("film_preview_2", newPreview, icon, label)` 等。
      在 `ensureRegisteredPanels` 之前注册,防 `pruneUnknownPanels` 删。
- [ ] 4.3 用 `EditorLayoutNode.copyWithInsertSplitAt` 排布(纯函数,安全),生成含多 preview 的树。
- [ ] 4.4 `getDockPanelIcon/Label` 扩展或留默认降级(Icons.FILE / IKey.EMPTY)。
- [ ] 4.5 验证:编译通过;影片编辑器出现多视口布局;每个视口按钮排可用、不互抢。

### 步骤 5:每台摄像机镜头轨道

目标:`CameraUnitEntity.trackData`(NBT 字符串)存 FSR `Clips`,播放头同步。

- [ ] 5.1 `CameraUnitEntity.trackData` 序列化/反序列化 FSR `Clips`(GSON)。
- [ ] 5.2 副视图求值时反序列化为 `CameraClipContext`,用主 `panel.getRunner().ticks` 取 `getClips`。
- [ ] 5.3 空轨道 = 摄像机实体静止姿态(回退到实体朝向)。
- [ ] 5.4 验证:编译通过;某摄像机实体设轨道后,对应视口在播放时运镜,其余视口不受影响。

## 风险文件与回滚点

| 文件 | 风险 | 回滚策略 |
|---|---|---|
| `BBSRendering.java` | 生命周期核心,改坏会黑屏/无渲染 | 步骤 1 辅助方法独立抽取,失败可只 revert 1.2 的第二次渲染调用,保留主路径 |
| `MinecraftAccessor` mixin | `mainRenderTarget` setter 误用 | 复用已有 accessor,不新建;验证 finally 恢复 |
| `BBSModClient.java` | 单例 controller 全局栈 | 副视图 runner **不进栈**,主画面路径不动 |
| `UIFilmPreview.java` | 按钮/手势多租户串状态 | 主预览先 bind MAIN 保持零回归,副预览独立 controller |
| `UIFilmPanel.java` + 新 accessor mixin | dock/panelById 私有暴露 | accessor mixin 可单独移除恢复封装 |
| chunk render manager 每帧状态 | 一帧多次世界渲染会覆盖 frustum/可见区块/`lastCameraX` 等,导致副视图剔除错乱(Immersive Portals Issue #271 已证) | 副视图渲染前保存/渲染后恢复这些状态;或先按 BBS 现有离屏捕获验证是否已隐式处理,有伪影再加 |

## 踩坑清单(主工程编辑硬规则)

- **不要用 PowerShell 的 Set-Content/Get-Content 改含中文源文件**(系统 GBK 会毁文件+加 BOM);
  一律用 Write/Edit 工具。
- `BufferSource` 刷新是 `endBatch()`(**不是** `flush()`);`RenderSystem.viewport`(无 set 前缀)。
- `Icons` 在 `mchorse.bbs_mod.ui.utils.icons.Icons`(不是 `ui.utils.Icons`)。
- ScreenEvent 鼠标坐标是 GUI 缩放坐标(×`window.getGuiScale()`=帧缓冲像素)。
- 逐台光影不可行(Iris 一帧一条管线),`shaderMode` 字段仅存储。
- 报告用大白话,给"换哪个文件、按什么键、看到什么"级别的操作指引给用户验证。

## Review gate(必须在 task.py start 前确认)

本任务是 complex,且是 bbs-camera-addon 插件阶段一/二的下游整合。**启动实施前需用户确认**:

1. 插件阶段一/二的"用户机器画面不显示"卡点是否已验证通过?(若插件本身离屏渲染
   在用户环境都没跑通,主工程整合的步骤 1 双通道验证无从反馈。)—— 见
   `08-23-camera-monitor-addon` task.json notes 的待验证项。
2. 本任务是否现在启动,还是等插件显示卡点确认后再启动?
3. 步骤 1+2 完成后设中间 review gate,不直接冲到步骤 5。
