## Bug Analysis: glint pass corrupts later model materials

### 1. Root Cause Category
- **Category**: E - Implicit Assumption
- **Specific Cause**: The implementation treated a vanilla `RenderType` as a
  local shader switch. Its clear handler actually tears down blend, texture,
  lightmap, overlay, cull, and depth state owned by the surrounding model draw.
  Glint was also submitted before deferred translucent base geometry and used
  `GL_LEQUAL` even when its geometry was identical to the base.

### 2. Why Fixes Failed
1. Replacing the standalone shader with vanilla RenderTypes fixed shader-pack
   routing but retained per-bone interleaving and state leakage.
2. Applying `LEQUAL` to every glint made transformed layers visible, but removed
   the depth mask that prevented transparent texture regions from becoming
   solid glint/black silhouettes.
3. Rendering glint immediately after a selected bone ignored the separate world
   translucent queue, so the decoration could run before its base surface.

### 3. Prevention Mechanisms
| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | Collect secondary draws and submit them after all base paths | DONE |
| P0 | Runtime state | Snapshot and restore nested RenderType state | DONE |
| P0 | Depth contract | Use EQUAL for identical geometry and LEQUAL only for transformed layers | DONE |
| P1 | Documentation | Record pass ordering/state ownership in rendering quality guidelines | DONE |
| P1 | Validation | Exercise VAO, CPU, hybrid, cutout, translucent, and Iris cases in game | TODO |

### 4. Systematic Expansion
- **Similar Issues**: Billboard, Extruded, Block, and Item whole-form glint use
  the same nested RenderType state and world translucency boundary.
- **Design Improvement**: `GlintRenderState` remains the single state owner, and
  `FormTranslucentQueue` owns delayed VAO/mesh glint commands.
- **Process Improvement**: Rendering reviews must write the base/decorative pass
  order and depth owner before changing shader or RenderType code.

### 5. Knowledge Capture
- [x] Updated `.trellis/spec/new/backend/quality-guidelines.md`.
- [x] Recorded this task-local analysis.
- [ ] Complete the in-game shader/cutout/translucency matrix.

## Bug Analysis: shader-pack glint routing and shifted Fresnel origin

### 1. Root Cause Category
- **Category**: B - Cross-Layer Contract, plus E - Implicit Assumption
- **Specific Cause**: BBS assumed every known entity RenderType would receive enchantment-compatible shader-pack treatment. Photon and IterationRP instead define the contract specifically in `gbuffers_armor_glint`. Separately, CPU Fresnel treated PoseStack-baked camera-relative vertices as local vertices and applied the global model-view translation a second time.

### 2. Why Fixes Failed
1. Replacing the standalone BBS shader with `energySwirl` entered a known vanilla entity path, but not the shader packs' enchantment-specific compositor.
2. Restoring render state and exact depth fixed material corruption without changing the program-routing mismatch or the shifted CPU view origin.

### 3. Prevention Mechanisms
| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | Route shader-pack world glint through `armorEntityGlint` | DONE |
| P0 | Coordinate contract | Compute CPU view terms from position/normal in one PoseStack-baked space | DONE |
| P1 | Documentation | Record shader-pack program and color-capability checks in backend spec | DONE |
| P1 | Validation | Test four compass directions with Photon and IterationRP | TODO |

### 4. Systematic Expansion
- **Similar Issues**: Any custom decorative pass that borrows a vanilla entity RenderType without checking the target pack's dedicated program routing.
- **Design Improvement**: Keep RenderType selection in `GlintRenderState`; do not distribute pack-specific branches across form renderers.
- **Process Improvement**: Inspect representative shader-pack ZIP programs before claiming compatibility, and distinguish routing support from parameter-consumption support.

### 5. Knowledge Capture
- [x] Updated task PRD/design/implementation evidence.
- [x] Updated `.trellis/spec/new/backend/quality-guidelines.md`.
- [ ] Complete the in-game Photon/IterationRP direction and transparency matrix.

## Bug Analysis: custom armor-glint matrix regression and direction-dependent exact depth

### 1. Root Cause Category
- **Category**: B - Cross-Layer Contract, D - Test Coverage Gap, and E - Implicit Assumption
- **Specific Cause**: The shader-pack route reused `armorEntityGlint` but then replaced the vanilla texture matrix without proving that Photon/IterationRP consumed the same coordinate contract. Separately, VAO base geometry evaluated `global * bone * vertex` in the shader while its CPU edge overlay evaluated `global * (bone * vertex)` across CPU and GPU. `GL_EQUAL` exposed the resulting float reassociation as a compass-dependent visible arc.

### 2. Why Fixes Failed
1. Moving every shader-pack mode to `armorEntityGlint` fixed the program name but the extra custom texture matrix remained an unverified consumer-contract change, making both custom modes disappear while vanilla mode still worked.
2. Changing only the Fresnel origin treated the visible arc as an opacity problem. It did not change the two passes' non-bit-exact depth calculation, so the same directional failure remained.
3. Assuming every PoseStack-baked path has camera origin zero ignored UI/hand/view transforms left in the global ModelView.

### 3. Prevention Mechanisms
| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | Preserve vanilla armor-glint texture state in shader-pack mode; custom speed degrades before visibility | DONE |
| P0 | Depth contract | Keep VAO base and `GL_EQUAL` overlays local until the same captured ModelView | DONE |
| P0 | Coordinate contract | Calculate emitted-space camera origin from the inverse remaining ModelView | DONE |
| P1 | Documentation | Add exact transform-partition and shader-pack matrix rules to backend quality spec | DONE |
| P1 | Validation | Test all compass directions and three modes in Photon/IterationRP | TODO |

### 4. Systematic Expansion
- **Similar Issues**: Any exact-depth outline, decal, trim, or selection pass that regenerates VAO geometry through an immediate CPU buffer.
- **Design Improvement**: Treat the vertex transform partition as part of the depth API, not an interchangeable implementation detail.
- **Process Improvement**: Compare a broken custom mode against the working vanilla mode state-by-state before changing render routing; compilation cannot validate a shader-pack consumer.

### 5. Knowledge Capture
- [x] Updated task PRD/design/implementation evidence.
- [x] Updated `.trellis/spec/new/backend/quality-guidelines.md`.
- [ ] Complete the in-game Photon/IterationRP direction and transparency matrix.
