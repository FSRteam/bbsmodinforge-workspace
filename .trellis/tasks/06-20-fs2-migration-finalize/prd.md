# FS2.0 迁移收尾与运行时bug修复（父任务）

## Goal

完成 BBS-FS 从 Fabric 到 NeoForge 1.21.1 的迁移收尾：先修复 codex 上一轮遗留的运行时缺陷，再补齐 `docs/migration-status` 中仍"正在修复/待验证"的 FS2 功能差距。统一以 FS2 官方 Fabric 1.21.1 分支（`temp/bbs-fs-1.21.1-ui`）为黄金参考。

## 子任务（按优先级）

1. **[06-20-fs2-runtime-bugfix]（当前优先）** — 6 个运行时 bug：闪烁、黑屏连锁、空白影片列表、阴影/受光、轨道相机、行为编辑器；附日志清理 + 汉化核实。
2. **[待建] fs2-feature-gap** — `docs/migration-status/current-status.md` 中"正在修复/待验证"项：Gizmo 交互、texture editor 工作流、film export 组合、particle manual rate/collision、API2 broker smoke、FS2 2.2.1 bugfix、WorldChunkMixin、master-only 决策（exportFramebuffer/limit_frame_rate 保留或回退）。

## 跨子任务验收

- [ ] `new` 编译通过（compileJava + compileClientJava）。
- [ ] 6 个运行时 bug 游戏内验证通过。
- [ ] migration-status 各项给出"已落地+已验证"或明确遗留说明。
- [ ] 仅对 `new/` 提交，用户确认后进行。

## 约束

- 不破坏向后兼容、不引入新工具、不迁入 Fabric API 形态。
- 子任务可独立规划/实现/验证；顺序约束写在各子任务 prd/implement。

## Notes

- 事实源：`docs/migration-status/`（与旧 docs 冲突时以此为准）。
- 完整方案另见 `docs/fs2-migration-plan/10-runtime-bugfix-plan.md`。
