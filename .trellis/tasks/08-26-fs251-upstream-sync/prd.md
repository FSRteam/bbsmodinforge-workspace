# BBS FS 2.5.1 增量同步到 FSR

## 目标

将 `Wemppy4/bbs-fs` master 从已同步基线 `8d99d77feea3ad13f7ceada78ee7679571251bd4` 到
2.5.1 终点 `73b8b3e4d1b3266eec801104924c80ab776267ef` 的新增行为移植到独立工作区
`new-fs25-sync`。

调研已完成，当前进入源码迁移阶段。差异报告见 `research/fs-1.20.1-to-2.5.1-delta.md`。

## Commit markers

| Marker | Value |
|---|---|
| FS 1.20.1 source start | `4cdee4ace033f12c450976bb8f9707f3cfa77092` |
| FS 1.20.1 remote terminal | `165fa591c08acab522c3879a453caf0a2a6c02d6` |
| FS master functional terminal | `73b8b3e4d1b3266eec801104924c80ab776267ef` |
| FSR worktree start | `f0aa738d3c99fe70b25a51729b545e019ee834fc` |
| FSR terminal | pending final build/commit |

## 范围

- 移植 10 个代码提交：透明队列开关/渲染排序与 Fabulous! FBO、录制标记等待、纹理失败占位、
  Ctrl+Shift+V 绑定、菜单失焦、数值输入即时刷新、关键帧预览开关、按键可视化。
- 保留 FSR 的 NeoForge 1.21.1 API、渲染队列扩展、多语言结构和现有未提交改动。
- 不复制上游 `gradle.properties` 的 `mod_version=2.5.1`；FSR 继续使用 `0.0.11`。

## 验收标准

1. 上述行为在 `new-fs25-sync` 中可编译并与 FSR 现有实现兼容。
2. 设置默认值和新增本地化键存在，旧配置迁移不丢值。
3. `git diff --check` 通过，相关定点测试/编译通过。
4. `new-fs25-sync\gradlew.bat build --no-daemon --console=plain` 成功，生成现有 FSR JAR。

## Implementation constraints

- Only modify the independent `new-fs25-sync` worktree and this Trellis task record; do not modify `new/`.
- Preserve existing dirty changes in `new-fs25-sync`; stage only files owned by this task.
- Do not copy upstream Fabric mappings or the upstream `mod_version=2.5.1`.
