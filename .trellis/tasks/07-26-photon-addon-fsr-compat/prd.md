# BBS Photon Addon 兼容 BBSFSR

## Goal

把 `https://github.com/ElGatoPro300/bbs-photon-addon`（当前仅兼容 BBS CML Edition，靠 Sinytra Connector
在 NeoForge 上跑 Fabric 版 BBS）的源码改造为**直接兼容本仓库 `new/` 的 BBSFSR**（原生 NeoForge 1.21.1，
`mod_id=bbs`），落地为仓库根目录下的独立 Gradle 工程 `bbs-photon-addon/`。

改造后的 addon 让用户能在 BBSFSR 的伪装（Form）系统里选用 Photon 粒子特效作为一种 Form，
并在 Film 时间轴上用 `photon_fx` 轨道驱动特效切换。

## 背景（研究结论，详见 `research/`）

- 上游共 8 个 Java 文件 / 1537 行；其依赖的 46 个 BBS API 中 **40 个在 BBSFSR 里包名与位置完全一致**。
- 真正的适配面集中在两处：**addon 注册协议**（上游 `addons.BBSAddon` / `BBSClientAddon` / `AddonInfo`
  在 BBSFSR 不存在，BBSFSR 用 `events.BBSAddonMod` + `api.addon.BBSAddon` 两代协议）
  与 **Fabric 客户端事件**（BBSFSR 已备好 `api.client.BBSClientApi` 门面一一对应）。
- 占总量 54% 的 `PhotonFormRenderer`（834 行）所依赖的 BBS API 全部存在且签名兼容。

## Requirements

### R1 工程落地

- R1.1 在仓库根新建独立 Gradle 工程目录 `bbs-photon-addon/`，与 `new/`、`bbs-structure-addon/` 平级。
- R1.2 保留上游的包名 `elgatopro300.bbsphoton`、`mod_id=bbs_photon_addon`、资源命名空间 `bbs_photon`，
  以便日后从上游同步改动时 diff 可读。
- R1.3 保留上游 `LICENSE.md`（MIT）与出处标注；注明本工程源自上游哪个 commit。
- R1.4 不修改 `new/`（BBSFSR）的任何源码。addon 只消费 BBSFSR 的公开 API。
  若发现某项功能确实缺公开通道，先记录，不擅自改 BBSFSR。

### R2 构建

- R2.1 依赖 BBSFSR 本地产物（`new/build/libs/BBS_FSR_beta_v0.0.10.jar`）编译，
  不依赖 `maven.modrinth:bbs-cml-edition`。
- R2.2 移除 Sinytra 相关依赖与插件（`org.sinytra.adapter.userdev`、`connector`、`forgified-fabric-api`）
  —— BBSFSR 是原生 NeoForge，不需要 Fabric 兼容层。
- R2.3 移除与本 addon 功能无关的依赖（`sodium`、`iris`、`watermedia`）。
- R2.4 保留 `photon-editor`（`mc1.21.1-2.1.4-neoforge`）与其依赖 `ldlib`。
- R2.5 平台版本与 BBSFSR 对齐：Minecraft 1.21.1 / Java 21 / NeoForge `21.1.x`。
- R2.6 删除 `src/main/resources/fabric.mod.json`（不再走 Connector 路线）。
- R2.7 `neoforge.mods.toml` 的依赖声明改为：`neoforge` + `minecraft` + `bbs` + `photon`，移除 `connector`。

### R3 addon 注册协议迁移

- R3.1 `BBSPhotonAddon` 从 `extends mchorse.bbs_mod.addons.BBSAddon` 改为
  `implements mchorse.bbs_mod.events.BBSAddonMod`，保留 `@Subscribe registerForms(RegisterFormsEvent)`。
- R3.2 `BBSPhotonClientAddon` 从 `extends BBSClientAddon` 改为 `implements BBSAddonMod`；
  删除 BBSFSR 不存在的 `RegisterFormsRenderersEvent` / `RegisterIconsEvent` 订阅，
  改走静态注册通道（见 R4）。
- R3.3 主入口 `BBSPhoton` 在 mod 构造期调用 `BBSMod.registerAddon(String, Supplier<BBSAddonMod>)` 注册
  —— 这是 `BBSAddonMod` javadoc 为外部 NeoForge 模组指定的入口，且注册窗口会在构造期后关闭。
- R3.4 addon 需在 BBS 的诊断中可见。经 `design.md` §4.1 论证：走 API 1.x 并以 addonId 注册即可
  被 `BBSAddonCollector.getRegistrationDiagnostics()` 收录，**不做 API 2.0 双注册**；
  额外定义一个 `BBSAddonDescriptor` 常量备用（供 `BBSClientApi` 的 descriptor-aware 重载将来使用），
  本次不参与注册。
- R3.5 清理上游的多重兜底注册（`BBSPhoton` 构造期 + `commonSetup` 各注册一次 Form、
  `BBSPhotonClient` 里再手动注册一次）—— 保留单一确定路径，避免重复注册噪音。

### R4 客户端注册通道替换

- R4.1 Form 渲染器：`FormUtilsClient.register(PhotonForm.class, PhotonFormRenderer::new)`。
- R4.2 Form 编辑面板：`UIFormEditor.register(PhotonForm.class, UIPhotonForm::new)`。
- R4.3 图标：`Icons.register(new Icon(...))` 替代 `RegisterIconsEvent`。
- R4.4 source pack / L10n / Extra 表单分类注入：BBSFSR 通道与上游完全一致，保持原逻辑。
- R4.5 `photon_fx` 关键帧编辑器覆写：`UIKeyframeFactory.FACTORIES` 在 BBSFSR 已私有化，
  改用 `UIKeyframeFactory.register(KeyframeFactories.STRING, factory)`，
  并保证非 `photon_fx` 轨道仍落回默认字符串编辑控件（不能因覆写而破坏 BBS 原有的字符串关键帧编辑）。
- R4.6 `photon_fx` 轨道颜色与图标：`UIReplaysEditor.COLORS` / `ICONS` 在 BBSFSR 仍是私有字段且无公开
  注册通道，保留反射注入，但反射失败必须降级为「轨道显示为默认样式」而非抛错中断。

### R5 Fabric 事件替换

- R5.1 `ClientLifecycleEvents.CLIENT_STARTED` → `BBSClientApi.onClientStarted(Consumer<Minecraft>)`。
- R5.2 `ClientTickEvents.START_CLIENT_TICK` → `BBSClientApi.onStartClientTick(...)`。
- R5.3 `ClientTickEvents.END_CLIENT_TICK` → `BBSClientApi.onEndClientTick(...)`。
- R5.4 `UIPhotonFormPanel` 里通过 `FabricLoader` 定位 Photon 配置目录的逻辑，
  换为 NeoForge 等价物（`FMLPaths` / `ModList`）。
- R5.5 改造后源码中**不得残留任何 `net.fabricmc.*` import**。

### R6 行为对等

- R6.1 PhotonForm 可在伪装选择器的 Extra 分类中被找到并创建。
- R6.2 Form 编辑面板能选择 Photon FX 文件、能切换 `paused`。
- R6.3 特效在世界中与 UI 预览中都能渲染（`render3D` + `renderInUI` 两条路径）。
- R6.4 Film 时间轴上 `photon_fx` 轨道可编辑，轨道有专属颜色/图标（反射成功时）。
- R6.5 保留上游的清理看门狗：渲染器不再活跃时停止 Photon 特效，
  避免 Photon 引擎全局状态被污染（上游注释明确标注了这是必要防护）。

## Non-Goals（明确不做）

- N1 **不保留对 BBS CML Edition 的兼容**。本次改造目标是单一兼容 BBSFSR；不建双兼容适配层
  （双兼容需要两套 addon 基类 + 全反射，复杂度远超本任务价值）。
- N2 **不移植「Addons 展示面板」注册**。BBSFSR 客户端不存在 `AddonInfo` 及任何 addon 展示面板
  （已核对：`src/client/java` 下零引用），上游 `BBSModClient.registerAddon(AddonInfo)` 那段代码删除。
  addon 身份信息改由 `BBSAddonDescriptor` 承载（注：descriptor 无 authors / description / icon /
  homepage 字段，这部分展示信息在 BBSFSR 下无处安放，属已知功能缺口）。
- N3 不改动 `new/`（BBSFSR）源码。
- N4 不升级 Photon Editor 版本、不修改 Photon 侧代码。
- N5 不做 addon 的发布/打包分发流程（CurseForge/Modrinth）。

## Constraints & Assumptions

- C1 运行时前提：用户环境需同时装有 BBSFSR、Photon Editor（NeoForge 1.21.1 版）及其依赖 LDLib。
- C2 编译依赖 `new/build/libs/BBS_FSR_beta_v0.0.10.jar` 已存在（已确认，7.9 MB，2026-07-26 构建）。
  若 BBSFSR 版本升级，addon 需重新指向新 jar。
- C3 `photon-editor` / `ldlib` 通过 Modrinth Maven 解析，需要网络可达 `api.modrinth.com`。
  若网络受限，需改为本地 jar（`libs/` + `flatDir`）—— 此为已知风险，见 `design.md` 风险节。
- C4 **已确认（2026-07-26，用户决定）**：只兼容 BBSFSR，放弃 CML 兼容（见 N1）。
- C5 **已确认（2026-07-26，用户决定）**：新建独立目录，名为 `bbs-photon-addon`，
  沿用 `bbs-structure-addon` / `bbs-web-remote` 命名风格。

## Acceptance Criteria

- [ ] AC1 `bbs-photon-addon/` 工程存在，`./gradlew build` 真实 exit code 为 0，编译零 error。
- [ ] AC2 改造后源码全局 grep 无 `net.fabricmc.` 命中，无 `mchorse.bbs_mod.addons.` 命中，
      无 `mchorse.bbs_mod.BBS;`（旧 `BBS` 门面）命中。
- [ ] AC3 `neoforge.mods.toml` 依赖项为 `neoforge` / `minecraft` / `bbs` / `photon`，无 `connector`；
      `fabric.mod.json` 已删除。
- [ ] AC4 `build.gradle` 无 Sinytra 插件与依赖，无 `bbs-cml-edition`，BBSFSR 依赖指向本地 jar。
- [ ] AC5 冒烟（需用户在游戏内执行）：BBSFSR + Photon 环境下启动无崩溃，
      伪装选择器 Extra 分类中可见并可创建 Photon 伪装。
- [ ] AC6 冒烟：Photon 伪装能选择 FX、UI 预览与世界渲染均出现粒子，`paused` 生效。
- [ ] AC7 冒烟：Film 时间轴 `photon_fx` 轨道可编辑；关闭编辑器后 Photon 特效被正确清理
      （不残留、不影响后续特效）。
- [ ] AC8 回归：BBS 原有的字符串类关键帧轨道（非 `photon_fx`）编辑行为不变（R4.5 的降级正确）。

## Open Questions

- ~~Q1 C4/A1：确认只兼容 BBSFSR、不保留 CML 双兼容？~~ → **已确认：只兼容 BBSFSR**（2026-07-26）
- Q2 上游 `PhotonFormRenderer` 大量反射访问 Photon 内部（`EntityEffectExecutor` / `FXRuntime` /
  `Emitter`）。这部分与 BBS 无关，只要 Photon 版本不变就应原样可用 —— 计划原样保留，
  仅在编译报错时逐点处理。
