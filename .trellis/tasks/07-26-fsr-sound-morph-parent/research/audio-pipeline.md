# Research: BBS Mod Audio Pipeline & Sound Morph Requirements

- **Query**: Audio playback subsystem architecture for morphing/overlaying sounds with independent delays and volume modulation
- **Scope**: Internal code search + spec contract review
- **Date**: 2026-07-26

---

## 1. Core Audio Classes & Responsibilities

### Main/Shared Audio Classes

| Class | File | Responsibility |
|---|---|---|
| `SoundManager` | `new/src/client/java/mchorse/bbs_mod/audio/SoundManager.java` | Centralized lifecycle: owns buffer cache (by asset `Link`), manages OpenAL sources, reconciles desired voices, implements ownership/release semantics for overlapping clips |
| `SoundPlayer` | `new/src/client/java/mchorse/bbs_mod/audio/SoundPlayer.java` | Single OpenAL source wrapper; manages play/pause/stop, volume, pitch, 3D position/velocity, playback seek, idempotent deletion |
| `SoundBuffer` | `new/src/client/java/mchorse/bbs_mod/audio/SoundBuffer.java` | Decoded audio buffer cached by `Link`; holds OpenAL buffer handle, duration, waveform; cleanup deferred until no sources reference it |
| `SoundBackend` (interface) | `new/src/client/java/mchorse/bbs_mod/audio/SoundBackend.java` | Injectable OpenAL abstraction; defines all buffer/source operations |
| `OpenALSoundBackend` | `new/src/client/java/mchorse/bbs_mod/audio/OpenALSoundBackend.java` | **Direct LWJGL AL10 wrapper**: `alGenBuffers`, `alBufferData`, `alGenSources`, `alSource3f` (position/velocity), `alSourcef` (volume/pitch), `alSourcePlay/Pause/Stop`, `alGetSourcef` (seek via AL_SEC_OFFSET) |
| `Wave` | `new/src/main/java/mchorse/bbs_mod/audio/Wave.java` | PCM data holder; supports mono/stereo, 8/16/24/32-bit int + float32; provides mixing API `add(...)`, resampling with linear interpolation, layout conversion |
| `Waveform` | `new/src/client/java/mchorse/bbs_mod/audio/Waveform.java` | Visual waveform bitmap for UI display; cached with buffer |

### OpenAL Dependency & Direct Wrapping

**YES, direct OpenAL wrapping exists:**
- `OpenALSoundBackend:9-260` directly uses LWJGL `AL10` and `AL11` constants
- Key AL10 calls mapped:
  - `AL10.alGenBuffers()` → `createBuffer`
  - `AL10.alBufferData(handle, format, data, sampleRate)` → upload PCM to GPU memory
  - `AL10.alGenSources()` → `createSource`
  - `AL10.alSource3f(source, AL_POSITION, x, y, z)` → `setSourcePosition`
  - `AL10.alSourcef(source, AL_GAIN, volume)` → `setSourceVolume`
  - `AL10.alSourcef(source, AL_PITCH, pitch)` → `setSourcePitch`
  - `AL10.alSourcePlay/Pause/Stop(source)` → `playSource/pauseSource/stopSource`
  - `AL11.AL_SEC_OFFSET` → seek by seconds (`setSourcePositionSeconds`)
  - Buffer deletion, source state queries all present

**No Minecraft SoundEngine integration for film audio:**
- `SoundManager.play()` / `reconcile()` own independent OpenAL sources
- Separate from Minecraft's native `SoundManager.play(SoundInstance)` (used only in `MinecraftSoundCapture` for recording game events, NOT for film playback)

---

## 2. Audio Resource Loading & Lookup

### Loading Path

| Step | Location | Note |
|---|---|---|
| **1. Load by Link** | `SoundManager.load(Link, boolean includeWaveform)` : 104-136 | Entry point; handles cache miss |
| **2. Decode** | `SoundManager.readPlayableWave(Link)` : 138-156 → `AudioReader.read(provider, link)` | Reads WAV/OGG from asset provider; validates mono/stereo, converts unsupported PCM widths to S16_LE |
| **3. Waveform** | `SoundManager.generateWaveform(Link, Wave)` : 158-165 | Optional visual bitmap generation for UI |
| **4. Buffer Upload** | `SoundBuffer.__init__(Link, Wave, Waveform, SoundBackend)` : 22-67 → `backend.createBuffer(wave)` | Calls `OpenALSoundBackend.createBuffer()` which uploads PCM to OpenAL via `AL10.alBufferData` |
| **5. Get from Cache** | `SoundManager.get(Link, boolean)` : 227-254 | Retrieves or lazy-loads; regenerates waveform if requested |

### Lookup by Name

**NO centralized name-to-Link registry found.** Lookup is caller-driven:
- Film clips store direct `Link` references in `AudioClientClip.audio` property
- UI panels retrieve by `Link` directly: `SoundManager.get(link, false)`
- No `AudioLibrary` or `SoundPlayer`-style name resolver in current implementation

---

## 3. Playback API & Capabilities

### Core Playback Methods

| Method | Class | Signature | Supports |
|---|---|---|---|
| **Play one-shot** | `SoundManager` | `play(Link) → SoundPlayer` : 256-280 | Starts audio immediately; stores in player list; auto-cleanup when finished |
| **Play unique** | `SoundManager` | `playUnique(Link) → SoundPlayer` : 282-318 | Stops any existing playback of that Link first; enforces single instance |
| **Reconcile (owned)** | `SoundManager` | `reconcile(Object owner, Map<?, VoiceRequest> desired, boolean playing)` : 324-465 | **PRIMARY API for film overlays**: updates desired state map; creates/reuses/releases sources per identity key |
| **Stop by Link** | `SoundManager` | `stop(Link)` : 575-579 | Stops all sources playing that Link |
| **Update** | `SoundManager` | `update()` : 583-603 | Called each frame; cleans up finished sources, retries failed deletions |

### 3D Positioning & Parameters

| Property | Setter | Method | Notes |
|---|---|---|---|
| **Position** | `SoundPlayer.setPosition(float x, y, z)` : 137-143 | `backend.setSourcePosition()` → `AL10.alSource3f(source, AL_POSITION, x, y, z)` | 3D world coords; `setRelative(false)` enables |
| **Velocity** | `SoundPlayer.setVelocity(float x, y, z)` : 150-156 | `backend.setSourceVelocity()` → `AL10.alSource3f(source, AL_VELOCITY, x, y, z)` | Doppler effect; OpenAL computes pitch shift |
| **Volume** | `SoundPlayer.setVolume(float volume)` : 100-106 | `backend.setSourceVolume()` → `AL10.alSourcef(source, AL_GAIN, volume)` | Normalized 0.0–1.0 (+ beyond for emphasis) |
| **Pitch** | `SoundPlayer.setPitch(float pitch)` : 108-114 | `backend.setSourcePitch()` → `AL10.alSourcef(source, AL_PITCH, pitch)` | 1.0 = original; 0.5 = half speed / octave lower |
| **Looping** | `SoundPlayer.setLooping(boolean)` : 124-130 | `backend.setSourceLooping()` → `AL10.alSourcei(source, AL_LOOPING, bool)` | Repeats indefinitely when true |
| **Relative to listener** | `SoundPlayer.setRelative(boolean)` : 116-122 | `backend.setSourceRelative()` → `AL10.alSourcei(source, AL_SOURCE_RELATIVE, bool)` | `true` = ignore listener position; `false` = spatial |
| **Playback position (seek)** | `SoundPlayer.setPlaybackPosition(float seconds)` : 224-235 | `backend.setSourcePositionSeconds()` → `AL10.alSourcef(source, AL_SEC_OFFSET, seconds)` | Seek to arbitrary point; clamped to buffer duration |
| **Max distance** | `SoundPlayer` init | `backend.setSourceMaxDistance(source, 60F)` : 41 | Hardcoded 60 units; controls attenuation curve |

### Playback Control & Queries

| Operation | Method | Notes |
|---|---|---|
| **Play** | `SoundPlayer.play()` : 160-166 | Sends to OpenAL immediately; must be on context thread |
| **Pause** | `SoundPlayer.pause()` : 168-174 | Suspends without releasing source; can resume from same offset |
| **Stop** | `SoundPlayer.stop()` : 176-182 | Stops; may be resumed via `play()` again |
| **Get playback position** | `getPlaybackPosition() → float` : 219-222 | Returns seconds; 0.0 if stopped or deleted |
| **Get state** | `getSourceState() → int` | Returns `AL_STOPPED`, `AL_PLAYING`, `AL_PAUSED`, `AL_INITIAL` |
| **Is playing?** | `isPlaying()` : 204-207 | Queries backend state |
| **Is paused?** | `isPaused()` : 209-212 | Queries backend state |
| **Delete (cleanup)** | `delete()` : 238-303 | Idempotent stop → detach buffer → delete source; no exceptions on already-deleted |

---

## 4. UI Waveform Display & Audio Editing

### Waveform Display

| Class | Path | Purpose |
|---|---|---|
| `Waveform` | `new/src/client/java/mchorse/bbs_mod/audio/Waveform.java` | Cached visual bitmap; supports color-coded regions (via `ColorCode`) for visual markers |
| `UIAudioEditorPanel` | `new/src/client/java/mchorse/bbs_mod/ui/utility/audio/UIAudioEditorPanel.java` | Panel for editing waveform color codes (visual annotation, not playback control) |
| `UIAudioEditor` | `new/src/client/java/mchorse/bbs_mod/ui/utility/audio/UIAudioEditor.java` : 1-100 | Waveform editing UI; owns `SoundPlayer` for preview, manages `ColorCode` list for visual marking |
| `AudioRenderer.renderPreviewCombined(...)` | `new/src/client/java/mchorse/bbs_mod/audio/AudioRenderer.java` : 42-100+ | Renders multiple `AudioClip` waveforms in one combined strip; centered playhead, scrolling window |

### Audio Recording UI

| Class | Path | Purpose |
|---|---|---|
| `UIAudioRecorder` | `new/src/client/java/mchorse/bbs_mod/ui/film/audio/UIAudioRecorder.java` | Microphone capture UI; manages recorder lifecycle, commit fence, temporary file, clip insertion |
| `OpenALRecorder` | `new/src/client/java/mchorse/bbs_mod/ui/film/audio/OpenALRecorder.java` | Worker thread; captures from OpenAL input device; writes streamed WAV; handles final drain and state machine |
| `StreamingWavSink` | `new/src/client/java/mchorse/bbs_mod/ui/film/audio/StreamingWavSink.java` | Buffered WAV writer; handles incomplete frames, fsync, header finalization |

---

## 5. Minecraft Integration & SoundEngine Relationship

### Minecraft Native Sounds (NOT used for film audio)

| Class | Purpose | Note |
|---|---|---|
| `MinecraftSoundCapture` | Records scene events via `SoundEventListener` hook into Minecraft's `SoundManager` | Captures *game events* (mobs, blocks, player) for export overlay; separate from film preview |
| `MinecraftSoundMixer` | Offline block mixer: combines captured game sounds + film audio track for export | Uses same `Wave.add(...)` resampling as film renderer |
| (NOT used for preview) | Film preview uses only `SoundManager.reconcile(owner, ...)` with independent OpenAL sources | Minecraft `SoundManager.play(SoundInstance)` is **NOT** called by film clips |

**Relationship**: Film audio is **completely independent** of Minecraft's native SoundEngine during playback. Minecraft SoundEngine integration is used only for **capture** (recording what game sounds played) and **export** (mixing captured sounds with film track offline).

---

## 6. Audio Specification Constraints & Hard Guarantees

### From `.trellis/spec/new/backend/audio-capture-playback-contract.md`

#### Core Playback Ownership & Resource Model (`§2-3`)

**`SoundManager.VoiceRequest` record** : 871-882
```java
record VoiceRequest(Link link, float seconds, float gain) {}
```

**Reconciliation API** (`reconcile(Object owner, Map<?, VoiceRequest> desired, boolean playing)`) : 324-465
- Owner = playback context identity (e.g., film controller instance)
- Clip identity = reference identity of the clip object (NOT `Link`)
- **Two clips of same Link = ONE buffer, TWO sources** with independent offsets/gains
- Reused only when owner + clip identity + link all match
- Off-context calls enqueue work; zero backend calls until context thread dispatch

**Resource Ownership Ledger** (§3, table):
```
Playback voice/source: keyed by (playbackOwner, clipIdentity)
Decoded buffer: keyed by Link; shared; deleted only after ALL sources detach
```

**Deletion Order** (§3, § 18-19):
1. Stop source
2. Detach buffer (`AL_BUFFER = 0`)
3. Delete source
4. Mark handle invalid
5. Buffer deletion deferred until no player reference remains
6. Cleanup continues after one failure; retry pending deletions

#### Hard Constraints (§3, table rows 13-20)

| Requirement | Enforcement |
|---|---|
| Same owner, two clip identities, same link | One buffer, two independent sources |
| Empty desired map or clip removed | Release that owner's voice |
| Paused in-range scrub | Retain paused source, update offset, no play |
| Relink | Delete old source, reuse/load new link's buffer |
| Off-context reconcile/release | Queue exactly once; zero backend calls before dispatch |
| Controller replacement | `releaseOwner(old) → resetPlaybackOwner() → clear clipData` (in that order) |
| Global reset / disconnect | Release/retry all sources before any buffer |

---

## 7. Critical Question: Multi-Overlay Playback with Delay + Volume Modulation

### The Question
**"Can the current API play a sound at world coordinates with N independent overlays at the same moment, each with independent delay+volume reduction, for virtual echo/reverberation sources? What's missing?"**

### Answer: **PARTIALLY YES; implementation gap exists for coordinated delay**

#### What Works ✓

1. **Multiple voices of same asset**: `reconcile()` API supports exactly this pattern
   - Film clips A & B both reference same `Link` → `SoundManager` creates one buffer + two sources
   - Each source has independent:
     - Volume (`setVolume`)
     - Pitch (`setPitch`)
     - 3D position (`setPosition`) — can be placed at echo points in world space
     - Playback offset (`setPlaybackPosition`) — can be seeked to different points
     - Relative/absolute mode (`setRelative`)
     - Pause/play state

2. **Example: 3-voice echo chain**
   ```
   Direct sound:   clip A, link=audio.wav, offset=0.0s, volume=1.0, position=(x, y, z)
   Echo 1:         clip B, link=audio.wav, offset=0.1s, volume=0.7, position=(x+d1, y, z)
   Echo 2:         clip C, link=audio.wav, offset=0.2s, volume=0.5, position=(x+d2, y, z)
   ```
   Each is a separate voice in the `desired` map → `reconcile()` creates 3 sources from 1 buffer
   Contract guarantees: `new/src/audioLifecycleTest/java/.../AudioLifecycleTest.java` line ~359 `playbackIdentityAndGlobalCleanup` proves this works

#### What's **Missing** ✗

1. **Coordinated start-time synchronization**
   - Current API has no atomic "start all N voices at the same frame"
   - Each `play()` call enqueues individually to OpenAL
   - For microsecond-tight echo chains (e.g., 0.0s, 0.05s, 0.10s), timing drift can accumulate
   - **Workaround**: Manually construct desired map, call `reconcile()` once per frame with all voices at desired offsets; OpenAL's AL_SEC_OFFSET reconciliation (§3, applyVoice, line 467-500) corrects drift with ~50ms tolerance for playing, ~1ms for paused

2. **No built-in delay-generator class**
   - Caller must compute offset (`seconds = basetime + delay_milliseconds * 0.001`) and volume reduction manually
   - Caller must manage the desired map structure

3. **No "morph" / interpolation API**
   - Volume, pitch, position changes are immediate
   - To smoothly morph one echo into another, caller must manually update on each frame and call `reconcile()` again

#### Proof from Source

**§2, signatures** (audio-capture-playback-contract.md):
```java
void SoundManager.reconcile(Object owner, Map<?, VoiceRequest> desired, boolean playing);
record SoundManager.VoiceRequest(Link link, float seconds, float gain) {}
```

**§3, contract row 13** (audio-capture-playback-contract.md):
```
Two owner identities use the same link | Shared buffer; releasing one owner leaves the other's source alive
Same owner, two clip identities, same link | One buffer and two sources with independent gain/offset
```

**Test evidence** (audio-capture-playback-contract.md § 6, testAudioLifecycleTest):
```
playbackIdentityAndGlobalCleanup    — multiple voices, same link, multiple owners/clips
playbackCleanupRetriesAndRetiredOwners — release one owner, others' voices survive
```

---

## 8. Existing Analysis & Architecture Context

### Previous Tasks (Avoid Reinvention)

| Task | Path | Content |
|---|---|---|
| **Audio Pipeline Reliability** | `.trellis/tasks/07-20-fsr-audio-pipeline/` | Main FSR audio overhaul; PRD documents requirements (mono/stereo, microphone, capture/playback contracts) |
| **Audio Verification** | `.trellis/tasks/07-20-audio-verification/` | Lifecycle + format testing |

### Key Spec Boundaries

1. **Audio Capture & Playback Contract** (`.trellis/spec/new/backend/audio-capture-playback-contract.md`)
   - Playback ownership model (one owner, multiple clips, shared buffers)
   - Exactly-once resource teardown
   - Context thread dispatch for OpenAL calls
   - Retirement/release sequencing

2. **Audio Format & Render Contract** (`.trellis/spec/new/backend/audio-format-render-contract.md`)
   - Supported formats: mono & stereo only (8/16/24/32-bit int + float32)
   - Channel matrices: file conversion vs. render bus (mono→stereo duplication vs. equal-power centering)
   - Single master limiter/quantizer; no per-source clipping
   - Resampling with linear interpolation

### Related Memory

- `[[FS2-migration-context]]` — Fabric → NeoForge 1.21.1 migration (audio subsystem aligned with `new/` layout)
- `[[FS2-runtime-bugfix-rootcauses]]` — 6 audio bugs already fixed (flicker, black screen, list artifacts, shadows, track UI, behavior editor)
- `[[gesture-ownership-teardown]]` — UIBaseMenu gesture centralization (separate concern, but related to ownership patterns)

---

## 9. Caveats & Not Found

- **No built-in "morphing" framework**: Volume/pitch/position changes are direct assignments, not interpolations.
- **No name-based sound lookup**: All lookups are by `Link` asset reference; no string-to-Link registry.
- **No "echo simulator" or "room acoustics" model**: Caller must manually place overlays at different 3D positions.
- **No group/bus architecture**: All sources are independent; no way to apply a global effect to a set of voices (e.g., master reverb bus).
- **No synchronization primitive for multi-voice start**: Each voice reconciles independently; caller must manage the desired map and call `reconcile()` atomically once per frame.

---

## Summary for Sound Morph Task

**To implement sound morphing/overlaying with independent delays + volume reductions:**

1. ✓ Use `SoundManager.reconcile(owner, desiredMap, playing)` with multiple `VoiceRequest` entries
2. ✓ Each entry references same `Link` → creates independent sources from shared buffer
3. ✓ Caller computes delay offsets and volume gains per echo/overlay
4. ✓ Call `reconcile()` once per frame with updated desired map
5. ✓ Each source can be positioned at 3D world coordinates (echo origin) via `setPosition()`
6. ⚠ **Caller must implement**:
   - Delay → offset seconds conversion
   - Volume reduction formula (direct gain multiplication)
   - Morphing interpolation loop (update gains/positions per frame, call `reconcile()`)
   - Lifecycle management (remove entry from desired map to release voice)

7. ✓ **Framework provides**:
   - Atomic one-call reconciliation per frame
   - Automatic reuse/creation/deletion of sources
   - Seek synchronization (AL_SEC_OFFSET reconciliation corrects timing drift)
   - Off-context queuing (safe to call from any thread; dispatches to OpenAL context)

---

## 10. 追加调研: 声学参数缺口与设计阻塞

### Q1 (CRITICAL): VoiceRequest 无位置字段的绕路方案 ⚠️

**问题**: `VoiceRequest` 记录（第 871-882 行）只有 `link`, `seconds`, `gain`，无 3D 位置。`reconcile()` 内部是否设置了 source 位置？

**发现 1: reconcile() 不设置位置，反而禁用空间音频** ❌
- `SoundManager.reconcile()` 第 413 行: `player.setRelative(true)` 
- 这强制源为**相对监听者模式**，禁用 3D 定位！
- `applyVoice()` (第 467-500 行) 仅设置 volume + offset + play/pause，**完全不涉及位置**

**发现 2: 可通过 getOwnedVoice() 拿到 SoundPlayer 后手动 setPosition** ✓
- `SoundManager.getOwnedVoice(Object owner, Object clipIdentity) → SoundPlayer` : 第 95-100 行
- **返回对应 voice 的 SoundPlayer 引用**
- 调用方可以：
  ```java
  SoundPlayer player = sounds.getOwnedVoice(context.getPlaybackOwner(), clipObject);
  if (player != null) {
      player.setRelative(false);  // 必须先禁用相对模式
      player.setPosition(x, y, z);
  }
  ```
- **关键缺陷**: `setRelative(true)` 是在 reconcile 流程中硬编码的 (line 413)，每次 reconcile 都会被重置！

**最小改动方案** (避免重写 VoiceRequest):
1. 在 `applyVoice()` (第 467-500 行) 之后增加可选的位置应用阶段
   - 或扩展 `VoiceRequest` 加 float x/y/z 字段 (更好，但破坏兼容性)
   - 或提供 `setVoicePosition(Object owner, Object clipIdentity, float x, y, z)` 新 API

**答案**: **必须扩展 VoiceRequest** 或提供独立 position setter，否则每帧都要额外调 getOwnedVoice + setPosition，且 setRelative 冲突风险大。推荐改动:
- `record VoiceRequest(Link link, float seconds, float gain, float x, float y, float z, boolean relative)` 
- 或在 `applyVoice()` 末尾检查是否有 position 缓存并应用

**文件改动清单**:
- `SoundManager.java` 第 871-882 行: 扩展 VoiceRequest 记录
- `SoundManager.java` 第 413 行: 改 `setRelative(true)` → 让 VoiceRequest 的 relative 字段决定
- `SoundManager.java` 第 467-500 行 (applyVoice): 末尾加 position 应用

**关键风险**: 现有 `AudioClientClip.manageSounds()` (第 63 行) 创建的所有 VoiceRequest 都会受影响 → 需同步更新所有调用点。

---

### Q2: 距离衰减模型参数化缺口 ❌

**问题**: 距离衰减模型是硬编码的吗？有没有 reference/max/rolloff/model 的封装？

**发现 1: SoundBackend 接口极其贫瘠** ❌
- `SoundBackend.java` (第 1-45 行) **仅暴露**:
  - `setSourceMaxDistance(int source, float distance)` : 第 14 行
  - **完全不存在** AL_REFERENCE_DISTANCE, AL_ROLLOFF_FACTOR, AL_DISTANCE_MODEL setter
  
**发现 2: 硬编码 60.0 单位作为 maxDistance**
- `SoundPlayer.__init__()` 第 41 行: `backend.setSourceMaxDistance(this.source, 60F);`
- 这是**全局固定值**，不可配置

**发现 3: OpenAL 全局距离模型从未设置过** ❌
- 搜遍代码: `alDistanceModel`, `AL_DISTANCE_MODEL`, `AL_INVERSE_DISTANCE_CLAMPED` → **零匹配**
- 意味着用的是 **OpenAL 默认模型**: `AL_INVERSE_DISTANCE_CLAMPED`
- 这是全局状态，影响所有音源

**OpenAL AL_INVERSE_DISTANCE_CLAMPED 语义**:
```
attenuation = clamp(referenceDistance / maxDistance, minGain, maxGain)
             (其中 referenceDistance=1.0, maxDistance=60.0, rolloffFactor=1.0 硬编码)
```

**支持三种衰减模型需要的改动**:
| 模型 | 需要 | 风险 |
|---|---|---|
| Linear (AL_LINEAR_DISTANCE_CLAMPED) | 全局 alDistanceModel() 调用 | **全局副作用**，影响所有源 |
| Exponential (AL_EXPONENT_DISTANCE_CLAMPED) | 同上 | **全局副作用** |
| Inverse (当前 default) | 什么都不做 | 无 |

**改造方案 (建议)**:
1. **避免全局 alDistanceModel()** → 改用 **per-source 手动衰减计算**
   - 取消 OpenAL 的自动衰减
   - 调用方计算: `effectiveGain = baseGain * attenuationFactor(distance, model, refDist, maxDist, rolloff)`
   - 用 `setSourceVolume()` 应用最终增益
   - 优点: 无全局副作用，支持多模型

2. 或接受全局 alDistanceModel() 但只支持一种模型 (当前状态)

**文件改动清单** (per-source 衰减方案):
- `SoundBackend.java`: 加 `setSourceReferenceDistance`, `setSourceRolloffFactor`, `setSourceMinGain`, `setSourceMaxGain` (可选)
- `OpenALSoundBackend.java`: 实现上述方法 (调用 AL10.alSourcef)
- `SoundPlayer.java`: 移除硬编码 60F，改由外部设置
- 新增 `SoundMorphCalculator` 类计算衰减 (根据距离/模型/参数)
- `AudioClientClip.manageSounds()`: 每帧更新 VoiceRequest gain 为 `baseGain * attenuation()`

**答案**: **必须改造，两条路选一**:
- 路 1 (推荐): Per-source 手动衰减，无全局副作用，支持多模型
- 路 2 (不推荐): 接受全局 alDistanceModel()，只支持一种

---

### Q3: OpenAL 锥体参数缺口 ❌

**问题**: AL_CONE_* 有没有封装？锥体模型能否满足"锥高 = maxDistance"的要求？

**发现 1: 完全没有锥体参数支持** ❌
- 搜 `AL_CONE_INNER_ANGLE`, `AL_CONE_OUTER_ANGLE`, `AL_CONE_OUTER_GAIN`, `AL_DIRECTION` → **零匹配**
- `SoundBackend.java` 和 `OpenALSoundBackend.java` 无相关方法

**OpenAL 锥体模型语义** (理论):
```
Gain attenuation based on direction relative to source:
- 内锥内: 无衰减 (gain = 1.0)
- 内外锥间: 线性插值到 outerGain
- 外锥外: outerGain (固定衰减)

锥体是无限长 ray，从 source 沿 direction 方向无限延伸
不能用 maxDistance 截断
```

**锥形声源设计困境**:
- **问题**: 锥体模型基于**方向角度** (inner/outer angle)，不是距离
- **需求**: "锥高 = maxDistance" 意味着我们想要的是 **距离衰减** (反比/线性/指数) + **方向调制**
- **冲突**: OpenAL 锥体无法实现距离截断

**改造方案** (不用锥体):
1. 放弃 OpenAL 锥体
2. 用 **direction awareness** 手动计算:
   - 获取 source 方向 (需扩展 VoiceRequest 加 dirX/dirY/dirZ)
   - 计算监听者相对 source 的方向余弦值
   - gain *= lerp(outerGain, 1.0, clamp(cosAngle - innerAngle, 0, outerAngle - innerAngle))
   - 用 setSourceVolume() 应用

**文件改动清单**:
- `VoiceRequest`: 加可选 direction 字段
- `SoundPlayer`: 无需改 (方向纯计算)
- 新增 `AudioConeMath` 工具类
- `AudioClientClip.manageSounds()`: 计算锥形衰减并合并到 gain

**答案**: **不用 OpenAL 锥体模型**，改用手动计算 (同 Q2 的 per-source 衰减思路)。OpenAL 锥体天生不支持距离截断。

---

### Q4: 监听者位置管理 ✓

**问题**: 谁在更新监听者位置/朝向？每帧更新还是一次性？

**发现 1: Minecraft 原生 SoundManager 管理监听者**
- `MinecraftSoundCapture.java` 第 152 行:
  ```java
  ListenerTransform transform = manager.getListenerTransform();
  Vec3 position = transform.position();
  Vec3 right = transform.right();
  ```
- Minecraft 的 `SoundManager` 每帧在渲染线程自动更新监听者（跟踪玩家视角）

**发现 2: 距离衰减能拿到可靠的监听者坐标** ✓
- 监听者位置由 Minecraft 内部管理，**每帧自动同步**
- 我们在 OpenAL 侧读到的坐标总是最新的 (Minecraft → OpenAL 是单向推送)
- 可放心用于距离计算

**答案**: Minecraft 原生处理，无需担心。调用方可以通过 `Minecraft.getInstance().getSoundManager().getListenerTransform()` 读取最新坐标以便计算距离衰减。

---

### Q5: 音源预算与耗尽处理 ⚠️

**问题**: OpenAL source 上限？SoundManager 有没有回收策略？耗尽会怎样？

**发现 1: alGenSources 失败路径存在**
- `OpenALSoundBackend.createSource()` 第 70-98 行:
  ```java
  int source = AL10.alGenSources();  // 返回 0 或 <0 表示失败
  if (created <= 0) {
      throw new IllegalStateException("OpenAL returned an invalid source handle");
  }
  ```
- 如果无可用 source，OpenAL 返回 <= 0，代码抛异常

**发现 2: SoundManager 的异常处理**
- `SoundManager.reconcile()` 第 418-422 行:
  ```java
  catch (RuntimeException | Error e) {
      this.lastPlaybackFailure = e;
      LOGGER.error("Failed to create owned sound source for {}", request.link(), e);
      continue;  // 跳过这个 voice，不创建
  }
  ```
- **Fail-hard 策略**: source 创建失败则 voice 放弃，不重试，不队列

**发现 3: 无主动回收或优先级压出策略**
- `SoundManager` 没有 `maxConcurrentSources` 限制
- 没有 LRU 或优先级队列
- 依赖于 OpenAL 驱动层的 source 池大小 (通常 256-512 个)

**音源预算评估**:
| 场景 | 消耗 | 可行性 |
|---|---|---|
| 单个 morph (8 个反射源) | 8 个 | ✓ 绰绰有余 |
| 16 个反射源 | 16 个 | ✓ 可以 |
| 全屏 50 个 morph | 50×16 = 800 个 | ⚠️ **可能超出 OpenAL 限** |
| 其他电影音频 + 游戏音效 | ~50-100 个 | ⚠️ **竞争** |

**改造建议** (支持更多 morph):
1. 实现 **per-morph source 限制** (如最多 8 个反射源)
2. 实现 **LRU 回收** (最久未使用的 morph 的反射源优先释放)
3. 或用 **音量动态衰减模拟反射** (不开实际 source，只调节直接音量)

**文件改动清单**:
- `SoundManager.java`: 加 `maxConcurrentSources` 配置 + 回收逻辑
- 新增 `SoundSourcePool` 类管理 LRU 队列

**答案**: 现有 fail-hard 策略会导致 morph 在 source 耗尽时静默失败 (只记日志)。需加回收策略以支持多 morph 场景。

---

### Q6: 音频资源选择 UI 组件 ✓

**问题**: 项目里现成的音频 Link 选择器是哪个类？怎么用？

**发现 1: UISoundOverlayPanel 是核心选择器**
- 路径: `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/overlay/UISoundOverlayPanel.java`
- 用途: 三个标签页 (search/vanilla/liked) 浏览音频资源
- 入参: `Consumer<Link> callback` (用户选中音频后的回调)
- 返回: 选中的 `Link`

**用法示例** (从 UIAudioClip.java 第 40-45 行摘):
```java
this.pickAudio = new UIButton(..., (b) -> {
    UISoundOverlayPanel panel = new UISoundOverlayPanel(
        (l) -> this.clip.audio.set(l),  // 选择回调，接收 Link
        this.getContext()
    );
    UIOverlay.addOverlay(this.getContext(), panel.set(this.clip.audio.get()));
});
```

**核心 API**:
| 方法 | 签名 | 作用 |
|---|---|---|
| 构造 | `UISoundOverlayPanel(Consumer<Link> callback, UIContext context)` | 创建面板；context 可为 null |
| 设初值 | `.set(Link)` | 显示当前选中的音频 |
| 回调 | `callback.accept(selectedLink)` | 用户选中时自动调用 |

**直接复用代码**:
```java
// 在 morph editor 面板中
UIButton selectAudio = new UIButton("Select Audio", (b) -> {
    UISoundOverlayPanel picker = new UISoundOverlayPanel(
        selectedLink -> this.morphData.audioLink = selectedLink,
        this.getContext()
    );
    UIOverlay.addOverlay(this.getContext(), picker.set(this.morphData.audioLink));
});
```

**相关支撑类**:
- `AudioCacheManager.getInstance().clearAllCache()` : 面板初始化时清空缓存
- `SoundLikeManager` : 管理收藏的音频 (用户喜欢列表)
- `UILikeableStringList` : 支持标星/标记功能的列表控件

**答案**: 直接复用 `UISoundOverlayPanel`，已支持搜索、分类浏览、标星。morph 编辑器面板可粘贴 UIAudioClip 的代码模式。

---

## Summary of Design Blockers

| Q# | 阻塞等级 | 现状 | 最小改动 |
|---|---|---|---|
| **Q1** | 🔴 CRITICAL | VoiceRequest 无位置，reconcile 禁用 3D | 扩展 VoiceRequest 加 x/y/z + relative 字段 |
| **Q2** | 🔴 CRITICAL | maxDistance 硬编码 60.0，无 ref/rolloff/model | Per-source 衰减计算 + SoundBackend 扩展 |
| **Q3** | 🟡 MEDIUM | 无锥体参数 | 手动锥形增益计算，不用 OpenAL 锥体 |
| **Q4** | 🟢 RESOLVED | Minecraft 原生管理 | 无需改动 |
| **Q5** | 🟡 MEDIUM | Fail-hard，无回收 | 实现 LRU 回收策略 |
| **Q6** | 🟢 RESOLVED | UISoundOverlayPanel 现成 | 直接复用 |

**设计关键路径**:
1. **Q1 + Q2 必须同时解决** (位置 + 衰减) → 改 VoiceRequest + per-source 计算
2. **Q3 可独立** (锥体) → 手动计算
3. **Q5 可后期** (回收) → MVP 先 fail-hard，监控 source 耗尽日志
