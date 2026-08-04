# Research 目录说明

本目录存放该任务的 Trellis research 成果，**按 Trellis 约定只保留 `.md` 总结文件**。

> Trellis 工作流的设计：`research/` 一个文件对应一个研究主题，用 markdown
> 记录结论与引用；原始运行时调试输出（游戏 log、gradle log 等）不在此层，
> 不应被 `implement.jsonl`/`check.jsonl` 登记注入子代理上下文。

## 历史原始日志已归档

该任务在 2026-07-27 ~ 2026-07-29 调试期间产生了大量原始运行时日志
（`runtime-fix*-*.log`，单文件 0.7–2.2 MB，总计约 13 MB）。
为避免恢复任务上下文时被整目录读入导致 200k token 爆掉，这些 `.log`
已于 2026-07-30 归档到仓库内的：

```
.archive/tasks/07-26-fix-irlite-connector-mixin-compatibility/research-logs/
```

需要回看原始日志时，从该路径取；正式调试结论以本目录的
`*.md` 总结为准（`diagnosis.md`、`runtime-fix*-validation*.md` 等）。

## 内 `BeamInterpolation`/`SpotMatrix` 调试相关

`runtime-fix8-*.log` 对应 BeamInterpolation 与 point-spot matrix 调试链，
当前结论见 `runtime-fix8-beam-matrix-validation.md`（本目录保留）。
