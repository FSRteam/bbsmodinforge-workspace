# S2 执行计划

## 0. 前置与基线

- [x] 确认 S1 已归档，`bbs-snow-addon` HEAD 至少包含 `cad8464`。
- [x] 记录 `bbs-snow-addon` 与 `new/` 的 git status；不覆盖其他任务改动。
- [x] 运行 `verifyBbsCoreJar`。若 jar 旧于 `new/src`，先确认当前 `new/` 快照可构建，再重建 jar；
      不通过降低校验来绕过。
- [x] 保存 S1 正向/负向日志位置，后续替换 probe 后用真实 Mixin 证据取代。

## 1. API 契约

- [x] 迁移 10 个 common API 到 `mchorse.bbs_snow.api`。
- [x] 迁移 13 个 client API；删除 Fabric dist 注解。
- [x] 全量重命名 duck 方法/常量为 `bbs_snow$` / `BBS_SNOW_*`。
- [x] 搜索并确认 API 目录无 `bbs_fslovecml`、`net.fabricmc`。

## 2. common 数据依赖

- [x] 迁移 5 个 PBR 数据/关键帧工厂类，不迁移 renderer/UI/shader。
- [x] 迁移 `ActionTimelineEvaluator`、`PresetDataOperations`。
- [x] 将内部 namespace 数据键改为 `bbs_snow`，保留 BBS value id。
- [x] 编译一次，先解决纯类型/API 漂移，再接 Mixin config。

## 3. common Mixin

- [x] 分批迁移 action timeline 4 个 Mixin。
- [x] 迁移 settings + preset accessor 2 个 Mixin。
- [x] 迁移 ModelForm/PBR 2 个 Mixin。
- [x] 迁移 model/pose/transform 4 个 Mixin。
- [x] 删除 `TransformMixin` / `PoseTransformMixin` 的 `autoLerp` 注入，逐个核对其余 descriptor。
- [x] 更新 `bbs_snow.mixins.json`，列出恰好 12 个类。

每批后都运行 build；若编译绿但目标签名未实际验证，不勾选运行验收。

## 4. client accessor Mixin

- [x] 将 5 个 accessor 实现放入 `mixin/client/accessor/`。
- [x] 核对 `Films.controllers`、`UIClips.factory/addClip`、`UIClipsPanel.panel/target`、
      `UIDockLayout.panelById/iconById/layoutRoot/setLayoutRoot`、
      `UIFilmPanel.panelById/getCurrentFilmLayoutRoot/setCurrentFilmLayoutRoot/setupEditorFlex`。
- [x] 更新 `bbs_snow.client.api.mixins.json`，列出恰好 5 个类。

## 5. 设置、L10n 与 client addon

- [x] 迁移 `CMLSettings`、`ValueSectionHeader`，保留 20 个 value 的默认值/范围/hidden 状态。
- [x] 新增 `BBSSnowClientAddon`，构造期用 `bbs_snow_client` id 注册。
- [x] 注册 `RegisterL10nEvent` provider、addon source pack 与 client-started reload。
- [x] 注册 `ValueSectionHeader` 的只读居中 label factory。
- [x] 新建 S2 最小 `en_us`/`zh_cn` strings，所有键使用 `bbs_snow` namespace。

## 6. 删除探针并组装 config

- [x] 删除 `UIBaseMenuProbeMixin.java` 和 `bbs_snow.client.mixins.json` 中的 probe 条目。
- [x] 保持 client/iris config 空壳；四份 config 均无 refmap、`defaultRequire: 1`。
- [x] 检查 Mixin package 下不存在 helper/普通类。

## 7. 构建与静态验收

```powershell
.\gradlew.bat build --console=plain
git diff --check
rg -n "bbs_fslovecml|net\.fabricmc|refmap" src
```

- [x] build exit code 0。
- [x] jar 中有 23 API、17 Mixin、四份 config、两份 strings，无 BBS core class。
- [x] `git diff --stat new/` 为空。

注意：`rg` 无匹配返回 1 是预期空结果，必须把“无匹配”和命令故障区分开。

## 8. 运行 Gate 2

```powershell
.\gradlew.bat runClient
```

- [x] common/client addon id 均 accepted + bridged。
- [x] 日志显示 17 个 Mixin 全部 apply，无 critical injection/accessor/class-load 错误。
- [x] 打开设置并记录 `bbs_snow` category、20 values、2 headers 的证据。
- [x] 切换语言验证 en_us/zh_cn，无裸 key。
- [x] 保存、复制、插值并重载 action/pivot/pose texture-grade/ModelForm 数据，记录结果。

## 9. 收尾

- [x] 把实际漂移的 descriptor、运行日志和未移植项写进 `notes.md`。
- [x] 运行 `trellis-check`，复核 spec、构建、运行证据与 `new/` 零改动。
- [x] 按明确文件列表提交 addon 变更，不使用 `git add -A`。

## 回滚点

- API/数据类、common Mixin、accessor Mixin、settings/bootstrap 四批保持可独立回退。
- 单个 Mixin 失败时从 config 暂时撤出并定位，不修改 `defaultRequire`。
- 需要改 `new/` 才能继续时立即停止，按父任务 R5.3 请求用户决策。
