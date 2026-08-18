# BBS-PoseCurve 1.21.1 BBSFSR Migration Design

## Scope and Boundaries

The deliverable is one standalone NeoForge addon at
`BBS-PoseCurve-fsr-1.21.1/`. It consumes the local BBSFSR jar built by `new/`
and does not add source files to `new/` or modify the 1.20.1 reference tree.

The old addon is Mixin-driven. Its empty Fabric entrypoints do not own feature
state, so the NeoForge entrypoint only establishes mod identity and optional
development verification. Functional behavior remains in helper classes and
Mixins.

## Project Shape

- Build: NeoGradle `net.neoforged.gradle.userdev` 7.1.21, Java 21.
- Platform: Minecraft 1.21.1, NeoForge 21.1.222.
- Dependency: `compileOnly` local
  `../new/build/libs/BBS_FSR_beta_v0.0.11.jar`.
- Runtime: the build installs that jar into the addon's development client mods
  directory without embedding it in the addon jar.
- Identity: directory `BBS-PoseCurve-fsr-1.21.1`, mod id `bbs_posecurve`,
  version `2.8.3-1.21.1`, original all-rights-reserved license declaration.
- Source layout: common code under `src/main/java`, client code under
  `src/client/java`, compiled into one NeoForge source set.

## Functional Migration Map

1. Curve editing core
   - Preserve automatic Bezier and clamped handles, automatic vector alignment,
     interpolation inheritance, multi-axis hit testing and handle dragging.
   - Port the three mapped 1.20.1 rendering blocks to Mojmap 1.21.1
     `Tesselator`/`BufferBuilder`/`BufferUploader`/`GameRenderer` APIs.

2. Pose-transform curve graph
   - Preserve per-component visibility/locking, linked component channels,
     selection, scalar editing, transform-panel synchronization, zoom and
     viewport state.
   - Keep `UIPoseTransformKeyframeGraph` as a dedicated graph implementation;
     do not duplicate BBSFSR core classes.

3. Film and replay editor state
   - Preserve curve versus dope-sheet mode, X/Y viewport, expanded pose tabs,
     clip editor viewport, undo cursor and per-replay orbit camera state.
   - Keep state ownership weak or UI-instance scoped as in the source to avoid
     leaking discarded editors and undo objects.

4. Pose evaluation and picking
   - Preserve per-component Bezier interpolation for `Transform` and
     `PoseTransform`, pose-property priority handling, additive bone picking,
     hierarchy offers and selected-component writeback.

5. Platform metadata
   - Replace `fabric.mod.json`, Fabric entrypoints and Java 17 Mixin metadata
     with `neoforge.mods.toml`, `@Mod`, Java 21 Mixin configs and strict BBSFSR
     dependency ordering.

## Mixin Compatibility Contract

- Declare common and client Mixin configs through `[[mixins]]` in
  `neoforge.mods.toml`.
- Use `compatibilityLevel: JAVA_21`, no refmap and `defaultRequire: 1`.
- Mark BBS targets and member injections as non-remapped because BBSFSR classes
  are addon classes under Mojmap and are not obfuscated.
- Keep helper classes outside packages claimed by Mixin configs.
- Validate private lambda targets against the compiled BBSFSR 0.0.11 jar;
  current evidence confirms `UIKeyframeFactory.lambda$new$4` and
  `UIKeyframeClip.lambda$registerUI$4` still exist with compatible roles.
- Do not weaken a required injection to hide a changed host method. Adapt the
  descriptor or injection boundary and keep startup failure strict.

## Compatibility Changes

- Fabric initializers and data generation are removed because they are empty
  and have no feature behavior.
- The empty access widener is omitted. Existing Mixin accessors/shadows remain
  the access mechanism.
- Intermediary Minecraft names (`class_286`, `class_287`, `class_289`,
  `class_290`, `class_293`, `class_757`) are replaced with the 1.21.1 Mojmap
  rendering classes and their current build/draw calls.
- BBSFSR API changes are handled at call sites after compiling against the
  current local jar. No compatibility shim is added unless more than one
  migrated consumer needs the same adaptation.
- `UIPropTransform.useRayDrag()` no longer exists in the current BBSFSR API;
  ray eligibility is now owned by `DragStrategyFactory` and the PoseCurve
  translation hotkey is intercepted at `enableMode(TransformOp)`. The old
  ray-specific injection is therefore removed because its host behavior is
  covered by the new strategy boundary, while the PoseCurve scalar-edit
  routing remains explicit.
- `ValueChangeUndo` moved from `mchorse.bbs_mod.utils.undo` to
  `mchorse.bbs_mod.ui.film.utils.undo`; the cursor-preservation Mixin and the
  strict target verifier use the current package.

## Verification Design

- A small Java logic-test source set exercises `AutoBezierHandles`,
  `AutoVectorHandles`, interpolation inheritance and pose-component access.
- `verifyBbsCoreJar` rejects a missing or stale BBSFSR jar and checks the key
  target classes needed by this addon.
- An opt-in client verifier (`-Pverify=1`) forces all BBS Mixin target classes to
  load after BBS client startup, checks bundled Mixin resources and runs core
  logic probes. It always stops the client in a `finally` block.
- Jar audit requires `neoforge.mods.toml` and both Mixin configs, forbids
  `fabric.mod.json`/`net.fabricmc` references and confirms no
  `mchorse/bbs_mod/**` classes are bundled.
- A manual Film editor smoke remains necessary for visual/gesture behavior that
  cannot be proven by class loading: curve opening, handle drag, pose component
  selection, undo/redo viewport restoration and replay switching.

## Risks and Rollback

- Highest risk: large UI Mixins compile while an injection descriptor or lazy
  target is stale. Mitigation: strict requirements plus forced target loading.
- Medium risk: BBSFSR has added its own keyframe/pose behavior around the same
  methods. Mitigation: preserve original calls and cancel only where the old
  addon deliberately owns the interaction.
- Medium risk: rendering semantics changed in 1.21.1. Mitigation: use the exact
  rendering pattern already present in `new/` and verify the Film curve UI.
- Rollback is removal of the standalone addon directory/jar; `new/` and the old
  reference source remain untouched.
