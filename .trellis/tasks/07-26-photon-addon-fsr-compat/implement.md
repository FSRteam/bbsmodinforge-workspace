# 执行清单：BBS Photon Addon → BBSFSR

前置阅读：`prd.md` → `design.md` → `research/upstream-api-surface.md` §2/§3（API 核对表与签名差异）→
`research/fsr-addon-protocol.md` §3（落地通道汇总表）。

**红线**：验证一律取真实 exit code（`echo REAL_EXIT=$?`），**禁止**用管道结尾（`| tail`、`| grep`）
判断成败 —— 本仓库有过 `gradlew | tail` 假绿事故。每次构建同时确认日志里出现 `BUILD SUCCESSFUL`
且无 `error:`。

**上游快照**：会话 scratchpad `upstream-photon/`（`git clone --depth 1` 于 2026-07-26）。
若已失效，重新 `git clone https://github.com/ElGatoPro300/bbs-photon-addon.git`。
**阶段 0 第一步就是把上游 commit 记进 README**，之后 scratchpad 可弃。

**前置条件**：`new/build/libs/BBS_FSR_beta_v0.0.10.jar` 存在。若不存在，先
`cd new && ./gradlew jar`（真实 exit code 判定）。

---

## 阶段 0：工程骨架落地（无逻辑改动）

- [x] 0.1 `git -C <scratchpad>/upstream-photon rev-parse HEAD` 记下上游 commit。
- [x] 0.2 把上游整树复制到仓库根 `bbs-photon-addon/`（含 `gradle/wrapper`、`gradlew*`、
      `settings.gradle`、`LICENSE.md`、`src/`）。**不复制** `.git/`。
- [x] 0.3 删除 `bbs-photon-addon/src/main/resources/fabric.mod.json`（PRD R2.6）。
- [x] 0.4 写 `bbs-photon-addon/README.md`：注明源自上游 `ElGatoPro300/bbs-photon-addon@<commit>`、
      本工程面向 BBSFSR（非 BBS CML）、MIT 许可延续、构建前提（`bbs_fsr_jar` 属性）。
- [x] 0.5 确认 `.gitignore` 覆盖 `build/`、`.gradle/`、`run*/`（上游自带 `.gitignore`，核对即可）。

**验证**：`ls bbs-photon-addon` 结构符合 `design.md` §2；无 `fabric.mod.json`。

**回滚点 A**：`rm -rf bbs-photon-addon/`。

---

## 阶段 1：构建文件重写（先让依赖能解析）

按 `design.md` §3 重写：

- [x] 1.1 `gradle.properties`：
      `neoforge_version=21.1.222`（对齐 BBSFSR `neo_version`）、`mod_id=bbs_photon_addon`、
      `mod_group_id=elgatopro300.bbsphoton`、`mod_version`（延用上游 `1.1.1` 或标 `-fsr` 后缀）、
      `photon_version`、`ldlib2_version`、新增 `bbs_fsr_jar=../new/build/libs/BBS_FSR_beta_v0.0.10.jar`。
      删除 `connector_version`、`ffapi_version`、`spruceui_version`、`lambdynamiclights_version`。
- [x] 1.2 `build.gradle` plugins：删 `org.sinytra.adapter.userdev`，保留
      `java-library` / `maven-publish` / `net.neoforged.moddev`。
- [x] 1.3 `build.gradle` dependencies：只留
      `compileOnly files(project.bbs_fsr_jar)` + `photon-editor` + `ldlib`。
      删 `connector`、`forgified-fabric-api`、`bbs-cml-edition`、`sodium`、`iris`、`watermedia`。
- [x] 1.4 保留 `repositories` 里的 Modrinth maven 与 `flatDir { dirs 'libs' }`（RK2 的本地回退口）；
      删 Sinytra maven。
- [x] 1.5 `processResources` 的 `filesMatching` 去掉 `fabric.mod.json`，只留 `META-INF/neoforge.mods.toml`。
- [x] 1.6 `neoforge.mods.toml`：依赖块改为 `neoforge` / `minecraft` / `bbs`（`ordering="AFTER"`）/ `photon`，
      删除 `connector` 依赖项（PRD R2.7）。

**验证**：
```bash
cd bbs-photon-addon && ./gradlew dependencies --configuration compileClasspath > /tmp/ph1.log 2>&1; echo REAL_EXIT=$?
```
exit 0 且日志中出现 BBSFSR jar 与 photon/ldlib。
若 Modrinth 不可达（RK2），改用 `libs/` 本地 jar 并在此处记录实际做法。

**注意**：此阶段 Java 源码还是上游原样，`compileJava` **预期失败**（`mchorse.bbs_mod.addons.*` 等
6 个缺失类）。不要在这里尝试通过编译。

---

## 阶段 2：addon 注册协议迁移（手术面 A）

按 `design.md` §4：

- [x] 2.1 `BBSPhotonAddon.java`：`extends mchorse.bbs_mod.addons.BBSAddon`
      → `implements mchorse.bbs_mod.events.BBSAddonMod`；去掉 `registerForms` 上的 `@Override`
      （接口无此方法），保留 `@Subscribe` 与方法体。
- [x] 2.2 `BBSPhotonClientAddon.java`：`extends BBSClientAddon` → `implements BBSAddonMod`；
      删除 `registerFormsRenderers` 与 `registerIcons` 两个方法及其 import。
      **实施期偏差**：`registerDashboardPanels` / `registerFormCategories` 的方法体在上游本就是空的，
      保留空订阅只是噪音 → 一并删除，只剩 `registerL10n`。详见 `design.md` §4.3。
- [x] 2.3 `BBSPhoton.java`：删 `import mchorse.bbs_mod.BBS`；构造器改为
      `BBSMod.registerAddon(MOD_ID, BBSPhotonAddon::new)` + 客户端侧
      `BBSMod.registerAddon(MOD_ID + "_client", BBSPhotonClientAddon::new)` 与 `BBSPhotonClient.init()`；
      **删除** `commonSetup` 监听器与全部 Form 兜底注册（PRD R3.5）。
- [x] 2.4 `BBSPhoton.java`：加 `public static final BBSAddonDescriptor DESCRIPTOR`
      常量（addonId / displayName / addonVersion / `capability(FORMS)` / `capability(CLIENT_RENDER)` /
      `capability(CLIENT_UI)` / `capability(SOURCE_PACKS)` / `requiredMod("bbs")` /
      `requiredMod("photon")` / `namespace("bbs_photon")`）。按 `design.md` §4.1，本次**不用它注册**。

**验证**：
```bash
cd bbs-photon-addon && ./gradlew compileJava > /tmp/ph2.log 2>&1; echo REAL_EXIT=$?
```
此时仍会因 `BBSPhotonClient` / `UIPhotonFormPanel` 的 Fabric 引用失败 —— **确认失败原因只剩
`net.fabricmc.*` 与 `RegisterFormsRenderersEvent`/`RegisterIconsEvent` 相关**，
不应再有 `mchorse.bbs_mod.addons` / `mchorse.bbs_mod.BBS` 的报错。

---

## 阶段 3：客户端 bootstrap 重写（手术面 A/B 交界）

`BBSPhotonClient.init()` 按 `design.md` §5 重写。顺序敏感，逐段做：

- [x] 3.1 删 `import mchorse.bbs_mod.BBS`、`addons.AddonInfo`、两个 `net.fabricmc.fabric.api.*`。
- [x] 3.2 addon 注册段：删掉 `BBS.getEvents().register(new BBSPhotonClientAddon())`
      —— 已在阶段 2.3 由 `BBSMod.registerAddon` 承担。
- [x] 3.3 渲染器/面板注册（§5.1）：`init()` 里直接调
      `FormUtilsClient.register(PhotonForm.class, PhotonFormRenderer::new)` +
      `UIFormEditor.register(PhotonForm.class, UIPhotonForm::new)`，从 `CLIENT_STARTED` 块里移出。
- [x] 3.4 图标注册（§5.2）：`Icons.register(new Icon(PhotonFormRenderer.ICON, "bbs:photon", 0, 0, 40, 40, 40, 40))`。
- [x] 3.5 关键帧编辑器扩展（§5.3）—— **实施期改用更优方案**：不覆写 `UIKeyframeFactory`，
      改用 BBSFSR 内建的 `UIStringKeyframeFactory.registerDecorator(...)` 扩展点
      （`UIStringKeyframeFactory.java:18`，BBSFSR 内零调用者，即为 addon 预留）。
      删 `UIPhotonFxKeyframeFactory.java`，新增 `PhotonFxKeyframeDecorator.java`。
      **RK3 时序风险随之消除** —— 不再需要放进 `onClientStarted`，`init()` 里直接注册。
      详见 `design.md` §5.3。
- [x] 3.6 轨道颜色/图标（§5.4）：保留 `UIReplaysEditor` 的 `COLORS`/`ICONS` 反射注入；
      catch 块改为 `LOGGER.warn` + 说明「轨道退化为默认样式」，不抛出（PRD R4.6 / RK1）。
- [x] 3.7 tick 看门狗（§6）：`ClientTickEvents.END_CLIENT_TICK` → `BBSClientApi.onEndClientTick`；
      `START_CLIENT_TICK` → `BBSClientApi.onStartClientTick`。回调体不动
      （`checkCleanup()` / `onClientTickStart()`，PRD R6.5）。
- [x] 3.8 启动钩子（§6）：`ClientLifecycleEvents.CLIENT_STARTED` → `BBSClientApi.onClientStarted`。
- [x] 3.9 `onClientStarted` 块内保留：两个 `InternalAssetsSourcePack` 注册、L10n reload、
      Extra 分类注入（§5.5，原因见上游注释：必须晚于 `BBSResources.init()`）。
      **实施期修正**：上游的 `getFormCategories().getExtraForms().getExtraCategory().addForm(...)`
      三级链在 BBSFSR 不存在，改用专用公开 API `FormCategories.addExtraForm(Form)`
      （`FormCategories.java:80`，且额外做了 `markDirty()`）。详见 `design.md` §5.5。
- [x] 3.10 **删除**（§5.6）：整段 `AddonInfo` + `BBSModClient.registerAddon(info)`；
      整段 `getResourceAsStream` 图标存在性验证（上游临时诊断代码）。

**验证**：
```bash
cd bbs-photon-addon && ./gradlew compileJava > /tmp/ph3.log 2>&1; echo REAL_EXIT=$?
```
预期只剩 `UIPhotonFormPanel` 的 `FabricLoader` 报错。

---

## 阶段 4：面板的 Fabric 依赖替换

- [x] 4.1 `UIPhotonFormPanel.java`：删 `import net.fabricmc.loader.api.FabricLoader`；
      两处 `FabricLoader.getInstance().getGameDir().toFile()`（原 `:71` 打开文件夹、
      `:169` 扫描 FX 目录）→ `BBSMod.getGameFolder()`（`design.md` §6）。
      `BBSMod` import 已存在，无需新增。
- [x] 4.2 确认 `ldlib2/assets/photon/fx` 相对路径语义不变（相对游戏根目录）。

**验证**：
```bash
cd bbs-photon-addon && ./gradlew compileJava > /tmp/ph4.log 2>&1; echo REAL_EXIT=$?
```
**此处必须 exit 0**。若 `PhotonFormRenderer` / `UIPhotonForm` / `PhotonForm` 报错，
说明 `design.md` §7 的签名核对有遗漏 —— 逐点记录到 `research/` 并处理，不要靠改签名硬凑。

**实测结果**：`REAL_EXIT=0`、`BUILD SUCCESSFUL in 11m 22s`、零 `error:`。
§7 的签名核对结论全部得到验证 —— `PhotonFormRenderer`(834 行) / `PhotonForm` /
`UIPhotonForm` 确实零代码改动即可编译。
**但到这一步前先换掉了构建插件**：moddev 在本环境不可用，改为 NeoGradle，见 `design.md` §3.2b。

---

## 阶段 5：完整构建与静态验收

- [x] 5.1 `cd bbs-photon-addon && ./gradlew build > /tmp/ph5.log 2>&1; echo REAL_EXIT=$?`
      → exit 0 + 日志含 `BUILD SUCCESSFUL` + 无 `error:`（AC1）。
- [x] 5.2 AC2 grep（三项必须零命中）：
      `rg "net\.fabricmc\." bbs-photon-addon/src`、
      `rg "mchorse\.bbs_mod\.addons\." bbs-photon-addon/src`、
      `rg "mchorse\.bbs_mod\.BBS;" bbs-photon-addon/src`
- [x] 5.3 AC3：`neoforge.mods.toml` 依赖项为 `neoforge`/`minecraft`/`bbs`/`photon`，无 `connector`；
      `fabric.mod.json` 不存在。
- [x] 5.4 AC4：`build.gradle` 无 `sinytra`、无 `bbs-cml-edition`，BBSFSR 依赖走 `bbs_fsr_jar`。
- [x] 5.5 确认产出 jar 存在（`bbs-photon-addon/build/libs/*.jar`）且**不含** BBSFSR 类
      （`compileOnly` 生效的证据）：`unzip -l` 查无 `mchorse/bbs_mod/`。

---

## 阶段 6：交用户冒烟（本会话无法自动验证，RK5）

产出安装说明并交付，列明 AC5–AC8 的验证步骤：

- [x] 6.1 冒烟包：把 `bbs-photon-addon/build/libs/*.jar` 与所需环境
      （BBSFSR + Photon Editor NeoForge 1.21.1 + LDLib）写成一段安装说明。
- [ ] 6.2 AC5：启动无崩溃；伪装选择器 Extra 分类里能看到并创建 Photon 伪装。
- [ ] 6.3 AC6：能选 FX 文件；UI 预览与世界渲染都出粒子；`paused` 开关生效。
- [ ] 6.4 AC7：Film 时间轴 `photon_fx` 轨道可编辑；轨道有专属颜色/图标
      （若为默认样式 → RK1 反射失效，查日志 warn）；关闭编辑器后特效被清理、不残留。
- [ ] 6.5 AC8 回归：BBS 原有的字符串类关键帧轨道（非 `photon_fx`）编辑控件正常，不空白。
- [ ] 6.6 日志检查：搜 `bbs-photon-addon` 相关 WARN/ERROR；
      `BBSMod.getAddonDiagnostics()` 或注册诊断中能看到 addon（PRD R3.4）。

---

## 阶段 7：收尾

- [x] 7.1 按 Trellis 3.1 跑质量验证，3.3 更新 spec（若本任务产出了可复用的
      「外部 NeoForge 模组如何接入 BBSFSR addon 协议」知识，写进 `.trellis/spec/`）。
- [ ] 7.2 3.4 提交 —— **计划有误，已更正**：workspace 仓库的 `.gitignore` 首行是 `/*`
      （白名单式），注释写明「This repository tracks the shared workspace control plane only」。
      `new/`、`bbs-structure-addon/`、`bbs-web-remote/` 均为 0 跟踪文件，代码目录本就不进
      这个仓库（`new/` 是独立 repo，见 `workspace-manifest.json`；另两个 addon 目录无任何
      版本控制）。因此：
      - 可提交的只有 `.trellis/` 下的任务文档与新增 spec。
      - `bbs-photon-addon/` 保持与 `bbs-structure-addon` 一致的现状（不纳入 workspace 仓库）。
        若需要版本控制，应由用户决定是否 `git init` 成独立 repo —— 不擅自修改 workspace 的
        `.gitignore` 白名单。
- [ ] 7.3 若冒烟发现运行期缺陷，回 Phase 2 修复，不在本清单里就地扩张。

---

## 用户决定（2026-07-26）

- **提交时机**：先冒烟，AC5–AC8 通过后再提交。若冒烟发现运行期缺陷，按 7.3 修完一并提交。
- **代码版本控制**：`bbs-photon-addon/` 保持现状（无版本控制），与 `bbs-structure-addon` /
  `bbs-web-remote` 一致。不 `git init`，不改 workspace 的 `.gitignore` 白名单。

## 交付物

- `bbs-photon-addon/build/libs/bbs-photon-addon-1.1.1-fsr.jar`（102,804 字节）
- 重建命令：`cd bbs-photon-addon && ./gradlew build`
  （首次配置约 11 分钟，之后约 2.5 分钟；勿加 `--offline`，Gradle 不认跨工程缓存元数据）

## 回滚点汇总

| 点 | 时机 | 操作 |
|---|---|---|
| A | 阶段 0 后 | `rm -rf bbs-photon-addon/` |
| B | 阶段 1 后 | `git checkout bbs-photon-addon/{build.gradle,gradle.properties}` 或重跑阶段 1 |
| C | 阶段 2–4 每阶段后 | 各阶段独立、按文件分组，可单文件 `git checkout` 回退 |

全程不触碰 `new/`（PRD R1.4 / `design.md` §9），最坏情况整目录删除即完全回滚。
