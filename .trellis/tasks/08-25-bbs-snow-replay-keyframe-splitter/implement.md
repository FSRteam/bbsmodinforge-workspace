# Implement：回放关键帧编辑器时间线与效果面板边界可拖动

## 前置说明

本任务跨两个独立 git 仓库（`new/` 主 mod、`bbs-snow-addon`），推荐方案 A 主要改 `bbs-snow-addon`。
实施前必须先完成「实现前确认」（第 1 步），其结论决定第 2 步的精确改动。

## 执行清单（按序）

### 1. 实现前确认（决定方案 A 的精确改动点）`[gate]`

**已澄清（静态推断）**：补丁守卫三条件在回放编辑器激活时**成立**——editArea 是 active，而面板可见性（`UIDockLayout.java:977` `setVisible(isPanelActive(id))`）不乘 `layoutLocked`，故 editArea `isVisible()=true`，`filmVisible` 成立，补丁把 splitterHandles 设回 visible。**因此根因不是「守卫未生效」，而是「splitter 被设回 visible 但仍拖不动」**，属命中/启用层。

实施前用 strict verification 实测确认以下一点：
- [ ] 1.1 回放编辑器激活 + 选中关键帧时，editArea 邻接那条 dock splitter 是否：被设回 visible=True、
  `area` 落在屏幕可见区、`enabled` 供应器=true。三者皆成立但仍不可拖 → 问题在**事件被 inspector/上层吞掉**。
- [ ] 1.2 若 1.1 三者皆成立但不可拖：定位鼠标在 editArea 外缘时，事件分发是先到 dock splitter 还是先到
  inspector（占满 editArea 内部但不含外缘）或 UIKeyframeEditor。重点查 `UIKeyframeEditor` 是否拦截了
  本应到达 dock 层的外缘鼠标事件（editor `relative(this).x(CATEGORY_BAR_WIDTH)...` 区域与 splitter 外缘的重叠）。

**产出**：确认结论写入「确认结果」节，据此选第 2 步子项。

### 2. 实施修复 `[gate after 1]`

依第 1 步结论二选一：

#### 2A. 守卫成立但 splitter 仍不可拖（命中区/覆盖问题）
- [ ] 2A.1 在 `bbs-snow-addon` mixin 里调整 editArea 邻接 splitter 的命中或 inspector 覆盖，
  使该 splitter 可被鼠标命中拖动。优先不降低 inspector 命中区，而是在 splitter 侧加宽命中条带或
  提升 splitter z-order。
- [ ] 2A.2 若发现是 inspector `w(1F).h(1F,-top)` 覆盖了 splitter 命中区，评估让 inspector 在
  splitter 命中条带内不吞事件（与 dock 已有的 `isInsideSplitterIntersectionHitbox` 机制对齐）。

#### 2B. 守卫不成立（`editArea` 在回放场景被判不可见等）
- [ ] 2B.1 修正 `keepFilmSplittersResizable` 守卫，使回放编辑器激活时 editArea 邻接 splitter 仍恢复可见。
  方式：放宽 `filmVisible` 判定（如改用 `isPanelActive` 或把 editArea 的 active 状态纳入），
  或补一条 `@Inject` 针对回放编辑器场景单独恢复该 splitter。
- [ ] 2B.2 守卫放宽后确保不泄漏到普通 locked dock（验证测试守住）。

### 3. 验证扩展 `[gate]`

- [ ] 3.1 在 `BBSSnowRuntimeVerification` 扩展或新增 case：构造 locked film dock + editArea 承载 inspector
  的场景，断言 editArea 邻接 splitter 在 `editorResizablePanels=true` 时可见可拖、`=false` 时不可拖。
- [ ] 3.2 保留「普通 locked dock splitter 仍隐藏」防泄漏断言。

### 4. 构建验证 `[gate]`

- [ ] 4.1 `cd bbs-snow-addon && ./gradlew runClient -Pverify=1`，真实 exit code 0 + 日志无 injection failure。
  （红线：见 [[gradle-pipe-exitcode-trap]]，管道吞退出码，必须双确认。）
- [ ] 4.2 若改了 `new/`（方案 B 才会），`cd new && ./gradlew compileClient`/对应目标确认编译。

### 5. 提交 `[gate after 4]`

- [ ] 5.1 `bbs-snow-addon`（若方案 A）：一个 commit，message 形如
  `fix(ui): keep replay keyframe editor splitter resizable in target mode`。
- [ ] 5.2 `new/`（仅方案 B 才需要）：单独 commit。
- [ ] 5.3 外层仓库记录任务进展（journal + task commit markers，按 Trellis 约定）。

### 6. 人工冒烟交付

- [ ] 6.1 告知用户新 jar 路径 `bbs-snow-addon/build/libs/bbs-snow-addon-fsr-*.jar`。
- [ ] 6.2 冒烟清单：顶栏场景图标进回放编辑器 → 选中关键帧 → 拖时间线/效果面板边界 →
  宽度变化 → 重开影片比例保留 → `editorResizablePanels=false` 时不可拖。

## 回滚点

- 每步 `[gate]` 前确保改动可回退（mixin 改动直接 revert commit 即可）。
- 第 1 步若结论推翻方案 A（例如确认 dock splitter 已可见可拖而问题在别处），回到 design 重新评估方案 B。

## 确认结果（第 1 步完成后填）

实测方式：扩展 `BBSSnowRuntimeVerification.verifyLockedFilmDockSplitters`（addon，`-Pverify=1` strict 跑绿，exit code=0，日志无 injection failure，`S9 runtime verification passed`）。新增断言：
- 构造 **带真实尺寸** 的 locked film dock（`UIBaseMenu` 900×600，`sizedFilmDock.relative(menu.main).wh(1F,1F)`，`menu.resize(900,600)`）+ `defaultFilmLayout` + 5 面板 `{main,preview,editArea,replaysList,replayProps}`。
- 补丁把全部 splitterHandles 设回 visible：**通过**。
- 每条 splitterHandle 的 `area.w>0 && area.h>0` 且落在 dock.area 矩形内：**通过**。
- 用 `findEditAreaMainSplitterIndex`（按像素几何：水平 handle 宽≥高、中心 Y 在 editArea 与 main 中心 Y 之间、X 与两面板重叠）定位 editArea 邻接 splitter = **索引 2**（`defaultFilmLayout` 第二层 `[preview|editArea] vs main` 的横 splitter）。
- 在该 splitter 的 `area.mx()/my()` 模拟 mouseButton=0 点击 `sizedFilmDock.mouseClicked`：**`draggedSplitterIndices` 含索引 2 → 拖动启动**。
- `editorResizablePanels=false` + `refreshVisibility()` 后同点点击：**`draggedSplitterIndices` 空 → 不启动**。
- 普通 locked dock（无 film 面板集）splitter 仍 hidden：**通过**（防泄漏）。

实测结论：在 **默认布局树** 下，locked film dock 的 editArea 邻接 splitter 三前提（visible / enabled / area 在落点）**全部成立**，且点击能正常启动 `beginSplitterDrag`——**验证场景下 splitter 是可拖的**，与用户「拖不动」相悖。即：**R1/R2/R3 在默认树+验证场景下均不成立**，bug 不在 dock splitter 的可见性/命中/启用层，而落在「运行期实际布局树与默认树不同」或「回放编辑器激活时 inspector/keyframeEditor 子树的事件覆盖」——属 R4（实测发现的新情况）。

- 1.1 守卫三条件：成立（locked + containsAll(FILM_PANELS) + editorResizablePanels=true）。
- 1.2 `editArea.isVisible()` in replay scene：在验证场景下 editArea active → visible=true（默认树）。
- 1.3 splitter visible/enabled/area：visible=true、enabled=true、area.w/h>0 且在 dock 内、点击启动 drag。
- 1.4 失败环（若有）：**无**（默认树下 splitter 可拖）。真凶在默认树之外——回放编辑器激活时实际布局树经 `UIFilmPanel.copyWithInsertSplitAt` 改写（`UIFilmPanel.java:811/816/824`），或 inspector `w(1F).h(1F)` / `UIKeyframeEditor` 子树在运行期吞事件，验证场景未覆盖这两者。
- 据此选：**2A 仍可能是方向，但需先扩 verification 构造「回放场景布局树」**（设计第 3 步 / 协调员第 3 步），再定是 area 落点（R2 变体）还是命中层（R3 变体）。当前实测**排除**了「默认树下 splitter 本身不可拖」这条死路。

下一步（由主会话决定）：按协调员第 3 步——构造 `copyWithInsertSplitAt(... editArea, replaysList, EDGE_BOTTOM)` 的回放场景布局树（非默认树），在 verification 里查 editArea 邻接 splitter 的 visible/area，再据结果定 R2/R3；或转 2A 命中层。

## 静态分析初判（第 1 步实测前的方向预判）

- **守卫成立**：`UIDockLayout.java:977` 面板 `setVisible(isPanelActive(id))` 不乘 `layoutLocked`；
  回放编辑器激活时 main 面板 active → visible → `filmVisible` 成立 → 补丁把 splitterHandles 设回 visible。
- **真凶方向 = 命中/事件分发层（2A）**：
  - target 模式下 `view.resetFlex().full(this).w(1F)`（`UIKeyframeEditor.java:96`）→ 时间线占满整个 editor（=main 面板）直到右边缘。
  - editArea（inspector 宿主）是 dock 另一独立面板，splitter 在 main 与 editArea 之间。
  - `UIElement.childrenMouseClicked`（第1472行）**逆序遍历**子节点，先命中者吞事件 return。
  - 鼠标在 splitter 几像素上时**同时落在时间线 view 的 area 内**（view 占满 main）→ 若时间线子树先于 splitter handle 收到点击，且 view `subMouseClicked` 命中，则 splitter 收不到。
  - 取决于 dock 子列表里 splitter handle（`addBefore(borders)`）vs main/editArea 面板的添加顺序。
- **实测待确认**：dock 子节点遍历顺序下，editArea 邻接那条 splitter 是否先于时间线 view 收到外缘点击。
  若否（时间线吞事件）→ 2A：让 splitter handle 在命中区不被时间线吞（提升 z-order / 早于 view 收事件 / 命中条带排除 view）。
  若是（splitter 先收但仍不拖）→ 查 splitter `enabled`/`beginSplitterDrag` 开关或 area 落点。
