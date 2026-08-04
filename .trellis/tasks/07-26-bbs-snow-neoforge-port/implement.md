# 执行计划（父任务）

父任务本身不写功能代码，只负责：确定推进顺序、把守子任务之间的集成 gate、最终整体验收。功能实现全部在 S1–S10 子任务中完成。

## 推进顺序

```
S1 骨架 + Mixin 基建  ←── 必须最先，且必须端到端验证通过
      │
      ▼
S2 核心数据层 + API 接口层  ←── 所有功能模块的地基
      │
      ├──► S3 PBR 与纹理分级
      ├──► S4 Cubic 渲染与动画
      ├──► S5 粒子扩展
      ├──► S6 UI 框架与迷你窗口 ──► S7 回放编辑器与关键帧
      ├──► S8 剪辑类型与快捷栏
      └──► S9 流体模拟 Form
                    │
                    ▼
            S10 Premiere 差异补齐 + 本地化 + 集成验收
```

S3–S9 相互独立，可并行。S7 依赖 S6（回放编辑器的浮层/内嵌检视建立在 UI 框架扩展之上）。

## 关卡

### Gate 0 — S1 之前：确认 BBS FSR jar 可用

```bash
cd new && ./gradlew jar
echo "EXIT=$?"
ls -l build/libs/BBS_FSR_beta_v0.0.10.jar
```

**不要用管道接 `tail`/`head`** —— 管道会吞掉 gradlew 的退出码，造成构建失败被读成成功（仓库已有事故记录）。每次构建都要独立回显 `$?` 并复核日志。

### Gate 1 — S1 完成后（最关键的一道）

S1 交付的不是"工程能编译"，而是"**Mixin 在游戏里真的注入进去了**"。验收：

- [ ] `cd bbs-snow-addon && ./gradlew build`，独立确认 exit code 0
- [ ] 产出 jar 中含 `META-INF/neoforge.mods.toml` 与全部 mixin config
- [ ] 启动 NeoForge 客户端，同时加载 BBS FSR 与本 addon
- [ ] 探针 mixin（注入 `mchorse.bbs_mod.ui.framework.UIBaseMenu`）的日志实际出现在游戏日志里
- [ ] 日志无 mixin apply 失败、无 `defaultRequire` 报错

**Gate 1 不通过就停止**，不要继续往下推 S2–S10。回到父任务重新评估落地形态（退路见 `design.md` §7），并把选择权交回用户。

### Gate 2 — S2 完成后

- [x] 23 个 api 接口全部编译通过，`bbs_snow$` 前缀统一
- [x] 12 个 common Mixin 与 5 个 client accessor Mixin 全部 apply 成功
- [x] `bbs_snow` 设置分类在游戏内可见，包含当前源插件的 20 个设置值与两个分区标题

S2 是地基，它不稳则 S3–S9 全部返工。这一关卡要实际进游戏看到设置面板，而不只是编译通过。

### Gate 3 — 每个功能子任务（S3–S9）完成后

统一标准，每个子任务自己跑：

- [ ] 该模块全部 mixin apply 成功
- [ ] 该模块功能在游戏内可见可操作
- [ ] 摘掉本 addon 后 BBS FSR 行为不变

### Gate 4 — S10 整体验收

按父任务 `prd.md` 的 AC1–AC7 逐条过。R3.1–R3.9 的逐项冒烟结果记录在 S10 的任务目录里。

## 回滚点

- 每个子任务独立提交，提交粒度对齐模块边界。
- `bbs-snow-addon/` 是全新目录，任何时候可整体删除而不影响仓库其余部分。
- **`new/` 零改动是硬约束**（AC7）。每次提交前跑 `git diff --stat new/`，有输出就是越界了。
- 工作树存在多会话并发修改 `new/` 的情况，提交时按文件挑选，不要 `git add -A`；编译失败先排除是否为他人改动所致。

## 验证命令

```bash
# 主 mod（依赖前置）
cd new && ./gradlew jar; echo "EXIT=$?"

# addon 构建
cd bbs-snow-addon && ./gradlew build; echo "EXIT=$?"

# 越界检查
git diff --stat new/

# 客户端启动（Gate 1/2/3 需要）
cd bbs-snow-addon && ./gradlew runClient
```

## 记录要求

- 每个子任务完成时，在其目录留下：实际改了哪些 mixin 的注入点、以及与 1.20.1 版本行为不一致的地方（若有）。
- 遇到 `old/` 与 `new/` 写法分歧时，记录取舍理由 —— 后续子任务会反复遇到同类问题。
