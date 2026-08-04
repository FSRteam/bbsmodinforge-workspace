# Design: sync FS 5848363c to FSR

## Baselines and ownership

The source behavior boundary is FS `116ed397..5848363c`. The 1.20.1 and 1.21.1
tips are supporting references, not code to merge mechanically. The target boundary
is `new@0544eada`; implementation happens in a dedicated worktree on
`codex/sync-fs-5848363-to-fsr`.

FS `master` defines behavior, FS `1.21.1` supplies Yarn-side 1.21.1 API evidence,
and the target supplies NeoForge/Mojmap integration and FSR-specific behavior.
Changes are ported by feature cluster so FSR-only code remains authoritative at
each shared-file boundary.

## Migration clusters

1. Standalone correctness: film context reset, ordered value insertion, replay
   ground and sprint state.
2. Transform system: quaternion channels, `TransformSpace`, drag strategies,
   numeric input, gizmo routing and persistence.
3. IK and weld runtime: DLS tree, multi-effector solving, per-bone DoF, classic
   two-bone option, stretch/orient integration, weld parent share/twist/diagnostics.
4. Editor selection and navigation: searchable bone tree/picker, stencil eyedropper,
   orbit navigation sphere, orthographic preview, tracker flight/orbit authoring.
5. Film and UI lifecycle: frozen last frame, keyframe hover preview, section state,
   single Dock implementation and editor-edge docking.
6. Render cache and integration: `FormFrameCache` without replacing
   `FormTranslucentQueue`, plus assets/localization and focused regression coverage.

## Compatibility boundaries

- `FormTranslucentQueue` remains the rendering owner. Upstream transparency changes
  may be used only when they strengthen correctness without removing that queue.
- Particle changes are limited to the vanilla replay state required by
  `d6a8c847`; FS particle engine paths are excluded.
- Existing FSR theme behavior controls disabled icon appearance.
- Existing replay collection and procedural panel ownership from `0544eada` remains
  the base when applying bone picker and IK UI changes.
- Serialized data additions need default values and backward-compatible reads.
- Fabric/Yarn classes are translated at the integration edge; no Fabric API types
  enter the NeoForge source tree.

## Validation and rollback

Compile after each cluster and keep commits coherent so a failing cluster can be
reverted without discarding earlier verified fixes. Run focused source/logic tests,
then full compilation, `check`, and `build`. Compare version, particle, translucency,
and initial-worktree invariants before the terminal commit is recorded.

Rollback is `git revert` of the affected cluster commit. The original `new` working
directory is never reset or stashed, and the dedicated worktree can be removed only
after its terminal commit and Trellis evidence are recorded.
