# 08-02 bbs-snow 收尾修复 执行计划

## 结果速览（2026-08-03 收尾）

- 构建：`verifyBbsCoreJar build` EXIT=0（×3），jar 审计通过。
- strict 客户端：`-PrunVerification=1` 全绿（S9 runtime verification passed，targets=61，s7=22/s9=4 mixin，无 injection failure）。
- addon 提交：`9ecba6a` premiere、`dd7976e` texture-grade 修复、`95c991c` follow-orbit、`e781493` fluid S9、`565bc45` strict 修复。
- 关键发现 1：GPT 的三个「没效果」中，**纹理调色/白化在 pose 关键帧宿主下确实有一个真 bug**——`UIPoseKeyframeFactoryMixin` 的 INVOKE 注入 owner 写成 `UIPoseFactoryEditor`，但字节码里 `add` 调用的 owner 是 `UIElement`，回调从未触发；已改为 RETURN 注入并加幂等 guard。
- 关键发现 2：`UIModelPoseEditor` 在 FSR 已改名 `UIPoseFormEditor`，verification 目标列表一并修正。
- 关键发现 3：strict run 的 verification 开关此前靠 CLI `-D` 传参，**根本到不了游戏 JVM**（之前会话的「strict 全绿」实际没跑 verification）；已在 build.gradle 加 `-PrunVerification=1` 入口。
- 游戏内人工冒烟（premiere 按钮点击导出、fluid 三 mode 视觉、follow-orbit 操作）仍待用户在新 jar 上确认；jar 位置 `bbs-snow-addon/build/libs/bbs-snow-addon-1.0.0.jar`。

## 1. 构建基线（R1，先于一切修复）

- [x] 确认无客户端进程占用 `bbs-snow-addon/build/libs` 与 `runs/`。
- [x] `cd bbs-snow-addon && ./gradlew.bat --no-daemon verifyBbsCoreJar build --console=plain`，记录真实 exit code；失败则按编译错误逐组修复（预期高发点：`UIPoseFormEditor` 漂移、`renderHybrid` Redirect 目标、`premiere/` 对 `new/` 0.0.11 新 API 的引用、fluid 对 `FormUtilsClient`/`UIFormEditor` API 的引用）。
- [x] 审计 jar：premiere/fluid/forms/simulation 类、三份 mixin 配置新条目、premiere 与 fluid 本地化键存在；无 Fabric/Yarn 类名残留。

## 2. Premiere 按钮链路（R2 / AC3）

- [x] 代码走查 `PremiereExportButtonInstaller.onClientTick` → `PremiereExportButton.install`：确认 `getDashboardIfCreated` 返回非空时序、`getPanel(UIFilmPanel.class)` 在 dashboard 初始化完成后才非空、`preview.icons.add(button)` 后 icons 行会重排（对照 FSR `UIFilmPreview.icons` 布局与 S7 已验证的 bonePriority `addBefore` 先例）。
- [x] 若 install 首次因 filmPanel==null 失败：确认 WeakHashMap 不吞掉重试（失败时不应 put，靠后续 tick 重试 —— 读代码确认现状正是如此）。
- [x] 若按钮 add 后不可见：检查 icons 行宽/裁剪、是否需要 `icons.resize()`/重排触发；对照 `UIFilmPreviewMixin` bonePriority 按钮的挂载方式。（`icons.add(button)` 与 bonePriority 的 addBefore 走同一 `UIElement.add` → `markChild` 路径，行容器 resize 时自动重排；挂法成立）
- [ ] strict run 或日志验证：点击图标弹出设置 overlay / 触发导出前置检查（FFmpeg 缺失时弹提示即算链路通）。（**待人工冒烟**：strict 环境无交互，点击链路需真实点击确认）

## 3. 纹理调色/白化链路（R3 / AC4）

- [x] 核对 `ModelInstanceTextureGradeMixin` 两个注入点对 `new/build/libs/BBS_FSR_beta_v0.0.11.jar` 字节码真实命中（`render` HEAD ModifyVariable + `renderHybrid` 内 `BBSShaders.getModel()` Redirect）；strict 日志已确认 `Mixing ModelInstanceTextureGradeMixin ... into mchorse.bbs_mod.cubic.ModelInstance` 且无 injection failure。
- [x] 走查 UI 写入路径：`UIPoseEditorMixin`（UIPoseFormEditor 宿主）、`UIPoseKeyframeFactoryMixin`（关键帧宿主）、`UIKeyframeSheetTextureGradeMixin`（轨道表）是否都落到 `PoseTransformMixin`/`ModelGroupMixin` 的 tint/whiten 字段并触发 pre/postNotify。**发现并修复关键帧宿主注入从未触发的 bug**。
- [x] 走查渲染读取路径：`ModelInstanceTextureGradeMixin` 读取 `TextureGradeProvider`（ModelForm）与逐 group `GroupTextureGradeHolder`，VAO/hybrid/CPU 三条路径的 shader 选择均有 cover（hybrid 新增 Redirect）。
- [x] 验证 shader uniform 写入：`ModelTextureGradeShader.apply` 只在 `program == shader` 时写 uniform；select/selectCurrent 后 draw 用的就是该 program（hybrid 路径由新 Redirect 保证）。
- [ ] strict run 验证 + 游戏内目视：模型轨道调 tint/whiten 立即生效，VAO 与 hybrid 两种渲染都覆盖。（strict 已过数据/装配断言；**目视待人工冒烟**）

## 4. 跟随轨道链路（R4 / AC5）

- [x] 核对 `UIFilmControllerFollowOrbitMixin` 对 FSR 0.0.11 `UIFilmController` 的注入点签名（`<init>`/`setPov`/`getPovMode`/`getOrbitModeIcon`/`populateCameraModeMenu`/`toggleOrbitMode`/`handleCamera`）——strict 日志确认全部 apply 无 failure。
- [x] 核对 `OrbitFilmCameraControllerFollowMixin` 的 `writeAnchor`/`getReplayPivot` shadow 与 anchor 覆盖逻辑，确认跟随的是渲染枢轴而非逻辑位置。
- [x] 核对 `UIFilmPreviewMixin` 滚轮缩放注入（`subMouseScrolled` HEAD、mode==6 时 `zoomOrbit`）。
- [ ] 游戏内：相机菜单出现「跟随轨道」项、选中后相机跟随 replay、滚轮缩放、切回其他模式无残留附着、重进存档后按 `followOrbitMode` 设置恢复。（**待人工冒烟**）

## 5. S9 流体收尾（R5 / AC6）

- [x] 按 S9 implement.md 第 1–6 节核对 8 个新文件的完成度（19 值契约、budget、确定性、三 mode renderer、UI、本地化）。
- [x] strict run 通过 `verifyFluidAndPremiereIntegration` 段。（修正 19 值断言为 id 集合契约后通过）
- [ ] 游戏内：Extra 分类出现 Fluid、三种 mode 渲染不崩、编辑器面板可用；视觉/交互项登记 `s9-manual-acceptance.md`。（清单已建；**目视待人工冒烟**）

## 6. 验收、提交与记录（R6 / AC1/AC2/AC7）

- [x] strict 客户端全绿（全部 mixin apply、verification 全过、预期 `client.stop()`），保存日志关键段。
- [x] addon 仓库分组提交：premiere 组 / texture-grade 修复组 / follow-orbit 组 / fluid 组 / strict 修复组（不 `git add -A`）。
- [x] 回填 S7/S9 implement.md 勾选与 validation record；更新 S10 manual-acceptance 清单；本任务 check 后归档。
- [ ] root 仓库仅提交 `.trellis/` 任务记录；确认 `git diff new/` 为空。

## Review Gates

- Gate A：构建 exit code 0 之前不改任何逻辑（先让 GPT 的改动编译过）。
- Gate B：每个功能组先 static 走查 + strict run 日志证据，再进游戏目视；不一次改多组。
- Gate C：任一组修复后不回归其他组（mixin 清单不变、jar 审计不过期）。

## 验证命令

```powershell
Set-Location bbs-snow-addon
.\gradlew.bat --no-daemon verifyBbsCoreJar build --console=plain
Write-Output "EXIT=$LASTEXITCODE"

# strict 客户端（确认无客户端占用后）
.\gradlew.bat --offline --no-daemon runClient --max-workers=1 --console=plain
# JVM 属性：bbs_snow.runtimeVerification=true、bbs_snow.exitAfterVerification=true（按项目既有入口）
```
