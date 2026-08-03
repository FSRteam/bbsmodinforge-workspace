# bbs_snow 新版（2026-07-31 sources jar）增量移植

## Goal

用户提供了新版 bbs_snow 1.20.1 Fabric 源代码（`bbs-snow-addon/docs/bbs_snow-1.0.0-1.20.1-sources.jar`，191 个 java 文件）。与移植基线 `bbs_FSloveCML_1.21.1/src` 相比有 **16 个全新文件 + 57 个内容变更**。用户已确认：**全部移植**到 NeoForge addon `bbs-snow-addon/`（包名 `mchorse.bbs_snow`，`bbs_fslovecml$` 前缀 → `bbs_snow$`）。

## 新功能清单（16 个新文件）

### F1 动画状态编辑器 dock 布局（~1250 行，最大件）

- `api/AnimationStateLayoutHost`（接口：prepare/attach state keyframe editor）
- `api/TransparentDockLayout`（接口：dock 基底透明开关）
- `ui/forms/AnimationStateLayoutSource`（`ILayoutSource` 实现，持久化到 `CMLSettings.animationStateLayout` ValueString JSON）
- `ui/forms/UIAnimationStatePreviewPanel`（透明 dock 面板 + 骨骼优先按钮，实现 `INonFloatingDockPanel`）
- `ui/miniwindow/INonFloatingDockPanel`（标记接口：禁止 undock）
- `mixin/client/UIFormEditorAnimationStateLayoutMixin`（497 行：stateDock 三面板 preview/timeline/inspector、布局锁定/预设/重置、undo/redo 后 pose factory 重建与骨骼选择恢复）
- `mixin/client/UIAnimationStateEditorMixin`（341 行：pose tab 展开状态记忆、IK sheets、poses→limb tracks、clear IK）

### F2 Actions 附加层相加叠加（~120 行）

- `cubic/animation/AdditiveLayerContext`（深度计数 ThreadLocal 语义开关）
- `mixin/client/BOBJModelAnimatorMixin`（BOBJ 侧 additive applyGroupAnimation）
- 变更：`CubicModelAnimatorMixin` 新增 additive 注入（61 行 diff）、`ModelFormRendererMixin` 在附加层循环外包 begin/end + `isSnowActionsEnabled` 门控
- 新设置：`CMLSettings.snowActions`（`snow_actions`，默认 true）+ `isSnowActionsEnabled()`

### F3 纹理调色 UX 重设计（~250 行）

- `mixin/client/UISectionMixin`（29 行：`UISection.resizeParent` 改为向上找最近 `UIScrollView`  resize）
- `mixin/client/UIPoseTransformKeyframeFactoryMixin`（106 行新增：展开的 pose 肢体关键帧面板加 tint/whiten 控件）
- `UIPoseEditorMixin` 重设计（82 行 diff）：texture grade 区改 `UISection`（可折叠、默认收起）、tint 首次改 RGB 自动把 alpha 置 1、新增 `bbs_fslovecml$refreshTextureGradeControls()` 公开刷新入口
- `UIPoseKeyframeFactoryMixin` 重设计：宽布局经 `UI.column` redirect 合并追加、窄布局 redirect `add` 数组、`resize` 前调 refreshTextureGradeControls
- `api/PoseTextureGradeEditorHolder` 新增 `bbs_fslovecml$refreshTextureGradeControls()` 方法

### F4 miniwindow 健壮性重写（~200 行 diff）

- `UIMiniWindowManager`：`redock` 改 `Minecraft.execute` 延迟到鼠标分发结束后（BBS 2.4 立即改 UI 树会留悬空指针）、active tab 最后还原、三层 reassert、`hostCanUndockPanel` 门控、miniLayer 可见性跟随窗口数
- `UIDockLayoutMiniWindowMixin`：实现 `TransparentDockLayout`（透明基底 + `renderPanelSurfaces` 跳过 `INonFloatingDockPanel`）、`isTransparentPreviewHit` 右键穿透、`hostCanUndockPanel`/`hostBringMiniWindowLayerToFront` 新方法、常驻还原按钮（dock 右上，floatingIds 非空时可见）
- `IMiniWindowDockHost` 接口新增 `hostBringMiniWindowLayerToFront()`、`hostCanUndockPanel(String)`

### F5 骨骼优先级扩展（~30 行 diff）

- `utils/BonePriority`：`openMenu` 拆出 `AnimationState` 与 `Map<String, KeyframeChannel>` 重载（供动画状态编辑器与 preview 面板复用）

## Requirements

- R1 全部按现有移植约定落地：包名 `mchorse.bbs_snow`、前缀 `bbs_snow$`、Yarn→Mojmap（`class_310`→`Minecraft`、`method_18858`→`execute`、Yarn `Vector3f` 等）、Java 21。
- R2 F1 的 `CMLSettings.animationStateLayout` 用 `builder.getString("animation_state_layout", "")` + `invisible()`（与新版一致）；`snow_actions` 设置加入 `bbs_snow` 分类并附本地化键。
- R3 新增 mixin 全部进 `bbs_snow.client.mixins.json` 且 `defaultRequire: 1`；新目标类加入 `BBSSnowRuntimeVerification` 的 MIXIN_TARGETS；S9/F1/F3 关键路径加确定性 verification（dock 挂载、additive 上下文、UISection resizeParent、PoseTransform tint/whiten 写入）。
- R4 与已落地功能的冲突裁决：
  - `UIPoseKeyframeFactoryMixin`：新版布局注入方案（UI.column redirect + add 数组 redirect）替代 08-02 的临时 RETURN 修复，但**保留** RETURN 位置的 refreshTextureGradeControls 调用等价物（新版在 `UIKeyframeFactory.resize` 前调）。
  - miniwindow 重写不得破坏 07-21/07-23 已修的 gesture 所有权与 demorph 行为（见 [[gesture-ownership-teardown]]）；`UIFilmPanelMiniWindowMixin` 侧 diff 需逐条核对后再合入。
  - premiere/`UIFilmPreviewMixin`/`ModelInstanceTextureGradeMixin`/`FluidFormRenderer`/`FluidController`/`events/ClientEventHandler` 等剩余 diff 中，仅注释/文档/归一化差异**不合入**（保持我们已修复的版本为准）；发现行为性差异单独提出。
- R5 构建红线照旧：`verifyBbsCoreJar build` 真实 exit code；strict 客户端 `-PrunVerification=1` 全绿；不使用 `git add -A`；`new/` 零改动。

## Acceptance Criteria

- [ ] AC1 构建 EXIT=0，jar 含全部新类与新 mixin 条目。
- [ ] AC2 strict 全绿（含新增 verification 段），无 injection failure。
- [ ] AC3 游戏内冒烟：动画状态编辑器出现 preview/timeline/inspector 三面板 dock，可拖拽分栏、锁定、预设保存/恢复，布局重启后保持。
- [ ] AC4 游戏内冒烟：actions 附加层多层叠加为相加（非覆盖）；关闭 `snow_actions` 后回到原版行为。
- [ ] AC5 游戏内冒烟：pose 编辑器/展开肢体关键帧面板中 tint/whiten 可折叠、首次改 RGB 自动启用 alpha；改动立即反映到渲染。
- [ ] AC6 游戏内冒烟：dock 面板可浮动为小窗、合并、还原（按钮+右键菜单），动画状态 preview 面板不可浮动且右键穿透到骨骼拾取。
- [ ] AC7 骨骼优先级菜单在动画状态编辑器与 replay 编辑器均可用。

## Out of Scope

- 不同步新版对 premiere、fluid、quick-replay 键位等的纯注释/归一化差异。
- `ExtraFormSectionMixin`（新版仍在，但我们已用 `FormCategories.addExtraForm` 正规 API，明确不移植）。
