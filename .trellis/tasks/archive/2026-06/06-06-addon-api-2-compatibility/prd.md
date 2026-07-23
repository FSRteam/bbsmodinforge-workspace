# Addon/API 2.0 规划

## 目标

为 `new` 中的 NeoForge 1.21.1 代码库设计一套完整的 Addon/API 2.0 规范与实施计划，在最大化开放扩展能力的同时，提高运行稳定性、诊断能力与兼容策略清晰度。

用户已明确收窄范围：本任务只考虑 `new`。`old` 不作为实现对象，也不作为差异分析基线。旧插件识别仅作为 `new` 内现有兼容规范和兼容层的运行时能力来设计。

## 用户价值

- Addon 作者获得稳定、文档化、可版本化的公共 API，而不是依赖内部类。
- 当前 `new` 已存在的 Addon v1 / compat 能力可以被明确归档为兼容层，不被 2.0 误删。
- 核心代码可以逐步把内部实现、平台适配、公共扩展接口分离，减少 addon 直接访问内部 manager 带来的崩溃和行为漂移。
- 未来新增扩展点可以通过统一的注册、元数据、生命周期和诊断机制接入。

## 已确认事实

- 工作区根目录不是 Git 仓库；`new` 是独立 Git 仓库。
- `new` 当前目标为 Minecraft 1.21.1、Java 21、NeoForge 21.1.222。
- `new` 当前有未跟踪目录：`.claude/` 与 `runs/`。后续不得提交，除非用户另行确认。
- `new` 已有 Addon v1 雏形：
  - `mchorse.bbs_mod.addon.BBSAddonRegisterEvent`
  - `mchorse.bbs_mod.addon.BBSAddonCollector`
  - `mchorse.bbs_mod.addon.BBSAddonBridge`
  - `mchorse.bbs_mod.addon.BBSAddonProtocolSelfCheck`
  - `mchorse.bbs_mod.events.BBSAddonMod`
  - `mchorse.bbs_mod.addon.demo.*`
- `new` 已有兼容/过渡层：
  - `mchorse.bbs_mod.client.compat`
  - `mchorse.bbs_mod.network.compat`
  - `mchorse.bbs_mod.client.rendering.context`
  - `mchorse.bbs_mod.loader.LoaderAccess`
- `docs/plan/02-common-lifecycle/addon-protocol-v1.md` 已定义 Addon v1：NeoForge 显式注册、一次性注册窗口、first-wins 冲突策略、桥接到内部 `EventBus`。
- `docs/plan/11-loader-resource-access/migration-spec.md` 已定义 `LoaderAccess#getEntrypoints("bbs-addon", BBSAddonMod.class)` 的范围受限兼容行为。
- `docs/plan/04-networking/migration-spec.md` 与 `new` 的 `NetworkCompat` 均说明当前网络层有冻结通道和兼容裸 buffer 过渡层。
- 已按用户目录规范在根目录 `temp/` 下克隆 Blender 参考源码：`temp/blender-api-reference`。未放入 `docs`。
- Blender 参考体现的 API 设计要点是：元数据、注册类、注册/注销生命周期、运行时校验、只开放有限可控类型，而不是直接暴露内部实现。

## 需求

- 所有后续实现计划只允许修改 `new`。
- `docs` 用于零散资料和研究记录；克隆源码统一放根目录 `temp/`。
- Addon/API 2.0 规范必须基于 `new` 现有代码、`docs` 现有资料、NeoForge 1.21.1 官方资料和 Blender API 设计参考。
- 规范必须定义：
  - 公共 API 包边界；
  - addon 元数据、版本和能力声明；
  - addon 自动检测与分类；
  - v2 生命周期；
  - v2 注册上下文；
  - v1/current 兼容路径；
  - 稳定注册器/上下文 facade；
  - 事件模型；
  - 资源、settings、forms、clips、particles、render、network 等扩展点的公开策略；
  - 错误隔离、日志、诊断、自检；
  - 文档与示例 addon；
  - 验证和回滚策略。
- legacy/current addon 检测应优先使用 `new` 已存在的协议证据：
  - `BBSMod.registerAddon(...)`
  - `BBSAddonRegisterEvent`
  - `LoaderAccess#getEntrypoints("bbs-addon", ...)`
  - `NeoForgeLoaderAccess` 扫描校验
  - `BBSAddonProtocolSelfCheck`
- API 2.0 不应直接暴露内部 manager 的可变对象；应使用专用注册器或只读上下文包裹。
- 不得在用户确认前实现代码。
- 不得在用户确认前本地 commit。
- 后续 commit 只允许包含 `new` 下的内容。

## 建议兼容策略

推荐策略：`兼容优先 + 明确警告 + 逐步迁移`。

- 检测到 v2 addon：走 API 2.0 生命周期和注册上下文。
- 检测到 v1/current addon：继续走现有 Addon v1 规范和 compat 层，启动时输出一次性兼容告警与迁移提示。
- 检测到冲突 addonId：延续 v1 first-wins + reject-later，但在 2.0 诊断中记录冲突来源和协议版本。
- 检测到未知/非法 addon：隔离失败 addon，不中断 BBS 核心启动。

该策略牺牲一部分严格性，换取现有 addon 的可用性和较低迁移成本。

## 验收标准

- [ ] `prd.md` 明确记录范围、需求、约束和验收标准。
- [ ] `design.md` 定义 Addon/API 2.0 架构、包边界、生命周期、版本、兼容检测、错误策略和扩展点。
- [ ] `implement.md` 给出有序实施计划、验证命令、回滚点和 review gate。
- [ ] 规划引用 `new` 的实际代码、`docs` 的现有迁移资料、NeoForge 官方文档、Blender API 设计参考。
- [ ] 不在用户确认前开始实现。
- [ ] 不在用户确认前 commit。
- [ ] 后续如进入实现，Git staged/commit 范围必须限制在 `new`。

## 非目标

- 修改 `old`。
- commit `new` 之外的文件。
- 重新维护 Fabric 1.20.4 目标。
- 一次性重写全部内部系统。
- 制作远程 addon 市场、包管理器或下载系统。
- 删除现有 Addon v1 兼容路径。

## 待用户确认

是否采用推荐的 `兼容优先 + 明确警告 + 逐步迁移` 策略作为 API 2.0 默认兼容政策。
