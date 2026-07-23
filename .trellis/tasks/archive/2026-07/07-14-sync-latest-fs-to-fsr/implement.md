# 执行计划：同步最新 FS 到 FSR

## 0. 审核与基线

- [x] 审核 `new` 相对 `cnb/main` 的 27 个本地提交。
- [x] 运行当前 HEAD 的 `compileJava compileClientJava` 与 `build`。
- [x] 建立 `9b28a8a4..395f6927` 的 45 提交覆盖矩阵。
- [x] 以 `8b25fbaf` 作为 Minecraft 1.21.1 文件参考。

## 1. 分簇迁移

- [x] 核心模型/IK/物理/weld/bevel/debug overlay。
- [x] framebuffer 与 F4/F6 video export sessions。
- [x] 模型编辑器、texture manager、item/block picker。
- [x] film clips、motion paths、action shortcut、replay presets。
- [x] gizmo redesign、pick tolerance、2D pie、multi-bone selection。
- [x] shared UI、settings、keys、language/resource integration。

每个簇要求：记录上游 commits；对照 1.21.1 最终文件；保留 NeoForge 映射；检查序列化/设置/语言键；完成后编译。

## 2. 整合审核

- [x] 解决共享文件冲突和重复/遗漏键。
- [x] 搜索 Fabric/Yarn 泄漏、TODO/FIXME、调试日志、空 stub。
- [x] 对 45 个上游提交逐项标记已迁/等价/N/A 并人工复核。
- [x] 核对现有 27 个提交的高风险审核结论已处理。

## 3. 验证

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon build
```

- [x] 编译与构建通过。
- [x] 记录无法自动化的游戏内 smoke 清单。
- [x] 更新 `docs/migration-status/current-status.md`。
- [x] 按用户后续授权分组提交并推送到 `cnb/main`。

## 4. 2026-07-15 七提交续作

- [x] `804e8c97`：模型不可见时同步隐藏 name tag。
- [x] `458d0414`：录制图标菜单“传送到玩家”并插入位置/旋转关键帧。
- [x] `0e374c33`：时间线标尺点击只 scrub 游标，不抓取后方 clip。
- [x] `55ce67b2`：空白处手动创建关键帧使用可配置默认插值。
- [x] `3f2435a4`：影片导出文件名模板。
- [x] `453de7ce`：F6 世界渲染导出音频。
- [x] `07bcc918`：列表 3D 模型预览开关。
- [x] 集中合入 BBSSettings、UIKeys 和 en/ru/zh 语言资源。
- [x] 审核主线粒子文件与 `a0776721` 一致，没有重新迁入隔离粒子实现。
- [x] 独立审查 7 个提交覆盖、NeoForge/Mojmap 适配和 Fabric/Yarn 泄漏。
- [x] 在独立干净工作树运行 `clean build`，包括 `testVideoExportUtils`，结果为 `BUILD SUCCESSFUL`。

## Risk gates

- 导出/session 与 framebuffer 作为一个原子簇验证，避免只迁一半导致黑屏或分辨率状态泄漏。
- IK/物理/weld/bevel 按数据模型 → runtime → renderer → editor 的顺序落地。
- Gizmo 的 `Gizmo`、`GizmoInteraction`、各 viewport consumer 必须整簇迁移并验证 picking 坐标链。
- `UIKeys`、`Keys`、`BBSSettings`、语言文件最后集中整合，避免并行覆盖。
