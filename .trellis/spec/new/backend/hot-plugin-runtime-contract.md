# Hot Plugin Runtime Contract

## 1. Scope / Trigger

Apply this contract when changing FSR hot-plugin discovery, artifact validation,
generation routing, contribution ownership, file watching, client projection
refresh, or runtime shutdown. It does not apply to NeoForge mod loading or the
startup-only Addon API 2.0.

## 2. Signatures

- Public entrypoint: `BBSPlugin.prepare(BBSPluginContext)`, `start()`, and
  `stop(BBSPluginStopReason)`.
- Runtime control: `BBSPluginManager.start()`, `rescan()`, `reload(id)`,
  `enable(id)`, `disable(id)`, `diagnostics()`, and `close()`.
- Active route identity: `PluginOwner(pluginId, generation)`.
- Artifact manifest: `META-INF/bbs-plugin.json`, schema `1`, one plugin per JAR.
- Default source directory: `config/bbs/plugins/`; verified shadow artifacts:
  `config/bbs/plugin-cache/<id>/<sha256>/plugin.jar`.

## 3. Contracts

- `autoApply` defaults to `true`. A stable create/replace/delete is applied by
  the serial lifecycle controller without a normal rescan command.
- Any user-facing installer that writes into `config/bbs/plugins/` follows the
  same source-artifact contract as an external build copy: stage a temporary
  file in that directory, close it, then atomically replace the final JAR. If
  the install updates an existing `pluginId`, retire sibling source JARs for
  that plugin before publishing the new file so watcher reconciliation sees one
  owner artifact instead of a transient `DUPLICATE_ID` pair.
- The watcher emits immutable intents only. It never mutates active routes or
  invokes plugin code.
- Validate and shadow-copy before class loading. Immediately before publication,
  recapture the source fingerprint and require the same SHA-256 used to build
  the candidate.
- Prepare and start a candidate while the incumbent remains routable. All
  contract-compliant installation effects must be staged or ledger-owned.
- Publish one immutable active root. Under the same commit monitor, stop new
  incumbent acquires before exposing the candidate root.
- After publication, drain admitted incumbent leases, call `stop`, close ledger
  entries in reverse order, then close the generation classloader.
- Candidate failure before publication preserves the incumbent. Deterministic
  drain or cleanup failure marks `RESTART_REQUIRED`; delayed classloader GC is
  telemetry only.
- Content plugins create immutable host-owned snapshots and do not create a
  plugin classloader. Client refresh occurs only after the active root changes.
- Dedicated servers never resolve client entrypoint classes. Code and content
  artifacts both obey their declared physical side.
- Existing Addon API `2.0` descriptors and frozen payload IDs remain unchanged.

## 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Partial or changing source JAR | Do not publish; wait/retry or report `FAILED` while retaining the incumbent. |
| Source hash changes during candidate preparation | Reject as stale before active-root replacement. |
| Invalid manifest/API/side/dependency | Reject before entrypoint class loading. |
| NeoForge metadata, Mixin, AT, coremod, native, or protected host classes | Reject before publication; structural capability reports `RESTART_REQUIRED`. |
| Candidate constructor/prepare/start failure | Close staged resources and loader; incumbent stays active. |
| Callback throws `Throwable` | Contain at the plugin boundary and record bounded diagnostics. |
| Drain or deterministic teardown timeout | Mark `RESTART_REQUIRED` and block further automatic reload for that plugin. |
| WatchService overflow or invalid key | Perform full reconciliation; uncertain files must not cause false unload. |

## 5. Good / Base / Bad Cases

- Good: copy a build to a same-directory temporary file and atomically replace
  the final JAR; the watcher observes a stable hash and publishes one new
  generation.
- Good: the Dashboard install/update flow removes older source JARs for the
  same plugin id, writes the replacement to a same-directory temp file, then
  atomically moves it into place.
- Good: v3 fails during `prepare`; v2 continues serving events and assets.
- Base: deleting the only active source drains and unloads its generation.
- Bad: publish the candidate before fencing incumbent acquisition; callers can
  enter two generations for the same plugin.
- Bad: load classes directly from the source JAR; Windows builds cannot replace
  the file reliably and validation no longer identifies executed bytes.
- Bad: register plugin objects directly in permanent global collections; the
  classloader remains strongly reachable after unload.

## 6. Tests Required

- `testPluginHotReload`: drop-in install, same-id replace, failed-candidate
  retention, content replace, delete/unload, watcher stability/overflow, event
  routing, owner cleanup, and classloader telemetry.
- `testAddonLifecycle`, `testClientStructuralRegistration`, and
  `testBinaryCompatibility`: prove the new path is additive.
- `compileJava`, `compileClientJava`, and `pluginHotReloadTestClasses`.
- Production-shaped manual smoke remains required for a NeoForge client and a
  dedicated server before release; unit tests do not prove physical-side class
  initialization behavior.

## 7. Wrong vs Correct

### Wrong

```java
activeRoot.set(candidate);
incumbent.beginDrain();
```

This exposes the candidate before preventing a reader that captured the old
root from acquiring the incumbent.

### Correct

```java
synchronized (commitMonitor)
{
    incumbent.beginDrain();
    activeRoot.set(candidateRoot);
}
```

`acquire(...)` uses the same monitor, so every new call linearizes entirely
before or after the generation switch.
