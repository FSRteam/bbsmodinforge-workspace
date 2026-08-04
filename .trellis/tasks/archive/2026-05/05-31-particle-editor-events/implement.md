# Particle editor events support implementation plan

## Ordered checklist

1. Preparation
   - Re-read the `new/backend` and `frontend` spec indexes before editing.
   - Confirm current `new` status is clean or identify unrelated user changes.
   - Run focused searches for existing particle event, list, overlay, and sound/particle spawning helpers.

2. Data model and parser
   - Add event model classes under `new/src/client/java/mchorse/bbs_mod/particles/events/`.
   - Add `ParticleScheme.events` as an insertion-ordered map.
   - Parse `particle_effect.events` in `ParticleParser.parseEffect()`.
   - Serialize `particle_effect.events` in `ParticleParser.toData()` only when non-empty.
   - Normalize string/list event references and timeline maps in shared helper classes.

3. Lifetime and collision trigger components
   - Refactor `ParticleComponentEmitterLifetimeEvents` to use normalized fields and runtime guard state.
   - Refactor `ParticleComponentParticleLifetimeEvents` to use normalized fields and per-particle guard state.
   - Keep raw compatibility for unsupported fields.
   - Wire `ParticleComponentMotionCollision.events` through the same trigger helper.

4. Runtime dispatcher
   - Add a dispatcher/resolver that executes named `ParticleEventNode` trees.
   - Implement sequence and weighted randomize traversal.
   - Implement expression execution first.
   - Implement log output.
   - Implement particle-effect spawning with child emitters or a deliberately scoped equivalent that works in editor preview and form rendering.
   - Implement sound playback if resource mapping is clear; otherwise keep serialized/editable and record the limitation before completion.
   - Ensure recursive events have a depth guard to avoid infinite loops.

5. UI controls
   - Expand `UIParticleSchemeEventsSection` into named event and trigger editing sections.
   - Add small helper UI components if needed for recursive event nodes, trigger lists, timelines, and event picking.
   - Add localization keys for labels/tooltips.
   - Wire UI edits to `ParticleScheme.events` and event trigger components.
   - Make UI refresh correctly on load, undo, redo, add, rename, and remove.

6. Round-trip samples/manual checks
   - Create or use an in-memory/sample particle JSON with:
     - one named event containing expression + particle_effect
     - emitter creation/timeline trigger
     - particle creation/timeline trigger
     - collision trigger
   - Verify parse -> save retains expected JSON shape.
   - Verify removing all events omits empty top-level/component event data.

7. Quality checks
   - Run `.\gradlew.bat build` from `new/`.
   - Run any narrower compile/test task discovered during implementation if build is too slow.
   - Inspect `git -C new diff` for accidental unrelated changes.

8. Finish
   - Update Trellis artifacts with final implementation notes.
   - Handle Snowstorm source movement only after the feature is complete and according to the user's requested temp destination.
   - Ask user before local git commit.
   - Commit only `new/` changes after confirmation.

## Validation commands

Run from repository root unless noted:

```powershell
git -C new status --short
.\gradlew.bat build
```

If Gradle must run from `new/` instead:

```powershell
.\gradlew.bat build
```

with working directory `D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new`.

## Risky files

- `new/src/client/java/mchorse/bbs_mod/particles/ParticleParser.java`
- `new/src/client/java/mchorse/bbs_mod/particles/ParticleScheme.java`
- `new/src/client/java/mchorse/bbs_mod/particles/emitter/ParticleEmitter.java`
- `new/src/client/java/mchorse/bbs_mod/particles/emitter/Particle.java`
- `new/src/client/java/mchorse/bbs_mod/particles/components/events/ParticleComponentEmitterLifetimeEvents.java`
- `new/src/client/java/mchorse/bbs_mod/particles/components/events/ParticleComponentParticleLifetimeEvents.java`
- `new/src/client/java/mchorse/bbs_mod/particles/components/motion/ParticleComponentMotionCollision.java`
- `new/src/client/java/mchorse/bbs_mod/ui/particles/sections/UIParticleSchemeEventsSection.java`

## Review gates before `task.py start`

- User confirms the runtime scope for this pass.
- User approves starting implementation from this plan.
- Planning artifacts remain consistent with the selected runtime scope.
