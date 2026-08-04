# Bug Analysis: 观察者状态污染模型物理并由后置约束抹除 IK

## 1. Root Cause Category

- **Category B — Cross-Layer Contract**：渲染矩阵和模拟矩阵的语义边界未保留，摄像机相对/view 空间进入物理历史。
- **Category C — Change Propagation Failure**：FS → FSR 迁移时遗漏了 `FormRenderingContext.world` 以及 Form/body-part/model 的整条同步传播链。
- **Category D — Test Coverage Gap**：构建与单功能检查无法发现“旋转相机”“退出编辑器”“IK+constraints 组合”这类集成场景。
- **Category E — Implicit Assumption**：静态 `IEntity + modelId` 被误认为足以代表一个模拟实例；后置 Euler 限制被误认为能安全作用于组合四元数结果。

## 2. Why Earlier Fixes Were Insufficient

1. 历史修复聚焦 form/particle 的渲染 model-view，建立了“世界渲染只应用一次相机矩阵”的规范，但没有把同一原则扩展到模型模拟输入。
2. 最新 FS 功能迁移通过了编译与构建，但迁移审核只确认类和功能存在，没有逐字段核对 `FormRenderingContext.world` 这类跨文件传播契约。
3. IK 与约束各自单独可工作，未验证组合顺序；最终约束清空 `orient` 后，表象像 IK 未执行。

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | 恢复独立 semantic `world` 矩阵，与 render `stack` 分离 | DONE |
| P0 | Architecture | 物理历史改为 renderer + explicit simulation owner 所有 | DONE |
| P0 | Solver contract | 固定 FK constraints → IK → physics 顺序，程序求解器内部限位 | DONE |
| P1 | Documentation | 在 backend quality spec 增加 7 节可执行契约 | DONE |
| P1 | Review checklist | 跨层指南增加 render/simulation/owner/solver-order 四项检查 | DONE |
| P1 | Integration validation | 增加编辑器、世界、多实例和组合功能 smoke 矩阵 | DONE（文档）；待游戏内执行 |

## 4. Systematic Expansion

- **Similar issues**：粒子 emitter、trail、布料/未来模拟、世界目标 gizmo、模型方块和第一人称物品都可能误用主相机或共享模型资源。
- **Design improvement**：以后新增 stateful renderer 时，构造签名必须能看出 semantic transform 与 simulation owner；不要从全局渲染阶段推断。
- **Process improvement**：迁移有跨文件传播字段时，不只对比入口类；应搜索该字段在上游的所有读写点并建立覆盖清单。
- **Knowledge gap**：编译成功只证明 API 形状正确，不证明坐标空间、所有权与求解阶段语义正确。

## 5. Knowledge Capture

- [x] 更新 `.trellis/spec/new/backend/quality-guidelines.md`。
- [x] 更新 `.trellis/spec/guides/cross-layer-thinking-guide.md`。
- [x] 任务 PRD/design/implement 记录根因与验收矩阵。
- [ ] 用户执行游戏内 smoke 后记录结果。
- [ ] 顶层目录不是 Git 仓库，spec 无法在此处提交；保留工作区记录并由用户决定后续版本化方式。
