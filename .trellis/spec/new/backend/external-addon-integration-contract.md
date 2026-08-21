# External NeoForge Addon Integration Contract

## 1. Scope / Trigger

Apply this contract when a **separate NeoForge mod** (its own JAR, its own `@Mod` class,
its own Gradle project) needs to extend BBSFSR — register forms, form renderers, editor
panels, icons, source packs, L10n, or timeline-track affordances.

Out of scope:

- Hot-reloadable plugins loaded from `config/bbs/plugins/` → see
  [Hot Plugin Runtime Contract](./hot-plugin-runtime-contract.md) (that document explicitly
  excludes NeoForge mod loading and startup-only Addon API).
- Code living inside BBSFSR's own source tree.

Verified against: `bbs-photon-addon/` (port of `ElGatoPro300/bbs-photon-addon@303df3a`,
which originally targeted Fabric-based BBS CML Edition via Sinytra Connector).

## 2. Signatures

- API 1.x registration: `BBSMod.registerAddon(String addonId, Supplier<? extends BBSAddonMod> supplier)`
  (`BBSMod.java:971`). `mchorse.bbs_mod.events.BBSAddonMod` is an empty marker interface;
  behaviour comes from `@Subscribe`-annotated methods bridged onto `BBSMod.events`.
- API 2.0 registration: `BBSMod.registerAddon(BBSAddonDescriptor, Supplier<? extends BBSAddon>)`
  (`:817`) / `BBSMod.registerAddon(Supplier<? extends BBSAddon>)` (`:826`).
- Single event bus: `BBSMod.events` (`public static final EventBus`, `BBSMod.java:165`).
  Client-side events are posted to the *same* bus — there is no separate client bus.
- Client compatibility facade: `mchorse.bbs_mod.api.client.BBSClientApi`.

## 3. Contracts

### 3.1 Registration must happen during mod construction

`BBSAddonCollector` closes its external registration window
(`closeExternalRegistrationWindow()`, `BBSAddonCollector.java:324`) after construction.
Registering later is silently rejected. `BBSAddonMod`'s own javadoc states the rule:

> External NeoForge mods should call `BBSMod.registerAddon(...)` during mod construction,
> because their own mod bus does not receive events posted on BBS' mod bus.

Corollary: an external mod must **not** wait for `FMLCommonSetupEvent` or any lifecycle
event to register itself.

### 3.2 Pick API 1.x unless you need Addon 2.0 capabilities

API 2.0's common `BBSAddonRegistrationContext` exposes only
`resources()/forms()/settings()/clips()/particles()/network()/events()`. Client-only
Dashboard content is registered separately through `BBSClientApi.registerDashboardPanel(...)`;
form renderers, form editor panels, standalone icons, and L10n still use the static entry
points regardless of which API generation you pick. So for an addon whose needs are
"register a form + render it + edit it", API 1.x plus the static entry points in §3.4 is
strictly less machinery.

Declare a `BBSAddonDescriptor` anyway when you touch `BBSClientApi` methods that take one.

### 3.3 One addonId per addon object

`BBSAddonCollector` de-duplicates by addonId (`BBSAddonIdentityRegistry`). If common-side
and client-side concerns are split into two `BBSAddonMod` classes, register them under
distinct ids (e.g. `<mod_id>` and `<mod_id>_client`). Both land on `BBSMod.events`, so a
client-side `@Subscribe` works from either object.

Keep client-only classes behind a `Dist.CLIENT` guard *and* behind a dedicated client
bootstrap class, so the common `@Mod` class never references a client-only type.

### 3.4 Capability → entry point

| Need | Entry point | Notes |
|---|---|---|
| Register a form type | `@Subscribe registerForms(RegisterFormsEvent e)` → `e.register(Link, Class)` | `RegisterFormsEvent` has a `register` convenience method; there is **no** `getForms()` accessor |
| Form renderer | `FormUtilsClient.register(Class, IFormRendererFactory)` (`:102`) | Plain static registry, no event. Paired `unregisterPluginRenderer` (`:112`) exists |
| Form editor panel | `UIFormEditor.register(Class, Supplier<UIForm>)` (`:147`) | Paired `unregisterPluginPanel` (`:157`) |
| Icon | `Icons.register(Icon)` (`Icons.java:260`) | Public static; `Icons.ICONS` map is also public |
| Source pack | `BBSMod.getProvider().register(new InternalAssetsSourcePack(MOD_ID, namespace, path, ownerClass))` | Use the four-argument constructor. The three-argument overload defaults `modId` to `bbs`, so packaged lookup can scan the core jar instead of the addon's jar. |
| L10n | `@Subscribe registerL10n(RegisterL10nEvent e)` captures `e.l10n`; `onClientStarted` registers the provider after the source pack | `Link.source` must equal the source-pack prefix; do not encode `assets/<namespace>` in the link path. Registering the provider during the earlier event makes the initial reload print missing-asset failures. |
| Add form to the "Extra" picker category | `BBSModClient.getFormCategories().addExtraForm(Form)` (`FormCategories.java:80`) | Purpose-built public API; also calls `markDirty()`. Paired `removeExtraForm` (`:94`) |
| Extra controls on a string keyframe editor | `UIStringKeyframeFactory.registerDecorator(IStringKeyframeDecorator)` (`:18`) | Receives `(factory, track)` where `track` is the sheet id; use `factory.addControl(UIElement)` and `factory.setStringValue(String)` |
| Dashboard panel | `BBSClientApi.registerDashboardPanel(BBSAddonDescriptor, BBSDashboardPanelSpec, BBSDashboardPanelFactory)` | Requires `CLIENT_UI`; host composes `<addonId>:<localId>`, owns the wrapper, and isolates factory/lifecycle failures. |
| Client lifecycle / tick hooks | `BBSClientApi.onClientStarted` (`:82`), `onStartClientTick` (`:97`), `onEndClientTick` (`:107`), `onClientStopping` (`:87`), `onDisconnect` (`:92`), `onEndWorldTick` (`:102`) | One-to-one replacements for the Fabric `ClientLifecycleEvents` / `ClientTickEvents` an addon ported from BBS CML will be using |
| Game directory | `BBSMod.getGameFolder()` (`:322`) | Prefer over `FMLPaths` — one less platform API in an addon that already depends on BBS |
| Timeline track colour / icon | Reflection on private `UIReplaysEditor.COLORS` / `ICONS` (`:86,87`) | **No public channel.** Degrade to default styling on failure; never propagate |

### 3.4.1 Packaged source-pack and L10n contract

#### 1. Scope / Trigger

Apply this contract when a separate addon serves BBS assets from its own jar, especially
translation files registered through `RegisterL10nEvent`.

#### 2. Signatures

```java
new InternalAssetsSourcePack(MOD_ID, SOURCE, "assets/" + MOD_ID, ClientBootstrap.class)
pendingL10n.registerOne((language) -> new Link(SOURCE, "strings/" + language + ".json"))
pendingL10n.reload()
```

#### 3. Contracts

- The first constructor argument is the owning addon mod id used to resolve the physical mod
  file. Never rely on the three-argument overload, which substitutes the BBS core mod id.
- The second argument is the logical source-pack prefix. Every `Link.source` served by this
  pack must equal that prefix.
- The third argument is the classpath/jar root. Keep the namespace there, not in `Link.path`.
- The L10n event captures the target `L10n` instance but does not register the provider yet.
  In `onClientStarted`, register the pack, register the provider, then reload L10n in that order.
- Guard pack registration against repeated lifecycle delivery, while L10n provider registration
  may use identity de-duplication.
- `registerOne` promises one link for *every* language. An addon that ships a subset of the
  languages BBS supports must use `register(Function<String, List<Link>>)` and return an empty
  list for the languages it does not carry. `L10n.reload(...)` loads `DEFAULT_LANGUAGE` first and
  then the active language, printing `Failed to load <link> language file!` plus a stack trace for
  every link it cannot resolve — a French client should never see an addon's language files in that
  output. Returning nothing is safe: `en_us` is always loaded first and remains the fallback layer.
- A missing translation key never throws. `getKey(key)` seeds `content` with the key itself and
  `getKey(key, fallback)` seeds it with the fallback, and `LangKey.get()` returns `content`. The
  observable defect is therefore silent: an English reader sees the addon's fallback language, and
  a `FormatKey` whose specifier count no longer matches the arguments catches the exception and
  renders the raw key. Neither is visible to javac or to a "does the key resolve" smoke check, so
  the key set the code requests must be verified against the shipped language files (see §6).

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Three-argument pack constructor used by an addon | Reject in review; it can pass from expanded development resources but scan the BBS jar after packaging. |
| `Link.source` differs from the pack prefix | Treat `Asset <source>:<path> couldn't be found` as a namespace defect. |
| `Link.path` starts with `assets/<namespace>/` | Remove that prefix; the pack's internal root already owns it. |
| L10n event fires before the pack exists | Capture the `L10n` instance only; defer provider registration until after pack registration. |
| Client lifecycle callback runs again | Do not register a duplicate source pack. |
| Addon ships fewer languages than BBS supports | Use `register(...)` and answer with an empty list for the rest; do not let `registerOne` fabricate links to files that are not in the jar. |
| A language file omits a key the code requests | Treat it as a defect even though nothing throws; the reader silently gets the fallback language. |
| A language file's `%s` count differs from the arguments the code passes | Treat it as a defect; `FormatKey` swallows the exception and renders the raw key. |
| A language file declares a key nothing reads | Remove it; it is dead weight that no runtime check will ever surface. |

#### 5. Good / Base / Bad Cases

- Good: addon id `bbs_snow`, source `bbs_snow`, root `assets/bbs_snow`, and link
  `bbs_snow:strings/en_us.json` resolve in development and from the packaged addon jar.
- Base: the earlier L10n event captures the registry instance; client-started registration then
  activates the pack and provider before one explicit reload.
- Bad: use source `assets` with path `bbs_snow/strings/en_us.json`, or omit the addon mod id
  from the pack constructor because an expanded development resource directory happened to work.
- Bad: call `registerOne` during `RegisterL10nEvent`; the initial language reload runs before
  the addon source pack exists and prints missing-asset failures even if a later reload succeeds.

#### 6. Tests Required

- Inspect the addon jar for `assets/<mod-id>/strings/<language>.json`.
- Run the client and assert its complete console and logs contain no missing addon translation asset.
- Assert at least one addon translation key exists in `L10n.getStrings()` and resolves to a
  value other than the bare key.
- Assert the key set the code requests against every shipped language file, without loading BBS
  classes: each requested key is present and non-empty, its `%s` count equals what the code passes,
  and no language file declares a key nothing reads. Derive the language list from the addon's own
  authoritative declaration rather than restating it in the test, so a language added to the
  provider without a file fails the check instead of being skipped.
- Prove the check has teeth: delete one key and change one format string's specifier count, confirm
  the check fails, then restore. A key-contract check that was never seen failing is not evidence.

#### 7. Wrong vs Correct

Wrong:

```java
new InternalAssetsSourcePack(NS, "assets/" + NS, ClientBootstrap.class);
l10n.registerOne((language) -> new Link("assets", NS + "/strings/" + language + ".json"));
```

Correct:

```java
new InternalAssetsSourcePack(MOD_ID, NS, "assets/" + NS, ClientBootstrap.class);
l10n.registerOne((language) -> new Link(NS, "strings/" + language + ".json"));
l10n.reload();
```

An addon that ships only a subset of languages answers per language instead:

```java
static final Set<String> TRANSLATED_LANGUAGES = Set.of("zh_cn", "en_us");

l10n.register((language) -> TRANSLATED_LANGUAGES.contains(language)
    ? Collections.singletonList(new Link(NS, "strings/" + language + ".json"))
    : Collections.emptyList());
l10n.reload();
```

Anything registered after `BBSResources.init()` rebuilds form categories gets wiped, so
source packs and `addExtraForm` belong in an `onClientStarted` callback. Renderer, panel,
icon and decorator registration are plain static maps and can run during construction.

### 3.4.2 Addon Dashboard panel contract

- Declare `BBSAddonCapability.CLIENT_UI` and call
  `BBSClientApi.registerDashboardPanel(...)` during the addon client bootstrap.
- `BBSDashboardPanelSpec.id()` is owner-local and matches
  `[a-z0-9][a-z0-9_.-]{0,63}`. Never pass a namespaced/full id; the host composes
  `<addonId>:<localId>` and applies first-wins.
- The factory returns `BBSDashboardPanelContent` with one non-null, unattached
  `UIElement` root. The host owns the `UIDashboardPanel` wrapper and mounts the
  root full-size without exposing internal Dashboard types.
- Client-thread callback order is
  `open -> appear -> update* -> disappear -> close`. A Dashboard is reused
  across screen sessions, so session resources belong in `open/close` and
  selection resources in `appear/disappear`.
- Rejected registration is recorded at `REGISTER_CLIENT`; factory or lifecycle
  failure is recorded at `CLIENT_SETUP` and isolated. Addon registrations are
  startup-only and have no unload facade.
- The legacy `RegisterDashboardPanelsEvent` remains available and is still
  emitted after host-owned API contributions are projected.
- The content root must not extend a host panel class. The host wraps the root in its own
  `UIExtensionDashboardPanel` and refuses a root that already has a parent, so
  `extends UIElement implements BBSDashboardPanelContent` is the only shape that works.
  Extending `UIDashboardPanel` compiles and then fails at registration.
- A `BBSDashboardPanelSpec.title(...)` key is read on every tooltip draw, and the spec is
  normally built during addon construction — before `BBSModClient.getL10n()` exists. Pass a
  deferred `IKey` that resolves on each `get()`; a key captured at construction either NPEs or
  pins the language that was active when the Dashboard was first built. The same applies to any
  panel text that stays mounted while the reader changes language or theme.
- Registration is best-effort from the addon's side: log `BBSRegistrationResult` and continue.
  A rejected panel costs the addon its entry and must not take the Dashboard down.
- Nothing about this chain is checked by javac when the client entry point is reached by name
  through reflection (the usual shape for keeping client classes off a dedicated server). A
  renamed entry class, a non-static `initialize`, an illegal local panel id, or a descriptor the
  host rejects all present identically in-game: the panel is simply absent. Pin the entry-point
  name, method signature, local-id pattern, and descriptor fields in a check that reads source or
  bytecode without loading BBS classes.

### 3.5 Do not extend a registration surface by overwriting it

When BBSFSR offers a decorator/extension hook, use it instead of replacing a registry
entry. `UIKeyframeFactory.FACTORIES` is `private static final` (`:34`) precisely so addons
stop swapping entries out; `register(...)` (`:74`) writes but cannot read, so a
"delegate back to the previous factory" pattern is not expressible. The decorator hook in
§3.4 covers the real use case without touching BBS' own registration.

### 3.6 `neoforge.mods.toml` dependency on `bbs`

```toml
[[dependencies.<your_mod_id>]]
modId="bbs"
type="required"
versionRange="[0.0.10,)"   # BBSFSR declares version="0.0.10"
ordering="AFTER"           # BBS must construct first — see §3.1
side="BOTH"
```

**Pitfall:** an addon written for BBS CML Edition will carry `versionRange="[1.0.0,)"`
(CML used 1.10.x). BBSFSR declares `version="0.0.10"`, which that range **rejects** — the
game fails to load with no compile-time signal whatsoever. Always check the range against
BBSFSR's actual `mod_version`.

Do not declare a `connector` dependency, and do not ship `fabric.mod.json`: BBSFSR is
native NeoForge and needs no Fabric compatibility layer.

## 4. Build setup

- Depend on BBSFSR as a **local jar** — it is not published to any remote Maven:
  `compileOnly files(project.bbs_fsr_jar)` with `bbs_fsr_jar` a `gradle.properties`
  property. `compileOnly` keeps ~8 MB of BBS out of the addon jar; verify with
  `unzip -l build/libs/*.jar | grep mchorse/bbs_mod` returning nothing.
- BBSFSR ships a fat jar containing both its `main` and `client` source sets, so a
  single-sourceSet addon can compile against client-only BBS classes.
- Use **`net.neoforged.gradle.userdev` (NeoGradle)**, the same plugin and version BBSFSR
  uses, not `net.neoforged.moddev`. Reason: moddev's NeoForm runtime downloads its tool
  chain (e.g. `net.neoforged.jst:jst-cli-bundle`, pinned to an exact version) over its own
  HTTP client, bypassing Gradle repositories — so neither a mirror nor the shared
  dependency cache can serve it. NeoGradle resolves NeoForge as an ordinary Maven
  dependency, which means it reuses whatever BBSFSR already pulled.
- Put `https://bmclapi2.bangbang93.com/maven` first in both `repositories` and
  `pluginManagement.repositories`, matching BBSFSR. `maven.neoforged.net` is reachable
  from this workspace but throttled to ~20 KB/s, which surfaces as opaque build stalls.
- NeoGradle requires an explicit toolchain or configuration fails outright:
  `java { toolchain.languageVersion = JavaLanguageVersion.of(java_version) }`.
- Align `neo_version` with BBSFSR's value so the addon and BBS never disagree at runtime,
  and so the artifact cache is shared.

**Debugging stalled NeoForge builds:** default Gradle output hides the cause entirely. A
build sitting on one task for tens of minutes needs `--info` before any other diagnosis;
without it, a throttled download is indistinguishable from a hang.

## 5. Wrong vs Correct

### Wrong — registering after construction, and overwriting a registry

```java
public MyAddon(IEventBus modEventBus)
{
    modEventBus.addListener(this::commonSetup);          // too late: window is closed
}

private void commonSetup(FMLCommonSetupEvent event)
{
    BBSMod.registerAddon("my_addon", MyAddonMod::new);   // silently rejected
}

// elsewhere: replacing BBS' string keyframe factory wholesale
UIKeyframeFactory.register(KeyframeFactories.STRING, (kf, editor) ->
    isMine(editor, kf) ? new MyFactory(kf, editor) : null);   // null → blank editor for
                                                              // every other string track
```

### Correct

```java
public MyAddon(IEventBus modEventBus)
{
    BBSMod.registerAddon(MOD_ID, MyAddonMod::new);

    if (FMLEnvironment.dist == Dist.CLIENT)
    {
        MyAddonClient.init();                             // client-only types stay behind
    }                                                     // this one reference
}

// MyAddonClient.init(), still inside mod construction:
BBSMod.registerAddon(MOD_ID + "_client", MyAddonClientMod::new);
FormUtilsClient.register(MyForm.class, MyFormRenderer::new);
UIFormEditor.register(MyForm.class, UIMyForm::new);
Icons.register(new Icon(MY_TEXTURE, "mymod:thing", 0, 0, 40, 40, 40, 40));

UIStringKeyframeFactory.registerDecorator((factory, track) ->
{
    if (MY_TRACK.equals(track))
    {
        factory.addControl(new UIButton(label, (b) -> pick(factory)));
    }
});                                                       // BBS' own registration untouched

BBSClientApi.onClientStarted((client) ->
{
    BBSMod.getProvider().register(new InternalAssetsSourcePack(MOD_ID, NS, "assets/" + NS, MyAddonClient.class));
    l10n.registerOne((lang) -> new Link(NS, "strings/" + lang + ".json"));
    l10n.reload();                                        // pack and provider now agree
    BBSModClient.getFormCategories().addExtraForm(new MyForm());
});
```

## 6. Migrating an addon written for BBS CML Edition

CML-era addons compile against the Fabric BBS API. Package names are overwhelmingly
identical in BBSFSR — in the verified port, 40 of 46 referenced BBS types needed no change
at all. Concentrate on:

1. `mchorse.bbs_mod.addons.BBSAddon` / `BBSClientAddon` / `AddonInfo` — **do not exist**.
   Use `events.BBSAddonMod` (§2). `AddonInfo` has no equivalent: BBSFSR has no addon
   listing panel, so drop that registration entirely.
2. `mchorse.bbs_mod.BBS` — **does not exist**. `BBSMod.events` replaces `BBS.getEvents()`;
   `BBSMod.getForms()` replaces `BBS.getForms()`.
3. `RegisterFormsRenderersEvent` / `RegisterIconsEvent` — **do not exist**. Use the static
   entry points in §3.4.
4. All `net.fabricmc.*` usage — replace via `BBSClientApi` and `BBSMod.getGameFolder()`.
5. Remove the whole Sinytra chain from the build (§4) and the `connector` dependency plus
   `fabric.mod.json` (§3.6).
6. Re-check the `bbs` `versionRange` (§3.6) — this one breaks at runtime, not compile time.
7. Delete CML-era defensive duplication. Addons often register the same form two or three
   times (construction, `commonSetup`, and a `CLIENT_STARTED` "fallback") because event
   delivery under Connector was unreliable. On BBSFSR delivery is deterministic; keep one
   path.

Note that `renderInUI` / `render3D` are `protected` on `FormRenderer` (`:103`, `:315`)
while CML-era code overrides them as `public` — widening access on override is legal Java,
so this needs no change.

Also verify **reflective** field access separately: it compiles fine and fails at runtime.
In the verified port, `FormRenderingContext.ui` / `stack` / `type` (`:31`, `:22`, `:18`)
all kept their names, but that is a fact to check, not to assume.

## 7. Mixin-bearing addon contract

### 7.1 Scope / Trigger

Apply this section when a separate NeoForge addon declares Mixins that target
`mchorse.bbs_mod.*` classes. This was verified by `bbs-snow-addon` against BBSFSR 0.0.10;
the workspace's other standalone addons do not exercise this path.

### 7.2 Signatures

- Declare each config through `[[mixins]]` in `META-INF/neoforge.mods.toml`.
- Use `compatibilityLevel: "JAVA_21"` and `injectors.defaultRequire: 1`.
- Omit `refmap`: BBS targets are addon classes under Mojmap and are not obfuscated.
- Put optional third-party integrations in a dedicated config with
  `requiredMods = ["<mod-id>"]`.
- Prefix members added to BBS classes with the addon's namespace, for example
  `bbs_snow$probe`.

### 7.3 Contracts

- A package claimed by a Mixin config contains Mixins only. Loggers, constants, state
  holders, and other helpers live outside every claimed package.
- A conditional Mixin must not override a target method and use Java `super` as the
  fallback. After merge, `super.method()` calls the target's superclass, not the original
  target implementation. Inject at `HEAD`, set/cancel the return only for the addon-owned
  case, and otherwise let the target method continue.
- `defaultRequire: 1` remains enabled in every config. Individual optional injections may
  weaken `require` only when their target mod is deliberately optional and the Mixin is
  also isolated with `@Pseudo` or a gated config.
- NeoGradle compilation does not prove an injection method exists. Every batch of ported
  Mixins must run the client and exercise the target code path.
- A stale BBS core jar is not a valid verification input. `verifyBbsCoreJar` must reject a
  jar older than the current BBS Java sources before compile or run tasks proceed.
- Treat the BBS runtime jar as an immutable publication for the lifetime of every client that
  opened it. Never rebuild or replace `runs/<name>/mods/BBS_*.jar` while a client from that run
  directory is alive; `SecureJar`/`ModuleClassLoader` may keep the startup index and resolve
  lazily loaded classes from the replaced file later.
- Separate Gradle invocations are separate writers even when `org.gradle.parallel=false` and
  `--max-workers=1`. Do not overlap `compileClientJava --rerun-tasks`, `jar`, or another task
  that writes the same `new/build` tree. Build main, client, and the runtime jar in one serialized
  invocation, then audit and copy the finished artifact.
- A strict-client verifier that requests automatic exit must stop the client in a `finally`
  boundary. A failed assertion must not leave an orphan client holding the runtime jar while the
  next build starts.
- A successful apply message is necessary but not sufficient for lazy paths. Trigger the UI,
  renderer, or event that executes the injected method and capture its observable evidence.
- Treat a host method's invocation shape as part of every external Mixin contract. Adding one
  child to a layout can change Java overload selection (for example, `add(IUIElement)` to
  `add(IUIElement[])`) even though the visible behavior still looks equivalent. When wide and
  narrow branches share one semantic boundary, inject after that shared descriptor and exercise
  both branches; do not maintain one redirect per current child count.
- Any BBS change to a method targeted by a workspace addon must propagate to that addon in the
  same verification session: rebuild one fresh core jar, compile the addon against it, inspect
  that the strict target descriptor still exists, then run the client and execute the target UI.
- NeoForge parses every declared Mixin config on a dedicated server before it filters client-only
  entries. A companion `IMixinConfigPlugin` named by a client config must therefore be common-safe
  and compiled into `src/main`; it may gate optional targets, but it must not reference Minecraft
  client classes or exist only in `src/client`.

### 7.4 Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Required injection method no longer exists | Client startup fails with a critical injection error; never silently skip. |
| Ordinary helper class lives under a claimed Mixin package | Treat `IllegalClassLoadError` on first use as a package-layout defect; move the helper outside the package. |
| Conditional behavior does not apply to this target instance | Return from the injection callback without cancelling; the original target method must run. |
| Optional integration mod is absent | FML skips its gated Mixin config without affecting the addon. |
| BBS Java source is newer than the local core jar | Build fails with the exact jar path and rebuild command. |
| Multiple Gradle invocations target the same BBS `build/` tree | Wait for every writer to finish, then rerun one serialized main/client/jar build; do not trust intermediate classes or jars. |
| A client is still alive when the core jar would be copied or rebuilt | Stop the client first. Never hot-replace the jar and continue using that JVM. |
| A host UI edit changes an invocation overload, descriptor, or branch cardinality | Search every workspace addon Mixin targeting that method, update the semantic injection boundary, and verify every reachable layout branch with strict `require` still enabled. |
| A long-lived client reports several unrelated BBS `ClassNotFoundException`s after its jar changed on disk | Treat it as artifact lifecycle corruption, restart from an audited stable jar, and do not patch the reported classes individually. |
| Config omits `refmap` and targets only BBS classes | This is the expected working state, not a missing-artifact warning. |
| Dedicated server reports `ClassNotFoundException` for a client Mixin plugin | Move the plugin implementation to the common source set and keep its logic client-API-free; do not remove the client config from the manifest. |
| Build passes but no client run was performed | Verification is incomplete because injection target lookup has not run. |
| `runClient` stops before Mixin config selection with `Failed to locate a primary monitor` | Treat it as a NeoForge early-window environment failure. Temporarily set `runs/client/config/fml.toml` `earlyWindowControl = false`, retry the same verification command, and restore the local value afterward. Do not weaken Mixin checks or edit addon/BBS source. |

### 7.5 Good / Base / Bad Cases

- Good: `runClient` logs config selection and Mixin application, then exercising the BBS
  dashboard logs the probe or feature behavior.
- Good: a particle-only list sorter injects at `UIDataPathList.sortElements` and sets the
  callback return only when the list uses the particle icon; file/model lists retain BBS sorting.
- Base: Iris is absent, so the Iris-only config is skipped while common and client configs
  still apply.
- Good: the client Mixin config names a common-safe plugin in `src/main`; a dedicated server parses
  the config without loading client classes, while the client applies the gated Physics Mod target.
- Base: the early splash cannot resolve a monitor, but disabling only `earlyWindowControl`
  lets the real client window load, the verifier pass, and the local setting be restored.
- Bad: accept `./gradlew build` as proof that 76 migrated injection signatures are valid.
- Bad: record a pre-Mixin early-window failure as an addon regression or lower injection
  requirements to make an unrelated environment failure disappear.
- Bad: place a logger next to a Mixin because both are client-only; Mixin owns the whole
  package regardless of Java-side intent.
- Bad: keep the companion plugin in `src/client` because every listed Mixin is client-only; the
  dedicated server still loads the plugin class while reading `neoforge.mods.toml`.
- Bad: override `sortElements()`, branch on an addon marker, then call `super.sortElements()`
  for ordinary instances; that reaches `UIList`, bypassing `UIDataPathList` sorting.

### 7.6 Tests Required

- Run `./gradlew build` without piping output and verify its real exit code.
- Before rebuilding or installing the core jar, assert that no client from the target run directory
  and no other Gradle invocation writing `new/build` is active. Run main/client compilation plus
  `jar` in one invocation, then verify representative main/client class entries and matching hashes
  for the source jar and installed copy.
- Inspect the addon jar for `neoforge.mods.toml` and every declared Mixin config; confirm it
  does not bundle `mchorse/bbs_mod/**`.
- Run `./gradlew runClient`, confirm BBS and the addon load, and exercise every newly ported
  injection path.
- Force one strict-verifier assertion to fail with automatic exit enabled; confirm the client still
  stops and no game JVM survives before restoring the assertion.
- If the log fails before Mixin config selection with the primary-monitor message, retry once
  with only local `earlyWindowControl = false`; require the normal runtime assertions and restore
  the setting after the run.
- Temporarily point one required probe at a nonexistent method; assert a runtime FATAL, then
  restore it and rebuild.
- Search addon sources for `net.fabricmc` and confirm the result is empty.
- Confirm the addon task did not modify `new/`.
- For every conditional target-method enhancement, exercise both the addon-owned instance and
  one ordinary instance; assert the ordinary instance retains the target class's ordering/state.

### 7.7 Wrong vs Correct

Wrong:

```java
package mchorse.bbs_snow.mixin.client;

final class ProbeLogger // ordinary class inside a package owned by a Mixin config
{
}
```

Correct:

```java
@Mixin(UIBaseMenu.class)
public abstract class UIBaseMenuProbeMixin
{
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbs_snow$probe(CallbackInfo ci)
    {
        BBSSnowMod.LOGGER.info("[bbs_snow] probe applied"); // helper owner is outside .mixin
    }
}
```

Wrong conditional fallback:

```java
@Override
protected boolean sortElements()
{
    return isParticleList() ? sortParticles() : super.sortElements();
}
```

Correct conditional injection:

```java
@Inject(method = "sortElements", at = @At("HEAD"), cancellable = true)
private void sortParticles(CallbackInfoReturnable<Boolean> cir)
{
    if (!isParticleList()) return;

    sortParticlePaths();
    cir.setReturnValue(true);
}
```

## 8. Owner-aware UI verifier fixture contract

### 8.1 Scope / Trigger

Apply when an addon runtime verifier directly constructs a BBS keyframe factory whose
production constructor resolves an owning Form from `UIKeyframeSheet.property`.

### 8.2 Signatures

- `new UIKeyframeSheet(int color, boolean separator, KeyframeChannel channel, BaseValueBasic property)`
- `FormUtils.getForm(BaseValue property)` follows the property's real parent chain.
- `UIKeyframeSheet.form(Form)` stores UI metadata but does not replace that value parent chain.

### 8.3 Contracts

- The fixture sheet must carry the same concrete Form-owned `BaseValueBasic` that owns the
  channel in production, such as `modelForm.pose` for `UIPoseKeyframeFactory`.
- Add the selected keyframe to that sheet and add the sheet to the `UIKeyframes` graph before
  constructing the factory.
- Keep the generic null-property helper only for factories that do not inspect an owner.

### 8.4 Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Owner-aware factory receives a null sheet property | Treat the resulting `FormUtils.getForm` failure as an invalid verifier fixture, not a product crash. |
| Sheet uses a property from a different Form | Fail the fixture; it does not exercise the intended production ownership path. |
| Factory never reads `sheet.property` | A null-property generic fixture remains valid. |

### 8.5 Good / Base / Bad Cases

- Good: pose channel + pose keyframe + `modelForm.pose` are mounted in one sheet before the
  pose factory is constructed.
- Base: a primitive factory that never resolves a Form uses the existing generic helper.
- Bad: pass `null` as the property and call `.form(modelForm)`; the visible Form metadata does
  not make `FormUtils.getForm(sheet.property)` safe.

### 8.6 Tests Required

- Run the strict client with runtime verification and automatic exit enabled.
- Require the verifier's final pass marker in `latest.log`, not only Gradle exit status.
- Assert no `client-started handler failed` or owner-resolution exception precedes the marker.

### 8.7 Wrong vs Correct

Wrong:

```java
UIKeyframeSheet sheet = new UIKeyframeSheet(0, false, poseChannel, null).form(modelForm);
```

Correct:

```java
UIKeyframeSheet sheet = new UIKeyframeSheet(0, false, poseChannel, modelForm.pose);
sheet.selection.add(poseKeyframe);
poseKeyframes.addSheet(sheet);
```

## 9. Client-started versus server-connected network timing

### 9.1 Scope / Trigger

Apply when an external addon registers client broker receivers during startup and sends an
initial handshake, settings state, or another C2S payload after joining a server.

### 9.2 Signatures

- Startup-only initialization: `BBSClientApi.onClientStarted(Consumer<Minecraft>)`.
- Play-connection event: `ClientPlayerNetworkEvent.LoggingIn` on `NeoForge.EVENT_BUS`.
- Transport entry: `BBSClientApi.sendNetworkToServer(BBSAddonDescriptor, ResourceLocation, FriendlyByteBuf)`.
- Active connection check: `Minecraft.getInstance().getConnection() != null`.

### 9.3 Contracts

- `onClientStarted` means BBS client resources and callbacks are ready. It also fires at the
  main menu, where no server connection exists, so it must not send C2S packets.
- Register receivers and non-network client state during construction/startup, but send the
  first hello/color/session payload from `ClientPlayerNetworkEvent.LoggingIn`.
- Every addon-owned C2S helper must fail closed before encoding/sending when the Minecraft
  connection is absent. This also protects settings callbacks at the title screen and late
  callbacks during disconnect.
- Protocol `connected` state remains false until the server acknowledges the addon handshake.
  Disconnect and client stopping clear that state and all session-owned data idempotently.

### 9.4 Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Main menu invokes `onClientStarted` | Register resources/UI only; send no C2S payload and log no handler failure. |
| A setting callback fires with no connection | Return `false`/no-op before calling `PacketDistributor.sendToServer`. |
| `LoggingIn` fires with an active play connection | Send one addon hello and initial client state. |
| Server acknowledgment has not arrived | Keep collaboration features disconnected; do not treat local registration as a negotiated session. |
| Disconnect or client stop | Clear connection, Film session, locks, transfers, and progress state; reconnect starts with a fresh hello. |

### 9.5 Good / Base / Bad Cases

- Good: translations activate at the main menu; joining a server fires `LoggingIn`, sends the
  hello, and the server reply enables collaboration.
- Base: the player stays at the main menu, so the addon remains registered but disconnected
  without warnings or exceptions.
- Bad: call `sendNetworkToServer` from `onClientStarted`; NeoForge's
  `PacketDistributor.sendToServer` dereferences a missing connection and aborts the remaining
  startup handlers.

### 9.6 Tests Required

- Start a development client to the main menu and assert no `client-started handler failed`,
  `NullPointerException`, or addon C2S send appears.
- Join a local or dedicated server and assert exactly one hello is sent after `LoggingIn`, then
  assert server acknowledgment changes the addon connection state.
- Disconnect and reconnect; assert cleanup runs and the next connection performs a fresh
  handshake without retaining Film sessions, locks, or transfers.
- Fire an addon settings callback at the title screen and assert the guarded sender returns
  without invoking the broker transport.

### 9.7 Wrong vs Correct

Wrong:

```java
BBSClientApi.onClientStarted(client -> sendHello());
```

Correct:

```java
BBSClientApi.onClientStarted(client -> activateClientResources());
NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> sendHello());

private static boolean send(ResourceLocation id, FriendlyByteBuf payload)
{
    if (Minecraft.getInstance().getConnection() == null)
    {
        return false;
    }

    return BBSClientApi.sendNetworkToServer(DESCRIPTOR, id, payload);
}
```

## 10. Version-port target verification and semantic-boundary migration

### 10.1 Scope / Trigger

Apply when porting a Mixin-bearing external addon across BBSFSR versions where
host methods, package paths, or the ownership of a behavior have changed.

### 10.2 Signatures

- A changed transform hotkey entry point may be `UIPropTransform.enableMode(TransformOp)`;
  do not assume the old integer overload still exists.
- `ValueChangeUndo` is currently
  `mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo`.
- Every BBS target uses `@Mixin(value = Target.class, remap = false)` and every
  accessor/invoker uses an explicit `remap = false`.
- The addon verifier must load every unique BBS target class and emit one final
  `runtime verification passed` marker.

### 10.3 Contracts

- Treat a missing old method as an API-boundary change, not as an optional injection:
  find the new method that owns the same behavior and keep strict injection requirements.
- Remove an old injection only when the current host owns the same behavior at a
  documented boundary; record that evidence in the migration design.
- A verifier target string must match the runtime class's fully qualified name. A
  wrong package is a failed fixture and must not be hidden by a caught exception.
- Runtime verification must run after BBS client startup and stop the client in a
  `finally` block when automatic exit is enabled.

### 10.4 Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Old target method is absent | Adapt to the current semantic boundary or fail startup; never lower `require`. |
| Host behavior moved into a strategy/helper | Inject at the public entry point only when that preserves the old addon behavior; otherwise document native coverage and remove the obsolete hook. |
| Verifier target package is stale | Correct the target string and require the class to load; do not catch `ClassNotFoundException` as success. |
| Compile succeeds but no client run occurs | Treat verification as incomplete because Mixin target resolution has not executed. |
| Runtime verifier reports a handler failure | Fix the target/configuration and require the final pass marker before completion. |

### 10.5 Good / Base / Bad Cases

- Good: port `enableMode(I)V` to `enableMode(TransformOp)` after checking the
  current source, and verify the transformed class at runtime.
- Base: a removed `useRayDrag()` hook is covered by the new drag-strategy ray
  gate, while PoseCurve's translation hotkey remains intercepted explicitly.
- Bad: keep a stale `useRayDrag` injection, change `require` to zero, or list a
  non-existent `mchorse.bbs_mod.utils.undo.ValueChangeUndo` verifier target.

### 10.6 Tests Required

- Run `testPoseCurveLogic` and a full `build` against the fresh local BBSFSR jar.
- Run `runClient -Pverify=1` and assert the final pass marker, no addon
  `client-started handler failed`, and no critical injection failure.
- Audit the addon jar for `neoforge.mods.toml`, every declared Mixin config,
  no `fabric.mod.json`, and no bundled `mchorse/bbs_mod/**` classes.
- Search sources for Fabric/intermediary names and stale target descriptors.

### 10.7 Wrong vs Correct

Wrong:

```java
@Inject(method = "useRayDrag", require = 0, at = @At("HEAD"))
```

Correct:

```java
@Inject(
    method = "enableMode(Lmchorse/bbs_mod/ui/framework/elements/input/drag/TransformOp;)V",
    at = @At("HEAD"),
    cancellable = true
)
```

## 11. Host shader ownership and effect-state isolation

### 11.1 Scope / Trigger

Apply when an external addon adds a model effect by replacing a BBS shader supplier,
copying a BBS core shader, or publishing resources under `assets/bbs/shaders/core/`.

### 11.2 Contracts

- Normal BBS rendering remains owned by the host. An addon must not unconditionally replace
  `BBSShaders.getModel()` or another host shader supplier for previews, picker passes, world
  rendering, and unrelated forms.
- Prefer effect inputs that survive the active rendering pipeline, such as bounded texture,
  material, mesh, or framebuffer state. If one such path works with both the BBS shader and
  Iris/vanilla entity shaders, use it everywhere and remove the duplicate custom shader path.
- When a custom shader is unavoidable, select it only inside the explicit effect render scope.
  Restore the host program and every static effect flag in `finally`; deferred draw suppliers
  must capture the intended program instead of reading an effect flag after the scope ends.
- A copied host shader is a versioned external contract. For every target BBSFSR update, compare
  its attributes, samplers, uniforms, imported helpers, fog-distance semantics, alpha/discard
  rules, and pass-control branches such as `PassMode` before accepting the addon build.
- Keep addon programs uniquely named. Never shadow the host's `model.json`, `model.vsh`, or
  `model.fsh` in-place to obtain load-order-dependent behavior.

### 11.3 Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Effect is inactive | Return the exact host shader and leave host uniforms/state untouched. |
| Addon shader copy differs from the current host contract | Update and exercise every render context, or remove the copy in favor of a pipeline-independent effect. |
| UI preview is invisible while other forms render | Compare UI fog-distance semantics and global host-shader interception before changing model data or textures. |
| Host adds a uniform or render pass | Treat the addon copy as incompatible until the control flow and deferred paths are audited. |
| Iris bypasses the addon shader | Move the effect to shared texture/material/mesh/framebuffer state; do not maintain two visually divergent implementations. |

### 11.4 Tests Required

- Render a normal model form in the form picker/UI preview and in the world with the effect off.
- Render one effect copy, then another normal model in the same frame and verify state restoration.
- Exercise opaque and translucent model textures so the host's split/deferred pass still runs.
- Repeat the world smoke test with vanilla rendering and the supported Iris/Sodium profile.
- Inspect the release JAR for accidental host `model.*` overrides and stale shader/Mixin classes.
- Do not accept compilation or a title-screen-only launch as visual rendering evidence.

### 11.5 Wrong vs Correct

Wrong:

```java
@ModifyReturnValue(method = "getModel", at = @At("RETURN"))
private static ShaderInstance replaceEveryModelShader(ShaderInstance host)
{
    return addonModelShader;
}
```

Correct:

```java
Texture rendered = EffectRenderState.active
    ? dissolveTextures.lookup(original, EffectRenderState.amount)
    : original;
```
