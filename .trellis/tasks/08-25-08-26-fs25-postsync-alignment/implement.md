# Implement: FS 2.5 post-sync 对齐修复（5项）

## Ordered Checklist

1. 建立修改前基线。
   - 记录 `new/` 当前分支、状态和相关文件哈希。
   - 确认 `new-fs25-sync/` 与 FS 上游对应提交的局部差异。
2. 修复半透明队列状态。
   - 小块移植每命令混合状态恢复。
   - 小块移植 Iris 顶点布局捕获/应用/恢复及所有构造调用点。
   - 扩充 `RenderRuntimeMigrationSourceTest`，覆盖混合状态和 Iris 布局契约。
3. 恢复原版粒子预览。
   - 添加本地 `VanillaParticleScene`、必要的 Mixin invoker/accessor 和 mixin 注册。
   - 给 `FormRenderingContext` 增加预览时钟，并从普通/可拾取 Form 预览传入。
   - 改造 `VanillaParticleFormRenderer`，区分本地预览与世界生成。
   - 扩充 `ParticleRuntimeConsistencyTest`，校验场景隔离、容量、时钟和状态恢复接线。
4. 恢复设置分类和本地化。
   - 语义移植设置分类、组合行和旧配置迁移。
   - 保留 `new/` 的 FSR 专用设置与 UI 工厂。
   - 迁移所有支持语言中的受影响键，补齐五类标题/说明和中英文新控件文案。
   - 添加或扩充源级测试，校验分类、迁移键和语言资源非空。
5. 补回两个小交互。
   - 新增身体附件后定位并选中追加项。
   - 相机模式按钮右键菜单只显示三项轨道功能，六种模式保留在左键模式选择入口。
   - 添加源级断言，防止条件显示或列表定位再次丢失。
6. 定向验证。
   - `./gradlew.bat --no-daemon compileJava compileClientJava`
   - `./gradlew.bat --no-daemon testFilmEditorMigration`
   - `./gradlew.bat --no-daemon testParticleRuntimeConsistency`
   - 运行新增设置/资源与交互测试对应的 Gradle 任务。
7. 扩大验证。
   - `./gradlew.bat --no-daemon testUiInputMailbox testPlatformResourceConsistency`
   - `./gradlew.bat --no-daemon check`
   - `./gradlew.bat --no-daemon build`
   - `git diff --check`
8. 人工烟测并记录结果。
   - Color Pose alpha: 模型、粒子、标签/物品层，无 Iris与有 Iris。
   - `bbs:vanilla_particles`: 模型编辑器预览、切换 Form、退出编辑器后无世界残留。
   - 设置五分类及中英文显示，旧配置迁移。
   - 连续新增身体附件的末尾定位。
   - 任意相机模式下右键菜单的九项功能。

## Review Gates

- 半透明修复不得删除或绕过 `FormTranslucentQueue`。
- 粒子预览不得写入世界粒子队列，不得把 FS 粒子主系统整体迁入 FSR。
- `BBSSettings`、`BBSRendering`、`UIFilmPreview` 和语言资源必须逐块审查，禁止从快照整文件覆盖。
- 旧设置迁移必须满足“新值优先、旧值只复制、不删除旧值”。
- 编译和定向测试通过后才能进入完整 `check/build`。

## Rollback Points

- 渲染队列与 Iris 布局为一组。
- 原版粒子本地场景、Mixin 和上下文时钟为一组。
- 设置注册、迁移、UI 行和语言资源为一组。
- 身体附件与相机菜单可分别独立回退。
