# 执行计划：移植 bbs-premiere-export 到 Fabric 1.20.1

## 前置条件

- [x] NeoForge 版本源代码已分析
- [x] old 项目（Fabric 1.20.4）结构已了解
- [x] BBS addon 系统工作原理已明确
- [ ] old 项目 BBS mod jar 已构建

## 执行步骤

### Phase 1: 环境准备

#### 1.1 构建 BBS mod 依赖 jar
```bash
cd old
./gradlew build
```
**输出**: `old/build/libs/bbs-1.9-1.20.4.jar`

#### 1.2 创建项目目录结构
```
bbs-premiere-1.20.1/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── src/
    ├── main/
    │   ├── java/mchorse/bbs_premiere_export/
    │   └── resources/
    └── client/
        └── java/mchorse/bbs_premiere_export/
```

### Phase 2: 构建配置

#### 2.1 gradle.properties
```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

# Fabric Properties
minecraft_version=1.20.1
yarn_mappings=1.20.1+build.10
loader_version=0.16.14

# Mod Properties
mod_version=0.1.0
mod_id=bbspremiereexport
mod_name=BBS Premiere Pro Export
maven_group=mchorse
archives_base_name=bbs-premiere-export

# Dependencies
fabric_version=0.92.2+1.20.1
```

#### 2.2 build.gradle
- 使用 `fabric-loom` 插件
- 配置 `splitEnvironmentSourceSets()`
- 依赖 BBS mod 本地 jar
- Java 17 工具链

#### 2.3 settings.gradle
```gradle
pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "bbs-premiere-export"
```

### Phase 3: 资源文件

#### 3.1 fabric.mod.json
- 配置 `bbs-addon` entrypoint
- 设置依赖关系
- 环境标记为 `client`

### Phase 4: 源代码移植

#### 4.1 无需修改的文件（直接复制）
- `XmemlGenerator.java` - 纯 Java，无平台依赖
- `TickToFrameConverter.java` - 需要小幅修改（switch 语法）
- `IndividualAudioExporter.java` - 仅依赖 BBS API
- `UIPremiereExportSettingsOverlayPanel.java` - 仅依赖 BBS UI API

#### 4.2 需要修改的文件

**PremiereExportAddon.java** (原 PremiereExportMod.java)
- 删除 `@Mod` 注解
- 实现 `BBSAddonMod` 接口
- 删除 NeoForge 事件总线代码
- 使用 `@Subscribe` 注解注册事件

**PremiereExportSettings.java**
- 保持不变（仅依赖 BBS API）

**PremiereExportButtonInstaller.java**
- 替换 `NeoForge.EVENT_BUS.addListener` 为 `ClientTickEvents.END_CLIENT_TICK.register`
- 替换 `ClientTickEvent.Post` 为 `MinecraftClient`

**PremiereExportHandler.java**
- 替换 `ClientTickEvent.Post` 参数为 `MinecraftClient`
- 替换 `net.minecraft.client.Minecraft` 为 `net.minecraft.client.MinecraftClient`

**PremiereExportButton.java**
- 保持不变（仅依赖 BBS API）

#### 4.3 删除的文件
- `PremiereExportBootstrap.java` - NeoForge 特有，Fabric 不需要
- `PremiereExportClientStarter.java` - 反射桥接，Fabric 不需要

### Phase 5: 验证

#### 5.1 编译验证
```bash
cd bbs-premiere-1.20.1
./gradlew build
```

#### 5.2 功能验证
- 检查 fabric.mod.json 配置正确
- 检查 entrypoint 注册正确
- 检查所有 BBS API 调用兼容

## 回滚点

- Phase 1 完成后：可回滚到清理目录
- Phase 2 完成后：可回滚到删除构建配置
- Phase 4 完成后：可回滚到删除源代码

## 完成标准

- [ ] `./gradlew build` 编译成功
- [ ] 所有 Java 文件无编译错误
- [ ] fabric.mod.json 正确配置
- [ ] 插件功能与 NeoForge 版本一致
