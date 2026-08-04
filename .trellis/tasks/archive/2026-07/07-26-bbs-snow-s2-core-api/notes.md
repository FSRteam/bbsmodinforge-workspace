# S2 执行记录

## 基线与构建

- S1 已归档；addon 基线包含 `cad8464`。
- `new/` 在本任务开始前已有多个并行任务的未提交改动。S2 未编辑、清理或还原其中任何源码。
- S2 构建期间 `verifyBbsCoreJar` 曾两次发现核心源码晚于 jar；复用并行核心构建生成的
  `new/build/libs/BBS_FSR_beta_v0.0.10.jar`，最终严格校验通过。
- 最终 `bbs-snow-addon/gradlew.bat build --console=plain` 退出码为 0。

## 实现与静态验收

- 迁移 common API 10 个、client API 13 个、common Mixin 12 个、client accessor Mixin 5 个。
- 49 个源文件做命名空间归一化对比，45 个完全一致；4 个差异均在设计范围内：
  `ValueSectionHeader` 入口类名修正、`ParticlePlusParticle` Fabric dist 注解移除、
  `TransformMixin`/`PoseTransformMixin` 的 `autoLerp` 注入删除。
- 6 份 JSON 均可解析；`en_us`/`zh_cn` 各 43 个键且键集合一致。
- 源码无 `bbs_fslovecml`、`net.fabricmc`、`autoLerp`、`refmap`、`PROBE` 残留。
- jar 含 23 个 API 类、17 个 Mixin、4 份 Mixin config、2 份 strings；不含
  `mchorse/bbs_mod/**` 或 `UIBaseMenuProbeMixin`。

## 运行时 Gate 2

- 初次自然启动只加载 12/17 个 S2 Mixin；其余目标为懒加载路径。
- 开发期运行时验证器通过系统属性启用，强制加载全部 17 个目标，并验证：
  action timeline 保存/copy/interpolate/playback，pivot 与 pose texture/grade 的
  保存/copy/interpolate，Model/ModelGroup 传播与 reset，ModelForm Molang/grade/PBR
  round-trip，以及设置/标题/UI factory/翻译键。
- ActionPlayback 首次断言把默认 `loop=true` 的 20 tick 动画误判为 28；原实现正确结果为
  `28 % 20 = 8`，修正的是验证器而非生产 Mixin。
- L10n 首版把资源 namespace 放入 `Link.path`，修正为独立 source `bbs_snow`。进一步从完整
  控制台发现 provider 早于 source pack 注册会在首次 reload 打印 missing-asset；最终改为
  `RegisterL10nEvent` 只捕获实例，client-started 按 source pack -> provider -> reload 激活。
- 最终严格 `runClient` 退出码 0：17 个 S2 Mixin apply、common/client addon 各 accepted 与
  bridged 1 次、运行时验证通过 1 次、翻译资源缺失 0、关键注入/类加载失败 0。
- 证据：`bbs-snow-addon/runs/client/logs/latest.log`，最终运行时间约 2026-07-27 02:40。

## 规范结论

- `.trellis/spec/new/backend/external-addon-integration-contract.md` 已补充打包资源契约：
  独立 addon 必须使用 `InternalAssetsSourcePack(modId, prefix, internalPrefix, ownerClass)`；
  L10n 的 `Link.source` 必须等于 pack prefix，且 provider 只能在 pack 注册后激活。

