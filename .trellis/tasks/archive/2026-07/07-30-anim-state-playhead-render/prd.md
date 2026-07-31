# 动画状态编辑器时间线播放头渲染异常

## Goal

修复动画状态编辑器时间线轨道区域播放头渲染异常，使用户能清晰辨识当前播放头位置。

## Root Cause

`UIAnimationStateKeyframes.renderBackground()`（渲染第 4 步）绘制播放头，但 dope sheet 的 `renderGraph()`（第 5 步）为每行绘制不透明背景矩形，覆盖了 1px 播放头竖线。

## Fix

将播放头渲染从 `renderBackground()` 移到 `renderOverlay()`（第 7 步），并包裹 `clip(graphArea)/unclip`，与 `UIFilmKeyframes` 一致。

## Acceptance Criteria

- [x] 播放头竖线在时间线区域清晰可见，不被轨道行背景覆盖
- [x] 播放头标签正常显示
- [x] 未引入回归（testUiMotions 的环境性报错与本次无关）

## Commit

- `c363c848` fix(ui): draw animation state playhead in overlay to prevent row-bg overwrite
