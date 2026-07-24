# Design: theme-skin-settings-ui 模块细化

总架构见父任务 design.md。本模块 = 纯 UI + 少量文件操作,全部走 ThemeManager 公开 API。

## 结构

```
client: mchorse/bbs_mod/settings/ui/UIThemeOverlayPanel.java   主题选择器 overlay
client: settings/ui/UIValueMap.java(或 UIValueFactory)        themeId 值 → 自定义"主题行"
main:   ui/UIKeys.java                                          bbs.ui.skins.* 常量
res:    assets/bbs/lang/en_us.json / zh_cn.json                 文案
```

## 关键点

1. **themeId 的设置行**:`ValueString` 默认渲染是文本框——不合适。做法:在 `UIValueMap`
   为 themeId 注册特例(参考现有 `ValueInt` COLOR/MODES subtype 特化的写法,或按值实例
   识别 `value == BBSSettings.themeId`):渲染为
   `[当前主题名 (作者)] [选择…按钮]`。核实 UIValueMap 的注册粒度后选择侵入最小的挂法。
2. **UIThemeOverlayPanel**:参照 `UISettingsOverlayPanel` 的 UIScrollView 列表模式;
   行内 renderSkin 画 5 个 8×8 色块(surfaceChrome/surfaceBase/surfaceRaised/accentPrimary/
   textPrimary)+ 名称 + 作者(mutedTextColor)+ 来源标签(内置=`BBSSettings.mutedTextColor()`
   小字 "built-in"/"内置",外部 "external"/"外部");当前项左侧
   `UIDashboardPanels.renderHighlight` 惯用高亮。
3. **打开文件夹**:检索仓库现成实现(插件面板 `UIPluginsPanel` 有"打开插件目录";
   复用同一工具方法;若是 `Util.getPlatform().openFile(...)` MC 惯用,照抄)。
4. **导出模板**:
   - 优先:从 `assets:themes/example/`(builtin-packs 落地后存在)经 provider 读出全部文件
     复制到目标目录;
   - 兜底(builtin-packs 未落地期):`ThemeParser` 提供 `serialize(UITheme)`?——不做;
     直接把内置 dark 的 theme.json 原文复制 + 在文件头部 `_note` 键写"由导出生成,规范见
     docs/theme-spec"。落地后切换到优先路径并删兜底。
   - 目录避让:`my-theme`、`my-theme-2`、`my-theme-3`…
5. **联动**:`primaryColor` 拾色器回调处(UIValueMap 的 COLOR 行或 BBSSettings 值监听)
   追加 `accentFollowsTheme.set(false)`;皮肤栏开关行 tooltip 说明该联动。
6. **文案键**(全集,en+zh 同步):
   ```
   bbs.ui.skins.title / .theme / .theme_select / .reload / .open_folder / .export_template
   bbs.ui.skins.accent_follows / .accent_follows_tooltip
   bbs.ui.skins.source_builtin / .source_external
   bbs.ui.skins.exported (含 %s 路径) / .reload_failed (含 %s 主题id)
   bbs.ui.skins.motion_enabled / .motion_speed   (motion-layer 的值行文案也归本任务)
   ```
   settings 分类标题键按现有分类命名惯例(核实 `UIValueFactory.getCategoryTitleKey` 生成规则,
   通常是 `bbs.settings.categories.skins`)补齐。

## 测试

UI 手工验收为主(prd 清单);唯一自动化:lang 完整性——若仓库已有 lang 键校验测试则挂进去,
没有则 grep 脚本核对 en_us/zh_cn 键集合相等。
