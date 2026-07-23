# 执行计划：回放/影片6个运行时bug修复

按簇推进，每簇结束跑一次编译，难簇单独游戏内验证后再进下一簇。所有改动仅在 `new/`。

## 阶段 0：准备
- [ ] 确认当前任务 `task.py current` 指向 `06-20-fs2-runtime-bugfix`。
- [ ] `cd new && git stash list / git status -s | wc -l` 记录基线（257 未提交）。**不要**动现有未提交改动的回退；在其基础上继续。
- [ ] 通读 `research/framebuffer-flicker-chain.md` 与 `research/orbit-camera-regression.md`。

## 阶段 1：簇 D 行为编辑器按钮（最快、最确定，先拿一个胜利）
- [ ] 在 `UIFilmPanel.java` 按 design 簇 D 的 6 处补回 `openActionEditor`（字段/创建/wh+tooltip/topBarActions/setEnabled/renderTopBarButton）。
- [ ] 校验 `Icons.ACTION`、`UIKeys.FILM_OPEN_ACTION_EDITOR`、`showPanel`、`FILM_TOP_BAR_BUTTON_SIZE` 在 new 存在；缺则按参考补。
- [ ] 编译：`new/gradlew.bat --no-daemon compileClientJava`。
- 回滚点：本簇单独 commit 候选。

## 阶段 2：簇 B 阴影/受光（独立、确定）
- [ ] 修 `model.vsh`：恢复 `uniform mat3 IViewRotMat;` 与 `vec3 fixNormal = normalize(NormalMat * Normal);`，光照传 `fixNormal`。
- [ ] 核对 shader 定义 json（`assets/bbs/shaders/core/model.json` 或等价）uniform 列表含 `IViewRotMat`/`NormalMat`；NeoForge 下不可用则改用等价 uniform 或在 `BBSShaders` 注入。
- [ ] 编译验证。
- 回滚点：单独 commit 候选。

## 阶段 3：簇 A 闪烁/黑屏/卡死/影片丢失（核心、最高风险）

**2026-07-03 修订**：黑屏链已由 ae39717b 修复；BBSRendering 拷贝逻辑（无条件 toggle + glCopyTexSubImage2D）已在工作区完成。闪烁根因改判为 **codex 丢弃了参考的 InverseView 矩阵体系**（详见 `research/flicker-rootcause-inverseview.md`），修复 = 成套移植参考体系并移除 codex 的 camera-relative 方案，不可混搭：

- [x] A1：BBSRendering 拷贝逻辑对齐参考（工作区已完成，待验证）。
- [ ] A5（新）：移植 `graphics/InverseView.java`（参考原样，包名一致）。
- [ ] A6（新）：`BBSRendering` 加 `public static final Matrix4f camera` 字段；在与参考 `onWorldRender`（:478-481）等价的位置喂 `InverseView.set(new Matrix3f().rotation(camera.getRotation()))`；删除死代码 `flushCustomFramebuffer()`。
- [ ] A7（新）：`WorldRendererMixin` 加 `prepareCullFrustum`（Mojmap，对应 Yarn `setupFrustum`）HEAD 注入：`BBSRendering.camera.set(frustumMatrix)`。
- [ ] A8（新）：`UIFilmController.renderPickingPreview`/`renderStencil` 逐行对齐参考（:1215-1509）：`InverseView.set(new Matrix3f(BBSRendering.camera).invert())`、`MatrixStackUtils.multiply(worldStack, BBSRendering.camera)`（替换 `panel.lastView`）、clearPicking 位置、收尾语序；移除 [BBS-DIAG] 日志。
- [ ] A9（新）：消费方对齐参考（移除 isCameraRelativeWorld/context.camera.view 方案）：`ModelVAORenderer`（补 `ViewRotationMat` uniform ← InverseView，兼修 #4 Iris 阴影）、`ParticleFormRenderer`、`TrailFormRenderer`、`VanillaParticleFormRenderer`、`UIModelRenderer`、`UIParticleSchemeRenderer`、`MatrixStackUtils`、`Gizmo`（camera-relative 痕迹）。
- [ ] A3：核对并补齐影片 save/markDirty 调用（`Films.java` + 数据面板）。
- [ ] A4：空白影片列表——把 `UIDataDashboardPanel.requestData()` 对齐参考（:534-537 简单直接回调 fill()）：移除 codex 的 dataRequestVersion/requestTab 过严检查与 editor.setEnabled(false) 加载锁（详见 `research/peripheral-file-diff.md` 文件4节）。
- [ ] 编译：`compileJava compileClientJava`。
- [ ] **游戏内验证**（关键）：放置回放目标不闪 / 退出不黑屏 / ESC 不卡 / 重进影片在 / 空列表正常 / **粒子朝向与世界 form 位置回归验证**（camera-relative 方案被替换）。
- 回滚点：A 簇较大，A5-A9 一次 commit（成套不可拆），A3-A4 单独 commit。

## 阶段 4：簇 C 轨道相机（中等置信，需细查）
- [ ] `cd new && git diff -- src/client/java/mchorse/bbs_mod/camera src/client/java/mchorse/bbs_mod/film` 精确定位黑屏修复改动的 camera/film hunk。
- [ ] 进入轨道模式时同步 `targetPosition/targetRotation`（对照参考 `orbit.setup(camera)`），确保 pivot 有效。
- [ ] 核对 `FILM_CONTROLLER_TELEPORT_ORBIT`(Z) 逻辑完整；移除/纠正撞键的 `FLIGHT_ORBIT`，确认 `FILM_CONTROLLER_ATTACH_ORBIT` 接线。
- [ ] 编译 + 游戏内验证：轨道模式进入/操作正常，Z 键行为正常。
- 回滚点：单独 commit 候选。

## 阶段 5：簇 E 日志清理（全部验证通过后）
- [ ] 删除 `BBSFlickerDiagnostics.java`，移除 ~55 处 `if (BBSFlickerDiagnostics.ENABLED){...}` 块及仅诊断用的辅助字段/方法。
- [ ] 编译 + 运行确认 `latest.log` 无 `[BBS-FLICKER]`。

## 阶段 6：收尾
- [ ] 全量编译：`new/gradlew.bat --no-daemon compileJava compileClientJava`（可选 `build`）。
- [ ] 汉化结论：记录 `docs/cn` 已与目标一致、无需改动。
- [ ] 更新 `docs/migration-status/current-status.md`（把 6 bug 状态从"未修"挪到"已修/待游戏验证"）。
- [ ] 等用户确认后，按簇分组对 `new/` 做本地 commit（仅 new 内容）。

## 阶段 7：F4 世界录制窗口冻结（2026-07-17）

- [x] 确认视频纹理持续更新而 `toggleFramebuffer(false)` 只切回 vanilla target、未呈现私有 target，导致窗口保留旧帧。
- [x] 对照 BBSFS 的条件式 framebuffer draw，恢复仅无 BBS menu 的 F4/F6 世界录制全屏呈现。
- [x] 保持编码纹理在录制提示前复制，提示在窗口呈现前绘制。
- [ ] 游戏内验证 F4 移动/转向实时可见、视频一致、Film/Morph UI 不被覆盖。

## 验证命令
```bash
cd /d/bbsinneoforge/bbsmodinforge/bbsmodinforge/new
./gradlew.bat --no-daemon compileClientJava        # 客户端快验
./gradlew.bat --no-daemon compileJava compileClientJava   # 全量
# 游戏内验证由用户在 PCL2 实例(NeoForge 21.1.233)运行，提供新 latest.log
```

## 评审门（task.py start 前）
- [ ] prd / design / implement 三件齐备且经用户评审。
- [ ] 策略=以 1.21.1 对齐、范围=先修 6 bug 已与用户确认。
