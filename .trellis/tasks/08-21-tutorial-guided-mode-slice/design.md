# 教程引导模式（ch01–ch05 切片）· 设计方案

## 0. 三个已定的决策

### 0.1 进度状态必须是单一实例，不能各持一份

`UITutorialPanel` 现在在构造期做 `TutorialProgress.load()`（`UITutorialPanel.java:43`）。覆盖层里也要能上一步/下一步，如果覆盖层自己再 `load()` 一次，就出现两份互不相干的读取位置：在覆盖层翻两页，切回教程面板还停在原处，且两边各自 `save()` 会互相覆盖。

**决定**：抽一个进程内单例（或由客户端 bootstrap 持有并注入），面板与覆盖层共享同一个 `TutorialProgress`。任何一侧翻页，另一侧下次 `refresh()` 就看到。这是本任务唯一的状态一致性要求，做错了症状是「翻页丢失」，很难在冒烟里立刻归因。

### 0.2 引导态不持久化

`TutorialProgress` 已持久化 gender / look / skin / skipped / 位置。引导态（当前是否在引导中）**不写盘**：读者关掉 Dashboard 再打开，不该被一个覆盖层迎面盖住。引导是一次会话内的临时模式，由读者显式进入。

这样也避开了旧进度文件的迁移问题——没有新字段，就没有缺省歧义。

### 0.3 覆盖层不得自造 tick 通道

`UITutorialCharacter.update()` 覆写了 `UIModelRenderer.update()` 来推 `form.update(entity)`（`UITutorialCharacter.java:82-90`）。这个 `update()` 是 `UIModelRenderer.render()` 里 `updateLogic(context)` 按 `context.getTick()` 补帧调的，**不经过面板生命周期**。所以人物挂在 root 层覆盖层里照样会动，什么都不用改。

宿主在 spec §3.4.4 明确写了「不要加 `UIDashboard.update()` 转发通道」。addon 侧同理：不要为覆盖层另造 tick 源。

## 1. 内容字段

给步骤加两个可选字段：

```
step: { …现有字段…, "panel": "<panel id>", "anchor": "<anchor id>" }
```

- `panel` 缺省 = 该步无「带我去」入口。
- `anchor` 缺省 = 导航但不画指向。
- **面板内**锚点非空时 `panel` 必须非空，否则解析必然返回 `PANEL_NOT_MOUNTED`。这条是内容检查的硬约束。

  **例外：Dashboard chrome 锚点。** 宿主给 `SETTINGS` / `SELECTORS` 登记的 `panelId` 是 null（`UIDashboard.java:137-138`），表示「不属于任何面板」，`resolveDashboardAnchor` 的面板判定会整条跳过。所以 `anchor: "dashboard.settings"` 配 `panel: ""` 是合法的——那个图标在哪个面板下都在。

  内容检查因此不能简单断言「anchor 非空 ⇒ panel 非空」，而要按锚点是不是 chrome 锚点分别处理。判定依据取宿主常量值：`dashboard.settings` / `dashboard.selectors` 这两个前缀属 chrome，`dashboard.panel_button.*` 也是（面板按钮在 taskbar 上，`panelId` 同样传 null）。

`TutorialStep.fromData` 照现有风格用 `data.getString(key, "")` 读取，缺字段降级空串。**不要**在解析期做白名单降级（对比 `action` 的 `orIdle`）：`action` 那样做是因为坏值会让动画静默卡住；这里坏值的后果是导航返回 `PANEL_NOT_FOUND` 或锚点 `CONTROL_NOT_FOUND`，两者都有明确状态可降级并记日志，不需要在解析期抹掉信息。

### 1.1 32 步的填法

按 prd 的三类划分。落值时只用宿主公开常量的产物：

- 面板：`BBSDashboardPanelIds.MORPHING` = `"morphing"`、`FILM` = `"film"` 等。
- 面板按钮锚点：`BBSDashboardAnchors.panelButton("morphing")` = `"dashboard.panel_button.morphing"`。
- Dashboard chrome：`BBSDashboardAnchors.SETTINGS` = `"dashboard.settings"`、`SELECTORS`。
- Morphing 内部：`MORPHING_PALETTE` / `MORPHING_PALETTE_LIST` / `MORPHING_PALETTE_EDITOR` /
  `MORPHING_DEMORPH` / `MORPHING_FROM_MOB`。
- Film 内部：selection / recorder / preview / duplicate、四个打开编辑器按钮、三个编辑器、
  camera timeline 与 replay keyframe editor；其余七个面板也提供各自一级入口锚点。

大致对应（实施时逐步核对 `instruction` 原文，本表是方向不是最终答案）：

| 步骤特征 | panel | anchor |
|---------|-------|--------|
| ch01_s3 点伪装/影片按钮 | `morphing` | `panelButton("morphing")` |
| ch01_s6 切到影片面板 | `film` | `panelButton("film")` |
| ch02_s5 点右上角设置图标 | 当前面板 | `SETTINGS` |
| ch03_s1 / ch03_s7 打开伪装 | `morphing` | `MORPHING_PALETTE` |
| ch03_s6 / ch04_s5 取消伪装 | `morphing` | `MORPHING_DEMORPH` |
| ch04 / ch05 在伪装面板内操作滑块、骨骼树、右键菜单 | `morphing` | 具体属性/菜单项仍为空；进入 palette/list/editor 等一级入口时可使用新增锚点 |
| 按键盘、去原版设置、纯理解步 | 空 | 空 |

编辑源 `chapters-01-10-skeleton.json` 与运行时 `content.json` 同步。沿用上轮教训：**用文本替换而非 `json.load`/`json.dump` 全量重写**，后者会把内联数组展开成多行，产生几百行无关格式噪声。

## 2. 覆盖层

### 2.1 结构

```
UITutorialGuide extends UIElement implements BBSDashboardOverlayContent
├── UITutorialCharacter          人物（复用，不改）
├── 步骤精简文本（台词 + 操作，不含预期/提示——覆盖层不该盖住半屏）
├── 上一步 / 下一步 / 回到教程 / 关闭引导
└── UIRenderable(锚点高亮绘制)   纯绘制，天然不吃输入
```

`root()` 返回 `this`。宿主已把包装与内容根都设为 `EventPropagation.PASS`（`UIExtensionDashboardOverlay.java:37-39`），**addon 不得把根改成 BLOCK**。人物与文本区做成靠一侧的小块，其余留空穿透。

### 2.2 锚点高亮

每帧在渲染回调里 `resolveDashboardAnchor` 的**结果缓存**上作画——注意区分两件事：

- **不能缓存矩形**（UI scale 可改、面板可滚动），所以每帧都要有新结果。
- 但 `resolveDashboardAnchor` 返回 `CompletableFuture`，在渲染路径里 `join()` 是错的。

**做法**：每帧发起一次解析，把结果存进字段，下一帧用上一帧的结果作画。API 内部走客户端线程执行器，实际是同帧内完成，视觉上无延迟。`available()` 为假时不画。

高亮绘制复用宿主 `UIDashboardPanels.renderHighlight(batcher, area, direction)`（`public static`），或用 `Batcher2D.outline` 自绘边框。

### 2.3 导航失败降级

| 状态 | 处理 |
|------|------|
| `NAVIGATED` / `ALREADY_ACTIVE` | 显示覆盖层 |
| `DASHBOARD_NOT_CREATED` / `NOT_DASHBOARD_SCREEN` | 提示读者先打开 Dashboard（语言键），不显示覆盖层 |
| `PANEL_NOT_FOUND` | 记日志（内容字段写错），退回面板内阅读 |
| `REJECTED` / `FAILED` | 记日志，退回面板内阅读 |

future 的回调在客户端线程，可直接改 UI 状态。

## 3. 注册与所有权

```
BBSTutorialClient.initialize()
  ├─ registerDashboardPanel(…)                        现有
  └─ registerDashboardOverlay(DESCRIPTOR, UITutorialGuide::new)   新增
       └─ 持有 BBSDashboardOverlaySubscription（初始隐藏）
```

订阅句柄由 `BBSTutorialClient` 静态持有，教程面板通过它 `setVisible(true/false)`。`registration().accepted()` 为假时只记日志——引导能力没了，教程面板照常可用（与面板注册失败同样的降级姿态）。

## 4. 测试

沿用现有两个裸 JVM 入口，不加新框架。**不得加载 BBS 类**（会触发 `BBSMod` 静态初始化）。

### 4.1 `testTutorialContent` 增补

- 32 步的 `panel` / `anchor` 值落在宿主公开常量集合内。集合从**宿主 jar 的 class 常量**或宿主源码抽取，不在测试里硬编码第二份清单。
- `anchor` 非空时 `panel` 必须非空。
- 其余 47 章无字段仍通过。

### 4.2 `testTutorialWiring` 增补

| 检查 | 失败时的真实症状 |
|-----|----------------|
| 三个 API 方法在宿主 jar 中存在且签名对得上 | 编译过了但运行期 `NoSuchMethodError` |
| 内容里用到的 anchor id 都在宿主 `BBSDashboardAnchors` 里 | 高亮永远不出现，无报错 |
| 覆盖层内容根未被设成非 PASS | 读者点不到真实按钮 |
| 新增 chrome 语言键中英齐备、`%s` 数量一致、无死键 | 英文玩家看中文 / 格式化异常 |

### 4.3 反例验证（必做）

每项新增检查都要确认它真的会失败：改坏 → 确认 exit 非 0 且报对的那条 → 恢复 → 确认 exit 0。上轮的教训是「新加的检查其实被 SKIP 了却显示绿」，只看 exit 0 不够。

## 5. 风险

| 风险 | 症状 | 缓解 |
|------|------|------|
| 面板与覆盖层各持一份进度 | 翻页丢失，难归因 | §0.1 单一实例 |
| 覆盖层根设成 BLOCK | 读者点不到真实按钮，功能自相矛盾 | §2.1 + §4.2 检查 |
| 渲染路径里 `join()` future | 卡帧或死锁 | §2.2 用上一帧结果 |
| 缓存锚点矩形 | 改 UI scale / 滚动后指错位置 | §2.2 每帧重发起 |
| 内容 anchor 写了不存在的 id | 高亮静默不出现 | §4.1 常量集合校验 |
| `anchor` 有值但 `panel` 空 | 必然 `PANEL_NOT_MOUNTED` | §1 硬约束 + 检查 |
| JSON 全量重写 | 几百行无关格式噪声 | §1.1 文本替换 |
| 引导态持久化 | 一开 Dashboard 就被覆盖层盖住 | §0.2 不持久化 |
| 宿主锚点不够细 | ch04/ch05 只能指到面板级 | prd 已明确接受，超出本切片 |

## 6. 交付后

- 冒烟通过后再决定 ch06–ch52 是否铺开（prd Out of Scope）。
- 若发现需要滑块/标签/右键菜单锚点，那是 `new/` 的新一轮任务，不在本任务内扩。
- `.trellis/spec/new/backend/external-addon-integration-contract.md` §3.4.4 已有宿主侧契约；本任务若发现契约描述与实际不符，回写该节。
