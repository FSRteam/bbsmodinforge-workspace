# FS → FSR Migration Baseline

> Last updated: 2026-07-29. This is the source of truth for selecting the next
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

fs_master_migrated_through = 116ed3973163a09439c21f027b3d4307b8d893a9
fs_1_21_1_reference_through = 31bd255e528cd620218ced82bef5ec7f9eab6b32
fs_1_20_1_reference_through = c3d1a47732ea85b804a249460c183bbf7a08190e
fs_reference_merge_base = 1ab0a887bada3d6a4749314cbb009d9c90e260eb

fsr_previous_target_commit = e11ca91bbc7ae95dc1e8262a496cafa725e6cea5
fsr_target_commit = 4143499b19d0f6138b7f8e8e1ff729a5a7ecbf77
fsr_target_branch = codex/new-local-backup-20260727

fsr_mod_version_before_after = 0.0.10 -> 0.0.10
fsr_artifact_identity = BBS_FSR_beta_v0.0.10.jar
particle_mainline_policy = keep legacy FSR particle system
translucency_policy = keep FSR FormTranslucentQueue; reject FS 31884112 rollback
particle_restore_marker = a0776721
particle_migration_archive = archive/fs-particle-migration-395f6927@54d6b74ea34defc0ad7c99919544a2173a9efcf5
```

Reference checkout:

```text
temp/bbs-fs-2.3
local branch = codex/fsr-sync-20260729
origin/master = 116ed3973163a09439c21f027b3d4307b8d893a9
origin/1.21.1 = 31bd255e528cd620218ced82bef5ec7f9eab6b32
origin/1.20.1 = c3d1a47732ea85b804a249460c183bbf7a08190e
```

## 3. Contracts

- `116ed397` is the migrated master behavior tip.
- `31bd255e` is an independent 1.21.1 API reference. Use it for Mojmap/API
  comparison; do not assume it contains every master commit.
- `c3d1a477` is the 1.20.1 behavior reference. The three references share
  `1ab0a887` as their audited merge base.
- `4143499b` is the migration-owned FSR terminal commit; its parent is
  `e11ca91b`. Existing uncommitted target changes are outside this boundary.
- The migration preserves `mod_version=0.0.10` and artifact identity
  `BBS_FSR_beta_v0.0.10.jar`; upstream release numbers are not copied into FSR.
- `origin/2.5` is outside this migration scope.
- Particle paths remain on the legacy FSR implementation unless the user explicitly
  reverses that policy.
- FS commit `31884112` is a translucency rollback and is excluded; FSR keeps the
  stronger `FormTranslucentQueue` implementation.
- Deferred viewport picks must bind the picked Form/bone before starting a Gizmo.
  Gizmo startup snapshots the editable transform and origin, so reversing this
  order produces a visible drag that still writes the previously selected bone.
- A visible IK, physics, or constraints panel may own the Gizmo bone without
  mutating the pose editor's hidden multi-selection. Transform binding, origin
  sampling, and edit fan-out must all resolve through the same panel-owned bone.
- Replay category collectors must gather the complete track union before one
  centralized category filter runs. All-tracks mode bypasses only that category
  filter; global and per-Form disabled-track filters still apply. FSR-specific
  `FormControlKeys` classify IK controls as IK and physics/wind controls as Physics.
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
| Proposed diff changes FSR artifact identity | Reject unless the user separately authorizes a release change |
| Proposed diff changes legacy particle paths | Exclude or isolate on the archived particle branch |
| Proposed diff applies FS `31884112` translucency rollback | Reject; keep FSR queue ownership |
| Deferred pick starts the Gizmo before binding its Form/bone | Reject; the drag snapshots and edits a stale target |
| Procedural panel Gizmo changes the pose list selection | Reject; preserve the hidden multi-selection and override only the transform owner |
| Any replay collector checks the active category | Reject; collect all sheets, then filter centrally so all-tracks is complete |
| FSR control channel has no centralized category mapping | Add the `FormControlKeys` mapping and a regression assertion before porting |
| Build passes but interactive Iris/audio/UI/physics smoke is not run | Record it as pending; never report it as passed |
| Code commit exists but task/spec terminal SHA is missing | Task is incomplete; backfill exact SHA |

## 5. Good / Base / Bad Cases

- Good: fetch immutable tips, validate the recorded merge base, treat 1.21.1 as
  an independent API reference, port only the audited delta, pass checks/build, commit
  migration-owned files, then backfill the real terminal SHA.
- Good: a deferred sphere drag binds its picked bone, then starts the Gizmo; an
  all-tracks rebuild collects player, model, pose, IK, physics, wind, sound, and
  material sheets before filtering.
- Base: upstream tips equal the stored markers; the next delta is empty.
- Bad: copy the all-tracks toggle but leave category guards inside individual
  collectors, or add a panel-local selection list without routing Gizmo ownership to it.
- Bad: infer cross-branch coverage only from branch names instead of the audited merge base.
- Bad: stage shared files wholesale from a dirty FSR worktree.
- Bad: stage `new/WORK_REPORT_2026-07-18.md` with migration code.

## 6. Tests Required

```powershell
git -C temp/bbs-fs-2.3 fetch --prune origin master 1.21.1 1.20.1
git -C temp/bbs-fs-2.3 cat-file -e 116ed397^{commit}
git -C temp/bbs-fs-2.3 cat-file -e 31bd255e^{commit}
git -C temp/bbs-fs-2.3 cat-file -e c3d1a477^{commit}
git -C temp/bbs-fs-2.3 merge-base 116ed397 31bd255e 1ab0a887
git -C new merge-base --is-ancestor e11ca91b 4143499b
git -C new diff --quiet e11ca91b..4143499b -- ':(glob)**/*particle*'
Select-String -Path new/gradle.properties -Pattern '^mod_version=0.0.10$'
```

For an implementation migration also run:

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon check
.\gradlew.bat --no-daemon build
git diff --check
```

For viewport-pick and replay-track migration regressions also run:

```powershell
cd new
.\gradlew.bat --no-daemon testUiInputMailbox
.\gradlew.bat --no-daemon testReplayKeyframeConsistency
.\gradlew.bat --no-daemon testFilmEditorMigration
.\gradlew.bat --no-daemon testIkPhysicsConsistency
```

Assertions: a promoted deferred pick records `pick` before `start`; procedural
panel binding leaves pose multi-selection untouched; every collector is category
agnostic; all-tracks bypasses category filtering; ordinary tabs retain their own
category; IK/physics/wind control sheets use their FSR categories.

Assertions: every command succeeds; particle paths are unchanged; the built artifact
retains `BBS_FSR_beta_v0.0.10.jar`; manual smoke status is explicitly recorded.

## 7. Wrong vs Correct

Wrong:

```powershell
git -C new add src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory.java
```

Correct:

```powershell
git -C new diff <pre-migration-index-snapshot> -- <shared-file>
git -C new apply --cached <reviewed-migration-only.patch>
```

Wrong:

```java
if (this.category != ReplayCategory.IK) return;
this.startGizmo(context, Gizmo.STENCIL_TRACKBALL);
this.viewport.pickGizmoForm(context, form, bone);
```

Correct:

```java
this.collectIKSheets(sheets, this.replay.form.get());
this.viewport.pickGizmoForm(context, form, bone);
this.startGizmo(context, Gizmo.STENCIL_TRACKBALL);
```
