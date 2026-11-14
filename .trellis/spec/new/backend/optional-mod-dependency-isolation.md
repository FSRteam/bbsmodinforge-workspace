# Optional Mod Dependency Isolation

> How code may reference classes from optional mods (Iris, Sodium, GeckoLib, ...) without crashing installs that lack them. Status: Active.

---

## The Class-Loading Rule

JVM resolution is lazy per instruction, but **linking a class resolves every
type its bytecode needs to verify**. A class that hard-imports an optional
mod's API (fields, method signatures, `invokeinterface`/`checkcast` targets)
cannot be linked at all when that mod is absent — and then *any* first call
into it throws `NoClassDefFoundError` at the caller.

Concrete crash: `UIFilmPreview.renderViewStatus` called
`IrisViewBackend.failure(...)` unguarded; without Iris the game died with
`NoClassDefFoundError: net/irisshaders/iris/pipeline/WorldRenderingPipeline`
on the first preview status frame.

## Valid Guards

1. **Mod-loaded flag short-circuit (required pattern).** Check
   `BBSRendering.isIrisLoaded()` (backed by `isModLoaded("iris")`, set in
   `setup()`) *before* any expression that could resolve a dependency-linked
   class. The same pattern exists for Sodium/OptiFine/GeckoLib
   (`GeoArmorSupport.isAvailable()`).
2. **Conditional mixins.** Optional-dependency mixins live in their own mixin
   config (`bbs.client.iris.mixins.json`) registered only when the dependency
   is present.
3. **Lazy-safe data holders.** A class whose static state and touched fields
   are dependency-free (e.g. `ShaderCurves.variableMap`, plain `String`/`Map`
   constants) may be used unguarded — only its methods that reference the
   dependency API are off-limits.

## Invalid Guard

`try { dependencyApi(); } catch (LinkageError e) { return false; }` **inside
the dependency-linked class itself cannot work** for the absent-mod case: the
class never finishes linking, so the internal catch never runs and the error
surfaces in the caller. `IrisViewBackend.isAvailable()`'s catch only covers
present-but-broken Iris. Guards must live *outside* the guarded class or use
the mod-loaded flag.

## Version Bridges (same dependency, incompatible builds)

A dependency can be *present* on both sides with **incompatible API shapes** —
e.g. BBS compiles against Sodium `mc1.21.1-0.6.13` while users run the
official `0.8.13+mc1.21.1` backport, where `ShaderParser.parseShader` returns
a `ParsedShader` record instead of a `String` (same params, different
descriptor → `NoSuchMethodError` at the compiled call site).

Bridging pattern (`UnshadedTerrainProgram`):

1. **Detect the line once** — `Class.forName` on a class that only exists on
   the newer line (`ShaderParser$ParsedShader`), `initialize=false`, cached
   in a static boolean.
2. **Bridge shared entry points by reflection** — when two builds keep the
   same method name/params but change the return type, call it via
   `getMethod`/`invoke` (return types are not part of `getMethod` lookup);
   construct version-specific types via `getConstructor(..., parsed.getClass())`.
3. **Mirror the upstream assembly** — when upstream moved logic (0.8 moved
   define assembly from `ChunkShaderOptions.constants()` into
   `ShaderChunkRenderer`), re-implement the assembly against the *old*
   compile target and reflect the parts that only exist on the new line
   (`RenderDevice.getMaxTextureLodBias()`).
4. **Fail soft at the injection edge** — mixins that substitute behavior must
   catch `RuntimeException | LinkageError`, log once (per-instance flag to
   stop per-frame log spam), and fall through so the host does what it would
   have done without us.

## Forbidden Patterns

- Calling `IrisViewBackend` / `IrisUtils` from UI or shared code without an
  `isIrisLoaded()` check (UICurveClip-style callers go through guarded
  wrappers like `BBSRendering.getShadersLanguageMap`).
- Adding a new import of `net.irisshaders.*` into a class reachable by
  unguarded code paths.
- Relying on a `catch (LinkageError)` inside the guarded class as the sole
  availability check.

## Audit Checklist

- `grep -rn "IrisViewBackend\.\|IrisUtils\."` — every call site must sit
  behind a runtime `if (iris)`-style flag or live in iris-only mixin/config.
- `grep -rln "import net.irisshaders"` — new importers must either be
  conditional mixins or reachable only through guarded wrappers.
