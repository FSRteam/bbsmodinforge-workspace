# Missing structural type behavior

## Current behavior

- `FormUtils.fromData` catches factory failures and returns `null`; `ValueForm` then loses the original map on the next save.
- `Clips.fromData` catches unknown factory types and drops them, except for one legacy `bbs:circular` migration.
- `IFactory.fromData` cannot represent unknown types because it instantiates before reading the payload.
- Particle components are class-name metadata and can be skipped when their class is unavailable, but the source scheme data itself remains file-backed.

## Decision

Introduce host-owned missing form and clip placeholders that retain a deep copy of the original `MapType`. Serialization returns that original data unchanged. `ValueForm` and `Clips` attempt lazy recovery whenever the missing type becomes registered again. Structural reload also tracks live `ValueForm` and `Clips` holders weakly, snapshots affected old-generation instances before teardown, and rebuilds them after candidate activation. A single failed rebuild stays as a placeholder and does not stop the transaction.
