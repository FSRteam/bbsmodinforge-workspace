# 动画 track 参数化

## Goal

把每个动画接入点从"固定语义 + 时长/曲线"升级为主题可编排:每接入点固定的可动画属性集(alpha/scale/x/y)上,主题给每条 track 起点值与动力学参数,并提供预设变换快捷方式。这是"macOS 级自定义"的封顶层——刻意止步于 track 参数化,不做 keyframe DSL。

## Requirements

- R1 theme.json 动画条目新增可选 `tracks` 对象:键为该接入点支持的属性(`alpha`/`scale`/`x`/`y`),值含 `from`(起点,终点恒为静止态);未声明的属性不参与动画。
- R2 新增可选 `preset` 快捷键:至少 `scale`(v1 默认观感)、`slide_right`、`slide_up`、`fade`;preset 展开为等价 tracks,显式 tracks 覆盖 preset。
- R3 出现与退场共用同一编排(退场反向),依赖 motion-exit 的机制。
- R4 兼容:无 `tracks`/`preset` 的条目行为与 v1/motion-engine 完全一致;`format` 保持 1。
- R5 接入点覆盖:overlay、context_menu 必须;panel_switch、notification 视实现代价纳入。
- R6 解析期定死:tracks 在 ThemeParser 解析为不可变结构,渲染期零查表零分配;坏值逐 key 回退不抛出(v1 解析器约定延续)。
- R7 内置主题演示:至少一个内置主题使用 tracks/preset 展示差异化编排(建议并入父任务的 strawberry 演示主题)。

## Out of Scope

- keyframe 序列、任意属性、每控件实例级编排。
- 贴花/背景的动画(decor-layer 若需浮动效果另行评估)。

## Constraints

- 依赖 motion-engine(弹簧参数)与 motion-exit(退场共用编排)。
- 属性应用顺序固定(translate → scale 围绕锚点 → alpha),文档写明,避免主题间歧义。
- `docs/theme-spec/` 与 spec 文档同步 schema。

## Acceptance Criteria

- [ ] tracks/preset 解析用例进 `testThemeCore`(合法/坏值回退/preset 展开/覆盖优先级)。
- [ ] 无新键主题(v1 四内置)行为逐位不变。
- [ ] 演示主题在 overlay/context_menu 上呈现明显差异化编排;用户游戏内冒烟确认。
- [ ] 全量 check 真实 exit code 0。
