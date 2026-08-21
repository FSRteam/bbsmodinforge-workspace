# BBSVFX 迁移到 NeoForge 1.21.1 与 FSR API 2.0

## Goal

将官方 `xavineditor/BBSVFX` 从 Fabric 1.20.1/1.21.1 代码基线迁移为可在
Minecraft 1.21.1 + NeoForge 21.1.222 + BBSFSR 0.0.11 上构建、启动和验证的独立 addon。
迁移必须使用 BBSFSR Addon/API 2.0 作为正式 BBS 集成入口，并保留 BBSVFX 现有的
forms、camera clips、smear/blend、destruction、volume/depth、shader 和导出能力。

实现只使用独立的 `bbs-vfx-neoforge-1.21.1-codex-ghproxy` 快照，不依赖其他执行者的
工作目录或未提交代码。

## Requirements

- 目标平台固定为 Minecraft 1.21.1、NeoForge 21.1.222、Java 21、BBSFSR 0.0.11。
- 使用 `BBSApi.registerAddon(BBSAddonDescriptor, Supplier)` 注册 addon，descriptor 精确声明
  API `2.0`、`bbs` 依赖和实际 capabilities；不得把旧 API 1.x 作为最终主路径。
- 使用 API 2.0 facade 注册 forms、settings、camera/action clips、source packs 和 BBS 事件；
  注册必须发生在 NeoForge mod 构造阶段。
- 使用 `BBSClientApi` 处理 BBS 提供的客户端生命周期和世界渲染 hook；使用 NeoForge
  `RegisterShadersEvent` 及 1.21.1 Mojmap 渲染类型处理 Shader 注册。
- 按 Fabric 映射迁移文档，将 Yarn 名称迁移到 Mojmap；拆分的 `src/main/java` 和
  `src/client/java` 必须分别检查，Mixin 描述符必须人工复核。
- 删除 Fabric loader、Fabric API、Fabric entrypoint 和 Fabric-only 元数据依赖；不得使用
  Connector 作为迁移替代方案。
- 仅在 API 2.0 没有公开扩展点时保留 Mixin，例如 BBS 内部 renderer/UI/keyframe factory、
  原版渲染状态和可选 Physics Mod 集成；可选集成不得让主 client mixin 配置在模组缺失时失败。
- 保持 common/client classloading 隔离，专用服务端不能加载客户端类、GLSL 或 Minecraft client API。
- 保留 BBSVFX 资源包覆盖语义，打包产物不得嵌入 `mchorse/bbs_mod/**`，也不得包含
  `fabric.mod.json`。
- 迁移过程使用 gh-proxy 获取的官方快照；不修改 BBSFSR 核心源码或 `mod_version=0.0.11`。

## Acceptance Criteria

- [ ] Common 和 client 源码在 Java 21 下分别通过 NeoForge 目标编译。
- [ ] 生成的 addon JAR 含有效 `META-INF/neoforge.mods.toml`、Mixin 配置和资源，且不含
      `fabric.mod.json`、Fabric loader/API 引用或嵌入的 BBS core 类。
- [ ] BBSFSR 启动日志显示 API 2.0 addon 被接受并进入 READY；无未处理注册失败或重复 source pack。
- [ ] 六种 BBSVFX form、follow-curve/impact clip、设置模块和 source pack 能在客户端注册。
- [ ] Shader 注册、world render hook、depth/OIT/framebuffer pass 在无 Iris 和有 Iris/Sodium
      的环境下都不触发启动崩溃，并完成至少一次手工渲染 smoke test。
- [ ] 所有 required Mixin 在真实 NeoForge client 启动中成功应用；不存在仅因编译通过而遗漏的注入。
- [ ] destruction/PhysX、smear/blend/label、camera/export 关键路径完成针对性验证；可选 Physics
      Mod 缺失时主模组仍可启动。
- [ ] `git diff --check`、目标 Gradle 编译/测试和 release JAR 内容检查通过，验证结果记录在任务中。

## Out of Scope

- 不迁移或重构 BBSFSR 核心实现。
- 不恢复 Fabric 兼容运行时，不引入 Connector。
- 不改变 BBSFSR 版本、Minecraft 版本或发布命名。
- 不把低层 Mixin 强行改造成没有对应能力的 API 2.0 facade。

## Confirmed Evidence

- 官方快照 SHA-256：`ED3022E9E3590C72FF5CA2FD5A30F23234A369B8117ECC1BB024AED9D52402B7`。
- 快照规模：174 个 Java、55 个 Mixin、18 组 vertex/fragment shader。
- Fabric 1.21.1 `compileJava` 成功；`compileClientJava` 基线有 6 个上游 API 错误，需与 NeoForge
  迁移错误分开处理。
- BBSFSR 源码提供 Addon/API 2.0、`BBSClientApi`、注册 facade 和诊断 API。
