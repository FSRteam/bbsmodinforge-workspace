# fs 2.5 → FSR 处置清单（源范围 770c0e7c6..8d99d77fe，38 提交）

处置时间：2026-08-23。FSR 侧：worktree `new-fs25-sync`，分支 `codex/sync-fs-8d99d77f-to-fsr`。
标记：✅ 已移植 · 🔁 已有等价 · ⏭️ 跳过

| fs 提交 | 主题 | 处置 | FSR 落点 / 理由 |
|---|---|---|---|
| 8d99d77fe | 录制倒计时 0 首拍哑火 | ✅ 034257e7 | UIFilmController 抽 startPlayback()，倒计时分支与 startRecording 末尾共用，带 isRunning 守卫 |
| 58b2b46ce | 锚点演员列表尾部丢失 | ✅ 034257e7 | UIAnchorKeyframeFactory.displayActors 以 replays.size() 与 map 最大键取计数上界 |
| a86b0688e | 轨道筛选「全部禁用」自锁 | ✅ 034257e7 | UIReplaysEditor/UIAnimationStateEditor 拆分类别过滤与禁用过滤，过滤后空列表仍建时间线；UIKeyframeDopeSheet 新增 setEmptyState 空态渲染（en/zh 文案，跟随 FSR「筛选轨道…」术语） |
| a28f20983 | 生物形态死亡倒向一侧 | ✅ 771d9bb5 | 新增 client/renderer/DeathPose.java（Mojmap 化）；MorphRenderer 两处 render 接入；ActorEntityRenderer 内联公式替换为 DeathPose.apply |
| f1aa3c338 | 预览物品形态三角扇面（Iris） | ✅ 771d9bb5（按 FSR 管线适配） | FSR 延迟绘制路径与 fs 不同（RenderLayerMixin 拦 RenderType.draw → drawLayer 上传）：捕获时快照 Iris `ImmediateState.renderWithExtendedVertexFormat`，RenderLayerCommand.draw() 的 drawWithShader 前钉住该值，画后恢复；ghost 命令固定 false（自建 builder 永不扩展）。IrisUtils/BBSRendering 新增 captureBufferLayout/applyBufferLayout |
| fdcc99240 | 方块形态水/岩浆/告示牌/末地门 | ✅ 771d9bb5 | 新增 FluidVertexConsumer（1.21.1 setUv/setUv1 API）与 SingleBlockRenderView（BlockAndTintGetter 实现，getShade/getBrightness(LightLayer)/getBlockTint/getLightEngine=null/getMinBuildHeight）；BlockFormRenderer.renderBlock 四路（流体→模型→BER→物品图标兜底），BER 经 getRenderer 判存在后 render(entity,0F,..)（1.21.1 光照由 dispatcher 内部处理） |
| eb6609dca | 伤害控制恢复必达且必然发生 | ✅ e69ebb77（补丁式语义移植） | FSR 生命周期与 fs 分叉大（07-17 安全加固），不做六文件重排：DamageControl 改 LinkedHashMap（修 O(n²)）+ 逐项 try/catch + finally 清空；ActionManager.stopDamage 改为先移出 map 再 restore（修「restore 期间被 mixin 回写 → 迭代中断」）；新增 isTrackingDamage() 供 mixin 在采样方块实体前短路。fs 的 owner-set 重构与 ActionRecorder 记忆世界两项不搬，FSR 的 teardown/terminal 体系已覆盖同等出口 |
| 433c5c82e | 录制开局等传送落地（awaitMark） | ✅ e69ebb77 | Recorder 新增 mark/markWait/MARK_REACHED=1/MARK_TIMEOUT=20 + awaitMark + reachedMark（超时告警放弃等待）；hasNotStarted 纳入 mark；update() 先倒计时后等 mark |
| a7a70fe3c | 传送只保留在「S Outside」按钮 | ✅ e69ebb77 | Films.startRecording 增 4 参重载（onMark），编辑器 outside 路径传 true，世界录制键维持原地 |
| 0aeb1a153 | 录制开局传送到 replay 标记点 | ✅ e69ebb77 | PlayerUtils.teleportToReplay（replay.getTick 时钟、无位置键则不动、返回 mark）；TELEPORT_GRACE=6 与服务端动作录制器时钟同步；UIReplaysEditor.teleport() 改为共用助手；设置 recording.teleport（默认开）+ en/zh 文案 |
| 675de150b | 跳变值默认常量插值 | ✅ 26bb5012 | IKeyframeFactory.isStepped() 默认 false；BlockState/Boolean/Integer/ItemStack/String 工厂返回 true；Keyframe(id,factory) 构造时对 stepped 工厂默认 Interpolations.CONST（只影响新建关键帧，不改存量数据） |
| 15c44607a | 拖动播放头自动重启动作 | ✅ 26bb5012 | UIFilmPanel.restartActions/updateRestartOnSeek（停稳一 tick 只触发一次）/canRestartOnSeek（播放/录制/控制/导出跳过）；FILM_CONTROLLER_RESTART_ACTIONS 键改引 panel::restartActions；设置 editor.restart_on_seek（默认关）+ en/zh 文案 |
| 8a9d3b321 | 鞘翅渲染到胸甲槽盔甲骨 | ✅ 26bb5012 | ArmorRenderer 构造器加 ELYTRA ModelPart；renderSlot CHEST+ELYTRA 分支 → renderElytra（站立/潜行/滑翔翼角，translate z+0.125，armorCutoutNoCull，glint，经 renderPart 走蒙皮路径）；ActorEntityRenderer 传 ModelLayers.ELYTRA |
| 26d35e664 | 演员头顶名牌 | 🔁 已有 | FSR BaseFilmController.renderNameTag + Replay.nameTag + UI 面板早已存在（见 research/a-group-verification.md） |
| 69b37fe98 | MobFormRenderer 矩阵快照 | 🔁 已有 | FSR 已有同款修复 |
| 704290a18 | 服务器只在编辑器在屏时驱动影片 | 🔁 已有 | FSR appear/disappear 生命周期语义覆盖 |
| 79a6134c1 | bump 2.5 | ⏭️ | 版本号不变更约束，FSR 保持 0.0.11 |
| 4fba216e6 | maven 发布项目名修复 | ⏭️ | FSR 构建体系不同，无此问题 |
| 7d35fd494 | 翻译修正（ru_ru 键） | ✅ 已应用（复核纠正） | FSR 实有 19 语言 strings，typo 存在；ru_ru.json「адио→аудио」已改 |
| 70274c5cb | Text 形态背景透明度 | ✅ 已移植（复核纠正） | FSR flush() 与 fs 修复前同构（blend 循环前一次 + clearRenderState 关混合）；已加 resetBlend() 逐命令重置（flush 循环 + GroupCommand child） |
| 0d11b2327 | 物品模型谓词（弓弯/盾挡）体系 | ✅ 8b1ccf41 + 29ce8157 + e7e913fd | 以 FSR UseItemActionClip/InteractionActionSemantics 为动作载体，新增 ItemPredicateDonor、ReplayItemUse、ItemUsePose 并完成 NeoForge 映射适配 |
| 92b84ed15 | 第三人称用物品手臂姿态 | ✅ 49113fe9 | VanillaArmPoses 接入 ProceduralAnimator，按回放使用状态驱动第三人称手臂 |
| c09434984 | 第一人称手部用物品动画 | ✅ 8b1ccf41 + e7e913fd | LivePlayerItemUse 与 LivingEntity/LocalPlayer client mixin 接入第一人称使用状态 |
| cca571c4e | 松开物品作为动作片段 | ✅ 8b1ccf41 | ReleaseUseItemActionClip、UI clip 注册及 LivingEntity release 注入已接入现有 InteractionActionSemantics |
| 237b369ab | 回放进食粒子与音效 | ✅ 8b1ccf41 + e7e913fd | ItemUseEffects 按 ReplayItemUse 播放进食粒子、声音和 burp 音效，完成 NeoForge API 适配 |
| b352c985c | 回放时箱子盖打开 | ✅ 已移植（2026-08-23） | 新增 actions/ContainerLid（blockEvent(1,1/0) + 双箱两半 + 末影箱，声音复刻 ChestBlockEntity 语义）；SuperFakePlayer 增 openLids/wantedLids/wantLidOpen/flushLids/getIfPresent；ActionClip 增 applyRange 钩子（每覆盖 tick 触发，先于频率门，apply 拆出 isEnabledInRange）；InteractBlockActionClip.applyRange（duration>1 且有权限时 wantLidOpen）；ActionRecorder 跟踪 interactClip（containerMenu≠inventoryMenu 期间按 tick 增长）；ActionPlayer 循环后 flushLids + stop() teardown 首步关全部盖。fs 依赖的物品使用体系并不需要——只依赖动作片段时长机制 |
| c03c64247 | IK 面板骨骼角色圆点 | ✅ 6081cd6b | UIBoneTreeList 与 UIModelIKFormPanel 增加骨骼角色标记和图例，保留 FSR classic-solver 分叉 |
| 72744b984 | 旧 IK 链迁移默认 classic | ✅ 已移植（2026-08-23） | FSR ModelIKIO.fromData 与 fs 修复前同构；无 "chains" 包装的扁平旧数据读入时 defaultClassic=true（redesign 前数据按旧求解器姿态），首次保存写显式 classic；javadoc 补版本标记说明 |
| 33ad09b37 | 拖动暂停 + 预览图标自动隐藏 | ✅ 840b215e | 拖动播放头自动暂停，预览图标按设置自动隐藏 |
| 04ba1dab4 | 轨道颜色/命名全局持久 | ✅ 6261057b | ValueTrackStyles 与轨道样式覆盖层持久化命名、颜色和稳定 filter key |
| 8034c677b | per-limb 箭头跨页存活 | ✅ d8629491 | 按 replay 保存 per-limb 展开集合，并在换页和插帧时恢复 |
| fc6a3c637 | 每条轨道都有图标 | ✅ e0463091 | curated 轨道统一提供安全图标 |
| e720f97ec | 模型轨道 picker 选择 | ✅ 66482934 + ae721c19 | 新增 UIModelPicker 并清理旧 picker 导入，模型轨道使用表单选择器 |
| 0a93fb98b | 数值字段同值 preset | ✅ 4e5a340c | UINumericInput 增加角度/系数 preset 同值操作 |
| 5437b9118 | 设置路径用文件对话框 | ✅ 4e5a340c | UIFileDialogs 与导出目录/路径行接入系统文件选择，兼容 FSR 现有 ffmpeg 设置 |
| e506e27cc | 设置分类重排 + 分辨率单滑条 | ✅ 4e5a340c | 设置页分类、分辨率行和旧设置迁移已落地，使用 FSR en/zh 文案 |
| 772847c5a | 形态编辑器渲染原版粒子 | ✅ 70b2c0ad | VanillaParticleScene、ParticleEngine/Camera accessor 与粒子 mixin 已按 NeoForge 1.21.1 对位 |
| ea3a356a6 | fs 自有 addon 客户端入口/事件层级 | ⏭️ | FSR addon API 2 为另一体系，只吸收「订阅者异常可见」思想（另行评估） |

## 版本分支独有项（不在 38 之内，附记）

- fs `1.20.1` 分支 `dc9637ea1`（1.20.1 ortho 修复）：与 FSR（1.21.1）无关，跳过。
- fs `1.21.1` 分支 `22cddcc67`/`13462beb2`（Sodium 0.8 反射兼容 / 新 Sodium 预览悬停 UI
  缩放）：FSR 原生 NeoForge 不受影响；Connector+Sodium 用户场景建议单开小任务评估。
- fs `1.21.11` 分支整包（Iris/着色器重构 ≈110 提交）：不在本次范围，仅作未来参考。

## 已移植项的游戏内冒烟清单

1. 倒计时设 0 → Ctrl+R 录制应立即开始（不再哑火）；对正在播放的影片上叠加录制不吞播放。
2. 禁用列表上方的 replay → 锚点选择菜单应列出全部演员（尾部不再丢）。
3. 轨道筛选「全部禁用」→ 时间线保留 + 空态文案；右键能再打开筛选恢复。
4. 水形态/含水方块形态/告示牌/末地门形态：预览与世界内均可见；录制后画面一致。
5. 死亡的 morph 玩家/生物应倒向一侧（20 tick 内平滑倒地）。
6. 影片放置方块后结束影片：所有方块（含双块结构的另一半）完整恢复；/fill 大范围改动不再卡顿。
7. 编辑器「S Outside」录制：开局玩家被传送到 replay 标记点，等待落地后开拍（0 倒计时不拍旧位置）。
8. 新建布尔/物品/字符串/方块状态关键帧默认常量插值；旧工程关键帧不受影响。
9. 开启「拖动时重启动作」设置后拖播放头：光标停稳后动作自动重启一次；播放/录制/导出期间不触发。
10. 胸甲槽放鞘翅：预览与回放中翅膀渲染在盔甲骨上，滑翔姿态正确，附魔光效正常。
11. Text 形态背景半透明（A 组遗留复核项）。
12. 演员与箱子交互且 clip 时长 >1：箱子盖（含双箱两半、末影箱）随片段开合；暂停/回拖/停止后盖不悬空；开关盖声音与原版一致（双箱只响一次、右半居中）。
13. 录制时打开箱子再关掉：交互 clip 的时长应等于开箱时长（时间线上 clip 拉长）。
14. 旧工程（扁平 ik map，无 "chains" 包装）载入：双骨链自动以 classic 求解器求解（姿态不漂移），首次保存后写显式 classic:true。
15. 半透明 Text 形态背景排在物品/方块形态之后绘制时背景正确混合（70274c5cb 场景）。
