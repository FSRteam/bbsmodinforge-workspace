# bbsmodinforge-workspace

`bbs-FSR` 团队共享的 Trellis 工作区。这个仓库保存任务、规范、开发日志和 AI
平台接入配置；实际代码继续保存在各自独立的 Git 仓库中。

## 包含内容

- `.trellis/`：工作流、任务、归档、规范、脚本和按开发者分隔的日志。
- `.agents/skills/`：Codex/Gemini 等平台共享的 Trellis skills。
- `.codex/`、`.claude/`、`.cursor/`：团队共享的平台接入文件。
- `workspace-manifest.json`：工作区依赖的代码仓库清单。
- `scripts/bootstrap-workspace.ps1`：克隆缺失仓库并初始化开发者身份。

本地运行态不会上传，包括 `.trellis/.developer`、`.trellis/.runtime/`、备份、
Python 缓存、Claude 本地设置和 Codex 本地模型目录。

## 首次安装

需要 Git、Python 3 和对私有工作区仓库及相关代码仓库的访问权限。

```powershell
git clone https://github.com/FSRteam/bbsmodinforge-workspace.git
Set-Location bbsmodinforge-workspace
./scripts/bootstrap-workspace.ps1 -Developer <你的开发者名称>
```

默认只克隆必需的 `new/` 主代码仓库。需要上游参考仓库时：

```powershell
./scripts/bootstrap-workspace.ps1 -Developer <你的开发者名称> -IncludeOptional
```

对于已经存在且工作区干净的默认分支，可以安全快进：

```powershell
./scripts/bootstrap-workspace.ps1 -UpdateExisting -IncludeOptional
```

脚本不会覆盖已有开发者身份，不会重置分支，也不会修改有未提交内容的代码仓库。

## 日常同步

同步 Trellis 信息：

```powershell
git switch main
git pull --ff-only
```

修改任务、规范或日志时，必须通过功能分支和 Pull Request：

```powershell
git switch -c work/<简短名称>
git add .trellis .agents .codex .claude .cursor
git commit -m "docs(trellis): update shared work context"
git push -u origin work/<简短名称>
```

随后在 GitHub 创建目标为 `main` 的 PR。不要直接 push 到 `main`。

### 分支保护说明

工作区仓库是私有仓库。GitHub Free 组织计划不允许为私有仓库启用分支保护，
因此当前只能通过 PR 约定和 CI 检查协作；GitHub API 返回 403 时不要把仓库
改为公开。升级到支持私有仓库分支保护的计划后，应启用至少一名审查者、
`Validate workspace`、最新主分支、已解决对话、管理员强制执行，并禁止强制
push 和删除 `main`。

## 并行协作约定

- 每位开发者使用自己的 `.trellis/workspace/<developer>/` 日志目录。
- `.trellis/.developer` 只保存在本机，不得提交。
- 共享任务和规范发生冲突时，在 PR 中解决，不覆盖他人的未完成工作。
- `new/` 等嵌套代码仓库拥有独立分支、提交和 PR；工作区仓库不追踪其文件。

## 验证

```powershell
python ./scripts/validate_workspace.py
python -m compileall -q .trellis/scripts .codex/hooks .claude/hooks .cursor/hooks scripts
```
