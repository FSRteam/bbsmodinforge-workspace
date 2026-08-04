# 修复 BBSFSR 世界实体伪装渲染与受击反馈

## Goal

记录并收尾 BBSFSR NeoForge 1.21.1 中围绕世界实体伪装渲染与演员模式受击反馈的修复工作。

## Requirements

- 世界实体、玩家伪装、演员实体、枪弹实体和世界模型方块的 form 渲染需要共享同一套矩阵隔离入口，避免渲染结果受到全局 model-view 恢复时机影响。
- form 渲染在恢复全局矩阵前必须 flush / endBatch，避免顶点在错误矩阵状态下提交。
- form 内部临时 LivingEntity 渲染需要递归保护，避免被世界实体伪装 mixin 再次替换。
- Minecraft camera 路径需要使用正确的 view matrix 语义，即从 camera rotation 取共轭后交给 BBS form 渲染。
- replay keyframe 同步到真实 ActorEntity 时需要保留真实 hurtTime，避免演员模式下原版受击红闪被每帧清掉。

## Acceptance Criteria

- [x] `BaseFilmController` 保留真实 `ActorEntity.hurtTime`，演员模式受击红闪持续时间已由用户确认恢复到接近原版表现。
- [x] 世界实体/player morph、演员实体、枪弹实体和模型方块渲染路径已接入统一 form 渲染入口。
- [x] form 渲染恢复全局矩阵前执行批次提交，降低错误 model-view 污染后续渲染的风险。
- [x] `FormRenderingContext` 和 `ParticleEmitter` 中 Minecraft camera overload 的相机矩阵语义已同步修正。
- [x] 已通过 `gradlew.bat compileClientJava`、`gradlew.bat build` 和 `git diff --check`。
- [ ] 世界中所有实体伪装随视角旋转/移动变化的问题仍需进游戏实测确认；本地 `runClient` 被 `bbs.client.mixins.json` 资源读取/解析失败阻塞。

## Notes

- 开发仓库为 `new`，对照仓库 `old` 未修改。
- 本任务用于记录当前 BBSFSR 修复工作，不包含 Voxy NeoForge 移植任务。
- 未主动创建 git commit。
