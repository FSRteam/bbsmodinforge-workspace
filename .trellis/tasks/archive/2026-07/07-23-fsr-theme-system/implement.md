# Implement: FSR 主题/皮肤系统 —— 跨子任务执行计划

父任务不直接写代码;本文件定义子任务的执行顺序、每步的验收 gate 与回滚点。
单个子任务内部的执行清单见各自的 `implement.md`。

## 执行顺序与 gate

### 阶段 0:规范先行(已完成于规划期)
- [x] `new/docs/theme-spec/README.md` + `example-theme/` 由规划会话产出。
- 后续所有子任务以该规范为数据格式契约;改规范须在对应子任务文档记录理由。

### 阶段 1:theme-core(串行,其余任务的地基)
- [x] 按 `07-23-theme-core/implement.md` 执行(2026-07-24,本会话完成,原计划交 GPT 改为
  Claude 主会话执行 + haiku 子代理分担机械批)。
- **Gate 1 ✓**:`compileJava` + `compileClientJava` + `testThemeCore` + `./gradlew check`
  全量通过;dark/light JSON 与旧常量逐值对拍有单测把关;迁移单测 5 例通过。
  (视觉逐像素与迁移的**游戏内**确认待用户冒烟。)

### 阶段 2:widget-tokens → motion-layer → skin-settings-ui(实际按此串行)
- [x] widget-tokens:按常量类别分批(47 处语义常量 + 33 处文字色 + 5 控件哨兵 +
  Batcher2D 单点 + 37 处 `primaryColor.get()` 直连修复);framework 语义常量残留 0。
- [x] motion-layer:UITween/UIMotions 基建 + 6 接入点全部落地,render-only 红线自查
  记录在其 implement.md;`testUiMotions` 单测。
- [x] skin-settings-ui:主题选择器 overlay(色板行/来源标记/坏主题通知)+ themeId 行特例 +
  皮肤分类头部操作菜单 + accent 联动 + en/zh 文案(键集合 diff 相等)。
- **Gate 2 ✓**:`./gradlew check` 全量通过(每个子任务完成时各跑一轮,含全部输入测试)。
  恒等性由"位级恒等替换规则 + isSettled 短路直达旧路径"保证;逐帧视觉对比待用户。

### 阶段 3:builtin-packs(收尾)
- [x] 内置 example(与 docs 逐字节同源,对拍测试把关)+ 演示主题 amber(三层差异断言)+
  规范对齐校验清单(逐 key 全勾,遗留不一致零)+ 内置清单更新。
- **Gate 3(最终集成验收)**:代码侧全部通过;游戏内冒烟见下。

## 集成验收命令

```bash
cd new
./gradlew compileJava compileClientJava   # 快验
./gradlew check                            # 全量(每阶段 gate)——2026-07-24 全绿
./gradlew runClient                        # 人工冒烟:切主题/坏 JSON/重载/动画开关
```

## 人工冒烟清单(最终)——**待用户游戏内执行**

1. 设置 → 皮肤:主题列表含 dark/light/example/amber;切换即时生效。
2. 拷贝 `docs/theme-spec/example-theme` → `config/bbs/assets/themes/example`(同 id 覆盖内置)
   → 重载 → 出现"外部"标记并可用。
3. 把 example 的 theme.json 改坏(删一个引号)→ 重载 → 不崩溃、回退 dark、有通知与日志。
4. 强调色:跟随主题开关两种状态 + 拾色器改动自动解除跟随。
5. 动画:6 接入点默认有过渡;全局开关关→行为与老版本一致;速度倍率 0.5/2 可感知;
   amber 主题弹性缓动可辨。
6. 老配置迁移:用迁移前的 `config/bbs/settings/bbs.json`(theme:0 和 1 各一份)启动,视觉不变。
7. 远程会话(bbs-web-remote)连上后操作 UI,无输入异常(动画红线回归)。

## 完成后

- [x] Spec 更新:`.trellis/spec/frontend/bbs-ui-theming.md`(语义色规约 + 热路径红线),
  index.md 已挂。
- [ ] 各子任务归档(`task.py archive`)——待用户冒烟确认后。
- [ ] 提交:分子任务提交(theme-core / widget-tokens / motion-layer / skin-settings-ui /
  builtin-packs 五个 commit 粒度),**须用户确认后执行**;未经确认不 commit(仓库惯例)。

## 执行注记(2026-07-24)

- 执行者变更:GPT(codex)不可用,改由 Claude 主会话实现;机械替换批派发 3 个
  haiku 4.5 子代理(精确 old/new 清单驱动),判断/架构/验收在主会话。
- 计划外修复:发现 37 处 `BBSSettings.primaryColor.get()` 直连引用绕过 accentFollowsTheme
  (含 UIButton 背景这一最大 accent 消费者),统一收敛到新增的 `accentColorRGB()`。
- 视觉类验收(截图逐像素、动画手感、远程会话)在本环境不可执行,全部标注待用户;
  代码侧等价性以"位级恒等规则 + 对拍单测 + 全量 check"覆盖。
