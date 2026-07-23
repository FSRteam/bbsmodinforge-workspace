# 同步最新 FS 到 FSR

## Goal

审核 `new/` 中尚未推送的 FSR 迁移成果，在不丢失 NeoForge 1.21.1 适配与本地兼容决策的前提下，把 FS 从现有冻结基线升级到远端最新版本，并将新增功能和修复完整迁移到 `new/`。

## Confirmed facts

- FSR 目标仓库是 `new/`，不是 `bbs-premiere-export/`。
- `new/` 当前工作树干净，`master` 相对 `cnb/main` ahead 27；这 27 个本地提交是待审核的未推送迁移成果。
- 这 27 个提交已把冻结参考 `temp/bbs-fs-2.3` 的 `9b28a8a4` 阶段迁入 `new/`。
- FS 远端最新 `master` 为 `395f6927`；同一内容已通过合并提交 `8b25fbaf` 进入 FS 的 `1.21.1` 分支。最新增量为 `9b28a8a4..395f6927` 的 45 个提交。
- `new/` 目标运行时保持 Minecraft 1.21.1 + NeoForge；最新 FS 的 `1.21.1` 分支是 API 行为参考，Fabric `master` 仅用于提交边界和意图追踪。
- 2026-07-15 远端再次前进：FS `master` 为 `07bcc918`，FS `1.21.1` 为 `65bb8c93`；新增范围为 `395f6927..07bcc918` 的 7 个提交。
- FSR 主线必须继续使用迁移前的旧粒子系统。曾迁入的新粒子实现隔离在 `archive/fs-particle-migration-395f6927`，本批不得把该实现重新写回 `src`。

## Requirements

1. 审核 `cnb/main..new/master` 的 27 个本地提交，找出编译错误、明显行为回归、遗漏迁移、Fabric/Yarn 泄漏和与任务文档不一致之处。
2. 逐项迁移 `9b28a8a4..395f6927` 的 45 个 FS 提交；以 `8b25fbaf` 的 1.21.1 实现为源码参考，以提交历史保留功能意图。
3. 保留 `new/` 中正确的 NeoForge/Mojmap、RenderSystem、GuiGraphics、payload broker、Mixin 和资源加载适配，不直接覆盖为 Fabric/Yarn API。
4. 保留已有、已记录的 FSR 本地兼容决策；若上游新实现取代旧决策，必须在迁移状态文档中说明。
5. 迁移范围包括上游新增功能、修复、资源和设置；不得只为编译通过而留下空实现、静默禁用或未记录的功能降级。
6. 更新迁移事实源，记录参考 commit、已迁簇、构建结果和仍需游戏内验证的项目。
7. 仅修改 `new/` 和本任务/迁移状态文档；不修改无关插件仓库，不擅自提交或推送。
8. 迁移 `395f6927..07bcc918` 的 7 个非粒子提交，并保留旧粒子系统作为唯一参与编译的实现。

## Acceptance Criteria

- [x] 27 个本地迁移提交已完成独立审核，所有确认的阻断/高风险问题均已修复或明确记录为需游戏内验证。
- [x] `9b28a8a4..395f6927` 的 45 个上游提交全部有“已迁入 / NeoForge 等价实现 / 明确不适用”的可追溯结论。
- [x] `new/gradlew.bat --no-daemon compileJava compileClientJava` 通过。
- [x] `new/gradlew.bat --no-daemon build` 通过。
- [x] 源码中没有因本轮迁移新增的 Fabric/Yarn API、调试刷屏、空 stub 或未说明的 `UnsupportedOperationException`。
- [x] 关键资源、设置与英文/俄文语言键完整；中文缺失项被记录或同步，不因本轮迁移破坏现有中文。
- [x] `docs/migration-status/current-status.md` 更新到最新 FS/FSR 基线，并列出必须由用户完成的游戏内 smoke 项。
- [x] 2026-07-15 新增 7 个提交全部有已迁入或 NeoForge 等价结论，且 `src` 中的粒子实现与 `a0776721` 恢复后的旧版保持一致。

## Out of scope

- 本轮不迁移 `bbs-premiere-export/`。
- 不改变 Minecraft 1.21.1 / NeoForge 技术基线。
- 不在没有用户确认的情况下提交、推送或改写现有 27 个提交历史。
