# S8 执行计划

## 1. 固化基线与分层

- [x] 保存主源码树与 `addon-project/` 差异研究；确认只迁移主树 Hotbar/Cinematic/Color，排除 Video。
- [x] 将 S3 `ColorEffect`/`ScreenEffectQueue` 提升为 common-safe owner，更新现有 client consumer imports，证明无平行 DTO。
- [x] 审计 BBS 1.21.1 clip factory、UI factory、`ClipContext` 和 HUD sprite API 的当前描述符。

## 2. 迁移 common 剪辑

- [x] 迁移 `HotbarState`/`HotbarClip`，完成 Mojmap、默认值、clamp、ItemStack copy 和旧 layout/hardcore 迁移。
- [x] 迁移 `ColorClip`/`CinematicClip`，使用 S3 namespaced queue 并发布不可变 tick 快照。
- [x] 在 common addon bootstrap 注册仅三种 clip factory；添加非默认值 serialization/clone/context isolation 验证。

## 3. 迁移 UI 与标题

- [x] 迁移 `UIHotbarClip`、`UICinematicClip`、`ClipTrackTitles`，按 `channels[]` 构建 25/8 轨道。
- [x] 新增 `UIColorClip`，编辑 overlay color 与 14 个 channel，注册三种 UI factory。
- [x] 迁移必要的 Clips accessor/invoker，按当前 BBS 描述符收窄注入并验证 keyframe 选择/刷新。

## 4. 迁移 Hotbar 渲染

- [x] 对照 1.21.1 Mojmap HUD sprite API 重写 `UIHotbarRenderer`，不引用旧 `icons.png`/`widgets.png` 图集。
- [x] 扩展现有 BBSRendering context consumer，按顺序消费 Hotbar state 和 S3 screen effects，每个 context 每帧一次。
- [x] 验证物品栏、副手、心形/吸收、盔甲、食物、氧气、经验、layout/alpha/scale 与空状态。

## 5. 本地化、自动验证和严格客户端

- [x] 补齐英语/简中 clip 名称和全部轨道标题，保持键集合与占位符对称。
- [x] 扩展 `BBSSnowRuntimeVerification` 覆盖 AC2-AC6；显式断言 Video 未注册、队列跨 context 不泄漏。
- [x] 重建稳定 core，运行 `verifyBbsCoreJar build`、JAR/禁止项审计和严格 `runClient`，记录真实退出码。
- [x] 把像素/复杂重叠/预览录制尺寸/addon-disabled 检查登记到 S10。

## Verification Evidence (2026-07-29)

- Rebuilt the core in one serialized `compileJava compileClientJava jar --rerun-tasks` invocation; Gradle daemon exited with status 0.
- Recompiled the addon against that core and installed a byte-identical runtime copy (matching SHA-256).
- Strict client logged `[bbs_snow] S8 runtime verification passed` with 25/8/14 clip tracks, then exited through the expected verification shutdown path.
- `verifyBbsCoreJar build` passed.
- JAR audit found all three clip classes, all three UI classes and both language files; embedded `mchorse/bbs_mod/**`, Video, old client DTO and Fabric entries/references were all zero.
- English and Simplified Chinese resources both contain 143 symmetric keys.

## 风险与回滚点

- common/client 边界是首个门禁：任何 common class 对 `mchorse.bbs_snow.client` 或 Minecraft client 包的引用都必须先修复。
- Hotbar 1.21.1 sprite 迁移是最高视觉风险；按 HUD 分组逐段验证，不一次性隐藏失败分支。
- S3 renderer 只允许 import/DTO 边界调整和已有 context consumer 的 S8 扩展，不复制 shader 或另建渲染管线。
- 不使用 `git add -A`；不修改/提交 `new/` 或 `bbs_FSloveCML_1.21.1/`。

## 验证命令

```powershell
Set-Location bbs-snow-addon
.\gradlew.bat --no-daemon verifyBbsCoreJar build --console=plain

# 确认 S8 专用运行目录无客户端后，安装同哈希 core 并运行自动退出严格客户端。
.\gradlew.bat --offline --no-daemon installBbsCoreForRun --rerun-tasks --max-workers=1 --console=plain
```
