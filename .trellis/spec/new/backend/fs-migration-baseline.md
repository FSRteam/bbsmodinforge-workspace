# FS → FSR Migration Baseline

> Last updated: 2026-07-19. This is the source of truth for selecting the next
> FS range to migrate into `new/` (FSR, NeoForge 1.21.1).

> **Current-release clarification (2026-07-21):** The `mod_version_lock=2.3.1`
> entries below are frozen evidence for this historical migration baseline, not
> the current runtime release. Current Core is `2.3.2`; its only active final
> artifact identity is `new/build/libs/BBS_FSR_beta_v0.0.10.jar`, and Premiere
> requires `[2.3.2,)`. No older jar filename or compatibility alias is an active
> release dependency. Do not rewrite the historical markers, commands, or
> assertions below to `2.3.2`.

## 1. Scope / Trigger

Read and validate this marker before comparing, cherry-picking, or porting any
FS commit into FSR. It prevents stale checkouts, rewritten history, the wrong
Minecraft branch, or an obsolete FSR history line from becoming the baseline.

## 2. Signatures

```text
legacy_old2_checkout = old2.0/bbs-fs@27844c61802d13c1623fbe1553a89a19124d18a8
legacy_old2_status = stale-reference-only; not the migrated-through marker

fs_master_migrated_through = f76cce04a59b30017692e7725295dc987c8cc4c9
fs_1_21_1_reference_through = 42313660f9ea9599741bada7293ca491f3ebcb69
fs_1_20_1_reference_through = d10c3b1f3c9ff10b689e8faacd1aa2d0073e73ca

fsr_previous_remote_base = b41e8eb0c586ad18a35ce0329a1d504a7e8539d4
fsr_migration_initial_commit = 36a8e0dd8edea23f857cfe431cc6e29ef3113cc0
fsr_target_commit = 50b95cea2a68486faf601331aabf874dd033694d
fsr_target_branch = new/master

mod_version_lock = 2.3.1
particle_mainline_policy = keep legacy FSR particle system
particle_restore_marker = a0776721
particle_migration_archive = archive/fs-particle-migration-395f6927@54d6b74ea34defc0ad7c99919544a2173a9efcf5
```

Reference checkout:

```text
temp/bbs-fs-2.3
origin/master = f76cce04a59b30017692e7725295dc987c8cc4c9
origin/1.21.1 = 42313660f9ea9599741bada7293ca491f3ebcb69
origin/1.20.1 = d10c3b1f3c9ff10b689e8faacd1aa2d0073e73ca
```

## 3. Contracts

- `f76cce04` is the migrated master behavior tip.
- `42313660` is an independent 1.21.1 API reference. It does **not** contain
  `f76cce04` as an ancestor; never claim it represents every master change.
- `d10c3b1f` is the 1.20.1 reference and does contain `f76cce04` as an ancestor.
- `new` moved to the `b41e8eb0 → b004afd2 → 36a8e0dd` line. The former marker
  `838d11d9` is not an ancestor of this line and must not be used for ancestry checks.
- `36a8e0dd` is the empty initial boundary commit for this migration. The task
  terminal commit is recorded separately after the migration-owned commit exists.
- `cff7d87a` is rejected. FSR remains `mod_version=2.3.1` and does not claim FS 2.4.
- Particle paths remain on the legacy FSR implementation unless the user explicitly
  reverses that policy.
- A successful migration advances the master, both Minecraft reference markers and
  `fsr_target_commit` together. Trellis task metadata must record the same terminal SHA.

## 4. Validation & Error Matrix

| Condition | Required response |
|---|---|
| Any marker object cannot be fetched/read | Stop; do not migrate from guessed content |
| Previous master marker is not an ancestor of the new master tip | Stop; review rewritten history |
| Master tip is not an ancestor of the 1.20.1 reference | Stop; do not use that branch as behavior confirmation |
| 1.21.1 tip does not contain the master tip | Expected for this baseline; use it only for Mojmap/API comparison |
| Target initial boundary is not an ancestor of the proposed terminal commit | Stop; wrong target worktree/history |
| Proposed diff changes `mod_version` or includes `cff7d87a` | Reject the version change |
| Proposed diff changes legacy particle paths | Exclude or isolate on the archived particle branch |
| Build passes but interactive Iris/audio/UI/physics smoke is not run | Record it as pending; never report it as passed |
| Code commit exists but task/spec terminal SHA is missing | Task is incomplete; backfill exact SHA |

## 5. Good / Base / Bad Cases

- Good: fetch immutable tips, validate master and 1.20.1 ancestry, treat 1.21.1 as
  an independent API reference, port only the audited delta, pass checks/build, commit
  migration-owned files, then backfill the real terminal SHA.
- Base: upstream tips equal the stored markers; the next delta is empty.
- Bad: require `f76cce04` to be an ancestor of `42313660`; that relationship is false.
- Bad: compare the target against `838d11d9`; it belongs to an obsolete FSR line.
- Bad: stage `new/WORK_REPORT_2026-07-18.md` with migration code.

## 6. Tests Required

```powershell
git -C temp/bbs-fs-2.3 fetch --prune origin master 1.21.1 1.20.1
git -C temp/bbs-fs-2.3 cat-file -e f76cce04^{commit}
git -C temp/bbs-fs-2.3 cat-file -e 42313660^{commit}
git -C temp/bbs-fs-2.3 cat-file -e d10c3b1f^{commit}
git -C temp/bbs-fs-2.3 merge-base --is-ancestor f76cce04 d10c3b1f
git -C new merge-base --is-ancestor 36a8e0dd HEAD
git -C new diff --quiet 36a8e0dd -- ':(glob)**/*particle*'
Select-String -Path new/gradle.properties -Pattern '^mod_version=2.3.1$'
```

For an implementation migration also run:

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon check
.\gradlew.bat --no-daemon build
git diff --check
```

Assertions: every command succeeds; particle paths are unchanged; the built artifact
retains `2.3.1`; manual smoke status is explicitly recorded.

## 7. Wrong vs Correct

Wrong:

```powershell
git -C temp/bbs-fs-2.3 merge-base --is-ancestor f76cce04 origin/1.21.1
git -C new merge-base --is-ancestor 838d11d9 HEAD
```

Correct:

```powershell
git -C temp/bbs-fs-2.3 merge-base --is-ancestor f76cce04 origin/1.20.1
git -C new merge-base --is-ancestor 36a8e0dd HEAD
```
