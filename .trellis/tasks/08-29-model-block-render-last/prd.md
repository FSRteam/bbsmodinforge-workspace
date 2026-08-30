# 模型方块「最后渲染」选项（Blockbuster render-last 移植）

## Goal

Port Blockbuster's model-block "Render last" option to BBS FSR (`new`). A model block flagged
render-last skips the normal block-entity pass, collects into a per-frame queue, and replays at
the AFTER_ENTITIES world render stage, depth-sorted far-to-near. This fixes, in most cases, a
model block failing to render behind a semi-transparent model block.

## Background (Blockbuster original)

Blockbuster 1.12 (`temp/blockbuster-ref`, branch `1.12`):

- `TileEntityModelSettings` has `renderLast` (`ValueBoolean`, NBT key `RenderLast`).
- `TileEntityModel#shouldRenderInPass` returns `!(renderLast && RenderingHandler.addRenderLast(this))`
  — the block opts out of the normal TE pass and registers itself into a frame list.
- `RenderingHandler#renderLastEntities` (called by ASM after entities + tile entities):
  sorts the list far-to-near by `getRenderLastPos()` (block pos + transform offset), then re-renders
  each `TileEntityModel` through `TileEntityRendererDispatcher.instance.render(...)`.
- GUI: a toggle in the model block panel (`GuiModelBlockPanel`), lang key
  `blockbuster.gui.model_block.render_last` ("Render last") with a tooltip explaining usage on
  semi-transparent model blocks.

## Requirements

### R1 — Data model & persistence

- `ModelProperties` gains a `renderLast` boolean (default `false`).
- Persisted in `toData()/fromData()` under key `render_last` (follows the existing snake_case
  convention of `look_at`), backward compatible (absent key = false).
- Synced to the server through the existing model-block form packet
  (`ClientNetwork.sendModelBlockForm` sends `properties.toData()` — no packet format change).

### R2 — UI

- New `UIToggle` in `UIModelBlockPanel` editor column (next to `global`/`lookAt`), label from a new
  `UIKeys.MODEL_BLOCKS_RENDER_LAST` entry `bbs.ui.model_blocks.render_last`.
- State round-trips via `fillData()` like the other toggles; changes save through the existing
  save flow (`toSave` + `sendModelBlockForm`).

### R3 — Rendering (the redesign for FSR's pipeline)

Blockbuster re-renders the TE through the dispatcher at the end of the entity stage. FSR renders
model-block forms through `ModelBlockEntityRenderer.render` (immediate, during the vanilla BE
pass). Redesign:

- `ModelBlockEntityRenderer.render` renders nothing for its model when the block is render-last
  **and** a render-last queue scope is active, instead registering the block entity into a
  `ModelBlockRenderLastQueue`.
- The queue replays at NeoForge's `RenderLevelStageEvent.Stage.AFTER_ENTITIES` (before
  `FormTranslucentQueue.flush()` at the translucent boundary — render-last blocks still
  participate in the translucent queue's sorting correctly since they enqueue during the replay).
- Replay re-invokes the actual model rendering (a shared render method extracted from
  `ModelBlockEntityRenderer.render`), re-using the world PoseStack from the event.
- Queue entries sort far-to-near by camera-space distance (matching Blockbuster's
  `renderLastEntities` sort and the `FormTranslucentQueue` convention).
- Shadow rendering (`properties.isShadow()`) still works for render-last blocks (it draws through
  `vertexConsumers` into the vanilla buffer, order-independent).
- Debug axes / bounding-box overlays stay attached to the immediate pass (editor-only helpers).

### R4 — Scope safety

- Only the world-render path defers. Editor form palettes, item rendering, picking/stencil, Iris
  shadow pass, and the multiview secondary pass must not double-render or leak queue entries:
  - The queue is begin/flush scoped per frame (like `FormTranslucentQueue`).
  - `render` only defers when the queue scope is active; every other context renders immediately.
  - Iris shadow pass (`BBSRendering.isIrisShadowPass()`): render immediately (the shadow map needs
    the geometry at the right time).

## Acceptance Criteria

- [ ] A render-last model block renders visually identical to a normal one (transform, look-at,
      shadow, texture) but after all other block entities, sorted far-to-near among other
      render-last blocks.
- [ ] A normal (opaque) model block behind a semi-transparent render-last model block stays
      visible.
- [ ] Toggle round-trips: UI → `ModelProperties` → network → server NBT → back on rejoin.
- [ ] No leak across frames (queue empty after flush), no double render in editor/picking/Iris
      shadow pass.
- [ ] `new` project builds (`gradlew build`) with the change.
- [ ] en_us string `bbs.ui.model_blocks.render_last` present.

## Notes

- Reference: `temp/blockbuster-ref/src/main/java/mchorse/blockbuster/client/RenderingHandler.java`
  (`renderLastEntities`), `TileEntityModel.java` (`shouldRenderInPass`, `getRenderLastPos`).
- FSR equivalents: `ModelBlockEntityRenderer`, `ModelProperties`, `UIModelBlockPanel`,
  `FormTranslucentQueue`, `BBSClientNeoEvents#onRenderLevelStage`.
