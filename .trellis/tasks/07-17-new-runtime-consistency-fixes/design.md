# Technical Design

## Architecture and Boundaries

本任务以入口边界而不是症状分组：网络输入在服务端一次验证；UI 输入由一个 owner 仲裁；渲染上下文显式携带空间语义；模拟状态由 renderer + simulation owner 持有；影片/Replay 引用变化在单一重映射函数处理；导出 session 统一拥有成功、失败与取消终态。

十个子任务拥有互不重叠的主要文件。共享文件 `BBSMod.java`、`ServerNetwork.java`、`UIScreen.java`、`UIFilmPanel.java`、`FormRenderer.java` 由对应唯一子任务修改，其他代理只报告需要的集成点，由主任务统一合并。

## Contracts

### Network and filesystem

- 所有客户端路径先解析为规范绝对路径，再验证 `candidate.startsWith(root)` 且处理根本身、后缀和符号链接边界。
- `PacketCrusher` transfer key 至少包含连接/玩家、逻辑通道和 transfer id；同时限制单连接/全局在途数量、字节和 idle 时间，并在断线清理。
- 暂停、共享形态、模型方块编辑等改变他人/世界状态的包必须在 mutation 时重新验证权限、目标、维度、距离和会话。

### Interaction recording

- 只记录原版最终实际执行的交互分支；消费结果决定副手是否继续。
- projectile owner 在入世界前设置。攻击/交互记录保留目标与 hand，回放走等价的权限、距离和实体钩子。

### UI and lifecycle

- 本地和远程输入不能逐字段混合。激活的 remote controller lease 要么抑制本地手势，要么通过显式仲裁转移所有权。
- 每个离散事件后重新验证 screen/session generation；终态只释放由对应 owner 持有的键和按钮。
- Film session 固定 repository identity；断线保存/恢复发生在 handshake 清理前。

### Rendering and simulation

- Particle/Trail 的 camera-relative、entity-local、UI-local 空间由 `FormRenderingContext` 明示，不由全局 render flag 猜测。
- 全局 RenderSystem 状态保存旧值并在 `finally` 精确恢复；每帧 world composite 只执行一次。
- IK/physics 对最终实际写给 renderer 的 quaternion 夹限，绑定和 scratch 归 renderer + owner。

### Data and compatibility

- Replay 删除/排序共享同一索引重映射器。
- Particle unknown/raw 数据逐 sibling 保留，编辑合法项不删除未知项。
- Addon registration facade 在 callback/phase 后失效；duplicate 诊断不能修改首个赢家。
- 保持 API 2.0、NetworkCompat ID 和既有 JSON/NBT schema。

## Validation Strategy

- 为纯逻辑边界新增 dependency-light Java test source sets，或扩展现有 `uiInputMailboxTest`、`videoExportTest` 等。
- NeoForge 行为用本地 patched sources 作为单位/InteractionResult 证据。
- 完成后运行 targeted tests，再串行执行 `check build`，避免 Gradle clean 与共享本地 jar 竞争。
- 手测覆盖多人越权、断线影片、双手枪、方块交互录像、粒子远坐标、Iris、IK 模型、Film timeline 和 FFmpeg 早退。

## Rollback

每个子任务形成独立文件域和验证门；若集成失败，可按子任务文件集合回退，不改变数据格式或网络 ID。当前用户未提交改动始终保留。
