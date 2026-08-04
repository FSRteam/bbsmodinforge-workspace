## Bug Analysis: Local transforms composed in camera-relative space

### 1. Root Cause Category

- **Category**: B/E - Cross-Layer Contract and Implicit Assumption
- **Specific Cause**: Armor joint deltas and several glint-layer transforms were
  model-local, but some render paths applied them after a matrix or CPU vertex bake
  that already contained outer render/camera-relative placement. Matrix types did
  not encode their coordinate space, so the invalid composition compiled and only
  failed visually when the camera or a non-identity layer transform moved.

### 2. Why Fixes Failed

1. **Initial armor skinning**: The two-bone interpolation and all three material
   passes were correct, but the lower joint delta was left-multiplied onto the
   completed upper matrix. This fixed continuity in one pose while rotating the
   camera-relative translation in actual gameplay.
2. **Initial glint layer transform**: One shader uniform was applied uniformly to
   VAO and CPU-baked geometry. VAO positions were local, but immediate, billboard,
   block, and item positions had already passed through a `PoseStack`, so identical
   shader code represented different coordinate spaces.
3. **Initial transparency fix**: Additive blending and base-alpha discard removed
   the black opaque overlay, but multi-material VAOs still sampled the texture left
   bound by the last base material. Alpha correctness therefore also required
   preserving the material-to-texture binding across the second pass.
4. **Compilation-only verification**: Java compilation proved signatures and data
   flow, but could not expose matrix-order, material-binding, shader-compile, or
   picking-buffer regressions.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Documentation | Add the local/effect transform composition contract to the renderer quality spec | DONE |
| P0 | Architecture | Apply CPU-path effect transforms before vertex emission; reserve shader-local transforms for local VAO vertices | DONE |
| P0 | Code review | Require camera-invariance and picking checks for every decorative secondary pass | DONE |
| P1 | Architecture | Preserve the actual per-material base texture for alpha-sampling secondary passes | DONE |
| P1 | Runtime validation | Test armor bends and glint transforms while moving/rotating the camera | TODO: in-game |
| P1 | Runtime validation | Test multi-material transparent cutouts and Form picking with glint enabled | TODO: in-game |

### 4. Systematic Expansion

- **Similar Issues**: Outlines, selection shells, emissive layers, attachment
  offsets, and CPU-captured vanilla geometry can repeat the same mistake.
- **Design Improvement**: A future typed wrapper for local, pose-baked, and
  model-view matrices would make invalid multiplication harder to express. This is
  broader than the current task and is not required for the shipped fix.
- **Process Improvement**: Review secondary passes as a complete contract:
  coordinate space, material/texture binding, GL state, depth behavior, and picking
  exclusion, rather than reviewing only the shader or only the geometry source.

### 5. Knowledge Capture

- [x] Updated `.trellis/spec/new/backend/quality-guidelines.md`.
- [x] Updated both task design/implementation records.
- [x] Recorded the analysis in the armor task research directory.
- [ ] Perform the documented in-game matrix after installing the newly built JAR.
- [ ] Commit together with the implementation only after the user selects the
  intended subset from the dirty nested repositories.
