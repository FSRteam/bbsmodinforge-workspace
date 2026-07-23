# Design: FSR 热插件 Dashboard 面板

## 已完成部分(本会话已落盘,未提交,均在 `new/` 仓库工作区)

### 后端(common)

1. `src/main/java/mchorse/bbs_mod/plugin/manager/BBSPluginManager.java`
   - `PluginStatus` record 末尾新增字段 `BBSPluginDescriptor descriptor`(可为 null)。
   - `recordStatus(...)` 现在会:优先用传入 descriptor,否则沿用上一条状态的 descriptor;
     version 为空时从 descriptor 补齐。`withDiagnostic` 保留 descriptor。
2. `src/main/java/mchorse/bbs_mod/BBSMod.java`
   - 在 `getPluginDiagnostics()` 后新增静态桥:`getPluginDirectory()`、`isPluginAutoApply()`、
     `setPluginAutoApply(boolean)`、`rescanPlugins()`、`reloadPlugin(String)`、
     `enablePlugin(String)`、`disablePlugin(String)`,全部对 `activePluginManager` 判空。

### 客户端(全部为新文件,包 `mchorse.bbs_mod.ui.dashboard.plugins`)

3. `PluginJarInfo.java` — 从 jar 读取 `META-INF/bbs-plugin.json`(复用
   `PluginManifestDecoder`)、可选 `META-INF/bbs-plugin-icon.png`(≤1MB)与
   `META-INF/bbs-plugin-changelog.md`(≤256KB)。解析失败返回 null。
4. `PluginInstaller.java` — `install(PluginJarInfo)`:目标名 `<id>-<version>.jar`
   (sanitize),先删除同 id 其他 jar(防 DUPLICATE_ID),same-directory temp file +
   `ATOMIC_MOVE` 落盘(遵循热重载合同)。另有 `uninstall(id)`、`findInstalled(id)`。
5. `UIPluginDetailOverlayPanel.java` — 双模式详情 overlay:
   - `installed(refresh, status)`:展示全部字段 + 重载/启用|禁用/卸载按钮;
   - `candidate(refresh, jarInfo, installedStatus)`:安装预览,`compareVersions`
     决定按钮文案(安装/更新为 x.x/重新安装)。
   - icon 用 `Pixels.fromPNGStream` + `Texture.textureFromPixels(GL_LINEAR)` 懒加载,
     `onClose()` 里 delete。
6. `UIPluginsPanel.java` — dashboard 面板:工具栏(重扫/打开目录/tinyfd 浏览安装/
   autoApply UIToggle)+ `UIPluginList extends UIList<PluginStatus>`(状态色点 +
   名称 + 版本 + 右对齐状态文本),每 40 tick 调 `BBSMod.getPluginDiagnostics()` 刷新,
   保持选中项;空列表画 textCard 提示。
7. `src/client/java/mchorse/bbs_mod/ui/UIKeys.java` — 文件尾新增 `PLUGINS_*` 常量约
   30 个 + `C_PLUGIN_STATE = new KeyCollection("bbs.ui.plugins.state.^")`(用
   `EnumUtils.getKeys(BBSPluginState.class, name 小写)` 加载)。

## 剩余设计要点(给实现者)

### 面板注册

`UIDashboard.registerPanels()`(`src/client/.../ui/dashboard/UIDashboard.java` 约 253 行)
在 `UIGraphPanel` 之后、Sandbox 判断之前追加:

```java
this.panels.registerPanel(new UIPluginsPanel(this), UIKeys.PLUGINS_TITLE, Icons.PROCESSOR);
```

import `UIPluginsPanel`。图标用 `Icons.PROCESSOR`(芯片)。

### 语言条目

`src/client/resources/assets/bbs/assets/strings/en_us.json` 与 `zh_cn.json` 添加所有
`bbs.ui.plugins.*` 键(具体键名见 UIKeys 新增段),包括 13 个
`bbs.ui.plugins.state.<小写状态名>`(discovered/validated/preparing/staged/active/
reload_pending/draining/unloading/logically_unloaded/disabled/incompatible/failed/
restart_required)。`bbs.ui.plugins.empty` 带一个 `%s`(目录路径),
`bbs.ui.plugins.action.update` 带 `%s`(新版本号)。其余语言文件可不动(缺键回退)。

### 风险点(编译期最可能出错的地方,写代码时未逐一验证)

- `TinyFileDialogs.tinyfd_openFileDialog(CharSequence, CharSequence, PointerBuffer,
  CharSequence, boolean)` 的 null 重载歧义 —— 已用 `(CharSequence) null` 消歧,
  若仍歧义改用 `MemoryStack` 构造 `*.jar` 过滤器。
- `UIScrollView.column(int)/scroll()/vertical()/stretch()/padding(int)` 链式 API 与
  `UISettingsOverlayPanel` 中用法保持一致即可。
- `UIText.color(int, boolean)`、`Batcher2D.textCard(String,float,float,int,int)`、
  `fullTexturedBox(Texture,float,float,float,float)` 已核对存在。
- `UIOverlayPanel.onClose()`(无参,line 198)已核对存在。
- `PluginStatus` 构造参数增加了 1 个:全仓库只有 `BBSPluginManager` 内部两处构造,
  已同步;`pluginHotReloadTest` 只读字段,不构造,但需编译确认。
- `UIPluginsPanel` 里 `UIToggle` 的 resizer 链 `.x(84).y(12).w(120).h(16)` 如与
  toggle 内部布局冲突,参考现有 UIToggle 用法调整。

### 测试插件 jar(验收用)

结构:

```
META-INF/bbs-plugin.json      {"schema":1,"kind":"CONTENT","id":"demo_pack",
                               "displayName":"Demo Pack","version":"1.0.0",
                               "api":"1","side":"BOTH","reload":"AUTO"}   ← 字段取值
                               需比对 PluginManifestDecoder 实际接受的枚举拼写
META-INF/bbs-plugin-icon.png       任意 64x64 PNG
META-INF/bbs-plugin-changelog.md   两行文本
content/data/demo.txt              任意内容
```

用 `jar cf demo_pack-1.0.0.jar ...` 打包即可;先跑
`PluginManifestDecoder` 相关测试或直接丢进 dev client 验证会不会 FAILED VALIDATE。
side/reload/kind 的合法字符串以 `PluginManifestDecoder` 源码为准(勿凭猜测)。
