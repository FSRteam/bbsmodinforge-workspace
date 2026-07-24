# motion-tracks 执行清单

前置阅读顺序:本任务 `prd.md` → `design.md` → 父任务 `../07-24-theme-v2/design.md` → `.trellis/spec/frontend/bbs-ui-theming.md`。

## 阶段 1:schema 与解析

- [ ] `UIThemeMotion` 扩 `preset/tracks` 结构。
- [ ] `ThemeParser` 实现 preset 展开与显式 tracks 覆盖。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon testThemeCore`。

## 阶段 2:overlay / context menu 接线

- [ ] 进入动画按 tracks 采样。
- [ ] 退出动画复用同一组 tracks 反向采样。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon testUiMotions compileClientJava`。

## 阶段 3:扩展接入点与文档

- [ ] 视代价扩到 panel_switch / notification。
- [ ] docs/theme-spec 与 frontend spec 同步 schema。
- [ ] 演示主题写入 tracks/preset 示例。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon testThemeCore testUiMotions`。
