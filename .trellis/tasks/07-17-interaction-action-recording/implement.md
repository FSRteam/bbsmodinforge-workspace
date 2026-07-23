# Interaction/action verification matrix

## Automated gate

Run from `new/` after parallel agents stop writing build outputs:

```powershell
.\gradlew.bat --no-daemon --console=plain testInteractionActions compileJava compileClientJava
```

The regression owns final-result recording, legacy serialization shapes,
recording-only factory metadata, packet-handler bytecode shape, target identity,
secondary-use restoration, and fake-player cooldown isolation.

## Runtime manual matrix

| Scenario | Expected result |
| --- | --- |
| Main-hand gun plus usable offhand | One main-hand shot; offhand is not dispatched after consumed success. |
| Gun projectile starts inside shooter bounds | It does not hit its owner; damage/kill source names the owner. |
| Gun launch mode on client and server | Selected hand is consumed on both sides and launch happens once. |
| Gun with non-finite/out-of-range `GunData`, excessive projectiles, or command data used by a non-admin | Use fails without launch/spawn; no gun command runs. A valid gun stays within per-requester/global projectile budgets. |
| Gun owner disconnects and reconnects with the same UUID while an old projectile exists | The replacement connection cannot inherit the old projectile's command authority; terminal removal releases its budget once. |
| Chest/door ordinary right-click | Recording contains one full-dispatch block action and replay opens/toggles the same branch. |
| Shift-right-click chest/door with a usable block item | Replay skips default block interaction exactly as recording did and uses the item branch. |
| Note block plus skull (`SKIP_DEFAULT_BLOCK_INTERACTION`) | Replay does not tune the note block before placing/using the skull. |
| Successful and failed block placement | Success records one full-dispatch action; failed/PASS placement records none. |
| Move a film containing absolute and actor-relative item drops | Absolute drop coordinates follow the film shift; relative offsets stay unchanged and follow the shifted actor only once. |
| Legacy film with `place_block` or `use_block_item` | It still loads and replays; neither legacy type is generated as a nested live recording. |
| Air-use item whose NeoForge hook consumes before `ItemStack.use` | One action is recorded and replay reaches the same game-mode hook path. |
| Empty-hand air-use packet/crafted clip | NeoForge rejects the packet before game-mode hooks; replay also remains a no-op. |
| Two cooldown-setting air/block actions far apart | Both execute; the first fake-player cooldown does not suppress the second. |
| Two replays use the same cooldown-setting item | Cooldown state does not leak between replays sharing a world fake player. |
| Consecutive films use armor, bottles/buckets, beds, or a long-use item through the shared fake player | Active use, sleeping/bed occupancy, inventory, armor, selected slot, Shift, and cooldown state are clean at every action entry and exit, including failures. |
| Admin player starts a command-action film for an ordinary target | Authority comes from the real issuer, never the target/fake player; command `@s` and position remain anchored to the replay actor/fake execution context. |
| Console or legacy overload starts targeted playback | Purely visual films still play; effectful actions and first-person state fail closed because requester authority is absent. |
| Primary attack plus two sweep victims | Each recorded damage hits its original target once; only the primary path fires the attack-target hook. |
| Successful attack on a non-Living entity, followed by a failed attack | The successful target records/replays exactly once; the failed attack records nothing and never duplicates the Living recorder. |
| Main-hand and offhand swings are recorded in the same film | Each replay uses its persisted hand and drives the matching procedural arm without changing legacy main-hand serialization. |
| Non-admin or revoked issuer reaches Place/Break/Drop/Use/Interact/Attack/Damage/Chat/Command clips | No world/player effect occurs; revocation inside one replay stops before its next clip. An online level-2 issuer can execute valid bounded inputs only. |
| Two concurrent ActionPlayers show break progress for structurally equal or reordered Replays | Each exact Replay keeps a distinct stable negative crack-overlay ID; deleting/replacing a Replay does not leak its identity map. |
| Two runtimes for one target enable first-person state through live sync | Only the runtime holding the target lease mutates inventory/health/form; the conflicting sync rolls back before mutation and stop/disable/reset releases exactly once. |
| Target removed before replay | Ordinary target action no-ops instead of retargeting a nearby entity. |
| Film Actor recreated before replay | Target rebinds by replay id and entity type. |
| Two concurrent playbacks share replay ids and positions | Each action resolves only actors owned by its current `ActionPlayer`. |
| Target replay is reordered or deleted | Target id follows reorder; deletion keeps a UUID tombstone and no-ops instead of raycasting another entity. |
| Static top-level/nested form anchors survive replay reorder/delete | Every anchor follows replay identity; deleted targets become no-attachment. |
| Two structurally identical replays are dragged, recorded, selected, or shared | Every UI/controller path resolves the exact Replay object, not the first equal value. |
| Main/offhand mob interaction, including name tag/spawn egg | Recorded hand and pre-use stack select the same mob/item branch. |
| `interactAt` armor-stand/body-part hit | Entity-relative hit vector and `onInteractEntityAt` hook are preserved. |
| Shift entity interaction | Secondary-use state is applied only for the action and restored afterward. |
| Unauthorized ModelBlock click | No editor packet/UI opens; click remains client SUCCESS/server CONSUME. |
| Authorized ModelBlock click | Editor opens once and mutation packets remain subject to server validation. |
| Add Clip menu | `interact_entity` is hidden, while recorded/serialized entity actions still load and show their panel. |

## Upgrade guard

`ServerPlayerInteractionMixin` targets the current NeoForge 1.21.1 anonymous
`ServerGamePacketListenerImpl$1` handler. Any NeoForge upgrade must rerun the
bytecode-shape regression and perform a server-start Mixin application smoke
test before release.
