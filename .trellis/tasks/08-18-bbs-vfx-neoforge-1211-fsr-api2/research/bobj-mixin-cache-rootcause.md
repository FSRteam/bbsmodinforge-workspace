# Bug Analysis: BOBJ model loading failure under the full client Mixin set

## 1. Root Cause Category

- **Category**: C/D - Change Propagation Failure and Test Coverage Gap.
- **Specific cause**: BBSFSR 0.0.11 kept `updateMesh(StencilMap)` as a wrapper, added
  `updateMesh(StencilMap, Matrix4f[])`, and moved the actual vertex upload into the cached
  `updateGeometry(Matrix4f[])` method. `BOBJSmearHideMixin` still targeted the old wrapper's
  `GL15.glBufferData` call. Mixin found zero matching instructions when the BOBJ class was
  first loaded, so the asynchronous model loader terminated before `emoticons` could become usable.

## 2. Why Earlier Fixes Failed

1. Disabling client Mixins made the models visible, which correctly narrowed the fault but hid the
   broken BOBJ injection instead of fixing it.
2. The first diagnostic group still included the stale Mixin JAR in `runs/client/mods`, so the
   new profile code was not used by the launched game.
3. Java compilation did not detect that the strict injection point had moved; only loading a BOBJ
   model exercised the target lookup.

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Exact target audit | Track wrapper, overload, and final side-effect descriptors against the locked BBSFSR source. | DONE |
| P0 | Cache-state contract | Force a geometry refresh when addon-owned hidden-bone state changes and once when it is cleared. | DONE |
| P1 | Runtime coverage | Load one BOBJ model through the same asynchronous path used by the `emoticons` category under the full Mixin set. | DONE |
| P1 | Artifact lifecycle | Stop the client before rebuilding and replace the run JAR only after the serialized build completes. | DONE |

## 4. Systematic Expansion

- **Similar issues**: Any BBS Mixin targeting a wrapper whose behavior moves into a cached helper
  can silently stop running while the addon still compiles.
- **Design improvement**: Inject at the narrowest final side-effect boundary and make cache invalidation
  explicit when addon state affects generated geometry or render state.
- **Process improvement**: Treat a real client path that loads the target class as mandatory evidence;
  startup-only checks are insufficient for lazy BBS model and UI classes.

## 5. Knowledge Capture

- [x] Updated the external addon Mixin contract with overload/cache migration rules.
- [x] Recorded the root cause and verification in the task implementation record.
- [x] Removed all temporary model probes and profile switches after the full-set fix was verified.
