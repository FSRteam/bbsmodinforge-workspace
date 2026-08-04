# bbs-snow 收尾修复：premiere 按钮 / 纹理调色 / 跟随轨道 + S9 流体构建验收

## Goal

上一个会话（GPT）在 `bbs-snow-addon/` 工作区留下了一批**未提交、未构建验证**的改动：premiere 模块移植（22 个文件）、S9 流体全部 8 个文件、跟随轨道（follow-orbit）2 个 mixin、纹理调色/白化的 `renderHybrid` 修复与 `UIPoseFormEditor` 漂移修正。用户实测反馈三个功能「没效果」：

1. **Premiere 导出**：设置中启用后，监视器下方图标栏**不出现导出图标**，退出重进游戏无效。
2. **纹理调色 / 纹理白化**：大多数回放的模型相关轨道中，改数值或拖颜色滑块后**画面无变化**。
3. **跟随轨道**：功能尚未实现/不完整，GPT 只完成了一部分。

本任务先把工作区现状**编译验证**（真实 exit code），确认「没效果」是「改动没进 jar」还是「逻辑缺口」，再逐项修复到游戏内可见、可用，最后按 S7/S9 implement.md 的收尾项提交并回填 Trellis 记录。

## 工作区现状（2026-08-02 盘点）

addon 仓库（`bbs-snow-addon/`，独立 git 仓库）未提交改动：

| 组别 | 文件 | 对应用户反馈 |
|---|---|---|
| premiere 模块移植（新增 22 文件） | `premiere/**`、`PremiereExportButtonInstaller`、`BBSSnowModClient` 调用、`en_us/zh_cn` premiere 键、`build.gradle` 新 core 类断言、`gradle.properties` core 升到 `0.0.11` | 反馈 1 |
| 纹理调色/白化修复 | `ModelTextureGradeShader.selectCurrent`（新增）、`ModelInstanceTextureGradeMixin` 新增 `renderHybrid` 的 `BBSShaders.getModel()` Redirect、`UIPoseEditorMixin`/`UIModelPoseEditorAccessor` 改 `UIPoseFormEditor`、`UIPoseKeyframeFactoryMixin` 布局注入重写 | 反馈 2 |
| 跟随轨道（新增 3 文件 + 改动） | `OrbitFilmCameraControllerFollowMixin`、`UIFilmControllerFollowOrbitMixin`、`UIFilmPreviewMixin` 滚轮缩放、`ReplayUIKeys.FOLLOW_TRACK`、`CMLSettings.followOrbitMode` | 反馈 3 |
| S9 流体（新增 8 文件） | `FluidForm`、`FluidFormRenderer`(36k)、`FluidController`、`FluidSimulation`、`FluidMeshBudget`、`UIFluidForm`、`UIFluidFormPanel`、`FluidUIKeys`；`BBSSnowAddon` 注册 `bbs:fluid`；`BBSSnowModClient` 注册 renderer/editor/category | S9 收尾 |
| 渲染状态 | `FormTranslucentVertexBufferCommandMixin`（新增，PBR `RenderStateSnapshot`） | S3/S9 共用 |

关键疑点：用户测试用的 jar 是否包含上述改动未知（工作区未提交，最近提交是 `75854ef` S7/S8）。`new/build/libs/BBS_FSR_beta_v0.0.11.jar` 已存在，core 版本断言已对齐。

## Requirements

- R1 **构建验证先行**：`cd bbs-snow-addon && ./gradlew verifyBbsCoreJar build` 必须拿到真实 exit code 0（禁止管道吞退出码，遵守构建红线：管道会吞掉退出码造成假绿，必须真实 exit code + 日志双确认）。编译不过先修编译。
- R2 **Premiere 按钮可见**（反馈 1）：启用 `premiere_export_enabled` 后，Film 面板监视器下方图标栏出现 Premiere 导出图标；重进游戏后依然出现（installer 的 tick 轮询 + WeakHashMap 幂等逻辑需在真实 dashboard 生命周期下验证）；点击能弹出设置/触发导出流程，不出现静默失败（日志可查）。
- R3 **纹理调色/白化生效**（反馈 2）：在模型形态（ModelForm）及骨骼级（pose/关键帧宿主）改 tint 颜色或 whiten 滑块后，回放中的模型渲染**立即**可见变化；VAO 与 hybrid 两条渲染路径都要覆盖（`renderHybrid` 的 Redirect 必须真正命中 FSR 当前 `ModelInstance.renderHybrid` 字节码）。
- R4 **跟随轨道完成**（反馈 3）：跟随轨道模式（pov mode 6）在 Film 控制器相机菜单中出现、可选中、相机实际跟随 replay 渲染枢轴；滚轮缩放可用；模式状态按 `followOrbitMode` 设置持久化；从跟随模式切回普通模式后不再残留附着（`UIFilmControllerFollowOrbitMixin` 的 stop/toggleAttachment 逻辑）。
- R5 **S9 流体收尾**：`bbs:fluid` form 在 Extra 分类出现、可选中、编辑器面板可打开、三种 mode（FULL_OCEAN / WATER_DROP / PROCEDURAL）在世界与 UI 中渲染不崩；`BBSSnowRuntimeVerification` 的 fluid 段落通过。
- R6 **验收与记录**：strict 客户端（`bbs_snow.runtimeVerification=true` + `exitAfterVerification`）全绿；无法自动证明的真实交互项登记到 S10 `s*-manual-acceptance.md`；addon 仓库按文件组提交（不 `git add -A` 夹带无关改动）；回填 S7/S9 implement.md 勾选项与本任务记录。

## Constraints

- `new/` 主 mod 源码**零改动**（父任务 AC7）；若发现 FSR 侧缺陷导致 addon 无法实现，单独提出由用户决定。
- 不使用 `git add -A`；root 仓库的 `new/`、旧源码树、他人并行改动不得纳入提交。
- 裸 JVM 单测不得触发 `BBSMod` 静态初始化。
- required mixin 启动失败必须修描述符，不得用 `require=0` 绕过。
- 构建/strict run 前确认没有正在使用对应 jar 的客户端进程。

## Acceptance Criteria

- [ ] AC1 `verifyBbsCoreJar build` 真实 exit code 0，产出新 addon jar，jar 内含 premiere/fluid/follow-orbit 全部类与 mixin 配置的新条目。
- [ ] AC2 strict 客户端日志：全部 mixin（含新增 `FormTranslucentVertexBufferCommandMixin`、`OrbitFilmCameraControllerFollowMixin`、`UIFilmControllerFollowOrbitMixin`）apply 成功、无 injection failure；`BBSSnowRuntimeVerification` 全绿并正常 `client.stop()`。
- [ ] AC3 游戏内：Premiere 图标出现且可用（R2），附 strict run 或人工验证记录。
- [ ] AC4 游戏内：模型轨道 tint/whiten 调整即时可见（R3），覆盖 VAO 与 hybrid 路径。
- [ ] AC5 游戏内：跟随轨道模式可选、可跟随、可缩放、可退出（R4）。
- [ ] AC6 游戏内：fluid form 三模式渲染不崩、编辑器可用（R5）；视觉项登记 S10。
- [ ] AC7 addon 仓库分组提交完成；S7/S9 implement.md 勾选与 validation record 更新；本任务归档前 root 仓库 `git diff new/` 为空。

## Out of Scope

- 不新增 bbs_snow 在 1.20.1 上没有的功能。
- S10 的整体集成验收（本地化对称审计、双 mod 完整冒烟矩阵、S1–S9 总收尾）不在本任务，但本任务的产出是 S10 的输入。
- `bbs-premiere-export/` 独立 addon 与本次 addon 内 premiere 模块的去重/合并决策（若用户要求合并，另开任务）。
