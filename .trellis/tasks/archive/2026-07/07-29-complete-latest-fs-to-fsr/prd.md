# 完成最新 FS 到 FSR 迁移

## Goal

Update the local FS reference to the latest fetched upstream state, migrate the
confirmed FS-to-FSR omissions into `new/`, verify the NeoForge adaptation, and
publish a new local FSR nightly tag without overwriting unrelated worktree edits.

## Confirmed Facts

- The historical marker `f76cce04` and current FS remote history diverge at
  `1ab0a887`; the next audit range must use the divergence-aware coverage matrix.
- `new/` contains unrelated uncommitted theme, plugin, render, and test work that
  must remain intact.
- The local FS checkout is clean but its `master@9b28a8a4` is not the current
  remote tip. Preserve that branch and use a fresh local sync branch.
- Legacy FSR particles remain an explicit policy boundary.

## Requirements

- Fetch the current FS remote branches and tags, then place the local FS working
  tree on a recoverable branch based on the verified current upstream tip.
- Port every confirmed applicable upstream behavior omission, adapting Fabric/Yarn
  APIs to NeoForge 1.21.1/Mojmap and preserving stronger FSR contracts.
- Fix confirmed migration regressions in pose recording and render-state handling
  when they are separable from unrelated worktree changes.
- Add focused regression coverage for crash and lifecycle fixes.
- Keep version identity and legacy particle policy unchanged unless the repository
  itself contains a newer explicit release decision.
- Commit only migration-owned hunks in `new/`; preserve all unrelated staged,
  unstaged, and untracked files.
- Create a new annotated local nightly tag using the repository's existing naming
  convention after validation succeeds.

## Acceptance Criteria

- [ ] Local FS remote refs and tags are fetched and the working checkout points to
      the verified latest upstream tip without deleting the old local branch.
- [ ] Every new upstream behavior commit has a recorded disposition: migrated,
      already equivalent, intentionally excluded, or blocked with evidence.
- [ ] Confirmed UI crash fixes, model-editor delta, pose recording gaps, and safe
      render-state restoration are implemented or explicitly blocked by overlap.
- [ ] `compileJava`, `compileClientJava`, focused tests, `check`, and `build` pass.
- [ ] Iris/Sodium and remaining interactive-only checks are reported accurately;
      no build-only result is called a passed manual smoke.
- [ ] Migration baseline and task evidence record the new FS tips, FSR terminal
      commit, policy exclusions, and pending manual checks.
- [ ] A new annotated local nightly tag points at the migration terminal commit.
- [ ] Existing unrelated `new/` worktree changes remain present and uncommitted.

## Out of Scope

- Reintroducing the archived new particle system.
- Publishing or force-moving a remote branch or tag.
- Folding unrelated theme/plugin work into the migration commit.
- Claiming completion of interactive Minecraft/Iris/FFmpeg smoke without evidence.
