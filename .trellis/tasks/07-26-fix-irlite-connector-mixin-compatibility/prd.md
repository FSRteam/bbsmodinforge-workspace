# 修复 IRLite 在 Connector 下的 Mixin 重载兼容性

## Goal

在 Minecraft 1.21.1、NeoForge、Sinytra Connector 与 BBS FSR 的组合中，
修复 IRLite 1.1.2 的 `WorldBlockChangeMixin` 启动崩溃，先验证小范围
IRLite 兼容补丁是否足以恢复完整运行，再决定是否需要原生 NeoForge 迁移。

## Confirmed Facts

- 真实测试组合包含 NeoForge `21.1.235`、Connector
  `2.0.0-beta.15+1.21.1`、Forgified Fabric API、
  `BBS_FSR_beta_v0.0.10.jar`、`irlite-1.1.2+mc1.21.1.jar` 和
  Iris NeoForge `1.8.12`。
- Connector 已发现 IRLite，并生成
  `irlite-1.1.2+mc1.21.1_mapped_moj_1.21.1.jar`；失败不是 Fabric 模组发现问题。
- 致命错误发生在 BBS common setup 之前：
  `irlite.client.mixins.json:WorldBlockChangeMixin` 应用到
  `net.minecraft.world.level.Level` 时抛出 `InvalidInjectionException`。
- IRLite 处理器保留四参数目标形状
  `(BlockPos, BlockState, int flags, int maxUpdateDepth, CallbackInfoReturnable)`，
  但 Connector/Mixin 将目标绑定到三参数 `Level#setBlock`，因此期望的处理器形状少一个
  `int`。
- Minecraft 1.21.1 的 `Level` 同时存在三参数和四参数 `setBlock` 重载。
- 当前本地 IRLite 快照仍将 Mixin 注入四参数
  `World.setBlockState(..., int, int)`。
- IRLite 上游提交 `f7e08e7a` 已针对同一 Connector 崩溃给出修复：把注入目标改为
  唯一的 `ClientWorld.updateListeners(BlockPos, oldState, newState, int)`，并使用其直接
  提供的新旧方块状态。
- 上游分支把插件版本提升到 1.1.4，同时把 `irl-core` 坐标改为 1.1.4；公开的
  `irl-core` 分支目前仍声明 1.1.3，因此不能未经验证地照搬依赖升级。
- 之前在 BBS FSR 本体完成的 `onInitialize()`、Fabric `bbs-addon` 反射导入和输入帧
  修复尚未在此组合中执行到；本次 Mixin 崩溃发生得更早。

## Requirements

- 修复应优先落在 IRLite，不能通过继续修改 BBS 生命周期、Addon API 或输入代码来
  掩盖外部 Mixin 描述符错误。
- 在 IRLite 1.1.2 对应源码/依赖基线上移植 `f7e08e7a` 中
  `WorldBlockChangeMixin` 的兼容修复。
- 保持当前可复现的 `irl-core` 版本和嵌套 JAR；除非先证明 1.1.4 artifact 可解析且
  必须升级，否则不得把 core 升级混入本任务。
- 生成带有可区分版本号或文件名的测试 JAR，避免与原始 1.1.2 混淆。
- 构建必须使用 Minecraft 1.21.1 对应配置和 Java 21。
- 检查成品 JAR 中只有预期版本的嵌套 `irl-core`，且
  `WorldBlockChangeMixin` 不再注入 `World/Level#setBlock(State)`。
- 替换测试 JAR 后清理 Connector 针对旧 IRLite 生成的转换缓存，再进行启动验证。
- 首轮整合验证使用最小模组组合，避免 Voxy、粒子等无关模组干扰责任归属。
- 启动成功后继续验证 BBS addon 注册、IRLite 点光/聚光 Form、Iris 光影加载以及
  方块变化后的阴影缓存刷新。
- 如果仍然失败，必须保留完整 `debug.log` 和 `latest.log`，并按 BBS API、
  Minecraft Mixin、Iris Mixin、Fabric API 或 Connector 转换边界分类。

## Acceptance Criteria

- [ ] `WorldBlockChangeMixin` 目标为唯一的客户端方块变化通知方法，不再绑定
      `World/Level#setBlock` 重载。
- [ ] IRLite 1.21.1 构建成功并生成可区分于原版 1.1.2 的测试 JAR。
- [ ] 成品 JAR 不包含重复或意外升级的嵌套 `irl-core`。
- [ ] 清理 Connector 缓存后，日志确认新 IRLite JAR 被重新转换和加载。
- [ ] 不再出现 `WorldBlockChangeMixin` 的三参数/四参数
      `InvalidInjectionException`。
- [ ] 启动越过 BBS common setup，日志确认 Connector/Fabric `bbs-addon` 被导入且
      IRLite addon 完成注册。
- [ ] 客户端能够进入世界并打开 BBS 编辑器，IRLite 点光源和聚光灯 Form 可用。
- [ ] 放置、破坏或由 BBS 修改方块后，相关动态灯阴影能够刷新且不产生新崩溃。
- [ ] 若仍失败，已给出新的首个失败边界和是否需要原生 NeoForge 迁移的证据化结论。

## Out of Scope

- 在本任务中完整迁移 IRLite 到原生 NeoForge。
- 修改 Connector 的通用转换器或发布 Connector 派生版本。
- 继续修改 BBS FSR 本体来绕过此 IRLite Mixin 错误。
- 升级 Minecraft、NeoForge、Iris 或加入与复现无关的模组。
- 修改 IRLite 光照算法、Shader patch 内容或无关 UI 功能。

## Open Questions

- 原始测试实例目录已在报告保存后移动或删除；执行者需要找到当前实际实例路径。
- 需要确认可用于重建 1.1.2 的精确源码基线和匹配的 `irl-core 1.1.2` artifact；若
  只能取得更新源码，应先建立依赖可复现性再修改。

## Decisions

- 2026-07-26：先做 IRLite 单点兼容补丁，不立即启动原生 NeoForge 迁移。
- 2026-07-26：只有在该修复后出现多处相互独立的 Minecraft/Iris 核心 Mixin
  转换失败，或 Connector 无法稳定承载关键运行路径时，才创建原生迁移任务。

