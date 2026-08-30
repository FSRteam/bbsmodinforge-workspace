# BBS多贴图模型方块渲染性能优化

## Goal

TBD.

## Requirements

- TBD

## Acceptance Criteria

- [ ] TBD

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
# BBS 多贴图模型方块渲染性能优化

## 目标

降低 `new` 版本中模型伪装和模型方块在多材质/多贴图、高面数场景下的客户端渲染开销，重点缓解开启光影时的卡顿，同时保持模型外观、动画、IK、物理、拾取和透明材质语义不变。

## 已确认事实

- `ModelFormRenderer` 将模型交给 `ModelInstance.render`，Cubic VAO 路径按模型组和材质逐个绘制。
- `CubicVAORenderer` 每个材质批次都会解析/绑定纹理，并进入一次 `ModelVAORenderer.render`；后者每次读取 VAO 状态、设置 shader、apply、绘制、clear。
- BOBJ 路径按网格逐个绑定纹理和绘制；每个网格都调用 `setupMatrices` 后的矩阵比较与可能的 CPU 蒙皮。
- 当前 `BOBJModelVAO` 已缓存上次上传的骨骼矩阵，但跨网格仍重复做相同的矩阵变化检查，且延迟透明命令会重复复制骨骼快照。
- 现有渲染规范禁止通过降低动画、IK、物理或约束采样频率来换取性能；不变骨骼矩阵时应复用已上传的几何。

## 需求

1. 对同一纹理的多个模型 VAO 绘制进行批处理：一次绑定纹理和 shader 生命周期，保留每个组自己的模型矩阵、法线矩阵、颜色和光照。
2. 对多材质模型缓存当前帧的材质纹理解析结果，避免同一材质/纹理重复加载和绑定；纹理变化时仍立即切换。
3. BOBJ 多网格渲染在一次模型绘制中只建立一次骨骼矩阵快照和变化判断；矩阵未变化时所有网格跳过 CPU 蒙皮和 GPU 上传。
4. 延迟透明路径继续保持远近排序、深度写入、光影兼容和纹理独立性，不能因为合批导致材质串色或透明面错误混合。
5. 模型方块世界渲染、普通实体伪装、影片/预览和拾取路径共享优化，不改变 UI 预览专用逻辑。

## 验收标准

- 多材质 Cubic 模型的同纹理 VAO 在一次 shader 生命周期内绘制，且每个 VAO 的矩阵、法线、颜色、光照正确。
- 同一帧同一纹理不会重复执行无意义的纹理解析/绑定；不同纹理仍按材质边界切换。
- BOBJ 多网格在骨骼矩阵不变时不重复 CPU 蒙皮或上传；矩阵改变时仍完整更新所有受影响网格。
- 拾取、透明贴图、Iris 光影和 glint 路径保持原有结果；不引入跨材质纹理泄漏。
- 至少有源码/逻辑回归覆盖合批边界、纹理切换、BOBJ 快照复用与透明延迟路径。
- `new` 工程完成 `compileClientJava`，并执行完整 `build`。

## 非目标

- 不降低动画、IK、物理、约束或形态键的更新精度。
- 不引入 GPU Compute、跨实例共享独立姿态状态或后台线程访问 Minecraft 世界。
- 不重写模型文件格式和材质数据格式。

## 开放问题

- 用户提到的“参考模型”未在请求中给出文件名；先以仓库内 BOBJ 和 Cubic 多材质模型作为覆盖范围，保持与现有材质解析契约兼容。
