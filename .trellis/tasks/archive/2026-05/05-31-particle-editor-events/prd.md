# Particle editor events support

## Goal

Bring the BBS particle editor's Events page up to Snowstorm/Bedrock parity for event authoring, serialization, and runtime behavior in the NeoForge 1.21.1 `new` project.

Particle authors should be able to define named particle events, reference those events from emitter/particle/collision triggers, save/load them through the existing BBS particle JSON pipeline, and preview the supported event behavior in the editor and particle form renderer.

## Requirements

- Work only against `new/` for implementation. `old/` and `docs/temp/snowstorm` are references only.
- Follow Trellis workflow: planning before implementation, quality check before completion, and commit only after explicit user confirmation.
- Keep replies in Chinese.
- Use the existing BBS Java UI framework and particle data types instead of introducing a web/Vue dependency.
- Preserve Snowstorm/Bedrock JSON shape:
  - `particle_effect.events` stores named event definitions.
  - `minecraft:emitter_lifetime_events` references named events with `creation_event`, `expiration_event`, `timeline`, `travel_distance_events`, and `looping_travel_distance_events`.
  - `minecraft:particle_lifetime_events` references named events with `creation_event`, `expiration_event`, and `timeline`.
  - `minecraft:particle_motion_collision.events` references named collision events.
- Support the event node fields documented by Bedrock/Snowstorm where practical in BBS:
  - `sequence`
  - `randomize` with `weight`
  - `particle_effect` with `effect`, `type`, and `pre_effect_expression`
  - `sound_effect.event_name`
  - `expression`
  - `log`
- The Events window must expose editing controls for named events and trigger lists/timelines, not remain an empty section.
- Existing particle JSON without events must continue loading and saving without adding empty event objects.
- Unknown or unsupported event payload data must not be silently destroyed during load/save.
- Undo/redo and dirty tracking must work for Events page edits using the existing particle undo manager.
- The implementation must not require moving Snowstorm source until after the feature work is complete; the existing `docs/temp/snowstorm` checkout is the analysis/reference location.

## Acceptance Criteria

- [ ] `new` can load a particle JSON containing `particle_effect.events`, `minecraft:emitter_lifetime_events`, `minecraft:particle_lifetime_events`, and collision `events` without dropping data.
- [ ] Saving the same particle preserves the supported event tree and trigger references in the expected Bedrock/Snowstorm JSON shape.
- [ ] The particle editor Events tab lets the user add, rename, remove, and edit named events.
- [ ] The Events tab lets the user edit emitter event triggers: creation, expiration, timeline, travel distance, and looping travel distance.
- [ ] The Events tab lets the user edit particle event triggers: creation, expiration, and timeline.
- [ ] Collision events are editable either in the Events page or through a clearly connected collision trigger control, and serialize back to `minecraft:particle_motion_collision.events`.
- [ ] Event nodes can be nested with sequence/randomize and can contain particle effect, sound effect, expression, and log fields.
- [ ] Lifecycle triggers fire each event at most once per relevant threshold crossing, not repeatedly every tick while age remains after the threshold.
- [ ] At minimum, `expression` event nodes execute against the firing emitter/particle context, matching the documented meaning.
- [ ] Supported `particle_effect` event nodes produce visible sub-particle behavior in preview/world rendering, or any intentionally deferred submode is explicitly recorded in the implementation notes before completion.
- [ ] Empty event components are omitted from saved JSON unless their corresponding UI data is non-empty.
- [ ] `.\gradlew.bat build` passes from `new/`.
- [ ] Only `new/` implementation changes are committed, and only after user confirmation.

## Notes

- Evidence gathered:
  - `new/src/client/java/mchorse/bbs_mod/ui/particles/sections/UIParticleSchemeEventsSection.java` currently only returns a title; the Events UI is effectively empty.
  - `new/src/client/java/mchorse/bbs_mod/particles/components/events/*` already parse and serialize lifetime event components, but comments and code show runtime dispatch is not implemented.
  - `new/src/client/java/mchorse/bbs_mod/particles/ParticleParser.java` currently parses `curves` and `components`, but not top-level `particle_effect.events`.
  - `new/src/client/java/mchorse/bbs_mod/particles/components/motion/ParticleComponentMotionCollision.java` preserves raw `events`, but does not execute them.
  - Official Microsoft particle docs describe top-level `particle_effect.events`, event node fields, and lifetime event triggers.
  - Official Snowstorm reference is available at `docs/temp/snowstorm` and is synced to `origin/master` commit `12fc199 v3.2.2`.

## Open Questions

- Resolved scope: implement full runtime behavior where feasible. `expression` and `particle_effect` must execute visibly; `sound_effect` should execute when it can be mapped through Minecraft/NeoForge sound events, otherwise it remains editable/serializable with the limitation recorded before completion.
