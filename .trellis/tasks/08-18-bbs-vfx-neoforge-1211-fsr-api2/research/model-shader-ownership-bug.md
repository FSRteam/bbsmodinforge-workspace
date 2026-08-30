# Bug Analysis: Model-form previews disappear with BBSVFX enabled

## 1. Root Cause Category

- **Category**: B/C - Cross-Layer Contract and Change Propagation Failure
- **Specific cause**: BBSVFX globally replaced `BBSShaders.getModel()` with a copied
  `model_dissolve` program. BBSFSR later changed its model shader to use object-space UI fog
  distance and added the `PassMode` translucent split, but the addon copy retained transformed
  UI distance and omitted the new pass. Java compilation could not detect this GLSL semantic
  drift. The custom `Dissolve` uniform was never set by Java and had already been superseded by
  the texture-alpha dissolve implementation, so normal model previews paid the compatibility
  cost of a shader path that provided no active effect.

## 2. Why Earlier Protection Was Insufficient

1. Startup smoke tests stopped at the title screen and did not render a model-form UI preview.
2. Shader build success proved syntax and declared inputs only; it did not prove host-equivalent
   fog, alpha, or deferred-pass behavior.
3. Keeping both shader-based and texture-based dissolve paths obscured which implementation
   actually owned the effect and allowed the obsolete global interception to remain.

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | Keep normal BBS model shaders host-owned; delete the global replacement and stale shader resources. | DONE |
| P0 | Documentation | Add the host shader ownership and effect-state isolation contract to the external-addon spec. | DONE |
| P1 | Release inspection | Reject stale `model_dissolve` resources/classes in the produced addon JAR. | DONE |
| P1 | Runtime coverage | Render a normal model preview, a world model, and a dissolving smear under vanilla and Iris. | MANUAL |

## 4. Systematic Expansion

- **Similar issues**: Any addon-owned copy of a BBS core shader can drift when the host adds a
  sampler, uniform, fog rule, alpha branch, or deferred render pass.
- **Design improvement**: Prefer texture/material/mesh/framebuffer effects that operate across
  the active host and shader-pack pipelines instead of replacing a general-purpose host shader.
- **Process improvement**: A version port must compare copied GLSL against the exact target
  BBSFSR sources and include UI preview plus world rendering in the smoke matrix.

## 5. Knowledge Capture

- [x] Updated `.trellis/spec/new/backend/external-addon-integration-contract.md`.
- [x] Recorded implementation and verification evidence in this task.
- [x] Removed the duplicate shader implementation from the addon.
- [ ] Complete the remaining human visual checks for normal model and smear rendering.

The repository has no `src/templates/markdown/spec/` tree, so there is no generated spec
template counterpart to synchronize for this contract update.
