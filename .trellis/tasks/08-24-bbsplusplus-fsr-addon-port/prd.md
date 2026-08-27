# 将 BBS++ 2.6.3 完整迁移为 FSR NeoForge addon

## Goal

将上游 `Gbeic/BBS-PlusPlus` 固定提交 `9e2235ba3f4ddf19501815df452bbc15aed2c865`（版本 2.6.3）完整迁移为一个独立的 NeoForge addon mod，使其在当前 BBS FSR 环境中提供与上游等价的功能。

用户价值：FSR 用户可以继续使用 BBS++ 的编辑器增强、渲染/伪装扩展、关键帧辅助和可选模组兼容能力，而不需要运行 Fabric 或 Connector。

## Confirmed facts

- 上游基线是 Minecraft 1.20.1、Fabric Loader 0.16.14、Yarn `1.20.1+build.10`、Java 17、BBS++ 2.6.3。
- FSR 目标工程是 `new/`：Minecraft 1.21.1、NeoForge 21.1.222、Java 21，当前发布 JAR 为本地依赖。
- 上游使用 Fabric entrypoint、Fabric API 和多个 Mixin 配置；不能通过只运行映射迁移任务完成平台迁移。
- Fabric 官方迁移说明明确：`migrateMappings` 只做映射辅助，Mixin 等结果必须人工复核。
- 当前 FSR 已有独立 NeoForge addon 接入约定：外部 addon 在 mod 构造期注册 BBS addon，优先使用公开 BBS/FSR API；只有没有公开扩展点时才保留必要 Mixin。
- 当前工作区有未提交改动；本任务必须使用独立 addon 目录和独立构建产物，不能覆盖或回退这些改动。

## Requirements

### Scope

- 交付形式固定为独立 NeoForge addon JAR，不把实现合入 FSR 核心，也不做 FSR 热插件。
- 目标平台固定为 Minecraft 1.21.1、NeoForge 21.1.222、Java 21、当前 FSR `new/`。
- 迁移范围是上游 2.6.3 的完整功能等价版本，包括基础增强、编辑器增强、物品喷射、AAA 粒子、结构伪装、视频伪装、光影曲线、UV/图片伪装、修复项和可选兼容功能。
- Iris、Sodium、IRLights、VFX、VFX Lights、AAA Particles、MediaPlayer、Together/Xavin 等可选依赖缺失时，主 addon 必须仍可启动，并只禁用相关能力。
- 保留 LGPL-3.0-or-later 许可证、版权和上游作者署名；迁移产生的修改必须保留清晰的来源与变更说明。

### Compatibility and architecture

- 删除 Fabric Loader、Fabric API、Fabric entrypoint 和 Fabric-only 元数据依赖；不得使用 Connector 作为最终运行时方案。
- 先用 Fabric Loom `migrateMappings` 辅助 Yarn → Mojang 映射，再以 NeoForge 1.21.1/FSR 的实际 API 和类签名人工修复。
- Common 与 client 源码必须隔离，dedicated server 不得加载客户端类、渲染类或客户端可选依赖。
- 注册、设置、关键帧、Form、UI、资源包、语言和生命周期优先接入当前 FSR/BBS addon API。
- 必要的结构性 Mixin 必须按功能和可选依赖拆分配置；Mixin 注入目标、描述符和 `require` 语义必须在真实客户端启动中验证。

### Delivery constraints

- 上游源码、临时映射工程、NeoForge addon 工程和 FSR 本地 JAR 依赖必须分开管理。
- 不修改 FSR `new/` 核心源代码、版本号或核心发布配置，除非后续单独获得授权。
- 不承诺兼容上游基线之后的新提交；任何更新另行评估。

## Acceptance Criteria

- [ ] 独立 addon 工程可在 Java 21/NeoForge 21.1.222 下编译 common 和 client 源集。
- [ ] 发布 JAR 含有效 `META-INF/neoforge.mods.toml`、必要 Mixin 配置、资源和许可证；不含 `fabric.mod.json`、Fabric Loader/API 运行时依赖或嵌入的 BBS 核心类。
- [ ] FSR 客户端启动时 addon 被接受并完成注册；无未处理注册失败、重复 source pack、客户端类加载错误或 MixinApplyError。
- [ ] 上游功能清单中的每个功能都有“已迁移、以 FSR API 替代、或因目标平台限制记录为不等价”的明确状态；完整等价目标下不得无记录地删功能。
- [ ] 设置和语言内容可保存/加载；关键帧轨道、姿势骨骼树、骨骼参数刷、跳过骨骼、剪贴板、时间线和影片库等编辑器功能可操作。
- [ ] 物品喷射、AAA 粒子、结构伪装、视频伪装、光影曲线、UV/图片伪装及其可选依赖兼容完成针对性验证。
- [ ] 无可选依赖、逐个安装可选依赖、Iris/Sodium 组合环境下，客户端都能启动并按预期启用/禁用功能。
- [ ] dedicated server 能启动且不加载客户端类或渲染资源。
- [ ] 通过目标 Gradle 编译/检查、`git diff --check`、发布 JAR 内容检查，并完成真实客户端启动和关键功能手动 smoke test；结果记录在任务文档中。

## Out of scope

- 不迁移上游 2.6.3 之后的新功能或提交。
- 不把 BBS++ 改造为 FSR 热插件。
- 不重构 BBS FSR 核心实现，不在本任务内改变 FSR 的公共 API。
- 不以 Connector 兼容层代替原生 NeoForge 迁移。

## Open decisions

- 具体模块拆分、子任务顺序和每个 Mixin 的保留/替代结论在 `design.md` 和 `implement.md` 中确定；实现前须复核上游快照和当前 FSR JAR/API。
