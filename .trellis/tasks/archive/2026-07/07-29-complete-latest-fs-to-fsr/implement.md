# Implementation plan: complete latest FS to FSR migration

## 1. Boundary and source update

- [x] Record root, FS, and FSR worktree baselines.
- [x] Fetch FS branches and tags; verify exact tips and ancestry.
- [x] Create/switch to a dated local FS sync branch without rewriting old master.
- [x] Produce the final divergence-aware upstream commit disposition matrix.

## 2. Parallel migration

- [x] Port the upstream UI/model delta.
- [x] Port historical UI/pose recording gaps.
- [x] Correct render/runtime state ownership and add focused tests.
- [x] Close build/resource/test assembly gaps that are applicable to NeoForge.
- [x] Independently review shared files and policy exclusions.

Agents own disjoint clusters and must not revert or overwrite existing changes.
Shared files are integrated by the primary agent after reviewing the current diff.

## 3. Validation

- [x] Run focused tests for migrated crash, recording, render, and build contracts.
- [x] Run `compileJava compileClientJava`.
- [x] Run `check` and `build` serially. Full `check` reaches an unchanged baseline
      failure in `testNetworkSecurity`; `check -x testNetworkSecurity` and
      `build -x testNetworkSecurity` pass in a clean worktree at `4143499b`.
- [x] Run `git diff --check`, inspect staged paths/hunks, and verify particle/version
      invariants.
- [x] Record manual Minecraft/Iris/FFmpeg checks as pending unless actually run.

## 4. Commit, baseline, and tag

- [x] Stage only migration-owned hunks and create one migration terminal commit.
- [x] Update the Trellis migration baseline and task evidence with exact SHAs.
- [x] Create annotated local tag `nightly-20260729-131928-r8-a1-4143499`
      at the terminal commit.
- [x] Confirm unrelated target worktree changes remain present and uncommitted.

Manual checks still pending: Iris relative replay, Mob Form custom texture/picking/blending,
translucent ordering, uniform-scale dragging, repeated S target cycling, orphan texture
dragging, stale keyframe panel, and FFmpeg-backed media verification.
