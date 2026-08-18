# 迁移 BBS-PoseCurve 到 1.21.1 BBSFSR

## Goal

将 `BBS-PoseCurve-fsr/BBS-PoseCurve-Addon-2.8.3-1.20.1-sources` 中面向
Minecraft 1.20.1 BBSFS 的 BBS-PoseCurve 功能迁移到与其并列的新插件目录，
使其能够在仓库 `new/` 的 Minecraft 1.21.1 NeoForge BBSFSR 上构建和加载。

迁移后的插件应尽量保持旧版用户可见行为一致，并采用 `new/` 当前实际 API，
不修改旧版参考源码。

## Confirmed Facts

- 旧版参考源码位于 `BBS-PoseCurve-fsr/BBS-PoseCurve-Addon-2.8.3-1.20.1-sources`。
- 目标 BBSFSR 源码位于 `new/`，游戏版本为 Minecraft 1.21.1，加载器为 NeoForge。
- 旧版插件包含约 40 个 Java、Mixin 和资源文件，功能涉及曲线编辑、姿势通道、
  自动 Bezier/向量控制柄、插值继承、撤销光标、剪辑视口和轨道镜头状态。
- 迁移产物应位于与 `BBS-PoseCurve-fsr`、`new` 并列的位置，而不是直接并入
  `new/` 主工程源码。
- 当前工作区存在其他任务的未提交改动；本任务不得覆盖或回退这些改动。

## Requirements

- 在仓库根目录新建独立的 `BBS-PoseCurve-fsr-1.21.1/` NeoForge 插件工程，
  并保持清晰、可识别的 BBS-PoseCurve 身份。
- NeoForge 模组 ID 使用 `bbs_posecurve`，发布版本使用 `2.8.3-1.21.1`，
  依赖 BBSFSR `0.0.11` 或更高兼容版本。
- 逐项迁移旧版功能；只有在 1.21.1 BBSFSR 已原生实现同等行为时，才允许删除
  对应补丁，并需在设计文档中记录依据。
- 将 Fabric 入口、元数据、Mixin 配置和访问声明适配为 NeoForge 1.21.1 可用形式。
- 针对 `new/` 中已变化的类名、方法签名、字段和 UI 生命周期进行实际适配，
  不以跳过编译或禁用全部 Mixin 作为完成方式。
- 插件工程只依赖本仓库 `new/` 产出的 1.21.1 BBSFSR，不复制 BBSFSR 主体代码。
- 保留旧版参考目录不变。

## Acceptance Criteria

- [x] 新插件工程可在当前仓库环境中独立完成 Gradle 构建并产出 jar。
- [x] NeoForge 能识别插件元数据，Mixin 配置和入口类指向有效实现。
- [x] 所有迁移后的 Java 源文件针对 `new/` 当前类和方法成功编译。
- [x] 旧版主要功能均有迁移实现或有证据表明已由 BBSFSR 原生覆盖。
- [x] 不包含用于掩盖迁移缺失的全局 Mixin 禁用、空实现或宽泛异常吞噬。
- [x] 对高风险注入点和核心纯逻辑补充可执行的验证，至少覆盖构建与源码契约检查。
- [x] `BBS-PoseCurve-fsr` 旧版参考源码保持不变，其他任务的现有修改不受影响。

## Out of Scope

- 修改 BBS-PoseCurve 的产品功能或重新设计其界面。
- 将插件功能直接合并进 `new/` 主工程。
- 同时维护 Fabric 1.20.1 与 NeoForge 1.21.1 的单一多加载器工程。
- 处理与本插件迁移无关的 BBSFSR 主工程缺陷。

## Open Questions

- 无阻塞问题。仓库检查确认 `new/` 没有 BBS-PoseCurve 的同名或同等完整实现；
  迁移按完整功能保留执行。

## Planning Decisions

- 保留旧版 `bbsplus.example.bbsplus` Java 包名和 `bbsplus$` Mixin 成员前缀，
  避免与跨版本接口适配无关的大范围重命名。
- 删除无行为的 Fabric 客户端入口和数据生成入口；公共入口改为 NeoForge `@Mod`。
- 空的 access widener 不迁移；若编译发现真实访问需求，优先使用既有 Mixin
  accessor/shadow，仅在确有必要时添加 NeoForge access transformer。
- 迁移完成必须包含逻辑测试、严格的 Mixin 目标类加载验证和 jar 内容审计；
  仅“编译成功”不视为全部验证完成。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.

## Verification Result

- 2026-08-18: `clean testPoseCurveLogic build` passed and produced the
  `2.8.3-1.21.1` addon jar.
- 2026-08-18: strict client verification passed with 24 unique BBS target
  classes and three required resources loaded; all 26 addon Mixins applied.
- The jar contains NeoForge metadata and both Mixin configs, contains no Fabric
  metadata or bundled `mchorse/bbs_mod/**` classes, and uses the same BBSFSR jar
  bytes in the build dependency and runtime installation.
- Manual Film editor interaction smoke testing remains a residual UI risk:
  opening curves, dragging handles, selecting pose components, restoring the
  viewport through undo/redo, and switching replay/track state.
