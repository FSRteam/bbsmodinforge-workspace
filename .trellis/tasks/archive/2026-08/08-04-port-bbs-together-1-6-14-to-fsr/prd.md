# 移植 BBS Together 1.6.14 到 BBSFSR

## Goal

将 `temp/bbs-together-1.6.14-bbs2.3-wenxu-1.20.1.jar` 反编译并移植为可独立构建、可随 BBSFSR 加载的 NeoForge 1.21.1 插件工程，保留原插件面向用户的核心功能。

## Confirmed Facts

- 唯一上游输入是 `temp/bbs-together-1.6.14-bbs2.3-wenxu-1.20.1.jar`；旧的 `bbs-together-addon-1.1.1-1.20.1.jar` 不再作为移植输入。
- 上游文件名标明版本为 BBS Together 1.6.14、BBS 2.3、Minecraft 1.20.1。
- 上游 JAR 的 SHA-256 为 `7802CBB9DDCDB52C80261560E625459DC3D165786113CB7F8A123FA27726DA61`。
- JAR 元数据声明 `mod_id=bbs_together`、版本 `1.6.14-bbs2.3-wenxu`、MIT 许可，作者为 McHorse 和 ChillinWithDylan。
- 映射后 CFR 输出 52 个 Java 源文件（含拆出的内部类）、2 个 Mixin 配置和 19 份翻译；CFR 仅有一个合成 lambda 未能完整结构化，需要结合字节码和调用上下文手工恢复。
- 上游功能包括玩家颜色、共享光标/选区、影片与回放 CRUD 同步、表单锁、同步录制、资产同步、协作命令和默认关键帧样式。
- 上游的 `cdnUrl`/`cdnToken` 没有被运行时代码使用，不存在需要保留的外部多人服务协议。
- 目标运行环境是本仓库 `new/` 对应的原生 NeoForge 1.21.1 BBSFSR。
- 当前目标 BBSFSR 版本为 `0.0.11`，NeoForge 版本为 `21.1.222`，本地构建 JAR 已存在。
- BBSFSR 提供 API 2.0 addon 网络中转通道以及原生影片协作/远端 presence 接口，可替代上游 Fabric 网络注册和一部分 UI Mixin。
- 交付物需要像仓库现有 addon 一样放在仓库根目录下的独立目录中，拥有自己的源码、资源、Gradle 配置和产物。
- BBSFSR 不是远程 Maven 依赖；独立 addon 应通过本地 JAR 以 `compileOnly` 方式编译，不能把 BBSFSR 类打进插件 JAR。

## Requirements

- 反编译新版 JAR 中的插件类并提取其原始资源、元数据和 Mixin 配置。
- 盘点所有 Fabric、Connector、Minecraft 1.20.1、BBS 2.3 API 依赖，并映射到 NeoForge 1.21.1 与 BBSFSR 的等价接口。
- 在独立目录中建立可维护的源码工程，保留合理的包名、资源命名空间、插件身份和功能语义。
- 独立目录命名为 `bbs-together-addon/`，mod id 和资源命名空间继续使用 `bbs_together`，移植版本使用 `1.6.14-fsr`。
- 使用 BBSFSR 外部 addon 规定的构造期注册、客户端隔离、资源包和本地化注册方式。
- 通过 BBSFSR API 2.0 addon broker 承载客户端/服务端消息；不直接注册 NeoForge 自定义 payload，也不占用 BBS 核心网络通道。
- 影片局部修改、快照和远端光标/选择状态优先接入 BBSFSR 原生影片协作 API，避免重复实现已有的 UI 内部同步边界。
- 资产上传、下载和服务端推送必须支持超过单包上限的分块传输，并限制路径、文件大小、总传输量、并发数和超时。
- 所有远程影片写入、录制控制和资产上传必须经过服务端权限与会话校验；网络输入不得直接用于越界索引或未约束文件路径。
- 对无法直接迁移的功能提供明确且不破坏启动的降级行为；不以静默删除核心功能作为完成标准。
- 构建配置与仓库已验证的独立 addon 保持一致，并使用 Java 21、NeoForge 21.1.x 和当前 BBSFSR 本地 JAR。
- 保留原作者和来源信息；反编译产物的许可边界在 README 中明确说明。

## Acceptance Criteria

- [ ] 新插件位于仓库根目录下的独立目录，不修改或混入其他 addon 的源码目录。
- [ ] 反编译得到的 Java 源码可读，并已清理阻止维护或编译的反编译伪影。
- [ ] 原 JAR 中需要的资源、Mixin 配置和本地化文件均被纳入新工程，且资源路径与清单一致。
- [ ] Fabric/Connector 元数据和运行时调用已移除或替换为原生 NeoForge/BBSFSR 接口。
- [ ] `gradlew build` 成功，并产出独立插件 JAR。
- [ ] 产物 JAR 不包含 `mchorse/bbs_mod/**`，不捆绑 BBSFSR 本体。
- [ ] 产物元数据声明对 `bbs` 的必需依赖，版本范围接受本仓库当前 BBSFSR 版本。
- [ ] 玩家颜色、远端光标/选区、协作影片修改、回放 CRUD/锁定、同步录制、资产同步和 `/bbstogether` 命令均有对应实现或经过说明的安全降级。
- [ ] 对网络解码、路径约束、分块重组、会话清理和远端修改回环抑制提供自动化测试。
- [ ] Mixin 配置使用 Java 21，所有保留注入点均已逐项对照当前 BBSFSR 源码；启动日志无 Mixin apply failure。
- [ ] 至少完成一次开发客户端启动验证；若环境依赖阻止启动，需记录已完成的较低层验证及具体阻塞。
- [ ] README 说明目标环境、依赖、构建方式、移植差异和已知限制。

## Out of Scope

- 修改旧的 1.1.1 JAR 或同时维护两个上游版本。
- 将插件合并进 BBSFSR 核心源码。
- 为未在上游 JAR 中出现的未来功能做扩展设计。
- 与 Minecraft 1.20.1 Fabric 客户端或原版 Together 服务器跨版本联机；两端必须使用本次 NeoForge 1.21.1 移植版。
- 恢复未被上游实际使用的 CDN URL/token 功能。

## Open Questions

- 无阻塞问题；等待用户审核规划并批准进入实现阶段。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
