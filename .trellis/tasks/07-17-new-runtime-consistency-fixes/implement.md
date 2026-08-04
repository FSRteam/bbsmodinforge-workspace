# Implementation Plan

## Ordered Work

1. 固定当前 `new` 状态、适用规范、FS 基线和每个子任务文件所有权。
2. 并行实施十个子任务；每个代理先读父任务与自己的 PRD，再运行 `trellis-before-dev`。
3. 子任务完成时运行其最小编译/测试并回报修改文件、验证结果和未解决风险。
4. 主任务检查共享文件、跨层契约、重复 helper 和所有审查 finding 的闭环。
5. 派出独立验证代理复查网络、交互、输入、粒子、IK 和 Addon 高风险修改。
6. 串行运行 `git diff --check`、`compileJava compileClientJava`、全部定向测试、`check build`。
7. 更新必要的 spec/任务记录；不自动提交，等待用户确认提交范围。

## Child Deliverables

- `07-17-network-server-security`: 路径、分片、权限、模型方块、回调清理。
- `07-17-interaction-action-recording`: 枪、owner、方块/攻击/实体交互录制回放。
- `07-17-input-disconnect-lifecycle`: 输入仲裁、旧 screen、release、断线 repository、HUD。
- `07-17-particle-world-rendering`: 粒子事件/JSON/焦点、空间、Iris 和 GL 状态。
- `07-17-ik-physics-selection`: 最终约束、pole/softness、owner/space/scratch、Ctrl 选骨。
- `07-17-film-editor-migration`: Ctrl+滚轮、anchor Gizmo、timeline/clip 滚动。
- `07-17-replay-keyframe-consistency`: 删除索引和关键帧空指针。
- `07-17-addon-api-lifecycle`: first-wins、窗口、异常、late client、诊断和台账。
- `07-17-video-export-lifecycle`: FFmpeg 健康、终态副作用、路径。
- `07-17-platform-resource-consistency`: tracking range、provider containment、英语键。

## Validation Commands

```powershell
Set-Location new
git diff --check
.\gradlew.bat --no-daemon --console=plain compileJava compileClientJava
.\gradlew.bat --no-daemon --console=plain testUiInputMailbox testFilmCollaborationApi testUiSurfacePlacement testUiMirrorDemand testVideoExportUtils
.\gradlew.bat --no-daemon --console=plain check build
```

新增测试任务由对应子任务接入 `check`，最终命令必须自动运行它们。

## Risky Files

- `new/src/main/java/mchorse/bbs_mod/network/ServerNetwork.java`
- `new/src/main/java/mchorse/bbs_mod/BBSMod.java`
- `new/src/client/java/mchorse/bbs_mod/ui/framework/UIScreen.java`
- `new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`
- `new/src/client/java/mchorse/bbs_mod/forms/renderers/FormRenderer.java`
- `new/src/client/java/mchorse/bbs_mod/cubic/ik/ModelIKApplier.java`

修改这些文件前后都要重新搜索所有入口和调用者。
