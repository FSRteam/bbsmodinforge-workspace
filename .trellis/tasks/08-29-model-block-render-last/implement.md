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
- [ ] full `gradlew build`
- [ ] in-game manual matrix: opaque block behind translucent render-last block; two render-last
  blocks at different depths; look-at + shadow + transform parity; toggle round-trip through
  rejoin.
