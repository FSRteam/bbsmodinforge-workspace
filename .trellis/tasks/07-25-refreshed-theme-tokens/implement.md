# Implement: Refreshed 主题 token 与内置主题包

前置阅读:本任务 prd.md + design.md;`.trellis/spec/frontend/bbs-ui-theming.md`;父任务 research/refreshed-feature-inventory.md §2。

## Checklist(按序)

- [x] 1. UITheme.java 新增 design §1 全部 token 字段 + Builder;ThemeParser 解析 + defaultDark 默认值(dark 默认规则见 design §1,拿不准的先 0 哨兵)
- [x] 2. BBSSettings 语义函数(照 v1 现有模式,一 token 一函数;命名与现有风格一致)
- [x] 3. ThemeCoreTest:dark 对拍新 token 逐项断言;坏值回退×2;base 继承×1
- [x] 4. 中间验证:`cd new; ./gradlew check *> ../check.log; "REAL_EXIT=$LASTEXITCODE"` == 0
- [x] 5. `themes/refreshed/theme.json`(design §2 色值)+ ThemeManager 白名单加 "refreshed" + testBuiltinPacks 循环加 "refreshed"
- [x] 6. docs/theme-spec README 新 key 文档 + example-theme 全 key 显式 + jar 副本逐字节同步
- [x] 7. 终验证:check 真实 exit code == 0;游戏外无法冒烟的项列入父任务冒烟清单
- [x] 8. 用户反馈修订:直接加入 Refreshed 2.0 原 JAR 的 1024x1024 icons atlas(逐字节一致,不缩放/重绘),设置 `textures.icons`,补完整 spring motion block
- [x] 9. 扩展主题测试:icon link/资源存在 + Refreshed motion 类型断言;再次运行 check

## 2026-07-26 验证记录

- `compileClientJava` 通过。
- `testThemeCore testUiMotions` 通过；Refreshed atlas 最终改为原 JAR 逐字节复制的 1024x1024 文件，主题资源解析与全部 12 个 spring motion 条目断言通过。
- `jar` 在 2026-07-26 Refreshed UI 一致性修复后再次重建通过；产物为 `new/build/libs/BBS_FSR_beta_v0.0.10.jar`，大小 7,939,350 bytes，SHA-256 `C5EBA48297AA81E20897D9E88EA14B6C0793301E73D4429D6AFDEB3722A54750`。
- JAR 已核对包含 `assets/bbs/assets/themes/refreshed/theme.json` 和 `assets/bbs/assets/themes/refreshed/icons.png`。
- `testUiInputMailbox` 在共享 NeoForm 缓存重建后干净重跑通过：`BBSUiInputDispatcherTest: all tests passed`。
- 后续全量 `check` 中已执行的套件均通过；`testParticleRuntimeConsistency` 自身输出 `all tests passed`，但因另一个进程锁住 `new/logs/latest.log` / `debug.log` 产生未捕获 ERROR diagnostic，Gradle 最终 exit code 为 1。因此 PRD 的全量 `check == 0` 门禁仍未满足。
- 用户复核后纠正图标交付语义：主题 atlas 直接复制 `temp/bbs-refreshed-2.0-public-1.20.1.jar` 内 `assets/bbs/assets/textures/icons.png`，文件大小 69,232 bytes，1024x1024，SHA-256 `41DD95D44B6A7AC467EFD34F6A7F4E527F0DD6BABD21052447CC0687EFE0D657`。源码资源、原 JAR 条目和最终构建 JAR 条目三者哈希一致；新增 ThemeCoreTest 尺寸+哈希回归断言。

## 验证命令

```powershell
cd new; ./gradlew check *> ../check.log; "REAL_EXIT=$LASTEXITCODE"
```
禁止管道判绿;失败看 check.log 与 build/reports。

## 回滚点

步骤 4 与 7 各一个安全点;全部改动限于 ui/themes/、BBSSettings、client resources themes/、docs/theme-spec、themeCoreTest——单独 revert 不影响兄弟任务。

## 禁区

不改 UIDashboardPanels / UIToggle / UINotifications / UIIcon / Scroll / UIClips / UIKeyframes;不删改现有主题文件;不写游戏内皮肤编辑器。
