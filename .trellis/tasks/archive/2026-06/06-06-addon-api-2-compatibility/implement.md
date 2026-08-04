# Addon/API 2.0 实施计划

## 前置 Review Gate

实施前必须由用户确认：

- 采用 `兼容优先 + 明确警告 + 逐步迁移` 作为默认兼容政策。
- 本阶段只修改 `new`。
- 本阶段 commit 只包含 `new` 下的内容；`.trellis`、根 `docs`、根 `temp` 不进入 commit。

确认后再运行 Trellis `task.py start`，把任务状态从 `planning` 切到 `in_progress`。

## 实施顺序

### 1. 建立 API 2.0 骨架

- 在 `new/src/main/java/mchorse/bbs_mod/api` 下新增公共入口和基础类型。
- 定义 `BBSApiVersion`、`BBSAddonDescriptor`、`BBSAddon`、`BBSAddonContext`。
- 定义 `BBSRegistrationResult`、`BBSAddonProtocol`、`BBSAddonState`、`BBSAddonCapability`。
- 增加 descriptor 校验器，覆盖 id、版本、side、namespace、dependency。
- 保持所有类型尽量 immutable，避免向 addon 暴露内部集合。

### 2. 新增 Addon 管理器与诊断索引

- 在内部包新增 `BBSAddonManager` 或等价 manager。
- manager 持有 v2 addons、v1 legacy addons、diagnostics snapshot。
- 将现有 `BBSAddonCollector` 作为 v1 collector 保留。
- 新增 `LegacyAddonAdapter`，把 `BBSAddonMod` 纳入 v2 diagnostics，但不改变其现有 `EventBus` 行为。
- 扩展 `BBSAddonProtocolSelfCheck`，验证 v2/v1 coexistence。

### 3. 接入 BBSMod 生命周期

- 在 `BBSMod` 构造和 `onConstructMod` 阶段初始化 manager。
- 保留当前 `BBSAddonRegisterEvent`，并新增或扩展 v2 显式注册入口。
- 保留 `BBSMod.registerAddon(String, Supplier<? extends BBSAddonMod>)` 作为 v1/current 入口。
- 新增 v2 注册入口，例如 `BBSMod.registerAddon(BBSAddonDescriptor, Supplier<? extends BBSAddon>)` 或 `BBSApi.addons().register(...)`。
- 在 `onCommonSetup` 中先完成 v1 bridge，再执行 v2 common registration adapter，确保当前 startup events 不丢。
- 所有 addon callback 通过 execution boundary 调用。

### 4. 实现 common registry facades

- `BBSResourceRegistry`：适配当前 `AssetProvider` 与 `RegisterSourcePacksEvent`。
- `BBSFormRegistry`：适配 `FormArchitect`。
- `BBSSettingsRegistry`：适配 `BBSMod.setupConfig(...)`。
- `BBSClipRegistry`：适配 `factoryCameraClips` 与 `factoryActionClips`。
- `BBSParticleRegistry`：先开放 component/factory 注册能力。
- 每个 facade 都返回 `BBSRegistrationResult`，并把 reject/duplicate 写入 diagnostics。

### 5. 实现 client registry facades

- 在 `new/src/client/java/mchorse/bbs_mod/api/client` 下新增 client-only API。
- 适配 `RegisterL10nEvent`、`RegisterClientSettingsEvent`、`RegisterDashboardPanelsEvent`。
- 适配 `ClientApiCompat` 的 render/tick/hud/renderer registration 能力，保持 compat 不删除。
- 验证 dedicated server 编译路径不会加载 client-only 类。

### 6. 实现 network facade

- 保持 `NetworkCompat` frozen payload 不变。
- 新增 v2 network registry，记录 addon-scoped payload definitions。
- 通过 NeoForge `RegisterPayloadHandlersEvent` 注册 typed payload。
- 保留 raw-buffer legacy bridge，但文档标注 deprecated/compat。
- 增加 diagnostics：payload id、direction、version、codec registration result。

### 7. 文档与 demo

- 新增 `new/docs/addon-api-2.md`，内容包括：
  - v2 快速开始；
  - descriptor 示例；
  - common/client/network 注册示例；
  - v1 迁移表；
  - 兼容 warning 解释。
- 更新或新增 demo addon：
  - v2 注册 source pack；
  - v2 注册 settings；
  - v2 注册 form；
  - v2 client hook；
  - v1 demo 保留，证明兼容路径仍工作。

### 8. 质量检查与收尾

- 检查 `new` 编译。
- 检查 `new` Git diff 只包含预期文件。
- 检查未跟踪 `.claude/`、`runs/` 不进入 staged。
- 检查 diagnostics 日志不刷屏，重复 warning 只出现一次。
- 通过 Trellis check 后，再等待用户确认 commit。

## 验证命令

从工作区根目录执行：

```powershell
.\new\gradlew.bat -p new compileJava
.\new\gradlew.bat -p new compileClientJava
git -C new status --short
git -C new diff --stat
```

如果项目没有独立 `compileClientJava` 任务，则改用：

```powershell
.\new\gradlew.bat -p new build
```

需要网络/NeoForge 运行验证时，再按现有 run config 执行 client/server dev run；不把 `new/runs` 加入 commit。

## 风险文件

- `new/src/main/java/mchorse/bbs_mod/BBSMod.java`：生命周期接入点，最容易破坏启动顺序。
- `new/src/main/java/mchorse/bbs_mod/addon/BBSAddonCollector.java`：v1 兼容协议，不能破坏 first-wins 和 closed-window 行为。
- `new/src/main/java/mchorse/bbs_mod/loader/NeoForgeLoaderAccess.java`：`bbs-addon` lookup 必须保持当前 v1 语义。
- `new/src/main/java/mchorse/bbs_mod/events/EventBus.java`：如需改动，必须保持 handler 异常隔离。
- `new/src/main/java/mchorse/bbs_mod/network/compat/NetworkCompat.java`：frozen payload 列表不能因 v2 改动而变化。
- `new/src/client/java/mchorse/bbs_mod/client/compat/ClientApiCompat.java`：只能扩展或桥接，不删除 current compat。

## 回滚点

- 完成 API 类型骨架后单独检查；如失败，只回滚 `api` 包。
- 完成 manager 接入后检查 v1 demo；如失败，关闭 v2 manager 接入并保留 v1 collector。
- 完成每个 registry facade 后分别检查；单个 facade 可独立回滚。
- network facade 独立回滚，不能回滚或改写 `NetworkCompat` frozen payload。
- 文档和 demo 可最后独立回滚。

## Commit 策略

只有用户明确确认 commit 后才执行本地 commit。

commit 前要求：

- `git -C new status --short` 中 staged 只包含 `new` 内预期文件。
- 不 stage `.claude/`、`runs/`。
- 不 stage 根 `.trellis`、根 `docs`、根 `temp`。
- commit message 建议：`feat(addon): add API 2 compatibility layer`。

## Review Checklist

- [ ] API 2.0 public 包没有暴露内部 mutable manager。
- [ ] v1 addon demo 仍能通过 `BBSAddonRegisterEvent` / `BBSMod.registerAddon(...)` 工作。
- [ ] duplicate addon id 仍是 first-wins + reject-later。
- [ ] late registration 仍有 warning，不崩溃核心。
- [ ] v2 addon lifecycle callback 异常被隔离。
- [ ] common/client 包边界清晰，server 不加载 client-only API。
- [ ] NetworkCompat frozen payload 未变化。
- [ ] 文档包含 v2 示例和 v1 迁移表。
- [ ] Git commit 范围只包含 `new`。
