# Team Shared Trellis Workspace Design

## Repository Boundary

The workspace root becomes a small Git repository whose tracked surface is an
explicit allowlist. Nested code repositories, local archives, dependency trees,
and build outputs remain outside its object graph.

The shared control plane contains `.trellis/`, `.agents/skills/`, supported AI
platform adapters, `AGENTS.md`, documentation, a repository manifest, bootstrap
automation, and validation automation. Existing nested repositories are never
converted to submodules because they include optional references and independent
work-in-progress checkouts.

## Local And Shared State

Shared state includes workflow definitions, task artifacts, specifications,
Trellis scripts, archived work, and developer-specific journal directories.
Machine state remains local through nested ignore rules: developer identity,
session pointers, update backups, caches, local permissions, and model catalogs.

Each teammate initializes `.trellis/.developer` locally. Journals remain separated
under `.trellis/workspace/<developer>/`, while shared task and spec changes are
reviewed through Pull Requests.

## Bootstrap Contract

`workspace-manifest.json` is the single repository inventory. The PowerShell
bootstrap script validates that every destination stays under the workspace root,
clones missing repositories, and leaves existing repositories untouched by
default. `-UpdateExisting` only performs `pull --ff-only` when the checkout is
clean and on its declared default branch.

## Validation And Governance

`scripts/validate_workspace.py` validates manifest shape, path safety, unique
destinations, required shared files, and the absence of local-only tracked paths.
CI also byte-compiles shared Python entry points.

The remote default branch is protected after the bootstrap PR exists, requiring
the `Validate workspace` check and human review. The initial GitHub-generated
README is the only seed commit made outside a Pull Request.

## Rollback

Deleting the root `.git` directory returns the local directory to its previous
polyrepo layout without touching nested repositories. The private remote can be
archived or deleted independently because it does not own code history.
