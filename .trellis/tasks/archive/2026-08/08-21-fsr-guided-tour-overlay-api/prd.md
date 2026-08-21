# FSR 宿主侧教程引导覆盖层 API

## Goal

让外部 addon 能把教程「带进真实工作区」：自动切到目标面板、在真实控件旁出现讲解人物、指出该点哪个按钮，并且切面板之后教程不消失。

当前 `08-05-fsr-onboarding-tutorial-plugin` 的教程只能待在自己的 Dashboard 面板里描述操作，读者必须自己找面板、自己找按钮。本任务在 `new/`（BBSFSR 宿主）补齐三项 Addon/API 2.0 能力，让教程 addon 可以实现「跳转 + 出镜 + 指认」，同时不把宿主内部 UI 类型暴露成公共契约。

## Context

### 为什么必须由宿主提供

实施 `08-05` 时核实过三条源码事实，它们共同决定这件事 addon 侧做不干净：

1. **面板切走后 addon 内容整体停摆。** `UIDashboardPanels.setPanelNow` 对旧面板调 `disappear()` 再 `removeFromParent()`；`UIDashboard.update()` 只 tick `this.panels.panel`。教程面板一旦不是当前面板，它的内容根就离开了 UI 树。所以「跳到 Morphing 面板、人物还在旁边指按钮」要求讲解层活在**任何面板之外**，addon 拿不到那个挂载点。
2. **切面板的入口不在 API 面上。** `BBSClientApi` 只有 `requestDashboardOpen`（打开 Dashboard，不选面板）。`UIDashboard.setPanel` / `getPanel(Class)` 虽然是 public，但要传 `UIDashboardPanel` 或宿主面板 `Class`，addon 用它就等于依赖宿主内部类型。
3. **控件定位没有稳定标识。** 认出「Morphing 面板左下角那个取消伪装按钮」目前只能靠 `getChildren(Class)` 递归 + 读 `UIElement.tooltip` 的 `LangKey.key` 反推。这条路能跑通，但宿主任何一次布局或语言键调整都会让它静默指错位置——不报错，只是箭头落在空白处。

结论：三项都属于宿主该拥有的契约，不是 addon 该猜的实现细节。

### 已确认可复用的宿主零件

- `UIDashboardPanels.renderHighlight(batcher, area, direction)` 是现成的面板高亮绘制，`public static`。
- `UIBaseMenu.getRoot()` 返回的 root 层可挂不属于任何面板的元素，`UIChalkboard` 就是宿主自己的先例（挂在 root、按 F10 切换、跨面板存活）。
- **root 层元素照样能驱动模型动画。** `UIBaseMenu.update()` 只推 `context.update()`（`tick += 1`），root 层元素拿不到面板那种 `update()` 回调；但 `UIModelRenderer` 的时钟是在 `render()` 里由 `updateLogic(context)` 从 `context.getTick()` 自己补帧的。所以覆盖层里的人物不依赖面板 tick 也会动。这条是浮层方案成立的前提，必须在实现时保住。
- `UIElement.getChildren(Class, List, boolean)` 递归公开，`UIElement.tooltip` 与 `LangKey.key` 均 public，宿主内部据此实现锚点解析是低成本的。

### 与既有契约的关系

- `08-05` 的 `prd.md` 把「覆盖层高亮真实 UI 控件」列为 Out of Scope，理由是超出面板内容契约。本任务正是要扩掉那条边界；`08-05` 的对应条目需在本任务落地后同步修订。
- 现有 `BBSDashboardPanelContent` 契约不变。本任务是新增能力，不改面板注册与生命周期。

## Requirements

### 1. 面板导航 API

- 新增按**稳定字符串 id** 切换 Dashboard 面板的入口，addon 不接触 `UIDashboardPanel` 类型。
- 宿主内置面板需要一组稳定 id（如 `morphing` / `film` / `model_blocks` / `particles` / `model_editor` / `textures` / `audio` / `graph` / `plugins`）。这些 id 是对外契约，不得直接复用类名。
- addon 面板用已有的 `<addonId>:<localId>` 全 id 寻址。
- 目标不存在、Dashboard 尚未创建、当前不在 Dashboard 屏幕，都要返回可判定的失败结果而非抛异常。
- 调用需 `CLIENT_UI` 能力；任何线程可提交，实际切换在客户端线程执行。

### 2. 控件锚点解析

- 提供按稳定锚点标识查询「某控件当前屏幕矩形」的能力，返回值是值对象（坐标 + 是否可见/可命中），不泄漏 `UIElement`。
- 锚点标识必须比「递归找 tooltip 语言键」更稳。设计需在两种方案里选一种并说明理由：宿主给关键控件显式登记锚点 id，或把锚点定义为「面板 id + 语言键」的组合并由宿主承担解析。
- 控件当前不存在（面板未挂载、被折叠、被滚动出视口）时返回明确的「不可用」而非零矩形——教程要能据此跳过指认而不是在角落画箭头。
- 首版覆盖范围至少包含教程 ch01–ch05 涉及的 Dashboard 面板按钮与各面板主入口控件；不要求覆盖全部控件。

### 3. Addon 拥有的 Dashboard 级覆盖层槽位

- addon 可注册一个 Dashboard 级覆盖层内容根，语义与 `BBSDashboardPanelContent` 平行：宿主拥有挂载点与生命周期，addon 拥有内容。
- 覆盖层在**面板切换后继续存在并继续渲染**，这是本任务的核心价值。
- 覆盖层不得抢走正常输入：默认只在自己的可见子区域内接收点击，其余区域必须穿透到底层面板，否则读者没法按教程真的去点那个按钮。
- 需要显式的显示/隐藏与注销，addon 卸载或注册失败时宿主不残留浮层。
- 覆盖层与 `UIChalkboard`、`UIOverlay` 弹层、taskbar 自动隐藏的层级关系必须明确且稳定。

## Acceptance Criteria

自动可验证：

- [ ] `new/` 编译通过，`./gradlew build` 真实 exit code 0（不得用管道判断）。
- [ ] 面板导航：内置面板 id 表齐备且与实际注册的面板一一对应；用不存在的 id 调用返回 rejected 而不抛异常。
- [ ] 锚点解析：登记的锚点在对应面板挂载时返回非空矩形，面板未挂载时返回「不可用」；锚点标识与宿主实际控件的对应关系有测试钉住（宿主改布局或改键时应失败，而不是静默指错）。
- [ ] 覆盖层：注册被拒 / 内容工厂抛异常 / 注销后，Dashboard 均正常可用且无残留浮层。
- [ ] 输入穿透：覆盖层可见区域外的点击能到达底层面板。
- [ ] 诊断：三项能力的拒绝与失败都进 addon 诊断记录，且 `BBSRegistrationResult` 语义与既有 API 一致。

需人工冒烟：

- [ ] addon 调用导航后 Dashboard 真的切到目标面板，且覆盖层里的人物仍在渲染并继续做动作（不是静止帧）。
- [ ] 覆盖层的箭头/高亮落在目标控件上；窗口缩放、UI scale 改变、面板滚动后仍然对位。
- [ ] 覆盖层区域外可正常操作底层面板（点按钮、拖时间轴）。
- [ ] 切主题、切语言后覆盖层外观与文本跟随。
- [ ] 删除 addon jar 后 Dashboard 无覆盖层残留、无异常日志。

## Out of Scope

- 教程章节内容、台词、人物模型与动作（`08-05` 已实现）。
- 交互门禁式练习验证（「必须点对按钮才能下一步」）。
- 合成输入 / 代替用户点击。
- 提供任意 `UIElement` 的读写句柄。
- 覆盖层跨屏幕（非 Dashboard 的 BBS 菜单）生效。
- 热插件（hot plugin）路径的等价能力。
