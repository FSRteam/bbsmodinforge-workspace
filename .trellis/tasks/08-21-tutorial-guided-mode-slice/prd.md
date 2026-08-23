# 教程 addon 接入宿主引导 API（ch01–ch05 垂直切片）

## Goal

让教程真的「把读者带到操作现场」：读者在教程面板点「带我去」，Dashboard 切到目标面板，讲解人物跟着出现在真实面板上方并指出该点的控件，读完可以切回教程面板继续。

本任务是**垂直切片**：只做 ch01–ch05 共 32 步，把整条链路（内容字段 → 导航 → 覆盖层 → 锚点高亮 → 返回）打通并可冒烟，验证后再决定是否铺到 52 章。

## Context

### 依赖：`08-21-fsr-guided-tour-overlay-api` 已交付

宿主侧三项能力已落地（`new/` 提交 `629bdc7c`，构建 EXIT=0，反例验证通过），本任务是它的第一个消费者：

- `BBSClientApi.navigateDashboardPanel(descriptor, panelId)` → `CompletableFuture<BBSDashboardNavigationResult>`。future 在客户端线程、**切换生效之后**完成。状态：`NAVIGATED` / `ALREADY_ACTIVE` / `DASHBOARD_NOT_CREATED` / `NOT_DASHBOARD_SCREEN` / `PANEL_NOT_FOUND` / `REJECTED` / `FAILED`。
- `BBSClientApi.resolveDashboardAnchor(descriptor, anchorId)` → `CompletableFuture<BBSDashboardAnchorResult>`。record，含 `x/y/width/height` + `visible` + `hittable` + `available()`。状态另有 `PANEL_NOT_MOUNTED` / `CONTROL_NOT_FOUND` / `CONTROL_HIDDEN` / `OUTSIDE_VIEWPORT`。
- `BBSClientApi.registerDashboardOverlay(descriptor, factory)` → `BBSDashboardOverlaySubscription`（`registration()` / `visible()` / `setVisible(boolean)` / `close()`）。**初始隐藏**，每 addon 一个，first-wins。内容接口 `BBSDashboardOverlayContent` 只要求 `root()`，另有 `onMounted` / `onUnmounted` / `onDashboardOpen` / `onDashboardClose` / `onShown` / `onHidden`。

宿主 jar（`new/build/libs/BBS_FSR_beta_v0.0.11.jar`）已含这三套类，教程工程按 `../new/build/libs` 取最新 jar，依赖路径已通。教程工程基线构建 EXIT=0（52 章 343 步、两套检查通过）。

### 现状缺口：内容里没有结构化定位信息

`content.json` 的步骤字段只有 `id / order / action / dialogueMale / dialogueFemale / instruction / userWillSee / hint`。`instruction` 是给人读的散文（例：「点击左侧 **伪装（人形图标）** 按钮 → 观察右侧内容变化」）。新 API 要的是稳定 id，**两者之间没有可自动推导的映射**，必须给步骤补结构化字段。这是本任务的主要工作量，不是几行调用。

### 排序依赖：`08-21-fsr-dashboard-anchor-coverage` 应先落地

本任务规划时核实出锚点密度不足（详见下表），已另开 `08-21-fsr-dashboard-anchor-coverage` 把宿主锚点从 5 个具名控件扩到覆盖九大面板的一级入口。

**两个任务不是强依赖，但顺序有明显优劣**：

- 锚点任务先做完 → 本任务的字段填写一次到位，ch04/ch05 也能指到面板内部。
- 本任务先做 → 前五章有一半步骤只能导航不能指认，锚点扩充后还要回来重填字段，且 `testTutorialContent` 的常量集合校验会经历一次扩容。

**建议**：锚点任务的阶段一（测试加强）+ 阶段二（Film / Morphing）完成后再开始本任务。Morphing 是前五章的主战场，那批锚点到位后本切片的指认精度就够了；Film 那批服务于后续章节铺开。

若坚持先做本任务，则按下表的「控件级无锚点」分类填空，并在锚点扩充后回来补填——这条路要多走一轮内容修改。

### 现有宿主锚点覆盖面（决定了本切片能指到什么）

`BBSDashboardAnchors` 现在提供：

- `panelButton(panelId)` / `panelContent(panelId)`——9 个内置面板都有
- `SETTINGS` / `SELECTORS`——Dashboard chrome
- Morphing：`palette`、`palette_list`、`palette_editor`、`demorph`、`from_mob`
- Film：选择、录制、预览、影片操作按钮、三个编辑器及相机/重放时间轴
- Model Blocks / Particles / Model Editor / Textures / Audio / Graph / Plugins：各自一级列表、工具栏、编辑器和切换控件

对照 32 步的实际需求，分三类：

| 类别 | 例子 | 处理 |
|------|------|------|
| 面板级可指 | ch01_s3 点伪装/影片按钮、ch03_s1 打开伪装面板 | 用现有锚点，能指 |
| 控件级无锚点 | ch03_s5 可见性滑块、ch04_s3 Rotation Y 滑块、ch04/ch05 的骨骼树与右键菜单、ch02_s5 设置弹层内的项 | 本切片**不指**，字段留空，只导航到面板；一级入口可使用新增锚点 |
| 本无控件可指 | ch01_s1 按数字键 0、ch01_s4 按 F9、ch02_s2 去原版按键设置、ch05_s1 纯理解步 | 字段留空，不导航 |

**明确接受**：本切片的指认精度到「面板 + 面板内主要入口」，不追求指到滑块与右键菜单项。要更细需要宿主继续加锚点，那是 `new/` 的另一轮工作，不在本任务内。

## Requirements

### 1. 内容字段扩展

- 给步骤新增可选定位字段：目标面板 id 与锚点 id（命名在 design 定）。两者都可缺省。
- 只填 ch01–ch05 的 32 步。其余 47 章保持无字段，运行时按缺省处理，不得因此报错或降级现有行为。
- 字段值必须是宿主公开常量对应的字符串（`BBSDashboardPanelIds` / `BBSDashboardAnchors` 的产物），不得手写魔法字符串。
- 编辑源（`chapters-01-10-skeleton.json`）与运行时 `content.json` 同步，`_check.py` 仍通过。

### 2. 引导模式交互

- 教程面板每步在有目标面板时显示「带我去」入口；无目标时不显示（不能出现点了没反应的按钮）。
- 触发后：调用导航 → 成功则显示覆盖层 → 覆盖层内出现讲解人物与当前步骤精简文本 → 有锚点则高亮/指向该控件。
- 覆盖层需提供「回到教程」与「关闭引导」，读者不能被困在引导态。
- 覆盖层里要能上一步/下一步，否则读者每换一步都得切回教程面板，引导就没有意义。
- 导航失败按状态分别降级：`DASHBOARD_NOT_CREATED` / `NOT_DASHBOARD_SCREEN` 提示读者先打开 Dashboard；`PANEL_NOT_FOUND` 记日志并退回面板内阅读；`ALREADY_ACTIVE` 视为成功。任何失败都不得让教程面板不可用。

### 3. 覆盖层内容

- 人物沿用 `UITutorialCharacter`（`emoticons/*` 模型、`ActionsConfig` 的 `idle` slot、`update()` 自推时钟）。这套约束不因换了挂载点而改变。
- 覆盖层是 root 层内容，**不得依赖面板 tick**。宿主已明确「不要加 `UIDashboard.update()` 转发通道」，addon 侧同样不能自己造一条。
- 覆盖层的空白区域必须让点击穿透到底层面板——读者要能真的去点教程叫他点的按钮。宿主已把包装与内容根都设为 `EventPropagation.PASS`，addon 内容不得把根改成 `BLOCK`，只有实际交互子元素可吃事件。
- 锚点高亮每帧重新解析，不缓存矩形（UI scale 可改、面板可滚动）。锚点不可用时不画（不能在角落画个指向空白的箭头）。
- 覆盖层文本走语言键，chrome 新增键需中英两版齐备。

### 4. 生命周期与所有权

- 覆盖层注册在客户端 bootstrap，与面板注册同期；注册被拒只损失引导能力，教程面板仍可用。
- `BBSDashboardOverlaySubscription` 的可见性由教程状态驱动，退出引导必须 `setVisible(false)`。
- 引导态是否持久化到 `TutorialProgress` 由 design 决定；若持久化，旧进度文件缺字段时必须安全缺省（不能一升级就弹出覆盖层）。

## Acceptance Criteria

自动可验证：

- [x] `bbs-tutorial-plugin` 的 `./gradlew build --rerun-tasks` 真实 exit code 0。
- [x] `testTutorialContent`：32 步的定位字段值都落在宿主公开常量集合内（从宿主 jar 反射取，非硬编码）；缺省字段不报错；其余 47 章无字段仍通过。
- [x] `testTutorialWiring`：三个 API 的方法名与描述符逐项核对宿主 class 常量池；覆盖层内容根未被设成非 PASS 且未 `markContainer()`。
- [x] 新增 5 个 chrome 语言键在 `zh_cn` / `en_us` 均存在、非空、`%s` 数量与代码一致，无死键（共 32 键）。
- [x] 四项新增检查全部用反例确认会 FAIL 并已恢复（详见 `implement.md` 反例记录）。
- [x] jar 审计：`content.json` 含 32 步新字段（21 步有值）、两份语言文件、`UITutorialGuide.class` 均进包。

需人工冒烟（本任务无法自动验证；2026-08-22 覆盖层返工为 v2 后行为）：

- [ ] ch01_s3 点「带我去」→ Dashboard 切到伪装面板，覆盖层人物出现在面板上方且**持续做动作**（不是静止帧）。
- [ ] 有锚点的步骤：人物滑行到目标按钮旁并持续做 `emote_pointing`；蚂蚁线+四角括号的框选套在按钮上，随面板滚动/窗口缩放跟随；人物与气泡**不遮住按钮本身**。
- [ ] 无锚点的步骤（如 ch04_s3）只导航不画指向，人物回落到该步脚本动作，pack 停靠右下角。
- [ ] 目标按钮在屏幕右半时 pack 落在按钮左侧（人物换到 pack 右缘），不越出屏幕。
- [ ] 覆盖层空白处可正常操作底层面板（点面板按钮、拖控件），框选本身不吃点击。
- [ ] 覆盖层内上一步/下一步可用，动作与文本同步，换步时人物平滑走位到新目标；翻到的步骤带 `panel` 时自动跳转到对应面板并继续指；「回到教程」能切回教程面板。
- [ ] 拖动气泡顶条可挪动整包，挪动后位置保持到换步为止。
- [ ] 关掉 Dashboard 再开，引导态与进度表现符合 design 的持久化决定。
- [ ] 切 zh_cn / en_us、切主题后覆盖层文本与配色跟随。
- [ ] 删除 addon jar 后 Dashboard 无覆盖层残留、无异常日志。

## Out of Scope

- ch06–ch52 的定位字段（本切片验证后另行决定是否铺开）。
- 宿主锚点的新增——属 `08-21-fsr-dashboard-anchor-coverage`。本任务只消费已存在的锚点。
- 属性字段、右键菜单项、弹层内控件、具体取值的指认（连锚点扩充任务也不覆盖这一层）。
- 交互门禁（「必须点对按钮才能下一步」）。
- 合成输入 / 代替读者点击。
- 女款皮肤素材补充。
- 覆盖层在非 Dashboard 屏幕生效。
