# FS → FSR Migration Baseline

> Last updated: 2026-08-01. This is the source of truth for selecting the next
> FS range to migrate into `new/` (FSR, NeoForge 1.21.1).

> **Current target lock (2026-08-01):** The completed migration preserves
> `mod_version=0.0.11` and artifact identity
> `BBS_FSR_beta_v0.0.11.jar`. Upstream FS release numbers must not be copied into
> FSR. Historical `0.0.10` evidence remains available in Git history.

## 1. Scope / Trigger

Read and validate this marker before comparing, cherry-picking, or porting any
FS commit into FSR. It prevents stale checkouts, rewritten history, the wrong
Minecraft branch, or an obsolete FSR history line from becoming the baseline.

## 2. Signatures

```text
legacy_old2_checkout = old2.0/bbs-fs@27844c61802d13c1623fbe1553a89a19124d18a8
legacy_old2_status = stale-reference-only; not the migrated-through marker

fs_master_migrated_through = 5848363c3e7c63ef124bad0c3b08809abd311f91
fs_1_21_1_reference_through = 5ed645834b46f7fd8c81e6c40dbc3e453949ac0b
fs_1_20_1_reference_through = 813be205e83b571823d58736b9213f8309811186
fs_reference_merge_base = 5848363c3e7c63ef124bad0c3b08809abd311f91

fsr_previous_target_commit = 0544eadacbd0d2d60fd27d2a317e9e73483fa468
fsr_target_commit = 94d2349f43b8d71dcf162e30f5f4294e3d9cc2e3
fsr_target_branch = codex/sync-fs-5848363-to-fsr

fsr_mod_version_before_after = 0.0.11 -> 0.0.11
fsr_artifact_identity = BBS_FSR_beta_v0.0.11.jar
particle_mainline_policy = keep legacy FSR particle system
translucency_policy = keep FSR FormTranslucentQueue; reject FS 31884112 rollback
particle_restore_marker = a0776721
particle_migration_archive = archive/fs-particle-migration-395f6927@54d6b74ea34defc0ad7c99919544a2173a9efcf5
```

Reference checkout:

```text
temp/bbs-fs-2.3
local branch = codex/fsr-sync-20260729
origin/master = 5848363c3e7c63ef124bad0c3b08809abd311f91
origin/1.21.1 = 5ed645834b46f7fd8c81e6c40dbc3e453949ac0b
origin/1.20.1 = 813be205e83b571823d58736b9213f8309811186
```

## 3. Contracts

- `5848363c` is the migrated master behavior tip and the audited merge base of
  the recorded 1.20.1 and 1.21.1 reference tips.
- `5ed64583` is the 1.21.1 API reference and `813be205` is the requested 1.20.1
  behavior reference. Both contain the recorded master tip.
- `94d2349f` is the migration-owned FSR terminal commit; its parent is
  `0544eada`. Existing changes in the original `new` checkout are outside this
  dedicated-worktree boundary.
- The migration preserves `mod_version=0.0.11` and artifact identity
  `BBS_FSR_beta_v0.0.11.jar`; upstream release numbers are not copied into FSR.
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
- Shared-file migration is semantic rather than wholesale. `UIPropTransform` must
  retain FSR remote-input protection and X/Y four-edge cursor wrapping;
  `UIValueMap` must retain audio channel, theme, motion, accent-follow, and video
  controls; trackpads/sliders must retain theme colors and Refreshed radii.
- Model-editor secondary thumbnails reuse the renderer's solved preview pose.
  DLS file diagnostics remain disabled by default and are enabled only for an
  explicit debugging session.
- A successful migration advances the master, both Minecraft reference markers and
  `fsr_target_commit` together. Trellis task metadata must record the same terminal SHA.

## 4. Validation & Error Matrix

| Condition | Required response |
|---|---|
| Any marker object cannot be fetched/read | Stop; do not migrate from guessed content |
| Previous master marker is not an ancestor of the new master tip | Stop; review rewritten history |
| Master tip is not an ancestor of the 1.20.1 reference | Stop; do not use that branch as behavior confirmation |
| Either Minecraft reference tip does not contain the recorded master tip | Stop; re-audit cross-branch coverage before migration |
| Target initial boundary is not an ancestor of the proposed terminal commit | Stop; wrong target worktree/history |
| Proposed diff changes FSR artifact identity | Reject unless the user separately authorizes a release change |
| Proposed diff changes legacy particle paths | Exclude or isolate on the archived particle branch |
| Proposed diff applies FS `31884112` translucency rollback | Reject; keep FSR queue ownership |
| Deferred pick starts the Gizmo before binding its Form/bone | Reject; the drag snapshots and edits a stale target |
| Procedural panel Gizmo changes the pose list selection | Reject; preserve the hidden multi-selection and override only the transform owner |
| Any replay collector checks the active category | Reject; collect all sheets, then filter centrally so all-tracks is complete |
| FSR control channel has no centralized category mapping | Add the `FormControlKeys` mapping and a regression assertion before porting |
| An upstream shared-file replacement removes FSR-only settings/input/theme behavior | Restore the target behavior and add a focused source or logic assertion before commit |
| DLS diagnostics create files during normal editing | Disable file diagnostics by default; require explicit opt-in for debugging |
| Build passes but interactive Iris/audio/UI/physics smoke is not run | Record it as pending; never report it as passed |
| Code commit exists but task/spec terminal SHA is missing | Task is incomplete; backfill exact SHA |

## 5. Good / Base / Bad Cases

- Good: fetch immutable tips, validate the recorded merge base, use 1.21.1 for
  API comparison, port only the audited delta, pass checks/build, commit
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
- Bad: accept a compiling upstream replacement that silently removes FSR audio,
  theme, remote-input, preview-cache, or logging-default behavior.

## 6. Tests Required

```powershell
git -C temp/bbs-fs-2.3 fetch --prune origin master 1.21.1 1.20.1
git -C temp/bbs-fs-2.3 cat-file -e '5848363c^{commit}'
git -C temp/bbs-fs-2.3 cat-file -e '5ed64583^{commit}'
git -C temp/bbs-fs-2.3 cat-file -e '813be205^{commit}'
git -C temp/bbs-fs-2.3 merge-base --is-ancestor 5848363c 5ed64583
git -C temp/bbs-fs-2.3 merge-base --is-ancestor 5848363c 813be205
git -C new merge-base --is-ancestor 0544eada 94d2349f
git -C new diff --quiet 0544eada..94d2349f -- ':(glob)**/*particle*'
git -C new diff --quiet 0544eada..94d2349f -- src/client/java/mchorse/bbs_mod/forms/renderers/FormTranslucentQueue.java
Select-String -Path new/gradle.properties -Pattern '^mod_version=0.0.11$'
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
.\gradlew.bat --no-daemon testAudioVerification
```

Assertions: a promoted deferred pick records `pick` before `start`; procedural
panel binding leaves pose multi-selection untouched; every collector is category
agnostic; all-tracks bypasses category filtering; ordinary tabs retain their own
category; IK/physics/wind control sheets use their FSR categories.

Assertions: every command succeeds; particle paths are unchanged; the built artifact
retains `BBS_FSR_beta_v0.0.11.jar`; manual smoke status is explicitly recorded.

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

## 8. Publishing Record

The 2026-08-01 migration was pushed and opened as a draft pull request.

```text
github_cli_path = C:\Users\29617\AppData\Local\Programs\gh\gh.exe
github_cli_version = 2.96.0
github_account = snowstarll
github_remote = https://github.com/FSRteam/bbs-FSR.git
pushed_branch = codex/sync-fs-5848363-to-fsr
pull_request = https://github.com/FSRteam/bbs-FSR/pull/15
pull_request_state = draft
```

On this machine Git's global proxy pointed at an unavailable local port. The
successful push used the authenticated GitHub CLI credential helper and cleared
the stale proxy only for that Git command; do not overwrite the user's global
proxy setting as part of a migration.
