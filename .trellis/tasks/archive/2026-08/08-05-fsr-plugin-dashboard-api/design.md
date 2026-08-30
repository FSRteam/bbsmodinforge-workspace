# Design: FSR 插件自定义 Dashboard 面板 API

## 1. Boundary

公开 API 只表达插件拥有的内容，不把 `UIDashboard` 或 `UIDashboardPanel` 作为新合同。
计划新增 client-only 类型：

- `BBSDashboardPanelSpec`：不可变描述，builder 创建；字段为 local id、`IKey` 标题、
  `Icon` 图标。
- `BBSDashboardPanelContent`：返回 `UIElement root()`，并提供默认空实现的
  `onOpen/onClose/onAppear/onDisappear/onUpdate`。
- `BBSDashboardPanelFactory`：SAM，按 Dashboard 实例创建 content。
- `BBSDashboardPanelRegistry`：热插件 context 使用的窄 facade。

Addon 入口新增：

```java
BBSRegistrationResult BBSClientApi.registerDashboardPanel(
    BBSAddonDescriptor descriptor,
    BBSDashboardPanelSpec spec,
    BBSDashboardPanelFactory factory
)
```

热插件入口新增：

```java
BBSPluginClientContext client = context.extension(BBSPluginClientContext.class);
client.dashboardPanels().register(spec, factory);
```

## 2. Ownership And Identity

宿主把 local id 标准化为 `<addonId>:<localId>` 或 `<pluginId>:<localId>`。注册表条目
保存不可变 contribution identity；热插件还保存 `PluginOwner(pluginId, generation)`。

- Addon：永久 startup owner，重复 id first-wins。
- 热插件首次安装：同完整 id 已存在则 duplicate。
- 同一 hot plugin 新 generation：只有 structural window 标记该 key 可替换时才允许
  覆盖 incumbent。
- remove 使用 contribution 对象 identity 比较，旧 ledger 不能删除新 generation。

## 3. Host Projection

新增 client host registry 保存当前活动 definition；`UIDashboard` 保存本实例的 mounted
panel 映射，避免全局注册表通过 panel -> dashboard 反向引用导致旧 Dashboard 泄漏。

```text
Addon/Hot facade
  -> validate owner/capability/spec
  -> active contribution registry
  -> current UIDashboard (if created)
  -> host-owned UIDashboardPanel wrapper
  -> plugin BBSDashboardPanelContent.root()
```

Dashboard 构造顺序：

1. 注册内置面板。
2. 投影当前公开 API contributions。
3. 发出旧 `RegisterDashboardPanelsEvent`。
4. 继续现有 UI 初始化。

因此旧事件继续工作，并且新 API 面板有确定顺序。

## 4. Dynamic Lifecycle

`UIDashboardPanels` 增加宿主内部的 replace/remove 操作，并跟踪 screen 是否 open：

- 动态 add：若 screen 已打开，先 `open()`；被选择时再 `appear()`。
- replace：先创建新 content；创建失败不触碰 incumbent。成功后在原按钮位置替换；
  incumbent 若当前选中，则依次 `disappear/close`，新面板依次 `open/appear`。
- remove：若当前选中，先切到 Film 或首个可用宿主面板，然后 `close`、移除按钮和列表项。
- Dashboard 关闭：所有 mounted panel 收到一次 `close`，仍保留对象供下次打开。

插件生命周期回调由 host wrapper 捕获 `Exception` 与 `LinkageError`，记录 owner、panel id
和 phase 后继续。内容树自身按钮回调仍遵守现有 UI 框架异常规则。

## 5. Addon API 2.0

`BBSClientApi` 的新增方法委托 client host registry：

- 验证 descriptor、addon id、`CLIENT_UI`、spec、factory。
- 以 `REGISTER_CLIENT` 记录接受/拒绝/duplicate 结果。
- 内容工厂在 Dashboard 投影时失败则以 `CLIENT_SETUP` 记录错误，跳过该面板。
- 不修改 `BBSAddonRegistrationContext`，避免 common API 解析 client-only 类。
- 不修改现有方法和枚举，API 常量继续为 `2.0`。

## 6. Hot Plugin SPI

- `BBSPluginCapability` 末尾追加 `DASHBOARD_PANELS("dashboard_panels", true)`，避免改变
  已有枚举 ordinal。
- `BBSPluginClientContext` additive 增加 `dashboardPanels()`。
- `BBSPluginClientStructuralBridge.ClientContext` 创建 facade，验证 capability 后把
  `dashboard-panel:<pluginId>:<localId>` stage 到 `PluginStructuralRegistrationWindow`。
- apply/undo 都在现有 client structural safepoint；undo 使用 contribution identity。
- SPI `CURRENT` 保持 `1.0`：旧插件不受影响；新插件的未知 capability 会让旧宿主在
  manifest 校验阶段拒绝，而不是运行到缺失方法。

## 7. Failure And Rollback

| Failure | Result |
| --- | --- |
| invalid id/null title/icon/factory | reject before registry mutation |
| duplicate owner+id | first contribution retained |
| candidate content factory throws while Dashboard exists | candidate apply fails; incumbent remains |
| content factory throws on later Dashboard creation | skip only that panel, record diagnostic, Dashboard continues |
| lifecycle callback throws | log/diagnose and continue remaining lifecycle |
| hot unload while panel selected | switch to fallback, then detach old content |
| old generation cleanup runs after replacement | identity mismatch; replacement remains |

回滚为 additive：移除新增 API/registry/wrapper、撤销 `UIDashboard` 与
`UIDashboardPanels` 的动态投影钩子、移除 hot capability/facade 即可；旧事件路径始终保留。

## 8. Verification

- 纯 Java：spec 校验、first-wins、identity-safe replace/remove、callback 顺序。
- hot-plugin：staged before commit、generation replacement、ledger cleanup、capability gate。
- binary：旧 descriptor、SAM 数量、Addon API `2.0`、枚举 ordinal 不变；新增 API 存在。
- smoke fixture：v1/v2 同 panel id 显示不同内容，人工确认热替换和删除。

