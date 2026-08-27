# 技术设计：多贴图模型批处理与 BOBJ 跨网格缓存

## 边界

改动集中在 `new/src/client/java/mchorse/bbs_mod/cubic` 的模型实例、Cubic VAO 渲染器、BOBJ VAO 渲染器和必要的窄范围测试。模型资源解析、Form 序列化和世界实体生命周期不改动。

## Cubic 数据流

`ModelFormRenderer` 继续负责求解姿态并调用 `ModelInstance.render`。`CubicVAORenderer` 在遍历模型组时先生成轻量 draw descriptor（VAO、纹理、modelView、normalMat、颜色、light、overlay），按连续纹理批次提交。每个纹理批次只执行一次绑定和 shader apply/clear；批次内对每个 descriptor 更新对应 uniforms 并调用 VAO 的无状态绘制入口。

批次只合并相同 `Texture` 对象且不跨越透明/延迟命令边界。需要 `FormTranslucentQueue` 的材质仍单独创建命令，以保留每材质纹理和深度策略；拾取路径保持同样的材质分割，避免 stencil 颜色串联。

## BOBJ 数据流

`ModelInstance.render` 在 BOBJ 分支中调用一次 `armature.setupMatrices()`，建立本次 render 的 `Matrix4f[]` 快照。每个 `BOBJModelVAO.updateMesh` 使用该数组，并由 VAO 自己判断是否与上次上传相同。延迟命令引用同一帧不可变矩阵快照；只有在需要跨帧持有时才复制，避免每个网格重复分配。

BOBJ 绘制可按纹理复用 shader 生命周期，但仍以网格的矩阵/法线矩阵为 uniform 边界。若某个网格进入透明拆分或整段延迟，则结束当前即时批次后再排队，不能与即时网格混合。

## 兼容性与回滚

- 纹理 resolver 返回 `Link`，解析后使用身份相同的 `Texture` 作为批次键；动画纹理每帧变化时自然产生新键。
- 缺失纹理继续使用 `TextureManager` 的错误纹理/当前绑定回退，行为与现有代码一致。
- 任何批次构建异常可回退到现有逐 VAO 绘制入口；优化不改变模型文件和外部 addon API。
- Iris 仍通过 `ModelVAORenderer.setupUniforms` 接收每个 descriptor 的矩阵和光照；不会把 Iris 状态缓存到跨帧。
