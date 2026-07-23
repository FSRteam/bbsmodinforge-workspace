# 建立团队共享 Trellis 工作区仓库

## Goal

共享 Trellis 任务、规范、日志与 AI 平台接入，通过私有 GitHub 仓库和 PR 协作。

## Requirements

- Create a private `FSRteam/bbsmodinforge-workspace` repository for shared
  Trellis state without absorbing the nested code repositories.
- Share Trellis workflow, tasks, archives, specs, scripts, developer journals,
  shared skills, and supported platform integration files.
- Exclude developer identity, session runtime pointers, backups, caches, local
  platform settings, large machine-specific catalogs, code repositories, and
  build outputs.
- Provide a manifest and a safe PowerShell bootstrap command that clones the
  required code repository and optional upstream references.
- Preserve existing repositories and local changes; updates may only fast-forward
  a clean checkout on its declared default branch.
- Require team changes to use feature branches and Pull Requests.
- Validate the manifest, tracked-file boundary, and shared Python entry points in
  GitHub Actions.

## Acceptance Criteria

- [ ] A teammate can clone the private workspace repository and run the bootstrap
  command with their own Trellis developer identity.
- [ ] The required `new/` repository is cloned to the expected path; optional
  reference repositories are opt-in.
- [ ] Existing dirty or non-default-branch repositories are never reset or pulled.
- [ ] `.trellis/.developer`, `.trellis/.runtime/`, backups, caches, and local
  platform state are absent from Git tracking.
- [ ] Existing tasks, archived tasks, specs, shared journals, skills, and platform
  integration files are present in the initial Pull Request.
- [ ] Workspace validation passes locally and in GitHub Actions.
- [ ] When the GitHub plan supports private-repository branch protection, the
  default branch requires a Pull Request, one approval, the validation check,
  current-base synchronization, resolved conversations, and applies to
  administrators; force pushes and deletion are disabled.

## Notes

- The code repositories remain independent Git repositories and retain their own
  branches, commits, Pull Requests, and release workflows.
- The initial private repository is on a GitHub plan that rejects branch
  protection for private repositories. Keep the PR workflow documented and defer
  hard enforcement until the organization upgrades or explicitly approves making
  this metadata repository public.
