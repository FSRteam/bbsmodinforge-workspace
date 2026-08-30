# 执行计划：BFX Light UI 与效果修复

1. [x] 读取当前任务规范，建立上游客户端类到 FSR API 的迁移清单；确认最新 `new-fs25-sync` JAR 快照可用。
2. [x] 迁移图标资源、语言键、通用灯光面板和四种专用面板，先让 UI 编译并绑定真实 `Value*`。
3. [x] 迁移 Light 预览 renderer/形状 renderer；按当前 Mojmap/NeoForge API 完成转换。
4. [x] 迁移 `FormLightCollector` 和当前宿主可验证的 fallback 光照消费路径；高级 Iris/Mixin 路径单独隔离并记录。
5. [x] 更新客户端注册、source pack 和资源清单；加入 UI/资源/数据流回归检查。
6. [x] 运行 `compileJava`、`check`、`build`，审计 JAR 内容和 Fabric/Yarn 残留。
7. [x] 更新 `PORT_STATUS.md` 和 Trellis 规格，记录真实效果范围与未迁移边界。

## 验证命令

```powershell
cd bfx-light
.\gradlew.bat --no-daemon clean build
.\gradlew.bat --no-daemon check
```

静态审计：

```powershell
rg -n "net\\.fabricmc|net\\.minecraft\\.(block|client|util\\.math)" src
jar tf build/libs/BFX-Light.jar | rg "META-INF/neoforge.mods.toml|mchorse/bbs_mod|fabric.mod.json|assets/vfxlights"
```

## 风险与停止条件

- 上游 renderer 依赖大量 1.20 Fabric/Iris 类；每次转换后立即编译，禁止批量替换后假定可用。
- 如当前宿主没有公开的 fallback 合成接口，不把 `LightRegistry` 提交误报成最终画面效果；必须在 `PORT_STATUS.md` 标明“数据提交已验证、画面消费未验证”。
- 不在 dedicated server 链接客户端类；common 入口继续使用反射启动 client bootstrap。
