# Implement: FSR 插件自定义 Dashboard 面板 API

工作区：`D:/bbsinneoforge/bbsmodinforge/bbsmodinforge/.worktrees/fsr-plugin-ui-api`

## Checklist

- [ ] 1. 新增 client-only panel spec/content/factory/registry 公共类型，补参数校验。
- [ ] 2. 新增 host contribution registry 与 host-owned wrapper，隔离工厂和生命周期异常。
- [ ] 3. 扩展 `UIDashboardPanels` 动态 add/replace/remove，保留位置、active panel 和
      open/appear 状态机。
- [ ] 4. 在 `UIDashboard` 接入活动 contribution 投影，并继续发旧注册事件。
- [ ] 5. 在 `BBSClientApi` 增加 descriptor-aware 注册方法，接入 `CLIENT_UI` 检查与
      `REGISTER_CLIENT/CLIENT_SETUP` diagnostics。
- [ ] 6. 追加 `dashboard_panels` hot-safe capability；在 `BBSPluginClientContext` 与
      `BBSPluginClientStructuralBridge` 接入 staged facade 和 identity-safe cleanup。
- [ ] 7. 扩展 client structural/binary/hot reload 测试，覆盖 first-wins、capability、
      staged mutation、replace/remove 和旧合同不变。
- [ ] 8. 更新 v1/v2 smoke fixture，以同 panel id 注册不同版本内容。
- [ ] 9. 更新 `docs/addon-api-2.md` 与 hot-plugin runtime contract，添加可复制示例、
      生命周期、线程和限制说明。
- [ ] 10. 运行格式/编译与定向回归；修复后扩大到 `build`（若完整 build 的既有门禁
      失败，区分本任务回归与基线问题并留证据）。
- [ ] 11. 人工 dev-client smoke：显示、按钮、切换、关闭重开、v1->v2 热替换、删除。
- [ ] 12. 运行 `trellis-check`、更新必要 spec、提交 `new` 独立分支，并记录任务结果。

## Validation Commands

```powershell
./gradlew.bat compileJava compileClientJava
./gradlew.bat testAddonLifecycle testClientStructuralRegistration testBinaryCompatibility
./gradlew.bat testPluginHotReload buildSmokeFixturePlugins
./gradlew.bat build
./gradlew.bat runClient
```

## Review Gates

- API：common source 不引用 client-only 类；既有 descriptor/enum ordinal 不变。
- 生命周期：每条 remove/replace 路径先退出现有面板，再解除引用。
- 热重载：candidate stage 不可见；旧 generation cleanup 不影响新 generation。
- UI：关闭重开不重复；当前插件面板卸载后有确定 fallback。
- 兼容：旧 `RegisterDashboardPanelsEvent` 仍在原构造流程中发出。

## Rollback Points

- Gate A：只完成 public types + host registry，尚未接入 Dashboard。
- Gate B：Addon API 接入完成，可单独撤销 hot plugin capability/facade。
- Gate C：热插件接入完成；若 smoke 不稳定，可先移除 fixture capability，保留 Addon API。

