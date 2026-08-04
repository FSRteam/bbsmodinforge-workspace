# S9 fluid port baseline

## Source inventory

| Owner | Source path | Lines | Responsibility |
|---|---|---:|---|
| FluidForm | `src/main/.../forms/FluidForm.java` | 84 | 19 values and mode enum |
| FluidFormRenderer | `src/client/.../forms/renderers/FluidFormRenderer.java` | 784 | UI/world rendering, time, simulation stepping, interaction bridge |
| FluidController | `src/client/.../simulation/FluidController.java` | 348 | world/Film/model samples and forces |
| FluidSimulation | `src/client/.../simulation/FluidSimulation.java` | 61 | two-buffer height wave |
| UIFluidForm | `src/client/.../ui/forms/UIFluidForm.java` | 21 | editor owner |
| UIFluidFormPanel | `src/client/.../ui/forms/UIFluidFormPanel.java` | 224 | values, modes, presets and visibility |
| FluidUIKeys | `src/client/.../ui/forms/FluidUIKeys.java` | 42 | 30 translation keys |

The paths above are relative to `bbs_FSloveCML_1.21.1/`. The target addon currently has no fluid class or fluid translation key.

## Serialized contract

Modes, in ordinal order:

```text
0 FULL_OCEAN
1 WATER_DROP (default)
2 PROCEDURAL
```

Values:

```text
mode
flowSpeed turbulence color opacity texture physicsSensitivity
subdivisions smoothShading debug
sizeX sizeY sizeZ fillBlock waveAmplitude waveFrequency
surfaceTension dropSize viscosity
```

Defaults include `sizeX/Y/Z=10/1/10`, `subdivisions=1`, water texture, alpha color plus independent `opacity=0.8`, and realistic model interaction controlled by the existing S2 setting.

## Verified source defects and risks

### No-op public values

A repository-wide search finds `fillBlock` only in `FluidForm` and `UIFluidFormPanel`; `FluidFormRenderer` never reads it. `sizeY` has the same effective problem. The UI enables fillBlock only in FULL_OCEAN, proving the intended boundary is an ocean volume shell rather than a drop/procedural control.

### Unbounded render work

FULL_OCEAN computes simulation axes as `clamp((int)(size * 8), 16, 512)` and render axes as `simulation axis * subdivisions`. At the legal maximum subdivision 8, the renderer loops a `4096 x 4096` grid and emits:

```text
(4096 - 1)^2 * 6 = 100,614,150 vertices per form per frame
```

This excludes height/normal sampling cost and duplicate render passes. Procedural/drop topologies use `16 * subdivisions`, so their source maximum is only 98,304 vertices; FULL_OCEAN is the critical path.

### Render-owned time and mutation

The old renderer uses `System.currentTimeMillis()` in all three modes, calls `simulation.update()` from render methods, and uses `Math.random()` for ocean turbulence. The number/timing of render passes therefore changes simulation state. Pause, UI redraw, Iris passes, motion blur and export can all diverge.

### Interaction scan/allocation

Each `tick()` copies all Film controllers, optionally appends the editor controller, scans every controller/entity to find the owner, then `FluidController` scans nearby world entities and every replay entity again. Detailed Cubic sampling creates many `Vector3f`, arrays and `FluidSample` objects. There is no candidate/sample budget.

BOBJ detailed sampling is intentionally unsupported because target/source BOBJ exposes VAO-side mesh rather than CPU mesh data. The source already falls back to root interaction; S9 preserves that fallback.

### Debug retention

`lastDebugSamples.clear()` only runs while debug is enabled. Turning debug off gates drawing but can retain the previous list until debug is enabled again or the renderer is discarded.

### Preset refresh

The source presets mutate form values and then call `startEdit(form)`. Current FSR Form panels also write the live value tree directly and expose no general preset undo transaction, so S9 should keep one callback + full widget refresh rather than inventing an isolated undo mechanism.

## Target API findings

| Old/Yarn | Target/Mojmap 1.21.1 |
|---|---|
| `MinecraftClient` | `Minecraft` |
| `MatrixStack` | `PoseStack` |
| `ShaderProgram` | `ShaderInstance` |
| `VertexFormats` | `DefaultVertexFormat` |
| `Tessellator` | `Tesselator` |
| `VertexFormat.DrawMode` | `VertexFormat.Mode` |
| `BufferRenderer` | `BufferUploader` |
| `Vec3d` | `Vec3` |
| `Box` | `AABB` |
| `World` | `Level` |

Current FSR adds contracts the old renderer did not have:

- `ITickable.tick(IEntity)` is the state-advance hook.
- `FormRenderingContext` carries render type/space, semantic world stack, simulation owner, world interaction permissions and Film timeline tick/playing state.
- `FormRenderer.release()` owns renderer runtime cleanup.
- `FormUtilsClient.getRegisteredFactory` and `UIFormEditor.getRegisteredPanel` allow conflict-aware client registration.
- `FormCategories.addExtraForm` is the public category API.
- `Films.controllers` remains private, but S2/S7 already installed `FilmsControllerAccessor`; no new accessor is needed.

## Registration contract

- Common: `@Subscribe RegisterFormsEvent`, register `Link.bbs("fluid")`.
- Client construction: `FormUtilsClient.register` and `UIFormEditor.register`.
- Client started: `BBSModClient.getFormCategories().addExtraForm(new FluidForm())` after resources/category setup.
- Never port `ExtraFormSectionMixin`.

## Intentional target deviations

1. Make `fillBlock/sizeY` functional with ocean sides + bottom.
2. Replace wall clock/random/render stepping with deterministic logical-tick ownership.
3. Add hard simulation/render/candidate/sample budgets while preserving full geometry extent.
4. Clear debug and runtime state on disable/release.
5. Keep BOBJ root fallback and do not add a core API.

These deviations preserve the serialized contract and intended controls while removing source behavior that is either observably broken or unsafe on the target runtime.
