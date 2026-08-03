# Journal - McAxolotl (Part 1)

> AI development session journal
> Started: 2026-07-29

---



## Session 1: 修复 runClient 缺失 client source set

**Date**: 2026-07-23
**Task**: 修复 runClient 缺失 client source set
**Branch**: `fix/run-client-startup`

### Summary

Gradle runClient 未包含 client source set 导致客户端启动失败，补齐配置；经 PR #6 合入主仓。

### Main Changes

### Main Changes

- `runClient` 未包含 client source set，导致客户端启动失败；修正 Gradle source set 配置。

### Context

迁移前在个人 fork（`McAxolotl/bbs-FSR`）目录 `Documents/MinecraftDev/Projects/Mods/bbs-FSR` 中完成，经 PR #6 合入 FSRteam 主仓。

### Notes

本条为 2026-07-29 工作区迁移时依据 git 历史回填，非当时实时记录。原始 Claude/Codex 对话可用 `trellis mem` 检索（见下方检索说明）。


### Git Commits

| Hash | Message |
|------|---------|
| `8cb749ed` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Windows CRLF 与 8.3 短路径测试硬化

**Date**: 2026-07-25
**Task**: Windows CRLF 与 8.3 短路径测试硬化
**Branch**: `fix/windows-line-ending-and-short-path-tests`

### Summary

硬化 source-level 测试以适配 Windows CRLF 行尾与 8.3 短路径，并清理硬化逻辑；经 PR #8 合入主仓。

### Main Changes

### Main Changes

- 让 source-level 测试在 Windows 上对 CRLF 行尾与 8.3 短路径保持稳定。
- 随后一次清理提交收敛了硬化逻辑。

### Context

Windows 开发环境特有问题：CRLF 与短路径会让部分测试在本机失败或无法失败（假通过）。经 PR #8 合入主仓。

### Lessons

Windows 上的行尾差异会波及 git 与测试两侧。工作区仓库通过 `.gitattributes`（`* text=auto` 加逐类 `eol=lf`）约束，`new/` 亦有同类配置；本机 `core.autocrlf` 未设置，依赖 gitattributes 生效。

### Notes

本条为 2026-07-29 工作区迁移时依据 git 历史回填。


### Git Commits

| Hash | Message |
|------|---------|
| `61cace6a` | (see git log) |
| `e8a6e06b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: pose form 与 MobForm 编辑修复链

**Date**: 2026-07-26
**Task**: pose form 与 MobForm 编辑修复链
**Branch**: `feature/pose-form-refactor`

### Summary

移植 pose form 骨骼增强，稳定 pose/MobForm 编辑，修复 film 预览骨骼位移与 gizmo 采样；经 PR #9 合入主仓。

### Main Changes

### Main Changes

- 移植 pose form 骨骼增强能力。
- 稳定 pose 与 MobForm 编辑行为。
- 修复 film 预览中 MobForm 骨骼位移错误。
- 将 film 骨骼 gizmo 统一为单一 placement 采样路径。
- 合并 upstream/master 进特性分支。

### Context

五个提交构成一条完整的 pose/MobForm 编辑修复链，经 PR #9 合入 FSRteam 主仓。相关 spec 位于 `.trellis/spec/new/backend/`。

### Notes

本条为 2026-07-29 工作区迁移时依据 git 历史回填。原始对话可用 `trellis mem` 检索。


### Git Commits

| Hash | Message |
|------|---------|
| `b544b789` | (see git log) |
| `3747bdf8` | (see git log) |
| `793b2a3d` | (see git log) |
| `76316eb6` | (see git log) |
| `78b99216` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: action state 层（未合入主仓，待续）

**Date**: 2026-07-26
**Task**: action state 层（未合入主仓，待续）
**Branch**: `feature/action-state-forms`

### Summary

新增共享实体 action state registry，并让 MobForm 与关键帧状态由该层驱动。已推 fork，未合入 FSRteam master——迁移后需决定是否继续。

### Main Changes

### Main Changes

- 新增共享的实体 action state registry（`feature/action-state-core`）。
- 让 MobForm 与关键帧状态由 action state 层驱动（`feature/action-state-forms`）。

### Status

**未合入主仓。** 两个提交已推到个人 fork `McAxolotl/bbs-FSR` 的同名分支，但尚未进入 `FSRteam/bbs-FSR` 的 master。

新工作区的 `new/` 目前配置为只读跟随主仓 master（单分支、origin=FSRteam），因此这两个分支的内容不在新工作区内。若要继续这条线，需要先在 `new/` 添加 fork remote 或另开分支。

### Notes

本条为 2026-07-29 工作区迁移时依据 git 历史回填，用于避免这条未完成工作在迁移中丢失。


### Git Commits

| Hash | Message |
|------|---------|
| `a39c745d` | (see git log) |
| `46ecf0bc` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: 部署共享 Trellis 工作区并迁移历史记录

**Date**: 2026-07-29
**Task**: 部署共享 Trellis 工作区并迁移历史记录
**Branch**: `codex/bootstrap-trellis-workspace`

### Summary

部署 bbsmodinforge-workspace 到本机，克隆 new/ 主代码仓库，初始化开发者身份 McAxolotl，并回填迁移前的历史开发记录。

### Main Changes

### Main Changes

- 克隆共享工作区仓库到 `Documents/FSRteam/bbsmodinforge-workspace`。
- checkout 到 `codex/bootstrap-trellis-workspace`（工作区内容所在分支；`main` 仍只有初始 README，PR #1 未合并）。
- 按 `workspace-manifest.json` 克隆必需仓库 `new/` = FSRteam/bbs-FSR @ master，跳过两个可选上游参考仓库。
- 初始化开发者身份 `McAxolotl`。
- 回填迁移前的历史开发记录（session 1-4）。

### Verification

- `python ./scripts/validate_workspace.py` → 3 repositories, 636 tracked files
- `python -m compileall -q .trellis/scripts .codex/hooks .claude/hooks .cursor/hooks scripts` → 通过
- `.trellis/.developer` 未被 git 追踪（由 `.trellis/.gitignore` 忽略）
- `new/` 未被工作区仓库追踪（`.gitignore` 以 `/*` 全排除加白名单）

### Lessons

- 工作区内容不在 `main` 上，而在未合并的 `codex/bootstrap-trellis-workspace` 分支。PR #1 合并后需 rebase 或切回 main。
- 旧 fork 目录 `Documents/MinecraftDev/Projects/Mods/bbs-FSR` 暂时保留未动，其中 `feature/action-state-*` 两个分支尚未合入主仓（见 session 4）。


### Git Commits

(No commits - planning session)

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: BBS-FS playback defect batch: MobForm death animation

**Date**: 2026-07-31
**Task**: BBS-FS playback defect batch: MobForm death animation
**Branch**: `work/mcaxolotl-journal-init`

### Summary

Fixed MobForm {Health:0} preview death animation by keeping vanilla entity.tick() dispatch (reference parity) and preserving Pose.DYING after per-frame synchronization. Added source-level regression contract. User verified dragon dissolve and generic mob death in-game before commit.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `4b2b1df0` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: BBS-FS playback defect batch - cleanup and handoff to external recording

**Date**: 2026-07-31
**Task**: BBS-FS playback defect batch (2/3)
**Branch**: work/mcaxolotl-journal-init

### Summary

Verified MobForm Health:0 death animation fix (vanilla entity.tick dispatch, no hardcoded entities, no new mixin/invoker) in-game; committed as 4b2b1df0. Removed 4 stale Claude worktrees from .claude/worktrees (all empty clones at Initial commit). Started external recording enchantment data task.

### Git Commits

| Hash | Message |
|------|---------|
| 4b2b1df0 | fix(mobform): restore vanilla death animation playback |

### Status

[OK] **In progress** (external recording)

### Next Steps

- Trace external recording serialization vs bbs-fs-master; find enchantment data loss point.


## Session 7: External recording enchantment data - root cause found, runtime regression recorded

**Date**: 2026-07-31
**Task**: External recording enchantment data - root cause found, runtime regression recorded
**Branch**: `work/mcaxolotl-journal-init`

### Summary

Root cause: ItemStack.CODEC enchantment holders need RegistryOps; plain NbtOps silently emptied enchanted items at record time. First registry-aware fix passes round-trip test but fails at runtime because a global client registry provider makes server playback decode client-bound holders; ByteBufCodecs stream encode then throws 'Can't find id ... Registry'. Next step: per-context registry provider (client record/preview vs server playback/load). Changes left uncommitted for handoff.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `4b2b1df0` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete

## Session 8: External recording enchantment data - per-context registry fix implemented and committed

**Date**: 2026-07-31
**Task**: External recording enchantment data (3/3 fix)
**Branch**: work/mcaxolotl-editor-defect-batch (new/); work/mcaxolotl-journal-init (trellis)

### Summary

Implemented the per-context registry provider fix. Key decompiled-source findings that shaped the design:
- `ServerLifecycleHooks.getCurrentServer()` is a plain static field (set at server start, cleared at stop), NOT thread-isolated — so on an integrated server the client thread also sees the server, and thread-group detection (`SidedThreadGroups.SERVER`) is the reliable side signal.
- `RegistryFixedCodec.encode` only writes the holder's ResourceLocation and its `canSerializeIn` is an identity check against the holder's owner registry, so encode must use a registry matching the holder's binding; `RegistryFixedCodec.decode` binds holders to the ops registry, so server-side film decode MUST use the server registry or `setItemSlot`'s `RegistryFriendlyByteBuf` encode throws "Can't find id".

Final approach in `ItemStackKeyframeFactory`: `currentRegistry()` uses the server registry when on a server thread (thread-group check) and otherwise the client level registry installed via `setClientRegistryAccess`; explicit `toData/fromData/tryFromData(value, HolderLookup.Provider)` overloads added. `Inventory.stageForPlayer` passes `player.registryAccess()` explicitly. Removed the broken global `setRegistryAccessProvider` machinery.

### Main Changes

- `new/src/main/java/.../factories/ItemStackKeyframeFactory.java` — thread-group-aware per-call registry resolution + explicit provider overloads
- `new/src/main/java/.../film/replays/Inventory.java` — stageForPlayer uses player.registryAccess()
- `new/src/client/java/mchorse/bbs_mod/BBSModClient.java` — setClientRegistryAccess installs client level registry
- `new/src/replayKeyframeTest/.../ReplayIndexRemappingTest.java` — round-trip test uses setClientRegistryAccess

### Git Commits

| Hash | Message |
|------|---------|
| 94be896c | fix(replay): preserve enchantment data for externally recorded equipment |

### Testing

- [OK] compileJava / compileClientJava / compileReplayKeyframeTestJava pass
- [OK] testReplayKeyframeConsistency passes (incl. enchanted equipment round-trip)
- [OK] User runtime verification: external recording + playback of enchanted armor/hand items intact; unenchanted unaffected

### Status

[OK] **Completed**

### Next Steps

- None - task complete

## Session 9: Actor-mode Form gizmo anchor follow - fixed, plus additive blend leak fix

**Date**: 2026-07-31
**Task**: Actor-mode Form gizmo anchor follow (3/3)
**Branch**: work/mcaxolotl-editor-defect-batch (new/); work/mcaxolotl-journal-init (trellis)

### Summary

Fixed gizmo placement for actor-mode film replays. Root cause: `BaseFilmController.renderEntity` skipped actor replays entirely, so `Gizmo.INSTANCE.captureVisual` never ran and the gizmo stayed fixed on screen (user repro "gizmo固定在监视器"). Fix: a `gizmoOnly` pass on `FilmControllerContext` that keeps capturing transform/pose/anchor matrices without drawing the editor form over the world actor. Also fixed: disabling a replay no longer leaves the gizmo on a stale matrix (`canShowGizmo` now requires `replay.enabled`).

Incidental: analyzed and fixed MobForm additive blend leaking into nearby non-additive MobForms (opaque layers' clearRenderState is a no-op for blend, so (SRC_ALPHA, ONE) leaked into RenderSystem; `applyAdditiveBlend` now resets to default for non-additive forms and the render finally restores it). ModelForm intentionally NOT aligned (documented: it only does BRIGHTEN vertex colors, clamped at 255).

### Main Changes

- `BaseFilmController` / `FilmControllerContext` - gizmo-only render pass for actor replays
- `UIFilmController` - canShowGizmo requires replay enabled
- `MobFormRenderer` - additive blend no longer leaks
- `PoseFormRegressionSourceTest` - source-level checks for all three fixes

### Git Commits (new/)

| Hash | Message |
|------|---------|
| 94be896c | fix(replay): preserve enchantment data for externally recorded equipment (amended, body added) |
| 514b2fe2 | fix(film): keep gizmo placement following the replay anchor in actor mode |
| 268a6353 | fix(forms): stop additive blend leaking into later non-additive MobForms |

### Testing

- [OK] compileJava / compileClientJava pass
- [OK] testReplayKeyframeConsistency, testUiInputMailbox (incl. new source checks), testFilmEditorMigration, testIkPhysicsConsistency, testFilmCollaborationApi, testBinaryCompatibility, testMatrixUtils, testUiMotions, testParticleRuntimeConsistency pass
- [OK] User runtime verification: actor-mode gizmo follows anchor; disabled replay hides gizmo; nearby MobForm blocks no longer additive-polluted

### Status

[OK] **Completed**

### Next Steps

- None - task complete. Batch 07-31-bbs-fs-playback-defect-batch now [3/3 done].

## Session 10: Rebase commit messages and submit PR to FSRteam/bbs-FSR

**Date**: 2026-07-31
**Task**: Final wrap-up - commit messages, PR, trellis closeout
**Branch**: work/mcaxolotl-editor-defect-batch (new/); work/mcaxolotl-journal-init (trellis)

### Summary

Supplemented the MobForm death fix commit message (9c39eb8f -> 4b2b1df0) and rebased the 07-31 commits onto it (trees unchanged; 9c4f4697/6ba97618/a701adaf -> 94be896c/514b2fe2/268a6353). Verified 9c39eb8f contains no death-related hardcoded content - the only entity-specific references (minecraft:ender_dragon) are pre-existing rotation compensation. Marked the 07-30 editor defect batch complete (6/6); all of McAxolotl's trellis plans are now complete. Comprehensive branch review: full build + all 20 test suites pass, no debug leftovers, no hardcoded death handling. Submitted PR #14 to FSRteam/bbs-FSR (10 commits, 16 files; branch pushed directly to FSRteam).

### Git Commits (new/, rebased)

| Hash | Message |
|------|---------|
| 4b2b1df0 | fix(mobform): restore vanilla death animation playback (amended, body added) |
| 94be896c | fix(replay): preserve enchantment data for externally recorded equipment |
| 514b2fe2 | fix(film): keep gizmo placement following the replay anchor in actor mode |
| 268a6353 | fix(forms): stop additive blend leaking into later non-additive MobForms |

### PR

- https://github.com/FSRteam/bbs-FSR/pull/14 (fix(film): editor and playback defect fixes 07-30 + 07-31 batches)

### Status

[OK] **Completed** - all McAxolotl plans done, PR submitted

## Session 11: actor+fp issues deferred, frame-rate fix kept, PR flow corrected

**Date**: 2026-08-01
**Task**: 08-01-07-31-actor-fp-replay-invisible-render (deferred) / PR #14
**Branch**: work/mcaxolotl-editor-defect-batch (new/); work/mcaxolotl-journal-init (trellis)

### Summary

Analyzed four actor+fp recording/playback problems against the bbs-fs-1.21.1 reference (invisible render, fp playback stuck, actor-off playback failure, actor-on rotation loss + flicker). Per user decision, all actor+fp changes were REVERTED and the analysis recorded in trellis task 08-01-07-31-actor-fp-replay-invisible-render for a future session. Only the morph-menu 3D preview frame-rate fix was kept (committed as f40d5a9b in new/).

Also corrected the PR flow: the branch was mistakenly pushed to the personal fork; it was re-pushed directly to FSRteam/bbs-FSR, the fork-based PR #13 closed, and a new intra-repo PR #14 created (draft). The origin fetch refspec was widened (was master-only) so work/* branches are trackable.

### Git Commits (new/)

| Hash | Message |
|------|---------|
| f40d5a9b | fix(ui): keep UI preview animations interpolating while the game is paused |

### PR

- https://github.com/FSRteam/bbs-FSR/pull/14 (draft; fix(film): editor and playback defect fixes 07-30 + 07-31 batches + UI preview animation)

### Status

[OK] **In progress** (PR #14 draft; actor+fp issues deferred)

### Next Steps

- Future session: fix the deferred actor+fp recording/playback issues per the analysis in task 08-01-07-31-actor-fp-replay-invisible-render.

## Session 12: PR #14 final review and formalization

**Date**: 2026-08-01
**Task**: PR #14 review → ready
**Branch**: work/mcaxolotl-editor-defect-batch (new/)

### Summary

Final review of PR #14 (10 commits, 16 files, 526 insertions): all commits have full subject+body, no debug leftovers, master is an ancestor (clean merge), full build + all 20 test suites pass, and every change was user-verified in-game. PR #14 converted from draft to ready-for-review. Session handoff: next session starts a new feature branch from master.

### PR

- https://github.com/FSRteam/bbs-FSR/pull/14 (OPEN, ready for review)

### Status

[OK] **Completed** (PR ready; session ends)

### Next Steps

- New session: create a new feature branch from master.

## Session 13: 94be896c 不完整修复——物品选择面板 NBT 附魔变空气

**Date**: 2026-08-01
**Task**: 08-01-08-01-item-picker-nbt-registry-ops
**Branch**: work/mcaxolotl-item-picker-nbt-registry (new/)

### Summary

分析提交 94be896c（fix(replay): preserve enchantment data for externally recorded equipment），确认其修复不完整：只把 `ItemStackKeyframeFactory` 的序列化改为 registry-aware `RegistryOps`，但物品选择面板（`UIUnifiedPickOverlayPanel` 及死代码 `UIItemStackOverlayPanel`）仍用纯 `NbtOps.INSTANCE` 编解码 `ItemStack.CODEC`。导致附魔（registry-held 组件）物品在关键帧编辑器 NBT 框显示 `{}`，且任何一次合法 parse→decode 失败 → `ItemStack.EMPTY` → 物品变空气。

修复：`ItemStackKeyframeFactory` 暴露 `public static currentOps()`（复用 per-call registry 解析），两个面板共 4 处 `NbtOps.INSTANCE` → `currentOps()`。新增回归测试 `testEnchantedItemPickerNbtRoundTrip` 覆盖面板的 encode/parse 往返（94be896c 的测试盲区）。

### Git Commits (new/)

| Hash | Message |
|------|---------|
| 4d89f78d | fix(ui): registry-aware NBT for item picker editing of enchanted items |

### Status

[OK] 实现完成；`compileJava`+`compileClientJava` 通过，`testReplayKeyframeConsistency` 全绿。待用户游戏内验证。

### Next Steps

- 用户在游戏内验证附魔物品 NBT 显示/编辑后可开 PR。
- 经验：ItemStack.CODEC 任何调用点都必须用 registry-aware ops；纯 NbtOps 对 registry-held 组件（附魔/药水效果等）编解码失败。可考虑写入 spec。

## Session 14: NBT 全面审查 + 动画抽搐修复失败延期

**Date**: 2026-08-01
**Task**: 08-01-08-01-item-picker-nbt-registry-ops（当前）；08-01-08-01-form-list-preview-animation-twitch（延期）

### Summary

1. **NBT 全面审查**（3 个并行代理 + 实证诊断）：确认 `ItemStack.save` 需 `HolderLookup.Provider`；`BlockState.CODEC` 纯 NbtOps 安全（实证）；实体 `saveWithoutId`/`load` 走 level registry 安全；`DataStorageUtils`/`ServerNetwork` 安全。唯一残留低危项 A2（`ItemStackKeyframeFactory` 静默 registry 回退）已建延期任务 `08-01-08-01-itemstack-factory-registry-fallback`（不修复）。

2. **Form 列表预览动画抽搐**：4 个菜单列表缩略图默认动画抽搐重复。根因假设：`renderInUI` 不 tick 实体 → `tickCount` 冻结 → `ageInTicks = tickCount + partialTick` 振荡。两个例外统一为共享 renderer 被模型方块持续 tick（用户澄清"烈焰人例外"实为模型方块选中过的 Form）。实现 `advanceUIAnimation`（partial tick 回绕时 `entity.tick()`）+ 回归测试，编译与 5 个测试套件全绿。

3. **用户游戏内验证：动画修复未解决问题**。按用户要求：动画修复代码全部回退，任务延期不修复（PRD 已记录），分支只保留 NBT 面板修复。

### Git Commits (new/)

| Hash | Message |
|------|---------|
| 4d89f78d | fix(ui): registry-aware NBT for item picker editing of enchanted items |

（动画修复已回退，未提交；分支当前只含上述 NBT 提交）

### 任务状态

- `08-01-08-01-item-picker-nbt-registry-ops`：in_progress（NBT 修复保留，待游戏内验证）
- `08-01-08-01-form-list-preview-animation-twitch`：延期不修复
- `08-01-08-01-itemstack-factory-registry-fallback`：延期（A2）

### Next Steps

- 用户游戏内验证 NBT 面板修复后可开 PR。
- 动画抽搐后续调查需先推翻"列表不 tick → ageInTicks 振荡"假设或换方向。

## Session 14b: NBT 修复 PR 提交，会话收尾

**Date**: 2026-08-01
**Task**: 08-01-08-01-item-picker-nbt-registry-ops（completed）

### Summary

NBT 面板修复提交为 PR。PR #16 已创建：https://github.com/FSRteam/bbs-FSR/pull/16（分支 `work/mcaxolotl-item-picker-nbt-registry`，1 提交 `4d89f78d`）。PR 正文按以往风格（Summary + commit 表格 + Testing），不含未完成修复内容。自动化测试全绿；游戏内验证清单见 PR Testing 段。

trellis 闭环：
- `08-01-08-01-item-picker-nbt-registry-ops` → completed（PR #16）
- `08-01-08-01-form-list-preview-animation-twitch` → planning（延期不修复）
- `08-01-08-01-itemstack-factory-registry-fallback` → planning（延期 A2）
- `08-01-07-31-actor-fp-replay-invisible-render` → planning（既有）

### PR

- https://github.com/FSRteam/bbs-FSR/pull/16 (OPEN, 待 review/merge)

### Status

[OK] Session closed; PR #16 ready for review.


## Session 15: PR #15 审批并 squash 合并（FS 5848363c 迁移）

**Date**: 2026-08-02
**Task**: PR #15 审批并 squash 合并（FS 5848363c 迁移）
**Branch**: `work/mcaxolotl-journal-init`

### Summary

审批并合并 PR #15（[codex] Sync FS 5848363c migration to FSR，作者 snowstar）；随后关闭被其取代的 PR #12。

### Main Changes

1. **PR 状态**：OPEN → `MERGEABLE` 但 `mergeStateStatus: BLOCKED`（`REVIEW_REQUIRED`，零审批）。
2. **审批**：`gh pr review 15 --approve` 后 `reviewDecision: APPROVED`、`mergeStateStatus: CLEAN`。
3. **合并**：用户确认后 `gh pr merge 15 --squash`，`mergedAt 2026-08-02`，merge commit `b66871f5`，状态 MERGED。
4. **PR 内容**（snowstar 的 codex 会话产物）：FS `116ed397..5848363c` 行为迁移到 NeoForge 1.21.1 FSR；154 个上游提交 disposition：131 迁移 / 19 等价或已被取代 / 4 有意排除 / 0 阻塞。包含四元数变换、变换空间、拆分拖动策略、数字输入、DLS/classic IK、多目标求解、bone DoF、焊缝诊断、bone 选择器、轨道/正交相机编辑、冻结胶片帧、关键帧悬停预览、统一 Dock 行为、FormFrameCache；恢复胶片预览轴、ValueOrder 插入、replay 地面/疾跑状态三项修复；保留 FSR 远程输入保护、主题/音频/视频控制、粒子所有权、FormTranslucentQueue、glint/盔甲行为、预览姿态复用。
5. **验证**：testUiInputMailbox / testFilmEditorMigration / testIkPhysicsConsistency / testReplayKeyframeConsistency / testAudioVerification / compileJava compileClientJava / check / build / git diff --check 全绿；严格 ffmpeg/ffprobe 媒体探测跳过（本地无工具）；游戏内 transform/IK/相机/freeze/Dock/焊缝冒烟与 shader-off/Iris/Photon 视觉检查仍待人工验证。
6. **PR #12 关闭**：`[codex] restore UI compatibility and add glint keyframes`（snowstar，OPEN 未合并，分支 `codex/new-local-backup-20260727`）被 #15 取代（#15 同步范围已含 glint/盔甲行为保留）。`gh pr close 12 --comment` 说明 superseded，`closedAt 2026-08-02`。


### Git Commits

| Hash | Message |
|------|---------|
| `b66871f5195a5d476662c685d3e8686d7107b6e2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- 可选：清理已合并/关闭 PR 的远端分支（`codex/sync-fs-5848363-to-fsr`、`codex/new-local-backup-20260727`）。
- PR #15 引入的迁移尚待游戏内 transform/IK/相机/Dock/焊缝冒烟与 shader-off/Iris/Photon 视觉人工验证。

---

## Session 16: 仪表盘 dock NPE 与模型方块显示槽修复，PR #17 实测通过

**Date**: 2026-08-02
**Task**: `08-02-fix-dashboard-dock-npe`、`08-02-fix-modelblock-display-slot-placeholder`（均 completed）
**Branch**: `new/` 仓 `work/mcaxolotl-ui-defect-batch`（PR #17）；本 journal 在 `work/mcaxolotl-journal-init`

### Summary

同日连修两个脚本客户端回归，合入同一批量 PR（2 提交），实测通过后更新 PR 描述并闭环 trellis。

### Main Changes

1. **仪表盘 dock NPE（模型方块右键 / b·0 键崩溃）**：`UIFilmPanel` 的 `final dock` 字段原在构造器第 252 行才赋值，而 NeoForge 移植给 `UIReplaysEditor`→`UIReplayPropertiesPanel`/`UIReplaysListPanel` 构造器新加了 `getEditPanelTopOffsetPx()`（解引用 `dock.isLocked()`）调用，导致懒构建仪表盘时 NPE。右键路径被 BlockableEventLoop 吞掉（"交互无效"），tick 路径（b/0）外抛致命崩溃。参考仓对应面板构造期不触碰 dock。修复：`dock` 构造提前到 editors 创建块之前，删除原 252 行重复赋值，接线时序不变；提交 `46613ff5`。
2. **模型方块显示槽空槽被解码为占位（一/三人称与物品栏空白、挪动/中键拾取回弹）**：参考仓 `FormUtils.fromData` 解析失败返回 `null`；移植改为返回非空 `MissingForm`，叠加 `MapType.getMap(missing)` 返回空 Map，`ModelProperties.fromData` 把未设置的 display 槽（default/third/first/inventory）解成非空占位，`getForm(mode)` 不再回退默认 form → 各视角空白，重解析即"回弹"。团队观测为"bbs:anchor"（实为 `MissingForm`，anchor 是 BBS 的空表单概念）。修复：`fromData` 四槽改读 `data.getMap(key, null)`，空槽=null，与 `toData` 对称，且不改全局 MissingForm 保留特性；提交 `05473fd6`。
3. **对参考仓验证 + 审查 + 建 PR**：两修复方向均与 `ref/java/mods/bbs-fs-1.21.1/src` 对照确认；`compileJava`/`compileClientJava`/`testClientStructuralRegistration`（含 ModelProperties 往返 + UIFilmPanel 结构）全绿。分支 `fix/bugfix-batch` 重命名为 `work/mcaxolotl-ui-defect-batch`（沿袭 `work/mcaxolotl-editor-defect-batch` 批量命名），推送并开 PR #17（conventional commit，正文按仓 PR 模板）。
4. **实测通过**：模型方块右键开面板、b/0 开仪表盘不崩；手持一/三人称、物品栏图标、挪动与中键拾取显示均正常；PR 描述 Validation 更新勾选。

### Git Commits

| Hash | Message |
|------|---------|
| `46613ff5` | fix(ui): construct UIFilmPanel dock before replay editors |
| `05473fd6` | fix(modelblock): keep unset display slots null in ModelProperties.fromData |

### PR

- https://github.com/FSRteam/bbs-FSR/pull/17 (OPEN，2 commits，待 review/merge)

### Testing

- [OK] compileJava / compileClientJava / testClientStructuralRegistration
- [OK] 游戏内实测：右键、b/0、手持一/三人称、物品栏、挪动、中键拾取

### Status

[OK] Session closed; 两任务 completed，PR #17 ready for review。

### Next Steps

- PR #17 review/merge；合并后可清理远端 `work/mcaxolotl-ui-defect-batch`（视团队习惯）。
- 完整 `./gradlew build` 留 CI；PR 描述已如实标注。


## Session 16: Session 17 - MobForm bone lists adopt 2.5 hierarchy tree display

**Date**: 2026-08-03
**Task**: Session 17 - MobForm bone lists adopt 2.5 hierarchy tree display
**Branch**: `work/mcaxolotl-journal-init`

### Summary

Analyzed the bbs-fs 2.5 migration delta (b66871f5): 2.5 switched bone lists from flat label rows to UIBoneTreeList outliner trees, synced only for IModel paths; MobForm (BoneHierarchy) paths stayed flat. Adapted all three MobForm bone-list paths (form panel pose editor, film pose keyframes, glint keyframes) to the tree style via new UIBoneTreeList.setHierarchy(BoneHierarchy, hidden, labels); removed dead hierarchicalLabels/getLabels(indent) code. Nested repo commit 309f5c24 (7 files, +103/-22); full test-strict build passed; trellis-check 0 defects. Spec: frontend/component-guidelines.md bone-list tree contract.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `13f95ef2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 17: Session 18 - tracker attachment picker adopts 2.5 tree + display names

**Date**: 2026-08-03
**Task**: Session 18 - tracker attachment picker adopts 2.5 tree + display names
**Branch**: `work/mcaxolotl-journal-init`

### Summary

Follow-up defect from session 17: tracker clip attachment picker on MobForm still showed the old flat alphabetical list with raw internal matrix keys. Root cause: UIBoneTreeList.fillAttachments' formNodes only walked ModelForm subtrees, so pose form bones fell into the flat safety net (regressed the 9fe77292 display-name fix during the 2.5 migration). Fix in UIBoneTreeList: PoseForm branch builds the bone tree from BoneHierarchy (boneHierarchyRoots + remapFormKeys to matrix keys), safety net shows last path segment. Values stay exact collectMatrices() keys (TrackerFrame.resolve unaffected). Full test-strict build passed; trellis-check 0 defects. Commit 51b90e10 on work/mcaxolotl-tracker-attachment-bone-display (stacked on 309f5c24).

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `51b90e10` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
