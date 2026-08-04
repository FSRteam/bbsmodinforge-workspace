# 技术设计：移植 bbs-premiere-export 到 Fabric 1.20.1

## 架构概览

```
bbs-premiere-1.20.1/
├── build.gradle                    # Fabric Loom 构建配置
├── gradle.properties               # 版本配置
├── settings.gradle                 # 项目设置
├── gradle/                         # Gradle wrapper
├── src/
│   ├── main/
│   │   ├── java/mchorse/bbs_premiere_export/
│   │   │   ├── PremiereExportAddon.java      # Fabric entrypoint (替代 NeoForge @Mod)
│   │   │   ├── settings/
│   │   │   │   └── PremiereExportSettings.java
│   │   │   └── util/
│   │   │       └── TickToFrameConverter.java
│   │   └── resources/
│   │       └── fabric.mod.json               # Fabric 模组元数据
│   └── client/
│       └── java/mchorse/bbs_premiere_export/
│           ├── audio/
│           │   └── IndividualAudioExporter.java
│           ├── client/
│           │   ├── PremiereExportButton.java
│           │   ├── PremiereExportButtonInstaller.java
│           │   ├── PremiereExportHandler.java
│           │   └── UIPremiereExportSettingsOverlayPanel.java
│           └── xml/
│               └── XmemlGenerator.java
```

## API 映射

### 模组加载系统

| NeoForge 1.21.1 | Fabric 1.20.1 | 说明 |
|-----------------|---------------|------|
| `@Mod(MOD_ID)` | `BBSAddonMod` interface | Fabric 使用 entrypoint + 标记接口 |
| `IEventBus modBus` | Fabric Entrypoint | 通过 `fabric.mod.json` 的 `bbs-addon` 注册 |
| `BBSAddonRegisterEvent` | 直接实现 `BBSAddonMod` | Fabric 自动扫描 entrypoint |
| `FMLEnvironment.dist == Dist.CLIENT` | `FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT` | 客户端判断 |

### 事件系统

| NeoForge 1.21.1 | Fabric 1.20.1 | 说明 |
|-----------------|---------------|------|
| `NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ...)` | `ClientTickEvents.END_CLIENT_TICK.register(...)` | Fabric API 事件 |
| `ClientTickEvent.Post event` | `MinecraftClient client` | Fabric 回调参数不同 |
| `modBus.addListener(BBSAddonRegisterEvent.class, ...)` | 通过 `bbs-addon` entrypoint 自动注册 | BBS mod 的 addon 系统 |

### BBS API（1.20.1 vs 1.20.4 差异）

BBS mod 的 API 在 1.20.1 和 1.20.4 之间基本兼容：
- `BBSMod.setupConfig()` - 相同
- `BBSMod.getSettingsFolder()` - 相同
- `BBSMod.getProvider()` - 相同
- `BBSModClient.getDashboardIfCreated()` - 相同
- `BBSModClient.getVideoRecorder()` - 相同
- `BBSRendering.*` - 相同
- UI 组件 (`UIDashboard`, `UIFilmPanel`, etc.) - 相同

### Minecraft API（1.20.1 vs 1.20.4 差异）

主要差异：
- `Minecraft.getInstance()` → `MinecraftClient.getInstance()` (Yarn mappings)
- `net.minecraft.client.Minecraft` → `net.minecraft.client.MinecraftClient`
- Java 17 兼容的 switch 语法

## 关键修改点

### 1. PremiereExportMod.java → PremiereExportAddon.java

**NeoForge 版本：**
```java
@Mod(PremiereExportMod.MOD_ID)
public class PremiereExportMod {
    public PremiereExportMod(IEventBus modBus) {
        PremiereExportBootstrap.bind(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PremiereExportClientStarter.initialize();
        }
    }
}
```

**Fabric 版本：**
```java
public class PremiereExportAddon implements BBSAddonMod {
    // BBS mod 通过 bbs-addon entrypoint 自动发现此类
    // 使用 @Subscribe 注解注册事件处理器
}
```

### 2. PremiereExportBootstrap.java → 删除

NeoForge 的 `BBSAddonRegisterEvent` 系统在 Fabric 上不需要。
Fabric 使用 `fabric.mod.json` 的 `bbs-addon` entrypoint 直接注册。

### 3. PremiereExportClientStarter.java → 简化

NeoForge 版本使用反射桥接 main → client source set。
Fabric 版本可以直接在 client entrypoint 中初始化。

### 4. PremiereExportButtonInstaller.java

**NeoForge 版本：**
```java
NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ...);
```

**Fabric 版本：**
```java
ClientTickEvents.END_CLIENT_TICK.register(client -> { ... });
```

### 5. PremiereExportHandler.java

**NeoForge 版本：**
```java
public void onClientTick(ClientTickEvent.Post event) { ... }
```

**Fabric 版本：**
```java
public void onClientTick(MinecraftClient client) { ... }
```

### 6. TickToFrameConverter.java

Java 14+ switch 表达式需要改为 Java 17 兼容写法：
```java
// 原始 (Java 14+)
return switch (this.fps) {
    case 24 -> 24;
    case 30 -> 30;
    case 60 -> 60;
    default -> this.fps;
};

// Java 17 兼容
switch (this.fps) {
    case 24: return 24;
    case 30: return 30;
    case 60: return 60;
    default: return this.fps;
}
```

### 7. fabric.mod.json

```json
{
    "schemaVersion": 1,
    "id": "bbspremiereexport",
    "version": "${version}",
    "name": "BBS Premiere Pro Export",
    "description": "BBS addon that generates Premiere Pro compatible xmeml v5 XML",
    "authors": ["McHorse"],
    "license": "MIT",
    "environment": "client",
    "entrypoints": {
        "bbs-addon": [
            "mchorse.bbs_premiere_export.PremiereExportAddon"
        ]
    },
    "depends": {
        "fabricloader": ">=0.15.0",
        "minecraft": "~1.20.1",
        "java": ">=17",
        "fabric-api": "*",
        "bbs": "*"
    }
}
```

## 依赖管理

### BBS Mod 依赖

需要从 `old` 项目构建 BBS mod 的 jar：
```gradle
dependencies {
    def bbsJar = files("../old/build/libs/bbs-1.9-1.20.4.jar")
    modImplementation bbsJar
    compileOnly bbsJar
}
```

### 第三方依赖

```gradle
dependencies {
    minecraft "com.mojang:minecraft:1.20.1"
    mappings "net.fabricmc:yarn:1.20.1+build.10:v2"
    modImplementation "net.fabricmc:fabric-loader:0.16.14"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.92.2+1.20.1"
    
    // BBS mod 本地 jar
    modImplementation files("../old/build/libs/bbs-1.9-1.20.4.jar")
}
```

## 风险和缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| BBS API 在 1.20.1 和 1.20.4 之间有差异 | 高 | 编译时验证，必要时添加兼容层 |
| BBS mod jar 未构建 | 高 | 先构建 old 项目 |
| Java 版本不兼容 | 中 | 确保所有代码使用 Java 17 语法 |
| Fabric API 版本差异 | 低 | 使用稳定版本 0.92.2+1.20.1 |
