# motion-engine 执行清单

前置阅读顺序:本任务 `prd.md` → `design.md` → 父任务 `design.md` → `.trellis/spec/frontend/bbs-ui-theming.md` → `new/docs/theme-spec/README.md`。

## 阶段 1:UIThemeMotion 扩字段 + 解析

- [ ] `new/src/main/java/mchorse/bbs_mod/ui/themes/UIThemeMotion.java`:按 design.md §3 扩字段(保留既有构造器兼容或全量替换构造点,构造点仅在 ThemeParser 与测试)。
- [ ] `new/src/main/java/mchorse/bbs_mod/ui/themes/ThemeParser.java`:动画条目解析加 `type/response/damping`(坏值逐 key 回退,不抛出);`defaultDark` 全部条目保持 EASE。
- [ ] `new/src/themeCoreTest/.../ThemeCoreTest.java`:spring 键解析用例(合法 / 坏值回退缺省 / 钳制边界 / 无 type 时为 EASE)。
- [ ] 验证:`cd new && ./gradlew testThemeCore > /tmp/t1.log 2>&1; echo REAL_EXIT=$?`(必须 REAL_EXIT=0 且日志含 all tests passed;**禁止管道结尾判断成败**)。

## 阶段 2:UITween 弹簧内核

- [ ] `new/src/client/java/mchorse/bbs_mod/ui/utils/motion/UITween.java`:内部加 `velocity` 与 spring 分支(design.md §1 三分支闭式解、§2 语义约定);接口签名不动。
- [ ] `new/src/client/java/mchorse/bbs_mod/ui/utils/motion/UIMotions.java`:speed 缩放 response(§1 末)、`duration()` spring 近似(§3)。
- [ ] 注意:UITween 在 client source set,但 UIMotionsTest 在裸 JVM 跑 —— 不得新增任何触发 `BBSMod.<clinit>` 的静态依赖(现状安全,保持)。

## 阶段 3:测试扩充

- [ ] `new/src/themeCoreTest/.../UIMotionsTest.java`:design.md §5 全部用例(三分支性质 / 打断速度连续 / settle / ease 回归断言不动)。
- [ ] 验证:`cd new && ./gradlew testUiMotions --rerun-tasks > /tmp/t2.log 2>&1; echo REAL_EXIT=$?`。

## 阶段 4:Batcher2D alpha 栈

- [ ] `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/Batcher2D.java`:`globalAlpha` + `pushAlpha/popAlpha` + 全部 public 绘制方法入口乘子(design.md §4 清单;`globalAlpha==1` 早退保证逐位等价);文字低 alpha skip。
- [ ] 本阶段不接任何调用点(机制就绪即可,消费方是 motion-exit)。

## 阶段 5:文档与收尾

- [ ] `new/docs/theme-spec/README.md`:动画条目增补 `type/response/damping` 逐 key 说明与示例。
- [ ] `.trellis/spec/frontend/bbs-ui-theming.md`:动画节补 spring 规约与"接口不变"约定。
- [ ] 全量验证:`cd new && ./gradlew check > /tmp/check.log 2>&1; echo REAL_EXIT=$?` + 日志确认 BUILD SUCCESSFUL、0 个 FAILED。
- [ ] 四个内置主题(无 spring 键)行为不变的走查说明写入本文件执行记录。

## 回滚点

每阶段独立可提交/可回滚;阶段 2 的 spring 分支与 ease 分支物理隔离,出问题可仅回滚分支保留解析。
