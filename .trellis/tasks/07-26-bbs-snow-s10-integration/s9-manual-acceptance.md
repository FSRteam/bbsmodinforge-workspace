# S9 Manual Acceptance Queue

These checks require a live world, a real Film project, visual review, or Iris
interaction. S9 automation and strict-client startup do not claim them as passed.

- Extra category: Fluid exemplar appears once, icon and localized title correct,
  and creating a replay with the fluid form does not log a missing-factory warning.
- FULL_OCEAN: bounded surface mesh follows sizeX/sizeZ, simulation height and
  turbulence respond to sliders, smooth vs flat shading toggles normals, and
  subdivisions beyond the mesh budget clamp rather than hang the frame.
- fillBlock/sizeY: side walls and bottom cap appear only in FULL_OCEAN, winding
  and culling look correct from above and below, and toggling the switch changes
  vertex bounds without leaking the previous mesh.
- WATER_DROP: sphere radius follows dropSize, tension/viscosity wobble reads
  naturally, and pole degenerates do not produce NaN streaks under motion.
- PROCEDURAL: waves advance on the Film timeline / logical tick (not wall clock),
  pausing the film freezes the surface, and stepping frame-by-frame is repeatable.
- Interaction: entities and replay owners displace the surface with
  fluid_realistic_model_interaction on and off; Cubic detailed sampling and the
  BOBJ/root fallback both stay within the sample budget (debug overlay on/off).
- Render passes: Iris, motion blur and video export run the same deterministic
  simulation state; repeated renders of a paused frame produce identical pixels.
- Release: switching a replay away from the fluid form or closing the editor
  releases the renderer (no lingering GL buffers or debug snapshots).
