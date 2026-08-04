# Implementation Plan

## Checklist

- [x] Inventory `docs/` and identify shader files, notes, logs, and screenshots.
- [x] Compare `old` and `new` render entry points and matrix setup.
- [x] Compare film replay rendering and form rendering call sites.
- [x] Research NeoForge 1.21.1 and Iris rendering matrix behavior.
- [x] Form a root-cause hypothesis with file/line evidence.
- [x] Apply the smallest source fix in `new/`.
- [x] Run `.\gradlew.bat compileClientJava` from `new/`.
- [x] Run additional targeted checks if the codebase exposes them.
- [ ] Report the fix and wait for user confirmation before any Git commit.

## Validation Commands

```powershell
cd D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new
.\gradlew.bat compileClientJava
git status --short
git diff --check
```

## Risky Files

- `new/src/client/java/mchorse/bbs_mod/client/BBSRendering.java`
- `new/src/client/java/mchorse/bbs_mod/client/renderer/MorphRenderer.java`
- `new/src/client/java/mchorse/bbs_mod/film/BaseFilmController.java`
- `new/src/client/java/mchorse/bbs_mod/film/FilmControllerContext.java`
- `new/src/client/java/mchorse/bbs_mod/forms/renderers/FormRenderingContext.java`
- `new/src/client/java/mchorse/bbs_mod/particles/emitter/ParticleEmitter.java`

## Commit Gate

Do not run `git commit` until the user confirms after reviewing the validation
result. If confirmed, stage and commit only `new/` repository changes.
