## Bug Analysis: Pose 宿主改动破坏 bbs_snow，缺失插件剪辑打开影片崩溃

### 1. Root Cause Category

- **Category**: C - Change Propagation Failure；D - Test Coverage Gap；E - Implicit Assumption
- **Specific Cause**: BBS 的宽 Pose 布局从单元素添加改成数组添加，但外部插件 Mixin 仍重定向旧重载；核心数据层虽已用 `MissingClip` 保存缺失插件数据，客户端时间线仍假定工厂元数据必定存在。

### 2. Why Fixes Failed

1. Pose 核心修复只验证了 BBS 自身编译与折叠测试，没有把“所有目标方法的外部 Mixin”纳入变更传播范围。
2. 插件生命周期测试证明了缺失剪辑会降级和恢复，却没有实例化时间线投影；因此数据层通过、界面层仍在首帧空指针崩溃。

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | `UIClips` 集中提供缺失剪辑静态元数据，渲染器和标尺不再直接解引用插件注册数据 | DONE |
| P0 | Test Coverage | 客户端源码回归约束时间线所有颜色消费者走安全入口；插件热重载继续验证降级/恢复 | DONE |
| P0 | Documentation | 外部 Addon 规范新增“宿主调用形状也是 Mixin 契约”和宽/窄分支验证要求 | DONE |
| P1 | Runtime | 使用隔离的已完成入口构建兼容 JAR，严格客户端实际创建宽/窄 Pose 并自动退出 | DONE |

### 4. Systematic Expansion

- **Similar Issues**: 其他对 BBS UI 方法做 `@Redirect` 的外部插件；时间线、属性面板和菜单中所有直接 `factory.getData(object)` 的调用。
- **Design Improvement**: 数据占位与 UI 占位必须成对设计；插件注册元数据不能作为持久对象永远存在的前提。
- **Process Improvement**: 修改被外部 Mixin 命中的宿主方法时，同一轮必须用新核心包编译插件并执行目标界面，不能以核心编译通过代替集成验证。

### 5. Knowledge Capture

- [x] 更新 `.trellis/spec/new/backend/external-addon-integration-contract.md`
- [x] 更新 `.trellis/spec/guides/cross-layer-thinking-guide.md`
- [x] 在当前任务 `implement.md` 记录修复、验证和独立阻塞
- [x] 仓库没有 `src/templates/markdown/spec/` 或其他规范模板镜像，无可同步目标
- [ ] 未提交：工作区包含用户和其他任务的多组未提交改动，且用户未授权提交
