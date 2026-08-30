# FSR FFmpeg 设置与自动下载

## Goal

让用户在 FSR 设置界面中直接选择、拖入或自动下载可用的 FFmpeg，不再需要手工查找安装教程或修改系统 PATH；下载中断后可以继续，失败不会污染当前设置或破坏已有版本。

## Confirmed Facts

- 当前持久化设置为 `BBSSettings.videoEncoderPath`，默认值是 `ffmpeg`。
- 当前设置行只有路径文本框；Windows 右键菜单可以扫描磁盘寻找 `ffmpeg.exe`，扫描是同步的。
- FSR 已有 TinyFileDialogs 文件选择器和全局文件拖入入口。
- `FFMpegUtils` 已支持路径、目录、`bin/` 目录解析以及带超时的 `-version` 检测。
- `https://gh-proxy.cn/<完整 GitHub Release URL>` 已实测支持固定 Release、完整文件下载和 HTTP Range 断点续传。
- 代理和 GitHub 直连返回的固定资产大小一致，实测文件 SHA-256 与 GitHub 官方摘要一致。

## Requirements

- FFmpeg 设置区域显示路径输入框、选择本地文件按钮、自动下载按钮和一行状态。
- 选择本地文件使用原生文件选择器；Windows 默认选择 `ffmpeg.exe`，Linux/macOS 选择 `ffmpeg`。
- 在设置界面打开期间拖入 FFmpeg 文件时，优先消费该文件并自动填写路径；无效文件继续交给原有资源导入流程。
- 选择或拖入的候选必须异步执行 `ffmpeg -version`，验证成功后才写入设置。
- 自动下载根据操作系统和 JVM 架构选择固定资产，支持 Windows/Linux/macOS 的 x64 与 ARM64。
- 国内下载首选 `gh-proxy.cn`，传输失败时自动尝试 GitHub 直连；不向代理发送令牌、Cookie 或私有仓库地址。
- 下载使用单连接和 HTTP Range；临时文件使用 `.part`，游戏退出或崩溃后下次从已有字节继续。
- 资产清单内置固定版本、文件名、长度和 SHA-256；不能依赖动态 `latest`，也不能信任经代理下载的校验文件。
- 下载完成后校验 SHA-256，再解压或发布到 `config/bbs/tools/ffmpeg/<version>/<platform>/`。
- 候选版本必须通过 `-version`、`libx264` 和 AAC 编码器检查，最后写入 `.ready` 并原子发布。
- 只有完整自检成功后才更新 `videoEncoderPath`；失败时旧路径和旧的已就绪版本继续可用。
- 手动选择的文件永不删除；自动管理目录只清理自己创建的临时安装目录和已判定损坏的 `.part`。
- UI 显示检测、下载、校验、安装、成功、失败、速度、进度和预计剩余时间，耗时工作不得阻塞 Minecraft 客户端线程。
- 下载按钮在任务执行期间不可重复启动；关闭设置界面不取消下载，关闭游戏由守护线程自然终止并保留 `.part`。

## Acceptance Criteria

- [ ] Windows/Linux/macOS x64 与 ARM64 能选择正确的固定资产；未知架构显示明确的不支持状态。
- [ ] 本地文件按钮可以选择并验证 FFmpeg，验证成功后路径输入框和设置值同步更新。
- [ ] 在设置界面拖入有效 FFmpeg 会自动填写；拖入普通素材仍走现有导入器。
- [ ] 自动下载首先请求 `gh-proxy.cn`，失败后使用原始 GitHub URL。
- [ ] 中断后保留 `.part`，再次启动会发送正确 Range 并继续写入，不会重复下载已有字节。
- [ ] 服务端忽略 Range 并返回完整响应时会安全地从头覆盖，而不是把完整文件追加到旧部分后面。
- [ ] 文件长度或 SHA-256 不匹配时不会解压、不会写 `.ready`、不会更改设置。
- [ ] ZIP 解压防止路径穿越；原始可执行文件与 ZIP 资产都能发布到同一管理目录结构。
- [ ] 自检缺少 `libx264` 或 AAC 时安装失败且不会替换可用版本。
- [ ] 下载完成后设置值指向管理目录内的 FFmpeg，并能通过现有 `FFMpegUtils.checkFFMPEG()`。
- [ ] 相关单元测试、`compileJava`、`compileClientJava` 和项目质量检查通过。

## Out Of Scope

- 不修改系统 PATH，不调用 winget/choco/brew/apt，不申请管理员权限。
- 不加入多分段并发下载；实测四段并行没有提高吞吐。
- 不自动更新到每日 `latest`；升级资产清单需要后续受控发布。
- 不接入阿里或清华镜像；目前没有可验证的固定 FFmpeg Release 资产映射。
