# Design: Refreshed 皮肤移植与仪表盘自动隐藏(父任务)

日期:2026-07-25。前置:theme v2 已提交(new/ `e0c9b4b0`)。事实依据:research/refreshed-feature-inventory.md(全部实证)。

## 总体架构决定

### D1 一切走 theme v2 体系,不引入 UiThemeCache
Refreshed 的 UiThemeCache/darkMode/UICornerRadii 全部**不移植**——对应能力由我们的
ThemeManager/UITheme/三级圆角 token 承担。Refreshed 色值只作为数据进入新内置主题包。

### D2 新增 token 的兼容红线
现有 UITheme token 集不够表达 Refreshed 的语义(如 text_field_border、tab_active_gradient、
notification_*、icon 五态、selection_*、scrollbar_shadow)。新增 token 规则:
- 每个新 token 在 **dark 主题的默认值 == 当前硬编码常量**,保证 dark 逐位一致(对拍单测把关);
- 解析规则与 v1/v2 相同:base 链继承、坏值回退 warn、parse-time 固化;
- 接入点把裸常量替换为 `ThemeManager.current().xxx` 时,必须逐一核对 `Colors.RGB` 掩码用法
  (裸语义常量做渐变终点的位置传 `fn() & Colors.RGB`,见 bbs-ui-theming spec)。

### D3 新增 motion entry 的兼容红线
新 motion key:`notification`(滑入)、`toggle`(开关滑块)、`text_entry`(打开缩放,若现有
overlay entry 已覆盖则并入其参数)、`drag_follow`(窗口拖动跟随)、`taskbar_hide`(任务栏滑出)。
- 全部走 UITween 弹簧内核 + settled 短路逐位等价;
- **dark/light/example/amber 四个旧主题默认全关**(表现不变);strawberry/refreshed 打开;
- render-only:通知滑入/toggle/文本缩放/拖动跟随都是纯渲染插值;任务栏隐藏见 D4。

Refreshed 主题文件必须显式写出全部 motion 条目并使用 `type: spring`,不能依赖 dark 的 ease 继承。用户级 `text_entry_open_anim` 和 `film_editor_layout_transition_enabled` 只负责门控现有 overlay/layout 条目,曲线与弹簧参数仍由主题负责。

### D4 任务栏自动隐藏(唯一的状态型动效)
- 全局设置三件套(BBSSettings 新增,不属主题):`dashboard_auto_hide_taskbar`(默认 **false**,
  与 Refreshed 的 true 不同——我们的现状是没有该行为,默认开会惊吓存量用户)、
  `dashboard_taskbar_hide_delay`(100ms)、`dashboard_taskbar_hide_duration` 由主题 motion
  `taskbar_hide` 控制(主题管样式,设置管开关/延迟,与 PRD R3 对齐)。
- 实现锚点:我们的 UIDashboardPanels 对应类;隐藏量 hiddenAmount ∈ [0,1] 由 UITween 驱动,
  **只影响渲染平移与命中区域计算**,不动 flex/resize——布局不变,鼠标检测用渲染后的实际区域,
  与 1.20.1 参考"命中与渲染一致"原则相同。不新增生命周期例外(元素不移除、不 setEnabled)。
- Ctrl+S 切换:注册全局 keybind(keys().register),**保存优先**——已注册 Ctrl+S=保存的界面
  (film editor 等)保存吃掉事件,其余界面触发切换;实现上切换 keybind 挂在 dashboard root、
  保存 keybind 挂在焦点面板,依靠现有 keybind 分发优先级(焦点深者先),需在实现时验证优先级顺序。
- 启示条 16px hover 350ms 唤回,常量照搬。

### D5 各项落点(承接实现的类)
| 项 | 我们的锚点类 | 方式 |
|----|-------------|------|
| C1 refreshed 主题 | client resources `themes/refreshed/` | 纯资源包(色值表在 research §2)+ 新 token 默认值 |
| C2 任务栏 | UIDashboardPanels + BBSSettings + Keys | D4 |
| C3 通知 | UINotifications | 滑入插值 Lerps→UITween;颜色换 token |
| C4 文本输入 | UIOverlayPanel(先确认 v2 overlay entry 是否已覆盖) | 覆盖则加参数,否则新 entry |
| C5 选区渲染 | 移植 UISelectionRenderer 到 ui/utils/ + UIClips/UIKeyframes 接入 | 颜色走 token;dark 关闭时走旧绘制路径 |
| C6 图标五态 | UIIcon(v2 已有显式背景色机制) | 新增 pressed/disabled/active token,未配置时回退现行为 |
| C7 tooltip | TooltipStyle 体系移植 + show_tooltips 设置 | 样式 int 设置 + 颜色 token |
| C8 时间线 | UIKeyframeDopeSheet/UIClips + BBSSettings | 竖线开关 + keyframe_shape_default 设置(已有形状体系) |
| C9 滚动条阴影 | Scroll 渲染处 | 单 token |
| E2 toggle | UIToggle | easeOutExpo→UITween 弹簧;off 时逐位旧渲染 |
| E5 拖动跟随 | UIDraggable | 渲染层平滑跟随(命中用真实位置),默认关 |

### D6 图标资源、设置分类与圆角选中态
- Refreshed 的 1024x1024 `icons.png` 是按 4 倍像素尺寸导出的 atlas;我方 `Icon` UV 固定在 256x256 坐标系,构建资源前按 nearest-neighbor 缩放为 256x256,放入 `themes/refreshed/icons.png`,主题 `textures.icons` 指向它。
- `BBSSettings` 新建 `refreshed` 分类,保留用户可见的行为开关;旧主题也可见且可配置,但默认值保持旧行为。设置 UI 对 `tooltip_style` 使用现有 dark/light 选项。
- `show_tooltips` 在 tooltip 注册/最终渲染入口门控;`text_entry_open_anim` 门控 overlay;`film_editor_layout_transition_enabled` 门控 `UIFilmPanel` 的 layout tween;自动隐藏开关门控 dashboard 状态机。
- `UIIcon` 消费 icon pressed/disabled/active token。`UIFilmPanel` 的 camera/replay/action 顶栏按钮和底部列表选中态继续使用现有布局,只把活跃背景改为 `roundedBox`/`roundedFrame` 与 `tab_active_*` token;圆角半径为 0 时回落旧方角路径。

## 子任务拆分(独立可验收)

1. **refreshed-theme-tokens**(C1+E1+D2/D6 资源部分):新增 token 集 + 对拍单测扩展 + refreshed 主题包、真实 icon atlas、完整 spring motion 配置 + docs 同步。
2. **dashboard-autohide**(C2+D4):设置 + keybind + 滑出动效 + taskbar_hide motion key。依赖 1(用其 token)。
3. **motion-additions**(C3/C4/E2/E5+D3):四个 motion entry + 接入点 + UIMotionsTest 扩展。依赖 1。
4. **visual-details**(C5–C9+F1/F2):选区/图标五态/tooltip/时间线/滚动条阴影、设置分类和编辑器/列表圆角选中态。依赖 1。
5. 父任务收尾:全量 check、theme-spec 终审(README+example-theme 全 key 显式、jar 逐字节同步)、集成冒烟清单。

顺序:1 → (2,3,4 可并行) → 5。依赖关系已写入各子任务 implement.md。

## 验证

- 每子任务:`gradlew check` 真实 exit code;themeCoreTest 对拍(dark 逐位)+ 新 key 解析测试。
- 回退:各子任务独立 commit,单独 revert 不影响他人;新 token/entry 未配置时全部回退旧行为。
