# PRD：回放关键帧编辑器时间线与效果面板边界不可拖动

## 背景

用户在影片剪辑里点顶栏「场景」图标进入回放编辑器（`UIReplaysEditor`），界面是
`[轨道/实体列表 | 时间线关键帧 | 效果面板]`。**时间线与右侧效果面板中间的边缘不能拖动改变窗口大小。**

此前断联的一次修复（`new/` commit `497c4284`、`bbs-snow-addon` commit `7c6c016`）只修好了两类场景：

1. **影片主 dock 面板之间的 splitter**（`keepFilmSplittersResizable`）——film 主布局 5 面板
   `{main,preview,editArea,replaysList,replayProps}` 在 locked 时仍可拖。
2. **嵌入式关键帧编辑器自带 splitter**（`UIKeyframeEditor.createSplitter`）——仅
   `target == null`（embedded）模式可见可拖，即 clip 级「编辑」按钮 `embedView` 进去那条。

**回放编辑器走的是 `target` 模式**（`UIReplaysEditor.java:798` `.target(this.filmPanel.editArea)`），
编辑器自带 splitter 被 `updateSplitterState()`（`UIKeyframeEditor.java:249-252`）的
`target == null` 守卫**强制隐藏**；效果面板被整体 reparent + 覆盖到 `filmPanel.editArea`
这个 dock 面板上（`UIKeyframeEditor.java:141` `w(1F).h(1F,-top)`）。
于是「时间线 ↔ 效果面板」的边界由 `UIDockLayout` 接管，但该 dock splitter 在实际使用中
仍拖不动——这是本任务要修的缺口。

## 目标

让回放编辑器中「时间线关键帧」与「右侧效果面板」之间的边界**可拖动改变窗口大小**，
且与已有的 `editorResizablePanels` 开关、`editorKeyframePanelWidth` 持久化、dock locked 行为保持一致。

## 范围

- **在范围内**：回放编辑器（`UIReplaysEditor` → `UIKeyframeEditor` target 模式 → film 主 dock 的
  editArea 面板）的时间线/效果面板边界可拖。
- **不在范围内**：clip 级 embedded 编辑器（已能拖，`497c4284` 覆盖）；动画状态编辑器
  `bbs_snow$stateDock`（另一条 target 路径，本任务不处理，但修复方案须避免对其产生回归）；
  上游 1.20.1 全量对齐；**addon 构建链对齐 `new-fs25-sync`**（6 处 `../new` 切换 + 重构建 jar，
  与 splitter 修复无关，另开任务）。

## 约束

- `new/` 与 `bbs-snow-addon` 是**两个独立 git 仓库**（外层 `.gitignore` 排除它们），改动分别提交。
- **本任务不改 `new/`**：`UIDockLayout`/`UIKeyframeEditor` 与 `new-fs25-sync` 0 差异（fs 2.5 同步未碰 dock 实现），
  且 `new-fs25-sync` 即将合并 → 改 `new/` 会和合并纠缠。修复只在 `bbs-snow-addon` mixin 层。
- 优先在 `bbs-snow-addon` 用 mixin 实现，遵守 [[bbs-snow-addon-mixin-contract]]：
  注册走 `BBSMod.registerAddon`，mixin 包禁放辅助类，注入点错误只在运行期暴露。
- 复用现有开关 `BBSSettings.editorResizablePanels`（默认 true），不引入新设置项除非必要。
- 不破坏 dock locked 语义：locked = 禁止拖出/重排面板结构，但允许拖 splitter 调比例（与
  `keepFilmSplittersResizable` 一致）。
- 构建验证红线见 [[gradle-pipe-exitcode-trap]]：真实 exit code + 日志双确认；strict run 用
  `-Pverify=1`。

## 验收标准

1. **功能**：回放编辑器中，时间线与效果面板之间的边界可拖动，拖动后两区宽度实时变化，
   松手后宽度持久化（重启或在 embedded↔target 切换后保持）。
2. **开关一致**：`editorResizablePanels=false` 时该边界不可拖，行为与其它 splitter 一致。
3. **无回归**：
   - clip 级 embedded 编辑器 splitter 仍可拖。
   - 影片主 dock 其它面板边界仍可拖。
   - 动画状态编辑器 `bbs_snow$stateDock` 行为不变。
   - 效果面板 inspector 的 reparent/embed 逻辑（`UIKeyframeInspectorEmbedMixin`、
     `UIReplaysEditorEmbedMixin`、`UIFilmPanelMiniWindowMixin`）不受影响。
4. **验证**：`-Pverify=1` strict 全绿，新增/扩展的 verification case 覆盖本修复场景。
5. **人工冒烟**：用户在游戏内确认可拖且比例保留。

## 待定（design 阶段确认）

- 拖动的是「`editArea` 面板与其相邻面板之间的 dock splitter」还是「重新启用编辑器自带 splitter」——
  取决于 inspector `w(1F).h(1F)` 是否盖住了 dock splitter 命中区，以及上游 1.20.1 原始实现
  （research agent 报告待回）。
