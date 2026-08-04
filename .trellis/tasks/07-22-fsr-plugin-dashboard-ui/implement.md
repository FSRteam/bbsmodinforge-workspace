# Implement: FSR 热插件 Dashboard 面板(剩余工作)

工作目录:`new/`(独立 git 仓库,HEAD = `0057bac0`)。
已完成的改动清单见 `design.md` "已完成部分";以下为**剩余**执行清单,按顺序做。

## Checklist

- [ ] 1. 注册面板:`UIDashboard.registerPanels()` 在 `UIGraphPanel` 行后追加
      `this.panels.registerPanel(new UIPluginsPanel(this), UIKeys.PLUGINS_TITLE, Icons.PROCESSOR);`
      并补 import。
- [ ] 2. 语言条目:`en_us.json` + `zh_cn.json` 添加全部 `bbs.ui.plugins.*` 键
      (键名清单 = UIKeys.java 文件尾新增段;13 个 state 键;empty 与 action.update 带 %s)。
      中文示例:title=插件, rescan=重新扫描, auto_apply=自动应用, open_folder=打开插件目录,
      install_browse=安装插件…, install_invalid=所选文件不是有效的 BBS 插件,
      state.active=运行中, state.failed=失败, action.reload=重载, action.enable=启用,
      action.disable=禁用, action.uninstall=卸载, action.install=安装,
      action.update=更新为 %s, action.reinstall=重新安装, install_failed=安装失败。
- [ ] 3. 编译:`./gradlew compileJava compileClientJava`。逐个修编译错
      (高危点见 design.md "风险点";不要改动运行时语义)。
- [ ] 4. 回归:`./gradlew testPluginHotReload testAddonLifecycle testBinaryCompatibility`。
      `PluginStatus` record 加了 descriptor 字段,若 binary-compat 门禁报 record 签名
      变化,按门禁基线更新流程处理(该 record 是 FSR 新增类型,不属于冻结 Addon API)。
- [ ] 5. 测试插件 jar:按 design.md 规格制作 `demo_pack-1.0.0.jar`
      (manifest 枚举拼写以 `PluginManifestDecoder` 源码为准),放到
      `new/build/test-plugins/`,并做一个 `1.1.0` 版本用于验证"更新"按钮文案。
- [ ] 6. 冒烟(dev client `./gradlew runClient` 或构建后部署):
      - dashboard 面板栏最后出现"插件"面板;
      - 丢 `demo_pack-1.0.0.jar` 进 `runs/client/config/bbs/plugins/` → 列表 ~2s 内出现
        ACTIVE 绿点条目;
      - 点条目 → 详情 overlay 字段齐全,图标与 changelog 显示;
      - 重载/禁用/启用/卸载按钮生效;
      - "+" 浏览选择 `demo_pack-1.1.0.jar` → 预览显示"更新为 1.1.0" → 安装后版本变化;
      - 删除 jar → 条目消失/变 unloaded;
      - autoApply 关闭后替换 jar → 状态变 RELOAD_PENDING,重扫后生效。
- [ ] 7. 提交:`new/` 内单独 commit,建议
      `feat: add hot plugin dashboard panel with install/update flow`。
      同时提交本会话已改的后端/UI 文件(git status 里的 plugin 相关改动;
      注意工作区还有**无关**的未提交改动如 BBSModClient film 保存修复、audio 测试等,
      勿混入本 commit,只挑 plugin/UI 相关文件)。
- [ ] 8. 更新 spec:若安装器/详情页行为与
      `.trellis/spec/new/backend/hot-plugin-runtime-contract.md` 有交集,在该文档追加
      客户端安装入口的约定(same-directory temp + atomic move 已遵守,写明即可)。

## 验证命令

```bash
cd new
./gradlew compileJava compileClientJava
./gradlew testPluginHotReload testAddonLifecycle testBinaryCompatibility
./gradlew runClient   # 手工冒烟
```

## 回滚

所有改动集中在:BBSPluginManager.java(2 处小改)、BBSMod.java(新增静态方法)、
UIKeys.java(尾部追加)、UIDashboard.java(1 行注册)、2 个语言 json、
`ui/dashboard/plugins/` 4 个新文件。`git checkout -- <file>` / 删除新目录即可回滚。
