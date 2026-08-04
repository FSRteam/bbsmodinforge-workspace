# Review and split commits for new repository

## Goal

Review uncommitted changes in the new repository only, exclude unrelated workspace changes, and split commits by coherent change set.

## Requirements

- Review only uncommitted changes inside the `new` Git repository.
- Do not modify or commit uncommitted changes in sibling repositories (`old`, `bbs-premiere-export`) or workspace-level files.
- Identify coherent change sets from the `new` repository diff and create separate commits for each set.
- Exclude generated/runtime output unless it is clearly source-controlled project material.
- Report any review findings, skipped files, verification performed, and resulting commit hashes.

## Acceptance Criteria

- [x] `new` repository diff is reviewed before committing.
- [x] Commits are split by coherent change set rather than lumped together.
- [x] Untracked runtime/output directories are not accidentally committed.
- [x] Verification appropriate to the changed files is run or the reason for skipping is documented.
- [x] Final status clearly states remaining uncommitted files, if any.

## Notes

- This is a lightweight review-and-commit task, so PRD-only planning is sufficient.
