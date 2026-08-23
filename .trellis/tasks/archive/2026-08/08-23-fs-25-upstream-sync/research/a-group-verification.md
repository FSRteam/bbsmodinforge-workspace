# A 组复核结论（fs 2.5 中 FSR 已有等价实现或不适用项）

复核日期：2026-08-23，基于 worktree `new-fs25-sync`（基线 497c4284）。

## 69b37fe98 — MobFormRenderer 全局 model-view 矩阵快照恢复

**结论：FSR 已有等价实现，跳过。**
FSR `src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java` 中已存在
`Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());` 与恢复点
`RenderSystem.getModelViewMatrix().set(modelView);`，与 fs 修复后的代码语义一致（来源为
07-23 model-block-form-preview-followup 任务）。

## 704290a18 — 服务器只在影片编辑器在屏时驱动影片

**结论：FSR 已有等价生命周期，跳过（语义覆盖）。**
FSR `UIFilmPanel` 已实现 `appear()` / `disappear()` + `activateLifecycle()` +
`ActionState.STOP` 的生命周期（迟钝构造场景下延迟到 update() 激活、幂等防重复注册），
语义上覆盖了 fs 的「RESTART 移入 appear()、disappear() 发 STOP」。FSR 的 `open()` 仍发
RESTART，但其触发条件已带 `isInCurrentLevel()` / `hasRecordedFrame()` 等额外守卫，
与 fs 的 bug 场景（任意 BBS 面板打开即重放影片）不等价。B1 批移植 `8d99d77fe`
倒计时修复时已顺带复核该路径无回归。

## 79a6134c1 — bump version to 2.5

**结论：按任务约束跳过（版本号不变更）。** FSR `mod_version=0.0.11` 保持不变。

## 4fba216e6 — maven 发布因项目名失败

**结论：不适用。** fs 修复的是其 Fabric/maven-publish 配置的项目命名问题；FSR 的
NeoForge 构建发布链路（cnb/cfworker 代理）不含该问题代码。

## 7d35fd494 — 翻译修正

**结论：适用，已应用（2026-08-23 复核纠正）。** 初判「FSR strings 为 en/zh 子集」有误：
FSR 实际持有完整 19 语言 strings（继承自 fs），`ru_ru.json` 中
`bbs.config.audio.waveform_time-comment` 的「адио полосе」typo 原样存在，已按 fs 修复改为
「аудио полосе」。

## 70274c5cb — FormTranslucentQueue：Text 形态背景忽略透明度

**结论：适用，已移植（2026-08-23 复核纠正）。** 初判只看了 `needsSplit/needsWholeDefer`
的 alpha 判据（那是「是否延迟」的分流，不是本 bug）。fs 修复的真实病灶是 flush() 里 blend
状态的生命周期：blend 在命令循环前设置一次，而 vanilla translucent RenderType 的
`clearRenderState()` 会 `disableBlend()`，于是排在 RenderLayerCommand 之后的命令（Text
背景四边形、公告板）在 GL_BLEND 关闭状态下绘制，alpha 不混合。FSR 重写版 flush() 与 fs
修复前**完全同构**（`enableBlend/defaultBlendFunc` 循环前一次 + `layer.clearRenderState()`
在 finally），同 bug 成立。已移植 fs 的 `resetBlend()`：flush 循环每条命令前重置 +
GroupCommand 每个 child 前重置。

## 26d35e664 — 演员头顶名牌（BaseFilmController）

**结论：FSR 已有完整实现，跳过。** FSR `BaseFilmController` 已有 `renderNameTag(...)`
（含彩色文本处理 `StringUtils.processColoredText`）、`FilmControllerContext.nameTag`、
`Replay.nameTag` 字段以及 `UIReplayPropertiesPanel`/`UIReplaysOverlayPanel` 的编辑 UI。
fs 该提交即此功能的首次引入，FSR 侧等价功能先行存在。
