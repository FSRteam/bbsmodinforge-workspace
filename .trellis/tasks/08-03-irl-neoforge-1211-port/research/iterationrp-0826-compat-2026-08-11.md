# IterationRP 0.8.26 compatibility and beam-strength verification

## Reproduction input

- User-provided pack: `bbs-irlights-addon/log/iterationRP Alpha 0.8.26 hotfix.zip`
- 0.8.25 regression pack:
  `.claude/worktrees/agent-a4902cde408b6105b/new/new/log/iterationRP Alpha 0.8.25.zip`
- The migrated, `port/1.21.1`, and latest `master` copies of
  `iterationrp.irlights` were byte-identical before this fix. The failure was
  therefore a new shaderpack compatibility change, not a missed upstream file.

## Root cause

The 0.8.26 pack changed this `shaders.properties` line:

```properties
sliders=PT_VOXEL_RESOLUTION \
```

to:

```properties
sliders=PT_VOXEL_RESOLUTION_X PT_VOXEL_RESOLUTION_Y \
```

The other patch anchors, including the surface and volumetric program anchors,
still matched. The patch was updated to accept either slider line.

Both 0.8.25 and 0.8.26 already declare:

```properties
iris.features.required=CUSTOM_IMAGES SSBO
```

The old substring replacement produced `SSBO SSBO` in an applied pack. That
redundant operation was removed; the final pack preserves the original feature
line exactly once.

## Beam-strength data path

The NeoForge port already carries the complete producer-to-consumer path:

```text
PointLightForm / SpotlightForm beam_strength
  -> form editor and renderer / LightCollector
  -> LightRegistry beam[]
  -> LightBuffer vlParams.z (96-byte, six-vec4 std430 record)
  -> patched IterationRP irlite_volumetric()
  -> light.colorIntensity.a * max(light.vlParams.z, 0.0)
  -> Volumetric_FS.glsl adds the result to color
```

The successfully applied 0.8.26 output contains both the `vlParams.z`
multiplication and the `Volumetric_FS.glsl` call site. Thus `beam_strength=0`
removes the IRLights volumetric contribution, while higher values scale it.

## Verification

- 0.8.25 dry-run: passed, 11 operations.
- 0.8.26 hotfix dry-run: passed, 11 operations.
- 0.8.26 real apply: passed; marker, settings, surface hook, volumetric hook,
  and generated `irlite_lights.glsl` are present under
  `temp/irlights-iterationrp-0.8.26-verification-final/`.
- The final applied properties contain one `SSBO` feature token and the X/Y
  voxel-resolution slider line followed by the IRLights sliders.
- `clean check build`: passed.
- Release JAR contains seven `.irlights` patches and two Mixin configurations,
  with no embedded BBS classes or Fabric metadata/classes.
- The IterationRP patch extracted from the release JAR is byte-identical to the
  source patch (`SHA-256
  87099935552927FAB40DE7CC67EE2DFCF6525E6FC376F360C0E8EEB4840AA572`) and
  passes the 0.8.26 dry-run.
- Release JAR SHA-256:
  `CFA229ACFD2B29872B7908E59173B9AAF84A903C6645B939BC75CAE5A29E970B`.
- `runClient -Pverify`: passed with
  `mixinTargets=11 forms=2 renderers=2 editors=2 extraForms=2 resources=12`.

## Remaining visual gate

Startup, patch application, CPU publication, SSBO packing, and shader consumer
presence are verified. A fixed-camera world comparison is still required for
pixel-level acceptance: keep all other values fixed and compare beam strength
`0`, `1`, and a clearly higher value such as `10` with IterationRP 0.8.26 active.
