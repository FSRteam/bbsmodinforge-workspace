# S1 addon 工程骨架与 Mixin 基建

父任务：`.trellis/tasks/07-26-bbs-snow-neoforge-port`

## Goal

建起 `bbs-snow-addon/` 这个 NeoForge 1.21.1 子项目，并**证明 Mixin 从 addon 注入 BBS FSR 的类这条路走得通**。

这是全项目的可行性关卡：仓库现有两个 addon（`bbs-photon-addon`、`bbs-premiere-export`）都不含 Mixin，而 bbs_snow 有 76 个 Mixin、9162 行，占全部代码的 40%。如果 addon 侧的 Mixin 注入走不通，整个"独立 addon"方案就得推翻。**S1 的产出不是"工程能编译"，而是"注入在游戏里真的生效了"。**

本任务不移植任何业务功能。

## Requirements

### R1 工程骨架

- R1.1 新建 `bbs-snow-addon/`，构建插件用 `net.neoforged.gradle.userdev 7.1.21`（与 `bbs-premiere-export` 及主 mod 同构）。
- R1.2 `gradle.properties`：`mod_id=bbs_snow`、`minecraft_version=1.21.1`、`neo_version=21.1.222`、`java_version=21`、`bbs_version=0.0.10`，版本号全部与 `new/gradle.properties` 对齐。
- R1.3 `sourceSets` 划分 `src/main/java` + `src/client/java`，与源插件的 main/client 分离保持一致。
- R1.4 依赖 `../new/build/libs/BBS_FSR_beta_v0.0.10.jar`，并照搬 `bbs-premiere-export` 的 `verifyBbsCoreJar` 任务：jar 缺失、过期（旧于 `new/src` 最新改动）时构建失败并给出可操作的错误信息。
- R1.5 `gradlew` / `gradlew.bat` / `gradle/wrapper/` 齐备，可独立构建。

### R2 mod 元数据

- R2.1 `META-INF/neoforge.mods.toml`：mod id `bbs_snow`，对 `neoforge` / `minecraft` / `bbs` 三个依赖的声明，其中 `bbs` 为 `required` + `ordering=AFTER`。
- R2.2 声明四份 mixin config（`bbs_snow.mixins.json`、`bbs_snow.client.mixins.json`、`bbs_snow.client.api.mixins.json`、`bbs_snow.client.iris.mixins.json` 后者带 `requiredMods=["iris"]`）。
- R2.3 四份 mixin config 建为空壳（`mixins: []`），`compatibilityLevel: JAVA_21`，**不带 `refmap` 字段**。

### R3 加载入口

- R3.1 `@Mod("bbs_snow")` 主类，通过 Mod Bus 监听 `BBSAddonRegisterEvent`，用 `event.register("bbs_snow", ...)` 注册 `BBSAddonMod` 实例。
- R3.2 客户端入口用 `Dist.CLIENT` 隔离。
- R3.3 addon 类实现 `mchorse.bbs_mod.events.BBSAddonMod`，带一个 `@Subscribe` 方法（如 `RegisterSettingsEvent`）打日志，用于验证内部 EventBus 桥接生效。
- R3.4 零 Fabric 依赖：不出现 `net.fabricmc.*` 任何引用。

### R4 Mixin 端到端验证（本任务的核心）

- R4.1 写一个**探针 Mixin**，注入 `mchorse.bbs_mod.ui.framework.UIBaseMenu`（BBS 自有类，非混淆），在某个必经方法上 `@Inject` 打一行可识别的日志。
- R4.2 实际启动 NeoForge 客户端，同时加载 BBS FSR 与本 addon，**在游戏日志中看到探针日志**。
- R4.3 确认 mixin 注解处理器在 NeoGradle userdev 下正常工作，无需 refmap。
- R4.4 验证 `defaultRequire: 1` 生效：故意写一个目标不存在的注入点，确认构建/启动会报错而非静默跳过 —— 证明后续 75 个 Mixin 的失败不会被吞掉。验证完删除该测试用例。

### R5 包与命名

- R5.1 Java 包根为 `mchorse.bbs_snow`（不是 `mchorse.bbs_fslovecml`）。
- R5.2 资源命名空间为 `bbs_snow`。
- R5.3 在 Mixin 中新增到 BBS 类上的成员统一用 `bbs_snow$` 前缀。此约定在本任务确立，写进 `bbs-snow-addon/README.md`。

## Constraints

- **不改 `new/` 一行代码。** 提交前 `git diff --stat new/` 必须为空。
- 构建验证禁止用管道（`| tail`、`| head`）接 `gradlew` —— 管道吞退出码会造成假绿。必须独立回显 `$?` 并复核日志文件。
- 不移植任何业务代码。探针 Mixin 在 S2 开始前删除或改造为真实注入。

## Acceptance Criteria

- [ ] AC1 `cd bbs-snow-addon && ./gradlew build`，独立确认 exit code 为 0
- [ ] AC2 产出 jar 含 `META-INF/neoforge.mods.toml` 与四份 mixin config
- [ ] AC3 `./gradlew runClient` 启动成功，BBS FSR 与 bbs_snow 同时加载
- [ ] AC4 **探针 Mixin 的日志实际出现在游戏日志中**（贴出日志行作为证据）
- [ ] AC5 `BBSAddonMod` 的 `@Subscribe` 方法收到事件（日志为证）
- [ ] AC6 R4.4 的负向验证通过：注入点写错时确实报错
- [ ] AC7 `git diff --stat new/` 为空
- [ ] AC8 全项目搜索无 `net.fabricmc` 引用

## 失败处置

AC4 不通过（addon 侧 Mixin 无法注入 BBS 类）时，**立即停止 S1 并回到父任务**，不要自行改方案往下推。把失败现象与已尝试的配置整理出来，由用户在两条退路间决定：

1. 换构建插件（ModDevGradle）重试；
2. 放弃独立 addon 形态，合入 `new/` 主 mod（需用户批准放开 AC7 约束）。

## Notes

- 参考模板：`bbs-premiere-export/build.gradle`（构建 + `verifyBbsCoreJar`）、`new/src/*/resources/bbs*.mixins.json`（mixin config 写法）、`new/src/main/java/mchorse/bbs_mod/addon/demo/BBSAddonDemoMod.java`（addon 注册姿势）。
- 前置：`cd new && ./gradlew jar` 产出 `BBS_FSR_beta_v0.0.10.jar`。
