# 同步 FS 5848363c 到 FSR

## Goal

将 `temp/bbs-fs-2.3` 中自上一迁移基线以来、适用于 FSR 的最新 FS 行为完整迁移到
NeoForge 1.21.1 的 `new` 项目，并保留 FSR 已有的更强实现和产品边界。迁移必须在
独立 Git worktree 中完成，版本号与产物名保持不变，起始和终止提交写入 Trellis。

## Confirmed Facts

- FS `master` 从 `116ed3973163a09439c21f027b3d4307b8d893a9` 推进到
  `5848363c3e7c63ef124bad0c3b08809abd311f91`。
- FS `1.20.1` 从 `c3d1a47732ea85b804a249460c183bbf7a08190e` 推进到
  `813be205`；FS `1.21.1` 从 `31bd255e528cd620218ced82bef5ec7f9eab6b32`
  推进到 `5ed645834b46f7fd8c81e6c40dbc3e453949ac0b`。
- 目标基线为 `new@0544eadacbd0d2d60fd27d2a317e9e73483fa468`，且历史迁移
  `4143499b19d0f6138b7f8e8e1ff729a5a7ecbf77` 是其祖先。
- 目标版本锁为 `mod_version=0.0.11`、
  `archives_base_name=BBS_FSR_beta_v0.0.11`，Minecraft/NeoForge 为
  `1.21.1` / `21.1.222`。
- 原 `new` 工作区存在既有未跟踪构建日志；它们不属于本任务，不能移动、删除或提交。

## Requirements

- 在从准确的 `0544eada` 创建的独立 worktree 和 `codex/` 分支中开发。
- 先迁移三个可独立验证的缺陷修复：影片预览双轴复位、`ValueOrder` 插入顺序、
  回放地面/冲刺状态。
- 迁移缺失的变换与四元数、DLS/多目标 IK、骨骼选择、轨道相机与正交预览、
  影片退出冻结帧、焊接增强、关键帧悬停预览、Dock 单实现和 `FormFrameCache`。
- 以 FS `master` 表达行为，以 FS `1.21.1` 参考 Minecraft API，并适配 NeoForge/Mojmap。
- 对增量中的每个上游提交记录结论：已迁移、目标已等价、主动排除或有证据阻塞。
- 保留 FSR 粒子系统、`FormTranslucentQueue`、主题、插件、音频、glint、armor 和
  `0544eada` 的回放轨道/骨骼所有权修复。
- 不迁移已由上游回滚的着色器参数解析修复；不照搬“非激活图标不变暗”。
- 不更改版本号、产物名、Minecraft 版本、NeoForge 版本或发布标签。
- 在 Trellis 任务和迁移基线中记录 `fsr_initial_commit` 与真实
  `fsr_terminal_commit`。

## Acceptance Criteria

- [x] 独立 worktree 的起始提交严格为 `0544eada`，原 `new` 工作区内容保持不变。
- [x] 三个独立缺陷均有实现和聚焦回归测试。
- [x] 所有适用的缺失功能已迁移，并有逐提交处置矩阵；排除项有明确理由。
- [x] 粒子主线与 `FormTranslucentQueue` 未被上游实现替换。
- [x] `mod_version=0.0.11` 和 `archives_base_name=BBS_FSR_beta_v0.0.11` 未变化。
- [x] 聚焦测试、`compileJava compileClientJava`、`check` 和 `build` 通过；已知基线
      失败必须用未受本次改动影响的证据说明，不能伪报通过。
- [x] `git diff --check` 通过，最终提交是起始提交的后代，工作区只含任务文件。
- [x] Trellis 任务、研究记录和迁移基线写入三个上游源提交、起始提交、终止提交、
      验证结果与仍待手工运行的游戏内检查。

## Out of Scope

- 将 FSR 粒子主线替换为 FS 粒子实现。
- 降级或移除 FSR 的透明 Form 队列。
- 修改版本号、发布远端分支/标签，或合并原 `new` 工作区的未跟踪文件。
- 把未实际运行的 Minecraft/Iris/UI 手工检查写成已通过。
