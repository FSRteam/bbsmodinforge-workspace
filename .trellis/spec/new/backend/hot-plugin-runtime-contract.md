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
- Structural registration facades (additive, `prepare`-phase only): `BBSPluginContext.forms()/clips()/particles()`;
  client-only `BBSPluginClientContext.keyMappings()/renderers()`. Shape aligns with the
  corresponding Addon API 2.0 registries; see §8 for the generation contract these facades feed.
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
- Manual smoke and local update scripts must keep versioned build artifacts
  outside `config/bbs/plugins/`. Publish every generation through one stable
  source name (for example, `fixture.jar`); temporary and backup names must not
  end in `.jar` (for example, `fixture.jar.tmp` and `fixture.jar.bak`). Never
  place `fixture-1.0.jar` and `fixture-2.0.jar` beside each other: distinct paths
  do not represent generations, they represent competing owners for one plugin
  id and correctly produce `DUPLICATE_ID`.
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
- Good: keep `fixture-1.0.jar` and `fixture-2.0.jar` in the build output, then
  copy the desired version to `config/bbs/plugins/fixture.jar.tmp` and atomically
  replace the sole `config/bbs/plugins/fixture.jar` source.
- Good: the Dashboard install/update flow removes older source JARs for the
  same plugin id, writes the replacement to a same-directory temp file, then
  atomically moves it into place.
- Good: v3 fails during `prepare`; v2 continues serving events and assets.
- Base: deleting the only active source drains and unloads its generation.
- Bad: publish the candidate before fencing incumbent acquisition; callers can
  enter two generations for the same plugin.
- Bad: load classes directly from the source JAR; Windows builds cannot replace
  the file reliably and validation no longer identifies executed bytes.
- Bad: copy a new version under a versioned `.jar` name while the old stable
  `.jar` remains in the plugin directory; the watcher must reject the second
  artifact as `DUPLICATE_ID` rather than guess which path owns the plugin.
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
- Structural capability coverage (§8) is hermetic for generation-scoped
  registration, snapshot/rebuild round-trip, degradation, the busy guard, and
  the 100x alternating-reload leak baseline.
- What automated coverage cannot reach: keymapping replay, entity-renderer
  replay, and block-entity-renderer replay all cast live Minecraft objects to
  Mixin `@Accessor` interfaces at the safepoint. A bare-JVM `JavaExec` run has
  no Mixin transformer, so that cast throws `ClassCastException`; the same
  applies to real particle rendering and UI projection (form list, clip
  picker, key-binding screen). These are verified only by production-shaped
  manual smoke against the fixture plugins produced by
  `gradlew buildSmokeFixturePlugins` (`src/pluginSmokeFixture/`).
- Startup-reconcile/runtime-load equivalence for structural capabilities has
  no dedicated automated case; it relies on both paths sharing the same
  validator and coordinator code, not a duplicated test.

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

### Wrong

```java
map.remove(clazz);
```

Removing a structural registration by key alone during teardown can delete a
same-named replacement that a newer generation already committed for that key.

### Correct

```java
if (map.get(clazz) == registeredValue)
{
    map.remove(clazz);
}
```

Teardown compares the live entry's identity (the exact `Class`/value reference
captured at registration time) before removing it. A `Class` object is scoped
to the classloader that defined it, so a same-named type from a different
generation's classloader never satisfies this check — teardown of an old
generation cannot remove a newly committed one. See §8.2.

## 8. Structural Capability Contract (Generation 2)

Applies to the six capabilities that flipped `hotSafe=true` in this
generation: `forms`, `clips`, `particles`, `key_mappings`, `entity_renderer`,
`block_entity_renderer`. Everything in §§1–7 still holds; this section only
adds the contract for these capabilities.

### 8.1 Capability matrix

| Capability | Status |
| --- | --- |
| `forms`, `clips`, `particles`, `key_mappings`, `entity_renderer`, `block_entity_renderer` | `hotSafe = true`; validator no longer rejects on these alone. |
| `minecraft_registry`, `mixin`, `access_transformer`, `coremod`, `jni`, `native_library` | Unchanged hard boundary: `RESTART_REQUIRED`. These are not reachable through the FSR/Addon channel even at startup, only as a NeoForge mod. |
| Manifest declares `reload=restart` | Still forces a restart regardless of capability declarations. |
| Structural jar entries (mixin json, AT cfg, coremod, native, NeoForge metadata) | Still rejected before entrypoint class loading, independent of capability flags. |

### 8.2 Generation-scoped registration surfaces

- Each of the six host registration surfaces (`FormArchitect`, the camera and
  action clip `MapFactory`s, the particle component table, and the client-only
  form-renderer/editor/category caches, keymapping table, and the two entity /
  block-entity renderer provider maps) now binds every plugin contribution to
  `(pluginId, generation)` and records it in that generation's
  `PluginContributionLedger`. Host-builtin registrations do not carry an owner
  and are never touched by ledger teardown.
- Teardown walks the ledger in reverse and removes only entries whose live map
  value still matches the one captured at registration time (identity
  comparison, not just key lookup) — see the Wrong/Correct pair above. This is
  what makes it safe for an old generation's teardown to run after a new
  generation has already published replacements under the same key.
- Facades are only open during `prepare` and seal afterward (same seal
  pattern as the existing event registration facade); every registration
  handle they hand back is ledger-owned and revocable.

### 8.3 Main-thread safepoint and what "atomic publish" means

- Guard → snapshot → teardown → publish → commit → rebuild → replay →
  client-projection refresh all execute inside one `Minecraft.execute()`
  closure. No render frame is interleaved between any of these steps.
- Dedicated servers and headless/bare-JVM callers have no client main thread;
  the safepoint entry point short-circuits to a direct synchronous call in
  that case. This is an explicit branch, not a missing lock — it does not
  deadlock and does not skip the guard/snapshot/teardown/rebuild sequence.
- Design note `design.md` §2.6 draws "atomic publish (root swap)" as a box
  between teardown and commit. Read that box as the **host-visible structural
  registration surface switch** — the six surfaces in §8.2 flipping from the
  old generation's entries to the new generation's entries — which does
  complete inside the single safepoint closure above and is therefore atomic
  with respect to any reader on the main thread during that window.
  It is a distinct thing from the `ActivePluginIndex` `pluginId → generation`
  bookkeeping swap (root-generation routing for leases/events, per §3), which
  happens afterward on the lifecycle thread once the safepoint call returns
  successfully. Nothing observes structural state between those two swaps, so
  ordering them this way is not a defect: it only means "which generation is
  the current lease/event owner" is settled slightly after "which generation's
  forms/clips/renderers are live", not before.

### 8.4 Snapshot / rebuild

- Instances affected by a generation change (those whose form/clip type
  belongs to the old generation's ledger) are captured as a `MapType` deep
  copy before teardown, using the existing serialization path (the same one
  used to save/load a world), and rebuilt from that copy once the new
  generation's types are committed. This is why the model is data-driven
  "teardown + rebuild from data" rather than instance-level migration.
- Particles are the one exception: a particle scheme is file-backed, not
  captured into a `MapType` snapshot. Teardown discards the cached emitter,
  and rebuild is simply "read the file again" — forced on the next tick via
  `ParticleFormRenderer.lastUpdate`, not part of the snapshot/rebuild pipeline
  above.

### 8.5 Degradation matrix

| Condition | Required result |
| --- | --- |
| Type no longer registered (plugin removed, or new version dropped the capability) | Silent degradation to a missing-type placeholder (`MissingForm`/missing clip); the original `MapType` is retained unchanged; **no** `REBUILD_FAILED` diagnostic — this is an expected path, not a failure. |
| Type is registered but deserialization throws | Placeholder **plus** a `REBUILD_FAILED` diagnostic — this is a real failure. |
| Distinguishing the two | Both sides check registration first (`FormArchitect.has()` / the clip factory's `hasType()`) before attempting to deserialize, so "not registered" never falls through into the exception branch. |

Saved data for a missing type is never stripped; it round-trips unchanged
until the type is registered again.

### 8.6 Keymapping replay

- Replay is a native-array operation: the world-safe registry updates
  `KeyMapping.ALL`, replaces the live `Options.keyMappings` array entry, and
  calls `KeyMapping.resetMapping()` to rebuild NeoForge's lookup.
- The user's chosen key binding is kept in a name-keyed table that survives
  across generations, so reinstalling a plugin at a different generation
  restores the same binding.
- Known limitation: that table is process-memory only. It is not persisted to
  `options.txt`. If the plugin is removed and the game is restarted before it
  is reinstalled, the custom binding for that key mapping is lost.

### 8.7 Renderer replay

- Host-owned Mixin accessors expose the private provider maps behind
  `EntityRenderDispatcher` / `BlockEntityRenderDispatcher`. At the safepoint,
  provider entries are installed or restored using the identity check from
  §8.2, then both dispatchers' `onResourceManagerReload` are invoked once per
  structural transaction.
- Renderer capability can only override an **already-registered**
  `EntityType`/`BlockEntityType`; it cannot introduce a new one (Minecraft
  registries are frozen at this point in the lifecycle).

### 8.8 Export/recording guard

- A structural reload attempted while a video export or recording is active
  is rejected as `RELOAD_REJECTED_BUSY`. The old generation keeps running;
  the watcher's pending intent is retried, not dropped.
- The busy check queries the recorder, the world video export session, and
  the Film panel fetched **by type** from the dashboard — not whichever panel
  is currently selected — so a panel-level export that keeps running in the
  background overlay still blocks a reload even after the user navigates
  away from the Film panel.
- Known behavior: there is no "export just finished, retry now" hook. A
  rejected intent is only retried by the watcher's periodic reconciliation
  (5-minute period, `PluginDirectoryWatcher` config), the same mechanism used
  for general watcher reconciliation.

### 8.9 Particle component classloader

- The particle component registry records the `ClassLoader` in effect at
  registration time alongside the component class name.
- Hot-plugin entries resolve their class with the three-argument
  `Class.forName(name, true, loader)` against that recorded generation
  classloader, because the host's own loader cannot see a class defined only
  inside a plugin generation's isolated classloader.
- Addon API 2.0 entries carry no recorded loader and keep the original
  single-argument `Class.forName(name)` call, resolving against the caller's
  own loader — this pre-existing semantic is unchanged.
