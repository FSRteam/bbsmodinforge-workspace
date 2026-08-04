# motion-coverage 执行清单

前置阅读顺序:本任务 `prd.md` → `design.md` → 父任务 `../07-24-theme-v2/design.md` → `.trellis/spec/frontend/bbs-ui-theming.md`。

## 阶段 1:平滑滚动

- [ ] 在共享 scroll/list 路径加入 `targetScroll + visualScroll` 双值模型。
- [ ] 关闭态保持 `visual == logical`。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon compileClientJava testUiMotions`。

## 阶段 2:hover / press

- [ ] 按钮/图标类控件接 hover scale。
- [ ] 可点击控件接 press bounce。
- [ ] 关闭态默认 scale=1, 不改变既有颜色动画。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon compileClientJava testUiMotions`。

## 阶段 3:layout transition

- [ ] 面板切换前后抓 from/to。
- [ ] 动画期每帧插值写回 bounds 并 `resize()`。
- [ ] 再次切换时冻结当前插值为新 from。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon compileClientJava testUiMotions`。

## 阶段 4:文档与冒烟

- [ ] theme spec 与 frontend spec 同步新增条目。
- [ ] 记录命中正确性与动画中断的游戏内冒烟项。
