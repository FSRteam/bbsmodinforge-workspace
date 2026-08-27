# Design：回放关键帧编辑器时间线与效果面板边界可拖动

## 1. 事实链（已核实到行）

### 1.1 三套 splitter 机制

| 机制 | 位置 | 作用域 | 状态 |
|---|---|---|---|
| ① film 主 dock splitter | `bbs-snow-addon` `UIDockLayoutMiniWindowMixin.bbs_snow$keepFilmSplittersResizable` (commit `7c6c016`) | film 主布局 5 面板 `{main,preview,editArea,replaysList,replayProps}` locked 时仍可拖 | ✅ 已提交 |
| ② 编辑器自带 splitter | `new/` `UIKeyframeEditor.createSplitter/updateSplitterState` (commit `497c4284`) | **仅 `target==null` (embedded) 模式** | ✅ 已提交，target 模式被守卫排除 |
| ③ 动画状态 dock splitter | `bbs-snow-addon` `UIFormEditorAnimationStateLayoutMixin.bbs_snow$stateDock` | `{statePreview,stateTimeline,stateInspector}` | ❌ 未修（本任务不在范围） |

### 1.2 回放编辑器路径（用户确认：顶栏场景图标进入）

```
UIFilmPanel
  dock: UIDockLayout, locked(true) by default (UIFilmPanel.java:220)
    panels: {main, preview, editArea, replaysList, replayProps}
    splitterHandles: 面板之间的 UIDraggable
  replayEditor: UIReplaysEditor.full(main)
    keyframeEditor: UIKeyframeEditor.target(filmPanel.editArea)  ← target 模式
      view (时间线): 在 editor 内部
      editor/inspector (效果面板): reparent 到 filmPanel.editArea, w(1F).h(1F,-top)
                                     (UIKeyframeEditor.java:141) → 占满 editArea 面板
```

### 1.3 为什么不能拖（根因）

回放编辑器「时间线 ↔ 效果面板」的边界 = **editArea 面板与其相邻面板（main/timeline 宿主）之间的 dock splitter**。

- `UIKeyframeEditor` 自带 splitter（机制②）在 target 模式被 `updateSplitterState()`
  (`UIKeyframeEditor.java:251`) 的 `target == null` 守卫隐藏 → 不参与。
- 边界实际由 dock splitter（机制①）接管。`updateTabVisibility`（`UIDockLayout.java:994-997`）
  在 `layoutLocked` 时把所有 splitter `setVisible(false)`。
- `keepFilmSplittersResizable` 补丁注入在 `updateTabVisibility` TAIL，把 film dock 的 splitter
  设回 `setVisible(true)`，守卫：`containsAll(FILM_PANELS) && editorResizablePanels && 任一 film 面板可见`。

**缺口定位（已澄清）**：`updateTabVisibility` 三类对象可见性不同：
- 面板（panel，第977行）：`setVisible(isPanelActive(id))`，**不乘 `!layoutLocked`** → locked 时面板仍按 active 保持可见。
- dragHandles（拖出/重排手柄，第986行）：`setVisible(show && !layoutLocked && active)` → locked 时隐藏（这是 locked 的本意：禁止拖出/重排）。
- splitterHandles（第994-997行）：`setVisible(show && !layoutLocked)` → locked 时隐藏。

补丁 `keepFilmSplittersResizable` 注入在 TAIL，守卫三条件 `isLocked + containsAll(FILM_PANELS) + editorResizablePanels`，
再 `filmVisible = 任一 film 面板 isVisible()`。回放编辑器激活时 `editArea` 是 active → `isVisible()=true` →
**守卫成立** → 补丁把所有 splitterHandles 设回 `setVisible(true)`。

**因此根因不是「守卫未生效」，而是「splitter 被设回 visible 但仍拖不动」**，落在命中/启用层：
- dock splitter handle 的 `enabled` 供应器是 `handle.enabled(() -> editorResizablePanels.get())`（`createSplitterHandle` 第1064行），默认 true。
- 命中靠 `splitter.area.isInside(context)`（第1044行 `subMouseClicked`）+ `beginSplitterDrag`（第1127行同开关）。
- inspector 占满 editArea **内部** `w(1F).h(1F,-top)`，splitter 在 editArea **外缘**与邻居面板交界——理论上 inspector 不吞外缘 splitter。

→ **实现前确认收敛为一点**：回放编辑器激活时，editArea 邻接的那条 dock splitter 是否真的被补丁设回 visible、其 `area` 是否落在可见屏幕区、鼠标落在外缘时事件是否先被 dock 层消费还是被 inspector/上层吞掉。
（此前 design 草稿里「守卫可能不成立」的假设已排除——面板可见性不乘 locked，editArea active 即 visible。）

## 2. 上游对照（权威源 = temp/bbs-fs-2.3 origin/master，commit 8d99d77f，fs 2.5）

Trellis `08-23-fs-25-upstream-sync` 记录：对齐目标是上游 `Wemppy4/bbs-fs` master `8d99d77f`（fs 2.5），
源范围 `770c0e7c..8d99d77f` 共 38 提交。`temp/bbs-fs-2.3` 是该上游的 clone（`origin/master` = `8d99d77f`）。

**「FSR」指谁**：`new/` 与 `new-fs25-sync/` 是同一仓库的两个 worktree（remote `cnb.cool/Twiap_Stuido/build_java`）：
- `new/` 分支 `codex/new-local-backup-20260727`（HEAD `497c4284`），**`bbs-snow-addon/build.gradle` 编译链接的就是 `../new/src`**（第64-65行）。
- `new-fs25-sync/` 分支 `codex/sync-fs-8d99d77f-to-fsr`（HEAD `6a61f72f`），fs 2.5 同步工作分支。
- 两者 `UIDockLayout.java`、`UIKeyframeEditor.java` **0 差异** → fs 2.5 同步未改 dock 实现，二者同属一套。
故「FSR 这套 dock 实现」= `new/` 与 `new-fs25-sync/` 共有的那套；**上游对照只能用 `temp/bbs-fs-2.3` origin/master，不能用 `new-fs25-sync`**（它也是 FSR，不是上游）。

直接对比上游源 `8d99d77f` 的 `UIDockLayout.java` 与 `new/`（`diff` 共 1454 行）——**两者是两套不同的 dock 实现**：

| 维度 | 上游 fs 2.5（`8d99d77f`） | FSR `new/` |
|---|---|---|
| 面板表 | `slotById` / `UIDockSlot` + `slot.dragHandle` | `panelById` / `UIElement` + `dragHandlesById` |
| `updateTabVisibility` 碰 splitterHandles? | **不碰**（只设 slot + slot.dragHandle + tabs） | **碰**：第994-997行 `for splitterHandles setVisible(show && !layoutLocked)` |
| `setupFlex` 里 locked 隐藏 splitter? | 无 | 第917-923行只隐藏 `dragHandlesById`（OK），但 `updateTabVisibility` 又隐藏 `splitterHandles` |

**结论（权威）**：
1. **上游 fs 2.5 locked 下 splitter 本就可拖**——`updateTabVisibility` 不碰 splitterHandles，splitter 始终保持 `applySplitterHandleBounds` 设的状态，locked 不隐藏它们。
2. **FSR `new/` 用的是另一套（更早/改写的）dock 实现**，在 `updateTabVisibility`（994-997行）**额外加了** `splitterHandles` 的 `setVisible(show && !layoutLocked)` → locked 时隐藏 splitter → 不可拖。**这段是 FSR 移植时引入的，上游没有**。
3. 这**确实是移植引入的回归**（research agent 方向正确，只是它查的旧 1.20.1 jar 非权威源；权威证据是上游 fs 2.5 源码）。
4. `keepFilmSplittersResizable`（`bbs-snow-addon` `7c6c016`）是对该回归的补丁：把被 FSR `updateTabVisibility` 隐藏的 film dock splitter 在 locked 时设回 visible。
5. 用户说的「最新面板有改动」= 上游 fs 2.5 的 dock 体系已大改（`slotById`/`UIDockSlot` 新体系），FSR 还停留在旧 `panelById` 体系。全量换上游新体系工程量巨大，**不在本任务范围**。

**修复边界**：`UIDockLayout`/`UIKeyframeEditor` 与上游是两套实现，不能简单"改成上游那样"。
务实方案是**在 FSR 现有旧体系上，补全 `keepFilmSplittersResizable` 在回放编辑器 target 模式的覆盖**（方案 A），
不碰 `new/`、不换 dock 体系。方案 B（改 `new/`）排除——会和上游两套实现的差异纠缠，回归面大。

## 3. 候选方案

### 方案 A：修 dock splitter（让 editArea 邻接 splitter 在回放编辑器场景下可见可拖）

**改动点**：`bbs-snow-addon` `UIDockLayoutMiniWindowMixin.bbs_snow$keepFilmSplittersResizable`，
确认/修正其守卫与循环，使回放编辑器激活时 editArea 邻接的那条 splitter 保持 visible+enabled 且可被命中。

- 优点：与已有补丁同一机制，改动集中；不碰 `new/` 主 mod；自动复用 `editorResizablePanels`
  开关、`onLayoutSettled` 持久化（dock 布局已持久化到 `editorLayoutSettings`）。
- 缺点：需先实测定位「visible 但不可拖」的精确原因（命中区被吞 / enabled / area 落点）。
- 风险：低，行为已由 `verifyLockedFilmDockSplitters` 覆盖，扩展即可。

### 方案 B：让编辑器自带 splitter 在 target 模式也可用

**改动点**：`new/` `UIKeyframeEditor.updateSplitterState`，把守卫从 `target == null` 放宽，
并在 target 模式下让 splitter 拖动时改 `propertiesWidth` 并把 inspector 从「占满 editArea」改为
「占 editArea 右侧 propertiesWidth 宽」。

- 优点：与 embedded 模式行为一致，宽度走 `editorKeyframePanelWidth` 持久化。
- 缺点：动 `new/` 主 mod（跨仓库）；target 模式下 inspector 当前是 `w(1F).h(1F)` 占满 editArea，
  改成窄条会破坏现有 miniwindow/embed 逻辑（`UIKeyframeInspectorEmbedMixin`、
  `UIFilmPanelMiniWindowMixin.bbs_snow$embedKeyframeInspector` 都假设 inspector 占满 editArea）；
  回归面大。
- 风险：高，触及 inspector 归属契约。

## 4. 推荐方案：A（修 dock splitter）

理由：根因在 dock splitter「被设回 visible 但仍拖不动」，而非编辑器自带 splitter。方案 A 改动集中在一个 mixin
方法、不碰 `new/`、不破坏 inspector 归属契约，且持久化复用 dock 布局现成机制。
任务性质是修复移植回归残留（见第2节反编译证据），补全已有补丁覆盖是正解。

## 5. 实现前必须确认的待定项

1. 回放编辑器激活 + 选中关键帧时，editArea 邻接那条 dock splitter 是否被补丁设回 visible=True、
   `area` 落在可见屏幕区、`enabled` 供应器=true。（守卫成立已澄清，见 1.3。）
2. 若三者皆成立但仍不可拖：定位鼠标在 editArea 外缘时，事件分发是先到 dock splitter 还是先到
   inspector（占满 editArea 内部但不含外缘）或 `UIKeyframeEditor`。重点查 `UIKeyframeEditor` 是否拦截了
   本应到达 dock 层的外缘鼠标事件（editor `relative(this).x(CATEGORY_BAR_WIDTH)...` 区域与 splitter 外缘的重叠）。

## 6. 验证策略

- 扩展 `BBSSnowRuntimeVerification.verifyLockedFilmDockSplitters`：新增一个 case，构造含 editArea
  且模拟「editArea 承载 inspector」的 locked film dock，断言其 editArea 邻接 splitter 保持可见可拖。
- strict `-Pverify=1` 全绿。
- 人工冒烟：顶栏场景图标进回放编辑器 → 选中关键帧 → 拖时间线/效果面板边界 → 宽度变化且重开保留。

## 7. 回归防护

- clip embedded 编辑器 splitter（机制②）不动 → 不受影响。
- 动画状态 dock（机制③）面板集与 FILM_PANELS 不相交，方案 A 守卫不变 → 不受影响。
- inspector embed 逻辑不碰 → 不受影响。
- 验证测试加「普通 locked dock splitter 仍隐藏」防泄漏（已有）。
