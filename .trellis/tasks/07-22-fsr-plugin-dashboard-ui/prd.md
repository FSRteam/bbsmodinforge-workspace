# PRD: FSR 热插件 Dashboard 面板(插件管理 UI)

## 背景

FSR(`new/`)已具备完整的热插件运行时(`BBSPluginManager`,commit `0057bac0`),
底层集成测试 `./gradlew testPluginHotReload` 全部通过。但客户端没有任何 UI 入口,
用户无法看到已加载插件、状态与错误,也无法安装/重载插件。

之前"实测热重载无效"的根因已确认为环境问题:游戏实例(`D:\1.21\versions\1.21.1-NeoForge_21.1.223`)
装的是 7月16日的 `bbs-2.3.1.jar`(不含插件运行时);含运行时的 `BBS_FSR_beta_v0.0.10.jar` 从未部署。
不是代码 bug。

## 用户需求(已确认)

1. 在 dashboard 底部面板栏(伪装、影片、模型方块、粒子……)**最后**添加一个"插件"面板。
2. 面板列出所有热插件:名称、版本、状态(带颜色)、点击打开详情。
3. 详情界面展示:基础状态(状态/代号 generation/sha256)、类型与能力(CODE/CONTENT、
   capabilities、side、api)、依赖列表、错误详情(lastCode/lastMessage)、最近变更时间、
   插件图标(jar 内 `META-INF/bbs-plugin-icon.png`)、更新日志(`META-INF/bbs-plugin-changelog.md`)。
4. 操作:重载 / 启用 / 禁用 / 卸载;面板工具栏:全局重扫、autoApply 开关、打开插件目录、
   浏览选择 jar 安装。
5. 安装流程:选择 jar → 解析 manifest → 详情预览界面 → 识别已装版本:
   未安装显示"安装",版本更新显示"更新为 x.x",相同显示"重新安装" → 点击后原子复制进
   `config/bbs/plugins/`,热重载自动生效。

## 验收标准

- `compileJava`、`compileClientJava`、`testPluginHotReload`、`testAddonLifecycle`、
  `testBinaryCompatibility` 全部通过。
- dev 客户端打开 dashboard 能看到插件面板;丢入合法测试插件 jar 后列表 2 秒内出现 ACTIVE 条目;
  删除 jar 后条目变为 unloaded/消失。
- 详情界面所有字段与操作按钮按上述规格工作。
- 附带一个可用的最小测试插件 jar(content 类型)供人工实测。

## 约束

- 不得违反 `.trellis/spec/new/backend/hot-plugin-runtime-contract.md`(安装必须
  same-directory temp + atomic move;禁止直接从源 jar 加载)。
- 不改动 Addon API 2.0 与已冻结的 payload ID。
- UI 代码遵循现有 mchorse UI 框架惯例(UIDashboardPanel/UIList/UIOverlayPanel)。
