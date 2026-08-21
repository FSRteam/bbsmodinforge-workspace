# 更新 bbs-irlights-addon 上游移植并修复 iterationRP 光束强度选项

## Goal

在现有 `bbs-irlights-addon` NeoForge 1.21.1 / BBSFSR 移植基础上，同步
`quaIett/bbs-irlights-addon` 的 `port/1.21.1` 分支内容，补齐当前端口缺失的上游能力，同时保持
BBSFSR 的 API、Mojmap、Iris/NeoForge Mixin 和本地 IRL Core 集成不回退。

用户重点需要 iterationRP 最新补丁中的设置项；其中 `IRLITE_VL_INTENSITY` 必须能在 Iris 的
shader settings 中实际显示并可调节。

## Confirmed Facts

- 上游 `port/1.21.1` 当前提交为 `c76a73f0e209da12505d3e9bff56d8e5155f47e4`，与本地 README 记录一致。
- 上游仓库是 Fabric 工程；当前工作区已经把 BBS 依赖迁移为 NeoForge + BBSFSR，并额外并入了 IRL Core 的客户端源码。
- 本地 iterationRP 补丁保留了 FSR 端口需要的补丁器差异：不再由该补丁声明 host 的 `SSBO` feature，并兼容两种 IterationRP voxel slider 锚点。
- 上游 iterationRP 补丁声明了 `IRLITE_VL_INTENSITY` 及其说明文字，但没有数值允许值注释，且 `screen.IRLIGHTS` 和 `sliders=` 注册列表没有包含它，因此 Iris 不会显示该选项。

## Requirements

1. 以远端 `port/1.21.1` 为事实来源，逐文件核对本地端口；同步适用于当前 NeoForge/BBSFSR 工程的源码、资源、补丁文本和文档内容。
2. 不把 Fabric Loader、Fabric API、Loom、`fabric.mod.json`、Yarn 名称或上游 BBS/Fabric Mixin 直接带回目标工程；保留现有 NeoForge 元数据、BBSFSR 依赖检查、Mojmap 适配和 IRL Core 源码。
3. 不覆盖本地已有的 FSR 专用修复，除非能证明上游版本已包含同等修复且不会破坏 BBSFSR；每个保留的本地差异要能在设计或变更记录中说明。
4. 更新 iterationRP 补丁时保留上游最新 GLSL/补丁内容，同时让 `IRLITE_VL_INTENSITY`：
   - 出现在 IRLights 设置屏幕的实际选项树中；
   - 出现在 `sliders=` 声明中，以数值滑杆显示而不是隐藏/布尔项；
   - 继续使用现有 `IRLITE_VL_INTENSITY` 注释值和运行时 UBO 回退语义。
5. 变更只针对 `bbs-irlights-addon` 和本任务记录，不修改 `new/` 的 BBSFSR 源码或其他插件。

## Acceptance Criteria

- [x] 上游与本地的适用文件完成比对，结果中明确列出同步内容、保留的 FSR 差异和排除的 Fabric 文件。
- [x] `bbs-irlights-addon` 能通过 `verifyExternalMods`、`compileClientJava` 和 `build`，且构建产物不包含 `mchorse/bbs_mod/**` 或 `fabric.mod.json`。
- [x] 源码中无 `net.fabricmc` / Yarn 依赖残留；现有 NeoForge Mixin 配置和 BBSFSR 版本约束仍然有效。
- [x] iterationRP 补丁包含 `IRLITE_VL_INTENSITY` 于 `screen.IRLIGHTS` 与 `sliders=` 的注册；静态检查能区分“声明了但未注册”的回归。
- [x] iterationRP 的 `IRLITE_VL_INTENSITY` 定义包含 Iris 可解析的允许值列表，且与其他 IRLights 补丁保持一致。
- [x] iterationRP 补丁仍包含本地 FSR 需要的两种 voxel slider 锚点兼容和 host SSBO ownership 约束。
- [x] 相关资源、补丁解析和打包检查通过；完整客户端人工视觉验证未在本次自动检查中执行，交付时明确说明该缺口。

## Out Of Scope

- 不升级 Minecraft、NeoForge、BBSFSR、Iris 或 Sodium 版本。
- 不重写 IRL Core 的阴影、SSBO、体积光算法，不把独立 Fabric 版本重新移植一遍。
- 不处理 iterationRP 以外的 shaderpack 行为，除非上游同步证明其资源在当前端口缺失且能独立验证。

## Open Questions

- 无阻塞性产品问题。默认按“同步全部可兼容上游内容，并保留本地 FSR 专用差异”的方案执行。
