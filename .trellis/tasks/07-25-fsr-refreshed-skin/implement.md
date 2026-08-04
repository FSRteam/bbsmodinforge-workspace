# Implement: Refreshed 皮肤移植(父任务执行计划)

父任务只做:子任务编排、集成验收、spec/docs 终审。实现全部在子任务内完成。

## 执行顺序

- [x] 1. 创建四个子任务(见 design.md 拆分),各自补 prd/design/implement(2026-07-25 完成,四子任务三件套齐)
- [ ] 2. 子任务 refreshed-theme-tokens 先行(其余三个依赖其 token)
- [ ] 3. dashboard-autohide / motion-additions / visual-details 可并行推进
- [ ] 4. 集成验收(全部子任务归档后):
  - [ ] `cd new && ./gradlew check` 真实 exit code == 0(禁管道,`*> log; REAL_EXIT`)
  - [ ] themeCoreTest 对拍:dark 主题所有新 token == 旧常量;四旧主题 motion 新 entry 全关
  - [ ] docs/theme-spec README+example-theme 覆盖全部新 key;example jar 逐字节同步测试绿
  - [ ] 用户游戏内冒烟:refreshed 主题观感、Ctrl+S 切换与保存优先、任务栏滑出/唤回、
        通知/toggle/选区/tooltip 各动效、图标 atlas 切换、Refreshed 设置分类、camera/replay/action
        按钮活跃态和底部列表圆角选中态、切回 dark 与旧版无差异
- [ ] 5. spec 更新(bbs-ui-theming.md 增补新 token/entry 规则)、父任务 commit、归档

## 验证命令

```powershell
cd new; ./gradlew check *> ../check.log; "REAL_EXIT=$LASTEXITCODE"
```

## 回滚点

每子任务独立 commit;新 token/motion entry 未配置一律回退旧行为,单独 revert 安全。
