# FSR 宿主侧教程引导覆盖层 API · 设计方案

> 本文档给出实现约束与已核实的源码事实。**每条带行号的断言都已在 `new/` 里读过原文**，实现时若发现与源码不符，以源码为准并回写本文档。

## 0. 实现者必读：三条会让实现静默失败的事实

这三条不是风格建议，是踩过的坑。任何偏离都表现为「功能看起来做完了，运行时没效果且不报错」。

### 0.1 面板切换会把旧面板从 UI 树里摘掉

`UIDashboardPanels.setPanelNow`（`new/src/client/java/mchorse/bbs_mod/ui/dashboard/panels/UIDashboardPanels.java:229`）对旧面板依次调 `disappear()` 与 `removeFromParent()`；`UIDashboard.update()`（`UIDashboard.java:376`）只 tick `this.panels.panel`。

**因此覆盖层绝不能挂在任何面板下。** 挂错位置的症状是：教程面板还在时一切正常，一跳转人物和文字全部消失。

### 0.2 root 层拿不到 `update()`，但模型动画照样能动

`UIBaseMenu.update()`（`UIBaseMenu.java:126`）只做 `this.context.update()`，而 `UIContext.update()`（`UIContext.java:711`）只做 `tick += 1` 加通知更新。root 层元素没有面板那种 per-tick 回调。

但 `UIModelRenderer.render()`（`framework/elements/utils/UIModelRenderer.java:251`）第一件事就是 `updateLogic(context)`，它按 `context.getTick()` 与自己记录的 `tick` 差值补帧调 `update()`（最多补 10 帧）。**所以挂在 root 上的模型渲染器不依赖面板 tick 也会动。**

这条是整个浮层方案成立的前提。实现时不要「顺手」给覆盖层加一条从 `UIDashboard.update()` 转发的 tick 通道——那会让覆盖层重新绑回面板生命周期，等于自废武功。覆盖层若确实需要逻辑 tick，应从 root 层自己的渲染路径推，或明确记录为独立机制。

### 0.3 root 层挂载已有宿主自己的先例，照它做

`UIChalkboard`（`ui/utils/UIChalkboard.java`）由 `UIDashboard` 构造期 `this.chalkboard.full(this.getRoot())` + `this.getRoot().add(this.chalkboard)` 挂上（`UIDashboard.java:116-122`），跨面板存活，按 F10 切换可见性。覆盖层槽位应复用同一模式与同一层级语义，不要另造一套挂载机制。

注意 `UIBaseMenu` 构造期已把 `main` 与 `overlay` 加进 root（`UIBaseMenu.java:74`），`UIOverlay` 弹层走的是 `menu.overlay`。覆盖层与它们的先后顺序决定谁盖谁，必须在实现里显式确定并写进契约，而不是依赖 `add` 的偶然顺序。

## 1. 三项能力的边界

### 1.1 面板导航

现状：`BBSClientApi` 只有 `requestDashboardOpen`（`BBSClientApi.java:280`），经 `BBSUiOpenDispatcher` 走任意线程邮箱、在客户端线程执行 `UIScreen.open(BBSModClient.getDashboard())`。它只负责打开 Dashboard，不选面板。

宿主侧已有 `UIDashboard.setPanel(UIDashboardPanel)` 与 `getPanel(Class)`（`UIDashboard.java:335-343`），但两者都要求宿主内部类型，不能直接开放给 addon。

要新增的是**按稳定字符串 id 导航**。设计要点：

- 内置面板 id 必须是**新引入的对外契约常量**，不能取类名或语言键。`registerPanels()`（`UIDashboard.java:315`）当前注册 9 个面板（morphing / film / model_blocks / particles / model_editor / textures / audio / graph / plugins，开发环境另加 Sandbox）。id 表要与这份注册一一对应，并有测试钉住「注册了但没给 id」和「给了 id 但没注册」两个方向。
- addon 面板复用现有 `<addonId>:<localId>` 全 id。`DashboardPanelContribution.fullId()` 已是这个形状，`UIDashboard.extensionPanels` 就是按它建索引的（`UIDashboard.java:82, 348`）。
- 线程与失败语义照 `BBSUiOpenDispatcher` 的既有形状：任意线程提交、客户端线程执行、返回 future。**不要新造一套并发模型。** 失败要区分「Dashboard 未创建」（`BBSModClient.getDashboardIfCreated()` 返回 null）、「不在 Dashboard 屏幕」、「id 不存在」三种，教程据此决定是提示读者手动打开还是跳过该步。
- `setPanel` 内部会经 `context.menu.runAfterCapturedMouseRelease` 延后执行（`UIDashboardPanels.java:225`）。所以导航不是同步生效的，future 的完成语义要写清楚：是「已排入切换」还是「切换已完成」。选后者需要额外挂钩，选前者要在契约里说明教程不能假设调用返回后立即能解析到目标面板的锚点。

**定案：future 表示“切换已完成”。** 导航请求先投递到 Minecraft 客户端线程，再调用宿主的面板切换入口；只有 `UIDashboardPanels.panel` 已经指向目标面板后才完成 future。当前 `UIBaseMenu.runAfterCapturedMouseRelease` 会在安全的层级变更边界执行回调，因此不另造第二套 tick 或轮询机制。若以后该边界改成真正异步，宿主必须把完成回调一起移到 `setPanelNow` 之后，不能把语义退化成“已排队”。

落地 API 为 `BBSClientApi.navigateDashboardPanel(descriptor, panelId)`，结果类型是 `BBSDashboardNavigationResult`；内置 id 统一由 `BBSDashboardPanelIds` 提供。

### 1.2 控件锚点解析

两种候选，**设计阶段必须选一种并写下理由**：

**A. 宿主显式登记锚点 id。** 在宿主控件创建处登记 `anchorId -> UIElement`。优点是宿主重构时登记点跟着走，语义明确；缺点是要改多处宿主 UI 代码，且登记表的生命周期要跟随面板创建/销毁。

**B. 「面板 id + 语言键」组合解析。** 利用 `UIElement.getChildren(Class, List, boolean)`（`UIElement.java:218`，递归、public）遍历目标面板，读 `UIElement.tooltip`（public 字段，`UIElement.java:65`）经 `ITooltip.getLabel()` 拿 `IKey`，若是 `LangKey` 则比对其 public `key` 字段（`l10n/keys/LangKey.java`）。优点是几乎不动宿主 UI 代码；缺点是只能定位「带 tooltip 且 tooltip 是 LangKey」的控件，且语言键被改名时静默失配。

倾向 A 或 A+B 混合（A 覆盖教程要指的关键控件，B 作为兜底）。无论选哪个，**返回值必须是值对象**：屏幕矩形 + 可用性状态，不返回 `UIElement`。

**定案：选择 A（宿主显式登记），不提供语言键兜底。** 首版登记稳定的面板按钮、面板内容根、Dashboard 设置入口以及 ch01-ch05 直接涉及的 Morphing 主入口控件。显式登记把“语义 id 对应哪个控件”的责任留在宿主；控件重构时编译/结构测试会逼迫维护者更新登记点，不会因为 tooltip 语言键改名而静默失配。语言键遍历看似省代码，却只能覆盖带 `LangKey` tooltip 的控件，且无法可靠表达列表、内容根和动态编辑器区域，所以不进入公共契约。

落地 API 为 `BBSClientApi.resolveDashboardAnchor(descriptor, anchorId)`；稳定标识由 `BBSDashboardAnchors` 提供，返回 `BBSDashboardAnchorResult`。

不可用状态要能区分至少：目标面板未挂载、控件不存在、控件存在但不可见（`UIElement.isVisible()`，`UIElement.java:1209`）、控件被滚动出视口。`UIElement.canBeRendered(Area viewport)` 与 `Area.intersects` 已提供视口相交判断。

坐标系要写清楚：`UIElement.area` 是 public `Area`（`ui/utils/Area.java:25-40`，`x/y/w/h` 均 public int），处于 UI 缩放后的坐标空间。UI scale 可被用户改（`UIBaseMenu.changeUIScale`，`UIBaseMenu.java:88`），所以锚点必须每帧重解析而不是缓存矩形。

### 1.3 覆盖层槽位

语义对标 `BBSDashboardPanelContent`（`api/client/dashboard/BBSDashboardPanelContent.java`）：接口只要求 `root()`，其余生命周期回调有默认实现；宿主用 `UIExtensionDashboardPanel` 包装，拒绝已有 parent 的 root（`UIExtensionDashboardPanel.java:33`），并把每个回调包在 try/catch 里经 `contribution.reportFailure(phase, error)` 上报（`UIExtensionDashboardPanel.java:101-124`）。覆盖层的宿主侧包装类应照抄这套隔离方式。

注册链照 `registerDashboardPanel`（`BBSClientApi.java:77`）：先 `validate*` 返回 rejected（含 `CLIENT_UI` 能力检查、`BBSAddonDescriptorValidator.validate`、id 合法性、null 检查），再经 host registry 投影，最后 `recordDashboardDiagnostic` 记 `REGISTER_CLIENT` / `CLIENT_SETUP`。`BBSDashboardPanelHostRegistry`（`client/dashboard/BBSDashboardPanelHostRegistry.java`）已有完整的「注册表 + 延后投影 + 失败回滚 + `installAll(dashboard)` 在 Dashboard 构造期统一投影」模式，覆盖层需要同构的一份。

**输入穿透是本项最容易做错的地方。** 覆盖层铺在整个 Dashboard 上，若整块接收点击，读者就没法按教程去点真实按钮——功能直接自相矛盾。宿主侧的相关机制：`UIElement.mousePropagation` 与 `EventPropagation`（`PASS` / `BLOCK` / `BLOCK_INSIDE`，见 `UIElement.cantPropagate`，`UIElement.java:1643`）。默认必须是「只有覆盖层自己的可见子元素区域吃事件，其余穿透」。这一条需要一个能自动验证的用例，不能只靠冒烟。

层级关系需显式确定：覆盖层 vs `UIChalkboard`、vs `menu.overlay` 上的 `UIOverlay` 弹层（`UIOverlay.addOverlay` 系列，`framework/elements/overlay/UIOverlay.java:36+`）、vs taskbar 自动隐藏。写进 spec，不要留给运行时偶然。

**定案：root 渲染顺序为 `main`（含面板和 taskbar）→ addon 引导覆盖层 → `menu.overlay` 模态弹层 → 既有 root 工具层（orbit keys / `UIChalkboard`）。** 引导层因此能跨面板并盖住普通 Dashboard 内容，但不会盖住确认框、设置弹层或宿主调试工具。覆盖层容器和 addon 内容根都强制使用 `EventPropagation.PASS`；只有 addon 放入的实际可见交互子控件能消费事件，空白区域继续传给下层面板。taskbar 自动隐藏仍按全局鼠标位置计算，不依赖覆盖层是否消费事件。

落地 API 为 `BBSClientApi.registerDashboardOverlay(descriptor, factory)`；返回的 `BBSDashboardOverlaySubscription` 默认隐藏并拥有显示、隐藏和幂等注销。

## 2. 可直接复用的宿主零件

| 用途 | 位置 | 备注 |
|------|------|------|
| 面板高亮绘制 | `UIDashboardPanels.renderHighlight(batcher, area, direction)`（`UIDashboardPanels.java:53-72`） | `public static`，含 Direction 变体 |
| 矩形/描边/圆角/渐变 | `Batcher2D.box` / `outline` / `roundedBox` / `gradientHBox`（`framework/elements/utils/Batcher2D.java:163, 280, 658, 806`） | 覆盖层自绘箭头与遮罩用 |
| 纯绘制子元素 | `UIRenderable(Consumer<UIContext>)`（`framework/elements/utils/UIRenderable.java`） | `isEnabled()` 恒 false，天然不吃输入 |
| root 层挂载先例 | `UIChalkboard` + `UIDashboard.java:116-122` | 跨面板存活的既有实现 |
| 递归查子元素 | `UIElement.getChildren(Class, List, boolean)`（`UIElement.java:218`） | 锚点方案 B 的基础 |
| 语言键反查 | `UIElement.tooltip` / `ITooltip.getLabel()` / `LangKey.key` | 均 public |
| 主题 token | `BBSSettings`（`chromeSurface()` / `cornerWidget()` / 各色值） | 每帧读，勿构造期缓存 |

## 3. 测试设计

宿主工程已有测试基建，实现前先看 `new/build.gradle` 与既有测试目录的组织方式，**沿用现有方式，不要新引入测试框架**。

必须自动化覆盖的（这些编译期发现不了，只表现为「运行时没效果」）：

1. **内置面板 id 表与 `registerPanels()` 双向一致。** 缺 id 与多余 id 都要失败。
2. **锚点标识与宿主控件的对应关系。** 宿主改布局/改键时应当失败。这是方案 A/B 都必须有的护栏——尤其方案 B，语言键改名是静默失配。
3. **输入穿透。** 覆盖层可见区域外的点击到达底层。
4. **失败隔离。** 内容工厂抛异常、注册被拒、注销后，Dashboard 均可用且无残留。

注意既有教训（`.trellis/spec/new/backend/` 与项目记忆）：裸 JVM 测试不得触发 `BBSMod` 静态初始化；验证构建必须确认**真实 exit code**，不能用 `gradlew | tail` 这类吞掉退出码的管道。

## 4. 风险

| 风险 | 症状 | 缓解 |
|------|------|------|
| 覆盖层挂在面板下 | 跳转后教程整体消失 | §0.1；挂载点必须是 root 层 |
| 给覆盖层接面板 tick | 又绑回面板生命周期 | §0.2；模型动画本就不需要它 |
| 覆盖层整块吃输入 | 读者点不到真实按钮，功能自相矛盾 | §1.3；穿透需自动化用例 |
| 锚点缓存矩形 | 改 UI scale / 滚动后箭头错位 | §1.2；每帧重解析 |
| 方案 B 语言键改名 | 静默指到空白处 | §3.2 对应关系测试 |
| 导航 future 语义含混 | 教程紧接着解析锚点却拿到旧面板 | §1.1 明确 future 完成时机并写进契约 |
| 新增 API 破坏既有契约 | addon 生态受影响 | 全部增量，不改现有签名与 `BBSDashboardPanelContent` 行为 |
| 混入并发会话改动 | 编译失败或误提交 | `new/` 有其他会话在改，提交按路径隔离 |

## 5. 交付后需同步

- `.trellis/spec/new/backend/external-addon-integration-contract.md`：新增一节描述三项能力（放在 §3.4.2 面板契约、§3.4.3 动画预览之后），含层级关系、输入穿透默认值、future 语义、锚点稳定性保证。
- `.trellis/tasks/08-05-fsr-onboarding-tutorial-plugin/prd.md`：Out of Scope 第一条「不实现覆盖层高亮真实 UI 控件」需按本任务结果修订。
- 若 API 形状与本文档偏离，回写 §1 对应小节。
