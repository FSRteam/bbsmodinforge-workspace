# Review new uncommitted files

## Goal

Review the uncommitted changes inside the `new` repository only, keep generated/local files out of version control, and split the resulting repository changes into clear commits.

## Requirements

- Scope is limited to the `new` repository.
- Inspect uncommitted files before committing anything.
- Do not commit local assistant permissions, run directories, logs, or generated runtime outputs.
- If repository metadata needs to be adjusted so the working tree is clean, commit those adjustments separately by concern.

## Acceptance Criteria

- [x] `git -C new status --short` shows no uncommitted changes after completion.
- [x] Any commits created are separated by logical concern.
- [x] The final report summarizes what was reviewed and committed.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
