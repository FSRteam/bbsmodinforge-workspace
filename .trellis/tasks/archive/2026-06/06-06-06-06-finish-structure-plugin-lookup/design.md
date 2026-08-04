# Design: Structure plugin saved structure lookup

## Data Flow

`/bbs structures save <id>` writes a Minecraft structure template through `StructureTemplateManager`. On disk this lives under the current world `generated/<namespace>/structures/...` tree.

The Structure form picker should list those files through the BBS `AssetProvider` as `world:` links. The selected `structure_file` value is then consumed by `StructureFormRenderer`, which first tries `StructureTemplateManager` ids and then falls back to reading provider NBT links.

## Project Boundary

The Structure feature must be a separate Gradle plugin project, following the
shape of `bbs-premiere-export`:

- standalone `@Mod` entrypoint
- bootstrap class that registers a `BBSAddonMod` through `BBSMod.registerAddon`
- common source set for form/resource-pack registration
- client source set for renderer/editor UI registration
- dependency on `../new/build/libs/bbs-${bbs_version}.jar`

`new/` should only keep general extension events used by external addons. The
accidentally embedded Structure addon/classes should be removed from `new/`.

## Registration Boundary

Use the existing BBS addon bridge for common-side registration. Standalone
NeoForge mods must first enter the BBS collector through `BBSMod.registerAddon`;
their own mod bus does not receive events posted on BBS' mod bus.

Once collected, the plugin handles these BBS internal events:

- register `StructureForm` from `RegisterFormsEvent`
- register `WorldStructuresSourcePack` from `RegisterSourcePacksEvent`

Client-only static registries still live in client classes. The standalone
plugin client initializer must register:

- `StructureFormRenderer` in `FormUtilsClient`
- `UIStructureForm` in `UIFormEditor`

## Lookup Contract

- Saved world structures are exposed as `world:structures/<namespace>/<path>.nbt`.
- BBS asset structures are exposed as `assets:structures/<path>.nbt`.
- Renderer accepts both links plus plain Minecraft ids.
- Missing/unreadable structures render nothing or placeholder UI, not a crash.
