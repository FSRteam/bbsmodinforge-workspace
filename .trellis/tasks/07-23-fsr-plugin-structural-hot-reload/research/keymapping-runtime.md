# Runtime key-mapping reload

## Findings

- Minecraft 1.21.1 exposes `Options.keyMappings` as a mutable public array.
- `KeyMapping.resetMapping()` rebuilds NeoForge's `KeyMappingLookup` from the static `KeyMapping.ALL` table.
- Constructing a `KeyMapping` immediately inserts it into `ALL`; staging therefore has to remove the mapping from `ALL` until commit, otherwise a prepared candidate leaks into the active runtime.
- `Options.load()` and `save()` only enumerate the active array. The host must retain the last `InputConstants.Key` by mapping name while a plugin is absent and restore it when the mapping returns.

## Decision

Use native array replay at a client-thread safepoint. The replay registry owns the candidate mapping, updates `KeyMapping.ALL`, replaces `Options.keyMappings`, restores the saved binding by name, and calls `KeyMapping.resetMapping()`. On teardown it saves the binding, removes the array/table entry only when identity still matches the generation, and resets the lookup again.
