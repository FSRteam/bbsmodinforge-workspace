# Structural instance holder inventory

## Persisted model holders

| Holder | Field | Thread | Serialization |
| --- | --- | --- | --- |
| `Replay` | `ValueForm form` | server/client main thread | `ValueForm` -> `FormUtils.toData/fromData` |
| `Film` | `Clips camera` | server/client main thread | `Clips` -> camera factory |
| `Replay` | `Clips actions` | server/client main thread | `Clips` -> action factory |
| `BodyPart` and nested forms | nested `ValueForm` values | main/render thread | recursive form `toData/fromData` |
| `ActionRecorder` | live `Clips` | server main thread | same `Clips` path |

## Runtime/UI projections

| Holder | Notes |
| --- | --- |
| `ActionPlayer.cachedForm` | derived from replay form; rebuilt when playback/model holder is refreshed |
| `UIFilmController.playerForm`, form editor and palette fields | UI projections; refresh after model rebuild |
| `UIClips` gesture/selection lists and open `UIClip` panels | UI projections; refresh/close affected editor state |
| particle emitters | component instances are derived from file-backed particle scheme data; unload disables missing components and manager reload reconstructs them |
| `FormCategories` recent/user/extra categories | contains form instances; mark dirty and rebuild plugin-owned extras after commit |

## Decision

Register every host `ValueForm` and `Clips` instance in a weak holder tracker from its constructor. A structural transaction selects holders by the old generation's registered form/clip Classes, snapshots their `MapType` before teardown, and rebuilds in-place after candidate activation. Weak registration covers films, replays, recorders and nested values without a second global strong owner graph. Client-only projections are refreshed at the same safepoint and must not be treated as persistence authorities.
