# Design: Refreshed 动效补全(通知/toggle/文本/拖动)

执行者注意:红线见 `.trellis/spec/frontend/bbs-ui-theming.md`(settled 逐位、render-only、parse-time 固化)。参考:父任务 research §4。依赖 07-25-refreshed-theme-tokens(notification_* token)。

## 1. 通知滑入对齐(不新建 entry)

- 我方已有 `notification` motion entry(UITheme:69,UIMotions.notification():90)与 UINotifications(`new/src/client/java/mchorse/bbs_mod/ui/framework/notifications/UINotifications.java`)。
- 工作:①核对现有通知动画轨迹与 Refreshed(从屏幕右侧滑入到中上,research §4)差异;若现有 entry+tracks(v2 UIThemeMotionTracks 已支持 x/y/alpha/scale)能表达,则 refreshed 主题里用 tracks 配置(如 `preset: slide_right` 或显式 x track),**零代码**;仅当 UINotifications 未接 tracks 时补接(照 UIOverlay 的 tracks 接入模式:translate→scale→pushAlpha/popAlpha)。②UINotifications 背景/文字色改走 token 子任务的 `notification_fill`/`notification_text` 语义函数;dark 默认==现常量(如现值与 token 子任务预设不符,回该任务修默认值并补对拍)。
- settled/关闭:逐位现状。

## 2. `toggle` entry(UIToggle 滑块动画)

- 参考:Refreshed UIToggle.java:111(easeOutExpo 位置插值)、:136-142(off 色→accent 色插值、滑块 -5197640→-1)。
- 我方 `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/buttons/UIToggle.java` 当前无动画(值变即跳)。
- 方案:新 entry `toggle`(UIThemeMotion + UIMotions 访问器)。UIToggle 持一个 UITween(0..1),set 值时 to(target)(**渲染期读取,状态变更只改 target——render-only**;toggle 值本身是即时生效的,动画只是视觉);渲染时按 tween 值插值滑块位置与颜色(Colors.interpolate,注意 `& Colors.RGB` 规约)。
- entry 关/settled:渲染分支必须与现状逐位一致(整数坐标、同色值);四旧主题默认关,refreshed/strawberry 开(弹簧参数照 press 档)。

## 3. `text_entry`(决策门)

- Refreshed:UIOverlayPanel 打开缩放 0.85→1.0(settings text_entry_open_anim*,research §3)。
- 我方 v2 overlay entry 已带 spring+tracks(scale preset 0.95)。**第一步先验证**:UIPromptOverlayPanel(文本输入弹层)走的是否就是 UIOverlay 动画路径?
  - 是 → **不新建 entry**,refreshed 主题 overlay tracks 配 `scale_from: 0.85` 即可,零代码;在任务 journal 记录结论。
  - 否(如 prompt 走独立渲染)→ 把它接入现有 overlay motion 路径(复用,不新建 entry)。
- BBSSettings 只新增用户级 `text_entry_open_anim` 开关并放入 `refreshed` 分类;它门控 overlay 动画是否执行,时长/起始 scale/曲线仍完全来自主题。

## 3.1 编辑器平滑切换开关

- BBSSettings 新增 `film_editor_layout_transition_enabled`(默认 true),放入 `refreshed` 分类。
- UIFilmPanel 现有 `UIMotions.layout()` 动画启动处先检查该开关;关闭时直接应用最终布局。主题仍控制 layout entry 的 spring 参数。

## 4. `drag_follow` entry(自研,Refreshed 无此实现)

- 我方 `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/UIDraggable.java`(224 行,含手势所有权改造,改动务必小心,先读 [[gesture-ownership-teardown]] 对应 spec/journal)。
- 方案:新 entry `drag_follow`,默认**全主题关**(包括 refreshed;strawberry 可开演示)。开启时:拖动中的元素**逻辑位置照旧即时更新**(命中、落点、数据全用真实位置——不改任何手势/布局语义),仅渲染时画在 UITween 平滑趋近的视觉位置(弹簧 target=真实位置,每帧 to());松手或 entry 关/settled → 视觉位置==真实位置,逐位现状。
- 若 UIDraggable 渲染路径不经手元素自绘(纯改 flex/area),则退化方案:视觉偏移用 matrix translate 包住该元素 render,并在 design 复核确认命中不受影响(命中不读矩阵)。实现者需先读清 UIDraggable 的位置写回机制再动手,结论记 journal。

## 5. 解析与测试

- ThemeParser:`toggle`/`drag_follow` 两个新 entry(text_entry 不建;notification 不建);defaultDark:toggle disabled、drag_follow disabled。
- UIMotionsTest:两个新 entry 的解析/回退/settled 短路;UIToggle 插值端点断言(0→旧 off 渲染值,1→旧 on 渲染值)。
- docs/theme-spec:新 key 文档 + example-theme 全 key 显式 + jar 同步(与兄弟任务改同一文件,注意 rebase 顺序:本任务排在 theme-tokens 后)。
