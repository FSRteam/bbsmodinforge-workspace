# Connector + IRLite 1.21.1 compatibility findings

## Scope and sources

- FSR source: `new/`, BBS mod id `bbs`, version `0.0.10`, Minecraft 1.21.1.
- Connector source: `temp/Connector-1.21.x`, version `2.0.0-beta.16`, Minecraft 1.21.1.
- IRLite source: `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1`,
  branch tip recorded as `395203bb1f391ce60ec3e47ed906f6d4d5ac4a7b`.
- IRLite build profile targets Fabric Loader 0.19.3, Fabric API 0.116.12, Iris 1.8.8,
  and BBS 2.2.1 for Minecraft 1.21.1.

## Evidence separation

The supplied July 22 crash report is a valid BBS/FSR viewport failure, but it is not a
Connector + IRLite failure:

- neither Connector nor IRLite appears in the loaded mod list;
- no Connector crash-report banner is present;
- BBS reports zero registered addons;
- the fatal chain is `ArrayDeque.pop -> UIViewportStack.popViewport -> UIContext -> UIBaseMenu`.

The local launcher instances also do not currently provide the target combination. The
`1.21.1-NeoForge_21.1.223` instance has Connector and Forgified Fabric API disabled and
contains no IRLite jar. The `.209` and `.222` instances enable Connector but also contain
no IRLite jar. A controlled target-combination run is still required.

## Confirmed FSR compatibility gaps

### 1. Required Mixin target removed by the port

IRLite declares `irlite.mixins.json` as required and injects:

```java
@Mixin(BBSMod.class)
@Inject(method = "onInitialize", at = @At("TAIL"))
```

Fabric BBS defines public `BBSMod.onInitialize()`. FSR has no such method; its initialization
is split between the NeoForge constructor, `onConstructMod`, and `onCommonSetup`. Connector
can remap and transform a Mixin, but it cannot supply a target method removed by the port.
This is a deterministic FSR API/ABI incompatibility and should fail before IRLite UI input is
relevant.

### 2. Fabric `bbs-addon` entrypoints are no longer discovered

Fabric BBS obtains `bbs-addon` entrypoint containers from `FabricLoader` and registers each
entrypoint in the BBS event bus. IRLite relies on this custom entrypoint for
`IrlightsAddon`, which subscribes to `RegisterSettingsEvent`.

FSR replaces the loader with `NeoForgeLoaderAccess`. Its `getEntrypoints("bbs-addon", ...)`
returns only objects already present in `BBSAddonCollector`; it contains no optional Connector
or Fabric Loader lookup. Connector initializes Fabric Loader and exposes its entrypoint API,
but FSR never calls that API.

There is a second integration issue in `BBSMod.onCommonSetup`: the value returned from
`loader.getEntrypoints(...)` is only logged. `BBSAddonBridge` then registers the collector
snapshot, so merely returning Fabric entries from `NeoForgeLoaderAccess` would still not put
them on the internal BBS event bus.

The Fabric entrypoints must be imported into the collector before its external window is
closed and before `RegisterSettingsEvent` is posted. Provider mod ids should be preserved for
duplicate ownership and diagnostics.

### 3. Other checked BBS targets remain present

The following IRLite BBS-side Mixin targets were found in current FSR source with their main
target methods or fields intact:

- `ExtraFormSection.initiate`
- `FormUtilsClient.<clinit>`
- `L10n.<init>`
- `UIFilmController.subMouseClicked`, `subMouseReleased`, `renderPickingPreview`
- `UIFormEditor.<clinit>`
- `UIPickableFormRenderer.subMouseClicked`, `subMouseReleased`, `renderUserModel`
- `UIReplaysEditor.getColor`
- `UISettingsOverlayPanel.refresh`
- `Films.controllers`

Minecraft and Iris targets still require a transformed runtime check because they depend on
mapping and external-version details. The static audit does not prove those injections work.

## Viewport issue is independent

FSR currently wraps click, release, scroll, and cancel dispatch with:

1. reset shared context state;
2. push the menu viewport;
3. dispatch to the root;
4. reset shared context state in `finally`.

`UIViewportInputCleanupTest` deliberately lets a child pop an extra viewport and asserts only
that the event completes and the stack is empty. This prevents the reported final root pop,
but does not preserve an outer dispatch when a callback synchronously causes another input
dispatch. `UIContext.setMouse()` also resets the same shared stack, so nested input can erase
the outer scope and cause a later ancestor `unapply()` to underflow or use incorrect shifts.

The proper direction is event-scoped viewport state with save/restore or an isolated stack per
dispatch, plus `try/finally` restoration. An empty-pop guard or unconditional reset alone is
not an invariant-preserving fix.

## Preliminary ownership judgment

The two confirmed startup gaps belong to FSR's compatibility surface, not Connector's generic
Fabric transformation engine:

- IRLite targets API and entrypoint contracts present in native Fabric BBS;
- FSR removed one target symbol and stopped consuming Fabric custom entrypoints;
- Connector exposes Fabric Loader and cannot infer BBS-specific lifecycle semantics.

This remains preliminary until a real combined log confirms the first runtime failure. A later
Minecraft/Iris Mixin transformation error could add an IRLite- or Connector-specific issue,
but it would not invalidate the two FSR gaps above.

## Recommended implementation boundaries

1. Add an optional, reflection-based Fabric `bbs-addon` discovery adapter in FSR so Connector
   remains an optional runtime integration rather than a hard dependency.
2. Import discovered entrypoints into `BBSAddonCollector` before bridge closure, retaining
   provider ids and duplicate diagnostics.
3. Restore a public, once-invoked legacy `onInitialize()` compatibility hook at a lifecycle
   point where core registries exist and before addon settings registration is finalized.
4. Replace shared viewport reset-as-cleanup with event-scoped save/restore semantics and add
   nested input regression tests.
5. Validate with native NeoForge controls, a native Fabric control, and the full Connector +
   IRLite 1.21.1 combination.

## Remaining evidence gap

The downloaded IRLite source contains no `libs/bbs-2.2.1-1.21.1.jar` and no built IRLite jar.
Its build also expects a locally published `org.qualet:irl-core:1.1.3`. Reproduction therefore
needs either the user's actual built addon jar or a reproducible way to supply those build
inputs without changing the upstream source.
