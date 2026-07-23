# Particle editor events support design

## Architecture and boundaries

Implementation stays in `new/`.

The feature crosses three existing layers:

1. Particle JSON model: `ParticleScheme`, `ParticleParser`, `ParticleComponent*Events`, and collision component raw event data.
2. Runtime emitter: `ParticleEmitter`, `Particle`, lifetime/collision components, and particle manager loading.
3. Editor UI: `UIParticleSchemeEventsSection` plus small reusable UI elements under the particle editor package.

Snowstorm remains a reference implementation. Its Vue files map to Java concepts as follows:

- `EventList.vue` -> named event list/editor in `UIParticleSchemeEventsSection`.
- `EventSubpart.vue` -> recursive Java event-node editor.
- `EventPicker.vue` and `InputGroup.vue` event list/timeline controls -> trigger picker/list/timeline controls in the Events section.
- `export.js` / `import.js` -> `ParticleParser` read/write and `ParticleScheme` model fields.

## Data model

Add a small typed event model instead of spreading raw `BaseType` casts through UI and runtime:

- `ParticleEventNode`
  - optional `List<ParticleEventNode> sequence`
  - optional `List<ParticleEventNode> randomize`
  - `float weight` for randomize children
  - optional `ParticleEventEffect particleEffect`
  - optional `String soundEvent`
  - optional `MolangExpression expression`
  - optional `String log`
  - optional raw passthrough map for unsupported keys
- `ParticleEventEffect`
  - `String effect`
  - enum/string type: `emitter`, `emitter_bound`, `particle`, `particle_with_velocity`
  - optional `MolangExpression preEffectExpression`
- `ParticleEventTriggerList`
  - normalizes a string or list of strings to a list
  - serializes one string as a string and multiple strings as a list, matching Snowstorm export behavior
- `ParticleEventTimeline`
  - ordered map from numeric string/time to trigger list
- `ParticleLoopingDistanceEvent`
  - distance plus trigger list

`ParticleScheme` should own `Map<String, ParticleEventNode> events`, using insertion order for stable UI and round-trip behavior. `ParticleParser.parseEffect()` should parse top-level `events` before components, and `addComponents/addEvents` should write `particle_effect.events` only when non-empty.

Lifetime event components should move from raw `BaseType`-only storage to normalized trigger list/timeline fields while preserving unknown data where necessary. For compatibility, they may still accept raw `BaseType` at parse boundaries, but UI/runtime should consume normalized helpers.

## Runtime flow

Triggers should resolve named event IDs through `ParticleScheme.events`.

Emitter triggers:

- `creation_event`: dispatch in `ParticleComponentEmitterLifetimeEvents.apply()`.
- `expiration_event`: dispatch when emitter stops/expires. If current emitter lifetime code has no explicit expiration hook, add a guarded dispatch point in `ParticleEmitter.stop()` or the lifetime component that causes stop.
- `timeline`: dispatch once when emitter age crosses each threshold for the current loop.
- `travel_distance_events`: dispatch once when total emitter travel crosses each configured distance.
- `looping_travel_distance_events`: dispatch every time incremental travel crosses each configured interval.

Particle triggers:

- `creation_event`: dispatch in `ParticleComponentParticleLifetimeEvents.apply()`.
- `expiration_event`: dispatch before dead particles are removed, with per-particle guard state.
- `timeline`: dispatch once per particle when age crosses each threshold.

Collision triggers:

- Dispatch from `ParticleComponentMotionCollision.collision()` when a collision first occurs or when an event condition matches a documented speed threshold.

Event node execution:

- `sequence`: execute children in order.
- `randomize`: choose one child weighted by `weight`.
- `expression`: evaluate against the firing emitter/particle context after relevant variables are set.
- `log`: print a concise debug message.
- `particle_effect`: create or reuse a sub-emitter depending on type:
  - `emitter` / `emitter_bound`: fire-and-forget sub-emitter.
  - `particle`: manually spawn a particle in a manual-rate effect.
  - `particle_with_velocity`: same as particle plus source velocity transfer.
- `sound_effect`: use Minecraft sound APIs for vanilla/resource sound event IDs where feasible.

Runtime needs loop guards to prevent repeated dispatch each tick after a threshold. Prefer storing fired indices in the emitter and particle runtime state, not component singletons shared by all particles.

## UI design

`UIParticleSchemeEventsSection` should become the single Events workspace with subsections:

1. Named Events
   - list of event IDs
   - add, rename, remove, reorder
   - recursive event-node editor for selected event
2. Emitter Event Triggers
   - creation and expiration trigger lists
   - timeline map editor
   - travel distance map editor
   - looping travel distance list editor
3. Particle Event Triggers
   - creation and expiration trigger lists
   - timeline map editor
4. Collision Event Triggers
   - event list compatible with `minecraft:particle_motion_collision.events`

Use existing BBS controls:

- `UIIcon` with `Icons.ADD`, `Icons.REMOVE`, `Icons.TRASH`, `Icons.MOVE_UP`, `Icons.MOVE_DOWN`, and `Icons.PARTICLE_TAB_EVENTS`.
- `UITextbox` for IDs, Molang expressions, timeline keys, sound IDs, and particle effect IDs.
- `UICirculate` for particle effect type.
- `UITrackpad` for weights/distances if it fits the existing style.
- `UIList`/custom list elements for event IDs and selectable trigger entries.

Every edit should call `editor.dirty()` for structural changes or `editor.markUndoBoundary()` for text edits, following the existing particle editor convention.

## Compatibility and migration

- Existing particles without events continue to load/save unchanged.
- Existing particles with unsupported event fields keep those fields via raw passthrough.
- A string event reference remains a string when it has one entry; multiple references serialize as a list.
- Invalid event IDs should stay editable rather than being dropped, because external Bedrock files can reference events created later.
- Timeline keys should preserve original string formatting when possible, while parsed numeric values drive runtime ordering.

## Risks and trade-offs

- Full sub-emitter execution is the largest runtime risk because BBS currently has one main emitter per form/editor preview. The implementation may need child-emitter ownership and rendering/update traversal.
- Sound execution depends on how BBS should map Bedrock `level sound event` names to Minecraft/NeoForge resource sound events.
- Top-level event editing can be completed independently from runtime execution, but claiming Snowstorm parity requires runtime dispatch for at least expression and particle-effect nodes.
- Raw `BaseType` preservation is safer for compatibility, but typed model helpers should own decoding to avoid duplicated casts across UI/runtime.

## Rollback

The clean rollback point is all files touched under:

- `new/src/client/java/mchorse/bbs_mod/particles/**`
- `new/src/client/java/mchorse/bbs_mod/ui/particles/**`
- `new/src/client/java/mchorse/bbs_mod/ui/UIKeys.java`
- `new/src/client/resources/assets/bbs/lang/**` if localization keys are added

Because the parser and runtime are shared by editor and in-world rendering, validate with a full `new` build before marking complete.
