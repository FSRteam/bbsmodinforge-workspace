# 执行计划

1. [x] 读取 `new` backend/spec 及 shared thinking guides，记录当前工作树。
2. [x] 修复 dashboard panel 的 reopen 生命周期，并补充轻量 source/logic regression 覆盖。
3. [x] 修复模型方块动态 palette 的失效实体清理和 camera/gizmo owner 清理。
4. [x] 将半透明队列限制到真实世界渲染，审查 MobForm 的 GL 状态恢复、阴影与预览立即路径。
5. [x] 修复 S 缩放轴循环及 R 旋转模式可达性，保留 gizmo 中心统一缩放。
6. [x] 优化动作模型预览的 normal/stencil 重复求值，确认动画与拾取一致。
7. [x] 运行 `git diff --check`、定向测试、`compileJava compileClientJava`、`check`、`build`。
8. [x] 运行 Trellis quality check，更新必要 spec/任务记录并输出游戏内复测矩阵。

## 验证命令

```powershell
Set-Location new
git diff --check
.\gradlew.bat --no-daemon --console=plain compileJava compileClientJava
.\gradlew.bat --no-daemon --console=plain testUiInputMailbox testFilmEditorMigration
.\gradlew.bat --no-daemon --console=plain check build
```

## 高风险文件

- `src/client/java/mchorse/bbs_mod/ui/dashboard/UIDashboard.java`
- `src/client/java/mchorse/bbs_mod/ui/dashboard/panels/UIDashboardPanels.java`
- `src/client/java/mchorse/bbs_mod/ui/model_blocks/UIModelBlockPanel.java`
- `src/client/java/mchorse/bbs_mod/forms/renderers/FormRenderer.java`
- `src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java`
- `src/client/java/mchorse/bbs_mod/forms/FormTranslucentQueue.java`
- `src/client/java/mchorse/bbs_mod/ui/framework/elements/input/UIPropTransform.java`
- `src/client/java/mchorse/bbs_mod/ui/forms/editors/utils/UIPickableFormRenderer.java`

## 验证结果

- `compileJava compileClientJava`: 通过。
- `testUiInputMailbox`: 通过。
- `testFilmEditorMigration`: 通过。
- `check build`: 通过（72 tasks）。
- `git diff --check`: 通过。
- 游戏内视觉、快捷键手感和帧率复测见 `manual-test.md`。
