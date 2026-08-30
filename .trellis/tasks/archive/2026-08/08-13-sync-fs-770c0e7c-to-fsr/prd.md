# 同步 FS 770c0e7c 增量到 FSR

## Goal

将 BBS FS `master@4220b453..770c0e7c`(2026-08-02~08-08 的 16 个提交)语义迁移到
`new/`(FSR,NeoForge 1.21.1)。全量逐提交处置:每个提交必须落为
ported / target-equivalent / rejected-with-reason 三者之一,拒绝必须有记录的理由。

迁移必须在独立 Git worktree 中完成;开始前记录准确的 FSR 初始提交,完成并验证后
记录真实的 FSR 终止提交,并同步更新 `fs-migration-baseline.md` 签名区。

## Confirmed Facts

- FS `master` 从已迁移基线 `4220b453bf96fbe41dde88f3ca51aa7fe9fcd612` 前进到
  `770c0e7c6b69cacc6dabf70eb63bf349f24bbaa6`(2026-08-08),线性 16 个提交,
  无 merge、无 force-push。
- 上游 `origin/1.20.1`(17183d15)与 `origin/1.21.1`(a982f1dc)已于 2026-08-09
  全量合并该批提交(merge-base = master tip);`origin/1.21.1` 含上游自己的
  Fabric 1.21.1 适配,是涉及 MC API 改动的直接对照。
- 版本号:上游这批提交期间 `mod_version=2.4` 逐字节未变;FSR 锁定
  `mod_version=0.0.11`、`archives_base_name=BBS_FSR_beta_v0.0.11`、
  Minecraft `1.21.1`、NeoForge `21.1.222`、Java 21。基线规格禁止把上游版本号
  抄入 FSR。
- `new/` 是独立 git 仓库,HEAD `637d6f76`(2026-08-08 merge,已含 GitHub
  PR #15~#20),当前分支 `codex/new-local-backup-20260727` 跟踪的远端分支已删除;
  工作区约 58 个未提交文件属于其他进行中任务,不属于本任务。
- 与 FSR 自有修复(07-29 以来 25 个【fix】提交)交叉比对:16 个上游提交无一已被
  FSR 完整等价修复。两处相邻但语义不同,移植时逐点核对:上游 `95a110e2`
  (编辑器 resize)vs FSR `46613ff5`(dock 构建顺序);上游 `861257a7`
  (gizmo 朝向)vs FSR `514b2fe2`(gizmo 锚点)。
- 逐提交行为分析与移植注意点见
  `research/fs-upstream-diff-2026-08-13.md`(本任务 research 目录)。

## Requirements

- 从 `new/` 确认的干净基点(预期 `637d6f76`,以开工时 fetch 核验为准)创建
  `codex/sync-fs-770c0e7c-to-fsr` 分支与独立 worktree,不修改原 `new/` 工作区。
- 任务文档先写入 `fsr_initial_commit`;验证并提交后再回填真实的
  `fsr_terminal_commit`,两者缺一任务即不完整。
- 16 个提交按依赖分组迁移(见 design.md 处置表),组内捆绑关系不得拆散:
  `8a293161`+`c02aa6e6`+`8c48a185` 捆绑;`fe552a58`+`0773f9df` 捆绑;
  `baafc5a4`+`770c0e7c` 捆绑。
- 共享文件只做语义合并,不整文件覆盖;保留 FSR 粒子主线、`FormTranslucentQueue`
  所有权、主题/远程输入/音频等 FSR 独有行为(遵守基线规格第 3 节全部契约)。
- 网络包改动按 NeoForge payload/StreamCodec 协议层重写,不照抄 Fabric
  `PacketByteBuf` 代码;物品序列化按 1.21.x DataComponent 现状适配。
- 电影存档格式变更必须连 `FilmLegacy` 旧格式迁移一起落地,旧电影行为
  (含"捡起物品可见"场景)不得回归。
- 为新增用户可见文本补齐 `en_us`、`ru_ru`、`zh_cn`,不得删除 FSR 其他语言或
  主题内容。
- 不改变模组版本、产物名、Minecraft/NeoForge/Java 版本或发布标签。

## Acceptance Criteria

- [x] 独立 worktree 的初始提交与任务记录一致,原 `new/` 工作区状态不变。
- [x] 16 个上游提交均有明确处置(ported / target-equivalent / rejected)且有证据,
      处置矩阵存入任务 research。
- [x] 渲染修复组生效:物品 form 不再渲染乱面、全半透明纹理模型不再自透;
      `FormTranslucentQueue` 所有权与粒子主线未被上游实现替换。
- [x] 热键栏九轨道 + inventory 移除落地:`FilmLegacy` 迁移可用,旧格式电影
      (含捡起物品场景)回归通过;网络包按 NeoForge 协议层实现。
- [x] UI 修复/功能落地:按钮间距、编辑器 resize、gizmo VIEW 朝向、滑条步进
      (FOV 除外)、主题色阶/阴影高光开关/开关控件;`bevelBox`→`surfaceBox`
      更名波及点全量处理,浅色主题不回归。
- [x] pixel art shader(`7f99e757`)与 UITrackpad ABI(`5b8d714f`)各有明确
      评估结论:落地,或记录拒绝/延期理由。
- [x] 聚焦测试、`compileJava compileClientJava`、`check`、`build`、
      `git diff --check` 通过;未运行的游戏内检查明确记为待验证。
- [x] `mod_version=0.0.11`、`archives_base_name=BBS_FSR_beta_v0.0.11`、
      Minecraft `1.21.1`、NeoForge `21.1.222` 均未变化。
- [x] 最终提交是初始提交的后代;任务与 `fs-migration-baseline.md` 回填真实
      终止 SHA 及三个上游参考标记。

## Verification notes (2026-08-16)

- 聚焦测试、`compileJava compileClientJava`、`check` 和实际存在的 `jarJar` 均成功；
  项目没有 `reobfJarJar` 任务，已核实并记录为不可用，而不是把它当作代码失败。
- `build` 在同一代码状态已成功生成 `BBS_FSR_beta_v0.0.11.jar`；重复组合命令因全套
  测试超过工具等待上限而中断，但没有出现编译或测试失败。
- 尚未运行人工游戏内烟测：半透明模型/物品 Form、九槽录制播放、主题观感，以及偏离
  屏幕中心的 VIEW Gizmo 场景；这些保留为发布前人工验收项。

## Out of Scope

- 上游 `origin/2.5` 分支;粒子系统改动;FS `31884112` 半透明回滚。
- 覆盖当前 `new/` 工作区中其他任务的未提交改动。
- 修改版本号、发布标签;推送分支或创建 PR(除非用户另行授权)。

## Commit Markers

```text
fs_master_previous = 4220b453bf96fbe41dde88f3ca51aa7fe9fcd612
fs_master_target = 770c0e7c6b69cacc6dabf70eb63bf349f24bbaa6
fs_1_20_1_reference = 17183d15cb32e5eff25d74ca6f7538ab5387faac
fs_1_21_1_reference = a982f1dc121185b04dbb26387f4cfa110b0a4c26
fsr_initial_commit = 637d6f76c5cdb08915c37682ef33ed0aa4c23e51
fsr_target_branch = codex/sync-fs-770c0e7c-to-fsr
fsr_worktree_path = .worktrees/new-fs-770c0e7c
fsr_terminal_commit = 19d4d0302ad8fd79a66baf231604ddd632c3a212
```

## Boundary Verification (2026-08-13)

- `git -C temp/bbs-fs-2.3 fetch --prune origin master 1.21.1 1.20.1`:成功。
- `4220b453` 是 `770c0e7c` 祖先:通过;区间提交数 16。
- `770c0e7c` 被 `origin/1.21.1@a982f1dc` 与 `origin/1.20.1@17183d15` 包含:通过。
- `new/` 侧:`origin/master@eaa43066` 已包含于 `637d6f76`,本地领先 7 个未推送提交,
  故取 `637d6f76` 为基点(GitHub master 的超集)。
- worktree `.worktrees/new-fs-770c0e7c` 创建于 `637d6f76`,原 `new/` 工作区未改动。
