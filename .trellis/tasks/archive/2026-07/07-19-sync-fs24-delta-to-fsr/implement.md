# 执行计划：FS 最新增量迁移到 FSR

## 0. Boundary gate

- [x] Pin `new` initial commit, upstream master/1.21.1 commits and version lock.
- [x] Confirm tracked target worktree and source checkout are clean.
- [x] Persist the audited commit matrix in `research/upstream-delta.md`.

## 1. Parallel implementation

- [x] Correctness/UI fixes cluster.
- [x] Physics/body-part cluster.
- [x] Minecraft sound capture/mix/mux cluster.
- [x] Translucent form queue cluster.
- [x] Fractional UI scale cluster.

Each agent must read task/spec context, inspect both upstream diffs and target code, edit
only its assigned files where possible, and report shared-file requirements instead of
blindly overwriting concurrent work.

## 2. Integration

- [x] Merge shared settings, keys, lifecycle and en/ru/zh language changes.
- [x] Reconcile with existing FSR fixes; remove duplicate or weaker implementations.
- [x] Verify all upstream commits have a coverage disposition.
- [x] Verify legacy particle paths and version properties are unchanged.

## 3. Validation

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon build
```

- [x] Run focused video export, IK physics, UI input mailbox and platform resource tests.
- [x] Record manual smoke for audio export/cancel/failure, translucent forms with and
  without Iris, GUI scales including auto/high-DPI, stencil picking, and relative replay
  as pending user validation in `research/upstream-delta.md`.
- [x] Run `git diff --check` and scan changed files for Fabric/Yarn imports, debug output and stubs.

## 4. Finish

- [x] Update `.trellis/spec/new/backend/fs-migration-baseline.md` to the verified tips and terminal SHA.
- [x] Create migration-owned commit `50b95cea` without staging `WORK_REPORT_2026-07-18.md`.
- [x] Record the real terminal commit in task metadata and `task.json.commit`.
- [x] Re-run version-lock and ancestry checks after the terminal commit.
