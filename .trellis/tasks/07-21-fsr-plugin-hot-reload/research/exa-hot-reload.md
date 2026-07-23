# FSR Plugin Hot Reload Research

Research date: 2026-07-21. External discovery used Exa MCP; architectural conclusions were checked against the local FSR/NeoForge code before entering the plan.

## Executive Findings

1. **Reload FSR-owned plugins, not NeoForge mods.** NeoForge mod discovery, mod buses, lifecycle and registries are startup structures. FSR can keep one stable NeoForge outer integration and dynamically route its own event/network/resource/plugin callbacks underneath it.
2. **Class loading is the smaller half of the work.** Per-plugin classloaders and `close()` are necessary, but real unload also requires removing every plugin instance, callback, reflective `Method`, `Class`, executor, cache entry and resource handle from host-owned structures.
3. **WatchService is an intent source, not a truth source.** Events may be duplicated, reordered, overflow, or arrive before a file writer is finished. Full directory reconciliation plus stable content hashing is required.
4. **Use candidate-first, atomic replacement.** Prepare the new generation fully while the old one stays active, atomically change host routing, then drain and retire the old generation. Candidate failure before commit must leave the incumbent untouched.
5. **PF4J is a reasonable internal engine, not the whole solution.** It supplies mature per-plugin classloaders and lifecycle states. FSR must still provide owner-aware reversible registries, generation fences, thread dispatch, shadow copies and NeoForge compatibility.

## External Sources

### NeoForge lifecycle

- [NeoForge Events / Mod Lifecycle](https://docs.neoforged.net/docs/1.21.1/concepts/events)
  - A per-mod mod bus is created and lifecycle events are fired during startup.
  - Many lifecycle events are one-shot and parallel-dispatched.
  - Runtime game bus and startup mod bus have different purposes.
- [NeoForge Mod Files](https://docs.neoforged.net/docs/gettingstarted/modfiles)
  - `neoforge.mods.toml`, language loader, dependencies and `@Mod` entrypoints determine startup loading.

Implication: a hot plugin cannot add a new NeoForge mod container, replay mod lifecycle, register payload types, apply Mixins/ATs or safely withdraw Minecraft registry objects at runtime. FSR must register stable outer hooks once and route hot-plugin contributions through its own manager.

### Java file watching

- [Java 21 WatchService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchService.html)
  - `OVERFLOW` means events were discarded and the consumer should re-examine state.
  - modify notification does not guarantee the writer has finished.
  - event count, ordering, timing and even detection depend on the filesystem/platform.
  - closing the service wakes blocked consumers with `ClosedWatchServiceException`.

Implication: debounce alone is insufficient. Use stable fingerprints/content hashes, atomic shadow-copy commit, full reconciliation on overflow/invalid keys, and a manual rescan fallback.

### Java classloader close and Windows

- [Oracle: Closing a URLClassLoader](https://docs.oracle.com/javase/8/docs/technotes/guides/net/ClassLoader.html)
  - `URLClassLoader.close()` invalidates further loading and closes opened JAR files.
  - It makes predictable JAR replacement possible, especially on Windows.
  - Classes become unloadable only after application references to loader/classes/instances are cleared.

Implication: load a content-addressed shadow copy, close the loader during retirement, and treat host registry/resource cleanup as a hard lifecycle requirement. Closing the loader alone is not an unload implementation.

### PF4J

- [PF4J plugin lifecycle](https://pf4j.org/doc/plugin-lifecycle.html)
  - Defines CREATED/DISABLED/RESOLVED/STARTED/STOPPED and load/start/stop/unload controls.
  - Only STARTED plugins should contribute active extensions.
- [PF4J class loading](https://pf4j.org/doc/class-loading.html)
  - Uses one `PluginClassLoader` per plugin.
  - Supports configurable parent/plugin/dependency delegation.
- [Maven Central: org.pf4j:pf4j](https://central.sonatype.com/artifact/org.pf4j/pf4j)
  - 3.15.0 was current in the Exa result, Apache License 2.0, small dependency surface.
- [PF4J releases](https://github.com/pf4j/pf4j/releases)
  - Recent release history includes a Java 21 verification fix.
- [PF4J 3.15.0 `AbstractPluginManager`](https://github.com/pf4j/pf4j/blob/release-3.15.0/pf4j/src/main/java/org/pf4j/AbstractPluginManager.java)
  - The standard manager stores plugins and classloaders in maps keyed by plugin id and rejects a duplicate id in the same manager.

Implication: PF4J can only be adopted behind an internal FSR adapter if the target NeoForge/Java 21 spike proves **incumbent ACTIVE + same-id candidate PREPARING**. A single standard PF4J manager cannot provide that topology; use a separate PF4J container per generation or the internal loader fallback. Relocate/shade PF4J and never expose its classes in FSR's public SPI. FSR owns global id/dependency state and the transactional contribution layer.

## Local Repository Evidence

### Current lifecycle is one-way

- `new/src/main/java/mchorse/bbs_mod/addon/v2/BBSAddonManager.java`
  - Permanently retains accepted addons in `addons`.
  - Runs discover -> register common -> setup, with no unload/remove/reopen path.
  - Claims identity before insertion; the identity registry has no release operation.
- `new/src/main/java/mchorse/bbs_mod/addon/v2/BBSAddonRecord.java`
  - Holds final descriptor/addon/diagnostics; no source artifact, classloader or teardown ledger.
- `new/src/main/java/mchorse/bbs_mod/api/addon/BBSAddon.java`
  - Declares `unload(...)`, but no inspected manager path invokes it.
- `new/src/main/java/mchorse/bbs_mod/BBSMod.java`
  - Creates one final collector/manager pair, exposes static active references, closes registration during startup and never replaces/clears the pair.

### Current facades close admission, not contributions

- `BBSAddonCommonContext.close()` only closes `BBSAddonRegistrationWindow`.
- `BBSAddonRegistryAdapters` writes directly into shared providers/factories/maps and records no rollback token.
- Existing planning contract in `.trellis/tasks/07-17-addon-api-lifecycle/prd.md` freezes first-wins, phase windows, error isolation and API 2.0 compatibility.

### Strong references that block unload

- `events/EventBus.java` stores subscriber objects and reflective methods; no unregister.
- `resources/AssetProvider.java` stores source packs; no owner/remove.
- form/clip `MapFactory` stores plugin `Class` objects; active serialized/runtime objects make safe replacement undefined.
- `network/compat/AddonPayloadBroker.java` stores static receiver instances; no owner/remove.
- `client/compat/ClientApiCompat.java` stores permanent client callbacks and one-shot structural registrations.
- `client/render/surface/BBSRenderSurfaceRegistry.java` stores listeners without a public close path.
- UI Mirror and Film collaboration already have closeable subscription patterns and generation/session cleanup that can be generalized.

### Existing addons are not hot-safe as packaged

- `bbs-web-remote` and `bbs-context-hints` are independent NeoForge mods with `@Mod` bootstraps.
- Web Remote also installs direct NeoForge listeners, client callbacks, static singleton state, executors, network servers and optional native WebRTC resources outside an FSR plugin ledger.
- Therefore this plan does not label existing addon JARs hot-reloadable. Each needs an explicit migration/audit after the core runtime exists.

## Alternatives Considered

### Hot reload existing NeoForge mods

Rejected. It cannot reliably undo mod-bus lifecycle, registries, mixins, access transformers, payload registration, static listeners or classes already loaded by ModLauncher.

### Java Instrumentation / HotSwapAgent

Not selected for the product runtime. Class redefinition is useful for developer code patching but does not provide plugin artifact discovery, dependency/version policy, contribution ownership, stop/unload semantics or classloader replacement. It would also add JVM launch-agent requirements.

### Hand-written URLClassLoader manager only

Viable fallback but not recommended initially. A small loader looks simple, yet state, dependency resolution, close ordering, class delegation and lifecycle edge cases repeat mature PF4J functionality. The PF4J Phase 0 gate limits adoption risk.

### OSGi / JPMS dynamic layers

Not selected for MVP. They impose a substantially broader module/package model and do not remove the need to redesign FSR registries and plugin resource ownership.

## Decisions Carried Into Planning

- New FSR hot-plugin SPI; legacy Addon API 2.0 stays unchanged.
- PF4J per-generation internal container behind a same-id concurrent feasibility gate; never one standard manager for both generations.
- One classloader and monotonic generation per plugin version.
- Content-addressed shadow copies and current/previous retention.
- Owner-aware ledger and stable host proxy for every hot-safe capability.
- Prepare -> atomic commit -> drain/retire transaction.
- Separate common/client entrypoints; dedicated server never resolves the client entrypoint.
- Declarative host-owned resource/settings projections; no plugin `ISourcePack`/`BaseValue` retained by global managers.
- Logical teardown failures are deterministic; delayed GC is bounded `SUSPECTED_LEAK` telemetry only.
- Development auto reload; production manual by default.
- Restart-only classification for forms/clips/particles and all NeoForge/Minecraft structural contributions in MVP.
- Full-trust warning; no sandbox or remote code installation claims.
