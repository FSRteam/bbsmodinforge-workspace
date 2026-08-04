# 同步 FS 2.4 功能修复到 FSR

## Goal

把已审核的 FS `07bcc918..f76cce04` 功能与修复迁移到 `new/` 的 NeoForge
1.21.1 实现，同时保留 FSR 本地兼容边界、现有运行时修复和版本号。

## Confirmed facts

- `new` 迁移前源码基线为 `b004afd23706a19e3a20548be837d80595fb9f82`，
  实际初始边界提交为 `36a8e0dd8edea23f857cfe431cc6e29ef3113cc0`。
- 上游功能意图终点为 FS master `f76cce04a59b30017692e7725295dc987c8cc4c9`。
- 同 Minecraft API 参考为 FS 1.21.1 `42313660f9ea9599741bada7293ca491f3ebcb69`。
- 上一轮已迁至 master `07bcc918` / 1.21.1 `65bb8c93`；本轮不得重迁旧范围。
- `cff7d87a` 只把 FS 版本升为 2.4，本轮明确不迁入。
- `new/WORK_REPORT_2026-07-18.md` 是既有未跟踪文件，不属于本任务。

## Requirements

- 迁入缺失的正确性修复：缩放物理、body-part world/碰撞、模型热更新、
  uniform scale CME、原子设置写入、最近颜色、导出命名和 FFmpeg preset。
- 迁入 Minecraft 场景声音捕获、混音和视频 mux 完整链路，不留下空实现。
- 适配半透明 Form 排序和分数 UI 缩放到 NeoForge/Mojmap 1.21.1；不得直接
  复制 Fabric/Yarn 注册或错误的 mixin 签名。
- 复核并保留已经等价完成的 Replay tick、关键帧导航、MobForm UI、钻石标记，
  不重复覆盖现有更强实现。
- 对 1.21.1 专属 `8979e27f` 执行 Iris relative replay 静态对照与 smoke 记录。
- 保持旧粒子系统政策，不修改粒子实现。
- 保持 `mod_version=2.3.1`，不顺带调整 Minecraft、NeoForge 或 Java 版本。
- 更新 FS -> FSR 迁移事实源和 Trellis 起止提交边界。

## Acceptance Criteria

- [x] 17 个 master 提交和 `8979e27f` 均有已迁入、等价、N/A 或拒绝的结论。
- [x] 所有确认缺失修复和适合 FSR 的功能均已落地，无 Fabric/Yarn API 泄漏。
- [x] `mod_version` 最终仍为 `2.3.1`，`cff7d87a` 没有进入目标差异。
- [x] `compileJava`、`compileClientJava`、自动化测试和 `build` 通过。
- [x] 音频、透明排序、分数 UI、Iris relative replay 的手工 smoke 项已记录（待用户实机验证）。
- [x] 旧粒子路径相对起点没有非预期变化。
- [x] Trellis 记录真实 `initial_commit=36a8e0dd` 与 `terminal_commit=50b95cea`。

## Out of scope

- 发布、推送、修改远端历史或把 FSR 版本宣告为 2.4。
- 重做上一轮 `9b28a8a4..07bcc918` 迁移。
- 重新启用隔离分支中的新版粒子系统。
