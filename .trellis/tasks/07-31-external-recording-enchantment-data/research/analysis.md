# Analysis: External recording loses enchantment data on equipped/held items

Date: 2026-07-31 (main-session analysis; sub-agent dispatch failed with a platform `reasoning_text` API error)

## Recording/replay data path traced (workspace new/)

1. "External" recording (`UIFilmController.startRecording` group "outside") closes the editor and starts `Recorder` (`Films.startRecording` -> `new Recorder(film, morph.getForm(), replayId, tick)`).
2. `Recorder.update()`:
   - `inventory.fromPlayer(player)` snapshots the full player inventory (Inventory.java), including armor/offhand slots when the vanilla container size covers them;
   - `keyframes.record(tick, morph.entity, null)` captures `entity.getEquipmentStack(...).copy()` for main/off hand + all armor slots (ReplayKeyframes.record).
3. Item values serialize through ItemStackKeyframeFactory using `ItemStack.CODEC` + `NbtOps`; `DataStorageUtils.toNbt/fromNbt` is type-faithful (byte/int/long arrays preserved, short arrays wrapped). No component-stripping was found in this chain.
4. Replay applies equipment via `ReplayKeyframes.apply` -> `setEquipmentStack` and `ActionPlayer.applySafely` -> `actor.setItemSlot(...)`.
5. `MobFormRenderer.synchronizeEntity` copies the source entity's stacks into the rendered form entity each frame; `ArmorRenderer.renderSlot` uses `itemStack.hasFoil()` for the enchantment glint. Item rendering is component-aware.

## Remaining suspect (needs one user confirmation)

`ActionPlayer.applySafely` gates equipment:

```
boolean applyServerEquipment = shouldApplyServerActorEquipment(actorProjection, isolatedProjection, this.isRequesterAuthorized());
// false -> all six slots are set to ItemStack.EMPTY
```

So an unprivileged/isolated actor projection receives empty equipment (a deliberate security design documented in 07-17-new-runtime-consistency-fixes/manual-test.md). Whether this is the user-visible loss depends on which replay surface shows the bug:

- Film editor preview / in-game playback as the recording owner -> equipment comes from the recorded keyframes and should keep components (chain above).
- Playback seen by another player / targeted (isolated) playback -> equipment is intentionally emptied server-side.

The user-reported "NBT 被清空" hypothesis was not confirmed by code inspection of the serialization path; the loss is most plausibly at the actor-equipment application gate or in a specific replay surface. Before implementing, confirm:

1. Where is the loss observed: Film editor preview, in-game playback by the recorder, or playback viewed by a second player?
2. Is the armor/weapon still visible (only the enchantment visuals/tooltip gone), or is the whole item gone?

Evidence files (all in new/): film/Recorder.java, film/replays/ReplayKeyframes.java, film/replays/Inventory.java, utils/keyframes/factories/ItemStackKeyframeFactory.java, data/DataStorageUtils.java, actions/ActionPlayer.java, cubic/render/vanilla/ArmorRenderer.java.
