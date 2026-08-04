# FSR 主题系统 v2:深度视觉定制与 macOS 级动画

## Goal

v1 换肤系统(见归档任务 `07-23-fsr-theme-system`,已实现:14 语义色 / 图标图集替换 / 3 风格开关 / 6 动画接入点 / 皮肤设置 UI / 4 内置主题)只覆盖"换色"。v2 的目标是两条线:

1. **视觉深度定制** — 用户给出的参考图(粉色"雨姐定制皮肤"风格:大圆角卡片、贴图装饰、柔和质感)要能通过主题 JSON + 贴图资源基本复刻;
2. **macOS 级动画** — 弹簧物理、速度连续的打断、出场/退场对称、覆盖面广、主题可编排。

参考实现:`temp/bbs-refreshed-2.0-public-1.20.1-decompiled/`(BBS Refreshed 2.0 反编译源,同一 UI 框架的前代动效升级实践,详见父任务 `design.md` 研究报告)。

## Requirements

### 视觉线

- R1 圆角:UI 核心控件(弹层、按钮、输入框、开关、列表选中态)支持圆角渲染,半径由主题参数控制,`radius=0` 时逐位回退 v1 直角渲染。
- R2 装饰:主题可声明贴花(锚定于屏幕/面板角落的装饰贴图)与扩面背景(所有根面板可铺底,支持显示模式),不影响交互。
- R3 贴图皮肤(增强项):控件可选 nine-slice 贴图替代纯色填充。

### 动画线

- R4 弹簧内核:动画模型升级为可选弹簧物理;任何进行中的动画被重定向时,值与速度均连续;`UITween` 既有调用点(v1 六接入点)不修改即获得升级。
- R5 退场对称:弹层/右键菜单关闭播放反向动画;退场期间元素不响应任何输入。
- R6 覆盖面:平滑滚动、hover 缩放、按下回弹、仪表盘面板布局过渡纳入动画接入点,每项独立主题开关。
- R7 编排自定义:每接入点支持 track 参数化(alpha/scale/x/y 起点 + 弹簧参数或时长曲线),提供 SCALE / SLIDE 类预设变换;不做 keyframe DSL。

### 全局约束

- C1 **v1 主题 JSON 完全向后兼容**:v1 的 `{duration, easing}` 动画条目、全部颜色/风格键在 v2 运行时行为不变;`format` 保持 1,新增键全部可选。
- C2 **关闭态逐位等价**:所有新视觉/动画特性在"关闭/默认关"状态下渲染结果与 v1 逐位一致(圆角半径 0、贴花空、动画关)。
- C3 红线修订:v1"render-only 不碰生命周期"在退场动画处有意识松动为"退场期元素只渲染、不交互、动画完成后移除";除此之外命中判定/事件分发不动。
- C4 热路径零分配、settled 短路直达旧代码路径(v1 红线延续)。
- C5 裸 JVM 测试红线延续:新代码不得在测试进程触发 `BBSMod.<clinit>`(参考 ThemeManager 的 `catch (Exception | LinkageError)` 模式)。
- C6 构建验证以真实 exit code + 日志双确认(禁止管道结尾判断成败)。

## 任务地图

| 顺序 | 子任务 | 交付物 | 依赖 |
|---|---|---|---|
| 1 | `07-24-theme-v2-motion-engine` | 弹簧内核(UITween 内部升级)+ Batcher2D 全局 alpha 栈 | 无 |
| 2 | `07-24-theme-v2-rounded-corners` | roundedBox/roundedFrame/filledCircle 原语 + 主题 corner_radius + 控件接入 | 无(与 1 并行) |
| 3 | `07-24-theme-v2-motion-exit` | 弹层/右键菜单退场动画 | 1(alpha 栈) |
| 4 | `07-24-theme-v2-motion-coverage` | 平滑滚动 / hover 缩放 / 按下回弹 / 面板布局过渡 | 1 |
| 5 | `07-24-theme-v2-motion-tracks` | track 参数化 + SCALE/SLIDE 预设 + theme.json schema 扩展 | 1、3 |
| 6 | `07-24-theme-v2-decor-layer` | 贴花层 + 背景扩面 | 无(建议在 2 后,可复用圆角) |
| 7 | `07-24-theme-v2-nine-slice` | nine-slice 贴图皮肤(增强项,最后) | 2 |

## Acceptance Criteria

- [ ] 所有子任务各自验收通过并归档。
- [ ] 跨子任务:v1 四个内置主题(dark/light/example/amber)在 v2 运行时渲染与升级前逐位一致(关闭态等价性,C2)。
- [ ] 跨子任务:新增一个内置演示主题(建议 `strawberry`),综合运用圆角 + 贴花 + 背景 + 弹簧动画 + track 编排,作为参考图效果的可行性证明与文档样例。
- [ ] 跨子任务:`docs/theme-spec/` 与 `.trellis/spec/frontend/bbs-ui-theming.md` 同步 v2 全部新键。
- [ ] 全量 `check`(含 testThemeCore / testUiMotions 及新增测试)真实 exit code 为 0。
- [ ] 用户游戏内冒烟确认。

## Notes

- 技术设计与 BBS Refreshed 研究报告见本任务 `design.md`;各子任务启动前在各自 `design.md` 中展开细化,引用父报告的文件坐标。
- v1 遗留清单(text.disabled token、UITextarea.textColor 死 setter、Colors.CONTROL_BAR 删除、overlay 关闭反放残影)中,"overlay 关闭反放"由子任务 3 正式解决;其余小项顺路时清理,不单独立任务。
