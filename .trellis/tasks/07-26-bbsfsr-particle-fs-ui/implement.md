# Implement: BBSFSR 粒子界面 FS 化

## 执行清单

- [x] 1. 读取 frontend/new 规范与预开发检查，确认当前工作树重叠文件。
- [x] 2. 为 `Textbox`/`UITextbox` 增加默认关闭的 leading icon、色条与 content inset，补默认路径回归检查。
- [x] 3. 将通用粒子模式基类及发射率、寿命、形状、方向、运动改为 `UIIcons`，保持索引顺序。
- [x] 4. 将材质、纹理、朝向、方向来源、颜色、过期、曲线改为 `UIIcons`。
- [x] 5. 将碰撞外观和事件节点/effect type 等 FSR 扩展模式改为 `UIIcons`。
- [x] 6. 为 MoLang 表达式输入接入左侧语义图标/色条；保留原地编辑、回调和撤销边界，普通字符串输入不改。
- [x] 7. 静态核对窄栏下 11 项朝向、7 项事件类型的等分宽高、tooltip 与 focus/hover/disabled/active 绘制路径。
- [x] 8. 运行定向检查:`compileClientJava`, `testUiInputMailbox`, `testParticleRuntimeConsistency`, `testThemeCore`。
- [x] 9. 运行 `gradlew check` 并核对真实退出码。
- [x] 10. 按 `trellis-check` 做 spec/代码/测试复核，记录无法自动完成的游戏内视觉冒烟项。

## 高风险文件

- `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/text/utils/Textbox.java`
- `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/text/UITextbox.java`
- `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/buttons/UIIcons.java`
- `new/src/client/java/mchorse/bbs_mod/ui/particles/sections/UIParticleSchemeAppearanceSection.java`
- `new/src/client/java/mchorse/bbs_mod/ui/particles/sections/UIParticleSchemeCollisionAppearanceSection.java`
- `new/src/client/java/mchorse/bbs_mod/ui/particles/sections/UIParticleSchemeEventsSection.java`

## 验证命令

```powershell
cd new
./gradlew compileClientJava
./gradlew testUiInputMailbox testParticleRuntimeConsistency testThemeCore
./gradlew check
```

## 开始前检查

- 图标加入顺序与旧 label 顺序逐项对齐。
- 不复制 FS `UIMolangExpression` 的 click-to-open 行为。
- 不修改 particle runtime/parser/serialization。
- 不覆盖当前未提交的 theme/spec/task 改动。

## 验证结果

- `compileClientJava`:通过。
- `testUiInputMailbox`:通过。
- `testParticleRuntimeConsistency`:通过，包含默认/带图标 content inset 与极窄宽度回归。
- `testThemeCore`:通过。
- `check`:通过，最终源码状态退出码为 0。
- 静态检查:粒子 UI 包内无 `UICirculate`/`addLabel` 遗留；未加图标的输入均为资源 ID、事件名、日志、声音、时间线键、距离等非 MoLang 字段。
- 待手动:进入游戏后用 Refreshed 与兼容主题分别检查 11 项朝向、7 项事件按钮组、长表达式光标/选择和 tooltip；当前环境未执行游戏内视觉冒烟。
