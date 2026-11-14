# FS → FSR Migration Baseline

> Last updated: 2026-10-04 (section 3b added by the FS 2.6 review-finding fixes).
> This is the source of truth for selecting the next FS range to migrate into
> FSR (NeoForge 1.21.1).

## 0. Current target: FS 2.6 dev-1.21.1 (opened 2026-09-26)

**Active continuation (2026-11-07):** edit and build `E:\bbsmodinforge\new`,
not `new-fs26-sync`. The active target is `0ee9fef7a6c471d46f6c1abbd0a104ac774de682`
plus inherited working changes; the worktree markers below are historical.
The pinned range is `8d99d77feea3ad13f7ceada78ee7679571251bd4..da84e5f8fa1dcb49faffcc310aa2b80013fab99f`.
Review original first-parent patches in the recorded 676-commit order, including
refactors and live consumers. Welcome/Tour is excluded. Preserve FSR extensions;
do not commit/push. The user prohibits new or modified test sources/fixtures;
existing checks may run. Current coverage and verification live in
`.trellis/tasks/10-05-fs26-fsr-full-parity/research/current-progress-20261114.md`.

The original 2026-09-26 migration used the dedicated worktree
`E:\bbsmodinforge\new-fs26-sync`. These worktree markers and the 2.5-era
markers below remain as historical reference only.

```text
fs26_reference_branch = Wemppy4/bbs-fs@dev-1.21.1 (Fabric, dev line, not stable)
fs26_target_commit = da84e5f8fa1dcb49faffcc310aa2b80013fab99f (2026-09-20)
fs26_reference_clone = temp/bbs-fs-2.6 (full clone via local proxy 7890; direct git is reset-prone)
fs26_delta_from_master_baseline = 676 commits (commit distance, not feature count)
fs26_worktree = E:\bbsmodinforge\new-fs26-sync
fs26_worktree_branch = codex/fs26-sync-base
fs26_base_commit = 51e79f535 (new/ HEAD)
fs26_snapshot_commit = e0aa7c949feda119462fb91378924b48812187bc
fs26_terminal_commit = 04482f4e636037219a9ec9faf100a41b73a33301 (B10 closing head, 2026-10-03)
fs26_terminal_artifact = BBS_FSR_beta_v0.0.11.jar, 10476732 bytes, sha256 CDF600080656EC8543EBC3CDDEFDC53EA2D7B9DDA098EC501F5FF0D25FD53815
fs26_terminal_status = batch gate green (28 test* + apiCheck = 29 tasks, BUILD SUCCESSFUL); runClient smoke NOT recorded; (B)-class gaps in DIVERGENCES.md remain
fs26_snapshot_contents = new/ dirty state incl. uncommitted 2.5.1 replay + user WIP (multiview, updater, texture hardening); presets/, logs, run dirs excluded
fs26_gate = compileJava compileClientJava build green; artifact BBS_FSR_beta_v0.0.11.jar
fs26_scope = P0+P1+P2 accepted topics plus 2026-09-13..20 additions
fs26_excluded = Welcome/Tour, branch wholesale merge, Fabric gradle/mappings,
  upstream mod_version, static film-home ads/news/banner, Graph/Sandbox removal,
  52-chapter tutorial into core, upstream junk files (run-client2.log)
fs251_unmerged_branch = codex/sync-fs-8d99d77f-to-fsr@f0aa738d3 (never merged into new/; content re-applied uncommitted inside the snapshot)
```

Network note (2026-09-26): direct `git clone https://github.com/...` fails with
connection resets on this machine; api.github.com and codeload.github.com are
reachable; a local proxy on `127.0.0.1:7890` works for git. Use
`git -c http.proxy=http://127.0.0.1:7890 <cmd>` per command; never write the
proxy into global config.

> **Current target lock (2026-08-16):** The completed migration preserves
> `mod_version=0.0.11` and artifact identity
> `BBS_FSR_beta_v0.0.11.jar`. Upstream FS release numbers must not be copied into
> FSR. Historical `0.0.10` evidence remains available in Git history.

## 1. Scope / Trigger

Read and validate this marker before comparing, cherry-picking, or porting any
FS commit into FSR. It prevents stale checkouts, rewritten history, the wrong
Minecraft branch, or an obsolete FSR history line from becoming the baseline.
The production-artifact rules also apply whenever required Mixins are compiled
from a custom source set and the published artifact is assembled by JarJar.

## 2. Signatures

```text
legacy_old2_checkout = old2.0/bbs-fs@27844c61802d13c1623fbe1553a89a19124d18a8
legacy_old2_status = stale-reference-only; not the migrated-through marker

fs_master_migrated_through = 8d99d77feea3ad13f7ceada78ee7679571251bd4
fs_1_21_1_reference_through = e6b8e382c6d6fa004ba1528035d190250d61d5fa
fs_1_20_1_reference_through = 4cdee4ace033f12c450976bb8f9707f3cfa77092
fs_reference_merge_base = 8d99d77feea3ad13f7ceada78ee7679571251bd4

fsr_previous_target_commit = 497c4284c1f96a125c8eaa2f54d37851422a6543
fsr_target_commit = ef3a08f4f9d28fa142e4d31053c8a94715210da9
fsr_target_branch = codex/sync-fs-8d99d77f-to-fsr

fs_delta_6f4f9ec9 = ported
fs_delta_0cecc8c9 = target-equivalent-width-height-audit
fs_delta_4220b453 = ported
fs_delta_5b8d714f = rejected-with-reason
fs_delta_378bac69 = ported
fs_delta_95a110e2 = target-equivalent
fs_delta_8a293161 = ported
fs_delta_8c48a185 = ported
fs_delta_c02aa6e6 = ported
fs_delta_7f99e757 = rejected-with-reason
fs_delta_459e9db7 = ported
fs_delta_52c5db8f = ported
fs_delta_d0f3754d = ported
fs_delta_bb1ac00f = ported
fs_delta_fe552a58 = ported
fs_delta_0773f9df = ported
fs_delta_861257a7 = ported
fs_delta_baafc5a4 = ported
fs_delta_770c0e7c = ported

fsr_mod_version_before_after = 0.0.11 -> 0.0.11
fsr_artifact_identity = BBS_FSR_beta_v0.0.11.jar
particle_mainline_policy = keep legacy FSR particle system
translucency_policy = keep FSR FormTranslucentQueue; reject FS 31884112 rollback
particle_restore_marker = a0776721
particle_migration_archive = archive/fs-particle-migration-395f6927@54d6b74ea34defc0ad7c99919544a2173a9efcf5
client_refmap_source = build/tmp/compileClientJava/bbs.client.refmap.json
client_refmap_artifact = bbs.client.refmap.json
minecraft_main_render_target_srg = f_91042_
```

Reference checkout:

```text
temp/bbs-fs-2.3
local branch = codex/fsr-sync-20260729
origin/master = 8d99d77feea3ad13f7ceada78ee7679571251bd4
origin/1.21.1 = e6b8e382c6d6fa004ba1528035d190250d61d5fa
origin/1.20.1 = 4cdee4ace033f12c450976bb8f9707f3cfa77092
```

## 3. Contracts

- `8d99d77fe` is the migrated master behavior tip and the audited merge base of
  the recorded 1.20.1 and 1.21.1 reference tips.
- `e6b8e382` is the 1.21.1 API reference and `4cdee4ac` is the requested 1.20.1
  behavior reference. Both contain the recorded master tip.
- `ef3a08f4` is the migration-owned FSR code terminal commit; its initial boundary is
  `497c4284`. Existing changes in the original `new` checkout are outside this
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
- Shared settings migrations fill only absent canonical values. Legacy initial
  local-frame preferences seed `transformation.transform_space`; an explicit new
  ordinal wins. `personalization.interface_glow` is the canonical glow boolean;
  retain the old panel-glow public field/method as aliases and gate the actual
  `Batcher2D.dropShadow` primitive so tooltips, notifications and clip glows follow
  the same switch. Curated replay channels are revealed once using the persisted
  `replay_sections_filter_migrated` marker; later manual filters must survive reload.
- World point/path/marker size uses ungated `getScreenSizeScale`. Gizmo fixed-size
  visibility/settings must not change those markers. Retain the legacy
  `getAxesDistanceScale` helper for addon compatibility without routing world point
  consumers back through the gizmo option.
- `SplitterHandleInfo` is geometry-only in FSR. Splitter handles and
  `EditorLayoutNode.collectSplitters(...)` both use pre-order traversal; operations
  such as double-click-to-even must resolve the splitter through that ordered tree
  list, never through a nonexistent node field on the handle geometry.
- Dock hidden-panel IDs and unlocked state are stored by layout ID in
  `ValueEditorLayout`. Film editor-specific IDs use `film.<editor>` while the shared
  film and particle layouts keep `film` and `particle`.
  Active Film UI chooses `isBound(editorId) ? editorId : FILM` through generic
  `getLayout/setLayout`. VERSION1 stores `layouts` and `bound`; pre-version
  camera/replay/action trees are migrated without losing dormant copies.
  Legacy public per-editor helpers retain inherited FSR independent-tree behavior.
  See [Editor layout and preview rendering](./editor-layout-preview-contract.md)
  for the storage contract and the explicit isolated preview queue exception.
- `EditorLayoutNode.fromData` returns null for an unreadable tree and collapses a
  broken splitter side to its usable sibling. The owning editor supplies its own
  default; the generic decoder must never inject film panel IDs into a particle
  dock. Fresh film defaults put the inspector in a full-height right column.
- Sidebar splitter positions use the FS `splits` settings map. Read the earlier
  FSR `split_sizes` map as a compatibility fallback, ignore nonnumeric entries,
  and clear remembered positions on every reload before reading the new data.
- `onLayoutSettled` fires after completed layout bounds changes and splitter drag
  end, not on each drag frame. Film preview auto-sizing must subscribe to this
  callback so maximize, undo, hide/show, tab activation, drop and reset stay in sync.
- Model-editor secondary thumbnails reuse the renderer's solved preview pose.
  DLS file diagnostics remain disabled by default and are enabled only for an
  explicit debugging session.
- A successful migration advances the master, both Minecraft reference markers and
  `fsr_target_commit` together. Trellis task metadata must record the same terminal SHA.
- A Minecraft-version backport is not startup-verified by Java compilation or Mixin
  annotation processing alone. Every required Mixin descriptor must be checked against
  the target runtime, and the exact Java/toolchain target must complete a client launch
  through resource reload to the title screen.
- On the locked NeoForge 1.20.1 runtime, do not use Mixin 0.8.5 `@ModifyArgs` on the
  per-part model render path. Its generated `org.spongepowered.asm.synthetic.args.Args$*`
  class is not reliably visible through the ModLauncher module classloader. Use an exact
  `@Redirect` plus private `@Shadow` for the target invocation when composition with an
  optional renderer is not required.
- A required Mixin must not rely on a Mojmap `@Shadow` member merely because it works in
  the development runtime. Before delivery, the reobfuscated JAR's refmap must contain
  the member mapping and its transformed bytecode must target the expected production
  name. When the target already exposes a public accessor, prefer a normal Java call so
  the reobfuscation task owns the mapping instead of the Mixin refmap.
- A client Mixin refmap generated by a custom `client` source set is a declared output of
  `compileClientJava` and an explicit input of the published `jarJar` task. The JarJar task
  must fail unless its archive contains exactly one `bbs.client.refmap.json` with the
  locked production mappings. Setter accessors for final fields, including
  `Minecraft.mainRenderTarget`, must also carry `@Mutable`.
- Film preview framebuffer handoff must not depend on the camera controller being absent.
  BBS screens hide the vanilla HUD while the Film runner remains active, so `Gui.render`
  is skipped and the `GameRenderer` hidden-HUD hook becomes the only path that copies the
  private world target and restores `Minecraft.mainRenderTarget`. The copy method owns an
  idempotent inactive guard; callers must not add a `currentController == null` guard.
- Oculus 1.8 on Minecraft 1.20.1 exposes main-target state through
  `pipeline.getRenderTargetStateListener().setIsMainBound(boolean)`, not the later
  `WorldRenderingPipeline.setIsMainBound(boolean)` API. Keep this bridge optional and
  reflection-only unless the exact Oculus artifact becomes a declared dependency.

## 3b. Contracts carried in by the FS 2.6 review fixes (2026-10-04)

Read these before touching the migrated surfaces they name; each one was a confirmed defect on the
2.6 target and is now covered by a source-contract assertion in the `replayKeyframeTest` or
`dataMigrationTest` gate.

- Stable-ID conversion walks <em>every</em> film camera clip collection, not only `camera`:
  `camera_tracks[*].clips` is a second list of the same clip shape. A conversion that skips it leaves
  legacy numeric selectors unconverted, and they read back as `""`, silently losing the replay target.
  The conversion stays idempotent: an already-stable string selector is never rewritten.
- `KeyframeChannel.sort` orders ticks with `Float.compare`, never with a truncated
  `(int) (a - b)`: fractional ticks less than one apart compare equal under the subtraction and leave
  the list unsorted, while segment lookup assumes ordering. A move onto an occupied tick replaces the
  occupant (`KeyframeChannel.sort(List<Keyframe>)`), and reselection after a sort is by keyframe
  reference, not by a remembered index a removal could shift.
- Keyframe selection operations (`clearSelection`, `selectAll`, `selectAfter`, `getSelected`,
  `getSheet(String)`, `removeSelected`) iterate every sheet, not only the currently visible ones:
  folding a nested track only hides its rows, and a selection made before the fold must stay
  reachable. A viewport pick reveals the row it selected (`UIKeyframeDopeSheet.revealSheet`) instead
  of leaving the highlight behind a fold.
- A numeric keyframe edit is one value gesture: snapshot the channels when the drag starts, submit
  exactly one unmergeable edit on accept, and restore the snapshot on cancel. Cancelling must not
  write the initial value back as a compensating edit, and removing the editor mid-gesture cancels
  instead of leaving a half-applied edit.
- Focused text inputs request the I-beam only while the pointer is inside their own bounds. Focus
  keeps controlling keyboard input; it is not a reason to keep the text cursor.
- CEM and vanilla-pose yaw use Mojmap `Mth.rotLerp` for the interpolation and `Mth.wrapDegrees` for
  the relative angle, so a ±180° crossing takes the short path. Do not port Fabric's `MathHelper`
  name.
- `ModelMesh.toData` writes vertices relative to `origin`, because runtime vertices include the pivot;
  `fromData` adds it back. Writing runtime vertices unchanged shifts the mesh by its pivot on every
  save/load round trip.
- Action clips mount under the Replay editor's timeline/inspector owner. Preserve
  `UIFilmPanel.actionEditor`, `openActionEditor` and saved panel index 2 as adapters
  that explicitly open Actions. `NEXT_PANEL` cycles Camera/Replay; the Action
  shortcut toggles the current mode via `toggleActionTimeline`, so pressing it
  again returns to keyframes with the viewport retained.
- `UIAnimationStateEditor.getOriginInternal` uses the full matrix for
  `forceMatrix || bone.b` and the parent origin otherwise. This is the correction
  introduced by `3a635fee1`, not merge23's intermediate inverted Boolean branch.
  The Boolean bone API is an FSR compatibility adapter; `getBoneSpace()` carries
  the complete display/drag frame separately. Final `TransformSpace.placesOnOwnFrame()`
  is true only for LOCAL, matching the adapter's `isLocal()` condition. The later
  typed-tuple extraction must still be reviewed at its own introducing commit.
- `ModelPivotFrames.collect(model, wanted, out)` delegates to the four-argument
  overload with a null base transform. Both default overloads consult
  `FormPoseEvents.PIVOT_OFFSETS`; explicit boolean/workspace overloads retain
  their caller's choice. The later event extraction is `35eeefa0657f5078d430bd08bb216801fb1aa042`.
  `ModelPhysicsDebug` uses the three-argument entry point, so hardcoding false
  there makes an addon's runtime offsets disagree with its overlay. No listener
  returns false and preserves the nominal-frame default.

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
| Required Mixin compiles but the target descriptor differs at runtime | Reject; derive the complete 1.20.1 descriptor and rerun the Java 17 client smoke |
| NeoForge 1.20.1 model rendering uses `@ModifyArgs` | Reject; use the startup-safe exact invocation redirect and retain a source gate |
| Development launch resolves an `@Shadow` but the reobfuscated refmap omits it | Reject the artifact; use a mapped public accessor or repair the member mapping, then inspect the rebuilt production JAR |
| Slim JAR contains the client refmap but JarJar/reobfJarJar does not | Reject the artifact; declare the generated refmap as a compile output and explicit JarJar input, then rebuild |
| Setter accessor targets a final Minecraft field without `@Mutable` | Reject; add `@Mutable` before runtime mutation can produce `IllegalAccessError` on Java 17 |
| Hidden-HUD preview hook requires the camera controller to be null | Reject; Film keeps a runner active while hiding HUD, so the preview copy and target restore would never execute |
| Oculus 1.20.1 bridge calls `WorldRenderingPipeline.setIsMainBound` | Reject; use `getRenderTargetStateListener().setIsMainBound` and fail closed when Oculus is absent |
| Code commit exists but task/spec terminal SHA is missing | Task is incomplete; backfill exact SHA |
| A stable-ID conversion walks only `camera` and skips `camera_tracks[*].clips` | Reject; legacy selectors there deserialize to `""` and lose their replay |
| A keyframe channel sorts ticks by a truncated `int` difference | Reject; distinct fractional ticks compare equal and the list stays unsorted |
| A keyframe move leaves the destination key in place beside the moved key | Reject; one tick holds one key |
| A folded nested track keeps a selection unreachable by clear/remove/navigation | Reject; selection operations must not filter on visibility |
| A numeric keyframe cancel writes the initial value back as an edit | Reject; restore the gesture snapshot so no rejected edit enters history |
| A focused text input requests the I-beam outside its bounds | Reject; the cursor is a pointer-bound decision, not a focus decision |
| CEM/vanilla yaw is interpolated linearly or subtracted unwrapped | Reject; use `Mth.rotLerp` / `Mth.wrapDegrees` |
| `ModelMesh.toData` writes pivot-inclusive vertices | Reject; the file stores vertices relative to `origin` |
| Repeated Action shortcut always opens the Action adapter | Toggle from Replay's current mode; direct legacy adapter calls still explicitly open Actions |
| A merge23 origin branch is copied over the later state-editor correction | Keep full local/forced sampling and parent-origin sampling for other frames |
| Default pivot overload hardcodes false while the other consults `PIVOT_OFFSETS` | Delegate both defaults through the event-aware entry point; preserve explicit boolean overrides |

## 5. Good / Base / Bad Cases

- Good: fetch immutable tips, validate the recorded merge base, use 1.21.1 for
  API comparison, port only the audited delta, pass checks/build, commit
  migration-owned files, then backfill the real terminal SHA.
- Good: a deferred sphere drag binds its picked bone, then starts the Gizmo; an
  all-tracks rebuild collects player, model, pose, IK, physics, wind, sound, and
  material sheets before filtering.
- Good: the final reobfuscated JarJar contains one common refmap and one client
  refmap, and the client refmap maps `MinecraftAccessor.mainRenderTarget` to
  `f_91042_`.
- Base: upstream tips equal the stored markers; the next delta is empty.
- Bad: copy the all-tracks toggle but leave category guards inside individual
  collectors, or add a panel-local selection list without routing Gizmo ownership to it.
- Bad: infer cross-branch coverage only from branch names instead of the audited merge base.
- Bad: stage shared files wholesale from a dirty FSR worktree.
- Bad: stage `new/WORK_REPORT_2026-07-18.md` with migration code.
- Bad: accept a compiling upstream replacement that silently removes FSR audio,
  theme, remote-input, preview-cache, or logging-default behavior.
- Bad: inspect only the slim JAR or assume that `mixin.add sourceSets.client` makes
  a separately assembled JarJar inherit the generated client refmap.
- Good: direct `showPanel(actionEditor)` opens Actions, while two Action shortcut
  presses return to Replay keys and preserve the view. Bad: use the direct adapter
  as an unconditional toggle callback.

## 6. Tests Required

```powershell
git -C temp/bbs-fs-2.3 fetch --prune origin master 1.21.1 1.20.1
git -C temp/bbs-fs-2.3 cat-file -e '8d99d77fe^{commit}'
git -C temp/bbs-fs-2.3 cat-file -e 'e6b8e382^{commit}'
git -C temp/bbs-fs-2.3 cat-file -e '4cdee4ac^{commit}'
git -C temp/bbs-fs-2.3 merge-base --is-ancestor 8d99d77fe e6b8e382
git -C temp/bbs-fs-2.3 merge-base --is-ancestor 8d99d77fe 4cdee4ac
git -C new-fs25-sync merge-base --is-ancestor 497c4284 ef3a08f4
git -C new-fs25-sync diff --quiet 497c4284..ef3a08f4 -- src/main/java/mchorse/bbs_mod/particles
git -C new-fs25-sync grep -n "class FormTranslucentQueue" ef3a08f4 -- src/client/java/mchorse/bbs_mod/forms/FormTranslucentQueue.java
Select-String -Path new/gradle.properties -Pattern '^mod_version=0.0.11$'
```

For an implementation migration also run:

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon testUiInputMailbox
.\gradlew.bat --no-daemon testFilmEditorMigration
.\gradlew.bat --no-daemon testPlatformResourceConsistency
.\gradlew.bat --no-daemon check
.\gradlew.bat --no-daemon build
.\gradlew.bat --no-daemon reobfJarJar
git diff --check
```

For required Mixins that access Minecraft members, inspect the reobfuscated class with
JDK 17 `javap -c -p` and inspect its refmap entry. A source-only or development-runtime
check is insufficient because missing member mappings fail only under production names.
Also assert that the final reobfuscated archive contains exactly one
`bbs.client.refmap.json`, that `MinecraftAccessor.mainRenderTarget` maps to
`f_91042_`, and that the setter accessor bytecode retains `@Mutable`.

For Minecraft-version backports, also run `runClient` with the target Java version.
Passing means the client completes resource reload and remains stable at the title
screen without `MixinApplyError`, `InvalidInjectionException`, generated `Args$*`
class failures, or a new crash report.

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

For the 2026-11-07 continuation, reuse the existing `FilmEditTargetParityTest`
through `testReplayKeyframeConsistency` to check repeated Action toggles, legacy
index 2, shared timeline visibility and viewport transfer. Do not add or change
tests under the current user instruction. Gate17 records a successful execution;
subsequent edits require checks appropriate to those edits.

For the default pivot-overload correction, run the existing
`testIkPhysicsConsistency` suite. Source review must trace `ModelPhysicsDebug`
through the event-aware default; the existing suite does not establish live
addon-overlay rendering acceptance. Do not write new tests under the current
user instruction.

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

Wrong:

```groovy
tasks.named('jarJar') {
    from sourceSets.client.output
}
```

Correct:

```groovy
def clientRefmapFile = layout.buildDirectory.file('tmp/compileClientJava/bbs.client.refmap.json')
tasks.named('compileClientJava') { outputs.file(clientRefmapFile) }
tasks.named('jarJar') { from clientRefmapFile }
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
