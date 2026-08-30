# 设计约定：多平台源码分层与 mixin 薄壳模式

本文件是各功能域重构子代理与后续平台移植共同遵守的结构契约。

## 分层与依赖规则

| 层 | 物理路径 | 允许依赖 | 说明 |
|---|---|---|---|
| core | `core/src/main/java`（独立 Gradle 模块，带 JUnit5） | JDK only | 无 BBS/MC 依赖的纯逻辑；唯一能写常规单测的层 |
| shared | `shared/src/main/java`、`shared/src/client/java`（纯源码目录，非模块） | JDK、`mchorse.bbs_mod.*`、`mchorse.bbs_fslovecml.*`、org.joml、org.lwjgl | 依赖 BBS 类型但平台无关的业务层；**禁止** `net.minecraft.*` / `com.mojang.*` / `org.spongepowered.*` |
| platform | `fabric-1.20.1/`、`neoforge-1.20.1/`、`neoforge-1.21.1/` | 全部 | mixin、加载器入口、MC 直接触点 |

平台模块通过 `sourceSets { main { java { srcDir rootProject.file('core/src/main/java') ; srcDir rootProject.file('shared/src/main/java') } } }` 引入共享源码，
各平台按自身 Java 版本与自身 BBS 宿主 jar 编译同一份源码——**API 漂移在编译期暴露**，这是选源码共享而非二进制共享的原因。

### Loom 顺序约束（已踩坑）

fabric 模块中 `sourceSets { client { ... } }` 必须写在 `loom { splitEnvironmentSourceSets() }` **之后**，
否则 loom 报 `Cannot add a SourceSet with name 'client'`（在 loom 创建 client 源集前配置它等于重复创建）。

## mixin 薄壳模式

mixin 只做三件事，方法体控制在 1-3 行：

1. `@Shadow` 私有成员 → 以 `Supplier`/函数式接口传给 support 类
2. `@Unique` 持有一个 support 实例（惰性创建，构造期不产生副作用，保持原有"未初始化"判定时序）
3. `@Inject` 回调与 duck 接口方法 → 一行委托 support

业务逻辑、UI 构建、状态机全部落在 shared 层 `*Support` 类；宿主之间的差异用小接口（如 `DockHostAdapter`）参数化，
避免为每个宿主复制一份实现。参考实现：`shared/src/client/java/mchorse/bbs_fslovecml/ui/miniwindow/MiniWindowDockHostSupport.java`
与瘦身后的 `fabric-1.20.1/src/client/java/mchorse/bbs_fslovecml/mixin/client/UIDockLayoutMiniWindowMixin.java`。

### 重构期红线

- 不改 mixin 类名/包名（`*.mixins.json` 注册项保持不变）
- 不改 `@Inject` 的 `method` / `at` 签名（注入点等价性是回归安全的基础）
- 不删 duck 接口（跨 mixin 通信协议）
- 行为等价优先：除任务书明确许可的修复外，重构不得引入新分支或改变时序

## 异常处理规范

- 统一走 SLF4J：main 侧 `BBSFSloveCML.LOGGER`，client 侧 `BBSFSloveCMLClient.LOGGER`
- 禁止 `printStackTrace`、禁止空 `catch`
- 确属预期忽略的捕获，保持捕获范围不变，补中文注释说明为何可忽略，并降级为 `LOGGER.debug`

## FSR 平台差异备忘

- FSR 1.20.1（NeoForge 47.x）：Java 17、Mojmap、SRG 运行时；`mods.toml` 依赖键为 `forge`
- FSR 1.21.1（NeoForge 21.1）：Java 21、Mojmap
- 两版 BBS 内部类包路径与 Fabric 版一致（`mchorse.bbs_mod.*`），BBS UI 框架自封装（`UIElement.render(UIContext)`），
  故 shared 层与多数 mixin 逻辑可跨平台；MC 类型触点（`MatrixStack`→`PoseStack` 等）集中在少数渲染/输入类
- 详细核对结果见 `research/fsr-migration-compat.md`
