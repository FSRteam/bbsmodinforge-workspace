# 执行计划：伪装编辑器物理、IK 与骨骼约束系统性修复

## 1. 恢复模拟世界矩阵链

- [x] 在 `FormRenderingContext` 恢复独立 `world` `PoseStack`，按实体绝对位置和 body yaw 初始化。
- [x] 在 `FormRenderer` 的表单与 body-part push/pop/transform 中同步推进 `world`。
- [x] 对照 FS 1.21.1 补齐 `BlockFormRenderer`、`MobFormRenderer`、`ModelFormRenderer` 的额外 world 变换。
- [x] `ModelFormRenderer` 只把 `world` 传给 IK 目标转换与物理求解；UI/手持等无世界语义路径使用局部空间。

## 2. 修正求解顺序

- [x] 将通用骨骼限制移动到 IK/物理之前。
- [x] 保持 IK 与物理内部现有的 per-bone limit 求解。
- [x] 审查调试绘制、装备/身体部件矩阵捕获是否读取最终程序姿态。

## 3. 隔离物理历史

- [x] 将 `ModelPhysicsRuntime` 的可变 `STATES` 从静态全局状态改成 renderer 所有的实例状态，并按显式 simulation owner 隔离世界/预览/缩略图/第一人称上下文。
- [x] 保留静态不可变编译缓存及其失效入口。
- [x] 删除无消费者的共享 `ModelInstance.lastBaseTransform` 状态。

## 4. 检查同根问题

- [x] 搜索所有把 `context.stack`、相机 view 或主相机变换传入 IK/物理的路径。
- [x] 搜索所有在 IK/物理后清空 `orient` 或重写骨骼旋转的路径。
- [x] 搜索其他按模型资源 ID 保存可变实例状态的实现。

## 5. 验证

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon build
git diff --check
```

- [x] `compileJava compileClientJava`、`build` 和 `git diff --check` 通过。
- [x] 输出 `manual-test.md` 游戏内复测矩阵，覆盖 PRD 验收标准。
- [x] 运行 `trellis-check`，并将坐标/所有权/求解顺序不变量写入项目 spec。
- [x] 代码提交：`6a5785bd fix(model): isolate physics space and compose constraints`。

## 6. IK / 物理性能优化（2026-07-16）

- [x] IK 编译缓存祖先排序与 per-chain wanted bones，运行时移除逐帧复制/排序/set 分配。
- [x] IK renderer workspace 复用 frames、positions、目标转换、方向构造与 solver 输入容器。
- [x] Physics 编译缓存 wanted/chain ids/rest topology，实例状态复用 frames 与 chain-frame 容器。
- [x] Pivot frame collector 原位更新已有 frame，并复用遍历栈/刚体分解 scratch。
- [x] 缓存 physics stiffness step 数组与求解 scratch，配置/点数不变时复用。

## 7. IK 伸直与回正（2026-07-16）

- [x] 缺省 softness 改为 0，保留显式旧数据。
- [x] 按 renderer/simulation owner/model/chain 恢复稳定 bend normal 状态。
- [x] 基于目标预测弯曲角实现 3°/20° rest-bend singularity/hysteresis 回正区间，并保留 pole/limits 优先级。

## 8. 二次验证

- [x] `compileJava compileClientJava`、`build`、`git diff --check`。
- [x] 静态核对热路径：`ModelIKApplier` 预热后的方向/容器路径不再显式 `new`，`ChainSolver` 显式分配点由 27 降到仅尺寸变化时的 stiffness 数组 1 处；逐帧拓扑集合已移除。
- [x] 更新游戏内性能/IK 复测矩阵、break-loop 记录与 Trellis spec。

## 9. 直线原角度恢复修正（截图复测）

- [x] 将 3° straight-restore 阈值收归 `IKSolver` 单点定义，并让 bend hint 判定复用。
- [x] 两骨解析解在恢复区间内输出精确共线的退化三角形，消除限制反方向的小负角。
- [x] 禁止 straight-restore 的内侧目标误触发反向 stretch。
- [x] 增加 0°/2°/3°/4° 数值回归探针，验证区间内共线、区间外恢复弯曲。
- [x] 重新运行 `compileJava compileClientJava`、`build` 与 `git diff --check`。

## 10. 最终渲染旋转约束修正（游戏内二次复测）

- [x] 对照 Exa 检索的 Blender/ozz IK 资料，区分位置解、局部旋转修正与最终关节约束。
- [x] 确认限制搜索的方向 Euler 与渲染器完整 swing+roll `orient` 存在契约分叉。
- [x] 在 cubic 与 BOBJ orientation 回写点钳制最终局部四元数，并用钳制结果推进子骨父坐标系。
- [x] 完整 `build` 与 `git diff --check`。
- [ ] 使用截图中的同一模型完成游戏内复测。

## 11. 视口骨骼选择同步

- [x] 追踪 stencil 拾取到 `UIModelForm`、活动面板和三种属性列表的事件链。
- [x] 普通点击与 Ctrl 点击优先转发给当前活动的 IK/物理/限制面板，姿态编辑器仅作为回退。
- [x] 追踪普通模型与 IK/physics 调试标记的 GPU 拾取 ID，确认上轮只修面板分发未触及上游失败。
- [x] 核对 cubic immediate、cubic VAO、BOBJ 与 picker shader 的 `Target + localIndex` 顺序契约；撤回未被证实的 shader/VAO 编码改写。
- [x] 对照 BBSFS 最新 `StencilMap`，确认 gizmo 已从 6 个移动句柄扩展为 19 个完整句柄，而迁移版仍从 7 开始分配骨骼。
- [x] 将表单/骨骼起始 ID 改为 `Gizmo.STENCIL_MAX + 1`，并登记全部 `Gizmo.Handle` 保留项。
- [x] `compileClientJava`、完整 `build`、picker shader JSON 解析与 `git diff --check`。
- [ ] 游戏内逐面板点击复测。

## 12. IK maintain-offset 与原姿态保持（2026-07-17）

- [x] 在 `ChainWorkspace` 增加 owner-local target/pole/tip-rotation 绑定基准与启用生命周期状态。
- [x] 将普通目标骨骼的控制量改为相对绑定位移；绝对影片 override 继续在权重 1 命中其目标。
- [x] 将 pole 从绝对瞄准改为相对绑定角度变化，并在其后叠加旧 `poleAngle`。
- [x] 零控制增量时直接保留本帧 FK，保证启用 IK 不改变原姿态。
- [x] orientation 构造保持 current-FK→solved 相对修正路径，覆盖 cubic 与 BOBJ；零输入不进入重建。
- [x] tip rotation 改为相对绑定旋转 delta，未移动控制器时保持 FK tip。
- [x] 完成静态数值不变量审查：预弯零输入与逐帧 FK 零输入均不写 `orient`；target/pole/tip delta、Pole Angle、weight、constraints 沿原求解路径组合。
- [x] `compileJava compileClientJava`、完整联网 `build` 与 `git diff --check` 通过。
- [ ] 使用同一只预弯手臂完成游戏内复测。
