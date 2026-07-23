# 视频导出失败语义修复

## Goal

让视频导出在 FFmpeg 成功、失败、取消和断线时进入准确且幂等的终态。

## Requirements

- FFmpeg 启动后早退、首帧/中途 pipe failure 和非零 exit 能通知 session 并 teardown。
- recording、PBO、进程、临时音频和 UI 状态在所有终态清理一次。
- 完成音/打开目录只在真实成功且 exit=0 时执行。
- encoder 根目录正确解析 `<root>/bin/ffmpeg(.exe)`，不使用 pathSeparator 拼文件路径。

## Acceptance Criteria

- [ ] fake process/非法参数覆盖 startup、pipe、nonzero exit、cancel、disconnect。
- [ ] 取消/失败无完成副作用；成功仍保持原体验。
- [ ] `testVideoExportUtils` 扩展测试、编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
