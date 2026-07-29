## Bug Analysis: collapsible glint section leaves stale scroll space

### 1. Root Cause Category
- **Category**: B - Cross-Layer Contract, C - Change Propagation Failure, and D - Test Coverage Gap
- **Specific Cause**: `UISection` changed its local child tree but resized only its direct parent, while the enclosing `UIScrollView` owned sibling placement, total scroll height, and scroll clamping. The Pose keyframe editor also rebuilt separate wide/narrow child trees and omitted the glint section from those branches. A Pose-only list stretch then absorbed the released height, magnifying the visible blank area.

### 2. Why Fixes Failed
1. Adding a foldable section fixed the local presentation but did not notify the scroll-layout owner, so the stale range survived until a window resize rebuilt the full tree.
2. Fixing the model/Form editor alone missed the separate Pose keyframe responsive layouts.
3. Letting the bone list stretch after every section fold converted the removed section height into empty list space instead of moving later controls upward.

### 3. Prevention Mechanisms
| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | `UISection.resizeParent()` resizes the nearest `UIScrollView` when present | DONE |
| P0 | Layout | Pose no longer stretches the bone list to consume fold height | DONE |
| P0 | Change propagation | Wide and narrow Pose layouts both re-add the same `glintSection` | DONE |
| P1 | Test coverage | Assert sibling movement, `scrollSize`, and scroll clamping after real fold events | DONE |
| P1 | Manual validation | Exercise model/Pose panels without resizing the game window | TODO |

### 4. Systematic Expansion
- **Similar Issues**: Any reusable collapsible section nested below an intermediate column inside `UIScrollView`, and any responsive editor that reconstructs children in multiple width branches.
- **Design Improvement**: Treat the scroll view as the owner of layout extent and clamping; sections only own their local expanded state.
- **Process Improvement**: A new foldable group must be checked in every responsive layout branch and tested without using window resize as a refresh trigger.

### 5. Knowledge Capture
- [x] Updated `.trellis/spec/frontend/component-guidelines.md` with the scroll-layout contract.
- [x] Added a real regression to `UISectionMouseButtonTest`.
- [x] Recorded task acceptance and verification in `prd.md` / `implement.md`.
- [ ] Complete the in-game model/Pose fold and bottom-scroll matrix.
