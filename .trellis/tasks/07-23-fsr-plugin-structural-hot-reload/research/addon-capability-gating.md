# Addon capability gating

## Findings

- `BBSAddonRegistryAdapters` calls `requireCapability` for each registry facade and reports a rejected `BBSRegistrationResult` when the descriptor omits the capability.
- The initial hot-plugin common adapters use the stricter failure form required by the task: facade misuse throws `IllegalStateException` during candidate prepare.
- Addon client structural windows validate lifecycle phase but do not provide hot replacement semantics.

## Decision

Keep hot-plugin facade gating strict and deterministic: every common/client facade checks the corresponding manifest capability before staging any contribution, and every facade is sealed after `prepare`. Addon startup behavior is unchanged.
