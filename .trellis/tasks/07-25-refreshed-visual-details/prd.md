# Refreshed 视觉细项(选区/图标/tooltip/时间线/滚动条)

父任务:`.trellis/tasks/07-25-fsr-refreshed-skin/`(design.md D5 C5-C9,research §3/§4/§5)。依赖子任务 07-25-refreshed-theme-tokens。

## Goal

五个互不依赖的视觉小项,全部"未配置=旧行为",不改布局与操作。

## Requirements

- R1 选区渲染增强:移植 UISelectionRenderer(Refreshed 独有类)到本项目,UIClips/UIKeyframes 框选接入;颜色走 selection_* token;dark(未配置)走旧绘制路径。
- R2 图标五态背景色:UIIcon 在 v2 显式背景色机制上扩展 pressed/disabled/active token;未配置回退现行为。
- R3 Tooltip:TooltipStyle 体系**我们已有**(ui/framework/tooltips/styles/ Dark/Light)——本项只补 `tooltip_style`(int)与 `show_tooltips`(bool)两个设置 + 样式选择接线;颜色如需随主题则走 token。
- R4 时间线:`vertical_lines_timeline`(默认 false)+ `keyframe_shape_default`(默认 DIAMOND,复用现有 KeyframeShape 体系)两个全局设置。
- R5 滚动条阴影:我们**当前无阴影**(全库无 scrollbarShadow)——新增可选阴影 token,dark 默认=无(保持现状),refreshed 主题给 -2013265920。
- R6 摄像机/回放/行为编辑器切换按钮消费 Refreshed 活跃态 token;底部列表选中背景和相关活跃背景在圆角主题下绘制圆角,半径 0 时保持旧方角路径。
- R7 设置面板提供可见 `refreshed` 分类;`show_tooltips`/`tooltip_style` 必须接入 tooltip 注册与样式选择,不是仅注册值。
- R8 解锁布局后的面板拖动柄、分割条和投放区域由影片编辑器与通用停靠布局共享同一主题渲染器;Refreshed 使用 area/drop/splitter token 与圆角,token 为 0 时保持旧绘制。

## Acceptance Criteria

- [ ] 全部默认值下与现状逐位一致(对拍单测)
- [ ] refreshed 主题/设置下各项生效可调
- [ ] camera/replay/action 切换图标活跃态可辨识,底部列表选中态为圆角
- [ ] 影片和粒子/通用编辑器的拖动柄、分割条、投放高亮一致且投放区域为圆角
- [ ] `gradlew check` 真实 exit code == 0
