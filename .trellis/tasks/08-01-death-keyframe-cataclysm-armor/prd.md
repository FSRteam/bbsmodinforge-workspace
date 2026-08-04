# 死亡关键帧与灾变盔甲兼容（父任务）

## Goal

本任务树解决两个独立的 BBS `new/` 能力增强需求：

1. **死亡关键帧轨道**：玩家/生物被击杀时，模型需要呈现"变红 + 向一个方向倒下"的死亡动画。这需要把"死亡状态"作为一个可录制的关键帧通道（channel）加入回放轨道。
2. **灾变（Cataclysm）模组盔甲兼容**：让 BBS 的 `item_head/item_chest/item_legs/item_feet` 盔甲关键帧轨道能够正常渲染 L_Ender's Cataclysm 模组的自定义盔甲模型。

> 范围界定（用户决策，2026-08-02 更新）：Iron's Spellbooks（铁魔法）/ GeckoLib 盔甲兼容**纳入本轮**（用户确认"铁魔法盔甲适配可以跟这个一起开始"）。本任务树范围：死亡关键帧 + 灾变盔甲 + 铁魔法（GeckoLib）盔甲。

## Child Tasks

- `08-01-death-keyframe-track` — 死亡关键帧轨道（含死亡时录制不中断）
- `08-01-cataclysm-armor-compat` — 灾变模组盔甲兼容 + 铁魔法（GeckoLib）盔甲兼容

## 跨子任务验收标准

- 三个交付物均独立可验证、可分别归档。
- GeckoLib 的接入方式需评估为**可选/反射依赖**（无 GeckoLib 环境仍能编译运行），具体以 research 结论为准。
- `new/` 工程在无上述 mod 的环境下仍能正常编译、启动。
- 改动遵循现有代码风格，不破坏既有回放/盔甲渲染行为（backward compatible）。

## Notes

- 实施时先启动 `08-01-death-keyframe-track`（交付物更独立、风险更低）。
- `08-01-cataclysm-armor-compat` 依赖死亡/回放基础设施，但两者主要文件不同，实现互不阻塞。
