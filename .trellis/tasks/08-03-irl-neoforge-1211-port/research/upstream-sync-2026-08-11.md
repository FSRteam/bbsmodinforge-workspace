# IRLights upstream sync (2026-08-11)

## Locked upstream state

- `quaIett/bbs-irlights-addon` `port/1.21.1` remains at
  `c76a73f0e209da12505d3e9bff56d8e5155f47e4`; GitHub has no newer commit on
  that branch.
- The repository default branch `master` is at
  `2e1b7b3aaf25885a408501b24ff068848259a660`.
- The previous 1.21.1 forward-port commit `395203bb1f391ce60ec3e47ed906f6d4d5ac4a7b`
  already imported `master` through `b8a8fdc060b41db7f2688df01b55b848398e081d`.
- `irl-core` `1.21.1` remains at
  `3624676e80833ac8f6c8beb961adea339d261ceb`; no core source update is needed.

The latest default-branch checkout is stored at
`temp/bbs-irlights-addon-master-latest-upstream`. The existing
`temp/bbs-irlights-addon-port-1.21.1-upstream` checkout remains on the clean
1.21.1 branch baseline.

## Newly imported addon changes

| Upstream commit | Behavior | NeoForge port mapping |
|---|---|---|
| `8bc936d` | ModelBlock and editor-preview lights follow transient Form animation states | Thread the frame transition into `LightCollector.walk`, and balance `applyStates`/`unapplyStates` with `finally` |
| `6893208` | Hide development UI by default and quiet normal cookie-cache logs | Gate `UIDebugSection` behind `-Dirlite.debug=true`; use DEBUG for cookie load/eviction |
| `2e1b7b3` | Wrap patch target/status text instead of truncating it | Replace the two `UILabel` instances with BBSFSR `UIText` and update them through `text(...)` |

## BBSFSR compatibility discovered during sync

The current BBSFSR `Transform` no longer exposes the legacy `rotate2` field.
It stores rotation as either Euler (`rotate`) or quaternion (`quat`) selected
by `rotationMode`. IRLights' model-block shadow hash now includes the active
mode and its matching rotation representation, instead of referencing the
removed field. This keeps the addon compilable against the current BBSFSR and
preserves invalidation for quaternion poses.

## Verification

- `compileJava compileClientJava`: passed after adapting the current BBSFSR
  rotation API.
- Clean-state `check build`: passed; the final JAR was rebuilt after Gradle
  removed prior outputs.
- `runClient -Pverify`: passed with
  `mixinTargets=11 forms=2 renderers=2 editors=2 extraForms=2 resources=12`.
- Final JAR inspection: 7 `.irlights` patches, 2 Mixin configs, no embedded
  `mchorse/bbs_mod` classes, and no Fabric metadata/classes.
- Bytecode inspection confirms `Form.applyStates`/`unapplyStates`, `UIText`,
  cookie `Logger.debug`, and `Transform.rotationMode`/`quat` references are in
  the release artifact; the old `rotate2` field reference is absent.
