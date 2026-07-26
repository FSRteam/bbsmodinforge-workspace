# Plugin class static-cache inventory

## Direct registration caches

| Cache | Plugin reference | Required teardown |
| --- | --- | --- |
| `FormUtilsClient.map` | form Class and renderer factory | identity-checked removal |
| `UIFormEditor.panels` | form Class and panel supplier | identity-checked removal |
| `UIClip.FACTORIES` / `SCROLLS` | clip Class, editor factory and scroll entry | identity-checked removal and scroll purge |
| `EntityRenderers.PROVIDERS` | entity type to plugin provider | restore prior provider |
| `BlockEntityRenderers.PROVIDERS` | block-entity type to plugin provider | restore prior provider |
| `KeyMapping.ALL` and `Options.keyMappings` | plugin `KeyMapping` | remove by mapping name/identity and reset lookup |

## Derived/runtime caches

- `FormCategories` stores form instances, not factory Classes. It must be marked dirty/refreshed after a structural transaction; plugin-provided extra instances must use owner-aware registration.
- `MobFormRenderer.parts` and related renderer caches key vanilla entity classes, not plugin form classes, and are not a registration surface.
- UI editor open-state fields can temporarily hold an old form/clip. The client projection refresh closes/rebuilds affected panels from the model after the main holder snapshot.

## Decision

The client structural facade owns all direct registration caches listed above. Teardown operations compare both key and value identity so closing an old generation cannot remove a newly committed replacement.
