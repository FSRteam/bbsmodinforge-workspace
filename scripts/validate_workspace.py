from __future__ import annotations

import json
import subprocess
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "workspace-manifest.json"
REQUIRED_PATHS = (
    ROOT / "AGENTS.md",
    ROOT / ".trellis" / "workflow.md",
    ROOT / ".trellis" / "config.yaml",
    ROOT / ".agents" / "skills",
    ROOT / ".codex" / "hooks.json",
)
FORBIDDEN_TRACKED_PARTS = (
    ".trellis/.developer",
    ".trellis/.runtime/",
    ".trellis/.backup-",
    "dual-client-runtime/",
    "__pycache__/",
    ".pyc",
    ".claude/settings.local.json",
    ".claude/scheduled_tasks.lock",
    ".codex/model-catalog",
)


def fail(message: str) -> None:
    raise SystemExit(message)


def validate_manifest() -> int:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))

    if data.get("version") != 1:
        fail("workspace-manifest.json must use version 1")

    repositories = data.get("repositories")

    if not isinstance(repositories, list) or not repositories:
        fail("workspace-manifest.json must declare at least one repository")

    seen_paths: set[str] = set()

    for index, repository in enumerate(repositories):
        if not isinstance(repository, dict):
            fail(f"repository {index} must be an object")

        path = repository.get("path")
        url = repository.get("url")
        branch = repository.get("defaultBranch")
        required = repository.get("required")

        if not isinstance(path, str) or not path:
            fail(f"repository {index} has an invalid path")

        normalized = PurePosixPath(path.replace("\\", "/"))

        if normalized.is_absolute() or ".." in normalized.parts or str(normalized) == ".":
            fail(f"repository path is unsafe: {path}")

        normalized_path = normalized.as_posix()

        if normalized_path in seen_paths:
            fail(f"repository path is duplicated: {normalized_path}")

        seen_paths.add(normalized_path)

        if not isinstance(url, str) or not url.startswith("https://github.com/") or not url.endswith(".git"):
            fail(f"repository {normalized_path} must use a GitHub HTTPS clone URL")

        if not isinstance(branch, str) or not branch:
            fail(f"repository {normalized_path} has an invalid defaultBranch")

        if not isinstance(required, bool):
            fail(f"repository {normalized_path} has an invalid required flag")

    return len(repositories)


def validate_required_paths() -> None:
    missing = [str(path.relative_to(ROOT)) for path in REQUIRED_PATHS if not path.exists()]

    if missing:
        fail("required shared paths are missing: " + ", ".join(missing))


def validate_tracked_files() -> int:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    tracked = [path for path in result.stdout.decode("utf-8").split("\0") if path]

    for path in tracked:
        normalized = path.replace("\\", "/")

        if any(part in normalized for part in FORBIDDEN_TRACKED_PARTS):
            fail(f"local-only file is tracked: {path}")

    return len(tracked)


def main() -> None:
    repository_count = validate_manifest()
    validate_required_paths()
    tracked_count = validate_tracked_files()
    print(f"workspace validation passed: {repository_count} repositories, {tracked_count} tracked files")


if __name__ == "__main__":
    main()
