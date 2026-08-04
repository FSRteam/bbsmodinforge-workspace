# S7 回放编辑器与关键帧扩展

## Goal

在独立 `bbs-snow-addon` 中把源插件的回放编辑器、关键帧、姿态/变换与动画导出能力移植到 NeoForge 1.21.1，并与当前 BBS FSR 已有的姿态标签、IK/物理轨道、S2 Actions 数据契约和 S6 迷你窗口系统共存。

本阶段以“功能可见、关键路径可执行、严格客户端不出现 Mixin 注入失败”为交付目标；涉及真实鼠标手感、复杂影片工程和第三方模组联动的完整人工验收留在 S10 汇总。

## Confirmed Facts

- S2 已提供 Actions timeline 数据层、PBR/骨骼纹理数据、duck API 与设置项；S6 已提供 `IMiniWindowDockHostRef` 和回放/关键帧 inspector 的重挂边界。
- 当前 FSR 已内建姿态标签展开、IK/物理轨道、关键帧选择与 Film gizmo，S7 只能做增量扩展，不能以源插件实现覆盖现有行为。
- 22 个候选 S7 Mixin 的目标类均存在于当前 BBS FSR jar；多数命名方法仍存在，但 `UIActionsConfigEditor` 和 `UIReplaysEditor` 的合成 lambda 已明显漂移。
- 用户已确认客户端可启动且大多数设置/功能可见，但具体交互可用性未知；该反馈属于 S10 人工验收基线，不代表 S7 可跳过运行验证。

## Requirements

### R1 Actions 时间轴

- 将 Actions 关键帧起止标记、配对参数同步、overlay 显示、删除/清理和颜色/图标扩展接入当前 FSR。
- Molang shared 控件在 Form 编辑器和 Actions 关键帧编辑器中保持可见，并复用 S2 的 `ActionTimelineConfig`、`ActionTimelineEvaluator` 与 `ActionsTimelineEditorContext`。
- 不覆盖 FSR 自己的 Actions 编辑器注册或关键帧工厂。

### R2 回放与关键帧层级

- 在当前姿态标签体系上增加骨骼子层级折叠、折叠状态和骨骼优先轨道选择。
- body-part pose 轨道获得“动画转姿势关键帧”入口；生成的关键帧继承可表示的动画插值。
- Replay、clip 和 keyframe inspector 在相关列表重建或选择变化后，继续由 S6 的浮动/停靠宿主正确重挂。
- 保留当前 FSR 的 IK、物理、声音、PBR 和其他既有轨道，不改变其排序、选择或上下文菜单。

### R3 姿态、纹理与变换

- 姿态编辑器提供骨骼纹理选择和纹理分级编辑入口，并在普通 Form 与关键帧工厂宿主中正确同步。
- 变换编辑器提供 pivot 控件；普通 `UITransform` 只显示/填充，`UIPropTransform` 写回数据并触发现有回调。
- 轨道显示名支持源插件的自定义 Form track name，但未配置时必须沿用 FSR 原返回值。

### R4 动画导出与预设工具

- 关键帧上下文菜单可打开动画导出面板，支持 pose/bone tracks 和可选 IK bake。
- 动画文件的列出、重命名、删除、复制、粘贴、翻转和写回不损坏同一 JSON 中的其他动画。
- Film preview 提供骨骼优先入口；预设数据菜单复用 S2 `PresetDataOperations` 提供重命名/移除，复制、粘贴、重置和保存继续由 BBS 原菜单负责。
- 所有新增界面文本从 `bbs_snow` 本地化资源解析，不恢复旧的总入口 `SnowUIKeys`。

### R5 兼容与严格性

- 继续使用 `defaultRequire: 1`、无 refmap、精确描述符和 `bbs_snow$` 成员前缀。
- Mixin config 声明包下只放 Mixin/Accessor；所有 helper、key 和工具类放在非 Mixin 包。
- 不修改 `new/`，不引入 Fabric 运行时依赖，不重复迁移 S8/S9/Premiere 功能。

## Acceptance Criteria

- [ ] AC1 `verifyBbsCoreJar` 和 `build` 真实退出码均为 0，产出 jar 不内嵌 `mchorse/bbs_mod/**`。
- [ ] AC2 新增 22 个 S7 Mixin/Accessor 均被严格客户端准备并成功应用；日志无 injection failure、`defaultRequire` 错误或 helper 类加载错误。
- [ ] AC3 自动验证覆盖 Actions 成对标记/同步/删除、骨骼折叠与优先选择、inspector 重挂、pivot 写回和动画 JSON 非目标内容保留。
- [ ] AC4 严格客户端能够构造并打开 S7 关键界面；Actions、骨骼折叠/优先、导出、纹理选择、pivot 和预设操作入口可见。
- [ ] AC5 动画导出至少验证 pose、bone tracks 和 IK 分支的输入检查及成功/失败语义，不静默覆盖现有动画。
- [ ] AC6 英文和简体中文新增键均可解析，jar 中包含对应资源与折叠图标，禁止项为 0。
- [ ] AC7 `git diff --stat -- new bbs_FSloveCML_1.21.1` 不包含本任务改动；移除 addon 时 BBS FSR 行为不受影响。
- [ ] AC8 真实拖拽、复杂回放工程、完整视觉/手感和第三方联动未能自动证明的项目，明确登记到 S10 人工验收清单。

## Out of Scope

- `UIReplaysEditorExtended`、`BoneTextureUtils`：源端无调用者，不移植。
- `UIQuickReplayOverlayPanel` 与 `ClientEventHandler`：注册链未被调用，避免在移植中擅自激活源端死功能。
- `TogetherClientNetworkReplaySelectionMixin`：依赖合成 lambda 且是第三方可选契约，留给 S10 按当前 Together API 单独适配。
- `UIScreenMixin`：旧音频拖入会绕过当前 FSR 的安全导入/发布契约。
- S8 剪辑类型、S9 流体、Premiere 差异补丁，以及任何源插件不存在的新功能。
