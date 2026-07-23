# Together source audit — 2026-07-15

Reference tree:

`D:\编译任务\bbs_FSloveCML\temp\bbs-together-1.6.10-bbs2.3-wenxu-source`

The directory is a compatibility patch/decompilation maintenance tree, not complete authoritative Together source. Core `TogetherClientNetwork`, `TogetherServerNetwork` and packet crusher implementations are absent. It is suitable for observing behavior and lifecycle contracts, not for copying packet formats or compression.

## Adopted behavior

- Switch received work onto the Minecraft client/server thread before touching BBS state.
- Coalesce semantic value updates, remove redundant child updates when an ancestor is already queued, and suppress echo with a nesting-safe remote-apply depth.
- Send presence changes immediately and cap stable cursor/presence traffic near 30 Hz; allow a slower idle/leave heartbeat.
- Prefer path-based Film/Replay updates. Use complete Film snapshots only for join, explicit save and resynchronization.
- Send explicit leave/clear state instead of retaining stale cursor or selection state.
- Budget chunks using actual encoded byte size, including UTF-8 and length-prefix overhead.
- Scope cursor/selection presence by editor type, Replay selection and stable full-sheet identity.

## Rejected implementation details

- Fabric packet ids, Yarn-obfuscated calls, mixin overwrites, reflection into private fields and historical fix-class structure.
- Negative sentinel values as the stable clear-state protocol.
- Client-selected snapshot authority without server authorization/version checks.
- Lazy offline pruning in place of a real disconnect hook.
- New daemon thread plus sleep/retry for UI recovery.
- `toData().toString()` as a durable identity or conflict key.

## Implication for this task

Browser co-control must retain server-owned gesture ownership and per-participant held-state cleanup. True multi-user editing must be a separate NeoForge semantic collaboration protocol with server membership, sequence ordering, authorization, gap recovery and client-thread echo-suppressed application.
