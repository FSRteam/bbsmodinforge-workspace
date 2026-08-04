# PRD: 设置"皮肤"栏与皮肤管理器 UI

父任务:`.trellis/tasks/07-23-fsr-theme-system`。
依赖:`07-23-theme-core` 已完成(themeId/accentFollowsTheme/skins 分类已由 core 注册,
ThemeManager API 可用)。motion-layer 若未完成,其两个设置值(motionEnabled/motionSpeed)
在本任务里预留位置、由 motion-layer 注册后自动出现(设置面板按分类自动渲染)。

## 目标

让玩家在设置里管理皮肤:选主题(带预览)、重载、打开主题文件夹、导出模板起步包、
控制强调色跟随。全部文案 en_us + zh_cn。

## 需求

1. **皮肤分类**(id `skins`,`Icons.BRUSH`,theme-core 已建):设置面板左栏出现"皮肤"分区,
   包含(按序):
   - 主题行:显示当前主题名称(本地化用主题自带 name,非 lang key)+ "选择…"按钮 →
     打开主题选择器 overlay;
   - `accentFollowsTheme` 开关 + 现有 `primaryColor` 拾色器(改动拾色器时自动置 follows=false,
     theme-core 已定义语义,本任务落 UI 联动与提示);
   - `motionEnabled` / `motionSpeed`(由 motion-layer 注册,自动渲染即可);
   - 操作按钮行:重载主题、打开主题文件夹、导出模板。
2. **主题选择器 overlay**(新 `UIThemeOverlayPanel`,风格对齐 `UIOverlayPanel` 家族):
   - 列表:每主题一行 = 名称、作者、来源标记(内置/外部)、5 格色板预览
     (chrome/base/raised/accent/text 五色小方块)、当前项高亮;
   - 点击即应用(即时生效,无确认);双击/应用后可继续浏览,ESC 关闭;
   - 底部:重载按钮(rescan+reparse,列表刷新)、打开主题文件夹按钮。
3. **打开主题文件夹**:系统文件管理器打开 `config/bbs/assets/themes/`
   (不存在则先创建;核实仓库里已有"打开文件夹"惯用实现,如插件面板的打开插件目录)。
4. **导出模板**:把内置的规范样例(与 `docs/theme-spec/example-theme` 同源,由
   builtin-packs 任务放进 jar 资源;在其落地前本任务用 dark 主题 JSON 序列化兜底)写到
   `config/bbs/assets/themes/my-theme-<n>/`(避让已存在目录),写完弹通知含路径。
5. **坏主题反馈**:reload/切换遇到解析失败时,通知(`UINotifications`)提示主题 id + 失败原因
   简述(详细在日志)。
6. **文案**:`assets/bbs/lang/en_us.json` + `zh_cn.json` 新增全部键(`bbs.ui.skins.*`);
   `UIKeys` 按现有惯例加常量。汉化只维护资源侧 json(项目惯例)。

## 验收标准

- [ ] `compileClientJava` + `./gradlew check` 通过。
- [ ] 设置 → 皮肤:全部条目可见可用;主题选择器列表含内置+外部主题,色板预览与主题实际
      颜色一致,点击即全 UI 换色;当前项高亮正确。
- [ ] 重载:改外部主题 JSON → 点重载 → 颜色变化即时可见;列表新增/删除主题文件夹后重载,
      列表同步。
- [ ] 打开文件夹:Windows dev 环境资源管理器正确打开目标目录(目录不存在时自动建)。
- [ ] 导出模板:生成的文件夹可直接被枚举与选用;重复导出不覆盖已有(my-theme-2…)。
- [ ] 强调色:改拾色器 → follows 自动关;开 follows → 回主题色;重启后状态保持。
- [ ] 语言切换 en/zh 文案完整无缺键(日志无 missing key)。
- [ ] 坏 JSON 重载 → 通知出现,UI 不崩溃。

## 约束

- UI 布局与交互风格对齐现有 overlay 面板(参考 `UISettingsOverlayPanel`、插件面板);
  不发明新控件风格。
- 主题应用/重载全走 `ThemeManager` 公开 API,本任务不碰解析内部。
- 色板预览直接读解析后 `UITheme` 字段,不重复解析 JSON。
