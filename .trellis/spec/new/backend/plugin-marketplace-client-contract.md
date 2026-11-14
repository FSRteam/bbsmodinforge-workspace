# Plugin marketplace and client embedding

## 1. Scope / Trigger

Apply to the generic FSR host seams for account taskbar entries, plugin catalogs,
external model sources, secure credentials and managed model publication.
Implemented in the isolated `workspaces/fsr-mbh-host` integration branch; original
`new/` does not yet contain these additions. MBH business logic belongs to the
independent integration plugin, not the host.

## 2. Signatures

The physical-client `BBSPluginClientContext` adds default methods
`accountEntries()`, `pluginCatalogs()`, `resourceSources()` and `services()`.
Their capabilities are `taskbar_account`, `plugin_catalog`, `resource_sources`
and `client_services`. Older implementations throw unsupported rather than
changing the frozen common Addon API descriptors.

Registries expose `register(String id, Provider)` returning
`BBSRegistrationResult`. Source content is created per mount through
`BBSResourceSource.factory()` and exposes an element root and optional
`BBSLocalModelSelection`. `BBSResourceSourceContext` carries intent, current
local key, liveness and a view revision; it deliberately carries no scene callback.
Capture `viewRevision().getAsLong()` before view work and require
`isCurrent(revision)` before applying results, including hide/show of cached roots.

`BBSPluginClientServices` exposes:

```java
boolean isActive();
AutoCloseable onActivated(Runnable callback);
BBSCredentialStore credentials(String provider, String client, String account);
CompletableFuture<BBSResourcePublicationResult> publish(BBSResourcePublication publication);
CompletableFuture<BBSLocalResourceState> inspectResource(String modelKey);
AutoCloseable onFocusReturned(Runnable callback);
AutoCloseable onOperationCompleted(Consumer<BBSClientOperation> callback);
void operationCompleted(BBSClientOperation operation);
```

## 3. Contracts

- Register during prepare; publish with the generation and revoke through its
  contribution ledger. Cleanup matches the exact contribution identity, so an
  old generation cannot remove its replacement. Service deactivate is reversible
  for rollback; close is final. Native bindings remain host-loaded and client-only.
- Place accounts at the far right of the global panel-icon taskbar. Reserve
  measured space before the scrollable panel tabs and use actual layout
  coordinates for auto-hide. The account is outside model-panel footers.
- Snapshot providers return immutable in-memory state. Rendering never performs
  network/archive/disk work. Avatar links refer to cached local textures.
- Model sources reuse `UIModelSources` in the selection screen, open overlay,
  form palette and model-key picker. Hide/show preserves per-mount content;
  removal closes it. Plugin unload falls back to Local. Reattach restores live
  input, and hidden ancestors make an acceptance context inactive.
- After managed publication, `LocalModelListRefresh` projects changed model keys
  into the editor/Open/model-key Local lists while retaining their query, path,
  selected values and scroll. It consumes in-memory revisions, never scans disk
  or invokes selection callbacks during rendering.
- `ModelSourceSession` accepts only the same ready local selection shown to the
  user, after an explicit host button click in the current live mount. Browsing,
  refresh and download completion cannot invoke a scene mutation. Model editor
  navigation uses `UIDataDashboardPanel.pickData` to preserve save/tab behavior.
- Credentials are namespaced by plugin/provider/client/account. Read/write/delete
  are asynchronous; Windows DPAPI persistence falls back to session memory when
  unavailable, never plaintext. Unload preserves the session; logout deletes it.
  Credential results own a secret copy and must be closed; callers clear copies
  returned by `secret()`. This is lifecycle isolation, not a same-JVM sandbox.
- Publication accepts a complete validated staging directory and opaque metadata.
  The host rechecks containment, bounds, links and all adapted-file fingerprints,
  including extra files. Keep the ledger outside scanned assets. Stable model keys
  do not contain account, title or revision. Conflict modes are CANCEL, KEEP_BOTH
  and REPLACE_WITH_BACKUP. Directory/journal publication is recoverable and only
  affected model/pose/shape/texture caches are invalidated. No scene is edited.
- Revalidate incumbent fingerprints after durable journaling and before the
  short gated commit, then check owner liveness inside that commit. A local edit
  made while staging/journaling must remain intact and return conflict. Once
  publication committed, a cleanup error still queues targeted invalidation.
- Cancellation of the returned publication future fences an operation before
  its final commit, including account cancellation while the plugin stays active.
  Plugin adapters must propagate cancellation to that host future rather than
  cancelling only a derived continuation. Already committed files remain local.
- Long staging, hashing and native encryption work must not hold the lifecycle
  gate needed by client tick, deactivation or UI callbacks. Fence the short commit
  and reject stale generations; complete futures outside that gate.
- Native controls, `Icons` and semantic theme/motion rules remain authoritative.
  Page refresh is metadata work and is distinct from local plugin rescan/reload.

## 4. Validation & Error Matrix

| Condition | Result |
| --- | --- |
| undeclared capability or registration after prepare | reject registration |
| old contribution cleanup after replacement | replacement remains registered |
| hidden, closed, stale or changed model selection | no acceptance callback |
| unavailable secure persistence | SESSION_ONLY; no plaintext disk fallback |
| old service generation | STALE_OWNER; no new write/publication |
| modified texture/config/extra file | CONFLICT unless explicit conflict choice |
| import matches recorded revision and all hashes | UNCHANGED |
| completed publication | PUBLISHED, stable model key and optional backup path |
| failed resource inspection | FAILED, distinct from missing/unmanaged |

## 5. Good / Base / Bad Cases

- Good: a source imports a model and exposes its local key; the user then clicks
  Use and the current live picker accepts that exact resolved model.
- Base: without a provider, the original Local model and installed-plugin paths
  remain usable and account/source slots consume no space.
- Bad: invoking the Film palette callback on download completion, storing bearer
  text in plugin settings, or calling global resource reload for one imported model.

## 6. Tests Required

Execute `testPluginHotReload`, `testClientStructuralRegistration`,
`testBinaryCompatibility`, `testThemeCore`, `testUiMotions`, `testAddonLifecycle`
and full `check` for shared UI changes. New suites include
`PluginMarketplaceStructuralTest`, `PluginMarketplaceUiTest` and
`PluginClientServicesTest`; compilation alone is not evidence of execution.
Bootstrap test localization before constructing native UI controls.

Cover staged rollback, exact old cleanup, independent mounts, source switching,
unload/reopen/hidden parents, selection revisions, taskbar overflow, secure-store
failure, explicit deletion, stale writes, transaction recovery and local edits.
Real NeoForge rendering/themes/scales, Windows DPAPI and complete native format
fixtures are separate checks; opaque filesystem fixtures do not prove those.

## 7. Wrong vs Correct

```java
// Wrong: completion changes the user's current scene.
download.thenAccept(model -> palette.accept(model));

// Correct: publish data; the host owns the later explicit Use action.
services.publish(publication).thenAccept(result -> updateLocalState(result));
```
