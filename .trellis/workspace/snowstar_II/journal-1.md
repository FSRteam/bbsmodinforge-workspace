# Journal - snowstar_II (Part 1)

> AI development session journal
> Started: 2026-05-24

---



## Session 1: Particle editor interaction and billboard fixes

**Date**: 2026-05-31
**Task**: Particle editor interaction and billboard fixes

### Summary

Fixed particle parser hardening, Bezier chain and curve editor interactions, textbox key repeat, billboard camera-facing parity attempts, and color palette zero-size rendering crash.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `e829fe57` | (see git log) |
| `ba95879e` | (see git log) |
| `3757cba0` | (see git log) |
| `0ff61d98` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Port BBS Structure Addon

**Date**: 2026-05-31
**Task**: Port BBS Structure Addon

### Summary

Ported the BBS Structure form into the NeoForge new project, registered model/UI/rendering paths, preserved StructureForm compatibility fields, validated with Gradle build, and committed only the new/ Structure changes.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `837a466c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: Finish Structure addon split

**Date**: 2026-06-06
**Task**: Finish Structure addon split

### Summary

Finished the standalone Structure addon split, fixed external addon registration into BBS internal events, added registration diagnostics, and validated new plus bbs-structure-addon builds.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `5acfed57` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: Review new repository uncommitted changes

**Date**: 2026-06-06
**Task**: Review new repository uncommitted changes
**Branch**: `master`

### Summary

Reviewed uncommitted source changes in new only, fixed a MorphRenderer hidePlayer ordering issue, split the work into render, particle camera, and film actor replay commits, and left local output directories uncommitted.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `c1aec908` | (see git log) |
| `f75b72b5` | (see git log) |
| `2d4cd3dd` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: Fix shader world form drift

**Date**: 2026-06-06
**Task**: Fix shader world form drift

### Summary

Fixed shader-dependent world form drift by keeping BBS world PoseStacks camera-relative and applying the active model-view matrix through RenderSystem. Verified compileClientJava and build in the new NeoForge repository.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `258a814a` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: Addon API 2.0 compatibility layer

**Date**: 2026-06-06
**Task**: Addon API 2.0 compatibility layer

### Summary

Implemented Addon/API 2.0 public facades, diagnostics, v1 timing hardening, client hooks, particle extension bridge, demo addon, and new addon author documentation in new.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `293c0c21` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: Fix replay actor drift

**Date**: 2026-06-06
**Task**: Fix replay actor drift

### Summary

Fixed replay actor drift by skipping disabled replay update/render paths and keeping real ActorEntity positions server-owned while syncing only visual replay state on the client.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `82714440` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 8: Review new uncommitted files

**Date**: 2026-06-07
**Task**: Review new uncommitted files

### Summary

Reviewed uncommitted files in new only; ignored Claude local settings and local run outputs via separate gitignore commits.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `b9636807` | (see git log) |
| `e8bec756` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 9: Particle facing Blockbuster parity

**Date**: 2026-06-08
**Task**: Particle facing Blockbuster parity

### Summary

Fixed particle billboard facing parity with Blockbuster, kept particle form world rendering camera-relative, and validated new build.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `ebe75984` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 10: Particle facing Blockbuster parity

**Date**: 2026-06-08
**Task**: Particle facing Blockbuster parity

### Summary

Restored Blockbuster-style particle billboard facing and fixed ParticleFormRenderer world camera-relative rendering.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `ebe75984` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 11: Fix non-actor replay shader drift

**Date**: 2026-06-08
**Task**: Fix non-actor replay shader drift

### Summary

Fixed Iris non-actor replay drift by passing renderSectionLayer's active matrix into BBS shader replay rendering; actor path left unchanged. Validation: compileClientJava and build passed.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `257ec5f2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 12: 同步最新 FS 到 FSR 并推送

**Date**: 2026-07-15
**Task**: 同步最新 FS 到 FSR 并推送

### Summary

完成 FS 2026-07-15 七提交到 NeoForge FSR 的迁移与双 agent 终审；保留旧粒子系统，修复导出参数、临时 WAV 与断线清理，干净 worktree clean build 通过并推送 cnb/main。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `b815cf27` | (see git log) |
| `838d11d9` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 13: 修复模型物理空间并优化 IK/物理骨骼

**Date**: 2026-07-16
**Task**: 修复模型物理空间并优化 IK/物理骨骼

### Summary

隔离摄像机/玩家与模型模拟空间和实例历史，修正 constraints→IK→physics 顺序；缓存 IK/物理拓扑并复用 renderer-owner workspace，恢复默认完全伸直及 3°/20° rest-bend 滞回。完整 build 与静态热点审计通过。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `6a5785bd` | (see git log) |
| `8386d180` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 14: Sync latest FS fixes to NeoForge

**Date**: 2026-07-19
**Task**: Sync latest FS fixes to NeoForge

### Summary

Audited FS master and Minecraft branches, migrated applicable correctness, physics, audio, translucent rendering and fractional UI scale changes into new/ while keeping version 2.3.1 and legacy particle paths, passed full check/build, and recorded exact migration boundaries.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `36a8e0dd8edea23f857cfe431cc6e29ef3113cc0` | (see git log) |
| `50b95cea2a68486faf601331aabf874dd033694d` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete

## Session 15: 规划插件结构性能力光影式热重载

**Date**: 2026-07-23
**Task**: `.trellis/tasks/07-23-fsr-plugin-structural-hot-reload` (planning)

### Summary

- 上一期 07-21 热插件运行时把 forms/clips/particles/key_mappings/entity_renderer/block_entity_renderer 六项能力拒为 RESTART_REQUIRED；本期按用户判据（凡重启后能经 FSR/Addon 通道运行的能力均可热重载）纳入热重载，采用光影包式全量 teardown+重建模型。
- 已写 prd.md / design.md / implement.md（7 个 Phase，交由 GPT/Codex 执行）；硬边界保持：minecraft_registry/mixin/AT/coremod/JNI/native。
- 关键事实：MapFactory.unregister 有 String/Link bug 实际不可用；FormUtilsClient.map 静态持有插件 Class；ClientApiCompat 结构性注册是一次性窗口。

### Status

[OK] **Planning artifacts complete** — 待用户审阅后由 GPT 执行（task 尚未 start）

### Next Steps

- 用户审阅三份规划文档
- 批准后 `task.py start`，按 implement.md Phase 0 起步（dispatch prompt 须带 Active task 头）


## Session 15: Fix dashboard morph and model editor regressions

**Date**: 2026-07-23
**Task**: Fix dashboard morph and model editor regressions

### Summary

Fixed dashboard menu re-entry lifecycle, transparent preview rendering, stale model-block morph submenu cleanup, transform shortcuts, gizmo ray access, and animated BOBJ render reuse. Verified compileJava compileClientJava, testUiInputMailbox, testFilmEditorMigration, check build, and git diff --check.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `b6b37ad4` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete

## 2026-07-23 FSR 主题/皮肤系统规划(Fable 会话)

- 用户需求:高度 DIY 换肤(UI+动画),设置加皮肤栏,规范样例单独文件夹,规划入 Trellis 后交 GPT 执行。
- 四项决策(用户确认):全套一步到位;动画 6 接入点全要;规范样例由本会话写到 new/docs/theme-spec/;父任务+模块子任务。
- 建任务:07-23-fsr-theme-system(父)+ theme-core/theme-widget-tokens/theme-motion-layer/theme-skin-settings-ui/theme-builtin-packs,全部含 prd(+复杂任务 design/implement),implement.jsonl 已挂规范上下文。
- 关键调研事实(已核实):BBSSettings 已有半主题化语义色(theme 0/1 + LIGHT_*/DARK_* 常量,62 文件);Colors.* 硬编码 146 文件 880 处;内置 assets 在 src/client/resources/assets/bbs/assets(非 main);外部 config/bbs/assets 同路径覆盖内置;Interpolations.MAP 按字符串键;UI 框架无动画层;icons.png 256×256/16 格。
- 规范样例已写:new/docs/theme-spec/{README.md, example-theme/{theme.json,README.md,textures/README.md}},未提交(new/ 提交需用户确认)。
- 下一步:用户审阅规划 → 按 theme-core 先行的顺序分次喂 GPT(codex),每子任务 task.py start 后执行。

## 2026-07-24 主题系统实现会话(claude 主会话 + haiku 子代理)

GPT 不可用,用户令本会话直接实现 07-23 主题任务树。五个子任务全部代码层完成:

- **theme-core**:`ui.themes` 包(UITheme/UIThemeMotion/ThemeParser/ThemeManager)、内置
  dark/light JSON(逐值对拍旧常量)、BBSSettings 接线(语义函数签名不变)、
  theme→theme_id 迁移、tooltipStyle 删除(确认为死值)、图标 atlas 重映射三点、
  `src/themeCoreTest` 单测(12 用例)。发现:内置 assets 在 client source set;
  测试基建 = 独立 source set + JavaExec main(无 JUnit)。
- **widget-tokens**:按常量类别分批(不按目录):七语义常量 47 处(位级恒等三规则:
  A100|x→fn()、Axx|x→(fn()&RGB)|Axx、裸→fn()&RGB)、文字色 33 处、控件默认色
  5 处哨兵(==WHITE→textColor())、Batcher2D 默认色/textShadow 汇聚点单点、
  **37 处 primaryColor.get() 直连修复→accentColorRGB()**(否则主题 accent 对按钮无效)。
  机械批派 3 个 haiku 子代理(精确 old/new 清单),framework 残留 0,全局残留 7 处全合法。
- **motion-layer**:UITween(isSettled 短路=旧路径逐位)+ UIMotions;六接入点:hover 三控件
  lerp、overlay 膜淡入+pose scale、context_menu scale、notification 接管既有信封
  (主题化时长/缓动)、panel_switch veil、scrollbar 静止淡出(scrollTo 记活动时间戳、
  悬停 snap 显形)。红线自查记 implement.md。
- **skin-settings-ui**:UIThemeOverlayPanel(色板行列表/来源标记/坏主题通知/重载/开文件夹/
  导出模板 my-theme-N 避让)、UIValueMap themeId 按钮特例 + primaryColor 联动
  (改拾色器→follows=false)、UISectionHeader skins 菜单、UIKeys SKINS_* + en/zh 文案。
- **builtin-packs**:jar 内 example(与 docs 逐字节同源,Files.mismatch 断言)+ 演示主题
  amber(琥珀纯平弹性,三层差异断言)、ThemeManager 内置清单四主题、规范逐 key 对齐
  校验清单全勾(零不一致)、docs README 实现状态注记更新。

验证:每阶段 `./gradlew check` 全量绿(testThemeCore/testUiMotions 已入 verification 列表)。
**待用户**:游戏内冒烟 7 条(父任务 implement.md)、确认后分五个 commit 提交。
规约落盘:`.trellis/spec/frontend/bbs-ui-theming.md`。

## 2026-07-25 主题系统 v2 实现会话(Fable 主会话 + 3 子代理)

GPT 已完成 motion-engine(~85%)与 rounded-corners(~70%),本会话补完其余全部:

- **审查**(子代理):弹簧闭式解三分支位移/速度互为导数、打断速度连续、圆角对照
  1.20.1 参考逐段核对,全部通过;3 个非阻塞问题已修 2(alpha 栈帧首 resetAlpha 兜底、
  UIIcon 显式背景色在 radius==0 走方形 box,默认 hover/active 底色仅 radius>0 生效)。
- **motion-exit**:UIOverlay/UIContextMenu 退场态——关闭即语义关闭(setEnabled(false)、
  事件即发、backdrop 不拦截),反向播放出现动画,settle 后 postRunnable 延迟物理移除
  (防渲染遍历 CME);close 幂等;UIOverlay.has() 忽略退场实例;motion 关闭走 v1 原路径。
- **motion-tracks**:UIThemeMotionTracks(alpha/scale/x/y from 值 + 采样)、preset
  (scale/slide_right/slide_up/fade)展开 + 显式覆盖;overlay/context_menu 进出场共用。
- **decor-layer**:decorations 九宫贴花(上限16/坏值丢弃/缺纹理 validateTextures 剔除)、
  background_mode(stretch/cover/tile)、background_dim;UIThemeBackdrop helper 接
  UIDashboard + UIBaseMenu.renderDefaultBackground。
- **motion-coverage**(子代理):scroll_smooth 主题门控叠加 v1 双值模型;hover_scale/press
  落 UIClickable 基类(合成 scale==1 零 push);layout 过渡落 UIFilmPanel dock 布局
  (dashboard 面板无 bounds 变化,落点说明见 agent 报告);四条目 + 条目级 scale 解析。
- **文档/演示**:theme-spec README v2 全键、example 全键显式同步(docs↔jar 逐字节)、
  新内置 strawberry 主题(圆角+弹簧+preset/tracks+cover 背景+程序化草莓贴花 PNG)、
  frontend spec 增补 v2 红线(退场唯一例外、圆角 token、alpha 栈、UIThemeBackdrop)。
- **测试**:UIMotionsTest +退场反向/coverage 默认值/布局插值;ThemeCoreTest +corner_radius
  (haiku 子代理)/tracks/preset/decorations/background 用例;全部通过。

**已知阻塞(非本任务)**:`./gradlew check` 中 testNetworkSecurity 失败
(FilmActionAuthorityPolicyTest:未知 action 应被 typed decoding 丢弃,被 plugin
hot-reload 任务的 MissingClip 占位符保留)——属 07-23-fsr-plugin-structural-hot-reload
回归,需该任务裁决安全语义。主题相关验证(compileClientJava/testThemeCore/testUiMotions)
全绿。**未 commit**;待用户游戏内冒烟。

## 2026-07-25 testNetworkSecurity 修复(接上条)

三处根因均为 plugin hot-reload 改动的连带,已修,全量 check exit 0:

1. FilmActionAuthorityPolicyTest"未知 action 应被丢弃"断言过时:MissingClip 保留其实
   **更严**(精确类白名单下必然 requiresAdministrator,且 typed/raw 计数一致)。测试更新为
   新契约:保留为不执行占位符 + 有/无 raw 都要求管理员权限。
2. Form 构造器直接解引用 BBSSettings.recordingPoseTransformOverlays(裸 JVM 为 null)
   → MissingForm 兜底自身 NPE。改为 null 时回退注册默认 0。
3. FormUtils.fromData 的 MissingForm 兜底再包一层 try(占位符构造失败回退旧 null 行为,
   解码永不因兜底而崩)。

## 2026-07-26 Refreshed theme token 子任务(Codex)

- 激活并完成 `07-25-refreshed-theme-tokens`:UITheme/ThemeParser/BBSSettings 新增 19 个
  扩展颜色 token,保持 parse-time 固化;旧版无对应视觉的 dark 默认用 0 哨兵。
- 新增内置 `refreshed` 主题包并加入 ThemeManager 枚举;example-theme docs→jar 逐字节同步。
- 参考源码核对发现盘点文档十六进制换算错误:`UI_CANVAS=-15789804` 精确为
  `#0F1114`,不是 `#0F0F14`;已同步修正 research/design,主题派生色以该整数为事实源。
- ThemeCoreTest 新增全 token 显式解析、坏值×2、base 继承、dark 对拍与 refreshed 包断言。
- 验证:`testThemeCore` 两次通过;首次全量检查超时后补跑剩余 5 项退出码 0,最终再次执行
  单次 `gradlew check` 于 5m31s 完成,真实退出码 0。
- 未 commit/归档:`new/` 工作区同时含 plugin structural hot reload 等其他任务的大量未提交改动,
  需由用户按任务边界选择性提交。


## Session 16: Complete bbs_snow S1 Mixin gate

**Date**: 2026-07-27
**Task**: Complete bbs_snow S1 Mixin gate
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Verified the standalone NeoForge addon skeleton, Mixin runtime gate, addon registration bridge, strict BBS jar validation, and archived S1 before advancing to S2.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `cad8464` | (see git log) |
| `f06f4b1` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 17: Complete bbs_snow S2 core API gate

**Date**: 2026-07-27
**Task**: Complete bbs_snow S2 core API gate
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Ported the bbs_snow core data/API contracts, verified 17 Mixins and addon localization/settings at runtime, recorded the external addon integration contract, and archived S2.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1d73747` | (see git log) |
| `ff9093b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 18: Complete bbs_snow S3 PBR texture-grade gate

**Date**: 2026-07-27
**Task**: Complete bbs_snow S3 PBR texture-grade gate
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Ported and verified PBR rendering/editors, texture grading, deferred GL state, screen effects, and Iris isolation; archived S3 after a strict runtime pass.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `8975a7e` | (see git log) |
| `8b1578e` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 19: Complete bbs_snow S4 Cubic rendering and animation

**Date**: 2026-07-27
**Task**: Complete bbs_snow S4 Cubic rendering and animation
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Ported Cubic pivot rendering, additive and procedural animation overlays, Molang scopes, Geo step parsing, and strict S4 runtime verification; client verifier passed with all eight S4 Mixins and the task was archived.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `6d37471` | (see git log) |
| `32aed30` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 20: bbs_snow S5 particle extensions

**Date**: 2026-07-27
**Task**: bbs_snow S5 particle extensions
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Ported particle morph rendering, editor controls, browser search/favorites/recent, and six strict Mixins; added deterministic runtime coverage, fixed conditional Mixin fallback sorting regression, and passed build/Jar/client verification.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `19781b6` | (see git log) |
| `8fda54b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 21: BBS Snow S6 mini-window port

**Date**: 2026-07-27
**Task**: BBS Snow S6 mini-window port
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Completed and archived S6 UI mini-window integration: five required Mixins, dock and Film floating-window lifecycle, overlay resizing, strict runtime verification including merge and resize interactions, jar isolation audit, and deferred full manual interaction smoke to S10.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `3e720d3` | (see git log) |
| `786971c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 22: Sound morph stage 2 loop interval delivery

**Date**: 2026-07-29
**Task**: Sound morph stage 2 loop interval delivery
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Completed timeline-driven sound loop intervals across Form, grouped keyframes, direct/reflected spatial playback, UI and regression coverage; updated the audio contract; built and verified BBS_FSR_beta_v0.0.10-sound-stage2-loop-20260729.jar without launching the client. Task remains active for user save smoke testing.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `e11ca91b` | (see git log) |
| `fb6d74b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 23: Complete BBS Snow S8 clips

**Date**: 2026-07-29
**Task**: Complete BBS Snow S8 clips
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

Fixed the owner-aware pose UI verifier, passed strict S8 runtime verification and the full addon/JAR gates, committed the combined S7/S8 addon snapshot, recorded S8 manual checks in S10, and archived S8.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `75854ef` | (see git log) |
| `e0fc678` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
