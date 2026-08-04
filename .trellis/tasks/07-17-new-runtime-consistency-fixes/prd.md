# new 实操逻辑一致性系统修复

## Goal

修复 2026-07-17 对 `new/` 当前工作树进行多代理审查后确认的实操、权限、生命周期、渲染、迁移和规范一致性问题，使 NeoForge 1.21.1 视频模组在单机、多人服务器、影片编辑、动作录制、粒子/模型模拟和 Addon 扩展路径上具有可预测且安全的行为。

## Confirmed Facts

- 审查基于 `new` 当前工作树，而不是仅基于 `HEAD`；现有未提交 Web Remote、Film collaboration、surface mirror 和 IK 改动属于用户工作，必须保留。
- `temp/bbs-fs-2.3@65bb8c93` 是同 Minecraft 版本参考基线；FSR 迁移标记与 legacy 粒子政策以 `.trellis/spec/new/backend/fs-migration-baseline.md` 为准。
- 网络 payload `s15/c18/c19` 已进入批准基线，本任务不得回滚或重编号；只同步过时台账。
- Addon/API 版本保持精确 `2.0`，不得破坏 v1 addon、旧 API 2.0 addon、dedicated server 或现有序列化数据。
- 用户明确要求派出代理修复审查中列出的全部问题。

## Requirements

- 消除影片仓库路径逃逸、跨玩家分片状态污染、无权限影片暂停、无距离模型方块写入等服务端信任边界缺陷。
- 使枪、方块交互、横扫攻击和实体右键录制/回放遵循 NeoForge/原版实际消费分支，不重复执行或丢失目标语义。
- 使本地/远程 UI 输入具有单一手势所有者；换屏、断线、释放和不同鼠标按钮不得混合或泄漏旧状态。
- 断线前保持远程影片 repository 身份，禁止延迟保存改写到本地同名影片；恢复用户原 HUD 状态。
- 修复粒子首次定位事件风暴、碰撞重新武装、raw JSON sibling 保留、输入焦点及 Actor/ModelBlock 粒子/Trail 坐标空间。
- 修复 IK/physics 最终旋转约束、pole 重绑、softness 默认值、nested/first-person simulation owner/space 和 Ctrl 选骨 owner。
- 补齐 FS 1.21.1 已有的影片编辑器交互与布局修复，并修复 Replay 删除索引及关键帧快捷键空指针。
- 修复 Addon first-wins、注册窗口、异常隔离、late client registration 和诊断生命周期；保持批准的网络 ID。
- 让 FFmpeg 早退/管道失败进入失败终态，取消不再表现为完成，并正确解析安装根目录。
- 修复实体 tracking range 单位、provider 路径边界和默认英语缺键。
- 新增与每项修复相匹配的自动回归；无法自动化的场景写入明确手测矩阵。

## Acceptance Criteria

- [ ] 所有审查确认项均映射到一个子任务，并被修复、明确降级或用代码反证关闭。
- [ ] 服务器端路径始终规范化并验证位于根目录内；普通玩家无法越权暂停影片、写模型方块或污染他人分片传输。
- [ ] 枪的主/副手消费、owner、方块交互录制、攻击目标和实体交互回放与实际 NeoForge 分派一致。
- [ ] 远程输入、本地输入、换屏、断线和 held-state 清理在自动测试中保持单一 owner 且无旧 screen 事件。
- [ ] 远程影片断线不丢改动、不写本地同名库；HUD、导出和 UI 资源终态成对恢复。
- [ ] 粒子 distance/collision/JSON、Actor/ModelBlock 空间和 Iris/GL 状态回归被自动或手动覆盖。
- [ ] IK tip/physics 最终 quaternion 均受限；pole、softness、owner、first-person 与选骨行为满足 quality spec。
- [ ] Replay 删除引用、关键帧快捷键及影片时间轴/Gizmo/滚动行为通过回归验证。
- [ ] Addon duplicate/late/error 路径满足 first-wins 和窗口合同；API 版本仍为 `2.0`，NetworkCompat ID 不变。
- [x] `git diff --check`、`compileJava`、`compileClientJava`、定向测试、`check` 和 `build` 全部成功。
- [x] 未覆盖、格式化或提交无关用户改动。

## Out of Scope

- 改变公开 Addon/API 主版本、网络 payload 编号或持久化格式。
- 重新引入已隔离的上游新粒子系统。
- 与审查问题无关的大规模架构重写或 UI 重设计。

## Open Questions

- None. 用户已批准进入实施，兼容和范围由现有规范确定。
