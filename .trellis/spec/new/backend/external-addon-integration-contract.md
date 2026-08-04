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

API 2.0's `BBSAddonRegistrationContext` exposes only
`resources()/forms()/settings()/clips()/particles()/network()/events()`. It has **no**
facade for form renderers, editor panels, icons, or L10n — those go through static entry
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

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Three-argument pack constructor used by an addon | Reject in review; it can pass from expanded development resources but scan the BBS jar after packaging. |
| `Link.source` differs from the pack prefix | Treat `Asset <source>:<path> couldn't be found` as a namespace defect. |
| `Link.path` starts with `assets/<namespace>/` | Remove that prefix; the pack's internal root already owns it. |
| L10n event fires before the pack exists | Capture the `L10n` instance only; defer provider registration until after pack registration. |
| Client lifecycle callback runs again | Do not register a duplicate source pack. |

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

Anything registered after `BBSResources.init()` rebuilds form categories gets wiped, so
source packs and `addExtraForm` belong in an `onClientStarted` callback. Renderer, panel,
icon and decorator registration are plain static maps and can run during construction.

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
| Build passes but no client run was performed | Verification is incomplete because injection target lookup has not run. |
| `runClient` stops before Mixin config selection with `Failed to locate a primary monitor` | Treat it as a NeoForge early-window environment failure. Temporarily set `runs/client/config/fml.toml` `earlyWindowControl = false`, retry the same verification command, and restore the local value afterward. Do not weaken Mixin checks or edit addon/BBS source. |

### 7.5 Good / Base / Bad Cases

- Good: `runClient` logs config selection and Mixin application, then exercising the BBS
  dashboard logs the probe or feature behavior.
- Good: a particle-only list sorter injects at `UIDataPathList.sortElements` and sets the
  callback return only when the list uses the particle icon; file/model lists retain BBS sorting.
- Base: Iris is absent, so the Iris-only config is skipped while common and client configs
  still apply.
- Base: the early splash cannot resolve a monitor, but disabling only `earlyWindowControl`
  lets the real client window load, the verifier pass, and the local setting be restored.
- Bad: accept `./gradlew build` as proof that 76 migrated injection signatures are valid.
- Bad: record a pre-Mixin early-window failure as an addon regression or lower injection
  requirements to make an unrelated environment failure disappear.
- Bad: place a logger next to a Mixin because both are client-only; Mixin owns the whole
  package regardless of Java-side intent.
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
