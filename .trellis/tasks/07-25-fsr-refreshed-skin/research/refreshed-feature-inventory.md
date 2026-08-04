# Refreshed 特性盘点(已核实)

日期:2026-07-25。来源:haiku 盘点 agent 报告 + 主会话逐项核实(grep/diff 实证)。
参考源:`temp/bbs-refreshed-2.0-public-1.20.1-decompiled/`(CFR 反编译,1.20.1 Fabric)。

## 范围裁定(用户 2026-07-25 确认)

- **不改布局、不改操作 UI 排布**——只做图标/动效/视觉设计的兼容性扩展。
- 排除:回放/相机编辑器预设、自动布局(`film_editor_auto_layout`、`film_editor_layout_preset_*`)。
- 结构性新增全部排除:纹理管理器标签页(UITextureTabs)、表单分类过滤(UIFormCategoryFilterOverlayPanel)、
  PlayerForm/UIPlayerForm*、Discord RPC、FilmPlaybackRange、各 mixin。
- 自动隐藏:全局设置+Ctrl+S(保存优先);主题只管动效样式。
- 本次只做 JSON/设置可配,不做游戏内皮肤编辑器。

## 已核实事实

### 1. 自动隐藏任务栏(UIDashboardPanels.java)
- 配置三件套(BBSSettings.java:312-314):`dashboard_auto_hide_taskbar`(默认 true)、
  `dashboard_taskbar_hide_delay`(100ms,范围 100-15000)、`dashboard_taskbar_hide_duration`(0.5s,范围 0.15-1.5)。
- 机制:任务栏(底部 20px)鼠标离开超时后 EXP_INOUT 缓动滑出;底边 16px 启示条 hover 350ms 唤回。
- 关键方法:tickTaskbarAutoHide / advanceTaskbarAnim / ensureTaskbarHiddenTarget / applyTaskbarSlideIfChanged / resetTaskbarAutoHide(UIDashboardPanels.java:97-199)。
- **Refreshed 没有切换按键**;Keys.java:27 `SAVE = S+LCtrl`(Ctrl+S 是保存)。我们的 Ctrl+S 切换是新增行为(保存优先)。
- 移植时映射到我们的弹簧 motion 体系(theme motion key),不照搬 EXP_INOUT。

### 2. 配色(Colors.java:10-66,全部实证)
UI_CANVAS = UI_PANEL = UI_SURFACE = CONTROL_BAR = **-15789804 (#0F1114)**。实现时从有符号
ARGB 常量转换得到该值;早期盘点中的 #0F0F13 / #0F0F14 均与反编译源码整数不一致。
```
UI_TEXT_FIELD_FILL   -15066079      UI_TEXT_FIELD_BORDER -12956328
UI_BUTTON_LABEL      -16111560      UI_LIST_SELECTED     -14013900
UI_TAB_ACTIVE_LINE   -12171686      UI_TAB_ACTIVE_GRADIENT -2008660392
UI_AREA_TINT         -15198178      UI_AREA_TINT_LIGHT   -15000798
UI_DROP_FILL         1142034968     UI_DROP_BORDER       -1440077258
UI_SPLITTER_ACTIVE   -1434813812    UI_SPLITTER_IDLE     0x22FFFFFF
UI_SHADOW_MUTED      -14277074      UI_TRACKPAD_SCRUB    -9539972
ACCENT_PURPLE_DEFAULT -7508552      CURSOR               -11012822
UI_FILM_EDIT_ZONE    mulRGB(UI_CANVAS,0.58)   UI_FILM_EDIT_ZONE_BAR mulRGB(…,0.93)
UI_PREVIEW_LETTERBOX mulRGB(UI_CANVAS,0.55)
A35=0x35000000 A25=0x44000000 A12=0x22000000 A6=0x11000000(新增细分透明度档)
```
另有 `UiThemeCache`(ui/theme/)单例 + `dark_mode` 开关(BBSSettings.java:183)——被我们的主题系统取代,色值做成 "refreshed" 主题包 token。

### 3. 设置项差异(BBSSettings key 级 diff,实证)
Refreshed 有、我们没有(排除项已剔):
- `dashboard_auto_hide_taskbar/_delay/_duration` → R3
- `text_entry_open_anim`(true)/`_duration`(0.5s)/`_start_scale`(0.85)→ UIOverlayPanel 文本输入打开缩放;
  我们 v2 overlay spring 可能已覆盖,实现时确认,若覆盖则只补参数 key
- `tooltip_style`(int,默认 1;TooltipStyle 样式类)
- `keyframe_shape_default`(KeyframeShape.DIAMOND;我们已有 KeyframeShape 体系,缺默认形状设置)
- `vertical_lines_timeline`(默认 false,时间线竖线)
- `shadow`(scrollbarShadow 颜色,-2013265920)
- `dark_mode`(被主题系统取代)、`show_tooltips`
- `refreshed` 只是 settings **category** 名,不是开关
- `transform_local_default`(编辑行为默认,非皮肤,不移植)
- `ui_corner_radius_buttons_trackpads/_interface`(v2 三级圆角 token 已覆盖,不移植)
- `film_editor_layout_transition*`(v2 layout motion 已覆盖)

### 4. 动效(v2 未覆盖部分)
- **通知系统**(UINotifications.java:46-72):Lerps.lerp 从屏幕右侧滑入到中上,宽 300,
  背景 mulRGB(UI_CANVAS,0.95),edge outline 用通知类型色,文字自动换行。
- 文本输入打开缩放 0.85→1.0(UIOverlayPanel)。
- 选区渲染 `UISelectionRenderer`(独有类,UIClips + UIKeyframes 使用)——框选视觉增强。
- UIIcon 五态背景色(默认/hover/按下/活跃/禁用),active 用 UI_TAB_ACTIVE_GRADIENT 渐变。
- 已覆盖确认:圆角、布局动画、平滑滚动、弹层过渡、hover/press 缩放(v2 已移植,不重做)。

### 5. Refreshed 独有类清单(文件级 diff 实证)
UI 相关:UITextureTabs/UITextureTabElement、UIFormCategoryFilterOverlayPanel、UITextureFileLinkList、
UiThemeCache、UICornerRadii、UISelectionRenderer、UIPlayerForm(Panel)。
非 UI:Discord RPC ×3、FilmPlaybackRange、PlayerForm、mixin ×4。
其中仅 **UISelectionRenderer** 在裁定范围内(视觉渲染,无布局改动);
UIDataTabs 等我们已有(FS2 迁移时已带入)。

## 我方现状修正(2026-07-25 主会话核实)

- **notification motion entry 我们已有**(UITheme.notification + UIMotions.notification(),v1 六接入点之一)→ C3 收窄:只补通知**样式颜色 token**(背景/边框/文字)与滑入轨迹参数,不新建 entry。
- **TooltipStyle 体系我们已有**(ui/framework/tooltips/styles/ Dark/Light)→ C7 收窄:只补 `tooltip_style`(int)与 `show_tooltips`(bool)设置 + 主题接线。
- `scrollbarShadow` 我们**没有**(全库无引用)→ C9 是新增可选阴影,dark 默认=无阴影(保持现状),refreshed 主题给 -2013265920。
- 现有 UITheme token 集(UITheme.java:24-37):surfaceChrome/Base/Raised/Deep/Divider、accentPrimary、textPrimary/Muted、statePositive/Negative/Warning/Active/Highlight/Cursor;chrome flags textShadow/bevel/panelShadow;corner 三级;motion 十个 entry(overlay/panelSwitch/hover/notification/contextMenu/scrollbar/scrollSmooth/hoverScale/press/layout)。

## 候选移植清单(待用户圈选)

| # | 项 | 类型 | 主题可编辑 |
|---|----|------|-----------|
| C1 | refreshed 内置主题包(§2 全部色值→theme token) | 视觉 | 本体 |
| C2 | 仪表盘任务栏自动隐藏(全局设置+Ctrl+S 保存优先) | 交互 | 动效样式 key |
| C3 | 通知滑入动画+样式 | 动效 | motion+颜色 key |
| C4 | 文本输入打开缩放(或确认 v2 已覆盖) | 动效 | motion key |
| C5 | 选区渲染增强(UISelectionRenderer) | 视觉 | 颜色 key |
| C6 | UIIcon 五态背景色 | 视觉 | 颜色 key(v2 hover/active 基础上扩展) |
| C7 | Tooltip 样式(tooltip_style + TooltipStyle) | 视觉 | 样式 key |
| C8 | 时间线竖线 + 关键帧默认形状 | 视觉 | 设置项(非主题) |
| C9 | 滚动条阴影色 token | 视觉 | 颜色 key |
