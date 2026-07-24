# motion-tracks 技术设计

依赖阅读:本任务 `prd.md`、父任务 `../07-24-theme-v2/design.md` 的“层 4 编排”与 §3.2。

## 1. 目标

把 motion 条目从“单一 ease/spring 参数”升级为“固定属性集上的可编排轨道”，但故意止步于轻量结构:

- 不做 keyframe DSL
- 不做任意属性
- 只做 `alpha/scale/x/y`

## 2. 数据结构

### 2.1 motion entry

在 `UIThemeMotion` 中新增:

- `preset`
- `tracks`

其中 `tracks` 是解析期构建的不可变小对象，而不是运行期 `Map<String, Object>` 查表。

### 2.2 preset

首批预设:

- `scale`
- `slide_right`
- `slide_up`
- `fade`

规则:

- preset 先展开为默认 tracks
- 显式 tracks 再覆盖 preset 对应属性

## 3. 应用顺序

固定为:

1. translate(x, y)
2. scale(围绕锚点/中心)
3. alpha

进入与退出共用同一组 tracks:

- 进入: `from -> rest`
- 退出: `rest -> from`

## 4. 接入点

首批必须支持:

- overlay
- context_menu

若成本可控，再扩:

- panel_switch
- notification

## 5. 与现有系统关系

- 没有 `tracks/preset` 的条目，完全保持当前 motion-engine 行为
- motion-exit 只负责“反向播放机制”，本任务负责“播放什么”

## 6. 测试策略

- `testThemeCore`
  - preset 展开
  - tracks 覆盖优先级
  - 坏值逐 key 回退
- `testUiMotions`
  - overlay/context menu track 采样的进入/退出对称性

