# Implement: theme-skin-settings-ui 执行清单

前置:theme-core 完成。motion-layer 已先行完成(motion_enabled/motion_speed 值行自动出现)。

## 清单

- [x] U1 UIKeys `SKINS_*` 9 常量 + en_us/zh_cn 文案(`bbs.ui.skins.*`);分类标题键
      `bbs.config.skins.title/tooltip` 与值行键在 theme-core 已就位。
- [x] U2 themeId 设置行特例:`UIValueMap` ValueString 工厂按值实例识别
      (`value == BBSSettings.themeId`,仓库既有模式)→ "浏览…"按钮打开选择器
      (260×0.6 overlay)。未做"按钮上显示当前主题名"(UIButton label 为静态 IKey,
      动态刷新需自定义控件;当前主题在选择器列表里高亮可见,信息不缺)。
- [x] U3 `UIThemeOverlayPanel` + 内嵌 `UIThemeList extends UIList<UITheme>`:
      每行 5 格色板(chrome/base/raised/accent/text,A100 + 淡描边)+ 名称(hover 高亮)
      + 右侧"作者 · 来源"(muted);来源=外部目录列举比对;选中高亮走 UIList 既有
      accent 行高亮;点击即 `ThemeManager.setTheme`。
- [x] U4 选择器右侧图标列:重载(reload + 列表刷新 + 坏主题通知)、打开文件夹
      (`UIUtils.openFolder`,先 mkdirs)。坏主题检测:`current().id != themeId.get()`
      即回退发生 → `notifyError(SKINS_BROKEN)`。
- [x] U5 皮肤分类头部操作:照 video 分类样板(`UISectionHeader` 按分类 id 挂图标),
      `Icons.MORE` 弹菜单(重载/打开文件夹/导出模板),动作复用 UIThemeOverlayPanel
      公开静态方法。
- [x] U6 导出模板:优先 jar 内 `assets:themes/example/theme.json`(builtin-packs 已落地),
      兜底内置 dark 原文;目录避让 my-theme → my-theme-2 → …;成功通知含目录名。
- [x] U7 accentFollowsTheme 联动:`UIValueMap` COLOR 工厂对 `primaryColor` 加 callback
      → 改动拾色器即 `accentFollowsTheme.set(false)`;tooltip(-comment 键)已说明联动。
- [x] U8 lang 完整性:en/zh `bbs.(ui|config).skins.*` 键集合 diff 相等(脚本核对通过)。

## 验证

- [x] `./gradlew compileClientJava` + `check`(见会话记录)。
- [ ] prd 验收清单逐条人工过(runClient)——**待用户游戏内**(选择器视觉/点击换肤/
      重载生效/导出可选用/en-zh 走查)。

## 回滚

新面板类 + UIValueMap 两处特例 + UISectionHeader 一段 + lang 键,单 commit revert 即净;
theme-core 提供的设置值不受影响。
