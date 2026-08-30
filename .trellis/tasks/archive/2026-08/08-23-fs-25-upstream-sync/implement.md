# Implement — fs 2.5 增量同步到 FSR

## 前置

- [x] `task.py start 08-23-fs-25-upstream-sync`
- [x] `new/` 仓库建独立 worktree：`git -C new worktree add ..\new-fs25-sync -b codex/sync-fs-8d99d77f-to-fsr`
      （基点 = new 当前 HEAD `497c4284c1f96a125c8eaa2f54d37851422a6543`，已回填 prd.md）
- [x] 基线构建：基线 `497c4284` 自身带一处源码契约失败（splitter 提交未同步更新
      `KeyframeInteractionTest` 标记），已修复（`be944459` 前的工作区修复，随测试对齐提交）

## 批次（每批一提交，批后构建）

### B0 复核批（A 组，只写 research 结论，不改码或微改）
- [x] `7d35fd494` 翻译键核对 → **复核纠正：适用，typo 已改**（research/a-group-verification.md）
- [x] `70274c5cb` Text 形态背景透明度 → **复核纠正：同构 bug 成立，resetBlend 已移植**（同上）
- [x] `4fba216e6` maven 发布：确认 FSR 无 publishing 块，不适用 → 同上

### B1 小修复批（低风险）
- [x] `8d99d77fe` 倒计时 0 首拍哑火 → UIFilmController 抽 startPlayback()（034257e7）
- [x] `58b2b46ce` 锚点演员列表尾部丢失 → UIAnchorKeyframeFactory（034257e7）
- [x] `a86b0688e` 轨道过滤自锁 + 空态 → UIKeyframeDopeSheet/UIAnimationStateEditor/UIReplaysEditor（034257e7）

### B2 渲染修复批
- [x] `a28f20983` 死亡倒向一侧 → 新增 DeathPose（771d9bb5）
- [x] `f1aa3c338` 预览物品三角扇面 → Iris 顶点布局钉住（771d9bb5，按 FSR 管线适配）
- [x] `fdcc99240` 方块形态流体/告示牌/末地门 → FluidVertexConsumer + SingleBlockRenderView（771d9bb5）

### B3 动作/录制批
- [x] `eb6609dca` 伤害控制恢复必达 → 补丁式落地（e69ebb77）
- [x] `433c5c82e`+`a7a70fe3c`+`0aeb1a153` 录制开局传送 + awaitMark（e69ebb77）

### C1 功能批（不含 0d11b2327 全家）
- [x] `675de150b` 跳变值默认常量插值（26bb5012）
- [x] `15c44607a` 拖动播放头重启动作（26bb5012）
- [x] `8a9d3b321` 鞘翅渲染到盔甲骨（26bb5012）
- [x] `26d35e664` 演员名牌 → 复核确认基线已有，无需移植
- [x] `b352c985c` 回放时箱子盖打开（2026-08-23 移植；仅需动作片段时长机制，不依赖物品使用体系）

### C2 完整迁移批（2026-08-24 用户要求不再推迟）
- [x] `0d11b2327` + `92b84ed15` + `c09434984` + `cca571c4e` + `237b369ab`：物品谓词、第三/第一人称姿态、松开动作、进食粒子音效（8b1ccf41/49113fe9/29ce8157/e7e913fd）
- [x] `72744b984` 旧链 classic 迁移（dd0e0d97）
- [x] `c03c64247` IK 面板骨骼角色圆点（6081cd6b）
- [x] `33ad09b37` 拖动暂停与预览图标自动隐藏（840b215e）
- [x] `04ba1dab4` 轨道颜色/命名全局持久（6261057b）
- [x] `8034c677b` per-limb 展开状态与插帧跟随（d8629491）
- [x] `fc6a3c637` 每条 curated 轨道安全图标（e0463091）
- [x] `e720f97ec` 模型轨道 picker（66482934/ae721c19）
- [x] `0a93fb98b` 数值 preset、`5437b9118` 文件对话框、`e506e27cc` 设置分类与分辨率行（4e5a340c）
- [x] `772847c5a` 原版粒子表单预览（70b2c0ad）
- [x] `ea3a356a6` 仍按平台不适用跳过：FSR 已采用独立 Addon API 2，不移植 fs Fabric 入口代码

### 测试契约对齐（本会话新增）
- [x] `KeyframeInteractionTest`：基线 497c4284（splitter）加 `moveToFront(splitter)` 未更新标记
- [x] `BBSUiLifecycleSourceTest`：e69ebb77 的 `startRecording(film,index,cursor,true)` 4 参调用未更新标记

### 收尾
- [x] 定点验证：compileJava/compileClientJava、InteractionActions、FilmEditorMigration、ReplayKeyframeConsistency、IkPhysicsConsistency、ParticleRuntimeConsistency、ClientStructuralRegistration 全部真实执行并通过
- [x] 全量构建：2026-08-24 `gradlew.bat build --no-daemon --console=plain` BUILD SUCCESSFUL（6m25s，80 tasks，40 executed，PowerShell 真实 exit 0）
- [x] 最终适配提交：e7e913fd（NeoForge 1.21.1 物品使用映射）/ ef3a08f4（关键帧 scrub 测试契约）
- [x] 处置清单（38 提交逐项）→ research/disposition.md（含游戏内冒烟清单 15 项）
- [x] 回填 prd.md Commit 标记表 FSR 终 commit（最终文档提交后写入）
- [x] root 仓库提交 trellis 记录

## 验证命令

- 构建：`gradlew.bat build`（worktree 内）
- 定点：`gradlew.bat compileJava` 快速失败

## 回滚点

每批提交独立可 revert；B2 渲染批若 FSR 管线冲突大，可先 revert 该批再单独重做。
