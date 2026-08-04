# 网络与服务端权限修复

## Goal

封闭影片仓库、分片传输和服务端 mutation 的信任边界，防止普通玩家越权、路径逃逸、跨连接污染和内存驻留。

## Requirements

- 规范化并约束 FilmManager 的 id/from/to/folder 路径到仓库根内。
- PacketCrusher 按玩家/连接、通道和 transfer id 隔离，限制全局与单连接在途数量/字节并支持超时、断线清理。
- pause film、share form、model block save 在 mutation 时验证权限、目标、距离、维度和编辑会话。
- disconnect/reset 清理客户端 manager callbacks；修正不可达的 legacy receiver 注册语义或明确拒绝合同。
- 保持现有 payload ID 和编码顺序。

## Acceptance Criteria

- [ ] `../`、绝对路径、同名前缀目录和根目录边界均被测试并拒绝。
- [ ] 两玩家/两通道同 transfer id 不冲突；超限、超时和断线释放可验证。
- [ ] 非授权玩家不能暂停影片、强制目标切面板或远程修改模型方块。
- [ ] 网络定向测试、编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
