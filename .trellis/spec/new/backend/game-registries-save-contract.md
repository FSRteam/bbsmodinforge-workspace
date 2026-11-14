# Game registries and save migration contracts

## Registry context for vanilla codecs

`data.GameRegistries` is the shared owner of source selection and NBT ops.
Bootstrap registers callbacks, rather than capturing a world's registry provider:
the common source queries ServerLifecycleHooks, and the preferred client source
queries the current play connection. `lookup()` selects the first available
source claiming the current thread, otherwise the first available fallback.

The server predicate must recognize NeoForge SidedThreadGroups.SERVER **and its
descendant thread groups** as well as the actual MinecraftServer main thread.
Client and integrated-server registries can contain the same entry keys but own
different holders. Encode against the registry owning the original stack; decode
against the registry owning the receiving stack. Registry key equality does not
prove holder ownership.

All ItemStack and BlockState codec consumers, including item-picker NBT display
and edits, use GameRegistries.nbtOps. With no provider the result is NbtOps.INSTANCE.
Explicit ItemStack provider overloads use the same shared per-provider cache.
There must be no duplicate provider routing or ops cache in a codec factory.

The cache has weak provider keys **and weak ops values**. RegistryOps may retain
its provider, so strong values could defeat weak keys and retain old worlds.
Cache lookup/create is synchronized; source registration uses a snapshot-safe
list. Do not add another static strong reference to a world registry.

ItemStackKeyframeFactory.currentOps and setClientRegistryAccess remain public
compatibility adapters to the owner. Replacing/clearing the injection removes
its previous source without removing normal bootstrap sources. The Optional
tryFromData boundary must distinguish invalid/null input from the valid empty
map representing an empty inventory slot. Explicit-provider overloads remain
available for callers that already know the owning registry.

## Raw migration and identity

`bbs_version` is document metadata. BaseManager runs raw migrations before
constructing values, refuses future versions without rewriting their files,
and stamps only outgoing saves. FilmManager supplies FilmStableIds, while
Film.fromData handles manager-bypassing imports and remote data.

The stable-ID pass must be deterministic across independent reads and idempotent.
Reserve valid saved IDs before generating IDs for legacy positions; repair
duplicate saved IDs before resolving references. Preserve stable IDs under list
insertion, reorder, removal, and binary save/reload.

FormStableIds owns both properties-map and tracks-list path migration, nested
state paths, legacy bodyParts.parts precedence, replay preset properties, and
axes_preview_bone. FormArchitect calls it before constructing known or missing
forms; Replay.fromData calls ensureReplay for standalone replay fragments.
Keep both authored tracks when a migrated key collides with a stable key.

Film anchors and camera attachments resolve against the target replay's form.
An already stable replay selector can still have an old indexed attachment;
convert that attachment without changing the selector. Include FSR camera_tracks
and recorded action-target references. Bare replay imports have no source order:
clear unresolved numeric action targets to preserve UUID fallback. Cross-film
paste prunes foreign stable action IDs through the shared migration owner.

DataBytesStorage and DataURLStorage are deprecated public addon compatibility
wrappers. Runtime byte/packet/URL consumers use stream codecs; do not restore an
independent active path through the retired wrappers.

## Verification

- `testReplayKeyframeConsistency` includes GameRegistriesTest: distinct actual
  enchantment providers, client/server holder identity, main/descendant server
  groups, source availability/preference, per-provider cache/world replacement,
  explicit providers, typed failure/empty slots, item-picker NBT, and BlockState.
- `testSaveVersionMigration` includes real manager file I/O, future-version
  refusal, FilmStableIdsMigrationTest's FSR extensions, and the twelve raw legacy
  StableIdsMigrationTest scenarios from the pinned FS range.
- Parent integration runs compileJava, compileClientJava, check, and packaging;
  headless codec success does not establish Minecraft visual acceptance.

## Scenario: Film disk loading with raw authority preflight

### 1. Scope / Trigger

Applies when FilmManager overrides the generic manager load path to authorize
raw data before any registered Film/action-clip factory can run. A future-format
file must be refused before construction, so opening and saving cannot downgrade it.

### 2. Signatures

- `FilmManager.loadRaw(String id)` returns nullable `MapType`.
- `FilmManager.load(String id, Predicate<MapType> rawPreflight)` returns nullable `Film`.
- `BaseManager.protected final boolean upgrade(String id, MapType data)` owns
  format refusal and ascending raw migrations for every manager load path.
- `BaseManager.save(String id, MapType data)` stamps outgoing `bbs_version`.

### 3. Contracts

Read one raw snapshot, reject null or a failed policy, run the shared upgrade on
that same accepted map, then call `create`. The policy receives the original map
before migration. Never authorize one read and construct a second read through
`super.load`, or construct a Film before its authority decision. The upgrade body
must remain shared and final; it does not stamp the input. Raw stable-ID migration
is idempotent, including the existing Film.fromData fallback for legacy imports.

This disk-load contract does not establish future-format refusal for direct
imports, `create` callers or ServerNetwork SAVE. Review their boundaries separately.

### 4. Validation & Error Matrix

| Input / event | Required result |
| --- | --- |
| Null policy or raw map | Return null; do not construct Film or addon clips. |
| Policy returns false | Return null before migration or construction. |
| `bbs_version > SaveVersion.CURRENT` | Log refusal, return null; retain disk bytes. |
| Legacy accepted document | Run shared raw migrations before typed construction. |
| Decode/policy/factory throws Exception or LinkageError | Existing load containment logs and returns null. |
| Normal outgoing save | Stamp the outgoing map through the existing save owner. |

### 5. Good / Base / Bad Cases

- Good: an accepted legacy Film is migrated from the authorized raw snapshot.
- Base: a current-format Film passes the shared gate and constructs normally.
- Bad: bypassing upgrade in the override accepts a future Film, whose subsequent
  ordinary save may discard fields understood only by the newer format.

### 6. Verification Required

Run existing `testSaveVersionMigration` for generic manager disk refusal and
migration, and `testNetworkSecurity` for the real FilmManager preflight overload's
authority-before-factory, rejection and exception behavior. Neither existing
suite directly exercises real FilmManager future-file I/O; record that coverage
limit rather than claiming the composed behavior has a dedicated executing test.
The current parity task prohibits adding or changing tests and fixtures.

### 7. Wrong vs Correct

Wrong after raw policy acceptance:

```java
return this.create(id, rawFilm);
```

Correct before construction:

```java
if (!this.upgrade(id, rawFilm))
{
    return null;
}

return this.create(id, rawFilm);
```
