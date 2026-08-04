# Implement: Structure plugin saved structure lookup

## Steps

- [x] Create a standalone `bbs-structure-addon` Gradle project modeled after `bbs-premiere-export`.
- [x] Move Structure form, renderer, editor UI, liked-list, and world-structure source-pack code into the standalone plugin namespace.
- [x] Register the plugin through `BBSMod.registerAddon`, then register common Structure form/source-pack/category hooks through BBS addon events.
- [x] Register client renderer and editor panel from the plugin client initializer.
- [x] Keep only reusable external-addon extension points in `new/` and remove accidental embedded Structure implementation from `new/`.
- [x] Fix `WorldStructuresSourcePack` so listing `world:structures` includes saved namespaced structures recursively.
- [x] Verify targeted diffs and run `new/gradlew.bat build` plus `bbs-structure-addon/gradlew.bat build` when possible.

## Validation

- `git -C new diff -- <structure-related files>`
- `cd new && .\gradlew.bat build`
- `cd bbs-structure-addon && .\gradlew.bat build`

Completed:

- `git -C new diff --check`
- `cd new && .\gradlew.bat build`
- `cd bbs-structure-addon && .\gradlew.bat build`

## Risk Notes

- The workspace has unrelated uncommitted changes in `new/`; avoid broad resets or formatting churn.
- Build failures may come from unrelated local edits and must be reported separately from structure changes.
