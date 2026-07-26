# 修复 BBS 在 Connector 下的输入兼容性

## Goal

让 BBS FSR 在 NeoForge 1.21.1 上通过 Sinytra Connector 加载并使用
`bbs-irlights-addon` 的 `port/1.21.1` 分支时能够稳定运行，同时修复可在
BBS 自身触发的 viewport 输入栈失衡问题，避免把 BBS 缺陷误判为 Connector
或插件缺陷。

## Confirmed Facts

- 当前目标运行环境是 Minecraft 1.21.1、NeoForge、Connector 1.21.x 与 BBS FSR。
- `bbs-irlights-addon` 必须使用 `port/1.21.1` 分支；本地源码位于
  `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1`。
- 该插件按 BBS `2.2.1-1.21.1` 编译，而当前 FSR 运行版本为 BBS `2.3.2`，
  因此 BBS API/ABI 兼容性需要验证。
- 已提供的崩溃报告没有加载 Connector 或 IRLite，并显示 BBS 注册了 0 个 addon；
  它只能证明 BBS/FSR 的 viewport 输入路径存在独立崩溃，不能证明 Connector
  与 IRLite 组合的根因。
- 该报告的致命调用链为
  `NoSuchElementException -> ArrayDeque.pop -> UIViewportStack.popViewport -> UIContext -> UIBaseMenu.mouseClicked`。
- 当前仓库已经存在针对 pointer dispatch 的 viewport 清理以及
  `UIViewportInputCleanupTest`；用户确认当前版本与 Connector 组合仍不能正常使用，
  因此已有修复不是完整解决方案。
- IRLite 1.21.1 源码没有直接调用 BBS 的 `pushViewport`/`popViewport`，但其 mixin
  会介入 BBS 鼠标事件路径。
- IRLite 的必需 Mixin `BBSModMixin` 注入 `BBSMod.onInitialize`；Fabric BBS 具有该
  方法，而 FSR 已移除该符号并改用 NeoForge 生命周期，因此 Connector 转换后会遇到
  缺失的注入目标。
- FSR 的 `NeoForgeLoaderAccess#getEntrypoints("bbs-addon", ...)` 只返回经
  NeoForge 收集器注册的 addon，不查询 Connector 提供的 Fabric Loader 自定义入口；
  IRLite 的 `IrlightsAddon` 因此不会按原 BBS 合同注册。
- FSR 当前虽然取得 `addonEntrypoints`，但只记录数量，实际内部事件总线桥接仍只使用
  `BBSAddonCollector`；单独扩展查询结果不足以完成兼容。
- IRLite 依赖的模组 ID `bbs` 与 FSR 的 `mod_id=bbs` 一致；已核对的其余 BBS
  Mixin 目标类和主要方法仍存在，首个明确静态断点是 `onInitialize` 与 addon 发现。
- 本机现有 NeoForge 实例没有同时启用 FSR、Connector 与 IRLite：一个实例已禁用
  Connector，另外两个启用 Connector 的实例没有 IRLite，因此仍需建立目标组合复现。
- 当前 pointer dispatch 在开始和结束时重置同一个共享 viewport 栈；这可以阻止部分
  栈下溢崩溃，但嵌套派发会破坏外层事件状态，尚不能视为正确修复。

## Requirements

- 所有产品代码修改仅限当前 FSR 项目的 `new/`；不得修改 Connector 或 IRLite 源码。
- 使用与用户实际组合一致的版本建立最小、可重复的故障场景。
- 分别验证原生 Fabric 控制组、FSR 单独运行、FSR + Connector、
  FSR + Connector + IRLite，确定第一个发生行为偏差的边界。
- 区分并记录加载/转换失败、BBS API/ABI 不匹配、Mixin 注入失败和运行时输入重入。
- 修复应落在拥有缺陷的边界；不得仅通过忽略空 viewport pop 来掩盖状态失衡。
- 输入事件边界必须在正常返回、嵌套派发、界面替换和异常路径上恢复一致状态。
- 保持本地输入、远程输入以及不使用 Connector 的 BBS 行为兼容。
- 对发现的根因增加确定性回归测试，并保留真实整合运行的验证步骤。

## Acceptance Criteria

- [ ] 已取得一份确实同时加载 FSR、Connector 与 IRLite 1.21.1 的完整日志或等价复现证据。
- [ ] 已通过最小版本矩阵定位第一个失败边界，并用调用链、Mixin 结果或二进制符号证据说明责任归属。
- [ ] 已验证 IRLite 针对 BBS 2.2.1 的调用在当前 FSR 2.3.2 上是否保持兼容。
- [x] 已有 viewport 清理测试继续通过，并新增测试能够在修复前稳定失败、修复后稳定通过。
- [x] `testUiInputMailbox` 通过。
- [ ] 目标整合环境中可加载 IRLite，完成受影响的点击/释放/滚动流程而不崩溃。
- [x] 未引入仅吞异常、留下损坏 UI 状态的容错分支。

## Out of Scope

- 修改、维护或发布 Connector 与 IRLite 的派生版本。
- 将 IRLite 的非 1.21.1 分支移植到 1.21.1。
- 与本问题无关的 BBS UI、渲染或主题重构。
- 在没有证据的情况下改写 Connector 的通用转换框架。

## Open Questions

- 目标组合的精确首个运行时错误；本机与工作区现有日志均不包含 IRLite。

## Decisions

- 2026-07-26：用户确认只修改 FSR `new/`，Connector 与 IRLite 保持原样。
- 2026-07-26：用户授权在规划完成后开始尝试修复。

## Notes

- 这是复杂兼容性任务；进入实现前必须补齐 `design.md`、`implement.md` 和研究记录。
