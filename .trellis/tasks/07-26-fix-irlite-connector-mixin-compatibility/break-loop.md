## Bug Analysis: IRLite beam false negatives and spotlight first-drag jump

### 1. Root Cause Category

- **Category**: B/D/E - Cross-layer contract, test coverage gap, and implicit assumption
- **Specific Cause**: Beam fixes stopped at publication boundaries until fix8 ran the real
  generated Photon pass and measured pixels. The first spotlight visual matrix then produced a
  false negative because the fixture silently assumed any registered light would be visible even
  though the cone was behind the camera and pointed away. Separately, the spotlight guide treated
  an absolute mouse-ray intersection as the property value and used opposite signed axes for
  visible and pick geometry, so the press frame could overwrite a valid keyframe before movement.

### 2. Why Fixes Failed

1. **fix3 source/JAR checks**: proved `beam_strength` serialization, packing, and bundled GLSL,
   but did not prove a selected generated pack or final pixels.
2. **fix4 independent texture**: removed reliance on SSBO field interpretation, but startup-only
   verification still had no active consuming shaderpack.
3. **fix5 sampler/program probes**: proved Iris binding and pass execution, but not that upload
   targeted the same real OpenGL texture object.
4. **fix6/fix7 readback**: proved CPU-to-GPU publication, but the initial fixture contained no
   IRLite light and later spotlight geometry was outside the framebuffer.
5. **spotlight drag implementation**: used absolute ray-derived values because they were locally
   plausible; it never asserted the gesture invariant that press without movement is identity.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Test coverage | Require `0/baseline/high` fixed-camera pixels for each distinct light registration path. | DONE |
| P0 | Runtime diagnostics | Keep opt-in Form/registry/index/texture readback and consuming-program evidence. | DONE |
| P0 | Gesture contract | Use `startValue + currentRaw - anchorRaw`; zero movement must be exact identity. | DONE |
| P1 | Fixture validity | Put directional effects through the camera frustum and round-trip generated Film data. | DONE |
| P1 | Spec | Record relative drag continuity and shared signed-axis ownership in backend quality guidelines. | DONE |
| P1 | Remaining test | Add dedicated point/spot replay `beam_strength` interpolation films. | DONE |

### 4. Systematic Expansion

- **Similar Issues**: Any directional sound cone, spotlight, particle emitter, or Gizmo handle can
  pass data tests while remaining outside the camera or using a mismatched local axis.
- **Design Improvement**: Generated fixtures should own camera pose and effect pose together
  instead of copying an arbitrary replay position.
- **Process Improvement**: Classify evidence as publication, binding, program execution, geometry
  visibility, and pixels; do not collapse these into one “runtime passed” label.
- **Knowledge Gap**: Mouse-ray coordinates are not property coordinates. They are gesture-local
  measurements and require an anchor before they can safely change stored values.

### 5. Knowledge Capture

- [x] Preserve the full fix8 runtime logs and fixed-region pixel measurements.
- [x] Update `.trellis/spec/new/backend/quality-guidelines.md` with press-anchored numeric drag
      continuity and signed-axis requirements.
- [x] Keep the GPU consumer boundary in `.trellis/spec/guides/cross-layer-thinking-guide.md`.
- [x] Add and run point/spot replay interpolation fixtures before archiving the task.
