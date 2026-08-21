# 修复 PoseCurve 轴向轨道首次编辑与旋转失控

## Goal

修复 BBS-PoseCurve 1.21.1 在编辑 Pose/Transform 轨道时，首次修改某个轴向不生效，以及退出后再次编辑导致旋转增量重复叠加的问题。

## Requirements

- 轨道关键帧被通道重建后，属性面板必须重新绑定当前选区中的关键帧和 Transform 数据。
- 分轴编辑必须能在关键帧对象重建后继续定位面板对应的当前关键帧，不得因旧对象引用而丢失本次编辑或重复应用增量。
- 只修改 `BBS-PoseCurve-fsr-1.21.1` 插件侧；不改动旧版参考源码，也不改变 BBSFSR 的通用关键帧 API。
- 保留现有 Euler/Quaternion、PoseTransform 和普通 Transform 两条编辑路径。
- FSR 更新后，PoseCurve 的曲线图和自动手柄绘制必须允许当前帧没有任何顶点，
  不得因空 `BufferBuilder` 导致打开编辑轨道或编辑 VFX 跟随曲线进度时崩溃。

## Acceptance Criteria

- [x] 首次进入编辑轨道后，直接修改任一可见轴向，当前关键帧立即显示新值。
- [x] 提交一次关键帧重建后再次修改同一轴向，修改量只应用一次，人物不会沿拖动方向持续旋转。
- [x] 普通 Transform 与 PoseTransform 两种面板路径都使用同一套对象同步规则。
- [x] `testPoseCurveLogic` 通过，插件编译和 Mixin/Jar 审计通过。
- [x] 记录客户端 UI 手工冒烟验证的边界和未覆盖风险。
- [ ] 空姿势曲线图可以打开，普通曲线在没有自动手柄时可以渲染，均不抛出
      `BufferBuilder was empty`。
- [ ] PoseCurve 与 VFX 同时安装时，点击跟随曲线的编辑进度关键帧按钮不再崩溃。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
