# 回放/影片6个运行时bug修复

## Goal

修复 codex 上一轮迁移遗留的 6 个运行时缺陷，使 `new`（NeoForge 1.21.1）的影片/回放编辑器、相机轨道模式、模型受光（阴影观感）行为与 FS2 官方 Fabric 1.21.1 分支一致。统一修复策略：**以 FS2 1.21.1 参考实现为准重新对齐被改乱的代码**，而非在 codex 的发散版本上继续打补丁。

## 背景事实

- 待修复代码：`new/`（独立 git 仓库，分支 master，HEAD `257ec5f2`，当前有 257 个未提交修改 = codex 留下的部分工作）。
- 黄金参考：`temp/bbs-fs-1.21.1-ui/`（FS2 官方 Fabric 1.21.1 分支，与 new 同为 MC 1.21.1，迁移仅需机械 API 映射）。
- 备用参考：`old2.0/bbs-fs/`（FS2 master）。
- 详细证据见本任务 `research/framebuffer-flicker-chain.md`、`research/orbit-camera-regression.md`。

## Requirements

1. **闪烁**：在回放编辑器中"添加回放"放置回放目标后，预览画面不再闪烁。
2. **黑屏连锁**：创建影片后退出影片画面不黑屏；ESC→"回到游戏"不卡死；重新进入游戏后已创建的影片项目仍在（持久化正常）。
3. **空白影片列表**：刚启动游戏、未创建影片时打开影片编辑器，影片列表正确显示（不再异常空白）。
4. **阴影/受光**：模型受光与"阴影"观感与 FS2 1.21.1 一致——恢复 `model.vsh` 被删除的法线变换/归一化（`IViewRotMat`、`fixNormal = normalize(NormalMat * Normal)`）。
5. **轨道相机模式**：摄像机轨道模式可正常进入与操作，Z 键传送到录制点逻辑正常，无快捷键冲突。
6. **行为编辑器(Action Editor)**：影片面板顶栏恢复"打开行为编辑器"按钮（`openActionEditor`），可正常打开 `actionEditor` 面板。
7. **F4 世界录制可见画面**：F4 录制期间编码器继续读取无叠加的导出纹理，同时玩家窗口每帧显示同一个实时世界画面；移动和转动视角不得只出现在最终视频而使窗口停在录制前一帧。

附带：
8. **日志清理**：上述 bug 修复并验证后，移除 codex 的 `[BBS-FLICKER]` 诊断框架（`BBSFlickerDiagnostics` 及全部 ~55 处插桩）。
9. **汉化**：经核实 `docs/cn/lang/zh_cn.json`、`docs/cn/strings/zh_cn.json` 已与资源目录目标文件完全一致，**无需改动**（仅在文档中记录结论）。

## 约束

- 不破坏向后兼容；不引入新依赖、新构建工具。
- 不迁入 Fabric/Yarn API 形态；保留 new 已有且正确的 NeoForge 适配（Mojmap、RenderSystem、GuiGraphics、payload facade）。
- 从参考重新对齐时，逐文件保留 new 中正确的 NeoForge API 映射，只替换被改坏的逻辑。
- 仅对 `new/` 提交；commit 须在用户确认后进行。

## Acceptance Criteria

- [ ] `new/gradlew.bat --no-daemon compileJava compileClientJava` 编译通过。
- [ ] 游戏内 smoke：放置回放目标后预览不闪烁。
- [ ] 游戏内 smoke：退出影片不黑屏、ESC 回游戏不卡、重进影片项目仍在。
- [ ] 游戏内 smoke：未创建影片时影片列表显示正常。
- [ ] 游戏内 smoke：模型受光/阴影观感与 FS2 1.21.1 一致。
- [ ] 游戏内 smoke：轨道相机模式可进入与操作，Z 键行为正常。
- [ ] 游戏内 smoke：影片顶栏"行为编辑器"按钮存在且能打开 action 面板。
- [ ] 游戏内 smoke：F4 录制时窗口实时显示移动/转向，最终视频内容一致；录制提示可见但不进入编码帧，影片编辑器 UI 不被全屏预览覆盖。
- [ ] 诊断日志已移除，重新运行后 `latest.log` 无 `[BBS-FLICKER]` 刷屏。

## Notes

- 6 个问题中 #1/#2/#3/#6 高度同源（影片面板 ↔ 渲染 framebuffer ↔ 可见性/顶栏链路），优先一起对齐。
- #4（阴影）与 #5（轨道）相对独立，可并行。
- 部分根因（ESC 卡死的 GL 死锁推断、orbit 平滑同步）置信度中等，须以游戏内验证为准。
