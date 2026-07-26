# Design: FSR Connector input compatibility

## Boundaries

All production and test changes are confined to `new/`. Connector and IRLite are treated as
external compatibility fixtures. FSR must still run without Connector or Fabric Loader on the
classpath.

The task has two failure boundaries:

1. restore the original BBS lifecycle and `bbs-addon` contracts used by Fabric addons;
2. make pointer viewport state safe under synchronous nested dispatch.

The loader repair is implemented first because the target addon cannot reach its UI paths until
its required Mixin and custom entrypoint load successfully.

## Legacy BBS lifecycle contract

### Public hook

Restore `public void onInitialize()` on `BBSMod` as a compatibility hook. Its FSR body is empty;
Fabric addon Mixins may inject into it exactly as they do on native BBS.

FSR invokes the hook once during common setup after the core settings, form, camera-clip, and
action-clip registries exist, but before `RegisterSettingsEvent` is posted. This is the closest
safe equivalent to the tail of Fabric BBS initialization while still allowing an addon loaded
from `bbs-addon` to register settings.

The invocation is guarded against accidental repeats. No Fabric interface is added to FSR and
Connector remains optional.

### Optional Fabric addon discovery

Add a small loader-side adapter that uses reflection to access Fabric Loader only when it is
present. It queries `getEntrypointContainers("bbs-addon", BBSAddonMod.class)` and returns:

- the provider mod id;
- the `BBSAddonMod` instance;
- a diagnostic source label.

Each discovered entrypoint is imported through `BBSAddonCollector.registerExternal(...)` before
the collector bridge closes its external window. The existing identity registry remains the
single duplicate-owner authority, so a native NeoForge registration wins or loses by the same
first-registration rule as every other addon.

Absence of Fabric Loader is a normal no-op. Reflection, provider metadata, and individual
entrypoint failures are isolated and logged with BBS addon diagnostics; they must not make a
plain NeoForge FSR installation depend on Connector classes.

The existing `LoaderAccess#getEntrypoints` contract remains collector-backed. Fabric discovery
imports into the collector rather than returning an unbridged parallel list, avoiding the
current problem where `BBSMod` logs a lookup result without registering it on the internal bus.

## Pointer viewport isolation

### Event frames

Each click, release, scroll, and cancel dispatch receives an isolated `UIViewportStack` owned by
that event frame. A frame captures the previous stack and pointer fields. When a callback starts
a nested pointer dispatch, the nested frame cannot clear or pop the outer frame's viewport.

On nested-frame exit, the previous mouse coordinates, button/wheel values, shifts, and viewport
stack are restored in `finally`. A top-level frame preserves the historical externally visible
event coordinates while still discarding its transient viewport state after dispatch.

### Invariants

- Menu viewport push/pop is balanced inside each frame.
- Nested dispatch restores the exact outer viewport and pointer values.
- Exceptions restore the prior frame before propagating.
- A genuine same-frame extra pop remains an invariant failure; production code does not silently
  turn `pop()` on an empty stack into a no-op.
- Render and resize viewport behavior is unchanged by the pointer-only frame API.

The current `UIViewportInputCleanupTest`, which intentionally corrupts the stack and accepts the
result, is replaced with nested-dispatch, exception-restoration, and balanced-cleanup coverage.

## Compatibility and ordering

Expected common setup order:

1. import optional Fabric `bbs-addon` entrypoints into the collector;
2. run addon protocol diagnostics;
3. bridge the collector into the BBS event bus;
4. build core registries and run common addon registration;
5. invoke legacy `onInitialize()` once;
6. post `RegisterSettingsEvent` and continue common setup.

IRLite's `BBSModMixin` can then apply, its point/spotlight forms are registered by the injected
tail callback, and its `IrlightsAddon` subscriber receives the settings event.

## Failure handling

- Connector absent: discovery returns an empty result without warning-level noise.
- Fabric Loader present but no `bbs-addon`: log a zero-result debug/info summary.
- One broken Fabric entrypoint: record and log that provider failure, continue with others.
- Duplicate provider id: existing collector diagnostics decide ownership.
- Viewport imbalance: preserve the original exception and always restore the enclosing frame.

## Rollback

The loader adapter and lifecycle hook are additive and can be reverted independently from the
pointer-frame change. Pointer dispatch remains centralized in `UIBaseMenu`, so rollback does not
require touching individual widgets.
