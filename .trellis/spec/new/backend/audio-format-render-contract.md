# Audio Format and Render Contract

## 1. Scope / Trigger

This contract applies whenever code changes any audio boundary in `new/` that
decodes, imports, converts, excerpts, renders, mixes, or writes PCM audio. The
primary implementation surfaces are `PcmEncoding`, `ChannelLayout`,
`PcmFormat`, `PcmSamples`, `Wave`, `WaveReader`, `VorbisReader`, `WaveWriter`,
`AudioImportPolicy`, `AudioRenderer`, and `MinecraftSoundMixer`.

The contract has two distinct channel operations that must not be conflated:

- a one-file `Wave.convertLayout(...)` conversion changes the stored channel
  representation; and
- a renderer/mixer routes a source onto an output bus before the single master
  limiter and quantizer.

Only `MONO` and `STEREO` are implemented. `SURROUND_5_1` exists only as the
reserved persisted identifier `"5.1"`; HRTF/binaural is not a layout in this
contract. Any new codec, layout, importer option, renderer adapter, or encoder
argument must preserve these boundaries and extend the executable tests before
it is advertised as supported.

## 2. Signatures

The following signatures and semantic responsibilities are stable:

```java
enum PcmEncoding {
    PCM_U8, PCM_S16_LE, PCM_S24_LE, PCM_S32_LE, IEEE_FLOAT32_LE;
    static PcmEncoding fromWaveFormat(int waveTag, int bitsPerSample);
}

enum ChannelLayout {
    MONO("mono", 1, true),
    STEREO("stereo", 2, true),
    SURROUND_5_1("5.1", 6, false);

    static ChannelLayout fromChannelCount(int channels);
    static ChannelLayout fromId(String id);
    static ChannelLayout normalizeExport(String id);
    static ChannelLayout normalizeExport(String id, Consumer<String> diagnostic);
}

record PcmFormat(PcmEncoding encoding, ChannelLayout layout, int sampleRate) {
    int channels();
    int bytesPerSample();
    int bytesPerFrame();
    long byteRate();
    int waveTag();
}

final class PcmSamples {
    static double readNormalized(PcmEncoding encoding, byte[] data, int offset);
    static double readNormalized(Wave wave, long frame, int channel);
    static void writeNormalized(PcmEncoding encoding, byte[] data, int offset,
                                double value);
}

class Wave {
    Wave(PcmFormat format, byte[] interleavedData);
    PcmFormat getFormat();
    long getFrameCount();
    Wave convert(PcmEncoding targetEncoding);
    Wave convertTo16();                         // preserves layout
    Wave convertLayout(ChannelLayout target);   // explicit conversion only
    Wave excerpt(float fromSeconds, float toSeconds); // preserves layout
    Wave excerptMono(float fromSeconds, float toSeconds); // legacy explicit mono adapter
}

class WaveReader {
    Wave read(InputStream stream) throws IOException;
    Wave read(InputStream stream, String source) throws IOException;
}

class VorbisReader {
    static Wave read(Link link, InputStream stream) throws IOException;
    static Wave read(Link link, InputStream stream,
                     AudioDecodeLimits limits) throws IOException;
}

class WaveWriter {
    static void write(File file, Wave wave) throws IOException;
    static void write(OutputStream callerOwned, Wave wave) throws IOException;
    static void writeHeader(OutputStream callerOwned, PcmFormat format,
                            long dataLength) throws IOException;
}

enum AudioImportPolicy {
    SOURCE, MONO, STEREO;
    ChannelLayout targetLayout();
    List<String> buildFfmpegArguments(String input, String output);
}

static AudioRenderResult AudioRenderer.renderAudioResult(
    File output, List<AudioClip> clips, int totalDuration, int sampleRate,
    float from, float to, ChannelLayout layout,
    BooleanSupplier cancelled, BiConsumer<Long, Long> progress);

static AudioRenderResult MinecraftSoundMixer.mixToFileResult(
    File output, List<CapturedSound> sounds, List<ListenerFrame> listenerFrames,
    Wave filmAudio, Map<ResourceLocation, Wave> resourceSnapshot,
    int sampleRate, double frameRate, int totalFrames, ChannelLayout layout,
    BooleanSupplier cancelled, BiConsumer<Long, Long> progress);

record AudioRenderResult(Status status, File output, ChannelLayout layout,
                         long frames, String message, Throwable cause) {}
```

Compatibility boolean entry points may remain, but they return `true` only for
`AudioRenderResult.Status.SUCCESS`. They must not collapse `EMPTY`,
`CANCELLED`, `UNSUPPORTED_FORMAT`, `MISSING_RESOURCE`, `IO_FAILURE`, or
`MIX_FAILURE` into success.

## 3. Contracts

### PCM and frame model

PCM data is interleaved by complete frame and is explicitly little-endian.
Stereo order is always left, then right. `data.length` must be divisible by
`format.bytesPerFrame()`; frame count is `data.length / bytesPerFrame`, and
duration is `frameCount / sampleRate`. Code must index a `(frame, channel)` and
must not infer time or channels from raw byte counts or an assumed 16-bit sample.

| Encoding | WAV identity | Normalized decode rule |
| --- | --- | --- |
| `PCM_U8` | tag `1`, 8 bits | `(unsignedByte - 128) / 128.0` |
| `PCM_S16_LE` | tag `1`, 16 bits | signed LE value `/ 32768.0` |
| `PCM_S24_LE` | tag `1`, 24 bits | sign-extend bit 23, then `/ 8388608.0` |
| `PCM_S32_LE` | tag `1`, 32 bits | signed LE value `/ 2147483648.0` |
| `IEEE_FLOAT32_LE` | tag `3`, 32 bits | finite IEEE float value; do not identify float from width alone |

`PcmSamples.writeNormalized` rejects non-finite values and clamps only at the
encoding boundary. `Wave.convert(...)` preserves frame count, source layout,
and channel order. `Wave.excerpt(...)` copies whole interleaved frames, uses a
half-open time range, and preserves the source layout; callers request mono or
stereo separately.

### Strict WAV boundary

`WaveReader` accepts only a bounded `RIFF`/`WAVE` container with exactly one
valid `fmt ` chunk and exactly one `data` chunk. It must:

- perform complete-read/complete-skip semantics, including streams that return
  short reads or zero from `skip`;
- interpret RIFF/chunk sizes as unsigned, use checked arithmetic, remain inside
  the declared RIFF boundary, and consume the pad byte after every odd chunk;
- reject truncated headers/payloads, duplicate or missing core chunks, partial
  PCM frames, invalid `blockAlign`, invalid `byteRate`, and non-positive rates;
- accept tag `1` only for U8/S16/S24/S32 and tag `3` only for float32;
- accept `WAVE_FORMAT_EXTENSIBLE` (`0xfffe`) only when valid bits equal container
  bits, the GUID is canonical PCM or IEEE float, and the mask is zero or
  front-center (`0x4`) for mono or front-left/front-right (`0x3`) for stereo;
- accept only one or two channels and never truncate a wider input; and
- enforce `AudioDecodeLimits` before allocation. The production defaults are
  512 MiB encoded container, 256 MiB single chunk, 256 MiB decoded data, and
  100,000,000 decoded frames.

Malformed optional `LIST`/`cue ` metadata may be ignored with at most one
bounded diagnostic per asset; malformed core audio is fatal. `WaveWriter`
validates the same PCM model, emits correct unsigned RIFF/data sizes and odd
padding, uses an 18-byte WAVEFORMATEX plus `fact` for float32, and never closes
a caller-owned `OutputStream`.

### Strict Vorbis boundary

`VorbisReader` reads the encoded stream through the configured container limit,
accepts only one or two decoded channels, and returns interleaved
`PCM_S16_LE` at the decoder-reported sample rate. The decoded frame count must
equal the reported stream length: an early zero/short decode, decoder error, or
extra decoded frame is malformed input, not silence. Encoded and decoded native
buffers and the stb_vorbis handle are released on every success and failure
path. Vorbis does not invent WAV metadata.

### Import channel policy

`AudioImportPolicy.SOURCE` is channel-preserving:

- `targetLayout()` is `null`;
- its FFmpeg arguments contain no `-ac` option;
- a WAV source is validated and copied without transcoding; and
- a non-WAV source may become PCM16 WAV, but FFmpeg retains the decoded source
  channel count because no output channel count is requested.

Only `MONO` and `STEREO` are explicit conversion policies; they emit exactly
`-ac 1` and `-ac 2`, respectively, and the produced WAV is validated against
the requested layout before publication.

### Channel matrices: conversion versus rendering

These two matrices are intentionally different at mono-to-stereo. `x`, `L`,
and `R` are normalized source samples before destination encoding.

| Source -> destination | `Wave.convertLayout` (stored-file conversion) | Render/mixer output bus |
| --- | --- | --- |
| mono -> mono | `M = x` | `M = x` |
| mono -> stereo | `L = x`, `R = x` | `L = x * sqrt(0.5)`, `R = x * sqrt(0.5)` |
| stereo -> mono | `M = 0.5 * L + 0.5 * R` | `M = 0.5 * L + 0.5 * R` |
| stereo -> stereo | preserve `L`, preserve `R` | preserve `L`, preserve `R` |

Unity duplication is correct for a file representation conversion. Equal-power
centering is correct when placing a mono source on a stereo master bus. Do not
make either path call the other merely to reuse code. Stereo Minecraft assets
also retain their authored L/R image; positional panning applies to mono event
gain before the bus matrix, not to authored stereo channels.

### Render and master boundary

Film and Minecraft paths route through the same bounded block accumulator. An
output block contains at most 8,192 frames. Sources are resampled with continuous
fractional position and linear interpolation, accumulated in float without
per-source clamp or quantization, limited once to `[-1, 1]`, and then quantized
once to interleaved `PCM_S16_LE`.

Film range semantics are `[startSeconds, endSeconds)` with:

```text
outputFrames = ceil((endSeconds - startSeconds) * outputSampleRate)
```

Captured-sound mix duration uses the same ceiling at the video-frame boundary:

```text
outputFrames = ceil(totalVideoFrames / frameRate * outputSampleRate)
```

A successful result reports that exact frame count and a WAV whose channel
count equals the requested supported layout. Cancellation is checked before
publication and at block/preparation boundaries; failure or cancellation must
not publish the final output.

### Reserved layout and compatibility boundary

Runtime validation and persisted-value migration are separate operations:

```text
persisted "mono"                     -> MONO
persisted "stereo"                   -> STEREO
persisted missing/blank/unknown/"5.1" -> MONO + one bounded diagnostic
typed runtime SURROUND_5_1 request    -> reject as unsupported
```

The legacy fallback exists only while loading old export settings. It must run
before request/descriptor/encoder creation. A caller that explicitly supplies
`ChannelLayout.SURROUND_5_1` must receive a typed unsupported result; it must
not be silently changed to mono.

## 4. Validation & Error Matrix

| Condition | Boundary | Required observable outcome |
| --- | --- | --- |
| Unknown WAV tag/bit combination or extensible GUID/mask | `WaveReader` | `UnsupportedAudioFormatException` with source identity |
| WAV channel count other than 1 or 2, including 5.1 | `WaveReader`/`PcmFileSource` | `UnsupportedAudioFormatException`; no channel truncation |
| Bad RIFF/WAVE signature, missing/duplicate core chunk, invalid alignment/rate, partial frame, or truncation | `WaveReader` | `MalformedAudioException`; never `null` or partial-success audio |
| Declared/decoded size exceeds a configured limit | WAV/Vorbis reader | `AudioDecodeLimitException` before the prohibited allocation |
| Vorbis reports channels other than 1 or 2 | `VorbisReader` | `UnsupportedAudioFormatException` and decoder/native cleanup |
| Vorbis is empty, truncated, short, overlong, or returns a decoder error | `VorbisReader` | `MalformedAudioException` and decoder/native cleanup |
| Float sample is NaN or infinite | `PcmSamples`/`Wave.convert`/render | deterministic `IllegalArgumentException` or render `MIX_FAILURE`, including frame/channel context where available |
| `Wave` metadata and data disagree or end in a partial frame | constructor/accessor | reject deterministically; do not derive a different format from bytes |
| `SOURCE` import | importer | no `-ac`; output retains one/two source channels |
| Explicit import conversion | importer | `MONO -> -ac 1`, `STEREO -> -ac 2`; mismatched converted output fails publication |
| Persisted missing, blank, unknown, or `"5.1"` layout | setting deserialization | normalize to `MONO` and emit at most one bounded migration diagnostic |
| Explicit `SURROUND_5_1` format/render/mix request | `PcmFormat`, renderer, mixer | constructor rejection or `UNSUPPORTED_FORMAT`; no output or encoder arguments |
| Missing decoded resource | renderer/mixer | `MISSING_RESOURCE`; do not report success |
| Valid request with no intersecting/audible source | renderer/mixer | `EMPTY`; do not publish a misleading successful WAV |
| Cancellation before/during render | renderer/mixer | `CANCELLED`; clean the session-owned temporary and do not publish final output |
| Output/open/write/move failure | renderer/mixer | `IO_FAILURE`; preserve any pre-existing destination |
| Invalid range/rate/count or unexpected mix failure | renderer/mixer | `MIX_FAILURE` with bounded message/cause |

Typed decode exceptions extend `IOException`. Readers and adapters propagate
them; they do not print a stack trace, swallow the error, or return `null`.

## 5. Good/Base/Bad

### Good

A stereo PCM24 WAV at 44.1 kHz has valid RIFF sizes, a `blockAlign` of 6, a
`byteRate` of `44100 * 6`, complete L/R frames, and independent left/right
impulses. `SOURCE` import copies it without `-ac`; `WaveReader` preserves PCM24,
rate, layout, frame count, and L/R order. Rendering it onto a stereo 48 kHz bus
interpolates each channel independently, preserves its image, and writes one
PCM16 quantization after master limiting.

### Base

For a mono normalized sample `x = 0.5`:

- `wave.convertLayout(STEREO)` stores approximately `[0.5, 0.5]`; and
- a stereo render stores approximately
  `[0.5 * sqrt(0.5), 0.5 * sqrt(0.5)]` after final PCM16 quantization.

For stereo `[0.75, -0.25]`, both explicit file conversion and render downmix
produce mono `0.25` before final encoding. A same-layout operation preserves
channels and frame count.

### Bad

- A WAV with six channels, an unsupported tag, bad mask, duplicate `data`, or
  a one-byte partial stereo frame is rejected rather than relabeled or padded.
- A truncated Vorbis stream is a typed malformed decode, not a shorter
  successful `Wave` and not zero-filled tail audio.
- `SOURCE` import must not contain hidden `-ac 1`.
- A typed 5.1 render request must not use the legacy setting fallback; it ends
  as `UNSUPPORTED_FORMAT` before media is created.
- A mono renderer must not call `Wave.convertLayout(STEREO)`, because unity
  duplication changes the render bus gain policy.

## 6. Tests Required

Run the dependency-light gates from `new/`:

```powershell
.\gradlew.bat --no-daemon testAudioFormat
.\gradlew.bat --no-daemon testVideoExportUtils
.\gradlew.bat --no-daemon testAudioVerification
.\gradlew.bat --no-daemon check
```

`check` must continue to depend on all three focused tasks. The required
assertion points are:

1. `testAudioFormat` / `AudioFormatContractTest`
   - decode handwritten mono and stereo U8, S16, S24, S32, and float32 fixtures
     to their exact sign, order, format, frame count, and normalized values;
   - assert float identity comes from tag `3`, PCM32 from tag `1`, PCM24 sign
     extension is correct, and non-finite float conversion identifies its
     frame/channel;
   - assert short-read streams succeed only when all declared bytes arrive;
     truncation, duplicate/missing core chunks, bad padding/alignment/rate,
     unsupported extensible GUID/mask, and allocation-limit fixtures throw the
     exact typed error class;
   - assert WAV write/read round trips every supported encoding/layout, odd
     data has one pad byte, float output has WAVEFORMATEX plus a correct `fact`
     frame count, and the stream overload flushes without closing its owner;
   - assert `Wave.excerpt` and `convertTo16` preserve stereo, mono-to-stereo
     file conversion yields `[x, x]`, stereo-to-mono yields
     `0.5 * L + 0.5 * R`, and 5.1 conversion throws;
   - assert `SOURCE` arguments have no `-ac`, while explicit policies contain
     exactly `-ac 1` or `-ac 2`; and
   - assert mono/stereo Vorbis retains rate, frame count, and L/R identity;
     three-channel, truncated, short, and limited inputs fail with no native
     allocation growth after cleanup.

2. `testVideoExportUtils` / `AudioRendererMixerFocusedTest`
   - assert render mono-to-stereo is
     `[x * sqrt(0.5), x * sqrt(0.5)]` within final PCM16 tolerance while
     `Wave.convertLayout` remains `[x, x]`;
   - assert stereo left/right impulses survive stereo output and
     `[0.75, -0.25]` downmixes to `0.25`;
   - assert file-backed normalizer, film renderer, and Minecraft mixer use the
     same render matrix, including authored-stereo preservation;
   - assert 44.1 kHz and 48 kHz ranges report and write the exact ceiling frame
     count with no half-frame/channel shift;
   - assert accumulation occurs before the single limiter/quantizer and source
     ordering stays within numeric tolerance; and
   - assert explicit 5.1 returns `UNSUPPORTED_FORMAT`, cancellation/failure
     publishes no target, and existing output is not replaced.

3. `testAudioVerification` / `AudioLayoutAvailabilityTest`
   - assert the supported/selectable set is exactly `[MONO, STEREO]`;
   - assert legacy missing/unknown/`5.1` values resolve and serialize as mono,
     while explicit typed 5.1 is rejected by format, renderer, mixer, recorder,
     mux, and encoder argument boundaries; and
   - assert generated WAV/encoder descriptors advertise only one or two
     channels and never advertise 5.1, HRTF, or binaural output.

When FFmpeg and ffprobe are available, the strict media gate is additional and
must not replace the hermetic gates:

```powershell
.\gradlew.bat --no-daemon testAudioMediaIntegration `
  -PrequireMediaTools=true `
  -PffmpegPath="C:\path\to\ffmpeg.exe" `
  -PffprobePath="C:\path\to\ffprobe.exe"
```

It must probe the selected one/two-channel layout, 48 kHz delivery rate, stream
presence, duration, and start/middle/end marker alignment. Missing tools are a
failure in strict mode, not a pass.

## 7. Wrong vs Correct

### Wrong: infer the encoding from sample width

```java
if (bitsPerSample == 32) {
    decodeAsFloat(data);
}
```

### Correct: validate the WAV tag and width together

```java
PcmEncoding encoding = PcmEncoding.fromWaveFormat(waveTag, bitsPerSample);
double sample = PcmSamples.readNormalized(encoding, data, offset);
```

### Wrong: destroy source channels during default import

```java
arguments.addAll(List.of("-ac", "1")); // hidden downmix in SOURCE mode
```

### Correct: omit channel conversion unless the caller requested it

```java
if (policy.targetLayout() != null) {
    arguments.add("-ac");
    arguments.add(Integer.toString(policy.targetLayout().channels()));
}
```

### Wrong: reuse file conversion as stereo-bus routing

```java
Wave stereoBusInput = monoWave.convertLayout(ChannelLayout.STEREO);
// Produces [x, x], which is not the render matrix.
```

### Correct: apply equal-power centering in the renderer

```java
float centered = sample * gain * (float) Math.sqrt(0.5D);
left[destination] += centered;
right[destination] += centered;
```

### Wrong: silently accept wider layouts or explicitly requested 5.1

```java
ChannelLayout layout = requested == ChannelLayout.SURROUND_5_1
    ? ChannelLayout.MONO : requested;
```

### Correct: fallback only while migrating persisted text; reject typed input

```java
ChannelLayout migrated = ChannelLayout.normalizeExport(persistedText, diagnostic);

if (requested == null || !requested.supported()) {
    return AudioRenderResult.failure(
        AudioRenderResult.Status.UNSUPPORTED_FORMAT,
        output, requested, 0L, "Unsupported audio output layout", null);
}
```
