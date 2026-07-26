# 仪表盘任务栏自动隐藏

父任务:`.trellis/tasks/07-25-fsr-refreshed-skin/`(design.md D4,research §1)。依赖子任务 07-25-refreshed-theme-tokens(motion/token 基座先行)。

## Goal

以 Refreshed 的任务栏自动隐藏为基础扩展为完整仪表盘自动隐藏:鼠标离开延迟后将活动面板与底栏整体滑出、底部启示条唤回;Ctrl+S 切换(保存优先);滑出动效走 UITween 弹簧并由主题 motion key `taskbar_hide` 控制样式。

## Requirements

- R1 全局设置(非主题):`dashboard_auto_hide_taskbar`(默认 **false**,区别于 Refreshed 的 true——存量用户无此行为)、`dashboard_taskbar_hide_delay`(100ms,100-15000)。
- R2 动效:hiddenAmount ∈ [0,1] 由 UITween 驱动,活动面板与底栏使用同一纵向位移,实际布局坐标与命中区域同步;不新增生命周期例外;motion 关闭时瞬时切换。根容器固定保留启示条 16px / hover 350ms。
- R3 Ctrl+S 切换,**保存优先**:已注册 Ctrl+S=保存的界面保存吃掉事件;实现时验证 keybind 分发优先级(焦点深者先),并写入测试或冒烟清单。
- R4 主题 motion key `taskbar_hide`:设置开=功能开,motion key 只控动画样式;四旧主题给合理默认弹簧,refreshed/strawberry 可自定义。
- R5 设置项显示在 `refreshed` 分类,与显示工具提示、窗口动画、平滑编辑器切换等 Refreshed 行为开关集中呈现。

## Acceptance Criteria

- [ ] 设置关(默认):行为与现状逐位一致
- [ ] 设置开:活动面板与底栏完整滑出/唤回,motion 关闭时瞬时切换,隐藏后原位置无残留命中
- [ ] Ctrl+S:film editor 内保存优先,dashboard 其余界面切换自动隐藏
- [ ] `gradlew check` 真实 exit code == 0
