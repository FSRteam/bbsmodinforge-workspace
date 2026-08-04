# Research notes

## Local code findings

- `new/src/client/java/mchorse/bbs_mod/ui/particles/UIParticleSchemePanel.java` already creates an Events tab and adds `UIParticleSchemeEventsSection`.
- `new/src/client/java/mchorse/bbs_mod/ui/particles/sections/UIParticleSchemeEventsSection.java` is currently only a title stub.
- `new/src/client/java/mchorse/bbs_mod/particles/ParticleParser.java` registers `emitter_lifetime_events`, `particle_lifetime_events`, and `emitter_rate_manual`, but only parses `description`, `curves`, and `components`; top-level `particle_effect.events` is not parsed.
- `new/src/client/java/mchorse/bbs_mod/particles/components/events/ParticleComponentEmitterLifetimeEvents.java` and `ParticleComponentParticleLifetimeEvents.java` preserve event trigger data but leave dispatch comments instead of executing events.
- `new/src/client/java/mchorse/bbs_mod/particles/components/motion/ParticleComponentMotionCollision.java` preserves collision `events` as raw `BaseType` but does not dispatch them.
- `new/src/client/java/mchorse/bbs_mod/particles/components/rate/ParticleComponentRateManual.java` already exists and is intended for event-spawned particles.
- `ParticleUndoManager` snapshots the whole `ParticleScheme.toData()` output, so event data must be part of `ParticleScheme.toData()` for undo/redo to cover it.

## Snowstorm findings

Reference checkout:

- Path: `docs/temp/snowstorm`
- Remote: `https://github.com/JannisX11/snowstorm.git`
- HEAD: `12fc199 v3.2.2`
- Status after fetch: local `master` matches `origin/master`.

Relevant files:

- `src/components/Sidebar/EventList.vue`
  - Adds/removes/renames top-level event IDs.
  - Stores events in `Config.events`.
- `src/components/Sidebar/EventSubpart.vue`
  - Recursive event node editor.
  - Supports sequence, randomize with weight, particle effect, sound effect, and expression.
  - Particle effect type options: `emitter`, `emitter_bound`, `particle`, `particle_with_velocity`.
- `src/components/Sidebar/EventPicker.vue`
  - Presents available event IDs to trigger fields.
- `src/input_structure.js`
  - Events tab contains named events, emitter triggers, and particle triggers.
- `src/export.js`
  - `particle_effect.events` is emitted only when named events exist.
  - Trigger lists serialize as one string for one event and a list for multiple events.
  - Emitter lifetime events serialize creation, expiration, timeline, travel distance, and looping travel distance.
  - Particle lifetime events serialize creation, expiration, and timeline.
- `src/import.js`
  - Loads `Config.events` into the Events UI list.
  - Existing unsupported event fields are tracked for fallback preservation.

## Official docs findings

- Microsoft Learn `minecraft:particle_document` says `particle_effect` includes `events`, `curves`, `description`, and `components`.
- Microsoft Learn documents event node fields under `particle_effect > events`: `expression`, `log`, `particle_effect`, `randomize`, `sequence`, and `sound_effect`.
- Microsoft Learn documents particle visual event types: `emitter`, `emitter_bound`, `particle`, and `particle_with_velocity`.
- Microsoft Learn documents particle lifetime events as string-or-list references for creation/expiration/timeline triggers.
- bedrock.dev/Mojang particle docs describe emitter lifetime events with creation, expiration, timeline, travel distance, and looping travel distance triggers, all referencing names in the event section.

Sources:

- https://learn.microsoft.com/en-us/minecraft/creator/reference/content/particlesreference/examples/particlecomponents/particle_document?view=minecraft-bedrock-stable
- https://learn.microsoft.com/en-us/minecraft/creator/reference/content/particlesreference/particlecomponents/minecraftemitter_lifetime_events?view=minecraft-bedrock-stable
- https://learn.microsoft.com/de-de/minecraft/creator/reference/content/particlesreference/particlecomponents/minecraftparticle_lifetime_events?view=minecraft-bedrock-stable
- https://mojang.github.io/bedrock-samples/Particles.html
- https://github.com/JannisX11/snowstorm
