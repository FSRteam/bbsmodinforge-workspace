# Research: splitter 命中顺序（回放编辑器 editArea 邻接 splitter 不可拖的精确机制）

- **Query**: 回放编辑器里「时间线 vs 效果面板」边界为何拖不动；定位是「时间线 view 吞事件」还是「splitter 没设回 visible」还是别的；给方案 A 的精确改法方向
- **Scope**: internal（静态源码分析，未运行实测）
- **Date**: 2026-08-25

## 结论速览（先给答案）

1. **dock 子节点顺序**：`mount()` 里面板先 add（children 1..6），再 `add(borders, dropHighlight)`，再 add dragHandles；`setupFlex` 用 `addBefore(this.borders, handle)` 把 splitter handle 插到 borders **前面**。最终顺序：surfaces → 面板 → **splitterHandles** → borders/dropHighlight → dragHandles。
2. **事件命中**：`UIElement.childrenMouseClicked`（`UIElement.java:1472`）**逆序遍历**（i = size-1 → 0），splitter handle 比 main/editArea 面板**更靠后**，故**先于面板**收到点击 → **splitter 先命中、先吞事件**。时间线 view 不可能抢先吞掉 splitter 条带上的点击。
3. **splitter 自身命中**：`createSplitterHandle` 的 `subMouseClicked`（`UIDockLayout.java:1042`）守 `this.area.isInside(context)` + `editorResizablePanels`；`beginSplitterDrag`（`UIDockLayout.java:1125`）也只守 `editorResizablePanels`。`enabled` 供应器（`UIDockLayout.java:1064`）默认 true，dock 从不调 `setEnabled`。
4. **精确机制**：静态分析下，补丁 `keepFilmSplittersResizable` 在回放编辑器激活时**守卫成立**且**把 splitter 设回 visible**，splitter handle 在 children 里**先于面板**被遍历、`area`/`enabled`/`visible` 三者皆成立 → **应当可拖**。与用户「拖不动」相悖，故真凶落在「补丁未真正设回 visible / `area` 不在落点 / 运行期布局与默认不同」三者之一，**必须实测确认**。方案 A 改法方向不在「提升 z-order / 让 view 不吞」（已证明 splitter 先于 view），而在「确保补丁对 editArea 邻接那条 splitter 真的设回 visible 且其 area 落在屏幕可见内缘」。

---

## Findings

### Files Found

| File Path | 描述 |
|---|---|
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/layout/UIDockLayout.java` | dock 布局主实现：children 顺序、splitter handle 创建/命中、`updateTabVisibility`、补丁注入点 |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/UIElement.java` | 事件分发基类：`childrenMouseClicked` 逆序遍历、`isEnabled()==enabled&&visible` |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/UIDraggable.java` | splitter handle 基类：`subMouseClicked` 守 `enabled.get() && area.isInside` |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/UIRenderable.java` | borders 类型：`isEnabled()=false`、`mouseClicked=null`，**不吞事件** |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeEditor.java` | 时间线/inspector 归属：target 模式 view 占满、inspector reparent 到 editArea 视觉区但 child 在 editor |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframes.java` | 时间线 view：`subMouseClicked` 守 `graphArea.isInside`（graphArea=main 全宽含右缘） |
| `new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java` | film 面板注册顺序、`showPanel`、`isEditAreaPanelActive`、dock locked 默认 |
| `new/src/client/java/mchorse/bbs_mod/ui/film/replays/UIReplaysEditor.java` | `keyframeEditor.target(filmPanel.editArea)`（行 798）、editor 加到 replayEditor（行 974） |
| `new/src/main/java/mchorse/bbs_mod/settings/values/ui/EditorLayoutNode.java` | `defaultFilmLayout` 嵌套结构、`computeSplitterHandles` 厚度算法 |
| `bbs-snow-addon/src/client/java/mchorse/bbs_snow/mixin/client/UIDockLayoutMiniWindowMixin.java` | `keepFilmSplittersResizable` 补丁（351-376）、`FILM_PANELS`（41-42） |
| `bbs-snow-addon/src/client/java/mchorse/bbs_snow/verification/BBSSnowRuntimeVerification.java` | `verifyLockedFilmDockSplitters`（1330-1362）、`createVerificationDock`（1364） |
| `new/src/client/java/mchorse/bbs_mod/ui/utils/Area.java` | `isInside` 标准矩形命中 |

---

### 1. dock children 添加顺序（决定 z-order）

`UIDockLayout.mount()`（`UIDockLayout.java:248-262`）按固定顺序 add：

```java
public void mount()
{
    this.add(this.surfaces);                          // [0] surfaces

    for (UIElement panel : this.panelById.values())    // [1..N] 各面板
    {
        this.add(panel);
    }

    this.add(this.borders, this.dropHighlight);        // [N+1] borders, [N+2] dropHighlight

    for (UIDraggable handle : this.dragHandlesById.values())  // [N+3..] 拖出/重排手柄
    {
        this.add(handle);
    }

    this.setupFlex(false);
}
```

`add(IUIElement)`（`UIElement.java:285`）是 `children.add(element)`（**追加到末尾**）。
`add(IUIElement... elements)`（`UIElement.java:291-311`）按数组顺序逐个追加。

`panelById` 是 `LinkedHashMap`（插入序 = addPanel 调用序）。`UIFilmPanel`（`UIFilmPanel.java:209-212, 236-237`）注册顺序：
`main, preview, preview2, editArea, replaysList, replayProps`（6 个）。

**关键**：`setupFlex()` 里创建 splitter handle 时（`UIDockLayout.java:910-915`）：

```java
for (int i = 0; i < splitters.size(); i++)
{
    UIDraggable handle = this.createSplitterHandle(i);
    this.splitterHandles.add(handle);
    this.addBefore(this.borders, handle);   // 插到 borders 之前
}
```

`addBefore(IUIElement target, IUIElement element)`（`UIElement.java:330-345`）：`children.add(children.indexOf(target), element)` → **插到 borders 索引处**，borders 及其后元素整体后移。

**最终 children 顺序**（N=6 面板，S=splitter 数）：

| index | 元素 |
|---|---|
| 0 | surfaces |
| 1..6 | 面板（main, preview, preview2, editArea, replaysList, replayProps） |
| 7..7+S-1 | **splitterHandles**（插在 borders 前） |
| 7+S | borders |
| 7+S+1 | dropHighlight |
| 7+S+2.. | dragHandles（拖出/重排手柄） |

`borders` 是 `UIRenderable`（`UIDockLayout.java:103`），`UIRenderable.isEnabled()` 恒 false、`mouseClicked()` 恒 null（`UIRenderable.java:23-38`）→ **不参与事件分发**。

---

### 2. 事件分发命中链

`UIElement.mouseClicked`（`UIElement.java:1326-1336`）先 `childrenMouseClicked` 再 `subMouseClicked`：

```java
public final IUIElement mouseClicked(UIContext context)
{
    IUIElement element = this.childrenMouseClicked(context);
    if (element != null) return element;
    return this.subMouseClicked(context) || this.keybindsMouseClicked(context)
        || this.mouseClickedContextMenu(context) || this.cantPropagate(...) ? this : null;
}
```

`childrenMouseClicked`（`UIElement.java:1470-1488`）**逆序遍历**（i = size-1 → 0），先命中者 return 吞掉：

```java
protected IUIElement childrenMouseClicked(UIContext context)
{
    for (int i = this.children.size() - 1; i >= 0; i--)
    {
        IUIElement element = this.children.get(i);
        if (element.isEnabled())                       // gate: enabled && visible
        {
            IUIElement anElement = element.mouseClicked(context);
            if (anElement != null) return anElement;
        }
    }
    return null;
}
```

`UIElement.isEnabled()`（`UIElement.java:1197-1201`）：`return this.enabled && this.visible;`
→ **`setVisible(false)` 直接让 `isEnabled()` 返回 false，元素被跳过**。

`UIDockLayout` 不重写 `childrenMouseClicked`/`mouseClicked`（grep 确认 `UIDockLayout.java` 无重写）→ 用基类逆序遍历。

**逆序遍历顺序**（按上面的 children 索引，size-1 → 0）：
dragHandles → dropHighlight → borders → **splitterHandles** → 面板（editArea, ..., main） → surfaces。

故 dock 层：**splitter handle 在 main / editArea 面板之前被访问**。

- dragHandles 在 locked 下被 `updateTabVisibility`（`UIDockLayout.java:986`）`setVisible(show && !layoutLocked && active)` → locked 时 visible=false → `isEnabled()=false` → 跳过。
- dropHighlight / borders：不吞事件。
- 故**第一个真正可吞事件的元素 = splitter handle**。

---

### 3. splitter handle 自身命中守卫

`createSplitterHandle`（`UIDockLayout.java:1037-1082`）匿名重写 `subMouseClicked`：

```java
protected boolean subMouseClicked(UIContext context)
{
    if (context.mouseButton == 0 && this.area.isInside(context)
        && BBSSettings.editorResizablePanels.get()
        && UIDockLayout.this.consumeSplitterDoubleClick(index))
    { return true; }

    UIDockLayout.this.beginSplitterDrag(index, context.mouseX, context.mouseY);
    boolean handled = super.subMouseClicked(context);   // UIDraggable.subMouseClicked
    if (!handled) { UIDockLayout.this.clearSplitterDragState(); }
    return handled;
}
```

`UIDraggable.subMouseClicked`（`UIDraggable.java:117-136`）：

```java
if (this.enabled.get() && this.area.isInside(context) && context.mouseButton == 0 && !this.isDragging())
{ ... acquire drag ownership; return true; }
return super.subMouseClicked(context);
```

`beginSplitterDrag`（`UIDockLayout.java:1125-1131`）只守 `editorResizablePanels` + index 范围，无 `isInside` 复检。
`enabled` 供应器（`UIDockLayout.java:1064`）：`handle.enabled(() -> BBSSettings.editorResizablePanels.get())` — 默认 true，dock 全程不调 `setEnabled`。

**因此 splitter 能吞事件的三前提**：① `visible=true`（补丁设回）② `enabled` 供应器=true（默认）③ `area.isInside(context)`（area 由 `applySplitterHandleBounds` 在 `setupFlex` 时按 `splitterHandleInfos` 设定）。

`applySplitterHandleBounds`（`UIDockLayout.java:1004-1027`）：大编辑器用 `SPLITTER_HANDLE_PX=14`（`UIDockLayout.java:55`）固定 14px 厚度条带；小编辑器用归一化厚度。条带中心 = splitter 比例位置（`computeSplitterHandles` 算出的 `hx/hy`）。

---

### 4. 面板子树是否吞事件

- **editArea 面板**：`new UIElement()`（`UIFilmPanel.java:206`），无子、无重写 `subMouseClicked`（基类返回 false，`UIElement.java:1572-1575`）→ **editArea 不吞任何点击**。
- **main 面板**：`new UIElement()`（`UIFilmPanel.java:205`），其子为 `cameraEditor/replayEditor/actionEditor`（`UIFilmPanel.java:287`）。回放编辑器激活时仅 `replayEditor` 可见（`UIFilmPanel.java:608`）。
- **replayEditor**（`UIReplaysEditor extends UIElement`，`UIReplaysEditor.java:89`）：不重写 `childrenMouseClicked`/`mouseClicked`（grep 确认无重写）。其子含 `keyframeEditor`（`UIReplaysEditor.java:974 this.add(replacement)`）。
- **UIKeyframeEditor**（`UIKeyframeEditor.java:34`）：不重写 `subMouseClicked`（grep 确认无 `subMouseClicked`）。构造时 `this.add(this.view.full(this).w(1F,-w))` + `this.add(this.splitter)`（`UIKeyframeEditor.java:72-73`）。
- **target 模式下**（`UIKeyframeEditor.java:92-100`）：`this.view.resetFlex().full(this).w(1F)` → view 占满整个 editor（=main 面板）至右/下缘。`updateSplitterState`（`UIKeyframeEditor.java:249-252`）`splitter.setVisible(this.target==null && propertiesVisible)` → target 模式编辑器自带 splitter **隐藏**，不参与。
- **inspector（UIKeyframeFactory）归属**（`UIKeyframeEditor.java:134-142, 174-177`）：`replacement.relative(this.target)`（视觉锚到 editArea）但 `this.add(replacement)`（child 仍在 UIKeyframeEditor，即 main 子树）→ **inspector 视觉覆盖 editArea，但事件上属 main 子树**。
- **时间线 view 的命中**（`UIKeyframes.java:1247-1269`）：`subMouseClicked` 先 `currentGraph.mouseClicked`，再 `graphArea.isInside(context)` 才吞。`graphArea.copy(this.area)`（`UIKeyframes.java:1230`）= view 的 area = main 全宽（含 main 右/下缘，与 splitter 条带重叠）。

**关键定性**：在 dock 层，splitter handle 先于 main 面板被遍历，所以无论 view 的 `graphArea` 是否覆盖到 splitter 条带，**dock 都先把点击交给 splitter handle**。view「吞事件」只在 splitter handle 未命中时才发生。

---

### 5. 默认 film 布局与 editArea/main 邻接关系

`EditorLayoutNode.defaultFilmLayout()`（`EditorLayoutNode.java:124-148`）：

```
Splitter(horizontal=false, 0.18)              // 按宽切，竖线
├ first:  Splitter(horizontal=true, 0.286)   // replayProps(上) / replaysList(下)
└ second: Splitter(horizontal=true, 0.666)    // 按高切，横线
          ├ first:  Splitter(horizontal=false, 0.793)  // 按宽切，竖线
          │         ├ preview
          │         └ editArea
          └ second: main                       // 时间线宿主在下半
```

`horizontal=true` = 按高度切（横分隔线），`horizontal=false` = 按宽度切（竖分隔线）（`EditorLayoutNode.java:703-704` 注释）。

**editArea 与 main 并非直接相邻**：editArea 在「preview|editArea」竖 splitter 里（上层 first 的 first），main 在上层 second。分隔「上层 first（preview/editArea 一组）」与「main」的是**第二条横 splitter（layer-2, horizontal=true, ratio=0.666）**——这是用户拖的「时间线↔效果面板」边界。editArea 在该横线上方，main（时间线 view）在下方。时间线 view 顶缘 = 该横线 = splitter 条带位置。

`computeSplitterHandles`（`EditorLayoutNode.java:673-699`）深度优先产生 3 个 handle，layer-2 那个 handle 的 area = 跨整宽、位于 y = 0.666*h 附近的 14px 横条带。

---

### 6. 补丁守卫与可见性恢复

`keepFilmSplittersResizable`（`UIDockLayoutMiniWindowMixin.java:351-376`），注入 `updateTabVisibility` TAIL：

```java
private void bbs_snow$keepFilmSplittersResizable(CallbackInfo ci)
{
    UIDockLayout self = (UIDockLayout)(Object) this;
    Map<String, UIElement> panels = this.bbs_snow$access().bbs_snow$getPanelById();

    if (!self.isLocked()
        || !panels.keySet().containsAll(bbs_snow$FILM_PANELS)
        || !BBSSettings.editorResizablePanels.get())
    { return; }

    boolean filmVisible = bbs_snow$FILM_PANELS.stream()
        .map(panels::get)
        .anyMatch((panel) -> panel != null && panel.isVisible());

    if (filmVisible)
    {
        for (UIDraggable splitter : this.bbs_snow$access().bbs_snow$getSplitterHandles())
        { splitter.setVisible(true); }
    }
}
```

- 守卫三条件：`isLocked` + `containsAll(FILM_PANELS={"main","preview","editArea","replaysList","replayProps"})` + `editorResizablePanels`。
- `filmVisible` = 任一 FILM 面板 `isVisible()`。
- 面板可见性在 `updateTabVisibility`（`UIDockLayout.java:975-978`）设为 `isPanelActive(id)`，**不乘 `layoutLocked`** → locked 下面板仍按 active 保持可见。
- 回放编辑器激活时 `editArea` active（`UIFilmPanel.java:592-594 isEditAreaPanelActive`）→ `isVisible()=true` → `filmVisible=true` → **补丁把所有 splitterHandles 设回 visible**。

**补丁无差别地把全部 splitterHandles 设回 visible**（含 editArea 邻接的那条），没有按 splitter 邻接关系筛选。

---

## 精确根因（静态判定 + 实测待确认项）

**静态判定一致**：守卫成立 → splitter 设回 visible → splitter handle 在 children 里先于面板被遍历 → `enabled/visible` 皆 true → 只要 `area.isInside` 就吞事件并拖动。**静态预期 = 可拖**。与用户「拖不动」相悖。

故真凶只能落在以下三者之一，**必须实测二选一确认**：

| 候选 | 证据缺口 | 判定方式 |
|---|---|---|
| **R1 补丁未真正设回 visible** | 静态推断守卫成立，但运行期布局/active 状态可能与默认不同（用户可能改过布局、editArea 可能非 active） | 实测 `keepFilmSplittersResizable` 进入 `if(filmVisible)` 分支、splitter `isVisible()=true` |
| **R2 `area` 不在鼠标落点** | splitter 条带由 `applySplitterHandleBounds` 按 `splitterHandleInfos` 设；若 `effectiveLayoutTree`/`ensureLayoutPanels` 在运行期裁剪了树（面板被隐藏/maximize），splitter 数或位置可能与默认不同 | 实测鼠标落点处那条 splitter 的 `area.x/y/w/h` 是否覆盖该点 |
| **R3 别的拦截**（可能性最低） | 已排除：borders/dropHighlight 不吞、editArea 空不吞、UIKeyframeEditor/UIReplaysEditor/UIClipsPanel 均不重写 `childrenMouseClicked`、编辑器自带 splitter target 模式隐藏、dragHandles locked 时隐藏 | — |

**最可能 = R1 或 R2**：用户实际布局可能与 `defaultFilmLayout` 不同（预览/editArea 被移除或折叠，或 editArea 非当前 active tab），导致补丁守卫对 editArea 邻接那条 splitter 的恢复条件不满足，或该 splitter 根本不在当前 `effectiveRoot` 里。

**时间线 view 吞事件**（design 草稿曾怀疑方向）**被排除**：splitter handle 在 dock children 里先于 main 面板（view 宿主）被遍历，view 不可能抢先吞 splitter 条带上的点击。

---

## 方案 A 精确改法方向

**不是**「提升 splitter z-order」（已最高）/「让 view 不吞」（已证明 view 在后）/「splitter 命中条带排除 view」（无必要）。

**应是**：

1. **先实测确认 R1/R2**：strict verification 加一个 case，构造 locked film dock + editArea 承载 inspector 场景，断言 editArea 邻接那条 splitter 在 `editorResizablePanels=true` 时 `isVisible()=true` **且 `area` 覆盖 main/editArea 交界**。现有 `verifyLockedFilmDockSplitters`（`BBSSnowRuntimeVerification.java:1330-1362`）只断言 `isVisible()`，**不查 area/不查 dragable**——这就是它没抓住本 bug 的原因。
2. **若 R1**（补丁未覆盖）：补丁无差别全设 visible 已是最宽，问题在守卫的 `filmVisible` 判定或 editArea active 判定。改法：放宽 `filmVisible` 判定（如改用 `isPanelActive` 而非 `isVisible()`，或把 editArea/main 的 active 状态显式纳入），或补一条针对回放编辑器场景的 `@Inject` 单独恢复 editArea 邻接 splitter。
3. **若 R2**（area 不在落点）：查 `effectiveLayoutTree`/`ensureLayoutPanels` 在用户布局下是否裁掉了 editArea 或改变了 splitter 拓扑；改法可能是保证 editArea 邻接那条 splitter 始终在 `splitterHandleInfos` 里且 area 落在交界。

---

## Caveats / Not Found

- **未运行实测**：本报告为纯静态分析。design.md（已存于任务目录）的「实测前方向预判」与本报告一致，其 implement.md 第 1 步 gate 要求 strict verification 实测确认 R1/R2，本任务不改代码故未执行实测。
- **用户实际运行布局未知**：`defaultFilmLayout` 是默认值，用户可能拖动/预设过布局，实际 `effectiveRoot` 拓扑可能不同；R2 依赖此。
- **`editorResizablePanels=false` 分支**已确认不可拖（补丁提前 return + `enabled` 供应器 false + `updateTabVisibility` 设 `show && !locked`）���符合预期，无回归风险。
- **`new/` 与 `new-fs25-sync/` 0 差异**（design.md 第 2 节已核实），本报告引的 `new/` 行号对两者等价。
- **未确认**：`bbs_snow$getSplitterHandles` accessor 返回的 list 是否与 `this.splitterHandles` 同一引用（补丁遍历的是它）；从命名与验证测试用法看应一致，但未读 accessor 源码。

---

## 实测结论（2026-08-25，strict verification）

实测方式：扩展 `bbs-snow-addon/.../BBSSnowRuntimeVerification.verifyLockedFilmDockSplitters`，新增 accessors `bbs_snow$getSplitterHandleInfos` / `bbs_snow$getDraggedSplitterIndices`（`UIDockLayoutAccessor` + `UIDockLayoutAccess`）。`cd bbs-snow-addon && ./gradlew runClient -Pverify=1`：exit code=0，日志无 injection failure，`S9 runtime verification passed` 一次。

### 实测断言与结果（默认布局树 `defaultFilmLayout`，900×600 locked film dock + 5 面板）

| 断言 | 结果 |
|---|---|
| 补丁把全部 splitterHandles 设回 visible | ✅ 通过 |
| 每条 splitterHandle `area.w>0 && area.h>0` 且落在 `dock.area` 矩形内 | ✅ 通过 |
| editArea 邻接 splitter 定位（`findEditAreaMainSplitterIndex`，像素几何）= 索引 2 | ✅ 找到（第二层 `[preview|editArea] vs main` 横 splitter） |
| 在索引 2 的 `area.mx()/my()` 点击 `sizedFilmDock.mouseClicked`：`draggedSplitterIndices` 含索引 2 | ✅ **拖动启动** |
| `editorResizablePanels=false` + `refreshVisibility()` 后同点点击：`draggedSplitterIndices` 空 | ✅ 不启动 |
| 普通 locked dock（无 film 面板集）splitter 仍 hidden | ✅ 通过（防泄漏） |

### 定论

- **默认布局树下，locked film dock 的 editArea 邻接 splitter 三前提（visible / enabled / area 在落点）全部成立，且点击能正常启动 `beginSplitterDrag`**——验证场景下 splitter **可拖**，与用户「拖不动」相悖。
- **R1/R2/R3 在默认树+验证场景下均不成立**。bug 不在 dock splitter 的可见性/命中/启用层（补丁 `keepFilmSplittersResizable` 对默认树正确生效）。
- **真凶在默认树之外 = R4（实测发现的新情况）**：回放编辑器激活时实际布局树被 `UIFilmPanel.ensureFilmLayoutPanels`/`copyWithInsertSplitAt`（`UIFilmPanel.java:811/816/824`）改写，或运行期 inspector `w(1F).h(1F,-top)` / `UIKeyframeEditor` 子树吞了本应到达 dock 层的外缘鼠标事件。验证场景未覆盖这两者。

### 关键证据值（900×600 默认树，editArea 邻接 splitter = 索引 2）

- editArea.area：x≈728, y≈0, w≈148, h≈400（上组右半）。
- main.area：x≈162, y≈400, w≈738, h≈200（下整宽）。
- 索引 2 splitter.area：x≈162, y≈399.6 附近, w≈738, h=14（横条带）。
- 点击点 = splitter 中心 → `draggedSplitterIndices=[2, ...]` → drag ownership acquire → 拖动启动。

### 下一步方向（交主会话定）

按协调员第 3 步：在 verification 里构造 **回放场景布局树**（`copyWithInsertSplitAt(out, editArea, replaysList, EDGE_BOTTOM)` + `copyWithInsertSplitAt(out, replaysList, replayProps, EDGE_RIGHT)`，模拟 `ensureFilmLayoutPanels` 对非默认布局的补全），再查 editArea 邻接 splitter 的 visible/area，定 R2 变体（area 不在新树落点）或 R3 变体（命中/事件分发）。当前实测**排除**了「默认树下 splitter 本身不可拖」这条死路，避免继续在补丁守卫/命中顺序上空钻。
