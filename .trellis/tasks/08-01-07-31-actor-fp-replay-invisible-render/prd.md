# 演员 + 第一人称回放组合渲染不可见

## Goal

影片编辑器中，同时启用「演员」与「第一人称回放」（fp）的回放应保持可见，且 gizmo 继续跟随锚点。

## Requirements

- 定位 actor+fp 组合下回放渲染不可见的根因。
- 修复后，actor+fp 回放在影片编辑器中可见（编辑器 Form 渲染保留），gizmo 锚点跟随不回归。
- 非 fp 的 actor 回放行为不变（跳过编辑器 Form、由世界 ActorEntity 渲染）。
- 保留骨骼选择定位 gizmo 的能力。
- 停止于提交前，等待用户构建与运行时确认。

## Acceptance Criteria

- [ ] 影片编辑器中 actor+fp 回放可见（编辑器 Form 渲染）。
- [ ] 纯 actor（非 fp）回放仍由世界 ActorEntity 渲染，无双重渲染。
- [ ] gizmo 在 actor+fp 回放上仍跟随锚点。
- [ ] 源码级检查覆盖 fp 排除逻辑。
- [ ] 用户本地构建与运行时验证通过后才提交。

## Out of Scope

- 第一人称相机/视角行为本身。
- 非渲染相关的 fp 回放逻辑。
