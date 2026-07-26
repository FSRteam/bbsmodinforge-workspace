# Runtime renderer reload

## Findings

- `EntityRenderers.register` and `BlockEntityRenderers.register` are runtime `Map.put` operations over private static provider maps.
- `EntityRenderDispatcher.onResourceManagerReload(ResourceManager)` and `BlockEntityRenderDispatcher.onResourceManagerReload(ResourceManager)` rebuild live renderer maps from those provider tables.
- Provider removal/replacement must restore the previous provider on teardown; only removing the live renderer leaves a plugin provider reachable from the static table.
- A targeted live-map mutation would require reconstructing provider contexts and touching private dispatcher state. The resource-listener path already owns that construction and failure behavior.

## Decision

Add host-owned Mixin accessors for the two provider maps. At the client-thread safepoint, install or restore provider entries with identity checks, then invoke the two dispatcher reload listeners once per structural transaction. Renderer capabilities remain limited to already registered entity/block-entity types.
