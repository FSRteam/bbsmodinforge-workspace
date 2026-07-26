# Design: BBSFSR 粒子界面 FS 化

## 设计边界

保持 `UIParticleSchemePanel` 的标签页、布局预设、撤销/重做、扩展组件和序列化不变。本任务只重做粒子分区内的模式选择表达与 MoLang 输入表面。

## D1 可视化模式选择

复用现有 `UIIcons`,不创建粒子专用的第二套 segmented control。每个原 `UICirculate` 的 label 顺序原样映射成 `UIIcons.add(icon, tooltip)`,回调和 `setValue()` 保持不变。

主要映射:

| 模式 | 图标顺序 |
|---|---|
| 发射率 | `BULLET / TIME / EDIT` |
| 寿命 | `CODE / REFRESH / STOP` |
| 形状 | `BULLET / SQUARE / SPHERE / CIRCLE / OUTLINE / SHAPES` |
| 方向 | `MAXIMIZE / MINIMIZE / ARROW_RIGHT` |
| 运动 | `ALL_DIRECTIONS / GRAPH` |
| 材质 | `SQUARE / DROP / FADING / LIGHT` |
| 纹理 | `IMAGE / FILM / FULLSCREEN` |
| 颜色 | `COLOR / CODE / FADING` |
| 过期 | `CODE / STOPWATCH` |
| 曲线 | `INTERP_LINEAR / CURVES / INTERP_BEZIER / LINK` |
| 事件节点 | `DISABLED / CODE / CONSOLE / PARTICLE / SOUND / LIST / EXCHANGE` |

相机朝向按枚举顺序映射；三种 emitter transform 用其平面法线 `Z/Y/X` 区分。碰撞外观使用与普通外观相同的映射。

`UIIcons` 的等分单元、tooltip、选中高亮和点击回调继续作为唯一行为来源。若需要补主题圆角/边框，只修改该通用控件，并保证圆角为 0、扩展 token 为 0 时回退旧路径。

## D2 带图标的内嵌输入框

不引入 FS 的 `UIMolangExpression`。在现有 `UITextbox` 增加可选能力:

- `icon(Icon)` 设置左侧语义图标。
- `barColor(int)` 设置图标槽右侧色条，默认使用主题 primary。
- 内部 `Textbox` 增加额外 content inset；默认 0 时所有宽度、光标和鼠标索引算法与旧版一致。
- 输入框背景、focus 边框、命中区域仍覆盖完整控件；图标只是绘制装饰，不拦截输入。
- 输入框已有 `cornerWidget/inputSurface/dividerColor` 路径保持为底层，图标槽只叠加主题语义色。

粒子表达式字段使用 FS 已验证的语义映射:轴向字段 `X/Y/Z + RED/GREEN/BLUE`,宽高 `HORIZONTAL/VERTICAL`,生成/更新 `ADD/REFRESH`,寿命 `TIME/STOPWATCH`,速率 `SPRAY/PARTICLE`,颜色 `COLOR/DROP`,曲线输入范围 `IN/OUT`,其余表达式默认 `CODE`。

普通 identifier、texture link、log、sound、effect id 等字符串字段不加 MoLang 图标。

## D3 兼容性

- 不更改任何 particle component class、parser、JSON key 或 enum ordinal。
- 不更改输入 callback、delayedInput、focus、Tab/Enter/Escape 或 undo boundary。
- 新通用输入能力默认关闭，因此其他编辑器逐位保持原行为。
- 原有 dark/兼容主题在扩展 token 为 0 时使用现有 surface/divider；Refreshed 主题可消费现有 token 和圆角。

## D4 验证与回滚

- 编译门: `compileClientJava`。
- 行为门: `testUiInputMailbox` 与 `testParticleRuntimeConsistency`。
- 主题门: `testThemeCore`。
- 全量门: `check`。
- 静态核对:粒子包不再保留用于语义模式切换的 `UICirculate`;仅允许确实不适合图标化的值循环器存在。
- 回滚点:通用输入装饰、核心模式转换、FSR 扩展模式转换分开审阅；任何一层可单独撤销而不影响粒子数据。
