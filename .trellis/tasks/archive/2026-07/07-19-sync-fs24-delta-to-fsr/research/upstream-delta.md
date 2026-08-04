# Upstream delta audit (2026-07-19)

## Frozen references

- FS local checkout: `temp/bbs-fs-2.3@9b28a8a4` (Fabric 1.20.4, FS 2.3.1).
- Latest master: `f76cce04`; latest 1.21.1: `42313660`; latest 1.20.1: `d10c3b1f`.
- Previously migrated through: master `07bcc918`, 1.21.1 `65bb8c93`.
- New master range: 17 non-merge commits.

## Coverage matrix

| Commit | Intent | Final disposition |
|---|---|---|
| `c8f9cf2f` | scaled-model physics jitter | migrated with continuous pivot rotation extraction |
| `e4d7f7d9` | body-part physics/world/collision | adapted; kept FSR per-renderer state and added world/collision propagation |
| `96110605` | replay-local loop tick | equivalent in `216c821a`; unchanged |
| `eef239e5` | Minecraft sound capture/mix/mux | migrated and hardened for loop closure, WAV recovery and atomic mux replacement |
| `63a510d2` | OBJ/BOBJ picker refresh | migrated |
| `c278e786` | uniform-scale drag CME | migrated |
| `a9d9105a` | next-keyframe null guard | equivalent in `216c821a`; unchanged |
| `25470552` | crash-safe atomic settings write | migrated with same-directory temp, fsync and atomic replace fallback |
| `6a83b60f` | MobForm body-part UI matrix reset | structural N/A; target has no upstream identity-reset path |
| `b564d547` | recent-color layout | migrated |
| `58faa778` | recent-color cap 33 | migrated |
| `99b11184` | diamond marker 1.5 | already present; unchanged |
| `cff7d87a` | version bump to 2.4 | rejected by version lock |
| `32c9cfb6` | audio export/path/artifact fixes | migrated relevant naming/preset/lifecycle fixes; kept stronger target argument parser |
| `1ab0a887` | keep custom FFmpeg args on preset | migrated |
| `b7bd5f22` | translucent form sorting | adapted to NeoForge/Mojmap with deferred queue and Iris/world-end fallback |
| `f76cce04` | fractional GUI scale | adapted to Mojmap `Window#setGuiScale(double)` and high-DPI target dimensions |
| `8979e27f` | Iris relative replay on 1.21.1 | static equivalent via target semantic-world/local-simulation contract; manual Iris smoke pending |

## Local constraints

- Keep `mod_version=2.3.1`; never port `cff7d87a`.
- Keep legacy FSR particle implementation.
- Preserve FSR's stronger simulation-owner and render-space contracts.
- Add Chinese strings for user-visible settings because upstream only updates en/ru.

## Verification

- `compileJava compileClientJava`: pass.
- Full `gradlew check`: pass, 60 tasks.
- Full `gradlew build`: pass, 62 tasks.
- Focused suites included video export, IK physics, UI input mailbox and platform
  resource consistency.
- `git diff --check`: pass; only Git CRLF conversion warnings.
- `mod_version=2.3.1`; `cff7d87a` excluded.
- Particle implementation paths have no diff from the initial boundary.

## Manual smoke matrix

These checks require an interactive Minecraft/Iris runtime and remain pending user validation:

| Area | Cases | Status |
|---|---|---|
| Translucent forms | Iris off/on, model/block/item/mob/label overlap, nested framebuffer | pending manual |
| Relative replay | Iris off/on, actor and non-actor replay while moving camera | pending manual |
| Fractional UI | auto `0`, `0.5`, fractional steps, `4`, high-DPI picking/stencil | pending manual |
| Minecraft sounds | one-shot and looping sounds, cancellation, mux failure WAV recovery | pending manual |
| Body-part physics | scaled parent, collision world, independent chains | pending manual |

## Recommended follow-ups

1. Move Minecraft sound mixing and the FFmpeg mux pass off the caller thread and add
   cancellation checks for long exports.
2. Expose an explicit warning/result when video export succeeds but Minecraft sound
   mixing or muxing falls back to a preserved WAV file.
3. Add pure regression tests for loop wrapping/closure and render-queue suspension,
   exception cleanup and depth-write policy.
