# Design — fs 2.5 增量同步到 FSR

## 总体路线

不做整树 diff/cherry-pick（fs 是 Fabric 1.20.4，FSR 是 NeoForge 1.21.1，渲染/网络/构建层
API 已分叉），而是**逐提交语义移植**：以 fs 每个提交的 diff 为规格说明，把行为落到 FSR 的
对应文件上。每个移植项在提交信息里注明 fs 源提交哈希，保证处置清单可追溯。

## 边界与契约

- **只动行为，不动架构**：FSR 已有等价实现的（矩阵快照、appear/disappear 生命周期）不重复
  移植；fs 新类（DeathPose、FluidVertexConsumer、ItemUsePose 等）按 FSR 包结构落位，包名、
  访问修饰符跟随 FSR 现有风格。
- **两套体系调和点**（唯一的设计决策点）：
  1. 物品使用回放：fs 用 `ItemPredicateDonor`+`ThirdPersonItemUse`+`ItemUsePose`+`ReplayItemUse`；
     FSR 已有 `UseItemActionClip`+`InteractionActionSemantics`（07-17 体系）。**决策：保留 FSR
     的 action clip 体系作为录制载体，fs 的模型谓词/姿态渲染层（`ItemUsePose`、谓词驱动）作为
     渲染侧增强合入**，即谓词与姿态在回放时由 FSR 的 clip 状态驱动，而不是再造一套
     ReplayItemUse。本次先移植不依赖该决策的项，`0d11b2327` 全家（及其 C2 依赖项）若在本次
     时间盒内无法完整调和，则整体推迟并记录——半套体系比没有更糟。
  2. 伤害控制：`eb6609dca` 的语义（恢复必达终点、必然触发）以补丁形式落到 FSR 的
     DamageControl/ActionManager 上，不引入 fs 的六文件重排。
- **Mixin**：fs 侧 mixin（CameraInvoker/ParticleManagerInvoker/LivingEntityMixin 等）需按
  NeoForge 1.21.1 Mojmap 重新对位签名；accesswidener 条目转 AT 或 accessor。
- **本地化**：新增 UI 键只加 en_us + zh_cn（FSR 现有双语言集合），不跟随 fs 的 18 语言重排。

## 数据流不变量

- `675de150b` 常量插值默认只影响**新建**关键帧（factory 默认值），不重写已有 .bbfilm 数据。
- `04ba1dab4`/`15c44607a`/`33ad09b37` 等设置项走 FSR `BBSSettings` 现有模式（含默认值与
  round-trip），不引入新配置文件。
- 录制传送三连不改网络协议字段（`awaitMark` 是纯客户端等待环）。

## 回滚

每批一个提交；批次内失败可 `git revert` 单批。worktree 整体可丢弃，不影响 `new/` 主检出。
