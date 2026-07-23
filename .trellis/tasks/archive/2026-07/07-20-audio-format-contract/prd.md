# Audio format contract and PCM codecs

## Goal

Give the FSR audio pipeline one explicit, validated contract for PCM sample
encodings and channel layouts. WAV and Vorbis input must either decode to
correct interleaved samples or fail with a useful supported-format error. Audio
import and file slicing must preserve mono/stereo source channels unless a
caller explicitly requests a layout conversion.

This task owns the format boundary and its import/codec adapters. The mixer,
capture/playback lifecycle, export-session lifecycle, and Premiere XML changes
consume this contract but remain in their respective child tasks.

## In Scope

- A shared audio format model used by `Wave`, WAV/Vorbis readers, WAV writer,
  importers, and downstream render/mix callers.
- Supported interleaved little-endian PCM encodings:
  - unsigned 8-bit integer PCM (`PCM_U8`);
  - signed 16-bit integer PCM (`PCM_S16_LE`);
  - signed 24-bit integer PCM (`PCM_S24_LE`);
  - signed 32-bit integer PCM (`PCM_S32_LE`);
  - 32-bit IEEE float PCM (`IEEE_FLOAT32_LE`).
- Mono and stereo channel layouts with stable persisted identifiers `mono` and
  `stereo`. Interleaved stereo order is always left then right.
- A reserved `5.1` channel-layout identifier for forward compatibility. It is
  not selectable, mixed, encoded, or advertised as implemented in this task.
- Strict RIFF/WAVE parsing with bounded chunk sizes, complete reads, odd-chunk
  padding, frame-alignment checks, and preservation of supported LIST/cue
  metadata.
- Safe Vorbis decoding to canonical signed 16-bit little-endian samples while
  retaining one or two decoded channels and rejecting unsupported channel
  counts or malformed/short decode results.
- A WAV writer that validates the format, writes correct RIFF sizes and pad
  bytes, and does not close a caller-owned stream.
- Channel-preserving excerpts and explicit conversion helpers. No importer or
  generic excerpt operation may silently downmix stereo to mono.
- Importer behavior that preserves source channel count by default. A mono or
  stereo conversion is an explicit option/argument and is not a hidden
  `-ac 1` side effect.
- Compatibility adapters for current public `Wave` fields/constructors and
  legacy settings. A missing or invalid export layout resolves to documented
  `mono` for compatibility; a persisted `5.1` value is rejected/falls back and
  never reaches an encoder.

## Requirements

### Format model

1. Define one source of truth for encoding metadata: format tag, bit depth,
   bytes per sample, signed/unsigned or float interpretation, byte order, and
   whether the format is accepted for OpenAL/export.
2. Define one source of truth for channel layout and channel count. `mono` is
   one channel; `stereo` is two channels in L/R order; `5.1` is reserved and
   unavailable. Reject channel counts/layout masks that cannot be represented
   by these layouts rather than silently dropping channels.
3. Keep `Wave`'s existing fields and constructors source-compatible while
   validating them and exposing the new model. Legacy callers must receive
   deterministic errors for invalid combinations instead of corrupt samples.
4. Duration and frame count must be derived from complete interleaved frames,
   not from an assumed two-byte sample. A partial final frame is an error.

### WAV/RIFF reader

1. Accept RIFF/WAVE only after validating the four-byte signatures and the
   declared RIFF boundary. Read all requested bytes (`readFully` semantics);
   never use one `InputStream.read` as proof that a chunk is complete.
2. Parse chunks within the declared boundary, using unsigned sizes and checked
   arithmetic. Enforce configurable decode limits for total container bytes,
   individual chunks, and decoded data before allocating arrays. Honor the
   required pad byte after odd-sized chunks.
3. Require one valid `fmt ` chunk and one valid `data` chunk. Validate sample
   rate, channel count/layout, format tag, bits, block alignment, byte rate,
   frame divisibility, and overflow. Reject unknown integer widths, float
   widths other than 32-bit, compressed/adpcm tags, malformed extensible
   subformats, and unsupported channel masks.
4. Support standard PCM (`1`) and IEEE float (`3`) for the formats listed
   above. WAVE_FORMAT_EXTENSIBLE (`0xfffe`) may be accepted only when its
   valid-bits/container-bits and GUID identify one of those same PCM formats;
   otherwise return an unsupported-format error. Do not infer a format from
   the file extension or from `bitsPerSample` alone.
5. Preserve the interleaved data, channel order, duration, and supported LIST
   and cue metadata. Malformed optional metadata may be ignored with bounded
   diagnostics, but malformed core chunks must fail the read.
6. Throw a typed decode/format exception (with link/path and reason where
   available). Do not print a stack trace and return `null`; callers must be
   able to distinguish unsupported input from an I/O failure.

### Vorbis reader

1. Read the complete bounded stream before handing it to stb_vorbis, and close
   the decoder on every success and failure path. Check allocation sizes and
   the reported frame count before allocating native buffers.
2. Accept only one or two channels. Decode to signed 16-bit little-endian
   interleaved samples, preserve L/R order, and use the actual number of frames
   returned by stb_vorbis. A short/failed decode is an error, not zero-filled
   audio.
3. Return the same validated `Wave`/format model as WAV input. Ogg/Vorbis is
   lossy, so source metadata that cannot exist in Vorbis is not fabricated.

### Conversion and writing

1. Provide one sign-correct sample reader/converter for all supported formats:
   unsigned 8-bit centering, 24-bit sign extension, 32-bit integer scaling,
   float clamping, and deterministic handling/rejection of NaN or infinity.
   `convertTo16` (or its replacement) must retain channel count/order and
   produce exact frame counts.
2. Provide a channel-preserving excerpt operation. Any downmix or mono/stereo
   expansion must be named and explicitly requested by the caller; it must
   operate on frames and apply a documented gain/clip policy.
3. WAV output must emit a validated supported format, correct `fmt ` values,
   32-bit RIFF/data sizes with overflow checks, and a pad byte for odd data.
   The stream overload must leave stream ownership with its caller; the file
   overload owns and closes its file stream. Known LIST/cue metadata should
   round-trip when supplied.

### Import and compatibility

1. `WAVImporter` must validate/copy or transcode a WAV without forcing mono.
   `ToWAVImporter` must omit the unconditional `-ac 1` and preserve source
   channel count; an explicit target layout is the only reason to pass `-ac`.
   FFmpeg conversion must select the audio stream, preserve metadata where
   possible, and report a failed process without leaving a partial output.
2. Keep existing `.wav`/`.ogg` `AudioReader` entry points, but return a typed
   failure for unsupported extensions, malformed files, or unsupported formats.
   Existing consumers that need OpenAL-compatible data must call the shared
   conversion path rather than reinterpret raw bytes locally.
3. Normalize legacy/missing persisted layout values at one boundary. The
   documented compatibility default for export is `mono`; `stereo` is opt-in;
   `5.1` and unknown values are not selectable and resolve to the default with
   a bounded migration diagnostic. Import source layout remains unchanged by
   this export default.
4. Do not add HRTF/binaural behavior or claim support for 5.1 in this task.

## Acceptance Criteria

- [ ] Golden fixtures for 8/16/24/32-bit integer and 32-bit float WAV decode to
      exact expected samples, frame counts, signs, and durations, or fail with
      a supported-format error where the fixture is intentionally unsupported.
- [ ] Fixtures with partial reads, truncated chunks, odd-sized chunks,
      duplicate/missing core chunks, bad block alignment, overflow sizes, and
      malformed extensible GUIDs fail safely without unbounded allocation or a
      `null`/swallowed exception.
- [ ] Mono and stereo WAV/OGG fixtures retain channel count, L/R impulse
      identity, order, and duration through read, conversion, excerpt, and WAV
      write/read round trip.
- [ ] Explicit mono conversion is covered separately and proves that the
      default import/excerpt path does not modify source channels.
- [ ] WAV output has probeable format tag, channels, rate, bit depth, byte
      rate, block alignment, data length, and odd-data padding consistent with
      the model; caller-owned streams remain open.
- [ ] Vorbis fixtures decode only supported 1/2-channel streams, use the
      actual decoded frame count, close native resources on errors, and reject
      unsupported channel counts/short decodes.
- [ ] WAV and non-WAV import tests show no implicit `-ac 1`; explicit mono and
      stereo options produce the requested layout and failed FFmpeg conversion
      leaves no partial destination.
- [ ] Legacy `Wave` constructors and settings deserialize compatibly; absent or
      unknown export layout resolves to `mono`, while `5.1` cannot be selected,
      mixed, or written by the supported export path.
- [ ] Existing compile/test tasks plus the focused audio-format tests and
      `git diff --check` pass.

## Out of Scope

- Mixing, resampling quality, limiter policy, microphone ownership, playback
  voice ownership, video/mux lifecycle, or Premiere XML semantics beyond the
  format API needed by those tasks.
- Actual 5.1/7.1 routing or encoding.
- HRTF, binaural, Ambisonics, Atmos, or other spatial rendering.
- New compressed codecs beyond the existing Vorbis reader and FFmpeg importer
  conversion path.

## Compatibility Notes

The current implementation exposes mutable public `Wave` metadata and assumes
16-bit data in several consumers. The implementation should preserve the
source-compatible surface while moving validation and sample interpretation
behind shared helpers. Downstream tasks must migrate raw-byte reads to those
helpers before enabling non-16-bit sources in a mix or export path.
