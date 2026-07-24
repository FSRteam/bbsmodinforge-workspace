# 弹簧动画内核与全局透明度栈

## Goal

把 v1 固定时长 + easing 的 `UITween` 内核升级为可选弹簧物理,实现速度连续的打断;为 Batcher2D 增加全局 alpha 乘栈。这是 v2 动画线全部后续任务(退场、覆盖面、track)的地基。

## Requirements

- R1 `UITween` 对外接口 `snap / to / update / isSettled` 保持不变;v1 六接入点(hover/overlay/context_menu/notification/panel_switch/scrollbar)**零调用方改动**获得弹簧行为。
- R2 动画进行中被 `to()` 重定向时,值与速度均连续(无顿挫);`snap()` 语义不变(值跳变、速度归零、立即 settled)。
- R3 主题动画条目支持 `type: "spring"`(参数 `response` 秒 + `damping` 阻尼比,damping<1 允许过冲);v1 `{duration, easing}` 条目(`type: "ease"` 缺省)行为完全不变。
- R4 全局 `motion.speed` 对弹簧的作用为缩放 response;`motion.enabled=false` 及单条 `enabled=false` 仍走 snap 短路。
- R5 Batcher2D 增加全局 alpha 乘栈(push/pop),作用于所有绘制的最终颜色;乘子为 1 时输出逐位等价;文字低 alpha 需 clamp 规避字体 discard 跳变。

## Out of Scope

- 新接入点、退场动画、track 参数化(后续子任务)。
- theme.json 的 `tracks` 键(motion-tracks 任务)。

## Constraints

- 弹簧用欠阻尼闭式解(见父任务 design.md 层 1),不做逐帧积分;settled 后短路直达旧渲染路径;热路径零分配。
- 裸 JVM 测试红线:不触发 `BBSMod.<clinit>`。
- v1 `testUiMotions` 全部用例保持通过(断言不得删除)。

## Acceptance Criteria

- [ ] `testUiMotions` 原有用例通过;新增弹簧用例:闭式解采样点对预期值(欠阻尼/临界/过阻尼三分支)、打断后差分速度不跳变、settle 收敛。
- [ ] alpha 栈:乘子 1 时逐位等价;嵌套 push/pop 正确复原。
- [ ] 四个 v1 内置主题渲染行为不变(`type` 缺省即 ease 路径)。
- [ ] 全量 check 真实 exit code 0。
