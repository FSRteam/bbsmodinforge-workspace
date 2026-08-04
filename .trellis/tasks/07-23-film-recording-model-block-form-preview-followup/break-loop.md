## Bug Analysis: BBSFS preview lighting drift and residual animation preview stalls

### 1. Root Cause Category
- **Category**: C/D/E - Change propagation failure, test coverage gap, and implicit assumption
- **Specific Cause**: The first repair aligned Mob thumbnail block light and reused the normal pose for the picking draw, but treated those as the complete visual and performance boundaries. BBSFS 1.21.1 also installs dedicated form-list diffuse lights and transforms viewport lights by the preview camera. The editor additionally samples bone matrices twice for Gizmo placement and renders a model-editor thumbnail after the main viewport; those consumers repeated animation, constraints, IK, and physics despite using the same owner, entity age, transition, semantic base, and model.

### 2. Why Fixes Failed
1. **Mob block-light alignment**: fixed the packed-light value but not the diffuse-light direction/state inherited at the surrounding UI boundary.
2. **Normal-to-picking pose snapshot**: removed the most visible duplicate draw solve, but did not enumerate same-frame matrix sampling and the secondary model-editor thumbnail as pose consumers.
3. **Source-shape tests**: asserted the narrow implementation that had been added, so they proved the patch stayed present rather than proving all preview consumers shared one solved pose.

### 3. Prevention Mechanisms
| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Documentation | Extend the preview render ownership spec with BBSFS list/viewport light contracts and all same-frame pose consumers. | DONE |
| P0 | Architecture | Keep solved-pose reuse renderer-local and key it by owner, entity identity/age, model, transition, semantic matrix, and world capability flags. | DONE |
| P0 | Test coverage | Assert list lights, camera-space viewport lights, matrix sampling reuse, and model-editor thumbnail reuse together. | DONE |
| P1 | Manual profiling | Compare frame time and solve/upload counts with an animated Form in Morph, Film, and Model Editor previews. | TODO |

### 4. Systematic Expansion
- **Similar Issues**: Any preview that renders a normal pass, a picker pass, attachment/Gizmo matrices, thumbnails, or remote-capture surfaces can accidentally re-evaluate stateful animation.
- **Design Improvement**: Treat a solved preview pose as a renderer-owned frame product with several read-only consumers, not as an optimization tied only to a particular draw pass.
- **Process Improvement**: During BBSFS parity work, compare the surrounding render-state owner as well as the renderer body; packed light, diffuse light direction, model-view state, and reset behavior form one visual contract.

### 5. Knowledge Capture
- [x] Updated `.trellis/spec/new/backend/quality-guidelines.md`.
- [x] Extended the existing source regression contract.
- [x] Recorded the follow-up in this task's implementation log.
- [ ] Complete in-game visual and frame-time acceptance.
