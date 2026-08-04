# Design: IRLite Connector Mixin compatibility patch

## Scope and ownership

本任务修改 IRLite 1.21.1 源码和测试构建产物，并允许对 BBS FSR 的模型方块物品
渲染器做一个独立的窄修复。Connector 和 Forgified Fabric API 仍作为固定整合环境，
不在本任务中修改。

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

## Model block item restoration

模型方块物品继续使用 `minecraft:builtin/entity` 和现有 BEWLR；PNG 与物品模型资源
已证明存在，不能退化为普通 `item/generated`。缺陷位于渲染数据恢复和缓存时机：

1. 从 `DataComponents.BLOCK_ENTITY_DATA` 读取 `CustomData`；
2. 从其 NBT 的 `Properties` 节点通过 BBS 数据存储工具恢复 `MapType`；
3. 将数据直接写入临时 `ModelBlockEntity#getProperties()`；
4. 只有解析成功后才写入物品缓存；
5. 每次绘制前同步客户端世界，避免主菜单阶段创建的临时实体永久持有空世界。

这条路径不依赖不同映射环境下不稳定的 `loadWithComponents/load/readNbt` 反射重载，
也不会把“当前没有客户端世界”误当成“物品没有模型数据”。

## Beam-strength runtime contract

`beam_strength` 的单一数据链为：

```text
Form ValueFloat -> Form renderer / LightCollector -> LightRegistry
-> LightBuffer SSBO vlParams.z -> generated shader patch
```

修复必须落在研究确认的第一个失真边界。构建检查同时验证当前 patch 模板确实消费
`vlParams.z`，并避免旧的、带 marker 但不符合当前合约的已补丁 Shaderpack 被直接
复用。Java UI、CPU 上传和 Shader 中不得各自维护不同的默认值或字段含义。

### Connector fix4 independent texture path

`connector-fix3` 已证明发布 JAR、Connector 映射后的方法描述符、`LightRegistry` 数组和
`LightBuffer.vlParams.z` 写入均存在，但用户实机像素仍无变化。`fix4` 因此在不删除
SSBO 路径的前提下增加第二条 GPU 契约：

```text
beam_strength -> LightBuffer packed order
              -> R32F texture irl_beamStrengths[lightIndex + 1]
              -> Iris dynamic sampler -> generated shader texelFetch
```

纹理宽度为 `LightBuffer.MAX_LIGHTS + 1`，高度为 1。texel 0 写入固定哨兵
`1937.25`；第 `lightIndex + 1` 个 texel 写入 `max(beam, 0) + 1`。Shader 只在哨兵
匹配时读取并减去 1，否则回退 `max(light.vlParams.z, 0)`。这使新 core + 旧 Shader
仍使用 SSBO，新 Shader + 旧 core 也会因无有效哨兵回退 SSBO，同时避免新增固定 SSBO
binding 或改变现有六个 `vec4` 的布局。

采样器通过现有 `IrlSamplers`/Iris `addDynamicSampler` 注册，并追加在已有采样器之后。
补丁契约提升到 v2，七套补丁使用 `irlite-beam-texture-l10n-v4` 标记。core 与外层
addon 均使用新版本坐标，防止 Gradle/Maven/Connector 缓存复用 fix3 字节。

### Connector fix6 texture-state ownership

fix5 的 program/sampler 探针证明 Iris 最终会把正确纹理 id 绑定给 Photon
`composite`，但原上传代码先用 `glGetInteger(GL_TEXTURE_BINDING_2D)` 读取真实 OpenGL
状态，随后却用缓存式 `GlStateManager._bindTexture` 绑定和恢复。Iris 可在不更新该缓存
的情况下直接改变 GL 纹理状态，因此缓存式调用可能被错误跳过，使
`glTexSubImage2D` 写入当时实际绑定的其他纹理；稍后 sampler 再正确绑定目标 id 时，
仅检查绑定仍会得到假阳性。

fix6 保持 R32F 格式、哨兵、索引和 Shader 协议不变，只统一状态所有权：上传前后均以
直接 `GL11.glBindTexture` 绑定目标并恢复真实查询到的原对象。启用
`-Dirlite.debugBeam=true` 时，在目标仍绑定期间用 `glGetTexImage` 回读并逐 texel 比较
CPU 期望值，日志输出 marker、encoded 值、匹配结果和首个失配索引。正常客户端不分配
回读缓冲，也不执行同步回读。

## Localization ownership

IRLite 使用 `assets/irlite/strings/en_us.json` 与 `zh_cn.json` 作为唯一文本源。实际
兼容实现通过 `L10nMixin` 在每个 `L10n` 实例构造完成时注册 addon source pack 和
语言 provider，使资源在该实例首次 reload 前可见。当前 BBS FSR 使用四参数
`InternalAssetsSourcePack(modId, source, internalPrefix, ownerClass)`；反射只用于保持
旧 Fabric BBS 三参数构造器的源码兼容，Connector 运行时已验证四参数路径。所有用户
可见文本使用 `L10n.lang` 或同一 resolver；枚举选项、设置标题、预设、Shader Patcher
与调试界面共享 `irlite` 命名空间。

回放轨道本地化通过 IRLite 自己的 Mixin 在 `UIKeyframeSheet` 四参数构造器返回后替换
标题，仅处理 `PointLightForm` 与 `SpotlightForm` 的已知属性 id，并保留 BBS 对嵌套
Form 路径的显示规则。这样不需要把 IRLite 类型硬编码进 BBS 本体。IRLite 客户端
Mixin 配置声明 `JAVA_21`，与 Minecraft 1.21.1 和成品 class version 65 一致。

## Spotlight guide drag continuity

IRLite 的聚光灯 range、outer radius 和 inner radius 拖动必须使用相对手势：按下时保存
目标值和鼠标射线对应的原始几何量，后续写入 `startValue + currentRaw - anchorRaw`。
不得把绝对射线交点直接写入属性或影片关键帧，因为粗拾取区域、矩阵轻微差异和关键帧
插值都会让按下第一帧改变数值。

可见轴、锥线、拾取环和拖动数学共享 `spotRingZ(range)` 的同一正负号。当前灯光方向、
拾取环和拖动数学均使用局部 `+Z`，因此可见锥体也统一到 `+Z`。回归测试至少覆盖：

- 鼠标原地不动时，起始原始量即使与属性绝对值不同，属性也完全不变；
- 第一次移动从原值连续增加或减少，而不是跳到 `0.1`/`1` 的 clamp；
- range、outer angle、inner angle 分别遵守原有上下限；
- 影片拖动以关键帧原值作为相对起点，不把当前插值预览绝对覆盖到关键帧。

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
