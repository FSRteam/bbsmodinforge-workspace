# Bug Analysis: connector-fix3 beam strength had no visible effect

### 1. Root Cause Category

- **Category**: B/D - Cross-layer contract and test coverage gap
- **Specific Cause**: fix3 verified Form values, Java method descriptors, SSBO packing, bundled
  GLSL text, and a Connector main-menu launch, but never verified the selected generated
  shaderpack with a real volumetric pass. Those checks prove publication, not final GPU
  consumption or changed pixels.

### 2. Why Fixes Failed

1. SSBO source/JAR inspection: proved `vlParams.z` was written but not that the active shader
   consumed the expected field after Connector, Iris program creation, and pack generation.
2. Connector fixture launch: used no active shaderpack, so it could validate Mixins and addon
   loading only.
3. Patch text checks: proved templates contained the multiplication but did not prove the user's
   selected output pack was regenerated from those templates or that its VL program ran.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | Add a sentinel-gated R32F texture path independent of SSBO field consumption. | DONE |
| P0 | Test coverage | Require a shader-enabled `0/1/10` pixel matrix for point, spot, and replay interpolation. | TODO |
| P1 | Build verification | Give every protocol revision unique core/addon/patch identifiers and inspect the nested JAR. | DONE |
| P1 | Documentation | Record that source, bytecode, and JAR checks cannot replace GPU consumer/pixel verification. | DONE |

### 4. Systematic Expansion

- **Similar Issues**: Any setting crossing Java, GPU buffers, generated Shader code, Iris binding,
  and a selected output pack can pass structural checks while pixels remain unchanged.
- **Design Improvement**: Version GPU contracts explicitly and retain a detectable compatibility
  fallback when adding a new transport.
- **Process Improvement**: Separate publication checks, binding checks, shader compilation, and
  pixel behavior in the acceptance matrix.

### 5. Knowledge Capture

- [x] Update `.trellis/spec/guides/cross-layer-thinking-guide.md` with the GPU consumer boundary.
- [x] Complete the fix4 implementation and artifact checks.
- [ ] Retain the manual shader-enabled matrix as the final task gate.
