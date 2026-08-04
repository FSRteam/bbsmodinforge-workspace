## Bug Analysis: Sparse armor faces stayed planar across bending joints

### 1. Root Cause Category

- **Category**: D/E - Test Coverage Gap and Implicit Assumption
- **Specific Cause**: The first skinning implementation assumed a continuous weight
  field was sufficient to create a curved surface. Vanilla armor side faces contain
  only four corner vertices, so the rasterizer still received two planar triangles.
  Linear blending also reduced cross-section volume at large joint angles.

### 2. Why Fixes Failed

1. **Two-bone LBS**: It connected transformed endpoints but introduced no intermediate
   geometry, producing a straight chord and inward pinching.
2. **Matrix-order correction**: It removed camera dependence but did not change the
   sparse topology, so the remaining visual defect became easier to see.
3. **Compilation-only validation**: It verified APIs and render-path wiring but could
   not prove curvature, thickness preservation, or the joint's silhouette.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | Subdivide vertical vanilla armor quads before skinning | DONE |
| P0 | Architecture | Interpolate rigid rotation around the recovered joint pivot | DONE |
| P0 | Documentation | Add the sparse mesh deformation contract to renderer quality guidelines | DONE |
| P1 | Runtime validation | Inspect 30/60/90 degree bends, inner surfaces, and cross-section thickness | TODO: in-game |
| P1 | Runtime validation | Verify material, trim, glint, normals, and camera invariance together | TODO: in-game |

### 4. Systematic Expansion

- **Similar Issues**: Capes, sleeves, tails, rigid accessories, and captured vanilla
  boxes can all remain planar when their source topology has no vertices across a
  deformation interval.
- **Design Improvement**: Treat topology preparation and deformation as one rendering
  contract; weight calculation alone cannot promise a curved result.
- **Process Improvement**: Add large-angle silhouette and thickness checks whenever a
  deformation feature is accepted, rather than relying on compilation or small poses.

### 5. Knowledge Capture

- [x] Updated `.trellis/spec/new/backend/quality-guidelines.md`.
- [x] Updated the armor task PRD, design, and implementation records.
- [x] Recorded this analysis in the task research directory.
- [ ] Complete the documented in-game visual matrix before archiving the task.
- [ ] No template copy exists in this application repository; no template sync applies.
