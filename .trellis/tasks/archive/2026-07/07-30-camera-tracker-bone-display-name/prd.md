# 摄像机编辑器追踪器骨骼显示名一致性

## Goal

追踪器「选取肢体」展示的骨骼名称与 Pose 轨道及过滤轨道一致，使用友好显示名而非持久化 ID。

## Root Cause

`UIAnchorKeyframeFactory.displayAttachments()` 只用 `FormUtils.getForm()` + `getTrackName()` 解析子 form 名称，对常规骨骼直接显示原始 ID；而 Pose 轨道/过滤轨道使用 `BoneHierarchy.getLabels()`。

## Fix

以 `BoneHierarchy.getBoneIds()` 为主数据源构建附件列表（与过滤轨道/Pose 轨道一致），配合 `hierarchyLabels` 解析显示名；MatrixCache 中的子 form 条目补充添加，resolveId 兜底 + 尾段回退。

## Acceptance Criteria

- [x] 追踪器骨骼名与 Pose 轨道/过滤轨道一致
- [x] 子 form 路径仍可正常选择
- [x] 编译通过

## Commit

- `9fe77292` fix(ui): show bone display names in tracker attachment picker
