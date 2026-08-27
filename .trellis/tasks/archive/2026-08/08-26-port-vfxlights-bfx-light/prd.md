# 移植 VFXLIGHTS BFX Light 到 BBSFSR

## Goal

在仓库根目录新增 `bfx-light/` 插件工程，把用户提供的上游 VFXLIGHTS 源码完整保留在 `bfx-light/TEMP/VFXLIGHTS/`，并创建面向最新 `new-fs25-sync/`（BBSFSR 1.21.1 NeoForge）的可构建兼容层。

## Confirmed facts

- 上游快照为 `xavineditor/VFXLIGHTS` 的 `master`，提交 `ceea96fd3f2026b0363613535a3433cd989471fb`。
- 上游目标是 Fabric/Yarn Minecraft 1.20.1/1.20.4，不能直接作为 BBSFSR 1.21.1 NeoForge 模组加载。
- BBSFSR 的外部扩展契约要求独立 NeoForge 工程、在构造阶段注册 `BBSAddonMod`，并使用从最新 `new-fs25-sync` 复制并校验的 `bfx-light/TEMP/BBS_FSR_beta_v0.0.11-fs25-latest.jar` 作为 `compileOnly` 依赖。
- 本任务不修改 `new/` 主工程；兼容代码放在独立的 `bfx-light/` 工程。

## Requirements

- [x] 保留上游源码快照和提交信息在 `bfx-light/TEMP/VFXLIGHTS/`，不覆盖用户已有快照。
- [x] 创建 NeoForge 21.1.222 / Minecraft 1.21.1 / Java 21 的 `bfx-light` 工程，并以 `new-fs25-sync` 为 FSR 基线。
- [x] 迁移 BFX Light 的核心数据模型：点光、聚光、区域光、环境光表单、分组关键帧值和逐帧光源注册 API。
- [x] 通过 BBSFSR 外部 Addon API 注册表单和资源，不复制或打包 `mchorse/bbs_mod/**`。
- [x] 对上游 Fabric/Iris 1.20.x 的渲染 Mixin 记录兼容边界；只有能在当前 BBSFSR API 上验证的部分才启用。
- [x] 提供构建/验证任务，明确 BBSFSR JAR 缺失或 API 条目缺失时失败；依赖快照的来源和 SHA-256 可追溯。

## Acceptance Criteria

- [x] `bfx-light/TEMP/VFXLIGHTS/.git` 可读，HEAD 等于上游快照提交。
- [x] `bfx-light` 的 `compileJava`、`jar` 和 `build` 成功，产物位于 `bfx-light/build/libs/`。
- [x] 产物包含 `META-INF/neoforge.mods.toml` 和 BFX Light 自有类/资源，不包含 `mchorse/bbs_mod/**` 或 `fabric.mod.json`。
- [x] `bfx-light` 的源码中不再依赖 `net.fabricmc`；上游 Fabric/Iris 渲染路径以迁移记录说明为准，不得伪装成已完成。
- [x] 构建日志和最终汇报说明：已兼容的功能、暂未启用的功能、验证命令和风险。

## 结果记录

- 最新 FSR 基线为 `new-fs25-sync`，其 JAR 已固定复制到 `bfx-light/TEMP/BBS_FSR_beta_v0.0.11-fs25-latest.jar`。
- Fabric 官方映射迁移文档：<https://docs.fabricmc.net/zh_cn/develop/migrating-mappings/>；本次按“工具迁移后人工复核，尤其是 Mixin”原则执行。

## Out of scope

- 不把上游 Fabric 1.20.x 的全部渲染管线强行改写成 1.21.1 NeoForge；这需要逐个校验 Minecraft、BBSFSR、Iris 的运行时注入点。
- 不修改 `new/` 主工程或其现有并发改动。
