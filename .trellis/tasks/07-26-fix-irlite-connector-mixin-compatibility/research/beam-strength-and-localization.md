# Research: IRLite beam strength and complete Simplified Chinese localization

- Query: Identify the most likely boundary behind IRLite beam-strength changes not appearing under Connector, and define the complete localization work including replay-editor keyframe tracks.
- Scope: internal code, local runtime fixture, local shader patches, and project Trellis specs
- Date: 2026-07-27

## Findings

### Evidence-based conclusion

The Java-side `beam_strength` value path is complete. The current evidence does not show Connector dropping, renaming, or freezing this property. The strongest supported failure boundary is the shaderpack deployment state: IRLite writes volumetric support into a newly patched shaderpack, but Connector only remaps Java/mod classes and does not turn an arbitrary Iris shaderpack into an IRLite-aware one.

The retained Connector fixture confirms startup and Mixin compatibility only. Its Iris configuration has no selected shaderpack (`temp/irlite-connector-runtime/runs/client/config/iris.properties:9` is `shaderPack=`), so that fixture cannot render a beam or prove that `beam_strength` affects pixels.

Consequently, the first repair should be operational and UI-facing: regenerate a supported shaderpack from a pristine input with the exact `.irlights` patch bundled by the current IRLite build, then select the generated pack in Iris. A Java/core renderer change is not justified unless a selected, freshly generated pack still shows identical output for materially different `beam_strength` values.

### Old generated packs and patch-marker behavior

The patcher deliberately does not upgrade an already generated pack in place:

- A successful current patch writes `irlite_patched.txt` at the output root (`temp/bbs-irlights-addon-1.21.1/upstream-irl-core-git/src/main/java/org/qualet/irl/patcher/IrlPatchApplier.java:238-245`).
- `PatchEngine` stops immediately with `ALREADY_PATCHED` when that file is present (`.../PatchEngine.java:69-73`).
- The marker records only the patch marker/name, target/declared pack version, and application date. It does not record a patch-content hash or the IRLite JAR/build hash, so its presence cannot prove that an output contains the current GLSL payload.
- Older generated packs may predate marker creation. The checked-in `run/shaderpacks/photon_v1.3b_IRLights/` has injected shader content but no `irlite_patched.txt`. This still cannot be upgraded as a source: every bundled patch has at least one `+file` operation (for example `patches/photon.irlights:8`), and `PatchEngine` reports `ADD_FILE_EXISTS` when that target already exists (`PatchEngine.java:86-92`). The UI intentionally maps `ALREADY_PATCHED` and `ADD_FILE_EXISTS` to the same “pick the original clean pack” instruction (`UIPatcherSection.java:289-294`).
- When the selected source is pristine and validation succeeds, `IrlPatchApplier.apply` may replace an existing output directory: it deletes the old output, copies the clean source, applies all current operations, and writes a fresh marker (`IrlPatchApplier.java:80-103`). Therefore the correct update path is to select the original clean pack and regenerate; deleting the marker from an old patched source is not a valid update method.

This behavior makes a stale generated shaderpack especially plausible after updating the IRLite JAR: the old pack can remain selected and continue compiling, while its injected GLSL does not reflect the current patch contents. The patcher should expose this constraint clearly in localized UI text; a future hardening improvement would include a patch-content/build identifier in the marker and surface “stale output” separately from “already patched,” but that is not required to restore the current beam behavior.

### Beam-strength data path is intact

- `PointLightForm` declares serialized `ValueFloat("beam_strength", 1F, 0F, 50F)` at `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1/src/main/java/qualet/irlite/forms/PointLightForm.java:17`.
- `SpotlightForm` declares the same property and range at `.../forms/SpotlightForm.java:20`.
- Point and spotlight panels write directly to the form values at `.../UIPointLightFormPanel.java:33` and `.../UISpotlightFormPanel.java:45`.
- Live form renderers forward the current value to `LightRegistry` at `.../PointLightFormRenderer.java:47` and `.../SpotlightFormRenderer.java:99`.
- The model-block/scanner path also forwards the value at `.../LightCollector.java:444` and `:473`; the failure is therefore not limited to one BBS form-render path.
- `LightRegistry.registerPoint` and `registerSpot` retain it in `beam[i]` at `temp/bbs-irlights-addon-1.21.1/upstream-irl-core-git/src/main/java/org/qualet/irl/light/LightRegistry.java:128` and `:170`.
- `LightRegistry.flush` forwards that slot to the GPU buffer at `LightRegistry.java:523` and `:527`.
- `LightBuffer` documents `vlParams.z` as `beamStrength` at `temp/bbs-irlights-addon-1.21.1/upstream-irl-core-git/src/main/java/org/qualet/irl/light/LightBuffer.java:20` and writes the float for point/spot lights at `:97` and `:114`.
- Every bundled patch reads `max(light.vlParams.z, 0.0)` in its volumetric calculation. Representative locations are `patches/bliss.irlights:1631`, `bsl.irlights:1631`, `complementaryreimagined.irlights:1568`, `iterationrp.irlights:1500`, `photon.irlights:1504`, `rethinkingvoxels.irlights:1580`, and `solas.irlights:1645`.
- The patcher writes a separate output shaderpack and explicitly tells the user to select it in Iris (`.../UIPatcherSection.java:257-260`, `:282-284`). This is the actual Java-to-GLSL deployment boundary.

### Required beam verification before code changes

1. Start from a pristine supported shaderpack whose version matches one bundled patch. Do not patch an already patched or stale output.
2. Run Validate, then Patch, and retain the exact output pack name.
3. Select that generated output in Iris and confirm Iris successfully compiles it.
4. At a fixed camera and fixed exposure, capture `beam_strength = 0`, `1`, and `10` for the same light. Zero must remove the per-light volumetric contribution, while ten must be visibly stronger than one.
5. In the BBS replay editor, keyframe `beam_strength` from `0` to `10`; scrub and play across the interval to verify the form value and visible beam interpolate together.
6. Repeat once for a live Form and once for a model-block/scanner light, since those are separate registration paths.
7. If the images remain identical, instrument or read back the light SSBO and verify `vlParams.z` for the affected light. Only a changing Java value plus a fixed GPU value justifies a `LightRegistry`/`LightBuffer` fix; a changing GPU value plus fixed pixels moves the defect into the selected generated shaderpack/pass.

### Localization is structurally missing

- The IRLite source/JAR has no `assets/irlite/strings/en_us.json` or `zh_cn.json`; its only namespaced resource is `assets/irlite/icon.png`.
- `L10nMixin` injects English-only defaults into every `L10n` instance (`.../mixin/client/L10nMixin.java:16-127`). It cannot provide Chinese values and makes localization depend on a BBS implementation Mixin rather than the addon resource contract.
- Form panels, preset/debug sections, patcher controls, mode labels, and status/error messages use `IKey.constant(...)`; representative occurrences are `UIPointLightFormPanel.java:38-64`, `UISpotlightFormPanel.java:50-92`, `UIPresetSection.java:30-79`, `UIDebugSection.java:18-38`, `UIPatcherSection.java:43-344`, and `IrlightsAddon.java:61-85`.
- Replay/keyframe sheets are a separate gap. `UIKeyframeSheet.getTrackTitle` asks the Form for a String and returns `IKey.constant(title)` for non-sound forms (`new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeSheet.java:65-84`). Base `Form.getTrackName` normally returns the raw property path (`new/src/main/java/mchorse/bbs_mod/forms/forms/Form.java:357-374`). `UIReplaysEditorColorMixin` adds colors, including `beam_strength`, but no translated title (`.../UIReplaysEditorColorMixin.java:30`).

### Correct file-backed L10n registration under Connector

The Connector/Fabric addon bridge is available before BBS posts the source-pack event: it imports `bbs-addon` entrypoints and bridges them to the internal bus at `new/src/main/java/mchorse/bbs_mod/BBSMod.java:528-537`, then posts `RegisterSourcePacksEvent` at `:547-554`.

The implementation should therefore add public, one-argument `@Subscribe` handlers to the bridged IRLite addon (or an equivalently bridged client addon):

```java
event.provider.register(new InternalAssetsSourcePack(
    "irlite", "irlite", "assets/irlite", IrlightsAddon.class));
```

and for L10n:

```java
event.l10n.registerOne(
    language -> new Link("irlite", "strings/" + language + ".json"));
event.l10n.reload();
```

The four-argument pack constructor is required because it resolves the owning addon JAR; its contract is visible at `new/src/main/java/mchorse/bbs_mod/resources/packs/InternalAssetsSourcePack.java:35-50`. The immediate reload is also required: BBS creates and reloads its own L10n before posting `RegisterL10nEvent` (`new/src/client/java/mchorse/bbs_mod/BBSModClient.java:396-400`).

After file-backed loading is verified, remove `L10nMixin` from `src/client/resources/irlite.client.mixins.json:10` and delete the obsolete source file. Keeping both paths would leave English defaults as a hidden second source of truth.

### Required JSON files and key inventory

Add:

- `src/main/resources/assets/irlite/strings/en_us.json` - English source/fallback strings.
- `src/main/resources/assets/irlite/strings/zh_cn.json` - Simplified Chinese translations with exactly the same keys.

Preserve all existing settings keys from `L10nMixin`:

- `irlights.config.title`.
- Category `title` and `tooltip` keys for `presets`, `volumetric`, `shadows`, `outline`, and `patcher`.
- Preset settings: `vl_intensity`, `max_shader_lights`, `show_guides`.
- Volumetric settings: `vl_steps`, `vl_max_dist`, `vl_shadows_live`, `vl_shadow_stride`, `vl_tip_boost`, `vl_tip_radius`, `vl_noise_live`, `vl_noise_amount`, `vl_noise_scale`, `vl_noise_speed`, `vl_noise_morph`, `vl_noise_stride`, `vl_dither_temporal`.
- Shadow settings: `shadow_quality`, `shadow_blocks`, `shadows_live`, `shadow_softness`.
- Outline settings: `outline`, `outline_target`, `outline_strength`, `outline_pixel_size`, `outline_fresnel_power`, `outline_back`, `outline_front`, `outline_front_strength`, `outline_glow`, `outline_glow_strength`.
- Every settings value above also needs its existing `-comment` key.

Add a coherent addon UI namespace, for example `irlite.ui.*`, covering:

- Form/panel names: Point light and Spotlight.
- Field/property ids: `color`, `intensity`, `radius`, `range`, `inner_radius`, `beam_strength`, `anisotropy`, `vl_density`, `bulb_size`, `entities_only`, `blocks_only`, `shadows`, `cookie`, `cookie_rotation`, `cookie_scale`, `cookie_invert`.
- Presets: section title; Quality; Beam style; Performance, Balanced, Quality, Ultra, Custom; Clean, Dusty, Smoky; and both long tooltips. The source arrays are at `IrlitePresets.java:20-21`.
- Mode labels: LOW, MEDIUM, HIGH, ULTRA, ALL, ENTITIES, BLOCKS (`IrlightsAddon.java:61-85`).
- Debug: section title, show/hide performance overlay, and the profiler tooltip (`UIDebugSection.java:18-38`).
- Patcher: Shaderpacks, Patches, refresh/open-folder tooltips, Create new pack each time, Validate, Patch, validation tooltip, every selection/meta/status/error sentence, and all parameterized messages containing target pack or output pack names (`UIPatcherSection.java:43-305`). Use `L10n.lang(key).format(...)`/`IKey.format(...)` for parameters instead of concatenating localized fragments.

The same field/property keys must drive replay-editor track titles. A client-side IRLite adapter/Mixin at `UIKeyframeSheet.getTrackTitle` is the narrowest current integration point: detect `PointLightForm`/`SpotlightForm`, map `property.getId()` to the corresponding localized `IKey`, and preserve any parent path prefix in `channel.getId()` using the same shape as the sound-track translation at `UIKeyframeSheet.java:87-95`. Do not change serialized property/channel ids; only replace the displayed title.

### Shaderpack settings localization

The seven `.irlights` patches currently add English shader setting `.lang` content, and Bliss/Photon/Solas additionally carry Russian. No patch adds Chinese:

- `patches/bliss.irlights` (`en_us.lang`, `ru_RU.lang`)
- `patches/bsl.irlights` (`en_US.lang`)
- `patches/complementaryreimagined.irlights` (`en_US.lang`)
- `patches/iterationrp.irlights` (`en_us.lang`)
- `patches/photon.irlights` (`en_US.lang`, `ru_RU.lang`)
- `patches/rethinkingvoxels.irlights` (`en_US.lang`)
- `patches/solas.irlights` (`en_US.lang`, `ru_RU.lang`)

Each patch needs an `@file shaders/lang/zh_CN.lang` section mirroring that patch's complete IRLights `screen.*`, `option.*`, enum-value, and `.comment` key set. Preserve Minecraft formatting codes and the exact keys used by the target pack. This is necessary for the Iris shader options screen; BBS JSON localization does not affect shaderpack `.lang` files.

### Likely implementation files

- `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1/src/main/java/qualet/irlite/IrlightsAddon.java`
- `.../src/main/resources/assets/irlite/strings/en_us.json`
- `.../src/main/resources/assets/irlite/strings/zh_cn.json`
- `.../src/client/resources/irlite.client.mixins.json`
- `.../src/client/java/qualet/irlite/mixin/client/L10nMixin.java` (remove)
- Point/spot form editor panel and form UI classes under `.../client/ui/forms/editors/`
- `.../client/ui/presets/UIPresetSection.java`
- `.../client/ui/debug/UIDebugSection.java`
- `.../client/ui/patcher/UIPatcherSection.java`
- `.../src/main/java/qualet/irlite/IrlitePresets.java` and mode-label construction in `IrlightsAddon.java`
- A new client adapter/Mixin for IRLite replay-track titles
- All seven files under `.../patches/*.irlights`

### Tests and quality gates

- Assert both JSON files parse and have identical key sets.
- Assert the IRLite internal source pack resolves `irlite:strings/en_us.json` and `irlite:strings/zh_cn.json` from the built JAR.
- Assert the old `L10nMixin` is absent from the Mixin config and artifact.
- Add a replay-title test for every point/spot property id, including `beam_strength`, in English and Simplified Chinese; assert serialized sheet/channel ids are unchanged.
- Add a guard against user-visible `IKey.constant("English text")` in IRLite UI classes, allowing only dynamic pack filenames and intentionally raw data.
- Parse all seven patches and assert each adds `shaders/lang/zh_CN.lang` with the same IRLights key set as its English section.
- Inspect the built JAR for both JSON resources, the replay-title adapter, and the current patch files.
- Manual Chinese matrix: form picker/editor, point/spot panels, settings categories and modes, presets, debug, patcher success/failure states, Iris shader options, replay dope-sheet titles, and keyframe editor labels.
- Manual beam matrix: selected freshly patched pack, values `0/1/10`, and a replay interpolation from `0` to `10`.

### Files found

- `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1/src/main/java/qualet/irlite/forms/PointLightForm.java` - point-light serialized properties.
- `.../forms/SpotlightForm.java` - spotlight serialized properties including gobo fields.
- `.../client/forms/PointLightFormRenderer.java` and `SpotlightFormRenderer.java` - live form-to-registry handoff.
- `.../client/light/LightCollector.java` - model-block/scanner registration path.
- `temp/bbs-irlights-addon-1.21.1/upstream-irl-core-git/src/main/java/org/qualet/irl/light/LightRegistry.java` - per-frame light storage and GPU flush.
- `.../org/qualet/irl/light/LightBuffer.java` - SSBO layout and `vlParams.z` write.
- `.../client/ui/patcher/UIPatcherSection.java` - explicit generated-pack workflow and hardcoded patcher strings.
- `.../mixin/client/L10nMixin.java` - current English-only fallback registration.
- `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeSheet.java` - raw non-sound track title conversion.
- `new/src/main/java/mchorse/bbs_mod/BBSMod.java` and `new/src/client/java/mchorse/bbs_mod/BBSModClient.java` - addon source-pack and L10n event order.
- `temp/irlite-connector-runtime/runs/client/config/iris.properties` - validation fixture with no active shaderpack.

## External References

- No external documentation was required for the primary conclusion. The Java-to-SSBO-to-GLSL path, shaderpack output workflow, event ordering, and fixture state are all directly evidenced in the checked-in/local workspace sources above.

## Related Specs

- `.trellis/spec/new/backend/external-addon-integration-contract.md` - four-argument internal source-pack registration, L10n provider/reload ordering, client-only integration, and runtime verification requirements.
- `.trellis/spec/frontend/component-guidelines.md` - consistent localized labels/tooltips and complete control states.
- `.trellis/spec/frontend/quality-guidelines.md` - UI verification expectations.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` - applicable because the behavior crosses serialized Form values, replay keyframes, registry/SSBO upload, generated GLSL, Iris selection, and two localization systems.

## Caveats / Not Found

- No current user-instance `latest.log`, `debug.log`, Iris configuration, or selected generated shaderpack was found. An inactive/stale/unpatched pack is the strongest supported diagnosis, not an absolute proof of the user's exact runtime state.
- The fixture's successful main-menu launch cannot validate beams, Forms in a world, replay interpolation, or shader compilation because `shaderPack=` is empty.
- A shaderpack may compile while its IRLite volumetric option is disabled. The manual matrix must also verify the pack's `IRLITE_VOLUMETRIC`/equivalent option and global BBS `vl_intensity` are nonzero.
- The active-task command returned `(none)` even though this task's `task.json` is `in_progress`; this research uses the task path explicitly supplied by the parent.
- The current PRD explicitly lists shader patch changes, lighting algorithm changes, and unrelated UI as out of scope. The newest user request expands that scope. The main session must revise the task artifacts or create a follow-up/child task before implementation.
- Research-agent policy forbids production edits and Git operations. No source, resource, patch, task planning artifact, or test file was changed by this research subtask.
