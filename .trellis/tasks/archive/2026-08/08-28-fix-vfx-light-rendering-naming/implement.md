# 执行计划：VFX Light 分类、命名与渲染修复

1. [x] 激活任务并读取相关 Trellis 规范。
2. [x] 按上游结构拆分 Light/Air/Style 面板和分组关键帧编辑器。
3. [x] 修复四种 renderer 的注册、世界坐标和 fallback 消费链。
4. [x] 更新 `VFX Light` 显示名、`VFX_Light_fsr` 产物名以及中英文资源。
5. [x] 增加回归审计并运行 `clean build`、`check`、JAR/Fabric 残留检查。
6. [x] 更新 `PORT_STATUS.md` 与 Trellis spec，完成任务归档。

## 验证命令

```powershell
cd bfx-light
.\gradlew.bat --no-daemon clean build
.\gradlew.bat --no-daemon check
```
