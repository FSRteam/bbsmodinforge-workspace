# Refreshed 主题 token 与内置主题包

父任务:`.trellis/tasks/07-25-fsr-refreshed-skin/`(design.md D1/D2/D5-C1,research/refreshed-feature-inventory.md §2)。

## Goal

为 theme v2 补齐 Refreshed 语义所需的新颜色 token,并交付内置主题包 `themes/refreshed/`(色值来自 research §2 实证表)。其余三个子任务依赖本任务的 token。

## Requirements

- R1 新增 token(text_field_border、tab_active_gradient、icon 五态、notification_*、selection_*、scrollbar_shadow 等,最终集合在 design.md 定):**dark 主题默认值 == 当前硬编码常量**,解析规则同 v1/v2(base 链、坏值回退 warn、parse-time 固化)。
- R2 接入点替换裸常量为 ThemeManager 语义函数时,逐一核对 `& Colors.RGB` 掩码规约(spec: bbs-ui-theming)。
- R3 内置主题包 `themes/refreshed/`:research §2 全部色值 + v2 圆角/背景/完整 spring motion 配置 + Refreshed 2.0 原 JAR 的 1024×1024 图标 atlas 逐字节直取,不做 AI 重绘、精修或缩放;不删不改现有五个主题。
- R4 docs/theme-spec 同步:README 逐 key + example-theme 全 key 显式 + jar 逐字节同步。

## Acceptance Criteria

- [x] themeCoreTest 对拍:dark 全部新 token == 旧常量(逐位)
- [x] refreshed 主题可选、解析无 warn
- [x] refreshed `textures.icons` 非空且指向 Refreshed 2.0 原 JAR 的 1024x1024 atlas;资源 SHA-256 与原包条目逐字节一致;切换主题后 `ThemeManager.resolveIconAtlas` 返回该资源
- [x] refreshed 显式配置全部 motion 条目为 spring,不再继承 dark 的 ease 类型
- [x] example-theme docs↔jar Files.mismatch 测试绿
- [ ] `gradlew check` 真实 exit code == 0
