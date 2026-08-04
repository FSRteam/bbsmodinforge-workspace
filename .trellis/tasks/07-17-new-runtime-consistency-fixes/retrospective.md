# Debug retrospective

## Bug Analysis: Input terminal and runtime ownership drift

### 1. Root Cause Category

- **Category**: B/C/D - cross-layer contract, change propagation, and coverage gap.
- **Specific cause**: lifecycle cleanup reused physical release, while tests
  exercised helper targets more often than real widget/screen owners. Runtime
  recovery paths also returned a success/null shape that did not describe a
  still-owned lease or retained runtime.

### 2. Why Earlier Fixes Were Incomplete

1. Per-widget button checks fixed wrong-button release but did not distinguish
   cancellation from commit.
2. Source-shape assertions proved calls existed but did not lock ordering or
   the real adapter behavior.
3. Happy-path teardown tests did not inject partial restore, notification, or
   entity-retirement failures.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific action | Status |
|---|---|---|---|
| P0 | Compile-time | Separate `mouseReleased` and `mouseCanceled` adapter methods | DONE |
| P0 | Tests | Run real owner/generation, failure-retry, and terminal-order regressions | DONE |
| P1 | Spec | Record UI terminal and API JVM descriptor contracts | DONE |
| P1 | Manual | Execute the NeoForge/GPU/multiplayer matrix before release | OPEN |

### 4. Systematic Expansion

- Resource/session owners must remain reachable until teardown succeeds.
- A retained recovery runtime must retain the matching mutation claim.
- Public API review must compare JVM descriptors, not only Java source calls.

### 5. Knowledge Capture

- Updated the new backend quality spec and cross-layer guide.
- Automated gates are green; real-client/server/GPU/FFmpeg rows remain open.
