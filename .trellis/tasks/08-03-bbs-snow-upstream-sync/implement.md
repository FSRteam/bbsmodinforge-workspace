# 08-03 bbs_snow 新版增量移植 执行计划

## 分组与顺序

按依赖与风险排序，每组完成后构建一次，不攒到最后。

### G1 F2 Actions 附加层相加叠加（最小、最独立，先行）

- [x] 新增 `cubic/animation/AdditiveLayerContext`（深度计数 ThreadLocal 语义开关）。
- [x] `CubicModelAnimatorMixin` 加 additive 注入（`toQuaternionZYXDegrees`，旋转 x/y 取负）。
- [x] 新增 `mixin/client/BOBJModelAnimatorMixin`（弧度制、不翻转旋转分量）。
- [x] `ModelFormRendererAnimationMixin`：附加层循环外包 `AdditiveLayerContext.begin/end` + `isSnowActionsEnabled` 门控。
- [x] `CMLSettings` 加 `snowActions`（`snow_actions`，默认 true）+ `isSnowActionsEnabled()` + en/zh 本地化键。
- [x] mixin json 加 `BOBJModelAnimatorMixin`；verification 加 additive 上下文开关断言。
- [x] 构建 + strict。

### G2 F3 纹理调色 UX 重设计

- [x] 新增 `mixin/client/UISectionMixin`（resizeParent 上溯 UIScrollView）。
- [x] 新增 `mixin/client/UIPoseTransformKeyframeFactoryMixin`（展开肢体关键帧 tint/whiten）。
- [x] `UIPoseEditorMixin` 重设计：UISection 折叠、tint 首次改 RGB 自动 alpha=1、`bbs_snow$refreshTextureGradeControls()`；`PoseTextureGradeEditorHolder` 加方法。
- [x] `UIPoseKeyframeFactoryMixin` 按新版重写：`UI.column` redirect + `add` 数组 redirect + `UIKeyframeFactory.resize` 前 refresh；移除 08-02 的 RETURN 临时修复。
- [x] mixin json 加两个新 mixin；verification 加 UISection/PoseTransform 断言；**更新 S7 断言为「后代+可见」**（新版布局把控件嵌进 column 内）。
- [x] 构建 + strict。

### G3 F4 miniwindow 健壮性重写

- [x] `IMiniWindowDockHost` 加 `hostBringMiniWindowLayerToFront()`、`hostCanUndockPanel(String)` 默认方法。
- [x] `UIMiniWindowManager` 按新版重写（`Minecraft.getInstance().execute` 延迟 redock、active tab 最后还原、三层 reassert、hostCanUndockPanel 门控、miniLayer 可见性跟随）。
- [x] `UIDockLayoutMiniWindowMixin` 合入：TransparentDockLayout 实现、renderPanelSurfaces 跳过 INonFloatingDockPanel、isTransparentPreviewHit、hostCanUndockPanel、hostBringMiniWindowLayerToFront、常驻还原按钮。
- [x] 新增 `api/TransparentDockLayout`、`ui/miniwindow/INonFloatingDockPanel`。
- [x] 构建 + strict。

### G4 F1 动画状态编辑器 dock 布局（最大件，最后）

- [x] 新增 `api/AnimationStateLayoutHost`、`api/TransparentDockLayout`（G3 已用）。
- [x] 新增 `ui/miniwindow/INonFloatingDockPanel`。
- [x] 新增 `ui/forms/AnimationStateLayoutSource`（ILayoutSource + `CMLSettings.animationStateLayout` ValueString invisible + JSON round-trip）。
- [x] 新增 `ui/forms/UIAnimationStatePreviewPanel`（BonePriority.openMenu(AnimationState)）。
- [x] 新增 `mixin/client/UIFormEditorAnimationStateLayoutMixin`（497 行，dock 三面板 + 布局锁定/预设/重置 + undo/redo pose 重建）。
- [x] 新增 `mixin/client/UIAnimationStateEditorMixin`（341 行，pose tab 展开记忆 + IK sheets + poses→limb + clear IK）。
- [x] `CMLSettings` 加 `animationStateLayout`（`animation_state_layout`，""，invisible）。
- [x] mixin json 加两个新 mixin；MIXIN_TARGETS 加 `UIFormEditor`/`UIAnimationStateEditor`/`UISection`/`UIPoseTransformKeyframeFactory`。
- [x] 构建 + strict。

### G5 F5 骨骼优先级扩展

- [x] `utils/BonePriority`：`openMenu` 拆 `AnimationState`/`Map<String, KeyframeChannel>` 重载（UIFilmPanel 委托到 Map 重载）。

### G6 收尾

- [ ] 全量 strict 全绿后审计 jar（新类/新 mixin/新本地化键）。
- [ ] 分组提交 addon（G1..G5 各一个提交）。
- [ ] S10 manual-acceptance 追加 F1/F2/F3/F4 的游戏内冒烟清单。
- [ ] root 仓库提交 trellis 记录；更新 memory。

## 风险与回滚点

- F1 497+341 行两个 mixin 对 FSR UI API 依赖最重；任何一个 shadow/inject 签名漂移都按「修描述符不绕 require」处理。
- G2 重写 `UIPoseKeyframeFactoryMixin` 时必须保住 08-02 修复的「控件真实出现」语义——strict 的 S7 UI 构造断言是门禁。
- G3 不得回归 [[gesture-ownership-teardown]] 的 demorph 修复；miniwindow 相关 S6 verification 全绿才算完。
- 新版源中 Yarn 中介名（`class_310`/`method_1551`/`method_18858`/`class_5944`）翻译：Minecraft.getInstance()/.execute/ShaderInstance。

## 验证命令

```powershell
Set-Location bbs-snow-addon
.\gradlew.bat --no-daemon verifyBbsCoreJar build --console=plain
Write-Output "EXIT=$LASTEXITCODE"
.\gradlew.bat --no-daemon runClient --max-workers=1 --console=plain -Pverify=1
```
