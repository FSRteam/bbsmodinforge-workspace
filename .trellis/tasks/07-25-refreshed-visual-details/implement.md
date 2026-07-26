# Implement: Refreshed 视觉细项(选区/图标/tooltip/时间线/滚动条)

前置阅读:prd.md + design.md;spec bbs-ui-theming;依赖 07-25-refreshed-theme-tokens 先合入。五项(V1-V5)相互独立,可按序或挑做,每项完成后独立可验证。

## Checklist

- [ ] V1 选区渲染:读 Refreshed UISelectionRenderer + UIClips/UIKeyframes 调用点 → 转译移植 → 接我方两处框选 → selection_* token 接线(dark 默认=现常量,回填 token 任务对拍)→ journal 记录 Refreshed 具体样式值
- [ ] V2 图标五态:UIIcon 扩展 icon_pressed/icon_disabled/active=tab_active_gradient,优先级与 0 哨兵按 design §V2;dark 逐位
- [ ] V3 tooltip:show_tooltips + tooltip_style 设置 + 渲染入口接线 + 设置 UI 挂载(UIValueMap 特例挂法)
- [ ] V4 时间线:vertical_lines_timeline + keyframe_shape_default 两设置 + 绘制/读取点接线(先 grep Refreshed 用法定位对应我方位置,记 journal)
- [ ] V5 滚动条阴影:scrollbar_shadow token 接 Scroll 渲染,0=无(现状)
- [ ] V6 影片编辑器:camera/replay/action UIIcon active 样式 + renderHighlight token/圆角;审计底部列表 override 并补圆角选中背景
- [x] V7 停靠布局:新增 UIDockStyleRenderer,让 UIFilmPanel/UIDockLayout 共用拖动柄、分割条、中心/四边投放高亮;0 token 保留旧路径
- [ ] 每项完成后:`cd new; ./gradlew check *> ../check.log; "REAL_EXIT=$LASTEXITCODE"` == 0 再进下一项
- [ ] 收尾:themeCoreTest 对拍合并、docs/theme-spec 若有新 key 同步(V1/V2/V5 的 token 文档在 token 子任务已建则只核对值)、冒烟项汇入父任务清单

## 回滚点

每项一个独立 commit,单项 revert 安全(未配置=旧行为)。

## 禁区

不改 UIClips/UIKeyframes 的交互与布局(只动绘制);不动 UIToggle/UINotifications/UIDashboardPanels(兄弟任务);tooltip 不做新样式类(用现有 Dark/Light);不搬 Refreshed 的 dark_mode。
