# 执行计划：BFX Light BBSFSR 兼容层

1. 核对上游提交、仓库现有 BBSFSR addon 模板和 `new-fs25-sync/build/libs` 新鲜度。
2. 创建 `bfx-light` NeoForge 工程与 `neoforge.mods.toml`。
3. 复制并整理核心表单、关键帧值、光源结构/注册表；移除 Fabric-only 类出编译源集。
4. 编写 BBS common/client 注册入口、表单资源和最小验证任务。
5. 构建 `compileJava` → `jar` → `build`；检查 JAR 内容、Fabric 引用和宿主类泄漏。
6. 运行 Trellis quality check，记录未迁移的渲染/Iris 功能和后续工作。
7. 固定最新 `new-fs25-sync` FSR JAR 快照并记录 SHA-256，避免并发构建时间戳造成依赖漂移。

## 验证命令

```powershell
cd bfx-light
.\gradlew.bat --no-daemon build
```

额外检查：

```powershell
jar tf build/libs/*.jar | rg "META-INF/neoforge.mods.toml|mchorse/bbs_mod|fabric.mod.json"
rg -n "net\.fabricmc|net\.minecraft\.(block|client|util\.math)" src
```

## 风险与停止条件

- 若 `new-fs25-sync` 源码继续变化，先在该目录完成 `jar`，再更新 `bfx-light/TEMP/BBS_FSR_beta_v0.0.11-fs25-latest.jar` 快照及其哈希后构建 addon。
- 若核心表单 API 与上游不同，只保留可验证的最小表单模型，并在 `PORT_STATUS.md` 记录，不修改宿主兼容行为。
- 不把当前构建失败伪装成“已兼容”；必须报告真实失败阶段。
