# S7 执行计划

## 1. 迁移稳定工具边界

- [x] 迁移 `AnimationFileOperations`、`AnimationInterpInheritance`、`PoseAnimationExporter`、`IKAnimationExporter` 与 `BonePriority`。
- [x] 完成 Yarn 到 Mojmap、包名、资源命名空间和 `bbs_snow$` 前缀转换。
- [ ] 为动画文件 round-trip、未知字段保留、冲突/失败语义和插值映射添加确定性验证。

## 2. 迁移 Actions UI

- [x] 迁移 `UIActionsFormPanelMixin`、`UIActionsConfigEditorMixin`、`UIActionsConfigKeyframeFactoryMixin`、`UIKeyframeSheetActionsMixin` 和 `UIReplaysEditorActionsOverlayMixin`。
- [x] 对当前 jar 重新确认 `pickAction`、控件回调和字段写入锚点；移除不再匹配的源端合成 lambda 假设。
- [ ] 验证配对端点创建、参数同步、重新选择、删除清理与 overlay 状态。

## 3. 迁移 Replay/Keyframe 扩展

- [x] 迁移 `UIKeyframeDopeSheetMixin`、`UIKeyframesMixin`、`UIReplaysEditorMixin`、`UIReplaysEditorUtilsMixin` 和 `ModelFormTrackNameMixin`。
- [x] 保留 FSR pose tab/IK/物理/声音/PBR 轨道，验证骨骼层级折叠和 BonePriority 的 fallback。
- [x] 迁移 `UIReplayKeyframeEmbedHelper` 与三个 inspector/replay embed Mixin，使用 S6 `IMiniWindowDockHostRef`。

## 4. 迁移 Pose/Transform/Preset/Export UI

- [x] 迁移两个 pose accessor、`UIPoseEditorMixin`、`UIPoseKeyframeFactoryMixin`、`UITransformMixin`、`UIPropTransformMixin`。
- [x] 迁移 `UIDataContextMenuMixin`、`UIFilmPreviewMixin`、`ExportUIKeys`、`ReplayUIKeys`、导出面板、Film IK 采样器和 `UIKeyframesExportMenuMixin`。
- [x] 补齐英语/简中键与折叠纹理；客户端初始化注册插值扩展。

## 5. 配置和自动验证

- [x] 将 22 个 Mixin/Accessor 加入 `bbs_snow.client.mixins.json`，保持 `defaultRequire: 1`。
- [x] 扩展 `BBSSnowRuntimeVerification`，覆盖 AC3 的确定性路径与 S7 target/Mixin 计数。
- [ ] 运行 `./gradlew verifyBbsCoreJar build`，记录真实退出码并审计 jar 内容。

## 6. 严格客户端和收尾

- [ ] 运行项目现有严格客户端验证，确认全部 S7 Mixin 准备/应用并构造关键 UI。
- [ ] 检查完整日志：无 injection failure、缺失 S7 本地化资源或 addon helper 类加载错误。
- [x] 登记无法自动证明的真实手势、复杂工程与可选模组交互到 S10。
- [ ] 运行最终 Trellis check、提交 addon 与任务记录、归档 S7。

## Validation record

- 2026-07-27 14:56：在稳定核心快照上，严格客户端准备 109 个 Mixin，22 个 S7 Mixin/Accessor 全部应用；确定性运行验证通过并由 `finally` 正常停止。该记录证明实现路径，但不是最终核心制品门禁。
- 2026-07-27 17:03：最终 addon `verifyBbsCoreJar build` 通过，生成 17:02 的新 addon JAR；其源码时间戳、jar 边界和资源内容仍需在最终 strict run 后统一记录。
- 最终 strict run 首次尝试在游戏启动前被新出现的 `new/src` 更新正确拦截；需要在并行核心修改稳定后重建/安装同哈希 core 再运行。
- 真实鼠标拖拽、完整视觉手感、复杂 Film 工程、addon-disabled 基线和可选第三方联动均登记在 S10 `s7-manual-acceptance.md`，S7 不声明这些项目已通过。

## 风险与回滚点

- Actions 合成 lambda、`UIKeyframeDopeSheet` 私有渲染签名和导出器模型 API 是最高风险点；每完成一组先构建，再继续下一组。
- 任何 required Mixin 启动失败都回到该组修正描述符，不通过 `require=0` 绕过。
- 不使用 `git add -A`；根仓库两份既有规范改动与 `new/`/旧源码树改动不得纳入 S7 提交。

## 验证命令

```powershell
Set-Location bbs-snow-addon
.\gradlew.bat verifyBbsCoreJar build
Write-Output "EXIT=$LASTEXITCODE"

# 严格客户端命令以项目现有 S6 验证入口为准；不得通过管道截断退出码。
```
