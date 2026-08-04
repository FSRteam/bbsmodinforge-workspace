# 同步 FS 27a6866c Dock 与生命周期修复到 FSR

## Goal

将 BBS FS `1.20.1@27a6866c` 中相对已迁移基线 `813be205` 的最新 Dock
功能和适用缺陷修复迁移到 `new/`（FSR，NeoForge 1.21.1），同时保留 FSR
现有主题、动画、插件、音频、渲染和编辑器扩展。

迁移必须在独立 Git worktree 中完成；开始前记录准确的目标初始提交，完成并验证后
记录真实的目标终止提交。

## Confirmed Facts

- FS `master` 从已迁移的 `5848363c3e7c63ef124bad0c3b08809abd311f91`
  前进到 `4220b453bf96fbe41dde88f3ca51aa7fe9fcd612`。
- FS `1.20.1` 从 `813be205e83b571823d58736b9213f8309811186` 前进到
  `27a6866c14e5351aed50fd3b85c9d6c5fa97467c`，并包含新的 `master` tip。
- 新增行为提交只有三个：`6f4f9ec9`、`0cecc8c9`、`4220b453`；
  `27a6866c` 本身是分支合并提交。
- `new/origin/master` 的核验基线为
  `156d0bffc2b884d7bf645225d27fc72bb57f3cb5`，它已包含上一次 FS
  迁移的压缩提交 `b66871f5`。
- 原 `new/` 工作区有其他任务的已修改和未跟踪文件，不属于本任务。
- FSR 版本锁为 `mod_version=0.0.11`、
  `archives_base_name=BBS_FSR_beta_v0.0.11`、Minecraft `1.21.1`、
  NeoForge `21.1.222`。

## Requirements

- 从 `new/origin/master@156d0bff` 创建 `codex/sync-fs-27a6866c-to-fsr`
  和独立 worktree，不修改原 `new/` 工作区。
- 在任务记录中先写入
  `fsr_initial_commit=156d0bffc2b884d7bf645225d27fc72bb57f3cb5`；验证并提交后再
  写入真实的 `fsr_terminal_commit`。
- 语义迁移 `6f4f9ec9`：Dock 标签拖动、拖动说明、悬停切换标签、插入位置提示、
  Shift 中心投放交换、方向性投放高亮、面板最大化/恢复、布局撤销、面板隐藏/恢复、
  面板名称、分割线像素最小尺寸、双击均分、布局锁持久化和重置一致性。
- 审计 `0cecc8c9` 的全部七个剪裁调用。目标已有宽高语义的调用保持不变；只有仍使用
  右下角坐标的调用才迁移，避免重复或反向修复。
- 语义迁移 `4220b453`：编辑器销毁时同步移除外置属性面板；用布局稳定回调更新影片
  预览尺寸，覆盖拖放、撤销、最大化、隐藏、标签切换、重置和分割线拖动结束。
- 将高优先级 undo/redo 覆盖层改成严格组合键匹配，避免吞掉布局撤销快捷键。
- 为新增用户可见文本补齐 `en_us`、`ru_ru` 和 `zh_cn`，不得删除 FSR 其他语言或
  Refreshed 主题内容。
- 共享文件只做语义合并，不整文件覆盖，不移除 FSR 独有行为。
- 不改变模组版本、产物名、Minecraft/NeoForge/Java 版本或发布标签。

## Acceptance Criteria

- [x] 独立 worktree 的初始提交严格为 `156d0bff`，原 `new/` 状态不变。
- [x] 三个新增上游提交均有明确处置：已迁移、目标已等价或有证据排除。
- [x] Dock 增强行为完整进入 FSR，布局树保存/加载兼容旧配置和预设。
- [x] 影片/关键帧/剪辑外置属性面板不会在所有者销毁后残留。
- [x] 影片预览尺寸在所有稳定布局变更后更新，分割线拖动中不逐帧重建。
- [x] 剪辑、回放、关键帧和时间尺剪裁不会越过右/下边界。
- [x] 普通 Ctrl+Z/Y 与布局撤销快捷键不互相吞掉。
- [x] 聚焦测试、`compileJava compileClientJava`、`check`、`build` 和
  `git diff --check` 通过；未运行的游戏内检查明确记为待验证。
- [x] `mod_version=0.0.11`、`archives_base_name=BBS_FSR_beta_v0.0.11`、
  Minecraft `1.21.1`、NeoForge `21.1.222` 均未变化。
- [x] 最终提交是初始提交的后代，任务和迁移基线记录真实终止 SHA。

## Out of Scope

- 迁移 FS `2.5` 分支或改动粒子、透明 Form 队列。
- 更换 FSR 主题/动画系统，或覆盖当前 `new/` 工作区的其他任务改动。
- 修改版本号、发布标签、推送分支或创建 PR。

## Commit Markers

```text
fs_master_previous = 5848363c3e7c63ef124bad0c3b08809abd311f91
fs_master_target = 4220b453bf96fbe41dde88f3ca51aa7fe9fcd612
fs_1_20_1_previous = 813be205e83b571823d58736b9213f8309811186
fs_1_20_1_target = 27a6866c14e5351aed50fd3b85c9d6c5fa97467c
fsr_initial_commit = 156d0bffc2b884d7bf645225d27fc72bb57f3cb5
fsr_terminal_commit = 1f46ac02ad07c98e47d43d6100df58fb0ae23acb
```

## Verification Record

- `testUiInputMailbox`: passed.
- `testFilmEditorMigration`: passed.
- `compileJava`, `compileClientJava`, `check`, `build`: passed.
- Built artifact: `BBS_FSR_beta_v0.0.11.jar`.
- Automated source audit: seven clip calls retain width/height semantics; particle
  core and `FormTranslucentQueue` are unchanged.
- In-game Dock interaction and visual smoke: not run; remains manual acceptance.
