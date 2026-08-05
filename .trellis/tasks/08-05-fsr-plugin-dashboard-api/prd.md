# FSR 插件自定义 Dashboard 面板 API

## Goal

让 NeoForge Addon API 2.0 插件与 `config/bbs/plugins/` 热插件都能通过公开 API
向 FSR Dashboard 注册完整的自定义面板和 UI 内容，同时由 FSR 统一负责面板标识、
标题、图标、打开/关闭、选择切换、诊断和热卸载清理。

## Confirmed Facts

- `RegisterDashboardPanelsEvent` 已提供内部扩展入口，但直接暴露 `UIDashboard`，没有
  owner、重复 id、诊断或卸载合同。
- Addon API 2.0 已声明 `CLIENT_UI`，`BBSClientApi` 尚无 Dashboard 面板注册方法。
- 热插件 SPI 当前为独立的 `1.0` 协议，`BBSPluginClientContext` 尚无面板 facade。
- Dashboard 对象会跨多次打开复用；面板的 `open/close` 与 `appear/disappear` 语义
  必须保留。
- 热插件结构注册已具备 staged commit、generation owner、反向 ledger 清理与客户端
  safepoint，可复用这些机制管理面板贡献。

## Requirements

1. 新增 client-only 公共 API，插件以“面板描述 + 内容工厂”注册面板；无需继承
   FSR 内部 `UIDashboardPanel`。
2. 面板描述至少包含 owner 内唯一 id、标题 `IKey` 和图标 `Icon`；最终 id 由宿主
   组合为 `<owner>:<local-id>`，禁止插件冒充其他 owner。
3. 内容对象能提供一个 `UIElement` 根节点，并收到 screen open/close、panel
   appear/disappear、update 生命周期回调。
4. Addon API 2.0 通过 `BBSClientApi.registerDashboardPanel(...)` 注册；必须声明
   `CLIENT_UI`，重复 id first-wins，结果写入 API 2.0 client diagnostics。
5. Addon API 版本保持精确 `2.0`；既有 public JVM descriptor、枚举 ordinal 与
   frozen payload id 不变，只增加新类型和静态方法。
6. 热插件通过 `BBSPluginClientContext.dashboardPanels()` 注册；manifest 必须声明
   新增的 hot-safe `dashboard_panels` capability。
7. 热插件面板注册必须 staged；candidate 发布前不修改当前 Dashboard。commit 时在
   Minecraft client safepoint 安装，旧 generation 清理只能移除自己的贡献。
8. 热重载同 id 面板时保留面板位置；若旧面板当前处于选中状态，则切换到新面板。
   删除/禁用插件时，当前面板先安全退出并切换到可用的宿主面板，再解除所有引用。
9. Dashboard 尚未创建时只保存贡献；创建时投影全部活动贡献。Dashboard 已创建时，
   注册、替换和移除必须在客户端线程完成。
10. 单个插件内容工厂或生命周期回调失败必须记录并隔离，不得阻止 Dashboard 本体
    打开、关闭或其他插件面板工作。
11. 保留 `RegisterDashboardPanelsEvent` 的旧行为，现有直接监听事件的 addon 无需修改。
12. 提供文档示例和热插件 smoke fixture，展示标题、图标、文本、按钮及生命周期。

## Acceptance Criteria

- [ ] Addon API 2.0 addon 声明 `CLIENT_UI` 后能注册一个带文本和按钮的 Dashboard 面板。
- [ ] 未声明 `CLIENT_UI`、空描述、非法 id、空工厂均返回 `REJECTED` 并写入诊断。
- [ ] 两个 addon 注册同一完整 id 时 first-wins，后者得到 `DUPLICATE`。
- [ ] Dashboard 创建前注册和创建后注册都能投影；关闭再打开不会重复按钮或重复面板。
- [ ] 热插件声明 `dashboard_panels` 后能注册面板；未声明时 prepare 明确失败。
- [ ] 热插件 v1 -> v2 替换后只存在新内容；当前选中的旧面板自动切换到新面板。
- [ ] 热插件删除、禁用或 candidate 失败后不保留旧 factory、UI 根节点、按钮或
      generation classloader 强引用。
- [ ] 内容回调顺序满足 open -> appear -> update* -> disappear -> close；每次 screen
      session 至多各调用一次 open/close。
- [ ] 原有 `RegisterDashboardPanelsEvent` 仍会发出且旧监听方式仍可工作。
- [ ] `compileJava`、`compileClientJava`、`testAddonLifecycle`、
      `testClientStructuralRegistration`、`testBinaryCompatibility`、
      `testPluginHotReload` 全部通过。
- [ ] smoke fixture JAR 构建通过，并可在开发客户端人工完成安装、显示、热替换、
      删除与重新打开 Dashboard 验收。

## Out of Scope

- 不实现声明式 JSON/XML UI DSL；插件内容继续使用现有 Java `UIElement` 组件。
- 不开放任意 Minecraft screen 替换、远程 UI 协议或新的网络 payload。
- 不允许热插件通过该能力注册 Mixin、AT、coremod、native 或新的 Minecraft registry。
- 不删除或重写旧 `RegisterDashboardPanelsEvent`。

