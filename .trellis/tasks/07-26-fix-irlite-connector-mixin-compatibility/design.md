# Design: IRLite Connector Mixin compatibility patch

## Scope and ownership

本任务只修改 IRLite 1.21.1 源码和其测试构建产物。BBS FSR、Connector 和
Forgified Fabric API 作为固定整合环境，不在本任务中修改。

问题发生在 Connector 将 Fabric intermediary 名称转换为 Mojmap 后：Fabric 中两个
`World.setBlockState` 重载具有不同 intermediary 名称，但 Mojmap 中都叫
`Level.setBlock`。IRLite 发布字节码里的 Mixin 目标退化为不带描述符的名称后，Mixin
会匹配错误的三参数重载，而处理器仍保留四参数形状。

## Compatibility fix

采用上游 `f7e08e7a` 的语义修复：

- Mixin 目标从基础 `World` 改为客户端 `ClientWorld`；
- 注入唯一的 `updateListeners(BlockPos, BlockState, BlockState, int)`；
- 回调从 `CallbackInfoReturnable<Boolean>` 改为 `CallbackInfo`；
- 直接使用通知方法提供的 `oldState` 与 `newState`；
- 保留 identity guard，并调用现有
  `BlockShadowCache.invalidateChange(world, pos, oldState, newState)`。

该目标没有重载歧义，只在客户端真实可见的方块变化后触发，也不再需要
`World.isClient` 和高度边界检查。

## Artifact and version policy

首个测试构建应基于用户实际 1.1.2 依赖图，只移植 Mixin 修复。不要把上游提交中的
`irl-core 1.1.4` 坐标升级一起带入，因为公开 core 仓库尚未提供一致的 1.1.4
版本证据。

测试 JAR 必须有不同文件名或版本元数据。构建后检查嵌套 JAR，确保只包含一个、且是
预期版本的 `irl-core`。若最终决定采用完整上游 1.1.4 分支，必须先单独证明其 core
依赖可获取、可复现且无重复嵌套 JAR。

## Runtime validation flow

首轮实例只保留：

1. NeoForge 1.21.1；
2. Connector beta.15；
3. Forgified Fabric API；
4. BBS FSR 当前测试 JAR；
5. 修补后的 IRLite；
6. 与插件兼容的 Iris NeoForge。

Connector beta.15 将转换结果放在实例 `mods/.connector/`。替换源 JAR后必须删除旧
IRLite 对应的 mapped JAR，或清空该可再生缓存目录，再启动游戏。验证顺序为：

1. Connector 重新生成 mapped JAR；
2. 所有 IRLite Mixin 完成 prepare/apply；
3. BBS common setup 和 Fabric `bbs-addon` 导入完成；
4. 客户端进入世界和 BBS 编辑器；
5. IRLite Form、Iris shader 和方块阴影刷新通过。

## Failure classification

- 同一个 `WorldBlockChangeMixin` 描述符错误：补丁没有进入实际 JAR或缓存未清理。
- BBS 类/方法/字段目标缺失：回到 BBS API/ABI 兼容边界评估。
- 单个新的 Minecraft/Iris Mixin 映射错误：优先做第二个窄 IRLite 补丁。
- 多个互不相关的 Minecraft/Iris 核心 Mixin 连续失败：Connector 适配成本开始超过
  原生迁移收益，应停止补丁循环并规划原生 NeoForge 端口。
- 启动成功但阴影不刷新：检查 `ClientWorld.updateListeners` 是否覆盖 BBS 的实际
  方块编辑路径，而不是恢复到有重载歧义的 `Level#setBlock`。

## Rollback

保留原始 1.1.2 JAR及其哈希。回滚时恢复原 JAR并删除 Connector 生成缓存即可；本任务
不改变世界存档格式、配置格式或 BBS 数据协议。

