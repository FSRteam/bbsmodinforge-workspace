# 执行计划：BBS 多贴图模型方块渲染性能优化

## 1. 现状与基线

- [x] 记录当前 Cubic/BOBJ 渲染调用边界和已有回归测试。
  - Cubic 侧（`ModelVAORenderer.renderBatch`/`BatchDraw`、`CubicVAORenderer` 材质纹理缓存 +
    `renderImmediateBatches` 按纹理分批、`ModelRenderPerformanceSourceTest`）在本任务前期会话已完成。
  - BOBJ 侧基础件（`BOBJModelVAO.updateMesh(matrices, armatureChanged)` 重载、`renderBound`、
    `BOBJModelVAO.renderBatch`）已备好但 `ModelInstance.render` 的 BOBJ 分支未接线。
- [x] 确认 `new` 层规范与透明/光影状态契约（quality-guidelines.md 的 BOBJ armature 复用与
  FormTranslucentQueue 命令状态所有权契约）。

## 2. 实现

- [x] 为 `ModelVAORenderer` 增加批次内无状态 VAO 绘制入口，统一 shader 生命周期。（前期已完成）
- [x] 在 `CubicVAORenderer` 中缓存材质纹理解析并按纹理分批即时绘制；延迟透明命令维持独立边界。（前期已完成）
- [x] BOBJ 路径接线（本次会话完成，`ModelInstance.render` BOBJ 分支）：
  - 单次 `hasBOBJArmatureChanged` 模型级姿态变化判断，所有网格共享（避免每网格重走骨骼数组）。
  - 每次绘制一张 `Map<String, Texture>` 材质纹理解析缓存（mesh 名 = 材质名）。
  - 即时网格按连续相同纹理合批，走 `BOBJModelVAO.renderBatch` 共享一次 shader apply/clear。
  - 延迟命令共享同一份 `deferredArmature` 快照（首个命令时复制一次），不再每网格每命令复制。
  - `needsSplit`/`needsWholeDefer` 边界与拾取、透明拆分语义原样保留。
- [x] 增加源码/逻辑回归，覆盖同纹理合批、纹理切换、BOBJ 矩阵复用及透明分支。
  - `ModelRenderPerformanceSourceTest`（挂在 `testClientStructuralRegistration`）断言批处理与共享快照形态。

## 3. 验证

- [x] 运行最小相关测试：`testClientStructuralRegistration`、`testUiInputMailbox` 均通过。
- [x] 运行 `./gradlew.bat compileClientJava`（BUILD SUCCESSFUL）。
- [x] 运行 `./gradlew.bat build`，`BUILD SUCCESSFUL in 9m 13s`（80 actionable tasks）。
  - 中途排障：机器仅 8G 内存且另一会话并行跑同一 `new/` 的 build（共享 `build/neoForm` 目录
    导致增量缓存互踩），等待其结束后独占重跑成功；另修复了 `BBSUiLifecycleSourceTest`
    对 `if (this.hasArmatureChanged(matrices))` 旧字面断言被重构破坏的问题（两参
    `updateMesh` 重载恢复显式 if 形式，语义不变）。
- [x] 检查最终 diff：本任务仅新增 `ModelInstance.java` BOBJ 分支接线与 `BOBJModelVAO.java`
  公开查询/合批入口；`ModelInstance.java`/`CubicVAORenderer.java` 中 translucent 拆分、
  `drawWithStableModelColor` 等为前期会话已存在的未提交改动，未修改其它任务文件。

## 风险点

- shader uniform 必须在每个 VAO 绘制前更新，不能误把第一个组的矩阵复用于整个批次。
  （`renderBatch` 内 `uploadDrawUniforms` 逐 draw 更新 ModelViewMat/NormalMat，已覆盖。）
- 延迟透明材质必须保留自己的纹理和深度策略。（BOBJCommand 每命令携带自己的纹理与快照。）
- 动画纹理、缺失纹理和拾取 light buffer 的状态不能跨帧污染。（纹理解析缓存每次绘制重建；
  picking 缓冲仍由 `updatePicking` 的 increment 判断守卫。）

