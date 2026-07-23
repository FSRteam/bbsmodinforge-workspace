# Port BBS Structure Addon design

## Boundaries

Implementation stays in `new/`. The port crosses these layers:

1. Addon registration: hook the migrated feature into the current NeoForge `bbs-addon` protocol or a local built-in registration point that mirrors it.
2. Form model: add a serializable Structure form value object using existing `Value*` fields.
3. Form registry/category: make the Structure form available in the BBS form selection flow.
4. Form editor UI: expose structure id editing through existing Java UI controls.
5. Runtime rendering: load a `StructureTemplate` by `ResourceLocation` and render its block data with 1.21.1 client APIs.

## Expected Data Flow

```
UI editor text/picker -> StructureForm.structure -> Form data JSON -> Form loader -> StructureFormRenderer -> StructureTemplateManager -> baked/rendered blocks
```

Contracts:

- The form owns a string structure id such as `namespace:path`.
- Empty id renders nothing.
- Invalid id logs or silently skips in the renderer; it must not crash previews.
- Form data uses the same naming/style as nearby form classes.
- Renderer caches per structure id where practical but must invalidate when the id changes.

## Integration Strategy

Prefer following existing patterns discovered in `new`:

- Form classes under `new/src/main/java/mchorse/bbs_mod/forms/forms`.
- Renderers under `new/src/client/java/mchorse/bbs_mod/forms/renderers`.
- Editor panels under `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels`.
- UI keys under `new/src/client/java/mchorse/bbs_mod/ui/UIKeys.java`.
- Localization in `new/src/client/resources/assets/bbs/assets/strings/*` and/or `new/src/main/resources/assets/bbs/lang/*` depending on existing form labels.

If the current BBS form registry is static, register Structure form directly in the same registry. If the addon protocol exposes an extension event for form registration, create a built-in addon class that registers through that path.

## Rendering Strategy

Use Minecraft/NeoForge 1.21.1 structure APIs already present in `new/src/main/java/mchorse/bbs_mod/BBSCommands.java` as the mapping anchor:

- `net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate`
- `net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager`
- `ResourceLocation`

Rendering should reuse existing BBS block/model rendering helpers where possible. If no direct helper exists, render block states from the template using Minecraft block render dispatcher, keeping transforms scoped to the current pose stack and avoiding persistent GPU allocations.

## Compatibility

- Existing forms must keep their ids and serialized shape.
- Missing structure templates should leave the form visible/editable.
- The implementation must not require Fabric loader entrypoints.
- Avoid adding dependency coordinates for the old addon jar unless explicitly needed and approved.

## Risks

- Structure rendering APIs are sensitive to client/server level access and render buffer lifecycle.
- Structure templates can contain block entities; initial implementation may render block states first and only add block entity rendering if existing BBS patterns support it safely.
- External addon source was not cloned due approval rejection, so implementation may not exactly match old private class layout.

## Rollback

Expected touched areas are limited to:

- `new/src/main/java/mchorse/bbs_mod/forms/**`
- `new/src/client/java/mchorse/bbs_mod/forms/renderers/**`
- `new/src/client/java/mchorse/bbs_mod/ui/forms/**`
- `new/src/client/java/mchorse/bbs_mod/ui/UIKeys.java`
- `new/src/client/resources/assets/bbs/assets/strings/**`
- possibly `new/src/main/java/mchorse/bbs_mod/addon/**` if a built-in addon hook is required.
