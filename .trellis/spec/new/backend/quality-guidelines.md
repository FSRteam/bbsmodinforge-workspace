# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

<!--
Document your project's quality standards here.

Questions to answer:
- What patterns are forbidden?
- What linting rules do you enforce?
- What are your testing requirements?
- What code review standards apply?
-->

(To be filled by the team)

---

## Forbidden Patterns

<!-- Patterns that should never be used and why -->

(To be filled by the team)

---

## Required Patterns

### Scenario: Reusable dashboard panels and editor preview render ownership

#### 1. Scope / Trigger

Apply this contract when a dashboard panel owns a camera/input controller, opens a
dynamic form palette, renders a form in `UIModelRenderer`, or adds a world-stage
deferred render/cache optimization.

#### 2. Signatures

- `UIDashboardPanels.open()` / `close()` own the active panel's transient
  `appear()` / `disappear()` pair.
- `FormRenderer.render(FormRenderingContext)` may call
  `FormTranslucentQueue.ensureStarted()` only during
  `BBSRendering.isRenderingWorld()` and never for `context.ui`, picking, or
  `context.modelRenderer`.
- `BOBJModelVAO.updateMesh(StencilMap, Matrix4f[])` owns the last GPU-uploaded
  armature snapshot and the immutable dominant-bone picking data.
- `FormRenderer.renderUI()` installs the BBSFS form-list diffuse-light pair before
  drawing each thumbnail. `UIModelRenderer` transforms its viewport light pair by
  the preview camera view before installing it.
- `ModelFormRenderer` owns the solved preview pose used by the normal draw,
  picking, same-frame attachment/Gizmo matrix sampling, and the Model Editor's
  secondary thumbnail.

#### 3. Contracts

- A dashboard panel object is reused across screen sessions. `close()` must retire
  the active panel's camera, key, gizmo, and palette ownership through
  `disappear()`; the next `open()` must call `appear()` even when the selected
  panel object did not change.
- Dynamic editor callbacks capture the entity they were opened for. Changing or
  removing that entity first removes the palette and its camera controller; a
  callback must never dereference the panel's later selection.
- `FormTranslucentQueue` is a world render-stage queue, not a generic form queue.
  `PREVIEW + modelRenderer()` and Film/UI-local previews draw immediately because
  the world's translucent flush boundary has already passed.
- An animated BOBJ normal pass and its picking pass may reuse skinned
  position/normal/tangent buffers only when every armature matrix exactly matches
  the last uploaded snapshot. Picking IDs derive from immutable vertex weights and
  bone indices, so their buffer is rebuilt only when `StencilMap.increment`
  changes. Animation time, IK, physics, and constraints are not downsampled.
- A solved preview pose cache is renderer-local. Picking and secondary thumbnail
  draws consume it once; same-frame matrix sampling may restore it without
  consuming it. Its key includes the exact context where applicable, simulation
  owner, entity identity, model identity, entity age, exact transition bits,
  semantic base matrix, and world-target/collision capabilities.
- Form-list thumbnails must not inherit arbitrary diffuse light from a previously
  rendered widget. The BBSFS list pair is `(0, 1, -0.2)` and
  `(-0.85, 0.85, 1)`, normalized. Full 3D preview lights are transformed by the
  current preview camera view so orbiting the camera preserves BBSFS shading.
- Vanilla Mob preview rendering saves and restores the incoming model-view matrix.
  Its UI thumbnail light matches BBSFS `MAX_BLOCK_LIGHT_COORDINATE`
  (`LightTexture.FULL_BLOCK`), while Form editor preview lighting remains governed
  by the Form context and lighting property.

#### 4. Validation & Error Matrix

- Dashboard closes with an active panel -> `disappear()` runs once before panel
  `close()`; reopen restores the same panel with `appear()`.
- Selected model block changes or `isRemoved()` becomes true -> remove all child
  `UIFormPalette` nodes, remove the immersive camera controller, then clear/fill
  selection.
- `isRenderingWorld() == false`, `context.ui == true`, picking, or
  `context.modelRenderer == true` -> do not start/defer through the world queue.
- BOBJ armature length or any matrix differs -> recompute and upload skin geometry;
  exact match -> reuse geometry and update only picking state if required.
- Preview context/owner/entity/model/age/transition mismatch -> solve animation,
  constraints, IK, and physics normally and replace the one-shot snapshot.
- Preview semantic base or world capability mismatch -> do not reuse the solved
  pose for matrix sampling.

#### 5. Good/Base/Bad Cases

- Good: ESC closes Morphing, B reopens the same panel, and its immersive controller
  is registered again without switching to another panel first.
- Good: Film form preview renders Allay/Vex/Player translucent layers immediately.
- Good: an animated Form normal preview solves once and its immediately following
  stencil pass, Gizmo matrix samples, and Model Editor thumbnail restore the exact
  solved Cubic/BOBJ pose without advancing physics.
- Good: form-list thumbnails use the fixed BBSFS list lights, while a full preview
  rotates its light directions with its own camera view.
- Base: world Actor/ModelBlock/Film replay forms still enter the deferred
  translucent queue and flush at the world translucent boundary.
- Bad: `setPanel(currentPanel)` is expected to reactivate a panel even though its
  equality guard returns before `appear()`.
- Bad: a model-block palette callback reads `this.modelBlock` after the original
  block was destroyed or another block was selected.
- Bad: a UI preview starts the world translucent queue and leaves its entity layers
  queued until the next world frame.
- Bad: reuse a preview pose by age alone; paused animation can then consume a stale
  pose from a prior UI frame.

#### 6. Tests Required

- Source/logic regression asserts active-panel `disappear`/`appear` ownership,
  same-panel camera restoration, removed-model-block palette cleanup, and the
  world-only queue predicate.
- Transform regression asserts S uses `scaleHotkeyOrder`, while pose/general
  transforms have a `hotkeyDrag` source for R view/sphere modes.
- BOBJ regression asserts unchanged armatures bypass CPU skinning and static
  dominant-bone picking data is not uploaded every frame.
- Preview regression asserts consuming draws are one-shot, matrix sampling is
  non-consuming, and reuse is keyed by context/owner/entity/model/age/transition/
  semantic policy; it also covers list and camera-space viewport lighting. Mob
  regression asserts per-layer picker shader setup plus model-view restoration.
- Run `testUiInputMailbox`, `testFilmEditorMigration`, `compileClientJava`, and an
  in-game Morph/Film/ModelBlock matrix with translucent mobs and an animated BOBJ.

#### 7. Wrong vs Correct

Wrong:

```java
if (!context.isPicking() && !context.ui)
{
    FormTranslucentQueue.ensureStarted(); // also captures UIModelRenderer previews
}
```

Correct:

```java
if (BBSRendering.isRenderingWorld()
    && !context.isPicking()
    && !context.ui
    && !context.modelRenderer)
{
    FormTranslucentQueue.ensureStarted();
}
```

### World form render matrix contract

World, film, and vanilla entity form rendering must keep one coordinate-space
contract:

- `PoseStack` carries camera-relative or local object transforms.
- The active world view/model-view matrix is applied through `RenderSystem` or
  the shader pipeline exactly once.
- Do not multiply the render-level model-view matrix into a `PoseStack` and then
  render through a pipeline that will also apply the captured model-view matrix.

NeoForge `RenderLevelStageEvent` exposes both `getPoseStack()` and
`getModelViewMatrix()`. Keep these separate when constructing
`BbsWorldRenderContext`: copy the pose stack as the local/camera-relative stack
and pass the model-view matrix as context metadata. If BBS code needs to draw
while the global model-view is temporarily different, set
`RenderSystem.getModelViewStack()` from `context.modelViewMatrix()` during that
draw and restore it afterward.

Good/Base/Bad cases:

- Good: film replay targets compute entity positions relative to
  `context.camera()` and render with `context.matrixStack()` while
  `RenderSystem` holds `context.modelViewMatrix()`.
- Base: vanilla entity renderers receive Minecraft's dispatcher-owned
  `PoseStack` and call `FormUtilsClient.render(...)` without baking the global
  model-view into that stack.
- Good: Iris chunk-layer callbacks pass the `renderSectionLayer(...)` matrix
  argument into `BBSRendering.onRenderChunkLayer(...)`; this is the layer's
  active world matrix.
- Bad: create a new `PoseStack`, multiply `event.getModelViewMatrix()` into it,
  reset `RenderSystem` model-view to identity, then render through Iris; Iris
  may still apply its captured gbuffer model-view and the form will drift with
  the camera.
- Bad: in a shared renderer such as particle or trail rendering, treat
  `BBSRendering.isRenderingWorld() && !context.ui` as proof that
  `context.stack` is a BBS camera-relative world stack. Actor and model-block
  renderers can satisfy that condition while still passing a Minecraft-local
  stack.
- Bad: inside `renderSectionLayer(...)`, replace the callback's matrix
  argument with `RenderSystem.getModelViewMatrix()`. Under Iris the global
  value can belong to another render scope, making non-actor replay forms drift
  with the camera.

Tests required:

- `./gradlew.bat compileClientJava` from `new/`.
- In-game visual check with shaders disabled: ordinary form/morph entities stay
  fixed in world space while the camera moves.
- In-game visual check with Iris shaders enabled: ordinary form/morph entities
  and film replay targets stay fixed in world space while the camera moves.

#### Local geometry and effect transform composition

Any transform defined in model, bone, attachment, or effect-local space must be
composed before a matrix that contains camera-relative placement or a view
transform:

```text
render/view base * local delta * attachment/effect transform * local vertex
```

Never left-multiply a completed render matrix with a model-local delta. Doing so
rotates the camera-relative translation as part of the joint/effect and makes the
geometry change orientation when the camera moves.

The same rule applies when a secondary pass is generated in different geometry
backends:

- VAO vertices remain local until the shader, so a local effect matrix may be a
  shader uniform between `ModelViewMat` and `Position`.
- Immediate, billboard, and captured vanilla vertices are transformed through a
  `PoseStack` before upload. Apply local effect transforms to that stack before
  emitting vertices and keep the later shader transform at identity.
- A secondary material pass that samples the base texture's alpha must restore the
  exact texture used by each material. The texture left bound by the last material
  is not valid for every material in the model.
- Picking/stencil passes must skip decorative secondary passes entirely; their
  output encodes object or bone identity rather than visible color.
- For non-uniform scale, normals use the inverse transpose of the effect's linear
  matrix. Degenerate scale requires a finite fallback rather than inverting a
  singular matrix.

Review and validation:

- Classify every secondary-pass vertex source as local-until-shader or
  pose-baked-before-upload before deciding where its transform belongs.
- Check a stationary object while translating and rotating the camera.
- Check non-identity translation, rotation, uniform scale, and non-uniform scale.
- Check multi-material cutouts and confirm transparent texels sample their own
  material texture.
- Check normal picking with the secondary effect enabled.
- Run `./gradlew.bat compileClientJava` and the full `./gradlew.bat build`; shader
  compilation and camera invariance still require an in-game visual check.

#### Decorative RenderType pass ordering and state ownership

Minecraft `RenderType.setupRenderState()` / `clearRenderState()` owns shader,
texture, texturing matrix, blend, cull, lightmap, overlay, and depth state for a
standalone draw. A BBS secondary pass invoked inside a larger form/model render
must treat those values as borrowed caller state.

- Finish every base-material draw in the model, including VAO and immediate
  fallback geometry, before submitting glint or another decorative RenderType.
  Never alternate `base bone -> glint bone -> base bone`; the RenderType clear
  handler can disable blend/lightmap/overlay or replace textures needed by later
  bones.
- If a base surface is deferred to `FormTranslucentQueue`, append its decorative
  pass to the same queue after the base commands. Drawing the decoration
  immediately can precede the surface that supplies its depth mask.
- Snapshot and restore the incoming shader, shader color, shader texture slots,
  texture matrix, active texture, blend equation/factors/enabled state, depth
  test/function/write mask, and cull state around a nested RenderType draw.
- An unchanged secondary geometry pass uses `GL_EQUAL`: a shader that does not
  sample the base texture alpha may then shade only pixels where the base surface
  actually wrote depth. The base and secondary passes must also split their
  transform chain at the same point. If the base submits local vertices with
  `global * bone` in the shader, do not CPU-bake `bone * vertex` and submit only
  `global` for the secondary pass: floating-point reassociation changes depth bits
  and makes `GL_EQUAL` pass over only direction-dependent arcs. Use `GL_LEQUAL`
  only when an explicit local transform makes the secondary geometry differ from
  the base.
- Immediate batches that mix unchanged and transformed secondary geometry must
  be split by depth mode. One transformed bone must not widen depth testing for
  every unchanged bone in the batch.

Required visual matrix: opaque and cutout multi-material models, partially
transparent forms, one glinted bone followed by ordinary bones, VAO/immediate/
hybrid paths, shaders off, and Iris shaders on. Confirm later bones keep their
base material, transparent texels do not turn black, and geometry remains hidden
behind nearer blocks/entities.

Shader-pack compatibility is a program-routing contract, not merely a known
Minecraft `RenderType` name. For glint and similar special compositors:

- Inspect representative packs for the exact gbuffer program they implement.
  A pack may provide `gbuffers_armor_glint` while treating `energy_swirl` as an
  ordinary entity or omitting equivalent overlay behavior.
- Under an active Iris world pipeline, route enchantment overlays through the
  dedicated armor-glint RenderType when that is the shared pack contract. Preserve
  that RenderType's vanilla texture matrix unless the representative packs have
  been pixel-tested with an override. A matrix that is valid for the vanilla core
  shader can sample an invisible region or violate a pack's armor-glint program;
  custom speed must degrade before visibility does.
- Check which inputs the pack consumes. One pack may read `gl_Color`, while
  another samples only the glint texture. In the latter case tint, opacity, and
  per-vertex Fresnel cannot be promised even though visibility and composition
  are correct; document the fallback instead of claiming pixel parity.
- For CPU Fresnel, position and normal must come from the same coordinate space.
  Derive the camera in that space by transforming the view-space origin through
  the inverse of the matrix that will still be applied at draw time. Do not assume
  that PoseStack-baked values always use `(0, 0, 0)` as the camera: UI cameras,
  hand scopes, and view transforms may leave translation in the global model-view.

Wrong:

```java
// Base VAO: global * bone * local; edge mesh: global * (bone * local)
edgeRenderer.renderGroup(builder, boneStack, group, model);
drawEdge(mesh, globalModelView); // GL_EQUAL is not bit-exact
```

Correct:

```java
edgeRenderer.renderGroup(builder, new PoseStack(), group, model);
drawEdge(mesh, globalModelView.mul(boneStack.last().pose()));
```

#### Sparse mesh deformation contract

A smooth weight function does not create a smooth surface when the source mesh has
no vertices inside the deformation interval. A four-corner cube face remains two
planar triangles after rasterization, so moving only its endpoints produces a chord
and can make the joint appear pinched rather than bent.

- Inspect source topology before choosing a vertex deformation algorithm. If a face
  spans a joint with only endpoint vertices, subdivide it in the deformation
  direction before applying weights.
- Choose subdivision density in model space and cap it explicitly. Preserve winding,
  UVs, color, light, overlay, and normals for every generated strip.
- For a rigid joint, interpolate rotation around the joint pivot and keep the exact
  upper/lower transforms at weights 0 and 1. Linear blending of transformed points
  may be used only as a documented fallback for scale or shear because it loses
  cross-section volume at large angles.
- Material, trim, glint, and other secondary passes must use the same subdivided
  geometry and deformation entry point as the base surface.
- Visual validation must include 30, 60, and 90 degree bends, both inner and outer
  joint surfaces, cross-section thickness, normals, and camera movement.

### Film actor replay ownership

Real `ActorEntity` instances spawned for actor/first-person film replays have
two owners:

- the server-side `ActionPlayer` owns world position, movement, spawning, and
  removal;
- the client-side film controller may apply visual state such as rotation,
  equipment, form properties, and client-only actions for preview smoothness.

Do not make the client film controller continuously overwrite real actor world
coordinates during normal playback. In NeoForge, vanilla entity rendering
already interpolates server-owned actor positions in a camera-relative pose
stack. If the client also writes replay position keyframes to the same actor,
server interpolation and client film preview state can fight each other,
especially when a replay is disabled or actor mode changes.

Good/Base/Bad cases:

- Good: `BaseFilmController` skips disabled replays before update, property,
  and render work, even if a stale editor entity still exists in the controller
  map.
- Good: client-side actor replay sync skips `ReplayKeyframes.GROUP_POSITION`
  for real `ActorEntity` and lets server/original entity interpolation place
  the actor.
- Base: non-actor film stubs still apply full replay keyframes locally because
  they are preview-only entities owned by the film controller.
- Bad: after an actor replay is disabled, a stale client entity continues to
  receive full replay position keyframes and appears to drift with the camera
  instead of disappearing or staying world-fixed.

Tests required:

- `./gradlew.bat compileClientJava` from `new/`.
- In-game visual check: toggle a replay's enabled state while the film editor is
  open; disabled replays stop updating/rendering immediately.
- In-game visual check: actor-mode replay targets stay fixed in world space
  while moving the camera after playback is stopped, paused, or the replay is
  disabled.

### Targeted film actor privacy and action capability

`TARGETED_COMMAND` actors are private projections in a shared `ServerLevel`.
They need two boundaries at once: ordinary world systems must not observe or
affect them, while explicitly recorded, administrator-authorized film actions
must still be able to reproduce damage, attacks, and entity interactions.

Contracts:

- Targeted playback requires a non-null delivery player. Reject it before
  constructing `ActionPlayer`; never downgrade a missing audience to a public
  actor.
- Install the audience and world-isolation state before `addFreshEntity`.
  Filter tracking through `Entity.broadcastToPlayer`, so spawn, metadata,
  equipment, movement, form data, and removal all use the same audience set.
- A private actor remains a spectator, non-pickable, non-pushable,
  no-physics, invulnerable projection that ignores block triggers, item pickup,
  public damage, persistence, and AI target selection.
- Recorded actions run inside an exception-safe, thread-local identity scope
  containing an immutable snapshot of the current `ActionPlayer` actor
  instances. Capability checks also revalidate the real requester against the
  actor's server at the moment of use.
- The internal capability may open only the operation required by the recorded
  action: `isAttackable` may address an exact owned actor, while `hurt` routes
  into that actor's visual-only damage projection. It must never make the actor
  non-spectator, pickable, pushable, globally tracked, or block-triggering.
- An isolated actor must not call `LivingEntity.hurt` or `LivingEntity.die`,
  even after temporarily clearing invulnerability. Those methods publish the
  actor to global NeoForge damage/death/drop hooks, sculk game events, range
  sounds, kill score/criteria, loot/XP and knockback/effect callbacks.
- Apply authorized damage through a bounded pure health transition. Publish
  only `broadcastDamageEvent`, tracked death event `3`, health/pose metadata and
  normal tracked removal; `broadcastToPlayer` then limits those packets to the
  playback audience. The audience client owns the corresponding hurt/death
  sounds and animations. Server-side private actor sounds are sent directly to
  that audience, and private actor game events/fire ticks remain suppressed.
- `AttackActionClip` skips `CommonHooks.onPlayerAttackTarget` only when its
  exact resolved target is an isolated `ActorEntity`; that global event and the
  item's `onLeftClickEntity` callback would expose the private projection.
  Ordinary entity targets retain the NeoForge hook. Both paths still pass the
  scoped `isAttackable`, `skipAttackInteraction` and `hurt` checks.

Good:

```java
Transition next = PlaybackActorDamageProjection.apply(getHealth(), amount);
setHealth(next.health());
level().broadcastDamageEvent(this, source); // tracked audience only
```

Bad:

```java
setInvulnerable(false);
super.hurt(source, amount); // enters global hooks, events, score and drops
```

Tests required:

- `testInteractionActions` covers missing targeted audiences, audience-before-
  spawn ordering, tracking/world isolation, Attack/Damage/EntityInteraction
  wiring, identity rather than `equals`, nested failure restoration,
  cross-thread isolation, non-lethal/lethal visual damage transitions, invalid
  damage rejection, absence of global hurt/death side-effect calls, private
  attack-hook bypass, and preservation of the ordinary public attack hook.
- `compileJava` verifies the common-side context and entity overrides without
  introducing client-only classes.

### Model simulation space, ownership, and solver-order contract

#### 1. Scope / Trigger

Apply this contract whenever form rendering feeds model IK, bone physics, bone
constraints, world-space controller targets, or any other stateful procedural
pose solver. It covers editor previews, world entities, model blocks, film
stubs, held-item forms, nested body parts, and list/UI previews.

#### 2. Signatures

- `FormRenderingContext.stack`: render-space `PoseStack`; it may be
  camera-relative, entity-local, item-local, or include an editor camera view.
- `FormRenderingContext.world`: semantic simulation `PoseStack`; it never
  contains camera/view transforms.
- `FormRenderingContext.simulationOwner`: stable identity that owns mutable
  simulation history. World paths default to `context.entity`; editor previews
  set this to the preview renderer instance.
- `FormRenderingContext.allowWorldTargetOverrides` and
  `allowWorldCollisions`: explicit capabilities of the semantic host. They are
  both false for local simulation even when its clock entity has a live level.
- `ModelPhysicsRuntime.apply(IEntity, Object, ModelInstance, float, Matrix4f)`:
  receives the clock/world source, stable simulation owner, shared model
  resource, render transition, and semantic model-to-world transform.
- `FormRenderer.collectMatrices(IEntity, Object, Matrix4f, boolean, boolean,
  float)`: placement-aware pose sampling with the same owner, semantic base,
  target policy, and collision policy as the rendered placement.
- `BaseFilmController.startRenderFrame(float)`: first applies ordinary form
  properties and scalar IK/physics/wind controls to every replay, then resolves
  all spatial IK/pole/physics target channels.

#### 3. Contracts

- `FormRenderingContext.set(...)` creates a fresh `world` stack. For
  `ENTITY` and `MODEL_BLOCK`, initialize it from the interpolated absolute
  entity position and body yaw. Other render types begin in model-local
  identity space.
- Every form transform, body-part transform, model-axis correction, and
  bone-attachment transform applied before model simulation must be applied to
  both `stack` and `world`, with balanced push/pop operations.
- Camera position, camera view, `RenderSystem` model-view, and an entity
  dispatcher's camera-relative translation belong only to rendering and must
  never be stored in physics history.
- World inputs are capability-gated, not inferred from `IEntity#getWorld()` or
  a global world-render flag. `localSimulation()` disables absolute target
  overrides and Level collision while retaining the entity only as a clock and
  animation source.
- Every `ItemDisplayContext`, including ground, fixed, head, and `NONE`, is an
  item-local placement with a stable context-specific owner. It must not borrow
  player/camera transforms or a live Level for simulation.
- A Film frame resolves spatial target channels in two phases. All replay and
  actor properties plus scalar procedural controls are installed before any
  target samples another replay's bone. Target resolution uses a separate
  per-entity owner, local IK, no physics, and no absolute target overrides;
  this prevents channel/replay iteration order from advancing or contaminating
  the actual world placement.
- Every replay derives its local tick independently from the unchanged
  controller tick. Never overwrite the loop's controller tick with
  `replay.getTick(...)`; otherwise the next replay inherits the previous
  replay's loop mapping and list order changes playback.
- After all target maps are complete, visual consumers such as anchors, axes,
  Tracker, Orbit, shadow-follow, and Gizmos use placement-aware matrix
  collection with the rendered placement's real owner, absolute semantic base,
  target policy, and physics policy.
- A relative Film replay has no absolute world host. Give it a resettable local
  owner, reject world collisions and absolute targets, and fail closed when it
  is requested as an absolute anchor, IK/pole/physics target, Tracker source,
  or Orbit source. Never reinterpret its replay-local coordinates as world
  coordinates.
- `Form.applyStates` / `unapplyStates`, render/semantic stack pushes, temporary
  entity selection, and captured bone caches are one exception-safe scope.
  Invisible forms, nested renderer failures, and recursive matrix failures must
  leave all of them restored.
- Mutable Verlet/solver history is owned by the form renderer plus
  `simulationOwner`; it must not be keyed globally by shared `ModelInstance`
  or model resource id alone.
- Compiled immutable IK/physics/constraint data may be cached by source data;
  simulation positions, previous positions, velocities, and tick history may
  not.
- Compile stable solver topology once: ancestor-first IK chain order, per-chain
  wanted bones, physics wanted bones/chain ids, rest lengths, and rest
  directions. A normal render must not rebuild these with copied lists or new
  sets.
- Retain mutable pivot frames and solver scratch under the renderer plus
  `simulationOwner`. Frame collectors must update the existing vectors and
  quaternions in place after warm-up; never move mutable workspace onto the
  shared `ModelInstance` to save allocations.
- Performance work must preserve configured iterations, physics fixed substeps,
  collision/constraint behavior, and deterministic catch-up. Lower simulation
  quality is not an acceptable substitute for removing redundant topology work
  and allocation.
- IK chains with omitted `softness` use hard reach (`0`) and may extend to the
  exact chain length. A serialized `softness > 0` remains an explicit soft-IK
  request and keeps its asymptotic reach behavior.
- Two-bone IK singularity recovery is based on the bend angle predicted for the
  current target, not only the FK input angle. At 3 degrees or below the
  hard-IK analytic position solve must become exactly collinear, restoring an
  authored-straight joint to its original local angle instead of leaving a
  tiny bend on the opposite side of a zero-degree limit. Use the authored rest
  bend to define the plane, keep the stable bend side until the predicted angle
  is at least 20 degrees, then return to live/pole control. Store that stable
  bend normal with the same renderer/owner/model/chain ownership as other
  mutable solver state.
- Explicit `softness > 0` opts out of exact straight restore as well as exact
  reach; do not let the dead zone erase the user's requested asymptotic bend.
- Bend magnitude and bend-plane direction are separate invariants. Hysteresis
  that only stabilizes the normal does not make a nearly straight triangle
  straight. The 3-degree straight-restore branch may leave the chain end a
  small distance beyond an inside target; do not convert that intentional
  dead-zone error into reverse stretch/compression.
- Position-level limits and render-orientation limits are separate invariants.
  Direction-only reconstruction (`fromToMirroredX`) does not validate the full
  swing+roll quaternion produced by `orientMirroredX`. Clamp the exact local
  quaternion assigned to `ModelGroup.orient` / `BOBJBone.orient`, then advance
  the child parent frame with that clamped value. If the target becomes
  unreachable, preserve the hard joint limit instead of letting the rendered
  mesh cross it while debug joint positions still reach the target.
- IK bend hints and pole controls are direction inputs, not absolute pose
  inputs. On the first effective frame, store target, pole, and optional tip
  rotation in the chain root-parent frame owned by
  renderer/`simulationOwner`/model/chain. Rebind after compiled data changes,
  owner changes, disable/re-enable, or weight returning from zero.
- The current animated FK chain is the maintain-offset reference every frame.
  With target translation delta zero, pole angular delta zero,
  `poleAngle == 0`, no absolute override, and tip-rotation delta identity, IK
  must return without writing `orient`. A static bind-pose snapshot would
  freeze animation; an absolute pole solve would manufacture a pose change.
- Apply ordinary target movement as a root-parent-local bind delta added to the
  current FK reach. Apply pole movement as its signed projected angular delta
  around the current root-to-target axis. Existing `poleAngle` is an additive
  relative offset after calibration. A bound pole may choose a deterministic
  bend side for an authored-straight chain, but it must not create bend
  magnitude while all controller deltas are zero.
- Tip rotation follows the same contract: compute controller
  `current * inverse(bind)` in root-parent local space, conjugate it into world
  space, and multiply it onto the current FK effector rotation. Never copy the
  controller's absolute rotation on enable.
- Model viewport bone picking belongs to the visible property panel first.
  `UIModelForm` must offer ordinary and Ctrl-click bone selections to the
  active IK, physics, or constraint panel through `pickBoneInList(...)`; only
  fall back to the pose editor when the visible panel cannot accept that bone.
  Do not update a hidden pose selection while leaving the visible procedural
  panel on stale properties.
- Model picking must keep cubic immediate, cubic VAO, BOBJ skinning, the picker
  shader, and `fillStencilMap` in one ordering contract. The BBSFS contract is
  `Target + localBoneIndex`, with registrations starting at `Target` in the
  same bone order. IK target/pole and physics attach markers append IDs after
  the ordinary-bone range and must never determine whether unbound bones are
  selectable.
- Gizmo owns the complete raw-ID range from `Gizmo.STENCIL_X` through
  `Gizmo.STENCIL_MAX`, including move, scale, rotate, trackball, view, screen,
  and scale-all handles. `StencilMap.setup()` must derive the first form ID as
  `Gizmo.STENCIL_MAX + 1` and seed mappings from `Gizmo.Handle.values()`.
  Never restore the legacy hard-coded base 7 or a six-entry move-only table:
  `GizmoInteraction` consumes reserved raw IDs before form/bone lookup.
- Solver order is FK/animation -> generic Euler constraints -> IK -> physics.
  IK and physics enforce their own per-bone limits. Do not run a generic
  constraint pass after them, because it clears composed `orient` quaternions
  and discards the procedural result.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Camera rotates or translates while the model/host is stationary | `world` and physics history remain unchanged |
| Player/camera moves while unrelated models are visible | Each model responds only to its own host transform/history |
| Same world entity is also rendered in an editor preview | Preview uses the preview renderer as `simulationOwner`; world uses the entity |
| Two forms use the same model resource id | Their renderer-owned mutable histories remain independent |
| IK is enabled on a pre-bent animated limb while controllers remain at bind | The enable frame and every later FK-only frame remain bitwise pose-preserving; no `orient` write occurs |
| Target returns to its bind transform after FK animation advances | The chain returns to that frame's FK pose, not the original bind-frame snapshot |
| Pole remains at bind and `poleAngle` is zero | Pole supplies no pose delta; a straight chain may only use it as a bend-side fallback |
| Pole moves 10 degrees and `poleAngle` is 5 degrees | The requested bend-plane change is the signed relative pole delta plus the 5-degree authored offset |
| IK is disabled or weight reaches zero, then becomes effective again | Discard the old calibration and bind against the newly evaluated FK/controller state |
| Constraint affects an IK/physics bone | FK input is clamped first; procedural result remains active and is limited inside its solver |
| Render type has no meaningful world host (inventory/UI/hand preview) | Use a stable local simulation space and a context-specific owner; never borrow the main camera/player transform |
| Nested body part applies a local transform | Apply the same local transform to `stack` and `world` before the child solver runs |
| Same compiled IK/physics config renders for many frames | Reuse topology, pivot frames, chain-frame containers, and solver scratch after warm-up |
| Physics performance is insufficient | Profile topology/allocation and solver work; do not reduce fixed substeps, configured iterations, collisions, or limits |
| Film replay/channel order changes | The same frame resolves identical target maps and placement poses; target resolution does not advance world physics |
| Replays have different loop periods and their list order changes | Each replay receives the same controller tick as input and keeps the same local playback time |
| Relative replay is sampled by an absolute anchor/target/Tracker/Orbit consumer | Reject the cross-policy source and keep the consumer unanchored/unchanged |
| Axes, shadow, Tracker, Orbit, or a Gizmo samples a procedural attachment | Use the rendered placement owner and semantic base after target maps are complete |
| `applyStates`, a nested form renderer, or recursive matrix collection throws | Run matching cleanup and restore light, both stacks, entity/owner context, and captured bones |
| IK softness is omitted | Reach may clamp exactly at full chain length |
| IK softness is explicitly greater than zero | Preserve the configured soft reach |
| Two-bone target predicts 0-3 degrees of bend | Emit exactly collinear segments and restore the authored-straight local angle; no opposite-side micro-bend |
| Two-bone target exits the straight singularity | Resume bend magnitude above 3 degrees; keep bend-side hysteresis until 20 degrees |
| IK/physics debug is toggled while picking model bones | Bound and unbound ordinary bones retain the same IDs; markers only append entries |
| Straight-restore chain end lies slightly beyond an inside target | Preserve the intentional dead zone; never create reverse stretch/compression |
| Position solve is straight/legal but final swing+roll orientation exceeds min/max | Clamp the final rendered local quaternion and use it as the descendant parent frame |

#### 5. Good / Base / Bad Cases

- Good: `ModelFormRenderer` converts world target overrides with the inverse of
  `context.world`, while drawing with `context.stack`.
- Good: `UIPickableFormRenderer` calls `.simulationOwner(this)`, so editing a
  live model block cannot overwrite the block's world physics history.
- Good: Film target resolution uses its own local-only owner during phase two,
  then anchor/Gizmo consumers use placement-aware collection with the real
  placement owner after target maps are complete.
- Good: a relative replay and every relative bone consumer use the same
  resettable local owner while absolute cross-policy consumers return no
  target.
- Good: `ModelIKCache.Compiled` owns sorted chains and wanted-bone sets, while a
  renderer-owned runtime retains mutable frame maps and bend history per owner.
- Good: a pivot-frame collector calls `set(...)` on an existing frame's
  position/rotations after the first collection.
- Base: an ordinary world morph uses its stable `IEntity` as simulation owner
  and absolute interpolated entity transform as `world`.
- Bad: pass `context.stack.last().pose()` directly to bone physics in an editor
  or world entity path; camera motion becomes fake model acceleration.
- Bad: store `lastBaseTransform` or Verlet history on the shared
  `ModelInstance`; every form using that resource can overwrite it.
- Bad: run `ModelConstraintsRuntime.apply` after IK/physics; it clears
  `orient` and makes the procedural chain appear disabled.
- Bad: allocate and sort `new ArrayList<>(chains)`, build `HashSet` wanted bones,
  or create a new frame map on every render.
- Bad: change `SUBSTEPS_PER_TICK`, silently cap user iterations, or disable
  collision to claim an IK/physics optimization.
- Bad: choose a straight-chain bend side from a camera axis or store the last
  bend normal on a shared model resource.
- Bad: stabilize only `bendNormal` while leaving a nonzero analytic `sinA`; the
  center line can look straight while the local joint angle crosses below its
  authored zero/limit.
- Bad: validate constraints against direction-only Euler, then write an
  unconstrained full-frame `orient`; the debug bone line and rendered mesh can
  disagree at the limit.
- Bad: resolve target channels while replay properties are still being applied,
  sample the actual world owner during that pass, or let a relative replay's
  local matrix enter an absolute world consumer.
- Bad: pop only the outer render stack after a nested body-part failure; the
  semantic stack, temporary entity, applied form state, or captured bone cache
  can remain stale for the next placement.

#### 6. Tests Required

- `./gradlew.bat --no-daemon compileJava compileClientJava` from `new/`.
- `./gradlew.bat --no-daemon build` from `new/`.
- Editor: keep the model still and orbit/pan the preview camera; gravity and
  world wind direction must not rotate or gain inertia.
- World: move/stop the player camera around two stationary physics models;
  neither model may inherit common player/camera acceleration.
- Ownership: edit a live model block while it remains visible in the world;
  preview motion must not reset or kick the world instance.
- Composition: test constraints only, IK only, physics only, IK+constraints,
  physics+constraints, and IK+physics+constraints.
- Nesting: test a physics/IK model used as a transformed body part.
- Reach: with omitted softness, pull a two-bone target beyond total length and
  assert the solved tip reaches exact full extension; repeat with explicit
  softness and assert soft reach is preserved.
- Straight restore: probe predicted bend angles 0, 2, 3, and 4 degrees. Assert
  0/2/3 produce exactly collinear segments and the authored-straight local
  angle, while 4 resumes normal bending. With stretch enabled, assert the
  inside-target dead zone does not create a reverse offset.
- Final orientation: with a zero/min boundary and bend-normal roll active,
  convert the exact cubic/BOBJ `orient` back to local Euler and assert every
  enabled axis remains inside min/max. Verify descendants are decomposed from
  the clamped parent frame.
- Hysteresis: move a two-bone target through straight extension from both sides;
  assert bend-side recovery starts at 3 degrees, remains stable below 20
  degrees, and explicit pole control resumes outside the band without a flip.
- Performance: compare the same multi-chain scene after warm-up and confirm the
  render path no longer reconstructs chain ordering/wanted sets/frame maps/
  chain-frame lists/stiffness arrays. Keep all solver quality settings equal.
- Film order: reorder replays and target channels while ordinary animated bones
  reference each other; assert target maps and final placement poses are equal
  and the target-resolution pass does not advance world physics history. Give
  the replays different loop periods and also assert their mapped ticks do not
  change with list order.
- Relative policy: assert local rendering keeps one owner across mesh/Gizmo
  sampling, ignores Level collision and absolute target maps, and is rejected
  by anchor, IK/pole/physics, Tracker, and Orbit world consumers.
- Cleanup: throw from `applyStates`, nested rendering, and recursive matrix
  collection; assert `unapplyStates`, light, render stack, semantic stack,
  temporary entity/owner, and bone cache are restored.

#### 7. Wrong vs Correct

Wrong:

```java
Matrix4f baseTransform = new Matrix4f(context.stack.last().pose());
ModelIKRuntime.apply(model, targets, poles);
physics.apply(entity, model, transition, baseTransform);
ModelConstraintsRuntime.apply(model);
```

Correct:

```java
Matrix4f baseTransform = new Matrix4f(context.world.last().pose());
ModelConstraintsRuntime.apply(model);
ModelIKRuntime.apply(model, targets, poles);
physics.apply(entity, context.simulationOwner, model, transition, baseTransform);
```

Wrong performance/state ownership:

```java
List<CompiledChain> ordered = new ArrayList<>(chains);
Set<String> wanted = new HashSet<>();
Map<String, PivotFrame> frames = new HashMap<>();
model.lastBendNormal = solvedNormal;
```

Correct performance/state ownership:

```java
private final ModelIKRuntime ikRuntime = new ModelIKRuntime();
private final ModelPhysicsRuntime physicsRuntime = new ModelPhysicsRuntime();

ikRuntime.apply(context.simulationOwner, model, targets, poles);
physicsRuntime.apply(entity, context.simulationOwner, model, transition, baseTransform);
```

Wrong Film target evaluation:

```java
for (Replay replay : replays) {
    applyProperties(replay);
    resolveWorldTargets(replay, replayEntityOwner, true, true);
}
```

Correct Film target evaluation:

```java
for (Replay replay : replays) applyPropertiesAndScalarControls(replay);
for (Replay replay : replays) resolveTargetsWithLocalOwner(replay);

collectMatrices(entity, placementOwner, absoluteSemanticBase,
    allowWorldTargetOverrides, allowWorldCollisions, transition);
```

### Morph renderer recursion and film preview hide-player order

`MorphRenderer.renderPlayer(...)` has two separate responsibilities:

- `hidePlayer` cancels vanilla player rendering while film preview renders a
  non-player `MobForm`.
- `FormUtilsClient.getCurrentForm() != null` prevents nested morph replacement
  while a form renderer is already rendering an entity.

Keep the `hidePlayer` special case before the general current-form recursion
guard:

```java
Form currentForm = FormUtilsClient.getCurrentForm();

if (hidePlayer && currentForm instanceof MobForm form && !form.isPlayer())
{
    return true;
}

if (currentForm != null)
{
    return false;
}
```

Wrong: return early for every non-null `currentForm` before checking
`hidePlayer`; this makes `UIFilmPanel` previews show the vanilla player inside
non-player mob forms.

Correct: first preserve the film-preview cancellation path, then skip ordinary
recursive morph rendering.

Tests required:

- `./gradlew.bat compileClientJava` from `new/`.
- Verify a film preview with `MorphRenderer.hidePlayer = true` and a non-player
  `MobForm` cancels vanilla player rendering.
- Verify nested entity rendering from a form renderer does not trigger another
  morph replacement pass.

### Particle camera-facing parity

Particle editor previews and outside-editor particle rendering both feed
`ParticleEmitter.cYaw`, `cPitch`, `cX`, `cY`, and `cZ` before
`ParticleComponentAppearanceBillboard` builds facing transforms.

When changing camera-facing behavior, keep these two overloads semantically
equivalent:

```java
ParticleEmitter.setupCameraProperties(mchorse.bbs_mod.camera.Camera camera)
ParticleEmitter.setupCameraProperties(net.minecraft.client.Camera camera)
```

The editor preview is the target visual behavior. If Minecraft camera angles are
used outside the editor, convert them to the same yaw/pitch semantics as the BBS
camera overload before assigning `cYaw` and `cPitch`.

Good case: `lookat_xyz`, `lookat_y`, `lookat_direction`, `rotate_xyz`, and
`rotate_y` face the same way in the particle editor preview and in world/form
rendering.

Bad case: one setup path assigns raw `camera.getYRot()` / `camera.getXRot()`
while the other applies BBS camera yaw/pitch conversion, causing outside-editor
particles to appear flipped or rotated relative to the editor.

Editor GUI previews are a separate perspective contract. Do not call the normal
world camera overload from `UIParticleSchemeRenderer`; use the GUI camera setup
path and set `ParticleEmitter.PERSPECTIVE_GUI`. Billboard rendering must then
apply the Blockbuster GUI branch for non-look-at modes:

```java
cameraYaw = 180F - cameraYaw;
width = -width;
height = -height;
```

World-facing paths should use first/third-person perspective flags only. The
front third-person path flips billboard width, while the GUI path flips
non-look-at billboards as Blockbuster did. Mixing the world yaw conversion with
the GUI perspective branch double-flips `rotate_xyz` / `rotate_y` and makes the
particle editor disagree with in-world rendering.

`ParticleFormRenderer` has a separate coordinate-space contract from the
particle editor preview. During NeoForge world rendering, `RenderLevelStageEvent`
passes a camera-relative `PoseStack` while the view matrix lives in
`event.getModelViewMatrix()` / `RenderSystem`. In this path, use
`context.stack.last().pose()` directly as the emitter render matrix, derive
`emitter.lastGlobal` from that matrix's translation plus `context.camera.position`,
and render particles with an identity local stack because billboard vertices
already subtract `emitter.cX`, `cY`, and `cZ`.

The UI/model-preview path still uses the GUI camera contract: invert
`context.camera.view` to recover the emitter world position from the preview
matrix, then render through a stack containing `context.camera.view`. Do not use
that preview conversion in world rendering, or a particle form's emitter position
will be rotated by the camera and the effect will appear to move when the camera
moves.

Wrong:

```java
Matrix4f matrix = new Matrix4f(context.camera.view).invert();
matrix.mul(context.stack.last().pose());
context.stack.setIdentity();
context.stack.mulPose(new Matrix4f(context.camera.view));
```

Correct for world rendering:

```java
Matrix4f matrix = context.stack.last().pose();
Vector3f translation = matrix.getTranslation(temp);
emitter.lastGlobal.set(translation.x, translation.y, translation.z)
    .add(context.camera.position.x, context.camera.position.y, context.camera.position.z);
context.stack.setIdentity();
```

Tests required:

- `./gradlew.bat compileClientJava` from `new/`.
- `./gradlew.bat build` from `new/` before reporting completion.
- In-game visual check: a `ParticleForm` using `docs/test1/黑洞1.json` stays
  fixed in world space while the camera moves.
- Particle editor visual check: `docs/test2/护盾5-0-stay.json` remains visible
  in the editor preview.

### Particle event JSON round-trip contract

Particle event support crosses parser/model, runtime emitter dispatch, and Java editor UI. Keep the JSON shape owned by shared model helpers instead of recasting raw `BaseType` in each consumer.

Contracts:

- `ParticleScheme.events` owns top-level `particle_effect.events` as an insertion-ordered event-id map.
- `ParticleEventTriggerList` accepts a string or list of strings; serialize one trigger as a string and multiple triggers as a list.
- `ParticleEventTimeline` keeps original string keys for serialization while parsed numeric values drive runtime ordering.
- `ParticleLoopingDistanceEvents` keeps each entry's `distance` type and `effects` trigger-list shape unless the user edits that entry.
- `ParticleCollisionEvents` owns `minecraft:particle_motion_collision.events` as a Snowstorm-compatible list of `{event, min_speed}` entries. It may parse legacy string/list references for compatibility, but must preserve the original raw payload until the user edits it.
- Collision event object entries without `min_speed` use the documented default threshold at runtime; legacy string/list event references remain zero-threshold compatibility triggers.
- Event nodes support `sequence`, `randomize`, `weight`, `particle_effect`, `sound_effect.event_name`, `expression`, and `log`; preserve unknown keys through load/save.

Validation/error behavior:

- Missing named event id: keep the trigger reference editable and skip runtime dispatch.
- Unsupported trigger payload shape: preserve raw `BaseType` until edited.
- Invalid timeline/distance key: preserve the original key; runtime ordering may treat it as zero.
- Collision event entries without an `event` string or with unsupported payload data: preserve raw `BaseType` until edited; runtime may skip entries that cannot be resolved to an event id.
- Event node fields with unsupported shapes, such as non-list `sequence` / `randomize`, non-object `particle_effect`, or non-object `sound_effect`, must be written back through raw/extra preservation instead of being dropped.
- Empty event maps/lists: omit the corresponding JSON field.

Tests required:

- `./gradlew.bat build` from `new/`.
- Round-trip a sample containing named events, emitter events, particle events, and collision events.
- Check timeline, travel-distance, looping-distance, and collision triggers dispatch once per crossing. Collision checks must include `min_speed` gating.

Wrong: rewrite event components from UI-only fields and drop unknown JSON members.

Correct: parse once into shared `particles.events` helpers, keep raw/extra data at the JSON boundary, and have UI/runtime consume the normalized helpers.

### Export session temporary-resource ownership

Video export sessions may own temporary audio files before the recorder starts,
while it is recording, and while FFmpeg is finishing. The session is the owner
of those files, so every terminal lifecycle path must reach the session's
teardown/reset logic before the UI or controller that exposes the session is
discarded.

Required paths:

- prepare or audio-render failure;
- warmup cancellation;
- recorder/FFmpeg startup failure;
- manual cancellation;
- natural completion;
- client disconnect or world unload.

On client disconnect, cancel the panel export session while the dashboard and
film panel are still reachable, then clear the dashboard reference. Stopping
only the underlying recorder is insufficient: it bypasses session-owned
temporary-file cleanup, and warmup may not have started the recorder at all.

Temporary files must be created with unique ownership (for example,
`Files.createTempFile`) and cleanup must only target the file tracked by that
session. Never derive a cleanup target from a reusable user filename.

Tests required:

- `./gradlew.bat testVideoExportUtils` from `new/`.
- `./gradlew.bat build` from a clean `new/` worktree that excludes unrelated
  untracked resources and sources.
- In-game: disconnect once during panel-export warmup and once during active
  recording, then verify no owned `bbs-export-*.wav` remains.

Wrong: clear the dashboard and call `videoRecorder.stopRecording()` directly.

Correct: call the panel recorder/session cancellation path before clearing its
UI owner, so teardown and reset run exactly once.

### World export encoding and presentation ownership

F4/F6 world export has two output consumers with different composition rules:

- the encoder texture receives the private world framebuffer before the
  operator-only recording overlay;
- the player window receives the same live framebuffer plus the recording
  overlay after the vanilla target is rebound.

Switching the main target back is not presentation. When custom-size world
export is active and no BBS `UIScreen` owns a local preview, blit the private
framebuffer to the window target every frame. When a BBS menu is present, do
not full-screen blit because the menu owns preview placement and will compose
it into a bounded region.

Validation must prove both outputs independently: movement and camera rotation
stay live in the window, the encoded video matches the world, the recording
indicator stays out of encoded frames, and Film/Morph UI is not covered.

### Structure form port contract

Structure forms cross the form registry, serialized form data, Java editor UI,
client renderer, and BBS resource provider. Keep the contract stable when
extending or optimizing structure rendering.

Signatures and fields:

- Registry id: `Link.bbs("structure")`.
- Model class in the standalone plugin:
  `mchorse.bbs_structure_addon.forms.StructureForm`.
- Required serialized field: `structure_file` string.
- Compatibility fields from the standalone StructureForm addon:
  `color`, `biome_id`, `emit_light`, `light_intensity`,
  `tint_block_entities`, `pivot_x`, `pivot_y`, and `pivot_z`.

Registration points:

- Server/common form registry: `BBSMod.onCommonSetup()`.
- Client renderer registry: `FormUtilsClient.register()`.
- Form palette/category: `ExtraFormSection`.
- Editor panel registry: `UIFormEditor`.

When a form is moved to a standalone addon/register-event path, keep all four
registration layers intact. Common-side addon events can register serialized
form ids and resource packs, but they do not replace client-only static
registries. Structure forms currently require:

- `RegisterFormsEvent` -> `Link.bbs("structure")` / `StructureForm.class`.
- `RegisterFormCategoriesEvent` -> add a `new StructureForm()` to the extra
  form category.
- `RegisterSourcePacksEvent` -> register `WorldStructuresSourcePack` so saved
  world structures are exposed as `world:` links.
- Plugin client initializer ->
  `FormUtilsClient.register(StructureForm.class, StructureFormRenderer::new)`.
- Plugin client initializer ->
  `UIFormEditor.register(StructureForm.class, UIStructureForm::new)`.
- Plugin client initializer -> register any string-keyframe controls through
  `UIStringKeyframeFactory.registerDecorator(...)`; the core
  `UIStringKeyframeFactory` must not import Structure addon UI classes.

Standalone addon resource contract:

1. Scope / Trigger: external BBS addons that need bundled assets or language
   files must register a provider source pack for their own jar, not the BBS
   core jar.
2. Signatures:
   `new InternalAssetsSourcePack(String modId, String prefix, String internalPrefix, Class clazz)`.
3. Contracts:
   - `modId` is the NeoForge mod id owning the jar to inspect.
   - `prefix` is the BBS provider source, such as `bbsstructureaddon`.
   - `internalPrefix` is the classpath folder inside that jar, such as
     `assets/bbsstructureaddon/assets`.
   - `clazz` is a class loaded from the target addon jar.
4. Validation and error behavior:
   - Unknown `modId` falls back to `clazz.getProtectionDomain()` lookup.
   - Missing provider assets should make lookups fail as missing assets, not
     crash startup.
5. Good/Base/Bad cases:
   - Good: Structure addon registers
     `InternalAssetsSourcePack("bbsstructureaddon", "bbsstructureaddon", "assets/bbsstructureaddon/assets", StructureAddon.class)`
     and language loads from `bbsstructureaddon:strings/en_us.json`.
   - Base: BBS core keeps using `new InternalAssetsSourcePack()`.
   - Bad: external addon registers `Link.assets("strings/en_us.json")`; provider
     may resolve the BBS core language file first.
6. Tests required:
   - `./gradlew.bat build` from `new/`.
   - `./gradlew.bat build` from the standalone addon project.
   - Verify plugin language links use the plugin source prefix, not
     `assets:strings/...`.
7. Wrong vs Correct:
   - Wrong: keep a Structure form class under `mchorse.bbs_mod` and bind it from
     `BBSMod` directly.
   - Correct: register the standalone plugin through `BBSMod.registerAddon` and
     keep `new/` limited to reusable extension points.

Standalone addon collector contract:

- External NeoForge addons must call
  `BBSMod.registerAddon(String addonId, Supplier<? extends BBSAddonMod> supplier)`
  during their mod constructor/bootstrap.
- `BBSAddonRegisterEvent` is still valid for code listening on BBS' own mod bus
  (for example internal/demo addons), but a separate addon mod's own mod bus does
  not receive events posted on BBS' mod bus.
- `BBSMod.registerAddon(...)` queues registrations that arrive before BBS creates
  its collector and flushes them when the collector is bound.
- The public external-addon API uses
  `BBSAddonCollector.registerExternal(...)`, which accepts constructor-time
  external registrations even after the event-only `BBSAddonRegisterEvent`
  window is closed. This covers NeoForge load orders where BBS constructs and
  closes its own construct event before the dependent addon mod constructor runs.
- External registrations must still arrive before `BBSAddonBridge` bridges the
  collector into `BBSMod.events` to receive startup events such as
  `RegisterSourcePacksEvent` and `RegisterFormsEvent`. If an addon registers
  after the bridge, BBS must reject it instead of attaching it to future
  internal events. Half-attached v1 addons are unsafe because they miss startup
  registration events.
- Debug logging for this path should identify: external registration accepted or
  rejected, collected addon ids before common setup, bridge target ids, and each
  addon handler receiving `RegisterFormsEvent` / `RegisterFormCategoriesEvent`.

Wrong: a standalone plugin only calls `modBus.addListener(...BBSAddonRegisterEvent...)`
on its own mod bus and then expects BBS form categories to include its forms.

Correct: a standalone plugin calls `BBSMod.registerAddon("myaddon", MyAddon::new)`
from its constructor/bootstrap, then the collected addon subscribes to
`RegisterFormsEvent`, `RegisterFormCategoriesEvent`, and
`RegisterSourcePacksEvent`.

### NeoGradle standalone-addon source-set runtime contract

#### 1. Scope / Trigger

Apply this contract when a standalone NeoForge addon creates a custom client
source set or keeps physical client sources outside `src/main/java`. A release
JAR can contain manually-added client output while `runClient` still omits that
output from the development mod classpath.

#### 2. Signatures

The tested single-mod-source layout is:

```groovy
sourceSets {
    main {
        java.srcDirs = ['src/main/java', 'src/client/java']
        resources.srcDirs = ['src/main/resources']
    }
}
```

Client isolation remains a Java lifecycle contract: the common `@Mod` class
checks `FMLEnvironment.dist` and reflectively loads the client entrypoint only
on `Dist.CLIENT`.

#### 3. Contracts

- NeoGradle `runClient` treats the default `main` source set as the development
  mod source unless the build explicitly configures additional mod sources.
- `jar { from sourceSets.client.output }` changes the published JAR only; it
  does not prove that `runClient` can load those classes.
- A small client-only addon may keep `src/client/java` as a physical directory
  while adding it to `sourceSets.main.java.srcDirs`. Do not also compile it as a
  second source set.
- The common entrypoint must not directly initialize client classes on a
  dedicated server. Use a dist guard plus reflection/another physical-side-safe
  boundary.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Published JAR contains client class, `runClient` reports `ClassNotFoundException` | Fix the development mod source layout; do not treat JAR contents as sufficient |
| Client directory is included in `main` and a custom `client` source set | Remove the duplicate compilation path |
| Dedicated server loads the common addon entrypoint | Client entrypoint is not resolved or initialized |
| Client development run starts | Addon descriptor is accepted and the client initialization marker appears before UI testing |

#### 5. Good / Base / Bad Cases

- Good: one NeoGradle mod source compiles both physical Java directories, while
  a dist-guarded starter reflectively loads the client implementation.
- Base: all addon code already lives in `src/main/java` and client classes are
  protected by physical-side-safe lifecycle boundaries.
- Bad: append `sourceSets.client.output` only to `jar`, see a successful build,
  and assume `runClient` uses the same classpath.

#### 6. Tests Required

- Run `gradlew.bat check build --no-daemon --console=plain` from the addon.
- Inspect the final JAR for the client entrypoint and resources.
- Run a client smoke test and assert there is no `Failed to create mod
  instance` / client-entrypoint `ClassNotFoundException`, and that the addon's
  client initialization log marker appears.

#### 7. Wrong vs Correct

Wrong:

```groovy
sourceSets { client { java.srcDirs = ['src/client/java'] } }
jar { from sourceSets.client.output }
```

This may publish the right bytes while leaving the development mod classpath
without the client entrypoint.

Correct:

```groovy
sourceSets {
    main { java.srcDirs = ['src/main/java', 'src/client/java'] }
}
```

Addon API 2.0 timing and compatibility contract:

1. Scope / Trigger: changes to BBS addon APIs, collector windows, registration
   events, public facades, client hooks, or network facade must preserve the
   lifecycle below. This is a cross-layer contract across NeoForge mod
   construction, BBS construct/common setup, internal events, client source set,
   and addon diagnostics.
2. Signatures:
   - v2 public entry:
     `BBSApi.registerAddon(BBSAddonDescriptor, Supplier<? extends BBSAddon>)`.
   - v2 alternate entry:
     `BBSApi.registerAddon(Supplier<? extends BBSAddon>)`.
   - v1 public entry:
     `BBSMod.registerAddon(String, Supplier<? extends BBSAddonMod>)`.
   - v1 event entry:
     `BBSAddonRegisterEvent#register(String, Supplier<? extends BBSAddonMod>)`.
   - Diagnostics entry:
     `BBSApi.addonDiagnostics()`.
3. Contracts:
   - BBS constructor creates `BBSAddonCollector`, `BBSAddonManager`, and
     `NeoForgeLoaderAccess` before binding queued addon registrations.
   - `BBSAddonRegisterEvent` is the v1 event-only window and closes at the end
     of `FMLConstructModEvent`.
   - v1 external constructor registrations are accepted only until
     `BBSAddonBridge` bridges the collector into `BBSMod.events`.
   - After the bridge, `BBSAddonCollector.closeExternalRegistrationWindow()`
     must run and late v1 external registrations must be rejected.
   - v2 registration closes before common registration facades run.
   - v2 callbacks must be wrapped in an execution boundary that catches
     `Exception` and `LinkageError`, including addon construction and
     descriptor resolution.
   - Common API packages must not import client-only Minecraft classes.
     Client-only API belongs under `src/client/java`.
   - `NetworkCompat` frozen payload IDs must not be modified by API 2.0.
4. Validation and error matrix:
   - Blank addon id -> reject and warn.
   - Null addon or supplier -> reject and warn.
   - Duplicate addon id -> first wins, later registration rejected.
   - v1 event registration after construct window -> reject.
   - v1 external registration after bridge window -> reject.
   - v2 registration after v2 window closes -> reject.
   - v2 descriptor throws or is invalid -> mark diagnostic `FAILED` or
     `SKIPPED`; do not crash core startup.
   - Client hook throws -> log and continue the render/tick path.
5. Good/Base/Bad cases:
   - Good: external addon calls `BBSApi.registerAddon(...)` or
     `BBSMod.registerAddon(...)` from its mod constructor; BBS queues or
     accepts it before common setup and the addon receives startup registration
     events.
   - Base: internal development addon listens for `BBSAddonRegisterEvent` on
     BBS' own mod bus and registers during construct.
   - Bad: addon registers after `BBSAddonBridge` has run and BBS attaches it to
     future events only. This misses `RegisterSourcePacksEvent`,
     `RegisterFormsEvent`, and `RegisterSettingsEvent`; reject instead.
6. Tests required:
   - `./gradlew.bat compileJava` from `new/`.
   - `./gradlew.bat compileClientJava` from `new/`.
   - `./gradlew.bat build` from `new/`.
   - Development self-check must verify v1 late event rejection, v1 external
     before-bridge acceptance, v1 external after-bridge rejection, and duplicate
     first-wins behavior.
7. Wrong vs Correct:
   - Wrong: when `addonsBridged == true`, call `events.register(addon)` for a
     late v1 external addon and warn that startup events may be missed.
   - Correct: close the v1 external registration window immediately after the
     bridge and reject late v1 external registrations.

### Connector legacy BBS addon compatibility contract

#### 1. Scope / Trigger

Apply this contract when a Fabric BBS addon is expected to run through Sinytra
Connector while FSR remains a plain NeoForge mod with no required Fabric Loader
dependency.

#### 2. Signatures

- Legacy Mixin target: `public void BBSMod.onInitialize()` with descriptor `()V`.
- Optional importer: `FabricAddonEntrypointImporter#importInto(BBSAddonCollector)`.
- Imported v1 entrypoints use the existing collector registration and bridge
  path; they do not create a second addon event bus.

#### 3. Contracts

- Fabric Loader types are referenced only by reflective class-name strings.
  Common classes must not import, implement, extend, or expose Fabric Loader
  types in fields, parameters, return values, or bytecode descriptors.
- Query Fabric `getEntrypointContainers("bbs-addon", BBSAddonMod.class)` only
  when Fabric Loader is present. Preserve each provider mod id for duplicate
  ownership and diagnostics.
- Import candidates before `BBSAddonBridge` closes external registration. The
  existing first-registration-wins identity registry remains authoritative.
- Invoke the legacy hook once from FSR common setup after forms and clip
  registries exist, and before `RegisterSettingsEvent`. The hook body stays
  empty so addon Mixins can inject at its tail without adding Fabric lifecycle
  interfaces to FSR.
- Isolate provider metadata, entrypoint construction, type, runtime, and linkage
  failures. One broken Fabric addon must not prevent later candidates or plain
  NeoForge startup.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Connector/Fabric Loader absent | Return an unavailable, zero-count no-op without warning-level noise. |
| Loader present with no `bbs-addon` entries | Report an available empty result and leave collector diagnostics empty. |
| Provider metadata or entrypoint fails | Record a `DISCOVER` diagnostic for that source and continue. |
| Entrypoint has the wrong type | Reject it as a discovery failure; do not cast blindly. |
| Provider id duplicates a native registration | Existing owner wins and the Fabric candidate is reported rejected. |
| `onInitialize()V` is absent or moved | Binary compatibility test fails; required addon Mixins would have no injection target. |

#### 5. Good / Base / Bad Cases

- Good: Connector exposes IRLite's `bbs-addon`; FSR imports it into
  `BBSAddonCollector`, bridges it once, invokes the legacy hook, then posts
  `RegisterSettingsEvent`.
- Base: Connector is not installed and the same JAR starts through the normal
  NeoForge collector path.
- Bad: return Fabric entrypoints only from `LoaderAccess#getEntrypoints`; the
  common setup may log them while the internal BBS event bus never registers
  them.
- Bad: add `implements ModInitializer` or a direct `FabricLoader` import to
  `BBSMod`; plain NeoForge then gains a hard optional-loader linkage.

#### 6. Tests Required

- `testAddonLifecycle` covers loader absence, empty discovery, successful
  bridge, duplicate ownership, and isolated bad entrypoints.
- `testBinaryCompatibility` asserts public virtual `BBSMod.onInitialize()V`.
- Run `compileJava`, `compileClientJava`, and `build` from `new/`.
- Inspect the release JAR for the importer classes and use `jdeps`/constant-pool
  inspection to prove Fabric names are reflection strings, not type references.
- Before claiming end-to-end compatibility, run FSR + Connector + the target
  addon and capture its transformed Mixin and addon-registration log.

#### 7. Wrong vs Correct

Wrong:

```java
FabricLoader.getInstance().getEntrypoints("bbs-addon", BBSAddonMod.class);
```

Correct:

```java
new FabricAddonEntrypointImporter().importInto(this.addonCollector);
this.addonBridge.bridgeToInternalBus(events);
```

### UI mirror direct-render surface contract

#### 1. Scope / Trigger

Apply this contract whenever an active BBS `UIScreen` renders content outside
the terminal `Batcher2D` command recorder. The primary cases are model, mob/
entity, item, block, billboard/extruded, particle and other form previews, plus
the world render shown behind the Morph selector. It also governs Film/Replay
world composites when the browser must receive a raster surface instead of a
translated Minecraft shader or GL object.

#### 2. Signatures

The stable logical kinds are additive to FSR Addon/API `2.0`:

```java
enum BBSRenderSurfaceKind {
    FILM_PREVIEW, WORLD_REPLAY, MORPH_WORLD_PREVIEW, FORM_PREVIEW_ATLAS
}
```

Core keeps two fixed physical lanes. WORLD may publish one JPEG carrying any
valid combination of `FILM_PREVIEW`, `WORLD_REPLAY` and
`MORPH_WORLD_PREVIEW`; UI publishes only `FORM_PREVIEW_ATLAS`.

```java
BBSRenderSurfaceRuntime.capture(RenderTarget source,
    Set<BBSRenderSurfaceKind> worldKinds);
BBSRenderSurfaceRuntime.captureUiRegion(RenderTarget source,
    Set<BBSRenderSurfaceKind> uiKinds,
    int logicalX, int logicalY, int logicalWidth, int logicalHeight,
    int logicalScreenWidth, int logicalScreenHeight);

BBSFormPreviewCapture.beginFrame(int logicalWidth, int logicalHeight);
BBSFormPreviewCapture.include(UIContext context, int x1, int y1, int x2, int y2);
BBSFormPreviewCapture.Region region = BBSFormPreviewCapture.finishFrame();
```

`BBSFormPreviewCapture` returns one viewport-clipped bounding union. It is a
single visible region/tile, not a sparse multi-tile atlas. It may cover the
full page only when a direct-GPU renderer's real clipped viewport is itself
full-screen. `recordFormPreviewAtlas(...)` records one quad for that union; it
must not expose a GL texture id or a mutable renderer.

Surface sequence numbers use one client-lifetime allocator shared by both
lanes. Each lane owns an independent capture generation, PBO/readback state,
RGB pool, latest slot and JPEG worker. The private media query uses
`/ws/media?profile=smooth|high|original` for presets or
`/ws/media?profile=custom&width=<w>&height=<h>&fps=<n>&quality=<q>` for a
custom demand; compatibility aliases are `mobile -> smooth` and
`desktop -> high`. The addon profiles are:

```text
smooth   960x540   30 FPS  JPEG Q68
high     1280x720  60 FPS  JPEG Q78
original 1920x1080 120 FPS JPEG Q90
custom   320..1920 x 180..1080, 30..120 FPS, JPEG Q30..95
```

The current Core exposes
`BBSRenderSurfaceDemand.MAX_FRAMES_PER_SECOND == 120`, but older Core jars
released under the same Addon/API `2.0` and mod version `2.3.1` may enforce a
30 FPS maximum. The Web addon must resolve the field reflectively at runtime;
a direct Java reference to this `public static final int` can be compiled into
the addon and therefore cannot identify the Core jar that Minecraft loaded.

#### 3. Contracts

- The public API remains exactly `2.0`; browser/media and collaboration wires
  remain additive `1.1`. These surface kinds are client-only and must not leak
  `UIElement`, `UIScreen`, `GuiGraphics`, GL ids, paths or mutable managers.
- No active viewer means `BBSRenderSurfaceDemand.none()`: do not issue GPU
  blits, PBO readback, hashing or JPEG work merely because a listener is
  registered. A stale demand cache fails closed.
- WORLD aliases share one physical JPEG and one bounded latest stream. UI
  `FORM_PREVIEW_ATLAS` always uses the independent UI lane/handle; a UI
  generation reset must not retire a valid WORLD frame, and vice versa.
- Direct form preview rectangles are collected only while the native UI frame
  is active. Each rectangle is transformed to logical screen coordinates,
  clipped against its `UIContext` viewport, and merged into the one visible
  union. `FormRenderer.renderUI(...)` registers list/picker rectangles.
  `UIModelRenderer.render(...)` registers `this.area` after `renderModel(...)`
  and before `batcher.unclip(...)`, covering full editor renderers such as
  `UIFormRenderer`/`UIPickableFormRenderer`. After all native/`GuiGraphics`
  work is flushed, capture that union's final framebuffer pixels and append
  one surface quad. Do not claim per-cell addressing when the source is one
  union or expand a small viewport to the full page.
- GPU capture stays on the render thread. Rectangular blit/readback must use the
  three-slot PBO/fence path; disable scissor for the copy if needed, then restore
  framebuffer/read buffer, pixel-pack, texture and scissor state exactly. The
  native UI flush must happen before the asynchronous readback is issued.
- Preserve native resolution while the source union fits the negotiated pixel
  budget (`maxWidth * maxHeight`). If the area exceeds that budget, downscale by
  area while preserving aspect ratio. Always clamp the result to the absolute
  protocol limit of 1920x1080 and its pixel count; never allocate an unbounded
  target from a browser request.
- `generation` fences are per physical handle/lane and are reset on stream,
  session, disconnect and world teardown. A callback admitted before reset may
  finish, but cannot publish after the handle's gate rejects it. Encoder input
  is capacity-one latest-wins; reliable UI/input messages are not replaced by
  stale media.
- Changing `smooth`/`high`/`original`/`custom` closes and reopens only the media socket.
  The UI/control socket, mirror session, controller identity and held-state
  ownership remain intact. Zero viewers disables capture.
- Literal media queries are capped at 256 characters. Missing, repeated,
  encoded or non-decimal known fields fail closed to `high`; finite decimal
  custom values are clamped before constructing a second validated
  `MediaRequest`. Active viewers share one encode, so width, height, FPS and
  JPEG quality aggregate component-wise within 1920x1080/Q95/120.
- The current Core keeps accepting 1..120 FPS for API `2.0` addon
  compatibility. The Web addon alone enforces the product's 30 FPS minimum;
  do not raise the public Core minimum to implement a browser setting.
- Addon startup must not assume that an installed `bbs-2.3.1.jar` has the
  current 120 FPS capability. Resolve `MAX_FRAMES_PER_SECOND` reflectively,
  conservatively fall back to 30 when the field cannot be read, and clamp
  preset/custom demands before constructing `BBSRenderSurfaceDemand`. Static
  initialization must remain valid with both 30 FPS and 120 FPS Core jars.
- Capture pacing uses a phase-preserving next-deadline accumulator. A render
  stall skips missed periods and issues at most one capture per render; never
  assign `lastCaptureNanos = now`, which undersamples 60/120 FPS requests on
  144/165 Hz render loops.
- When several PBO fences are ready, select only the highest current sequence.
  If both RGB leases are occupied, discard the encoder's replaceable pending
  frame and retry the pool before dropping the newest PBO. Keep the three PBO,
  two RGB buffers and capacity-one encoder input bounded.
- Media WebSocket deflate stays disabled because JPEG is already compressed.
  Production fan-out uses one final envelope allocation from Core's read-only
  JPEG and shares that byte array across viewers. Browser composition flips
  Surface V coordinates in the affine draw mapping; it must not allocate and
  redraw a full-resolution canvas merely to flip every decoded frame.
- Any new immediate-GPU UI renderer that bypasses both
  `FormRenderer.renderUI(...)` and `UIModelRenderer.render(...)` must call
  `BBSFormPreviewCapture.include(...)` while its native clip is still active.
  Otherwise the browser will replay surrounding controls but show a blank
  viewport even though the Minecraft window is correct.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| No media viewers or all demands are `none()` | No GPU/PBO/JPEG capture; retain no frame slot for the viewer. |
| Direct preview is outside a viewport | Clip it before union; an empty intersection contributes nothing. |
| Several visible form previews | Merge to one bounding `FORM_PREVIEW_ATLAS` union and one quad; do not emit sparse tile metadata. |
| Full editor uses `UIModelRenderer` | Include its clipped `area` after the GPU model draw; a genuinely full-screen area may produce a full-screen union. |
| Direct GPU renderer bypasses both registered owners | Add an explicit `include(...)` hook before claiming Web mirror support. |
| UI and WORLD kinds are requested together | Keep them on separate fixed lanes; reject/avoid aliasing `FORM_PREVIEW_ATLAS` to WORLD. |
| Union area is within profile budget | Keep native source pixels, subject to absolute 1920x1080 limits. |
| Union area exceeds profile budget | Downscale by area/aspect ratio, then enforce absolute width/height/pixel limits. |
| Scissor or framebuffer state differs before readback | Restore the exact prior GL state even when blit, fence or allocation fails. |
| Old callback arrives after reset | Per-handle generation/captured-time gate drops it; it cannot overwrite a newer frame. |
| Quality profile is malformed/repeated/encoded | Server falls back to `high`; `mobile`/`desktop` map only to their compatibility aliases. |
| One viewer changes quality | Reconnect only that media demand path; do not reset UI session or control lease. |
| Custom query has a missing/repeated/encoded/non-decimal known field | Use bounded `high` fallback; do not partially apply the custom request. |
| Custom numeric field is outside its finite range | Clamp at the handshake boundary, then validate the immutable request again. |
| Loaded Core reports or falls back to a 30 FPS maximum | Clamp `high`, `original` and custom demands to 30; log one startup warning and continue loading the addon. |
| Loaded Core exposes the current 120 FPS maximum | Preserve 30/60/120 presets and bounded custom FPS exactly. |
| Runtime Core FPS field cannot be read | Use the known API `2.0` compatibility limit of 30; never fail addon static initialization. |
| 144/165 Hz render loop requests 30/60/120 FPS | Preserve phase within rounding tolerance; do not fall to 28.8/48/72 FPS. |
| Several current PBO slots become ready together | Retire ready slots and submit only the greatest sequence; never let slot order choose an older frame. |
| Encoded JPEG exceeds the 4 MiB media wire limit | Drop before payload copying and issue only a rate-limited diagnostic; never log once per requested frame. |
| World unload, screen close or client stop | Idempotently fence both lanes, release PBO/FBO/RGB/encoder state and release held input through the owning lifecycle path. |

#### 5. Good / Base / Bad Cases

- Good: `FormRenderer.renderUI(...)` contributes viewport-clipped bounds,
  `UIScreen` flushes native drawing, `finishFrame()` returns one union, and the
  frame records exactly one `FORM_PREVIEW_ATLAS` quad over the captured region.
- Good: `UIModelRenderer.render(...)` contributes its current clipped `area`
  immediately after `renderModel(...)`, so model editor grids, models, gizmos
  and overlays survive browser replay.
- Good: Film/Replay/Morph aliases share the WORLD JPEG while the UI atlas uses
  its own handle and `SurfaceGenerationGate` lane; both lanes consume the one
  global client sequence.
- Good: one `profile=custom&width=1440&height=900&fps=90&quality=84`
  handshake becomes one validated request, and changing it preserves the UI
  socket and participant/held-input state.
- Good: the addon reflectively reads the FPS ceiling from the Core class that
  Minecraft actually loaded, then constructs all static preset demands through
  one clamping helper. A legacy 30 FPS Core starts successfully.
- Good: `SurfaceFrameMessageCodec.encodeSurfaceFrame(...)` writes the trusted
  Core JPEG into the final envelope once, then `LatestSurfaceBroadcaster`
  shares it without per-viewer application copies.
- Base: ordinary `Batcher2D` rectangles, textures and glyphs remain draw
  commands and do not become JPEG surfaces just because a surface viewer exists.
- Bad: capture each cell into separate slots and assume browser UVs can recover
  a sparse atlas when Core published one union image.
- Bad: capture the whole screen to make a small direct preview “easy”; this
  wastes bandwidth and turns unrelated native text/controls into lossy media.
- Bad: call `captureUiRegion(...)` before `GuiGraphics`/`Batcher2D` flush, or
  leave scissor/read framebuffer state changed after the blit.
- Bad: give each lane a local sequence counter, let an atlas reset invalidate
  WORLD frames, or reconnect `/ws/ui` when only the quality query changed.
- Bad: reset capture time to `now` after every issue, process ready PBOs in
  array order, deflate JPEG messages, or create a new 1080p flip canvas for
  each browser frame. These cause FPS undersampling, stale-frame latency,
  wasted CPU and large per-viewer backing-store churn.
- Bad: mark an immediate-GPU renderer as covered only because nearby
  `Batcher2D` controls appear in the browser; the model viewport needs its own
  registered region.
- Bad: construct a 60/120 FPS demand directly in a static field or read
  `BBSRenderSurfaceDemand.MAX_FRAMES_PER_SECOND` directly in addon bytecode.
  Same-version legacy jars can reject the demand, while the constant reference
  can be inlined from the build-time Core and hide the runtime capability.

#### 6. Tests Required

- Core deterministic surface placement: run
  `new\gradlew.bat testUiSurfacePlacement compileClientJava --no-daemon
  --console=plain`; assert no-demand gating, Morph painter-first ordering,
  one union quad/bounds, the compiled `UIModelRenderer` registration hook and
  frame/session lifecycle cleanup.
- Core clean gate: run
  `new\gradlew.bat clean testUiSurfacePlacement build --no-daemon
  --console=plain`; assert the final jar contains the current surface hooks.
- Addon targeted gate: run
  `bbs-web-remote\gradlew.bat testWireProtocol testServerSmoke testCoControl
  verifyWeb compileClientJava --no-daemon --console=plain`; assert per-handle
  latest/generation fencing, profile alias/highest-viewer aggregation,
  localStorage/automatic tier decisions, media-only reconnect and surface
  smoothing/UV restoration. The protocol test must also assert 60/120 -> 30
  clamping for a legacy Core ceiling, 120 preservation for a current Core,
  rejection of non-positive ceilings, and successful construction of a
  legacy-compatible demand.
- Pacing/PBO assertions must simulate 144 and 165 Hz render loops at 30, 60 and
  120 FPS, verify stalls do not burst, verify public 1 FPS compatibility and
  rejection above 120, and select sequence 3 from ready `1/2/3` plus sequence
  4 from ring order `4/2/3`.
- Server/browser assertions must cover exact custom and clamped requests,
  malformed fallback, component-wise multi-viewer aggregation, direct encoder
  byte identity, custom localStorage, 250 ms debounce, media-only reconnect and
  bottom-up UV mapping without a per-frame flip canvas.
- Addon release gate: run
  `bbs-web-remote\gradlew.bat clean check build --no-daemon
  --console=plain`; assert jar/manifest output and all protocol/server/browser
  checks pass.
- Real client: with a native form picker, verify model/mob/item/block/particle
  thumbnails, viewport scrolling and clipping, Morph world placement, no stale
  atlas after screen changes, all three quality profiles, and a quality switch
  that leaves UI/control and held input intact. These manual checks remain
  required even when automated tests pass.

#### 7. Wrong vs Correct

Wrong:

```java
for (FormEntry entry : visibleEntries)
{
    BBSRenderSurfaceRuntime.captureUiRegion(mainTarget,
        Set.of(BBSRenderSurfaceKind.FORM_PREVIEW_ATLAS),
        entry.x(), entry.y(), entry.width(), entry.height(), screenW, screenH);
    BBSUiFrameRecorder.recordFormPreviewAtlas(entry.x(), entry.y(),
        entry.width(), entry.height(), screenW, screenH);
}
```

This creates repeated readbacks, treats one union JPEG as sparse tiles, and can
capture before the final UI flush or retain stale per-cell placement state.

Correct:

```java
BBSFormPreviewCapture.beginFrame(screenW, screenH);
renderNativeMenu();                 // FormRenderer and UIModelRenderer call include()
guiGraphics.flush();
BBSFormPreviewCapture.Region union = BBSFormPreviewCapture.finishFrame();

if (union != null)
{
    BBSUiFrameRecorder.recordFormPreviewAtlas(
        union.x(), union.y(), union.width(), union.height(), screenW, screenH
    );
    BBSRenderSurfaceRuntime.captureUiRegion(
        mainTarget, Set.of(BBSRenderSurfaceKind.FORM_PREVIEW_ATLAS),
        union.x(), union.y(), union.width(), union.height(), screenW, screenH
    );
}
```

The corrected path performs one final-flushed UI crop on the UI lane, retains
native-resolution pixels within the bounded budget, and leaves WORLD aliases,
their generation gate and the global sequence independent.

For a direct full editor, the registration belongs inside the common renderer
while its clip is active:

```java
context.batcher.clip(this.area, context);
this.renderModel(context);
BBSFormPreviewCapture.include(
    context, this.area.x, this.area.y, this.area.ex(), this.area.ey()
);
context.batcher.unclip(context);
```

Wrong FPS compatibility handling:

```java
private static final BBSRenderSurfaceDemand ORIGINAL =
    new BBSRenderSurfaceDemand(ALL_KINDS, 1920, 1080, 120, 90);
int maxFps = BBSRenderSurfaceDemand.MAX_FRAMES_PER_SECOND;
```

Correct:

```java
private static final int CORE_MAX_FPS = detectCoreMaxFramesPerSecond();
private static final BBSRenderSurfaceDemand ORIGINAL =
    createDemand(1920, 1080, 120, 90); // clamps against runtime Core

int detected = BBSRenderSurfaceDemand.class
    .getField("MAX_FRAMES_PER_SECOND")
    .getInt(null);
```

### Web Remote runtime port lifecycle contract

#### 1. Scope / Trigger

Apply this contract when adding commands or configuration that starts, stops,
rebinds, or reports the embedded Web Remote HTTP/WebSocket service while the
Minecraft client remains alive. Runtime transport ownership is shorter than
the FSR UI mirror and addon-client lifetime.

#### 2. Signatures

The local client command surface is:

```text
/bbswebremote start [port]
/bbswebremote stop
/bbswebremote restart [port]
/bbswebremote status
/bbswebremote pair
```

Explicit command ports are decimal `1024..65535`. JVM startup configuration
continues to use `bbs.webRemote.port`, `bbs.webRemote.lan`,
`bbs.webRemote.bindHost`, and `bbs.webRemote.allowedHosts`.

#### 3. Contracts

- Runtime stop closes only the embedded server and its authenticated browser
  peers. It releases every participant's held input and media demand but keeps
  the current `BBSUiSessionInfo`, mirror subscription, control coordinators,
  immutable UI asset state and encoder ownership available for a later start.
- Final client stop additionally closes the mirror subscription/coordinators
  and clears the native UI session. Never call final teardown from a runtime
  command.
- A restart starts and primes a candidate before replacing the current server,
  including same-port restart after settings changes. Bind/start/publication failure leaves the current server
  reachable. The exposure policy is rebuilt through `ServerAccessConfig`; a
  port command never enables LAN or widens `allowedHosts`.
- Asset accept/publish and candidate replay/swap share one lifecycle critical
  section. Current-session encoded assets remain replayable across stop/start,
  but retention uses the same 16 MiB item, 64 MiB total and 1024-entry ceilings
  as `ContentAddressedAssetStore`; rejected publications are removed.
- Jetty start/stop from a command runs on a serialized daemon lifecycle
  executor. Only the completion message is marshalled back to the Minecraft
  client thread. Final shutdown prevents queued starts from reopening service.
- Stop or a successful port change clears authentication and disconnects all
  browsers. Every LAN participant must pair again with a distinct one-time code.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Command port below 1024 or above 65535 | Brigadier/command boundary rejects it before lifecycle work. |
| LAN enabled with empty/wildcard Host allowlist | Fail closed; do not fall back to loopback or wildcard exposure. |
| Target port is occupied | Candidate fails, current server/port remains online, and command reports the retained port. |
| Start requests the already-active port | Return an idempotent already-running result. |
| Restart/apply requests the already-active port | Start a replacement so changed LAN/bind/allowlist settings take effect; disconnect old browsers only after success. |
| Asset arrives while a candidate is being primed | It is serialized before or after the swap and exists in the selected server store. |
| Asset exceeds retention/store limits or fails hash/store validation | Reject/discard it without leaving pending bytes retained. |
| Runtime stop followed by start while a BBS screen remains open | New browsers receive the existing session and replayable resource definitions/content. |
| Client final shutdown races a queued command | Final shutdown wins; queued/in-flight start cannot reopen the server. |

#### 5. Good / Base / Bad Cases

- Good: `restart 30000` binds and primes port 30000, atomically selects it,
  resets surface generations, then closes the old authenticated sockets.
- Good: runtime `stop` calls participant-scoped disconnect and clear-input while
  leaving the native `session` and mirror subscription intact.
- Base: JVM startup uses the configured/default port before any command runs.
- Bad: runtime stop calls `control.close()`, `remoteOpen.close()` or
  `mirrorSubscription.close()`; later start has a listening port but no usable UI.
- Bad: published asset definitions survive while their only bytes were owned by
  the stopped server, or an unbounded second asset cache is introduced.
- Bad: call Jetty's five-second stop path directly from a Brigadier callback on
  the Minecraft render/client thread.

#### 6. Tests Required

- Compile client commands and assert explicit `1024`, `65535`, below-minimum and
  above-maximum parsing/validation.
- Server smoke: occupy the candidate port, assert bind failure, then fetch `/`
  from the original port and assert it remains running.
- Asset regression: after publication, assert bytes remain replayable; after a
  rejected publication, assert no pending/replayable entry remains; reset must
  clear the retained session cache.
- Lifecycle regression: cover start/stop idempotence, successful different-port
  replacement, stop/start with an active UI session, and final-shutdown versus
  queued-start ordering.
- Run `bbs-web-remote\gradlew.bat clean check build --no-daemon
  --console=plain` before publishing the replacement addon jar.

#### 7. Wrong vs Correct

Wrong:

```java
void commandRestart(int port) {
    stopServer(); // closes mirror/control final owners on the client thread
    startServer(port);
}
```

Correct:

```java
lifecycle.execute(() -> {
    BBSWebRemoteServer candidate = startAndPrime(validatedAccess(port));
    selectCandidateThenStopPrevious(candidate);
    Minecraft.getInstance().execute(() -> reportResult(candidate.boundPort()));
});
```

### Web Remote reconnect frame replay and control-handoff contract

#### 1. Scope / Trigger

Apply this contract when changing Web Remote UI-session publication, browser
WebSocket reconnect, co-control disconnect handling, or native `UIScreen`
input ownership. These paths are one lifecycle: a browser can reconnect only
if the transport can reconstruct the current UI and resolve its old controls.

#### 2. Signatures

- `LatestUiFrameReplay.publish(sessionEpoch, FrameInput)` retains one immutable
  encoded input for the current UI epoch.
- `CoControlCoordinator.disconnect(peer)` starts the ordinary reconnect grace;
  `disconnectImmediately(peer)` is reserved for server stop and terminal paths.
- `BBSUiInputDispatcher.preemptForLocalInput(screen, sessionId)` transfers the
  current native screen back to physical FSR input on the client thread.

#### 3. Contracts

- Cache only a frame whose native `sessionId` still matches the active session,
  and tag it with the current unsigned `sessionEpoch`. Opening or closing a UI
  session, disconnecting the Minecraft client, and final addon shutdown clear
  the cache. Runtime Web server stop/start preserves it with the live mirror
  session so the replacement server can restore the current UI.
- A new UI WebSocket receives the active `SESSION` gate first, then every
  current resource definition, then the cached frame. Check the socket identity
  and epoch before resource replay and again before frame replay. Never replay
  a frame across epochs, even when its dimensions or command handles look valid.
- Ordinary WebSocket disconnect marks the peer disconnected immediately,
  rejects new input, and starts a 10-second grace period. The lease and held
  snapshot remain resumable only for the same authenticated participant id and
  the same maximum permission. Reconnect rebinds the existing participant;
  grace expiry performs the normal cancellation/release path exactly once.
- Runtime server stop, session replacement, world disconnect, and final client
  stop bypass reconnect grace. They must not leave held native input active.
- Physical FSR input is always available: an in-progress local mouse/key gesture
  blocks remote acquisition, while any physical mouse, wheel, key, or text event
  cancels a current remote held state before the local event is dispatched.
  Remote mouse cleanup uses cancellation rather than a committing release.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Refresh during an unchanged active UI epoch | Send `SESSION`, resources, and the cached frame; do not wait for a future repaint. |
| Old encoder callback finishes after a session change | Drop it; it cannot populate or replay the new epoch cache. |
| Same authenticated controller reconnects within 10 seconds | Rebind the participant and preserve its lease/held snapshot. |
| Grace expires without a valid reconnect | Cancel held buttons, release held keys, and free the lease once. |
| Local FSR input arrives while remote input owns the screen | Cancel the remote gesture, then deliver the physical event normally. |
| Runtime Web server stops | Release browser control immediately but preserve the active UI session/frame for a later start. |

#### 5. Tests Required

- `testServerSmoke` must cover same-epoch latest-frame replay, replacement by a
  newer frame, cross-epoch rejection, and explicit cache clear.
- `testCoControl` must cover reconnect within a shortened deterministic grace,
  release only after expiry, identity/permission mismatch, and terminal cleanup.
- Core `testUiInputMailbox` must cover active-local rejection, local preemption,
  ordered synthetic key release, mouse cancellation, and retained local event
  delivery after the handoff.
- Release gates are `new\gradlew.bat testUiInputMailbox jar` and
  `bbs-web-remote\gradlew.bat check build`, followed by a real browser refresh
  while a Film editor is open.

### Web Remote settings, public proxy, telemetry and text-input contract

#### 1. Scope / Trigger

Apply this contract when exposing Web Remote lifecycle configuration in FSR's
settings UI or changing browser keyboard/IME forwarding. It crosses persisted
FSR values, client-only settings controls, public reverse-proxy trust, browser
telemetry, the Web wire and native `UIScreen` input dispatch.

#### 2. Signatures

- Addon capabilities: `BBSAddonCapability.SETTINGS` and `SOURCE_PACKS`.
- Module: `context.settings().register(Icons.SERVER, "bbswebremote", builder)`.
- Persisted values: `auto_start`, `lan_enabled`, `port`, `bind_host`,
  `allowed_hosts`, optional `public_origin`; port is `1024..65535`.
- Existing wire input kinds remain `KEY` and `TEXT`; no protocol version bump.
- Browser connection details are a local projection over existing UI/media
  messages; they do not add an anonymous status endpoint or wire kind.

#### 3. Contracts

- Register one independent FSR settings module through `BBSSettingsRegistry`.
  Language resources use source prefix `Link.ASSETS` so FSR can resolve
  `strings/<lang>.json`.
- Persisted values may be edited without changing live exposure. Apply, Stop
  and Pair run on the serialized lifecycle executor and publish only bounded
  status text back to the client UI. Never expose bearer tokens.
- LAN remains opt-in and fail closed: an empty or wildcard Host allowlist must
  reject startup. Applying a same-port LAN/bind/allowlist change performs a
  real replacement and invalidates old browser authentication.
- `public_origin` is one exact HTTP(S) origin with no user info, non-root path,
  query or fragment. Public Host and Origin must match its scheme, host and
  effective port. Never trust `Forwarded` or `X-Forwarded-*` merely because FRP
  is present. An exact HTTPS public Origin makes the pairing cookie `Secure`.
- Page, pairing, UI WebSocket and media WebSocket remain on one authority. FRP
  needs no separate WebSocket proxy; it must preserve Host, Origin, Cookie,
  Upgrade, Connection and Sec-WebSocket-Protocol.
- UI and media byte counters are browser-visible application payload. UI bytes
  are observed after WebSocket inflate; media bytes are JPEG plus application
  framing. Label neither as TCP/TLS line throughput or network packet loss.
- FSR textboxes consume `BBSUiTextEvent`. A printable `keydown` targeted at the
  focused canvas sends physical KEY plus TEXT when not composing and no
  Ctrl/Alt/Meta modifier is active. Hidden IME/beforeinput remains the only
  composition source and must not duplicate TEXT.
- Pointer movement while dragging sends coalescible `INPUT_STATE` with current
  coordinates and held-button mask; reliable press/release remains separate.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Port outside `1024..65535` | Clamp first-run fallback or reject at the command/settings boundary. |
| LAN enabled with empty/wildcard allowlist | Fail startup and keep the existing service online. |
| Printable canvas key without command modifiers | Send KEY and exactly one TEXT event. |
| Ctrl/Alt/Meta shortcut or composition/IME target | Send no synthesized canvas TEXT. |
| Drag crosses threshold | Send PRESS, then moved INPUT_STATE with held button, then RELEASE. |
| Pair requested while stopped or loopback-only | Report a bounded failure; issue no code. |
| Empty `public_origin` | Preserve direct loopback/LAN validation exactly. |
| Public origin has wrong scheme/host/port, user info, path, query, fragment or dangling port | Reject configuration/request; never fall back to forwarded headers. |
| Exact HTTPS public origin pairs through plain downstream HTTP | Emit HttpOnly, SameSite=Strict and Secure cookie. |
| FRP public and private ports differ | Accept only when the browser-visible origin is explicitly configured. |
| UI/media diagnostic rate is shown | Name it application bitrate and keep UI/media channels separate. |

#### 5. Good / Base / Bad Cases

- Good: settings Apply schedules `startRuntimeServer(configuredPort, true)` and
  live status reports running mode, bind address, port and browser count.
- Good: `public_origin=https://remote.example.com` authorizes exact public
  HTTPS/WSS Host/Origin while `allowed_hosts` continues to own direct access.
- Good: connection details show sanitized origin, requested/received FPS and
  split application bitrate without retaining JPEG payload references.
- Base: loopback auto-start on port `27165` needs no pairing code.
- Bad: read only JVM properties after showing saved FSR values, block the
  render thread on Jetty, synthesize TEXT for Ctrl+C, or rely on KEY alone for
  native textboxes.
- Bad: accept arbitrary forwarded hosts, rewrite Origin to bypass validation,
  enable deflate for JPEG media, or call sequence gaps TCP packet loss.

#### 6. Tests Required

- Register the settings consumer against two fresh `SettingsBuilder` instances;
  both must contain all persisted values (including `public_origin`) plus the runtime marker.
- Server smoke covers exact public Host/Origin, wrong scheme/port/path and
  dangling ports, different public/private ports, and Secure HTTPS pairing cookies.
- Node lifecycle tests assert sanitized connection details, split UI/media
  bytes/FPS, dimensions, decode/drop and reconnect state.
- Node input regression asserts `KeyA` produces KEY+TEXT, command modifiers and
  IME do not duplicate TEXT, and moved drag state contains coordinates/buttons.
- Java protocol regression asserts punctuation and keypad DOM codes map to the
  expected GLFW constants.
- Run `testWireProtocol testCoControl verifyWeb compileClientJava`, followed by
  `clean check build`, and inspect the jar for both language resource files.

#### 7. Wrong vs Correct

Wrong:

```javascript
canvas.onkeydown = (event) => sendKey(event.code); // textbox never sees text
```

Correct:

```javascript
sendKey(event.code);
if (event.target === canvas && printable(event) && !commandModifier(event)) {
    sendText(event.key);
}
```

Wrong proxy trust:

```java
String origin = request.getHeader("X-Forwarded-Proto") + "://"
    + request.getHeader("X-Forwarded-Host");
```

Correct proxy trust:

```java
URI expected = config.publicOrigin();
boolean accepted = expected != null && exactOriginMatch(expected, requestOrigin);
```

### Web Remote painted telemetry and adaptive quality contract

#### 1. Scope / Trigger

Apply this contract when changing browser media diagnostics, Canvas presentation
feedback, server delivery counters, `LINK_STATS`, or automatic media quality.
These features cross the JPEG media socket, UI JSON socket, server publication
ledger, Canvas renderer and quality reconnect lifecycle.

#### 2. Signatures

Additive UI-socket JSON remains version `v: 1` and does not change binary wire
`1.1` or Addon/API `2.0`:

```text
LINK_PING -> LINK_PONG
PAINT_ACK(sessionEpoch, ackSeq, clockPingId, clockClientRecvUs, paintUs,
          surfaces[<=16]{handle, surfaceSeq, uiFrameHint, captureUs, decodeUs})
LINK_STATS(..., delivery.ui?, delivery.mediaAggregate?)
```

`PAINT_ACK` is at most 2048 UTF-8 bytes and 10 Hz. All `LINK_STATS` sources
share one per-peer 1 Hz limiter. The browser sends a normal ping about every
five seconds so diagnostics can refresh even when no surface is active.

#### 3. Contracts

- A surface is painted only after decode, renderer installation, actual use by
  the current UI's `SURFACE_QUAD`, and completion of that Canvas draw inside
  `requestAnimationFrame`. Decode/application is not presentation or GPU scanout.
- Java `System.nanoTime()` and browser `performance.now()` are different
  monotonic domains. Use the four ping timestamps and only a clock sample whose
  browser receive time is not later than the acknowledged `paintUs`.
- Validate the exact publication tuple against a per-handle 256-entry,
  five-second ledger. Reset ledger, ACK sequence state, clock samples, error
  counters and rate-limit buckets at the session epoch boundary.
- Browser metrics retain at most 300 numeric samples and no JPEG, Blob,
  ImageBitmap, Image or canvas references. Source jitter uses media `timestampUs`,
  not browser receive time.
- Adaptive receive/decode/apply/paint counters use the same surface-frame unit.
  One rAF that draws two surface tokens increments painted by two; counting only
  the callback would make mixed-rate multi-surface screens look unhealthy.
- Per-handle media sequence skips and pending-decode replacements both contribute
  to adaptive drop pressure. They remain application latest-wins/coalescing
  signals and must never be labeled as TCP packet loss.
- `delivery.ui` belongs to the current UI socket. `delivery.mediaAggregate` is
  server-wide because UI and media WebSockets lack an unambiguous shared viewer
  identity. Never feed the aggregate into one browser's adaptive controller.
- Media `replaced/evicted` means server latest-wins coalescing, not TCP packet
  loss. UI success/media sent means asynchronous send completion, not browser
  receipt, application or paint.
- Automatic quality alone uses `smooth 960x540@30/Q68`,
  `high 1280x720@60/Q78`, and `original 1920x1080@120/Q90`, bounded by initial
  device detection. Manual presets and custom mode never adapt.
- Evaluate independent three-second windows after five seconds and at least 15
  received frames. One severe window drops two tiers, two consecutive bad
  windows drop one, ten healthy windows/30 seconds raise one, and every change
  starts a 15-second cooldown. Hidden/no-painted-surface/disconnected/intentional
  reconnect states suspend and reset evidence.
- Judge decode/apply/paint ratios against frames actually received. A healthy
  60 FPS source under a 120 FPS request is not missing 60 frames that were never
  captured. At the lowest tier, a bad aggregate shared stream must not cause a
  reconnect loop.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| ACK tuple absent, expired, wrong epoch or non-canonical decimal | Silently reject; do not close the UI/control connection. |
| Clock sample was observed after `paintUs` | Exclude it from clock selection. |
| ACK exceeds 2 KiB, 16 surfaces or 10 Hz | Reject/rate-limit without entering the input or lease queue. |
| New FSR session opens while old feedback arrives | Lifecycle fence makes old feedback complete before reset or observe the new epoch. |
| No Surface is painted | Ping may refresh delivery diagnostics; auto quality remains suspended. |
| Page hidden or media reconnect is intentional | Reset the current evidence window and make no quality decision. |
| Requested original mode receives a healthy render-limited 60 FPS | Hold original; do not compare receive count with requested 120 FPS. |
| Several media viewers are connected | Label media counters global; do not attribute them to the UI peer. |

#### 5. Good / Base / Bad Cases

- Good: a decoded surface is ACKed only after the same token is consumed by an
  actual Canvas draw; different paint timestamps are sent in separate batches.
- Good: two bad windows lower high to smooth, then recovery waits for ten
  healthy windows and the cooldown.
- Base: an old browser ignores optional `delivery`; a new browser displays
  "old server did not provide" when it is absent.
- Bad: subtract `captureUs` directly from `performance.now()`, call sequence
  gaps packet loss, use media aggregate for one viewer's auto quality, or lower
  120 FPS solely because Minecraft produced 60 FPS.

#### 6. Tests Required

- Java telemetry: exact tuple validation, future clock exclusion, u32 ACK wrap,
  reset counters/buckets, viewer permission isolation, 2 KiB/16-surface limits,
  total `LINK_STATS <= 1 Hz`, and ping-only stats without a Surface.
- Server delivery: latest replacement/eviction/failure, per-sink/global bytes,
  duplicate completion, reset epoch isolation and `Long.MAX_VALUE` saturation.
- Web: no-rAF/no-`SURFACE_QUAD` means no ACK; exact rAF draw means one ACK;
  different `paintUs` values batch separately; nested delivery parsing and old
  message compatibility.
- Adaptive Web tests use a fake clock and cover severe drop, bad/bad drop,
  cooldown, 30-second recovery, hidden/no-surface/intentional reconnect,
  manual disable, lowest-tier hold, sparse ticks, mixed-rate dual surfaces and
  healthy 60 FPS at a 120 FPS request.
- Release gate: run `testWireProtocol testServerSmoke testCoControl verifyWeb
  compileClientJava`, then `clean check build`.

#### 7. Wrong vs Correct

Wrong:

```javascript
const latencyMs = performance.now() - captureUs / 1000;
const receiveRatio = received / (requestedFps * 3);
```

Correct:

```javascript
const clock = bestClockSampleAtOrBefore(paintUs);
const deliveryRatio = decoded / Math.max(1, received);
```

The corrected path maps monotonic clocks before computing latency and judges
browser pressure only over frames that actually existed.

World structure provider lookup:

- `/bbs structures save <namespace:path>` writes under the world
  `generated/<namespace>/structures/...` tree.
- `BBSMod.getWorldFolder()` is the source of truth for that provider and may be
  null before a world is loaded; lookup must return empty/missing rather than
  throwing.
- Listing `world:structures` must treat `structures` and `structures/` as the
  root and recurse through all namespaces so newly saved structures appear in
  the picker.
- Links exposed to UI should keep the provider form
  `world:structures/<namespace>/<path>.nbt`; renderer path normalization may
  convert that back to the Minecraft id `<namespace>:<path>`.

Validation and error behavior:

- Empty `structure_file` renders nothing.
- Invalid resource ids, missing templates, missing client level, and unreadable
  NBT files must skip rendering without crashing previews or world rendering.
- Resource lookup should accept both Minecraft structure ids such as
  `minecraft:house` and BBS asset links such as `assets:structures/house.nbt`.
- Structure picker UI should discover every registered provider source's
  `structures/` folder instead of hard-coding only `assets:`.

Tests required:

- `./gradlew.bat build` from `new/`.
- `./gradlew.bat build` from `bbs-structure-addon/` after rebuilding `new/`.
- Verify the four registration points still reference `StructureForm`.
- Round-trip serialized data containing the compatibility fields above; do not
  drop fields just because the current renderer does not consume all of them.

Wrong: replacing `structure_file` with a new field such as `structure_id` and
only updating the editor.

Correct: keep `structure_file` as the persisted source of truth, add optional
fields only through `StructureForm`, and make renderer/UI consume the same
model values.

### UI gesture release and cancellation contract

#### 1. Scope / Trigger

Apply this contract when a mouse press can outlive one dispatch, cross a
scroll/overlay wrapper, or be released by screen replacement, disconnect, or
local/remote controller handoff.

#### 2. Signatures

- `mouseReleased(UIContext)` is a real physical terminal event and may commit.
- `mouseCanceled(UIContext)` is a lifecycle terminal event and must not commit.
- `MouseGestureOwnership` binds the initiating button and generation.
- `UIContext.PointerEventFrame` owns one pointer event's viewport stack and
  pointer fields; `UIBaseMenu` opens it with try-with-resources.

#### 3. Contracts

- A press acquires one `(button, generation)` token. Only a matching physical
  release may run the action, drop, accept, or drag-end callback.
- The element returned by press dispatch is the release owner. Later overlays
  or siblings must not intercept that physical release from the captured owner.
- A wheel event is a discrete, current-generation gesture. It routes through
  the current root/viewport exactly once, and a nested wheel dispatch restores
  the outer event's coordinates and both wheel axes before the outer callback
  resumes. Wheel callbacks and their deferred mutations may not cross a screen
  lifecycle generation.
- Every click, release, scroll, and cancel gets a fresh viewport stack. A nested
  event restores the exact outer stack object, shifts, coordinates, button, and
  wheel axes in `finally`; a top-level event retains its public event fields but
  discards transient viewport state.
- Menu viewport push/pop is balanced inside the event frame. A same-frame extra
  pop remains an invariant failure, and cleanup preserves an earlier callback
  failure with any pop failure suppressed.
- Overlay barriers, hierarchy replacement, screen close, disconnect, and
  controller handoff cancel the captured token before changing ownership.
- Timeline elements that need to rebuild a property panel during a press defer
  that hierarchy mutation until release; a canceled press restores its initial
  selection instead of leaving the timeline and property panel out of sync.
- A numeric geometry handle stores both the property's press-time value and the
  press-time raw ray/geometry coordinate. Movement writes
  `clamp(startValue + currentRaw - anchorRaw)`, never the absolute raw coordinate.
  Press without movement is exact identity even when the raw coordinate differs
  from the property value because of a coarse pick volume or interpolated preview.
- Visible guide geometry, pick geometry, and drag math share one signed local-axis
  convention. Film keyframe drags use the selected keyframe's press-time value as
  `startValue`; an interpolated preview must not become an absolute keyframe write.
- Long-lived elements may compare their owner token with
  `UIBaseMenu.getMouseCaptureGeneration(button)` during render. A changed
  capture generation means the physical release was consumed elsewhere and
  may be recovered exactly once; a matching generation must remain untouched.
- Cancellation retires pending context-menu actions, drags, Gizmos, scrolls,
  and pressed visuals without invoking their normal release callbacks.
- Programmatic `clickItself` is one complete instantaneous gesture. Its
  `finally` block retires only the token created by that invocation; it must
  not release an older physical or reentrant owner.
- Cleanup continues across sibling failures and preserves the first failure
  with later failures suppressed.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Different button is released | Keep the initiating owner active; commit nothing. |
| Overlay appears after press | Route the matching release to the original press owner. |
| Wheel callback reenters wheel dispatch | Give the nested event its own coordinates/axes, then restore the outer event exactly. |
| Nested pointer callback throws | Restore the outer stack and pointer fields, then propagate the original failure. |
| A callback pops the menu viewport early | Final menu pop still fails; do not hide the underflow with an empty-pop guard. |
| ESC, outside click, disconnect, or screen change | Cancel once; execute no pending destructive action. |
| Callback replaces the screen or reenters | Old generation cannot clear the new generation. |
| Programmatic callback throws | Retire its token and rethrow; later clicks still work. |
| One cancellation callback throws | Continue canceling the remaining captured inputs. |
| Timeline panel rebuild is requested during a drag | Defer the rebuild; do not commit selection from `mouseCanceled`. |
| Numeric handle is pressed without mouse movement | Keep the property/keyframe bitwise unchanged even if raw pick geometry is offset. |
| Directional guide uses local `+Z` | Render, pick, and drag against `+Z`; do not negate only the visible guide. |

#### 5. Good / Base / Bad Cases

- Good: a context-menu Delete action runs only on its matching physical release.
- Good: a click callback synchronously dispatches a scroll; the outer click
  resumes with its original viewport object and pointer state.
- Good: a spotlight range handle starts at `8`, its ray anchor is `-37`, and
  unchanged input remains `8`; moving the raw coordinate to `-35` produces `10`.
- Base: an ordinary button press/release invokes its callback once.
- Bad: synthesize `mouseReleased` during disconnect; a pending Delete action
  can run while the UI is being torn down.
- Bad: nested dispatch calls `UIContext.setMouse()` against the shared viewport
  stack and clears the outer element's pending `unapply()` state.

#### 6. Tests Required

- `testUiInputMailbox` covers multi-button ownership, programmatic clicks,
  overlay barriers, destructive menu cancellation, screen generations,
  local/remote handoff, press-owner release routing, nested wheel context,
  exception cleanup, same-frame viewport underflow, and Gizmo cancellation.
- Custom numeric guide tests cover zero-movement identity, continuous first
  movement, lower/upper clamps, every handle type, and Form plus Film keyframes.
- `compileClientJava` verifies every concrete input adapter implements the
  distinct cancellation terminal.

#### 7. Wrong vs Correct

Wrong:

```java
disconnectHeldButtons.forEach(menu::mouseReleased);
```

Correct:

```java
disconnectHeldButtons.forEach(menu::mouseCanceled);
```

Wrong:

```java
context.setMouse(x, y, button);
context.pushViewport(viewport);
root.mouseClicked(context);
context.popViewport();
```

Correct:

```java
try (UIContext.PointerEventFrame ignored = context.beginPointerEvent(x, y, button))
{
    dispatchPointer(root::mouseClicked);
}
```

### Replay editor identity and delayed-callback contract

#### 1. Scope / Trigger

Apply this contract when deleting/reordering Film replays or rebuilding the
selected replay's keyframe editor while render or context-menu callbacks from
the previous editor can still be queued.

#### 2. Contracts

- Replay lookup and selection use object identity, not structural `equals`.
- Deleting the final replay clears the replay editor with `setReplay(null)`
  before controller/channel refresh; it must not index an empty remaining list.
- Every callback installed on a rebuilt `UIKeyframeEditor` captures the editor,
  view, and replay snapshot it was created for. A delayed callback that no
  longer matches the current editor/replay returns without reading or mutating
  replacement state.
- Delayed context-menu actions that mutate pose or IK tracks repeat the same
  identity fence when invoked; guarding only menu construction is insufficient.

#### 3. Tests Required

- `testReplayKeyframeConsistency` covers identity lookup, final-replay empty
  deletion, stable ruler/view capture, and every delayed replay-mutating action
  fence.
- `compileClientJava` verifies the real editor/list call sites.

### Addon API 2.0 JVM binary compatibility contract

#### 1. Scope / Trigger

Apply this contract to changes in public/protected Addon v1, Addon/API 2.0,
compatibility facade, action, network, export, and client API types.

#### 2. Signatures

- `BBSApiVersion.CURRENT` remains exactly `"2.0"`.
- Frozen payload bindings remain `s1..s15` and `c1..c19`.
- `testBinaryCompatibility` owns the retained JVM descriptor baseline.

#### 3. Contracts

- Source compatibility is insufficient. Preserve erased JVM method and
  constructor descriptors, visibility, static/virtual shape, legacy SAM
  cardinality, inherited method resolution, and published enum ordinals.
- New behavior uses additive overloads or types. The legacy descriptor remains
  and delegates or fails closed according to its existing security contract.
- Do not change a public compile-time constant merely to change a runtime
  default; add a runtime constant instead when old callers may have inlined it.
- Client-only additive APIs stay in the client source set; common descriptors
  must not resolve client classes on a dedicated server.

#### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Parameter/return type changes after erasure | Reject unless the old descriptor remains. |
| Legacy SAM gains another abstract method | Reject; old lambdas must still link. |
| Enum gains a value before an old value | Reject; retain old ordinals. |
| Implementation moves to a superclass | Accept only when JVM resolution still finds the same descriptor/visibility. |
| New payload is added | Append after the frozen ids; never renumber existing bindings. |

#### 5. Good / Base / Bad Cases

- Good: add `tryStartRecording(...)Z` while retaining
  `startRecording(...)V` as a delegating descriptor.
- Base: add a new API type without replacing any existing descriptor.
- Bad: change a legacy `void` method to `boolean` and rely on Java source
  recompilation; already compiled addons fail with linkage errors.

#### 6. Tests Required

- Run `testBinaryCompatibility`, `testAddonLifecycle`,
  `testClientStructuralRegistration`, `compileJava`, and `compileClientJava`.
- The compatibility test must assert exact descriptors, constructors,
  visibility, SAM method count, enum ordinals, constants, and inherited lookup.

#### 7. Wrong vs Correct

Wrong:

```java
public boolean startRecording(Film film) { ... } // replaced legacy ()V shape
```

Correct:

```java
public void startRecording(Film film) { this.tryStartRecording(film); }
public boolean tryStartRecording(Film film) { ... }
```


---

## Scenario: Mixin injection helper type placement

### 1. Scope / Trigger

- Apply whenever an injected Mixin method creates, casts, stores, returns, or
  shares a project-defined helper type that becomes part of the transformed
  target class bytecode.

### 2. Signatures

- Mixin classes remain under `mchorse.bbs_mod.mixin` and are registered by
  `bbs.mixins.json`.
- Shared helper types referenced by transformed Minecraft classes must be
  public and live outside `mchorse.bbs_mod.mixin`.

### 3. Contracts

- The configured Mixin package is reserved for Mixin-owned classes. A helper
  referenced from injected target bytecode must use a normal package such as
  `mchorse.bbs_mod.actions`.
- Invocation-local state should remain invocation-local, for example through
  MixinExtras `@Share LocalRef<T>`; moving its type must not turn it into a
  mutable field on the transformed target.
- The helper must be accessible from the target class package after bytecode
  merge. Use a public immutable carrier when the target reads its members.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Injected bytecode references `MixinClass$Helper` | Reject; move the helper outside the configured Mixin package. |
| External helper is package-private | Reject; transformed Minecraft classes may fail access checks. |
| Helper is only used by Mixin transformation metadata and never merged into the target | It may remain Mixin-private. |
| Invocation state must cross HEAD/RETURN injections | Use `@Share LocalRef<PublicExternalType>` and preserve owner/generation checks. |

### 5. Good / Base / Bad Cases

- Good: `LocalRef<BlockBreakRecordingContext>` where the public immutable
  context lives in `mchorse.bbs_mod.actions`.
- Base: primitive or Minecraft-owned values need no project helper type.
- Bad: `LocalRef<ServerPlayerGameModeMixin.BreakRecordingContext>`; NeoForge
  throws `IllegalClassLoadError` when player construction loads the transformed
  server class.

### 6. Tests Required

- Compile the common source set and the affected regression source set.
- Assert the Mixin class bytecode references the external helper internal name
  and does not reference a nested helper in the configured Mixin package.
- Inspect the release JAR for the external class and absence of the forbidden
  `MixinClass$Helper.class` entry.

### 7. Wrong vs Correct

Wrong:

```java
@Mixin(ServerPlayerGameMode.class)
class ServerPlayerGameModeMixin {
    private static final class BreakRecordingContext {}
}
```

Correct:

```java
package mchorse.bbs_mod.actions;

public record BlockBreakRecordingContext(/* invocation state */) {}
```

## Testing Requirements

- A JavaExec regression may deliberately exercise production `ERROR` logging,
  but a passing build must not emit or write those events as unqualified red
  output. Use `src/testSupport/java/mchorse/bbs_mod/test/ExpectedErrorLogCapture`
  to detach only the declared logger(s), capture the exact expected count, and
  validate each message/cause before restoring the original appenders.
- Do not globally mute Log4j, lower production severity, or accept errors by
  substring alone. Any extra count, logger, message, cause, or missing expected
  event must fail the launcher.
- The passing launcher prints a bounded summary such as
  `captured 3 expected failure diagnostics`. A forced full `build` must have no
  `/ERROR]` records in its console output or generated log archives.

## Scenario: Hybrid WebRTC media with reliable WebSocket control

### 1. Scope / Trigger

- Apply when changing Web Remote signaling, ICE/TURN configuration, Surface
  delivery, browser media switching, or DataChannel input/state.
- HTTPS/WSS may terminate at an authenticated reverse proxy. The embedded
  Jetty listener remains HTTP/WS and must not trust forwarded host/origin
  headers; `public_origin` is the exact external origin contract.

### 2. Signatures

- Reliable signaling is additive JSON `v:1` on authenticated `/ws/ui`:
  `RTC_CAPABILITIES`, `RTC_OFFER`, `RTC_ANSWER`, `RTC_ICE_CANDIDATE`,
  `RTC_ICE_END`, `RTC_TRACK_BIND`, `RTC_STOP`, and `RTC_ERROR`.
- Every session-bound message carries unsigned 32-bit `sessionEpoch` and
  `rtcGeneration`. Track binding carries the negotiated `mid` and physical
  Surface `handle`.
- DataChannel label is `bbs-ephemeral`, configured with
  `ordered=false,maxRetransmits=0`.
- Host-only TURN secret property:
  `-Dbbs.webRemote.turnRestSecret=<coturn-rest-secret>`.

### 3. Contracts

- HTTPS/WSS owns page, pairing, configuration, reliable UI/input and RTC
  signaling. WebRTC VideoTracks carry at most four physical Surface lanes.
  `/ws/media` JPEG remains an independent automatic fallback.
- One PeerConnection is allowed per authenticated participant and generation;
  at most four peers and four tracks exist at once. Repeated offers are rejected
  before native peer replacement.
- The browser remains on JPEG until the first RTC frame is drawable. Failed,
  closed, 3-second disconnected, or 12-second negotiation/first-frame timeout
  sends one generation-fenced `RTC_STOP`, releases the peer, and falls back.
- `AUTO` uses host/STUN first and includes a TURN server only with a valid
  short-lived credential. `RELAY_ONLY` is disabled unless at least one
  credentialed TURN URL is available. `WS_ONLY` never creates an RTC peer.
- TURN credentials live 300..600 seconds. The long secret never enters settings
  DTOs, JSON, browser storage or logs. SDP, ICE candidates, IP addresses and
  temporary TURN credentials must not be logged.
- DataChannel messages are bounded to 4096 UTF-8 bytes and rate-limited before
  JSON parsing. Viewer data is silently discarded. Disposable state must use
  an independent admission/watermark and must never advance the reliable WS
  input sequence. Reliable KEY/BUTTON/TEXT/WHEEL/lease and a WS held-state copy
  always remain on WebSocket.
- JPEG-to-I420 conversion is a temporary bounded latest-wins bridge capped at
  1280x720@60. It runs off the render thread and is removed when Core exposes a
  raw RGB Surface branch.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Native library absent or unsupported | Disable RTC capability; keep WS/JPEG and mod startup alive. |
| Stale epoch/generation, second offer, oversized SDP/candidate | Consume/reject without replacing the reliable UI socket or peer. |
| `RELAY_ONLY` without usable TURN credentials | Advertise `enabled=false`, mode `WS_ONLY`. |
| `AUTO` TURN signing failure | Omit TURN, retain host/STUN direct candidates. |
| RTC failure, timeout, local fallback, or browser close | Idempotent `RTC_STOP`; remove peer demand and native resources. |
| Viewer or rate-limited DataChannel input | Silent drop before JSON parsing/control response. |
| RTC track not correlated to exact capture sequence | Do not fabricate existing `PAINT_ACK`. |

### 5. Good / Base / Bad Cases

- Good: WSS authenticates the browser, host/STUN connects directly, the first
  drawable VideoTrack replaces JPEG, and a reliable WS held snapshot remains.
- Base: WebRTC is unavailable or times out; JPEG stays visible and control is
  unaffected.
- Bad: an unordered DataChannel held snapshot advances the same monotonic
  sequence used by reliable key transitions, causing a later WS key-up to be
  rejected as stale.

### 6. Tests Required

- Java core: peer/track/candidate bounds, one offer per generation, generation
  fences, latest-wins I420 bridge, reset/disconnect/close idempotence.
- Native smoke: create and dispose the selected platform factory without making
  mod startup depend on success.
- Server/config: exact JSON fields and sizes, 63 KiB SDP transit, TURN REST HMAC
  vector/TTL/redaction, `AUTO` degradation, `RELAY_ONLY` fail closed, Viewer and
  DataChannel rate isolation, and RTC audience cleanup.
- Browser: MID/handle in either arrival order, first-drawable switch, one-shot
  failed/disconnected/watchdog fallback, `RTC_STOP`, retained-Surface cleanup,
  stale callback fences, and simultaneous DC plus WS held-state delivery.
- Final gate: `clean check build`; inspect the all-JAR for the selected root
  native library, nested Java API, Web resources and `NOTICE-webrtc.txt`.

### 7. Wrong vs Correct

Wrong:

```javascript
const sent = sendHeldOverDataChannel(state) || sendHeldOverWebSocket(state);
```

Correct:

```javascript
sendHeldOverDataChannel(state); // disposable low-latency copy
const sent = sendHeldOverWebSocket(state); // authoritative reliable copy
```

---

## Scenario: CNB Gradle resource budget and quiet-download keepalive

### 1. Scope / Trigger

Apply when changing `new/.cnb.yml`, Gradle memory/worker limits, or the
NeoForge/NeoForm dependency download path used by CNB.

### 2. Signatures

- CNB runner: `runner.cpus: 2` (4 GiB because CNB assigns 2 GiB per CPU).
- Build entry point: `sh ./cnb/gradle-build.sh`.
- Build timeout: `timeout: 30m`.
- Gradle limits: `-Xmx1536m`, `MaxMetaspaceSize=384m`, one worker, no parallel
  execution.
- NeoGradle CI mode: `CI=true` and
  `neogradle.subsystems.decompiler.enabled=false`; fallback decompiler limits
  are 1024 MiB and one thread.

### 3. Contracts

- `main.push` and `main.api_trigger` use the same runner budget and wrapper.
- The wrapper emits a timestamped line every 30 seconds while Gradle is alive.
- On Linux it reports aggregate non-loopback RX/TX rates from `/proc/net/dev`;
  absence of those counters degrades to a liveness message.
- Monitor cleanup must preserve the exact Gradle exit status.
- `BBS_PROXY_KEY` may supply the proxy credential; diagnostics never print it.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Gradle is quiet for a long asset download | Heartbeat continues below CNB's no-output limit. |
| `/proc/net/dev` is unavailable | Print liveness without failing the build. |
| Gradle fails | Stop the monitor and return Gradle's non-zero code. |
| Gradle succeeds | Stop the monitor and return zero. |
| One CNB trigger changes | Update and verify the other trigger in the same change. |

### 5. Good / Base / Bad Cases

- Good: 2-core runner, bounded single-worker JVM, binary patch mode, shared
  wrapper, and 30-second RX/TX output.
- Base: network counters unavailable but the build remains observable.
- Bad: enable `DecompilerExecute` on a 4 GiB runner, request a multi-gigabyte
  decompiler heap beside the Gradle daemon, duplicate scripts, or use a
  foreground heartbeat that masks Gradle's exit code.

### 6. Tests Required

- `git diff --check`.
- Assert exactly two `cpus: 2`, two `timeout: 30m`, and two shared-wrapper
  invocations, with no `cpus: 4`.
- Run `./gradlew help --no-daemon --console=plain` locally.
- On a Linux/CNB runner, run `sh -n cnb/gradle-build.sh` and observe at least
  one heartbeat during a long-running Gradle process.

### 7. Wrong vs Correct

Wrong:

```yaml
runner:
  cpus: 2
# gradle.properties still requests -Xmx7g and the task can remain silent.
```

Correct:

```yaml
runner:
  cpus: 2
stages:
  - name: build
    timeout: 30m
    script: sh ./cnb/gradle-build.sh
```

---

## Scenario: Clean commit-snapshot verification

### 1. Scope / Trigger

Apply when a feature branch is prepared from a dirty worktree, especially when
the edited file also contains changes owned by another task or developer.

### 2. Signatures

- Committed task diff: `git diff <base>..HEAD -- <task-paths>`.
- Clean verification worktree: `git worktree add -b <temporary-branch> <path> <commit>`.
- PR identity: head branch, head SHA, CI run URL, artifact name, and artifact
  expiry recorded in the task metadata.

### 3. Contracts

- Local compilation in a dirty worktree is not evidence that the committed PR
  snapshot compiles; uncommitted dependencies can make it pass accidentally.
- Before pushing, inspect `git diff <base>..HEAD` and verify every changed hunk
  belongs to the task. For an overlapping file, compare the staged/committed
  patch rather than relying on `git status` or the working-copy file.
- Run the required compile and regression tests from a clean worktree at the
  exact commit that will be pushed. A temporary worktree must use its own
  follow-up branch and must not clean, reset, or overwrite the dirty source
  worktree.
- Stage explicit task paths. Do not use repository-wide staging when concurrent
  work is present.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Dirty worktree defines a symbol used by the commit | Clean-snapshot compile must fail before push. |
| One file mixes task and concurrent hunks | Restore or split only the unrelated committed hunks in an isolated worktree. |
| A pushed commit needs correction | Add a follow-up commit; do not amend shared history. |
| Clean local verification passes | Push to the PR branch and require the GitHub Actions build plus artifact-upload step to pass. |

### 5. Good / Base / Bad Cases

- Good: an overlapping file is corrected in a clean temporary worktree, tested,
  committed separately, and pushed to the existing PR branch.
- Base: a clean feature worktree stages explicit task paths and its local build
  represents the same content as the PR head.
- Bad: compilation succeeds only because an uncommitted theme API exists beside
  the task, while the committed PR calls that API without its implementation.

### 6. Tests Required

- `git diff --check <base>..HEAD`.
- Task-specific regression tests and compile from the clean worktree.
- Confirm the PR head SHA equals the verified commit and the downloadable JAR
  artifact belongs to that SHA.

### 7. Wrong vs Correct

Wrong: compile a dirty checkout, stage an overlapping file wholesale, and infer
that the PR contains all symbols visible to the compiler.

Correct: inspect the committed diff, validate the exact commit in a clean
worktree, then push a narrowly scoped follow-up commit if CI exposes drift.

## Scenario: External-renderer OpenGL texture state ownership

### 1. Scope / Trigger

Apply when BBS or an addon uploads, reads back, or temporarily rebinds an OpenGL texture while
Iris, Sodium, or another renderer can change GL state outside Minecraft's `GlStateManager` cache.

### 2. Signatures

- Real binding query: `GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)`.
- Matching bind/restore: `GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)`.
- Optional verification: `GL11.glGetTexImage(...)` while the target texture is still bound.

### 3. Contracts

- A temporary state transaction uses one state authority from query through restore. A real GL
  query must be paired with direct GL bind and direct GL restore; do not cross that boundary through
  cached `GlStateManager._bindTexture` calls.
- Preserve and restore the actual active texture unit, texture binding, pixel-unpack buffer, and
  changed pixel-store fields. A diagnostic readback happens before restoration.
- Synchronous GPU readback is opt-in diagnostics only. Normal rendering must not allocate its
  buffer or call `glGetTexImage` per frame.
- Producer diagnostics compare expected CPU values with actual GPU texels. Their de-duplication key
  includes both sides so a stuck GPU value is still reported when expected input changes.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| External renderer changed the real binding but not Minecraft's cache | Directly bind the owned texture, upload, then directly restore the queried binding. |
| Cached and real state already agree | The same direct transaction remains valid and restores the caller unchanged. |
| GPU texel differs from CPU input in diagnostic mode | Log the target id, first mismatch index, expected transition, and actual encoded values. |
| Diagnostic mode is disabled | Allocate no readback buffer and perform no synchronous texture readback. |

### 5. Good / Base / Bad Cases

- Good: an Iris composite pass leaves an unrelated texture bound; the addon directly binds its R32F
  texture, uploads, verifies it in debug mode, and restores the exact prior object.
- Base: vanilla rendering owns both cache and GL state; the direct transaction is still balanced.
- Bad: query `GL_TEXTURE_BINDING_2D`, call `_bindTexture(ownedId)`, upload, then call
  `_bindTexture(previousId)`. Either cached call can be skipped against stale cache state.

### 6. Tests Required

- Compile and build the owning addon/core on the target Minecraft and Java versions.
- Inspect release bytecode for direct bind/restore calls and the absence of cached binds in the
  transaction.
- In an Iris-enabled client, require GPU readback to match a marker plus at least `0`, baseline, and
  a materially different encoded value; separately prove the consuming program binds and executes.
- Finish with a fixed-camera pixel matrix. Correct upload and binding alone are not consumer proof.

### 7. Wrong vs Correct

Wrong:

```java
int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
GlStateManager._bindTexture(ownedTexture); // may trust stale cache
GL11.glTexSubImage2D(/* ... */);
GlStateManager._bindTexture(previous);
```

Correct:

```java
int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
try
{
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, ownedTexture);
    GL11.glTexSubImage2D(/* ... */);
}
finally
{
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
}
```

## Code Review Checklist

<!-- What reviewers should check -->

(To be filled by the team)
