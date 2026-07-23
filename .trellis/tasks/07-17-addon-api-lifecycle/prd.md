# Addon API生命周期修复

## Goal

让 Addon API v1/v2 的 duplicate、窗口、异常和 client 生命周期满足 first-wins 与隔离合同。

## Requirements

- 后注册同 ID addon 的失败/拒绝诊断不得改变首个赢家状态。
- v1/v2 跨来源 duplicate 只运行一个实现。
- registration context/facade 在 phase 后失效；bridge 与关闭 external window 原子化。
- v1 supplier/反射边界捕获 LinkageError；null supplier 产生 warning/diagnostic。
- client renderer late registration 明确拒绝；client phases/来源进入诊断。
- 修正 legacy receiver 不可达合同；同步 NetworkCompat 旧台账但不更改批准 ID。

## Acceptance Criteria

- [ ] duplicate 异常、跨 v1/v2、retained context、late client、LinkageError 均有回归测试。
- [ ] first-wins、窗口关闭和诊断结果与文档一致。
- [ ] API 仍为 2.0，`s1..s15/c1..c19` 映射不变。
- [ ] dedicated server、编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
