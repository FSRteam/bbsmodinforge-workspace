# IRL 灯光插件迁移至 NeoForge 1.21.1

## Goal

将 `quaIett/bbs-irlights-addon` 的 `port/1.21.1` 分支中“依赖 BBS”的 IRL 插件迁移到本仓库的 BBSFSR（`new`）环境，使它能够作为原生 NeoForge 1.21.1 插件构建和运行，而不是依赖 Fabric Connector。

用户价值：在 BBSFSR 中继续使用 IRL 的灯光编辑、录制与回放能力，并避免 Connector/Mixin 转换带来的兼容性和启动风险。

## Confirmed Facts

- 目标游戏版本为 Minecraft 1.21.1，加载器为 NeoForge。
- 目标 BBS 实现是本仓库 `new` 目录中的 BBSFSR。
- 上游源码地址为 `https://github.com/quaIett/bbs-irlights-addon/tree/port/1.21.1`。
- 上游源码同时包含依赖 BBS 与不依赖 BBS 的实现；本任务只迁移依赖 BBS 的版本。
- 仓库已有 `temp/bbs-irlights-addon-1.21.1`，它主要承载此前 IRLite + Connector 的兼容修复，不能直接视为本任务完成品。
- 所有外部克隆和参考源码必须位于仓库根目录 `temp`；零散调研资料放入 `docs` 或任务的 `research` 目录。
- 当前主工作树存在与本任务无关的未提交改动，必须保留且不得混入本任务提交。
- 指定上游已克隆到 `temp/bbs-irlights-addon-port-1.21.1-upstream`，基线为 `port/1.21.1@c76a73f0e209da12505d3e9bff56d8e5155f47e4`。
- 同版本 `irl-core` 已克隆到 `temp/irl-core-upstream`，基线为 `1.21.1@3624676e80833ac8f6c8beb961adea339d261ceb`。
- 两个基线仓库均通过 `git fsck`，且本地分支、远端分支和工作区状态一致。
- 上游仓库根 `LICENSE` 与 `irl-core/LICENSE` 均为 MIT；`fabric.mod.json` 中旧的 `CC-BY-ND-4.0` 字段与仓库许可证不一致，迁移元数据以仓库 MIT 许可证文件为准。
- BBSFSR 目标版本为 `0.0.11`，NeoForge 为 `21.1.222`，Java 为 21；目标 Iris/Sodium 分别为 `1.8.8+1.21.1-neoforge` 和 `mc1.21.1-0.6.13-neoforge`。

## Requirements

- 以用户指定分支的现有代码为基线，保留 IRL 原有功能和资源，不重写无关逻辑。
- 把 Fabric/旧 BBS API、事件、注册、网络、渲染和 Mixin 接口适配到 NeoForge 1.21.1 与 BBSFSR 当前 API。
- 将 BBS 前置依赖声明指向 BBSFSR 实际模组 ID 和兼容版本，缺少前置时给出正常依赖错误而非运行期崩溃。
- 使用本仓库已有 NeoForge 插件的构建和目录惯例；不引入本任务不需要的新框架或通用抽象。
- 保持客户端专用代码与服务端/通用初始化边界正确，避免专用服务器类加载崩溃。
- 对关键灯光数据、编辑器集成、录制/回放和渲染入口提供与风险相称的自动或可重复验证。
- 迁移产物作为本仓库的新内容单独管理；只有用户确认后才执行本地 Git commit，且提交只包含本任务新增或修改的内容。

## Acceptance Criteria

- [ ] 依赖 BBS 的 IRL 版本在 Java 21 下完成干净构建，并产出 NeoForge 1.21.1 JAR。
- [ ] 构建不依赖 Fabric Loader 或 Sinytra Connector 作为运行前置。
- [ ] 模组元数据正确声明 Minecraft、NeoForge 和 BBSFSR 依赖，模组能够被 NeoForge 发现并加载。
- [ ] IRL 的灯光类型/数据能够在 BBSFSR 中注册，并能保存、加载和网络同步。
- [ ] BBSFSR 编辑器中的相关灯光创建、编辑、录制/关键帧回放入口可用，没有明显报错或缺失资源。
- [ ] 客户端启动验证通过；如环境允许，专用服务器启动或等价类加载检查通过。
- [ ] 现有 BBSFSR 相关测试/构建未因迁移产生回归。
- [ ] 最终汇报列出已验证功能、未能自动验证的人工项目、产物路径和已知风险。

## Out of Scope

- 不修改或继续扩展此前 IRLite + Connector 的 fix8 功能，除非其中代码可直接复用于本次原生迁移。
- 不迁移上游“不依赖 BBS”的独立版本。
- 不对 BBSFSR 或 IRL 做与兼容性无关的界面重做、性能重构或新功能扩展。

## Planning Decisions

- 新工程放在仓库根目录 `bbs-irlights-addon`，与 `bbs-photon-addon`、`bbs-snow-addon` 的外部插件布局一致；`temp` 中的上游克隆保持只读参考。
- 模组 ID、资源命名空间和 Form ID 继续使用上游的 `irlite`，避免已有影片、表单数据、补丁资源和配置失配。
- 迁移版本使用 `1.1.4-fsr`，保留上游 `1.1.4` 血缘，同时明确这是 BBSFSR/NeoForge 变体。
- `irl-core` 不再作为 Fabric/Loom 的嵌套 JAR或本地 Maven 依赖，而是把固定提交的源码和资源编入同一个 NeoForge JAR。
- Iris 是客户端功能前置，元数据声明客户端必需版本；Sodium 使用与 Iris/BBSFSR 相同的运行版本，不复制进插件 JAR。
- 构建、JAR 内容、Mixin 应用、注册表和客户端启动可以自动或半自动验证；灯光画面、阴影质量、聚光灯拖拽、关键帧动画、七种 shaderpack 补丁结果需要在完整游戏实例中人工验收。
