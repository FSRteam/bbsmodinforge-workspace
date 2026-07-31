# IRLite connector-fix8 beam matrix validation

Date: 2026-07-29

## Conclusion

IRLite `beam_strength` changes final pixels for both point lights and spotlights in the
isolated NeoForge + Connector + Iris runtime. The current evidence rejects Connector
property loss and Fabric/NeoForge Iris sampler differences as the cause of the reported
no-effect behavior. A native NeoForge IRLite migration is not justified by this defect.

## Runtime inputs

- NeoForge `21.1.235`
- Connector `2.0.0-beta.15+1.21.1`
- Forgified Fabric API `0.116.14+2.3.0+1.21.1`
- BBS FSR `0.0.10`
- Iris NeoForge `1.8.12`
- Photon generated IRLights shaderpack
- IRLite `1.1.2-connector-fix8+mc1.21.1`
- IRL core `1.1.3-connector-beamdiag3`
- Java `21.0.11`

Artifact hashes:

- outer fix8 JAR: `4E1AF04E286C1F8712B3505AB1C1A17C9958FFFBC18388BBD7FB59BA4384239E`
- Connector mapped outer JAR: `154E11B1C1085B345593FB621597CC94FC4C3DAD5C743CD3425B91C31108C232`
- Connector mapped core JAR: `4E0A6642E37865BB9C3AA47E23382DA9A7AC0E2A0E8119469F0349D7EA9C1D75`

## Fixture correction

The first spotlight screenshots were not a valid pixel test. The fixed camera was at
`(-7.5, -58.38, 5.5)` with yaw `180` degrees, while the spotlight was behind the camera
and its local `+Z` cone pointed farther away. The data publication logs passed, but the
cone never crossed the captured framebuffer.

`FilmMatrixGenerator` now places spotlight variants one block behind the camera, at
`(-7.5, -58.38, 6.5)`, and writes yaw/head yaw/body yaw `180` degrees. The generated
films were saved and loaded again before launch. `RuntimeFixture` also guards
`Minecraft.stop()` so the automatic exit request is emitted once.

## GPU and program evidence

For both light types and every value, the runtime logged this complete chain:

```text
form-renderer -> registry -> packed(index=0) -> texture-write
-> texture-readback(matches=true) -> sampler-bind(matches=true)
-> program-execute(name=composite)
```

The encoded texels were:

| `beam_strength` | encoded R32F texel | readback |
|---:|---:|---|
| 0 | 1 | match |
| 1 | 2 | match |
| 10 | 11 | match |

The spotlight-only rerun ended with exactly one fixture completion marker, one Minecraft
`Stopping!` marker, normal world saves, and Gradle `BUILD SUCCESSFUL in 2m 57s`. It had
no Mixin application failure and no texture mismatch.

Retained logs:

- `runtime-fix8-point-spot-matrix-debug-2026-07-29.log`: the original six-film data-path run
- `runtime-fix8-point-spot-matrix-latest-2026-07-29.log`
- `runtime-fix8-spot-facing-matrix-debug-2026-07-29.log`: corrected spotlight visual run
- `runtime-fix8-spot-facing-matrix-latest-2026-07-29.log`

## Fixed-region pixel matrix

Screenshots are under
`temp/irlite-connector-runtime/runs/client/screenshots/`. The same
`x=220, y=80, width=300, height=270` region was measured for every image. It avoids the
tutorial box and player hand while retaining the central beam.

| light | strength 0 mean | strength 1 mean | strength 10 mean | 0→1 mean abs delta | 0→10 mean abs delta |
|---|---:|---:|---:|---:|---:|
| point | 80.215 | 180.930 | 238.275 | 103.755 | 158.632 |
| spot | 95.890 | 149.923 | 220.699 | 57.862 | 125.982 |

For the spotlight, 82.704% of ROI pixels changed by at least 16 luminance levels from
0 to 1; 89.047% did so from 0 to 10. The progression is visible directly: zero retains
the ordinary night scene, one adds strong volumetric fog, and ten approaches white
saturation.

Spotlight screenshot hashes:

- 0: `CFE0A447D2CFD059C013A0636803DE470698B9294DFDF76FC738E3AE89DE7598`
- 1: `3B9488C366CC90C0005CC4B92B96FCF75B67433E52AD5B6AE144EAC6BCBB87C8`
- 10: `8A56F9CD1C2F0AD9BC3684A5A168C799B7A19EA4E3E1658CE8FE40EF4579070A`

## Replay interpolation matrix

Two additional Films keyframe `beam_strength` at `0/40/120/220/260` ticks with values
`0/0/1/10/10`. Both generated files were saved and loaded again before launch. The
fixture captured the same playing replay near start, middle, and end:

| light | capture beam | packed indexes | ROI mean luminance |
|---|---|---|---|
| point | `0.0 -> 0.968500 -> 10.0` | only `0` across 456 samples | `59.662 -> 170.680 -> 241.359` |
| spot | `0.0 -> 0.993751 -> 10.0` | only `0` across 534 samples | `83.593 -> 143.766 -> 228.885` |

Every changing value reached texture readback with `matches=true`; there were no mismatch
or Mixin-failure markers. The spot middle capture changed 81.374% of ROI pixels by at least
16 luminance levels from start, and the end capture changed 93.844%. The client emitted one
completion marker, saved the world, and Gradle reported `BUILD SUCCESSFUL in 2m 33s`.

Retained interpolation logs:

- `runtime-fix8-beam-interpolation-debug-2026-07-29.log`
- `runtime-fix8-beam-interpolation-latest-2026-07-29.log`

## Remaining gates

This closes the point/spot `0/1/10` GPU-consumer, fixed-camera pixel, and replay interpolation
gates. It does not replace the manual editor/localization/model-block/shadow-refresh matrix,
so the full task remains open.
