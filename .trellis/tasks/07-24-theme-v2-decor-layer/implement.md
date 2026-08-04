# decor-layer 执行清单

前置阅读顺序:本任务 `prd.md` → `design.md` → 父任务 `../07-24-theme-v2/design.md` → `.trellis/spec/frontend/bbs-ui-theming.md`。

## 阶段 1:theme schema

- [ ] `UITheme` / `ThemeParser` 新增 decorations、background_mode、background_dim。
- [ ] decorations 数量上限与坏值丢弃在解析期完成。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon testThemeCore`。

## 阶段 2:根绘制接线

- [ ] 抽共享背景/装饰绘制 helper。
- [ ] 接到 dashboard 与根界面公共绘制入口。
- [ ] 保证点击穿透。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon compileClientJava`。

## 阶段 3:演示主题与文档

- [ ] strawberry 或等价演示主题接入 background + decorations。
- [ ] docs/theme-spec 与 frontend spec 同步 schema。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon testThemeCore compileClientJava`。
