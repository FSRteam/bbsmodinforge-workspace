# 动效覆盖面扩张

## Goal

把动画从"六个点"扩展为"无处不在的微动画",全部机制采用 BBS Refreshed 已验证的实现模式(坐标见父任务 design.md 第 3.3/3.4 节)。每项独立主题条目、独立可回滚。

## Requirements

- R1 平滑滚动:所有 Scroll 区域采用双值模型(逻辑 targetScroll 即时、视觉 scroll 弹簧/指数趋近);主题条目 `scroll_smooth`(enabled + 参数),关闭时滚动行为与 v1 逐位一致。视觉值仅用于渲染偏移,命中/边界判定用逻辑值。
- R2 hover 缩放:按钮/图标类控件 hover 时可选轻微 scale(围绕中心 pose 变换,默认关闭,主题条目 `hover_scale` 指定倍率);与 v1 的 hover 颜色渐变叠加。
- R3 按下回弹:可点击控件按下时轻微压缩、松开回弹(主题条目 `press`,默认关闭)。
- R4 仪表盘面板布局过渡:面板切换/布局变更时,面板 bounds 从旧位置动画到新位置。机制采用"快照 from/to → 每帧插值写回 flex → 真实 resize()"(渲染与命中始终一致);打断时冻结当前插值为新 from;差异低于阈值不动画。主题条目 `layout`。
- R5 以上每项均挂 `UIMotions` 统一开关(`motion.enabled` 总闸 + 单条 enabled),关闭态逐位等价 v1。

## Out of Scope

- 列表增删动画(v3 候补,风险/收益比不足)。
- 每控件通用布局动画(仅面板级,即时模式下每帧 resize 成本约束)。

## Constraints

- 依赖 motion-engine(弹簧内核)。
- 布局过渡每帧 resize 仅在动画进行期发生,settled 后零开销。
- 平滑滚动不得改变滚轮一格的逻辑滚动量(灵敏度语义不变)。

## Acceptance Criteria

- [ ] 四项动效逐项可开关,关闭态与 v1 渲染/交互逐位一致。
- [ ] 平滑滚动:滚动中点击列表项命中正确(逻辑值判定验证)。
- [ ] 布局过渡:动画期间拖拽/点击面板内容命中正确;动画中再次切换面板无跳变。
- [ ] `testUiMotions` 扩充双值滚动模型用例;全量 check 真实 exit code 0;用户游戏内冒烟确认。
