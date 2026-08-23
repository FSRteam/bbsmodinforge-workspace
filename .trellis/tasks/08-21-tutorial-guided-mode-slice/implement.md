# 教程引导模式（ch01–ch05 切片）· 实施计划

## 前置

读 `prd.md` 与 `design.md`（**§0 三个已定决策**）。工作目录 `bbs-tutorial-plugin/`（被 workspace 根 `.gitignore` 忽略，源码不进主仓库）。宿主 API 已在 `new/build/libs/BBS_FSR_beta_v0.0.11.jar`，工程自动取该目录最新 jar。

基线已确认：`./gradlew build` EXIT=0，52 章 343 步，两套检查通过。

## 阶段一：内容字段

- [x] `TutorialStep` 加 `panel` / `anchor` 两个 final 字段，`fromData` 用 `getString(key, "")` 读取，另加 `hasPanel()` / `hasAnchor()`。**未做**白名单降级（理由见 design §1）。
- [x] 逐条核对 ch01–ch05 的 32 条 `instruction` 原文落值。21 步有目标、11 步留空（纯按键、原版设置、纯理解步）。
- [x] 同步编辑源 `chapters-01-10-skeleton.json` 与运行时 `content.json`，用行级插入而非 `json.dump` 全量重写。
- [x] `_check.py` 通过（errors 0，真实 exit 0）。顺带修掉它 `REQUIRED` 里遗留的 `"pose"`——上一轮把数据迁成 `action` 时漏改了这个脚本，导致全部 67 步误报 `missing pose`。
- [x] diff 只有 32 处插入，无格式抖动。

## 阶段二：进度共享

- [x] `TutorialProgress.shared()` 单例，面板与覆盖层共用。任一侧翻页后另一侧 `refresh()`（面板 `onAppear`、覆盖层 `onShown`）读到新位置。

## 阶段三：覆盖层

- [x] `UITutorialGuide extends UIElement implements BBSDashboardOverlayContent`。
- [x] 人物复用 `UITutorialCharacter` 未改动；未造 tick 源（动画靠 `UIModelRenderer` 自己的 `context.getTick()` 补帧）。
- [x] 未设置任何传播策略，沿用宿主强制的 `PASS`；人物与文本卡片靠左下角，其余留空穿透。
- [x] 锚点高亮：每帧发起解析、用上一帧结果作画，`available()` 为假不画；渲染路径无 `join()`。
- [x] 上一步 / 下一步 / 回到教程 / 关闭引导四个入口。
- [x] 5 个新 chrome 键（`take_me_there` / `back_to_tutorial` / `close_guide` / `guide_needs_dashboard` / `guide_unavailable`），中英齐备，共 32 键。

## 阶段四：注册与接线

- [x] `registerDashboardOverlay` 在 `initialize()` 尾部，静态持有 subscription；被拒只记日志并让 `isGuideAvailable()` 返回 false。
- [x] 「带我去」按 `step.hasPanel() && isGuideAvailable()` 显示。
- [x] 导航后按状态降级：`NAVIGATED` / `ALREADY_ACTIVE` 显示覆盖层；`DASHBOARD_NOT_CREATED` / `NOT_DASHBOARD_SCREEN` 提示先开 Dashboard；其余显示中性的「手动操作」提示。
- [x] 「关闭引导」与「回到教程」都 `setVisible(false)`，后者再导航回 `bbstutorial:tutorial`。

## 阶段五：测试

- [x] `testTutorialContent`：新增 `HostIds` 从宿主 jar 反射取 `BBSDashboardPanelIds` / `BBSDashboardAnchors` 常量并展开两个生成族（10 面板 / 67 锚点），**不硬编码第二份清单**；校验 panel/anchor 值合法、面板锚点必须配 panel；其余 47 章无字段仍通过。
- [x] `testTutorialWiring`：三个 API 的名字与**描述符**逐项核对；覆盖层根不得设非 PASS、不得 `markContainer()`；新增语言键契约。
- [x] 四条反例全部确认会 FAIL（详见下方记录）。

## 阶段六：验证与收尾

- [x] `./gradlew build --rerun-tasks` 真实 `EXIT=0`（4m59s）。
- [x] jar 审计：`content.json` 含 32 步新字段（21 步有值）、两份语言文件、`UITutorialGuide.class`、皮肤纹理均在包内。
- [x] 改动只涉及 `_check.py` 与骨架文件两个受版本控制的文件；`bbs-together-addon/*` 等并发会话改动未触碰。
- [x] design §1 的「anchor 非空 ⇒ panel 非空」按实际情况修正为区分 chrome 锚点。
- [x] 人工冒烟边界记录为未执行（见 `prd.md`）。

## 反例验证记录（均已恢复并重建通过）

| 注入 | 结果 |
|------|------|
| 内容 anchor 改成 `morphing.nope` | `EXIT=1`，`FAIL step ch03_s6_demorph targets anchor "morphing.nope", which the host does not publish` |
| 面板锚点的 `panel` 清空 | `EXIT=1`，`FAIL ... has anchor "morphing.demorph" but no panel, so it can only ever be PANEL_NOT_MOUNTED` |
| 覆盖层加 `markContainer()` | `EXIT=1`，`FAIL UITutorialGuide calls markContainer(); the guide would swallow clicks meant for the control it is pointing at` |
| API 方法名改成 `navigateDashboardPanelRenamed` | `EXIT=1`，`FAIL BBSClientApi has no navigateDashboardPanelRenamed` |

## 实施期发现

- **API 签名检查不能用反射。** `BBSClientApi` 的方法签名里有 Minecraft 类型，裸 JVM `getMethod` 抛 `NoClassDefFoundError`，检查会静默 SKIP（第一版就是这样"通过"的）。改为读 class 文件常量池里的方法名与描述符，不加载任何类。`SourceText` 因此新增 `readClasspathBytes`。
- **`HostIds` 的反射是安全的**：`BBSDashboardPanelIds` / `BBSDashboardAnchors` 只 import JDK，零 BBS 依赖，加载不触发 `BBSMod` 静态初始化。
- **填充脚本跑了两遍导致 32 步重复键。** JSON 允许重复键、解析器取后者，所以两套值相同时表面无碍，但反例注入只改了前一组，检查读到的是后一组合法值——反例"失效"。已折叠去重并加重复键校验确认清零。教训：改完数据要验重复键，不能只验解析结果。

## 验证命令

```bash
cd bbs-tutorial-plugin
python ../.trellis/tasks/08-05-fsr-onboarding-tutorial-plugin/_check.py \
  ../.trellis/tasks/08-05-fsr-onboarding-tutorial-plugin/chapters-01-10-skeleton.json
./gradlew build     # 含 testTutorialContent + testTutorialWiring
echo "EXIT=$?"
```

## 已知边界

- 只覆盖 ch01–ch05 共 32 步；ch06–ch52 无字段。
- 指认精度到「面板 + 面板内主要入口」。滑块、标签、骨骼树、右键菜单项、弹层内控件没有宿主锚点，本切片不指。
- 引导态不持久化，关掉 Dashboard 即退出引导。
- 不做交互门禁，不做合成输入。

## 返工（2026-08-22）：覆盖层 v2 —— 人物走到按钮旁 + 框选

### 起因

人工冒烟反馈：引导时人物+台词卡作为整块（448×178）停在右下角，把被指控件区域盖住了，读者反而看不到「原来呈现的具体位置」。授权重写 `UITutorialGuide`。

### 新行为

- **跟随目标**：锚点可用时，人物+气泡整包（pack，378×160）贴到目标控件旁——优先右侧，放不下翻到左侧（人物始终站在靠按钮一侧，气泡在远侧），再放不下贴边并让框选画在最上层。垂直方向以按钮中心对齐，收在视口与任务bar之间。
- **平滑走位**：位置不瞬移，每帧按 0.3 系数向目标插值（glide），换步/换目标时人物 visibly「走过去」。布局推导放在首个渲染回调里逐帧做（宿主不给 overlay 提供 update 通道，渲染回调是唯一逐帧入口，与 v1 拖拽驱动同一先例）。
- **指向动作**：锚点可用期间强制 `TutorialActions.POINTING`（新增常量，`emote_pointing` 本就在已发布集合里），无锚点回落到该步脚本动作。
- **框选高亮**：弃用宿主 `renderHighlight` 软渐变（大面积控件上读作一层覆盖色），自绘蚂蚁线（5/7 虚线，相位用 `context.getTick()` 驱动爬行）+ 四角粗括号 + 极低透明度（0x28）着色，控件本身完全可见可点。
- **不遮挡**：不做全屏压暗/聚光镂空。气泡宽度按视口收缩（176 下限），pack 永远夹在视口内；读者拖动过（placed）则尊重手动位置，直到换步换目标。
- **保持不变**：根仍由宿主设 PASS、无 `markContainer()`（wiring 测试仍扫源码）；锚点 future 仍「请求-上一帧结果作画」且不在渲染路径 join；进度仍走 `TutorialProgress.shared()`；语言键零新增（32 键不变）。
- **跟随跳转（08-22 追加）**：覆盖层内点「上一步/下一步」时，若翻到的步骤带 `panel` 字段则自动 `navigateDashboardPanel` 跟过去（`followStepPanel()`，复用「带我去」同一 API），future 完成后 `refresh()` 让锚点对着新挂载的面板重新解析；导航失败静默降级为停靠+脚本动作（覆盖层在根层，本身不受面板切换影响）。

### 结构变化

`UITutorialGuide` 从「固定块+拖拽偏移」改为「每帧布局引擎」：`updateLayout()` 由 `renderBubble` 回调驱动，产出 pack 的绝对坐标并按需翻转人物/气泡左右次序与收缩气泡宽；`renderAnchorBox` 末位渲染保证框选压在人物之上。`characterOnRight`/`bubbleWidth` 变化才重设 flex，其余帧只改 pack 偏移。

### 验证

- `_check.py` 通过（errors 0，内容未动）。
- `./gradlew build`：testTutorialContent + testTutorialWiring 通过、EXIT=0（见下方当日记录）。
- 反例未新增检查项（wiring 的 PASS/markContainer 扫描继续覆盖本文件）。
- 人工冒烟仍待执行，prd 清单已按 v2 行为更新。

## 回滚点

- 阶段一（内容字段）独立可交付：加了字段但没接线，运行时行为不变。
- 阶段三若覆盖层方案受阻，阶段四可退化为「只导航、指引留在教程面板内」，仍优于现状。
- 发现需要新的宿主锚点时**停下**：那是 `new/` 的另一轮任务，不在本任务内扩。
