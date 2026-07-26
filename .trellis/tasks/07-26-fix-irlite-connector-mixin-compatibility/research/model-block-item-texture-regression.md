# Research: Model-block item texture/render regression

- Query: Determine why the BBS FSR model-block item texture appears to have been lost, and identify the smallest evidence-backed source/resource/test repair for Minecraft 1.21.1 NeoForge.
- Scope: internal + local upstream/reference snapshots + cached official NeoForge/Minecraft sources
- Date: 2026-07-27

## Findings

### Conclusion and confidence

The bundled PNG is not absent from the current source or artifact. The strongest reproducible code-level failure mode is instead the model-block item's dynamic renderer caching an unhydrated `ModelBlockEntity` when no client level/registry provider is available. That leaves `ModelProperties.form == null`, so the BEWLR intentionally draws nothing and the item looks as if its texture were missing.

This is an evidence-backed defect in the current control flow, but the available runtime evidence only reached the main menu and did not reproduce the inventory view. Treat it as the leading root cause, not as a completed in-game diagnosis.

If "lost texture" means a separately redrawn PNG rather than a blank item render, that newer artwork is not present in any inspected workspace copy. Its exact pixels cannot be reconstructed from the current repository evidence.

### Files found

- `new/src/client/resources/assets/bbs/assets/textures/model_block.png` - current 16x16 ARGB model-block billboard image; 631 bytes.
- `new/src/main/resources/assets/bbs/models/item/model.json` - declares the item as `minecraft:builtin/entity`, which is the correct marker for a custom item renderer.
- `new/src/main/java/mchorse/bbs_mod/BBSMod.java` - creates the creative-tab model-block stacks and writes their serialized form into `DataComponents.BLOCK_ENTITY_DATA`.
- `new/src/client/java/mchorse/bbs_mod/client/BBSClientNeoEvents.java` - registers model-block and gun `IClientItemExtensions` on the mod bus.
- `new/src/client/java/mchorse/bbs_mod/client/renderer/item/ModelBlockItemBEWLR.java` - NeoForge BEWLR adapter that delegates to the BBS renderer.
- `new/src/client/java/mchorse/bbs_mod/client/renderer/item/BBSItemRenderers.java` - lazily creates and retains the BEWLR instances.
- `new/src/client/java/mchorse/bbs_mod/client/renderer/item/ModelBlockItemRenderer.java` - reads block-entity item data, caches an item renderer entity, and renders its form.
- `new/src/main/java/mchorse/bbs_mod/blocks/entities/ModelBlockEntity.java` - canonical `Properties` save/load contract.
- `new/src/main/java/mchorse/bbs_mod/resources/AssetProvider.java` and `new/src/main/java/mchorse/bbs_mod/resources/packs/InternalAssetsSourcePack.java` - resolve `assets:textures/model_block.png`, with external `config/bbs/assets` taking precedence over bundled assets.
- `new/src/clientRegistrationTest/java/mchorse/bbs_mod/client/compat/ClientStructuralRegistrationWindowTest.java` - current registration regression coverage; it does not assert either custom-item registration or model-block item hydration.
- `old2.0/bbs-fs/`, `temp/bbs-fs-2.3/`, `temp/bbs-fs-1.21.1-ui/`, `temp/bbs-fs-1.21.1-2.2.1-build/`, and `temp/bbs-refreshed-2.0-public-1.20.1-decompiled/` - comparison snapshots.

### Resource and artifact evidence

- All six inspected `model_block.png` copies are byte-identical, SHA-256 `3CEB877A590259EF3CA726DAB258A501406A0C4BAE31C48A6EBBE7663F7C2770`.
- `new/build/libs/BBS_FSR_beta_v0.0.10.jar` contains `assets/bbs/assets/textures/model_block.png` (631 bytes) with the same SHA-256.
- The same JAR contains `assets/bbs/models/item/model.json`, `BBSClientNeoEvents.class`, and `ModelBlockItemBEWLR.class`.
- The source and packaged item model both declare `parent: minecraft:builtin/entity` and `gui_light: front` (`new/src/main/resources/assets/bbs/models/item/model.json:2-3`). The apparent hash difference from reference JSON files is only line-ending formatting; their parsed content is identical.
- The current isolated Connector fixture and `new/run` contain no external `config/bbs/assets/textures/model_block.png` override. The user's prior launcher instance named in the crash report no longer exists, so its former external asset directory cannot be checked.

### NeoForge custom-renderer contract is wired correctly

- `BBSMod` registers the client bridge only on `Dist.CLIENT` and invokes `BBSClientNeoEvents.register(modBus)` before client mod-bus registration events (`new/src/main/java/mchorse/bbs_mod/BBSMod.java:461`, `:481`).
- The bridge subscribes to `RegisterClientExtensionsEvent` (`new/src/client/java/mchorse/bbs_mod/client/BBSClientNeoEvents.java:50-56`).
- The event registers an `IClientItemExtensions` whose `getCustomRenderer()` returns the model-block BEWLR for `MODEL_BLOCK_ITEM` (`BBSClientNeoEvents.java:99-108`).
- `ModelBlockItemBEWLR.renderByItem` delegates every render call with stack, display context, pose, buffers, light, and overlay intact (`new/src/client/java/mchorse/bbs_mod/client/renderer/item/ModelBlockItemBEWLR.java:22-25`).
- Official NeoForge 21.1.235 source says `RegisterClientExtensionsEvent` is a logical-client mod-bus event and `registerItem` stores the extension (`RegisterClientExtensionsEvent.java:26`, `:56-57`). It also says `getCustomRenderer()` is used when `BakedModel.isCustomRenderer()` is true (`IClientItemExtensions.java:170-179`).
- Minecraft 1.21.1 source maps `builtin/entity` to `ModelBakery.BLOCK_ENTITY_MARKER`, bakes that marker as `BuiltInModel`, and `BuiltInModel.isCustomRenderer()` returns true. Cached source evidence: `ModelBakery.java:58-60`, `:76-77`, `:200-205`; `BlockModel.java:220-225`; `BuiltInModel.java:50-52`.

Therefore replacing `model.json` with a normal `item/generated` model is not an appropriate repair: it would bypass the dynamic form renderer and break picked/customized model-block items.

### Data-component creation is correct

- The default creative item is created with `Link.assets("textures/model_block.png")` (`new/src/main/java/mchorse/bbs_mod/BBSMod.java:266-272`).
- `createModelBlockStack` creates a `BillboardForm`, assigns the requested texture, serializes `ModelProperties`, and writes it through `BlockItem.setBlockEntityData` (`BBSMod.java:301-317`).
- Minecraft 1.21.1 `BlockItem.setBlockEntityData` removes a prior `id`, adds the supplied block-entity type, and stores `CustomData` in `DataComponents.BLOCK_ENTITY_DATA` (cached source `BlockItem.java:223-230`).
- The canonical BBS load contract reads the `Properties` compound and calls `ModelProperties.fromData` (`new/src/main/java/mchorse/bbs_mod/blocks/entities/ModelBlockEntity.java:120-139`).

### Leading defect: empty entity is cached before hydration can succeed

The current renderer performs these operations in this order:

1. Return an existing cached item if present (`ModelBlockItemRenderer.java:115-120`).
2. Read `BLOCK_ENTITY_DATA`, create a blank `ModelBlockEntity`, create its wrapper, and immediately cache it (`:122-126`).
3. Attempt to hydrate through `applyBlockEntityTag` (`:128-135`).
4. `applyBlockEntityTag` only obtains the 1.21.1 `HolderLookup.Provider` from `Minecraft.getInstance().level` (`:171-179`). If the level is null, it skips `loadWithComponents`; the legacy reflective `load(CompoundTag)` and `readNbt(CompoundTag)` fallbacks do not exist on the 1.21.1 Mojmap `BlockEntity` (`:191-214`, `:239-270`). All failures are silently swallowed.

Minecraft 1.21.1 does expose public final `loadWithComponents(CompoundTag, HolderLookup.Provider)` (cached source `BlockEntity.java:82-88`), so hydration works once a level exists. The defect is the null-level path: it has already cached the blank entity, so later calls return at step 1 and never retry. `render` then sees a null form and emits no geometry (`ModelBlockItemRenderer.java:61-105`).

This regression was introduced by the 1.21.1 data-component/registry migration. The Fabric 1.21.1 reference also needed a world registry manager, but it did not add reflection; the older renderer constructed its `StubEntity` with the current world. The NeoForge port additionally changed the form entity to `new StubEntity()` (`ModelBlockItemRenderer.java:145-149`) and never refreshes its world, weakening arbitrary customized-form rendering even after data hydration.

### External override caveat

`BBSMod` registers the external/dynamic `config/bbs/assets` source before the bundled internal source (`new/src/main/java/mchorse/bbs_mod/BBSMod.java:547-551`), and `AssetProvider.getAsset` returns the first pack that contains a link (`new/src/main/java/mchorse/bbs_mod/resources/AssetProvider.java:109-126`). A stale or unintended `config/bbs/assets/textures/model_block.png` therefore overrides the JAR image by design. The deleted user instance prevents verifying whether that happened in the reported environment.

### Smallest source changes recommended

1. In `ModelBlockItemRenderer`, hydrate the renderer's one owned field directly from the copied `BLOCK_ENTITY_DATA` tag: call `DataStorageUtils.readFromNbtCompound(tag, "Properties")`, require a `MapType`, and call `entity.getProperties().fromData(map)`. This is the exact contract already owned by `ModelBlockEntity.loadAdditional` and needs no level or registry provider.
2. Insert the wrapper into the cache only after hydration is complete. Malformed/temporarily unavailable data must not permanently cache a blank form.
3. Restore the old world relationship for form rendering by constructing or refreshing `StubEntity` with `Minecraft.getInstance().level` before update/render. This matters for customized model-block items whose forms consult `IEntity.level()`.
4. Remove the `loadWithComponents`/`load`/`readNbt` reflection machinery if it has no remaining consumer. Keeping it as the primary path retains the null-level failure and silently swallowed exceptions.
5. Keep `models/item/model.json` as `minecraft:builtin/entity` and keep the existing NeoForge client extension/BEWLR registration.
6. Do not replace the current PNG blindly. If the requested artwork is a newer edited image, obtain its canonical file, screenshot, or history blob first; then replace only `new/src/client/resources/assets/bbs/assets/textures/model_block.png`.

### Tests required

- Add a focused model-block item data regression that round-trips a `BillboardForm` using `assets:textures/model_block.png` through `ModelProperties -> NBT Properties -> ModelProperties` without a client level, and asserts the resulting inventory form/texture link is non-null and exact.
- Add a cache-order regression: an initial null-level/no-registry render must not permanently retain a wrapper whose form is null.
- Extend `ClientStructuralRegistrationWindowTest.productionFacadeAndNeoForgeEventsStayWired` to assert the model-block `RegisterClientExtensionsEvent` registration and its BEWLR delegate. The current test only checks key mappings and entity/block-entity renderer windows (`ClientStructuralRegistrationWindowTest.java:123-176`).
- Add a resource contract that asserts the PNG exists, is a valid 16x16 image, and the item model retains the `minecraft:builtin/entity` parent. If a canonical replacement artwork is recovered, an explicit SHA-256 assertion can prevent accidental reversion.
- Inspect the built JAR for the PNG, item model JSON, client bridge, and BEWLR classes.
- Manual in-game matrix: creative-tab icon, model-block item in inventory, first/third person, dropped item, pick-block copy of a customized model block, and a relog/resource reload. Also check for an external `config/bbs/assets/textures/model_block.png` override.

## External References

- NeoForge 21.1.235 source JAR: `C:/Users/29617/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.235/4566e557485e2cf5843d29a9c44795f5f731d36d/neoforge-21.1.235-sources.jar`.
- Minecraft 1.21.1 Mojmap/NeoForge transformed source JAR: `C:/Users/29617/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_2314d4633d24914781c4a1939597440ee3ad7e24_output.jar`.
- Project compile target is NeoForge 21.1.222 (`new/gradle.properties`); the reported runtime target is 21.1.235. The custom-renderer API contract above is present in 21.1.235.

## Related Specs

- `.trellis/spec/new/backend/quality-guidelines.md` - current renderer ownership and required compile/in-game validation rules.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` - applicable because the visible item crosses resource, item data component, client registration, and renderer layers.
- `.trellis/spec/guides/code-reuse-thinking-guide.md` - supports reusing the canonical `Properties` serialization contract rather than maintaining a reflection-only parallel reader.

## Caveats / Not Found

- `python ./.trellis/scripts/task.py current --source` returned no active task. This file uses the task path explicitly supplied by the parent dispatch.
- Research-agent policy forbids Git operations, so Git history was not inspected in this subtask. The parent/implementation agent must inspect history if recovering a genuinely different PNG is required.
- No task/journal note, old2.0 copy, temporary source snapshot, decompiled public copy, current JAR, or local runtime override contains a different `model_block.png`.
- The previous launcher instance named by the crash report is gone, so its external BBS assets and actual on-screen item state are unavailable.
- Existing successful runtime logs stop at the main menu. They show no client-bridge failure or item-model load warning, but they do not prove the model-block inventory render.
