# Implementation — 模型方块「最后渲染」选项

## Files changed

| File | Change |
|---|---|
| `src/main/java/mchorse/bbs_mod/blocks/entities/ModelProperties.java` | `renderLast` field + getter/setter + `render_last` persistence key |
| `src/client/java/mchorse/bbs_mod/client/renderer/ModelBlockRenderLastQueue.java` | NEW — frame-local render-last queue |
| `src/client/java/mchorse/bbs_mod/client/renderer/ModelBlockEntityRenderer.java` | deferral branch in `render`, extracted `renderShadowFor`, added static `renderDeferred`, static `instance` |
| `src/client/java/mchorse/bbs_mod/client/BBSClientNeoEvents.java` | `begin()` at AFTER_ENTITIES, `flush()` at AFTER_BLOCK_ENTITIES, `release()` safety net at AFTER_LEVEL |
| `src/client/java/mchorse/bbs_mod/ui/model_blocks/UIModelBlockPanel.java` | `renderLast` UIToggle wired into editor column + `fillData` |
| `src/client/java/mchorse/bbs_mod/ui/UIKeys.java` | `MODEL_BLOCKS_RENDER_LAST` |
| `src/client/resources/assets/bbs/assets/strings/en_us.json` | `bbs.ui.model_blocks.render_last` = "Render last" |

## Pipeline placement (verified against decompiled 1.21.1 LevelRenderer)

```
renderLevel:
  entities pass                     ← active=false (item/held-form paths safe)
  AFTER_ENTITIES stage event        ← ModelBlockRenderLastQueue.begin()
  blockentities pass                ← ModelBlockEntityRenderer.render defers render-last blocks
  AFTER_BLOCK_ENTITIES stage event  ← ModelBlockRenderLastQueue.flush() (far-to-near)
  ...
  translucent terrain               ← FormTranslucentQueue flush (WorldRendererMixin)
  AFTER_LEVEL stage event           ← ModelBlockRenderLastQueue.release() (safety net)
```

Blockbuster's `renderLastEntities` ran after tile entities too — AFTER_BLOCK_ENTITIES is the
semantic equivalent in the NeoForge stage model, and still precedes the translucent boundary so
deferred translucent form commands sort against the replayed blocks.

## Scoping rules

- `shouldDefer` = `active && !isIrisShadowPass() && properties.isRenderLast()`.
- `active` is true only between begin() (AFTER_ENTITIES) and flush() (AFTER_BLOCK_ENTITIES).
- Detached item path (`BlockEntityRenderDispatcher.renderItem`) runs during the entity pass,
  before begin() — never defers. BlockForm's model-block state uses that same detached path.
- Shadows draw immediately during the BE pass (order-independent vanilla buffer).
- Editor axes / debug box remain on the immediate path only (they're skipped for deferred
  blocks; Blockbuster behaves the same way).
- Secondary multiview pass re-enters renderLevel and gets its own begin→BE→flush cycle per
  stage dispatch — self-consistent.

## Data flow (cross-layer)

UI toggle → `ModelProperties.setRenderLast` → `properties.toData()` (existing
`sendModelBlockForm` packet, no format change) → server `updateForm` → NBT `Properties.render_last`
→ `getUpdateTag` → client `loadAdditional` → `properties.isRenderLast()`. Old saves without the
key decode to `false` (getBool default).

## Validation

- [x] `compileJava compileClientJava`
- [x] full `gradlew build` — BUILD SUCCESSFUL in 5m44s, `build/libs/BBS_FSR_beta_v0.0.11.jar`
  produced (with `--project-cache-dir .gradle/codex-sound-jar-build -PbbsPreferMirror=true`;
  the default `build/neoForm` cache had been corrupted by repeatedly interrupted runs and had
  to be rebuilt from scratch). Note: an earlier full-build attempt failed `testUiInputMailbox`
  on a **pre-existing** local change; the passing final run used the warm alternate cache.
- [ ] in-game manual matrix (awaiting user retest after the pose-capture fix): opaque block
  behind translucent render-last block; two render-last blocks at different depths; look-at +
  shadow + transform parity; toggle round-trip through rejoin.

## Bugfix 2026-08-29 (user test: "开启后伪装直接消失")

First cut replayed through the AFTER_BLOCK_ENTITIES event's PoseStack. That stack is
camera-relative **without** the per-block translate the vanilla BE loop applies before calling
the dispatcher (`posestack.translate(blockX - camX, ...)`) — every render-last model drew at the
camera origin and was effectively invisible.

Fix: the queue entry now **captures the dispatcher's current PoseStack at enqueue time** (which
already contains the block-position translate). The replay draws through the exact transform
the immediate path would have used; `flush` no longer needs the event stack. `renderDeferred`
also skips the shadow (it already drew during the BE pass, where draw order does not matter) —
the first cut drew it twice.

Verified while fixing:

- `renderingWorld` stays true until `onWorldRenderEnd` (renderLevel RETURN) — AFTER_BLOCK_ENTITIES
  is inside renderLevel, so `canDeferWorldTranslucency()` still routes translucent texels of a
  render-last model into `FormTranslucentQueue`, which flushes later at the translucent boundary
  (correct: the render-last model's own translucent parts still sort globally).
- The model VAO path draws through RenderSystem directly (no vertex consumer); the `bufferSource`
  passed to flush is only used by the shadow path, which no longer runs there.
- The RenderSystem model-view stack pushed at renderLevel start (`matrix4fstack.mul(modelView)`)
  is popped only after AFTER_BLOCK_ENTITIES — the replay's shader state matches the BE pass.
