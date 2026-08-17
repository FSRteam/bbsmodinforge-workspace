# 2026-08-07 编辑伪装回到选择页核查

## 现象对应的 BBS 行为

`UIFormPalette.toggleEditor()` 只有在 `UIFormEditor.edit(form)` 返回 `false` 时才会继续显示选择列表。常见原因是选中项为空，或该 Form 的 editor panel 没有注册。面板构造抛出的链接错误则会直接进入异常处理，不是正常的页签切换。

## 核查结果

- NeoForge IRLights 的 `PointLightForm`、`SpotlightForm` renderer 和 editor 注册均存在。
- Fabric 1.21.1 + BBS 2.2.1 客户端实际启动后，两个 panel 均能构造。
- 两端均用真实 `UIFormPalette` 执行了 `setSelected(form)`、`edit(true)`；结果均为 editor 进入编辑状态、选择列表隐藏、编辑界面显示。
- Fabric 成品包含 `UIFormEditorMixin`，且干净运行中该客户端 Mixin 配置被 Fabric Loader 选中。
- 用户实际 PrismLauncher 实例路径来自另一台机器/账户，本机无法读取，因此无法核对用户实例是否仍加载旧 JAR、重复 JAR 或错误加载 Fabric/NeoForge 变体。

## 兼容兜底

- NeoForge 在客户端模组构造阶段立即注册两个 renderer 和 editor，客户端启动回调仍做幂等复核。
- Fabric 交付版在 `ClientModInitializer` 中显式注册两个 renderer 和 editor，同时保留上游旧 Mixin 注册路径以兼容原有加载顺序。
- NeoForge 运行验证新增完整的选择页到编辑页状态断言，避免以后只验证注册表条目而漏掉真实切换失败。

## 验证

- Fabric `runClient`：两个 panel 构造和完整 palette 编辑状态通过。
- NeoForge `runClient -Pverify`：日志输出 `runtime verification passed`，完整 palette 编辑状态断言通过。
- NeoForge `check build` 与 Fabric `clean build` 均成功。

Fabric 自检为临时诊断钩子，正式构建前已移除；成品字节码只保留显式注册，不含自检系统属性或自动退出逻辑。
