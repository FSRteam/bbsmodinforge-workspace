# Acceptance closure map

> Snapshot: 2026-07-18, current dirty `new/` worktree after the serial automated quality gate. No real NeoForge client/server/GPU/FFmpeg manual scenario was run.

## Status legend

- `STATIC-MAPPED`: production implementation and an automatic or manual verification owner are identifiable.
- `AUTOMATED-PASS`: the mapped compile/test/check/build gate passed on this snapshot.
- `MANUAL-OPEN`: deterministic source/unit coverage exists, but the required real client/server/GPU/FFmpeg scenario is still `未测`.

## Parent acceptance criteria

| Parent criterion | Production / child owner | Automatic evidence | Manual evidence | Status |
| --- | --- | --- | --- | --- |
| Every confirmed review item is mapped to a child and closed by fix/degrade/counter-proof | Ten `07-17-*` child PRDs; cross-cutting input additions remain under `07-17-input-disconnect-lifecycle` | Fifteen test source sets expose sixteen `JavaExec` gates, all wired into `check` | Parent matrix plus `REMOTE-13` for real-widget multi-button ownership | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Repository paths and server mutations are contained and authorized | `FolderManager`, `FilmManager`, `PacketCrusher`, `ServerNetwork`, `NetworkMutationSessions`, `NetworkDirectActionGate` | `testFolderManagerContainment`, `testNetworkSecurity` (`NetworkFilmKeyTest`, `PacketCrusherTest`, mutation/source-shape tests) | `AUTH-01`, `AUTH-06`, `AUTH-09`, `AUTH-10`, `COMPAT-03` | `MANUAL-OPEN` |
| Gun hand/owner, block recording, attacks and entity interactions match NeoForge dispatch | `GunItem`, `GunProjectileEntity`, `ServerPlayNetworkHandlerMixin`, `ServerPlayerInteractionMixin`, `AttackRecordingContext`, action clips | `testInteractionActions`; gun policy/budget/wiring also run from `testNetworkSecurity` | `BOOT-03`, `ACT-01` through `ACT-13` | `MANUAL-OPEN` |
| Local/remote input, screen change, disconnect and held cleanup keep one owner | `UIScreen`, `UIBaseMenu`, `BBSUiInputDispatcher`, `BBSUiRemoteHeldState`, `MouseGestureOwnership`, widget wrappers | `testUiInputMailbox` including modifier timeline, batch/screen fences, release/cancel, overlay/hierarchy/context-menu paths | `REMOTE-03` through `REMOTE-13` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Remote repository identity, HUD, export and UI resources restore in paired terminal paths | `RepositorySession`, `UIFilmPanel`, `BBSModClient`, `VideoExportSession`, `PanelVideoExportSession`, `UIScreen` | `testUiInputMailbox`, `testVideoExportUtils`, lifecycle source checks | `EXPORT-03` through `EXPORT-06`, `REMOTE-01`, `REMOTE-02`, `REMOTE-10`, `REMOTE-11` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Particle event/JSON/space and Iris/GL state regressions are covered | particle event helpers, `ParticleEmitter`, `FormRenderingContext`, `ParticleFormRenderer`, `TrailFormRenderer`, `BBSRendering` | `testParticleRuntimeConsistency` | `RENDER-01` through `RENDER-07` | `MANUAL-OPEN` |
| IK/physics final quaternion, pole, softness, owner/space and selection obey the quality spec | `IKSolver`, `ModelIKApplier`, `ModelRotationBlender`, `ModelPhysicsRuntime`, form render context and model editor selection | `testIkPhysicsConsistency` | `IK-01` through `IK-10`, child `manual-test.md` | `MANUAL-OPEN` |
| Replay references, keyframe shortcuts and Film timeline/Gizmo/scroll behavior regressions are covered | replay remappers, Film editor migration logic, UI replay/timeline/keyframe owners | `testReplayKeyframeConsistency`, `testFilmEditorMigration`, `testUiInputMailbox` | `FILM-01` through `FILM-08`, `REMOTE-13` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Addon first-wins/window/error/late-client contracts hold; API/IDs stay frozen | addon collector/manager/window/facades, client registration, `NetworkCompat` | `testAddonLifecycle`, `testClientStructuralRegistration` | `BOOT-02`, `BOOT-04`, `COMPAT-01` | `MANUAL-OPEN` |
| `git diff --check`, compile, targeted tests, `check`, and `build` pass | `new/build.gradle` wires all sixteen custom gates into `check` | Serial full gate passed 2026-07-18: `BUILD SUCCESSFUL`, 62 tasks | N/A | `AUTOMATED-PASS` |
| Unrelated user work is preserved | Current dirty tree retained; no reset/checkout/commit performed | Final diff ownership review still required | N/A | `STATIC-MAPPED` |

## Child acceptance criteria

| Child / criterion | Production mapping | Automatic mapping | Manual mapping | Status |
| --- | --- | --- | --- | --- |
| Network: traversal, absolute, sibling-prefix and root edges rejected | `FolderManager.resolveContained/getContainedFile`; `FilmManager` canonical lookup | `FolderManagerContainmentTest`, `NetworkFilmKeyTest` | `COMPAT-03` | `MANUAL-OPEN` |
| Network: same transfer id isolated by connection/channel; limits/timeout/disconnect release | `PacketCrusher.TransferKey`, owner/global quotas and cleanup | `PacketCrusherTest` | `AUTH-10` | `MANUAL-OPEN` |
| Network: unauthorized pause/panel/form/model-block mutations rejected | `ServerNetwork` handlers plus `NetworkMutationSessions`/permission and distance/dimension gates | `ServerFilmRuntimeAuthoritySourceTest`, `ServerModelBlockMutationSourceTest`, `ServerSharedFormAuthoritySourceTest`, session tests | `AUTH-01`, `ACT-12` | `MANUAL-OPEN` |
| Network: targeted gate, compile and build | `testNetworkSecurity` and `testFolderManagerContainment` are in `check` | Passed in the serial full gate | `AUTH-*`/`COMPAT-03` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Interaction: dual-gun/hand consumption, projectile owner and self-hit boundary | `GunItem.use`, `GunProjectileBudget`, `GunProjectileEntity` owner/lease | gun tests in `testNetworkSecurity`; action wiring in `testInteractionActions` | `ACT-01` through `ACT-03` | `MANUAL-OPEN` |
| Interaction: SKIP/PASS/default block dispatch records/replays one real branch | block/item packet redirects, `InteractionActionSemantics`, full-dispatch clips | `InteractionActionSemanticsTest` | `ACT-04`, `ACT-05` | `MANUAL-OPEN` |
| Interaction: sweep/non-living attack and entity interact/interactAt semantics | `AttackRecordingContext`, attack mixins/clips, `ServerPlayerInteractionMixin`, `EntityInteractionActionClip` | `InteractionActionSemanticsTest`, targeted actor isolation tests | `BOOT-03`, `ACT-06` through `ACT-08` | `MANUAL-OPEN` |
| Interaction: private Actor skips LivingEntity hooks/world effects while preserving targeted visuals | `ActorEntity.tickPlaybackProjectionServer`, hook-free `swing`, isolated server equipment gate | `PlaybackActorDamageIsolationSourceTest`, `InteractionActionSemanticsTest` | `AUTH-07`, `AUTH-11` | `MANUAL-OPEN`; outer `EntityJoinLevelEvent` and `EntityTickEvent.Pre/Post` remain observable until a client-only projection architecture exists |
| Interaction: compile/action/build gate | `testInteractionActions` is in `check` | Passed in the serial full gate | `ACT-*` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Input: local/remote conflict, screen-changing batch, button release/cancel and clear/disconnect | `BBSUiInputDispatcher`, `UIScreen`, `UIBaseMenu`, remote held state | `BBSUiInputDispatcherTest`, `BBSUiModifierTimelineTest`, `BBSUiOpenDispatcherTest` | `REMOTE-03` through `REMOTE-09`, `REMOTE-13` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Input: disconnect preserves pinned remote repository and unsaved changes | `RepositorySession`, Film panel force-save/lifecycle ordering | `BBSUiLifecycleSourceTest` plus repository unit path | `REMOTE-01`, `REMOTE-02` | `MANUAL-OPEN` |
| Input: original F1 HUD state restored | `UIScreen.lastHideGui` and teardown aggregation | lifecycle source checks | `REMOTE-10` | `MANUAL-OPEN` |
| Input: mailbox/compile/build gate | `testUiInputMailbox` is in `check` | Passed in the serial full gate | `REMOTE-*` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Particle: distant first placement and independent collision crossings | `ParticleEmitter` root baseline; collision crossing state | `ParticleRuntimeConsistencyTest` | `RENDER-04` | `MANUAL-OPEN` |
| Particle: edited known entry preserves unknown/raw siblings | shared event trigger/collision/looping helpers | `ParticleRuntimeConsistencyTest` | `RENDER-05` | `MANUAL-OPEN` |
| Particle: Actor/ModelBlock Particle/Trail stay in semantic space | explicit `FormRenderSpace` and renderer branches | `ParticleRuntimeConsistencyTest` render-space checks | `RENDER-01`, `RENDER-02`, `RENDER-06` | `MANUAL-OPEN` |
| Particle: targeted/compile/build gate | `testParticleRuntimeConsistency` is in `check` | Passed in the serial full gate | `RENDER-01` through `RENDER-07` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| IK: cubic/BOBJ tip, physics twist, short chains and limits | final-orientation clamps and constrained parent-frame propagation | numerical cases in `IKPhysicsConsistencyTest` | `IK-06`, `IK-07` | `MANUAL-OPEN` |
| IK: pole/softness/owner/first-person match quality spec | bind invalidation, softness zero default, renderer-owned workspace and local/world policy | `IKPhysicsConsistencyTest` | `IK-01` through `IK-08`, `IK-10` | `MANUAL-OPEN` |
| IK: ordinary/Ctrl selection targets the visible correct Form owner | `UIModelForm.pickBoneInList`, stencil/Gizmo ID ownership | structural/numerical test coverage; compile gate | `IK-09` | `MANUAL-OPEN` |
| IK: compile/numerical/build/manual gate | `testIkPhysicsConsistency` is in `check` | Passed in the serial full gate | child matrix and `IK-01` through `IK-10` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Film migration: five regressions have automatic or explicit manual checks | `FilmEditorMigrationLogic`, Film/timeline/Gizmo/scroll hooks | `FilmEditorMigrationLogicTest` | `FILM-01` through `FILM-04`, `FILM-07` | `MANUAL-OPEN` |
| Film migration: six cameras/context menu/save/exit/Web Remote do not regress | existing Film controller/panel paths retained around migration hooks | structural hook assertions | `FILM-06` | `MANUAL-OPEN` |
| Film migration: compile/build gate | `testFilmEditorMigration` is in `check` | Passed in the serial full gate | `FILM-*` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Replay: deleting/reordering A/B/C/D remaps all references by identity | `ReplayIndexRemapper`, `ReplayReferenceRemapper`, UI transaction paths | `ReplayIndexRemappingTest`, identity source test | `FILM-05`, `FILM-08` | `MANUAL-OPEN` |
| Replay: blank/no-sheet/no-selection keyframe shortcuts are safe | `UIKeyframes` navigation helpers/guards | `KeyframeNavigationTest` | `FILM-05` | `MANUAL-OPEN` |
| Replay: targeted/compile/build gate | `testReplayKeyframeConsistency` is in `check` | Passed in the serial full gate | `FILM-05`, `FILM-08` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Addon: duplicate error/cross-v1-v2/retained facade/late client/LinkageError regressions | addon identity/window/manager/facades and client registration | `BBSAddonLifecycleTest`, `ClientStructuralRegistrationWindowTest` | `BOOT-04` | `MANUAL-OPEN` |
| Addon: first-wins/window/diagnostics match docs | addon manager/collector diagnostics, `docs/addon-api-2.md` | lifecycle tests | `BOOT-04`, `COMPAT-01` | `MANUAL-OPEN` |
| Addon: API `2.0` and `s1..s15/c1..c19` unchanged | API constants and frozen `NetworkCompat` bindings | frozen-constant tests | `COMPAT-01` | `MANUAL-OPEN` |
| Addon: dedicated server/compile/build gate | common/client packages remain split; client registration has its own test | `testAddonLifecycle`, `testClientStructuralRegistration`, and `testBinaryCompatibility` passed | `BOOT-02` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Export: fake process/invalid args cover startup, pipe, nonzero, cancel and disconnect-equivalent cancellation | `VideoExportProcess`, `VideoExportSession`, resolution action gate | `VideoExportUtilsTest`, `VideoExportSessionTest`, `ExportResolutionActionGateTest` | `EXPORT-03` through `EXPORT-06` | `MANUAL-OPEN` |
| Export: failure/cancel have no success effects; real success keeps behavior | explicit session state/teardown and success-only side effects | video export tests | `EXPORT-01`, `EXPORT-03`, `EXPORT-04` | `MANUAL-OPEN` |
| Export: utility/compile/build gate | `testVideoExportUtils` is in `check` | Passed in the serial full gate | `EXPORT-*` | `AUTOMATED-PASS` / `MANUAL-OPEN` |
| Platform: Actor 16 chunks and gun 24 chunks | block-to-chunk conversion in `FabricRegistryCompat` | `PlatformResourceConsistencyTest` | `BOOT-05` | `MANUAL-OPEN` |
| Platform: `assets_backup`, root and link escapes are rejected | `ExternalAssetsSourcePack` canonical containment | `PlatformResourceConsistencyTest` | `COMPAT-03` | `MANUAL-OPEN` |
| Platform: default English contains canonical Chroma sky key | `en_us.json` | `PlatformResourceConsistencyTest` | `COMPAT-02` | `MANUAL-OPEN` |
| Platform: resource/unit/compile/build gate | `testPlatformResourceConsistency` is in `check` | Passed in the serial full gate | `BOOT-05`, `COMPAT-02`, `COMPAT-03` | `AUTOMATED-PASS` / `MANUAL-OPEN` |

## Current unclosed findings

### P0

- None established by static inspection.

### P1

- No open P1 was established after the final automated review and serial gate.
- All real NeoForge/dedicated-server/GPU/Iris/FFmpeg/manual rows remain `未测`; runtime-only contracts cannot be marked passed from source tests.

### P2

- All ten child `task.json` files remain `in_progress`; their PRD checkboxes are unchecked.
- Every child `implement.jsonl` and `check.jsonl` still contains only the generated `_example` placeholder, so the task artifacts do not yet record implementation/check evidence.
- Some automatic gates intentionally assert source/bytecode shape rather than a live NeoForge dispatch; corresponding Mixin/server/GPU/manual rows must remain explicit release gates.
