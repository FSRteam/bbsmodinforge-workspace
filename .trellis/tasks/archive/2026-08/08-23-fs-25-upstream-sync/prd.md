# fs 2.5 上游增量同步到 FSR（NeoForge 1.21.1）

## Goal

把上游 `Wemppy4/bbs-fs` 自上次同步点以来的 **38 个 master 提交（fs 2.4 → 2.5）** 中适用于
FSR（`new/`，FSRteam/bbs-FSR，NeoForge 1.21.1）的 bug 修复与功能更新逐项移植，跳过已修复与
平台不适用项。**本任务只做增量移植，不做大规模重构。**

## Commit 标记（任务开始/结束时必须回填）

| 标记 | 值 | 说明 |
|---|---|---|
| fs 上次同步点（源起点） | `770c0e7c6b69cacc6dabf70eb63bf349f24bbaa6` | FSR PR #23 已并入的 fs master 提交（fs 2.4） |
| fs 本次目标（源终点） | `8d99d77feea3ad13f7ceada78ee7679571251bd4` | fs master 最新（fs 2.5），源范围 `770c0e7c6..8d99d77fe` 共 38 提交 |
| FSR 初始 commit（已确认） | `497c4284c1f96a125c8eaa2f54d37851422a6543` | worktree `new-fs25-sync` 分支 `codex/sync-fs-8d99d77f-to-fsr` 的基点（2026-08-23 创建） |
| FSR 终 commit（代码终点） | `ef3a08f4f9d28fa142e4d31053c8a94715210da9` | 分支 `codex/sync-fs-8d99d77f-to-fsr`；自基点共 27 个迁移/修复提交，最后两项为 NeoForge 1.21.1 物品使用适配 `e7e913fd` 与 scrub 测试契约 `ef3a08f4`；全量 build 真实 exit 0 |

> 参考分支位（如需对照）：fs `origin/1.20.1` = `4cdee4ace033f12c450976bb8f9707f3cfa77092`，
> fs `origin/1.21.1` = `e6b8e382c6d6fa004ba1528035d190250d61d5fa`。

## 硬性约束

1. **版本号不变更**：FSR `new/gradle.properties` 的 `mod_version=0.0.11` 保持不动。
   fs 上游 `79a6134c1 bump version to 2.5` **不移植**；FSR 的版本演进由 FSR 自己的发布节奏决定。
2. **独立工作区**：实施必须在 `new/` 仓库的独立 worktree（如 `git worktree add ../new-fs25-sync -b codex/sync-fs-8d99d77f-to-fsr`）
   或等价独立克隆中进行，不得直接在 `new/` 主检出上开工；参考 PR #23 的分支命名
   `codex/sync-fs-<commit>-to-fsr`。
3. **不加新外部依赖**；Mixin 目标签名漂移按「修描述符不绕 require」处理。
4. 每组移植完成后构建一次（gradle build），不攒到最后。

## 源差异事实（2026-08-23 抓取）

- temp 的克隆（`temp/bbs-fs-2.3` 等 4 个）均为 `Wemppy4/bbs-fs`，本地参考位
  `5848363c`（fs 2.4 时代）落后 master 57 提交；已 `git fetch` 到最新。
- 注意：fs 的 **master 主线是 MC 1.20.4**（fabric_version 0.91.1+1.20.4），1.20.1 / 1.21.1 /
  1.21.11 是独立移植分支，mod_version 均已从 2.4 升到 2.5。
- 分支更新矩阵：
  - `master`: 770c0e7c6 → 8d99d77fe（38 提交，本次同步源）
  - `1.20.1`: 17183d15c → 4cdee4ace（= master 内容 + `dc9637ea1` ortho 1.20.1 修复 + 樱桃挑）
  - `1.21.1`: a982f1dc1 → e6b8e382c（= master 内容 + 2 个 Sodium 修复：`22cddcc67` Sodium 0.8 反射兼容、`13462beb2` 新 Sodium 下预览悬停 UI 缩放）
  - `1.21.11`: c22d89aad → fbd66eba7（≈110 提交的大版本移植 + Iris/着色器重构，**不在本次范围**，仅作参考）

## 分类清单（38 提交 → 三类）

### A. FSR 已有等价实现，跳过或仅复核

| fs 提交 | 内容 | FSR 现状 |
|---|---|---|
| `69b37fe98` | MobFormRenderer 全局 model-view 矩阵快照恢复（模型方块+生物形态撑爆 UI） | ✅ FSR 已有同款修复（`Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())` + `set(modelView)`，见 07-23 followup 任务） |
| `704290a18` | 服务器只在影片编辑器在屏时驱动影片（RESTART 移入 appear()/disappear 加 STOP） | ✅ FSR 已有 appear()/disappear() + `ActionState.STOP` 生命周期（实现不同但语义覆盖）；移植 `8d99d77fe` 倒计时修复时顺带复核 open() 的 RESTART 分支语义 |
| `79a6134c1` | bump version to 2.5 | ✅ 跳过（版本号不变更约束） |
| `4fba216e6` | maven 发布因项目名失败 | FSR 构建体系不同，N/A（如 FSR maven 发布无此问题则跳过） |
| `7d35fd494` | 翻译修正 | ⚠️ 需核对 FSR 的 strings 是否继承了同一处错键（低成本复核） |
| `70274c5cb` | FormTranslucentQueue：Text 形态背景忽略透明度 | ⚠️ FSR 的 FormTranslucentQueue 是重写版（needsSplit/needsWholeDefer 已带 alpha 判断），需实测 Text 形态背景半透明是否正确，代码大概率已覆盖 |

### B. FSR 尚未修复的 bug（本次核心，优先移植）

按风险从低到高排序：

1. `8d99d77fe` 录制倒计时=0 时首拍哑火：`handleRecording` 只在 `recordingCountdown > 0` 分支里
   启动回放，0 倒计时第一个 tick 直接判「影片没在跑→录制结束」。修复抽出 `startPlayback()`
   （带 `!isRunning()` 守卫，避免 toggle 掉正在跑的回放）。**FSR 的 UIFilmController 仍是旧代码
   （已核实无 startPlayback 方法），受影响。** fs 提交注明四分支同病，FSR 同源必中。
2. `a86b0688e` 轨道过滤「全部关闭」把过滤器自身锁死（dope sheet 空列表仍要建，空态文案归
   持有者；同一根因还在动画状态编辑器）。FSR 的 UIKeyframeDopeSheet 无对应处理（已核实）。
3. `58b2b46ce` 锚点关键帧工厂：实体 Map 按 replay 下标索引，禁用的 replay 无实体导致
   `size()` 不是键域，列表尾部演员丢失。FSR UIAnchorKeyframeFactory 无此修复（已核实）。
4. `f1aa3c338` 预览里物品形态被拆成三角扇面（BBSRendering/CustomVertexConsumerProvider/
   IrisUtils 三处）。FSR 有自己的 IrisUtils，需要按 FSR 渲染管线对照移植。
5. `fdcc99240` 方块形态渲染水/岩浆/告示牌/末地传送门（新增 FluidVertexConsumer +
   SingleBlockRenderView，357 行）。FSR 无这两个类（已核实），方块形态流体目前应不可见。
6. `a28f20983` 生物形态死亡倒向一侧（新增 DeathPose.java + MorphRenderer/ActorEntityRenderer
   改动）。FSR 无 DeathPose（已核实）；与 FSR 任务 08-01-death-keyframe-track 相关，需对接
   FSR 已有死亡关键帧。
7. `eb6609dca` 伤害控制恢复不彻底且非必然触发（ActionManager/ActionPlayer/ActionRecorder/
   DamageControl/WorldMixin/ServerNetwork 六文件 227 行重做）。FSR 有自己的伤害控制系统，
   需对照 FSR 的 DamageControl 逐条核对语义（恢复到终点、必然触发）。
8. `433c5c82e` + `a7a70fe3c` + `0aeb1a153` 录制传送三连：开局传送到 replay 标记点
   （`awaitMark` 等传送落地最多 20 tick 才开录，防止开头几 tick 拍在旧位置）+「传送到机位」
   只保留在 Outside 按钮上。FSR Recorder 只有 shutdown 时的位置恢复，无 awaitMark（已核实）。

### C. 功能更新（建议移植，按价值分组）

**C1 强烈建议（行为正确性/高频编辑器体验）**

- `675de150b` 跳变值默认常量插值（Keyframe + 6 个 factory 加 `interpolation` 默认）——
  防止布尔/物品/方块状态等离散轨道被线性插值弄脏。
- `15c44607a` 拖动播放头时重启动作片段（设置项 `scrubRestartActions` 类语义）。
- `0d11b2327` 物品模型谓词在演员身上生效（弓弯曲、盾格挡）——注意与 FSR 已有的
  `UseItemActionClip`/`InteractionActionSemantics`（07-17 任务）体系调和，fs 用的是
  ItemPredicateDonor + ThirdPersonItemUse + ItemUsePose + ReplayItemUse 一套新类
  （FSR 均无，已核实）。
- `8a9d3b321` 鞘翅放在胸甲槽时渲染到盔甲骨骼（ArmorRenderer +74 行；FSR 的
  ArmorRenderer/GeoArmorSupport 无 elytra 处理，已核实）。
- `26d35e664` 演员头顶名牌（BaseFilmController +65 行）。
- `b352c985c` 回放时箱子盖打开（ContainerLid + SuperFakePlayer，与动作系统耦合，
  排在 0d11b2327 之后）。

**C2 建议后续批次（编辑器 UX）**

- `33ad09b37` 拖播放头暂停 + 预览图标自动隐藏（设置项）。
- `04ba1dab4` 轨道颜色/命名手动设置并全局持久（ValueTrackStyles + UITrackStyleOverlayPanel）。
- `8034c677b` per-limb 箭头跨标签页存活、「I」键跟随。
- `fc6a3c637` 每条轨道都有图标；`e720f97ec` 模型轨道用 picker 选择（UIModelPicker）。
- `0a93fb98b` 角度/系数数值字段一键同值 preset（UINumericInput +21 行）。
- `5437b9118` 设置里路径用系统文件对话框（UIFileDialogs/WindowsFolderDialog——注意 FSR
  已有 08-17 的 UIFFMpegPath 下载面板，需合流而不是并存两套）。
- `e506e27cc` 设置页分类重排 + 分辨率单滑条（⚠️ 200+ 行 18 语言 strings 重排，移植成本高，
  建议放最后或只取分类骨架不改键名）。
- `772847c5a` 形态编辑器里渲染原版粒子（VanillaParticleScene + 3 个 mixin；FSR 是 NeoForge，
  mixin/accessor 需按 1.21.1 NeoForge 重新对位）。
- `92b84ed15` 第三人称用物品的手臂姿态（VanillaArmPoses/ProceduralAnimator）；依赖 C1 的
  0d11b2327 体系。
- `c09434984` 第一人称手部播放用物品动画（LivePlayerItemUse + 2 mixin）；同上依赖。
- `cca571c4e` 松开物品作为动作片段（ReleaseUseItemActionClip + LivingEntityMixin）；同上依赖，
  且与 FSR 的 InteractionActionSemantics 需要决定「接还是并」。
- `237b369ab` 回放时的进食粒子与音效（ItemUseEffects）；同上依赖。
- `c03c64247` IK 面板骨骼列表里骨骼角色用圆点标注（UIBoneTreeList +123 行）——FSR 的 IK
  是自己的 classic-solver 架构（ClassicLimbSolver/ModelIKIO），语义可对上。
- `72744b984` 旧 IK 链迁移时默认启用 classic solver（ModelIKIO 19 行）——需对照 FSR 的
  ModelIKIO 迁移路径。

**C3 暂缓/按需**

- `ea3a356a6` fs 自己的 addon 客户端入口/事件层级/订阅者错误可见——FSR 的 addon API 2
  （docs/addon-api-2.md、07-17/08-18 任务）是另一套体系，只建议吸收「订阅者异常要暴露」的
  思想，不移植代码。
- 1.21.1 分支独有的 `22cddcc67`/`13462beb2`（Sodium 0.8 反射兼容、新 Sodium 预览悬停 UI
  缩放）：FSR 原生 NeoForge 不受影响，但 FSR 用户可能经 Connector 跑 Sodium（见 07-26
  Connector 兼容任务），**值得单独开小任务评估**，不塞进本次。
- 1.20.1 分支独有 `dc9637ea1`（1.20.1 ortho 修复）：与 FSR（1.21.1）无关，跳过。
- 1.21.11 分支整包：跳过。

## Requirements

1. 按上文 A（复核）→ B（修复）→ C1 → C2 顺序在独立 worktree 分批移植，每批一次构建验证。
2. B 组 8 项必须全部落地或给出「FSR 不受影响」的核实结论（写入本任务 research/）。
3. C 组中依赖 0d11b2327 物品使用体系的条目必须排在它之后；与 FSR 已有
   UseItemActionClip/InteractionActionSemantics 的合并策略在 design.md 里先定案再动手。
4. `7d35fd494`/`70274c5cb` 两项复核结论（受影响/不受影响）写入 research/。
5. 版本号、构建脚本除必要 porting 适配外不动。

## Acceptance Criteria

- [x] 独立 worktree + 分支 `codex/sync-fs-8d99d77f-to-fsr`（或同等命名），PRD 的 Commit 标记表
      已回填 FSR 初始/终 commit。
- [x] B 组 8 项修复在 FSR 生效，源码/定点测试已覆盖；游戏内场景保留于冒烟清单。
- [x] C1 与所有适用 C2 功能均已迁移；只跳过版本号、构建体系及 Addon API 平台不适用项。
- [x] `new/` 全量构建通过（gradle build），新增 client mixin 已注册并通过客户端结构检查。
- [x] `mod_version` 仍为 `0.0.11`。
- [x] 合入后 fs 源范围 `770c0e7c6..8d99d77fe` 的处置清单（移植/已有/跳过+理由）完整存档于
      本任务目录。

## Notes

- 上游 fs 下一轮若有新提交，重新 fetch 后以本任务终 commit 对应的 fs 位为新同步点。
- 1.21.11 分支的 Iris/着色器重构（e71beccc3 等）与 FSR 的渲染路线可能在未来冲突，届时优先
  对齐设计而不是 cherry-pick。
