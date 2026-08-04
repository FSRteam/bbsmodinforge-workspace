# motion-exit 执行清单

前置阅读顺序:本任务 `prd.md` → `design.md` → 父任务 `../07-24-theme-v2/design.md` → `.trellis/spec/frontend/bbs-ui-theming.md`。

## 阶段 1:退场态骨架

- [ ] 在 overlay / context menu 相关类中引入 `closing/exiting` 状态与统一 close 入口。
- [ ] motion disabled 或 duration=0 时保持 v1 立即移除语义。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon compileClientJava`。

## 阶段 2:反向采样与输入短路

- [ ] 进入/退出共用同一组 motion 参数,关闭时反向播放。
- [ ] 退场期间立即短路鼠标/键盘/焦点/命中。
- [ ] backdrop 退场时只渲染、不拦截下层交互。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon testUiMotions`。

## 阶段 3:连打/强制移除/收尾

- [ ] 重复 close 幂等。
- [ ] 退场中再次打开同类 overlay/context menu 不崩溃、不残留。
- [ ] 父级强制移除时允许直接硬清理。
- [ ] 验证:`cd new && ./gradlew.bat --no-daemon testUiMotions compileClientJava`。

## 阶段 4:文档与冒烟记录

- [ ] 如有新行为约束,同步到 `new/docs/theme-spec/README.md` 与 `.trellis/spec/frontend/bbs-ui-theming.md`。
- [ ] 记录游戏内冒烟清单: ESC / backdrop / 快速开关 / 穿透点击。
