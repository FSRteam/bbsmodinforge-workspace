# 移植 bbs-premiere-export 插件到 Fabric 1.20.1

## 背景

`bbs-premiere-export` 是一个 BBS 模组的插件，当前运行在 NeoForge 1.21.1 上。该插件的功能是在 BBS 视频导出时：
1. 自动生成 Adobe Premiere Pro 兼容的 xmeml v5 XML 文件
2. 将每个 AudioClip 导出为独立的 WAV 文件
3. 在 BBS 电影面板 UI 中添加导出按钮和设置面板

需要将此插件移植到 Fabric 1.20.1，使其能够在旧版本的 BBS 模组上运行。

## 需求

### 功能需求
- [ ] 保持与 NeoForge 版本完全相同的功能
- [ ] 生成相同的 xmeml v5 XML 输出格式
- [ ] 支持独立音频导出
- [ ] 支持 NTSC 帧率标记
- [ ] 在 BBS 电影面板中添加导出按钮
- [ ] 右键按钮可打开设置面板

### 技术需求
- [ ] 使用 Fabric Loom 构建系统
- [ ] 使用 Fabric 1.20.1 的 API
- [ ] 使用 `bbs-addon` entrypoint 系统注册插件
- [ ] 使用 Fabric 的 `ClientTickEvents` 替代 NeoForge 的 `ClientTickEvent`
- [ ] Java 17 兼容（1.20.1 要求）
- [ ] 依赖 BBS mod 的本地 jar（从 old 项目构建）

### 版本目标
- Minecraft: 1.20.1
- Fabric Loader: 0.16.14+
- Fabric API: 0.92.2+1.20.1
- Yarn Mappings: 1.20.1+build.10
- Java: 17
- BBS Mod: 1.9（从 old 项目获取）

## 约束

1. BBS API 在 1.20.1 和 1.20.4 之间基本兼容，但需要验证
2. 插件依赖 BBS mod 的本地 jar，需要先构建 old 项目
3. NeoForge 特有的 API（事件总线、DistMarker 等）需要替换为 Fabric 等效物
4. `TickToFrameConverter` 中使用了 Java 14+ 的 switch 表达式，需要改为 Java 17 兼容写法

## 验收标准

1. `./gradlew build` 编译成功
2. 所有 Java 源文件无编译错误
3. `fabric.mod.json` 正确配置 entrypoint
4. 插件能通过 `bbs-addon` entrypoint 被 BBS mod 加载
5. 功能与 NeoForge 版本一致
