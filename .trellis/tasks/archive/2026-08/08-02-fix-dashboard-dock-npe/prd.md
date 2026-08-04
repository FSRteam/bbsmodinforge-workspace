# 修复 UIFilmPanel 构造期 dock 未初始化导致仪表盘 NPE 崩溃

## Goal

模型方块右键 / b、0 键打开仪表盘时，`UIFilmPanel` 构造期间 `UIReplaysEditor` 过早解引用 final 字段 `dock`
（构造器第 252 行才赋值）导致 NPE：右键模型方块无响应、b/0 键直接崩溃。修复构造顺序，
改动提交到 `new/` 仓库的 PR 分支 `fix/bugfix-batch`（用于汇集多个问题修复）。

## Requirements

- `UIFilmPanel` 构造器在创建 `UIReplaysEditor` / `UIReplaysListPanel` 之前必须已有非空 `dock` 实例，
  因为它们的构造器会通过 `getEditPanelTopOffsetPx()` 读取 `dock.isLocked()`。
- `final` 字段 `dock` 保持 final，构造器内只赋值一次（不可二次赋值）。
- 布局接线（`relative/source/addPanel/mount`、`editor.add(dock)`）的时序与现状保持一致。
- 本任务为轻量任务，PRD-only，不新增 design.md / implement.md。

## Acceptance Criteria

- [x] `UIFilmPanel.<init>` 中 `this.dock = new UIDockLayout()` 位于 "/* Editors */" 创建块之前，
      且第 252 行原赋值被移除；`compileClientJava` 编译通过。
- [x] 运行时已验证：放置的模型方块右键能打开模型面板；b / 0 键能正常打开仪表盘不崩溃。
- [x] 改动已以单提交 `46613ff5` 落在 `new/` 分支 `work/mcaxolotl-ui-defect-batch`（原 `fix/bugfix-batch`，PR #17）。

## Notes

- 崩溃日志：`new/runs/client/crash-reports/crash-2026-08-02_14.15.33-client.txt`
- 《分析、时序、修复方案》见会话记录；两个用户症状（右键无效、b/0 崩溃）为同一根因。