# Design: complete latest FS to FSR migration

## Baselines and source selection

Fetch `origin/master`, `origin/1.21.1`, `origin/1.20.1`, and tags in the local FS
checkout. Preserve the old local `master`; create a dated sync branch at the
current upstream behavior tip. Compare from the verified merge base rather than
assuming the historical marker remains an ancestor.

Use master commits for behavior intent, the 1.21.1 branch for Minecraft API shape,
and the current `new/` worktree for NeoForge/Mojmap integration. The FSR target is
the current branch plus unrelated uncommitted user work; migration ownership is
tracked at hunk level.

## Migration clusters

1. Upstream UI/model changes: model editor lifecycle and weld tools, selection
   banners, bevel policy, transformation defaults, texture and keyframe crash fixes.
2. Historical UI gaps: textbox cursor and Pose/PoseTransform recording semantics.
3. Render/runtime correctness: translucent queue ownership, framebuffer exception
   restoration, relative replay/Iris composition, and sound-capture fallback.
4. Build/resources: optional compatibility runtime coverage, executable test
   registration, and NeoForge metadata parity.

## Compatibility decisions

- Preserve the legacy particle mainline.
- Do not mechanically remove translucent sorting: reconcile the newer upstream
  rollback with FSR's prior explicit adoption and current render architecture.
- Preserve FSR theme/plugin changes even when they overlap shared files.
- Do not reset or stash the target worktree. Shared-file edits must be minimal and
  reviewed against the pre-existing diff.
- A migration commit is built from migration-owned hunks only. The tag points to
  that commit; unrelated working changes remain outside it.

## Validation and rollback

Run focused tests per cluster, then compile, check, and build serially. Inspect the
index before commit and compare the post-commit working tree with the recorded
pre-migration paths. The local FS sync is recoverable by switching back to its old
`master`; the FSR migration is recoverable by reverting the migration commit or
deleting the new local tag.
