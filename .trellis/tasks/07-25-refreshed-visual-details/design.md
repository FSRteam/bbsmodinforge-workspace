# Design: Refreshed 视觉细项(选区/图标/tooltip/时间线/滚动条)

执行者注意:五个互不依赖的小项,红线统一:**未配置/默认 = 旧行为逐位一致**;不改布局与操作。依赖 07-25-refreshed-theme-tokens(selection_*/icon_*/scrollbar_shadow token)。参考:父任务 research §3-§5。

## V1 选区渲染增强(UISelectionRenderer)

- 参考:`temp/.../ui/utils/UISelectionRenderer.java`(Refreshed 独有类)+ 其在 UIClips.java / UIKeyframes.java 的调用点(1.20.1 映射需转译)。
- 移植到 `new/src/client/java/mchorse/bbs_mod/ui/utils/UISelectionRenderer.java`;接入我方 UIClips 与 UIKeyframes 的框选绘制处。
- 颜色走 `selection_fill`/`selection_outline` token;**dark 默认 = 现框选绘制常量**(实现时从现调用点抠出,回填 token 子任务的 defaultDark 并补对拍)——即 dark 下观感不变,refreshed 下用 Refreshed 的样式值(从其源码调用点抄具体值,记入 journal)。
- 若 Refreshed 的选区渲染包含动画(呼吸/流动),只在 motion enabled 时启用,settled 逐位。

## V2 图标五态背景色(UIIcon)

- v2 已有:radius>0 时主题 hover/active 默认底色、显式背景色机制(getExplicitBackgroundColor)。
- 扩展:`icon_pressed`(按下态)、`icon_disabled`(禁用态)token;active 态用 `tab_active_gradient`。取色优先级:显式设置 > token(值≠0)> 现行为。token==0(dark 默认)→ 完全走现渲染分支,逐位。
- 按下态判定用现有 press 状态(v2 press 缩放同源判定),不新增输入状态机。
- UIFilmPanel 的 camera/replay/action 顶栏 UIIcon 必须设置/更新 active 状态;其额外 selection strip 改用 `tab_active_line`/`tab_active_gradient`,圆角半径来自 cornerWidget。

## V3 Tooltip 设置

- 我方已有 TooltipStyle 体系(`ui/framework/tooltips/styles/` Dark/Light)。
- BBSSettings 新增:`show_tooltips`(bool,默认 true)、`tooltip_style`(int,默认=现行为对应值;查现有样式选择逻辑——若已由主题 light 标志决定,则该设置做三态:0=跟随主题/1=dark/2=light,默认 0)。
- 接线:tooltip 渲染入口处 show_tooltips==false 直接跳过;样式选择读设置。设置 UI 挂现有 skins/appearance 分类(UIValueMap 特例挂法见 [[fsr-theme-system-plan]]:按值实例 == 判断)。

## V4 时间线设置

- `vertical_lines_timeline`(bool,默认 **false**):UIKeyframeDopeSheet(或我方时间线网格绘制处,实现时定位)在每个整拍/主刻度画竖线;参考 Refreshed editorVerticalLinesTimeline 的绘制点(grep `editorVerticalLinesTimeline` 用法)。颜色用现有 divider/muted token,不新增。默认 false = 逐位现状。
- `keyframe_shape_default`(int enum,默认 DIAMOND==现默认):我方已有 KeyframeShape 体系(UIKeyframeDopeSheet/UIKeyframeFactory);只补"新建关键帧的默认形状"设置与读取点(对照 Refreshed BBSSettings.java:200 的用法定位读取处)。

## V5 滚动条阴影

- 我方 Scroll 当前无阴影。`scrollbar_shadow` token(ARGB,0=无):Scroll 渲染滚动条前,token≠0 时在轨道区画一层阴影 box(照 Refreshed scrollbarShadow 用法定位其绘制方式,grep `scrollbarShadow` 在其 Scroll/渲染类的调用)。dark 默认 0 → 逐位现状;refreshed 给 -2013265920。

## V6 圆角选中态

- `UIList.renderListElement()` 在 `cornerPanel()>0` 时用 roundedBox 绘制 hover/selected 背景;0 时保留现有 `box`/`area.render` 路径。
- 审核影片编辑器底部专用列表/标签控件的 override;凡绕过 UIList 共用路径的 selected 背景,按同一半径/token 规则补齐。只改绘制,不改 item 高度、命中或布局。

## V7 停靠布局交互样式

- `UIDockLayout` 与 `UIFilmPanel` 的面板拖动柄、分割条、投放提示禁止复制绘制逻辑,统一调用 `UIDockStyleRenderer`。
- 拖动柄使用 `area_tint/area_tint_light` 填充、`splitter_idle/splitter_active` 边框和 `cornerWidget()`;分割条使用 splitter 两态 token 绘制圆角 pill。
- 投放区域使用 `drop_fill/drop_border` 和 `cornerPanel()` 绘制完整圆角 frame,边缘投放先计算实际目标矩形再绘制。
- 每类扩展 token 全为 0 时必须落回原 primary 方形/单像素绘制,保证旧主题逐位一致。

## 测试

- themeCoreTest:新 token dark==0/现常量对拍(与 token 子任务对拍表合并);V3/V4 设置默认值断言(设置注册处可 JavaExec 测的部分)。
- 游戏内冒烟项(写入父任务清单):框选观感 dark 不变/refreshed 生效;图标五态;tooltip 开关与样式;时间线竖线;滚动条阴影。
- 停靠冒烟:影片和粒子布局解锁后对比拖动柄/分割条;拖到中心与四边时圆角区域、颜色和命中目标一致。
