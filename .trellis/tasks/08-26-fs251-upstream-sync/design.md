# 设计：FS 2.5.1 增量移植

## 边界

上游 master 是 Fabric 1.20.4，不能直接 cherry-pick 到 NeoForge FSR。以提交 patch 为行为
规格，在 `new-fs25-sync` 当前工作树上按文件手工合入；与既有 FSR 改动冲突时保留 FSR 的
API、队列状态捕获和扩展构造函数，只吸收上游新增语义。

## 处理策略

- 透明队列：将上游设置开关、Fabulous! 主帧缓冲绑定和四边形平面排序接到 FSR 现有队列；
  不删除 FSR 的 shader-color/lightmap/overlay 捕获。
- 录制/纹理：按上游方法拆分等待标记和失败占位集合，保持 NeoForge 类型及线程模型。
- UI：在 FSR 现有 `UIBaseMenu`、数值输入和关键帧预览 API 上加入焦点清理、手势刷新、
  `inside()` 限定与设置门控。
- 版本：忽略上游版本号提交，保持 `mod_version=0.0.11` 和现有产物名。

## 风险控制

每个逻辑组先做源码编译或定点测试，再进行最终完整构建；不触碰 `new/` 主工作树和其它
用户未提交文件。若上游 patch 与 FSR 扩展冲突，以当前 FSR 的调用约定为准并在任务记录中说明。
