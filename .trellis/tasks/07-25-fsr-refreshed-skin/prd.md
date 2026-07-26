# PRD: Refreshed 皮肤移植与仪表盘自动隐藏

## 背景

- 本项目 theme v2(圆角/弹簧动效/退场/tracks/贴花/coverage)已落地并提交(new/ 仓库 `e0c9b4b0`),内置五个主题,规范见 `new/docs/theme-spec/` 与 `.trellis/spec/frontend/bbs-ui-theming.md`。
- `temp/bbs-refreshed-2.0-public-1.20.1-decompiled/` 是 BBS Refreshed 2.0(1.20.1 Fabric)反编译源码,其中圆角、布局动画、平滑滚动、弹层过渡、hover/press 缩放的机制已在 theme v2 中移植完成。
- 用户希望把 Refreshed 的**整体 UI 显示方式**(视觉风格 + 剩余交互/动效)引入 FSR,并且全部以"可编辑皮肤选项"的形式落地,而不是硬编码。

## 需求

### R1 Refreshed 内置主题
把 Refreshed 的视觉风格(配色、控件样式、背景等)做成 FSR 的一个新**内置主题包**(theme v2 体系下的主题目录),用户可在设置中切换。**在现有主题基础上新增,不删除/不改坏任何旧主题。**

主题包必须包含 Refreshed 的真实图标 atlas 覆盖和完整 spring motion 配置,不能只交付配色 token。切换到 Refreshed 后,图标外观和运动曲线类型都必须立即改变。

### R2 界面动效与视觉的移植(2026-07-25 用户二次确认)
- **不改布局、不改操作 UI 排布**——只做图标、UI 动效、视觉设计的**兼容性扩展**,在现有界面基础上加,不改变现有界面结构与交互方式。
- Refreshed 的结构性新增(纹理管理器标签页、表单分类过滤、玩家表单、Discord RPC 等)全部**不移植**。
- 移植的动效/视觉项必须接入 theme v2 的 token / motion 体系,成为**主题可编辑选项**(其它主题可关闭或调参),不得硬编码。
- 具体清单见 research/refreshed-feature-inventory.md 候选表,经用户逐项圈选后写入 design.md。

### R3 仪表盘边框自动隐藏(2026-07-25 用户确认)
- 移植 Refreshed 的仪表盘边缘面板自动隐藏能力(边栏/边框滑出隐藏)。
- 自动隐藏是**全局设置,与主题解耦**;主题只控制隐藏/滑出的动效样式(时长/弹簧参数等 motion key)。
- 切换键 **Ctrl+S,保存优先**:在 Ctrl+S 已被"保存"占用的编辑器界面里保存优先,自动隐藏切换仅在未被占用的界面生效。
- "哪些部件参与自动隐藏"做成可配置结构;**本次只做 JSON/设置可配**,游戏内可视化皮肤编辑器是以后的独立任务。

### R4 明确排除(不移植)
- 回放编辑器预设
- 相机编辑器预设
- 自动布局预设(本项目已有布局过渡)

### R5 Refreshed 设置分类与编辑器选中态(2026-07-26 用户验收反馈)
- 设置面板新增可见的 `refreshed` 分类,至少包含:显示工具提示、工具提示样式、窗口打开动画、平滑编辑器切换、自动隐藏仪表盘边框及隐藏延迟。
- 上述开关必须接到实际渲染/动效入口,不能只注册配置值。
- 摄像机编辑器、回放编辑器、行为编辑器的切换图标必须显示 Refreshed 活跃态;底部列表选中项和相关活跃背景在 Refreshed 下使用主题圆角,不再保持方角。

## 约束

- 遵守 theme v2 红线(`.trellis/spec/frontend/bbs-ui-theming.md`):
  - 动效关闭或 settled 时与 v1 渲染路径逐位等价;
  - 动画 render-only(生命周期例外仅 motion-exit 一处,不得新增例外,若自动隐藏需要新例外须在 design.md 论证并更新 spec);
  - 主题结构 parse-time 固化,渲染期零查找零分配;坏值按 key 回退并 warn,不抛异常。
- 不破坏现有五个内置主题的表现;dark 主题必须保持与旧常量逐位一致。
- Refreshed 源码是 1.20.1 Fabric intermediary 映射,移植时需转译到本项目 NeoForge 1.21.1 的映射与包结构,禁止直接拷贝。

## 验收标准

- A1 新内置主题 "refreshed"(或用户确认的命名)可选,整体观感与 Refreshed 源码一致;原有主题全部保留且表现不变。
- A2 切回 dark 主题时,所有被改动界面与旧版逐位一致(对拍测试把关)。
- A3 Ctrl+S 可切换仪表盘自动隐藏;隐藏/滑出有动效且遵守 motion 门控(动效关闭时瞬时切换)。
- A4 每项移植的动效/界面改动在主题 JSON 中有对应可编辑 key,并同步进 `docs/theme-spec/`(README + example-theme 全 key 显式)。
- A5 `gradlew check` 全绿(真实 exit code 验证,不经管道)。
- A6 Refreshed 图标 atlas 在运行时实际被选择;主题 motion 条目均解析为 spring,并可通过设置开关关闭对应窗口/布局动画。
- A7 设置面板显示 Refreshed 分类;toggle 点击有可见弹簧曲线;三个影片编辑器切换按钮与底部列表的选中态均为圆角主题样式。

## 待定项

(已全部裁定,2026-07-25 用户圈选完毕)

## 最终移植清单(2026-07-25 用户圈选)

| # | 项 | 说明 |
|---|----|------|
| C1+E1 | refreshed 内置主题包 + 界面按钮样式 | Colors.java 全部 UI_* 色值 → 主题 token;缺语义槽的新增 token(dark 默认值==旧常量保持逐位一致) |
| C2 | 仪表盘任务栏自动隐藏 | 全局设置(开关/延迟/时长)+ Ctrl+S 切换(保存优先);滑出动效走弹簧 motion,主题可调样式 |
| C3 | 通知滑入动画+样式 | 滑入 motion key + 背景/边框/文字色 token |
| C4 | 文本输入打开缩放 | 0.85→1.0;若 v2 弹层动效已覆盖则只补参数 key |
| C5 | 选区渲染增强 | UISelectionRenderer 移植(UIClips/UIKeyframes 框选视觉),颜色 token |
| C6 | 图标五态背景色 | 默认/hover/按下/活跃/禁用,v2 现有 hover/active 基础上扩展 token |
| C7+E4 | Tooltip 样式 + 显示开关 | tooltip_style + show_tooltips |
| C8 | 时间线竖线 + 关键帧默认形状 | 全局设置项(非主题) |
| C9 | 滚动条阴影色 token | 小项 |
| E2 | 开关(UIToggle)曲线运动反馈 | Refreshed easeOutExpo 滑块+颜色插值 → 我们的弹簧 motion key |
| E3 | 圆角/方角自定义 | **v2 三级圆角 token 已覆盖**,refreshed 主题体现即可,无新代码 |
| E5 | 影片内窗口拖动运动模式 | Refreshed 无此实现,自研扩展:UIDraggable 平滑跟随可选,默认即时(=旧行为),motion key |
| F1 | Refreshed 设置分类 | show_tooltips/tooltip_style/text_entry_open_anim/film_editor_layout_transition_enabled/dashboard_auto_hide_taskbar/_delay,逐项接入实际消费者 |
| F2 | 真实图标 atlas + 编辑器选中态 | 主题 atlas 覆盖;UIIcon 五态;camera/replay/action 切换图标及列表选中背景消费圆角/token |
