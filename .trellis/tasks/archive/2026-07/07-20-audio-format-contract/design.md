# Audio format contract and PCM codecs - technical design

## Current State

The current format boundary is implicit:

- `Wave` exposes mutable integer fields (`audioFormat`, `numChannels`,
  `bitsPerSample`, `blockAlign`, and `byteRate`) but does not validate that they
  describe the bytes in `data`.
- `Wave.convertTo16()` infers float from a four-byte sample instead of the WAV
  format tag, mishandles unsigned PCM8/sign extension, and relies on native byte
  order.
- `Wave.excerptMono()` indexes samples rather than interleaved frames, so a
  stereo source is sliced at the wrong offsets and relabeled as mono.
- `WaveReader` uses potentially partial reads, ignores RIFF boundaries/padding,
  trusts signed chunk sizes, catches every exception, prints it, and returns
  `null`.
- `WaveWriter` writes unchecked metadata and closes caller-owned streams.
- `VorbisReader` trusts reported allocation sizes and expected sample count,
  and does not guarantee native cleanup on every failure.
- Both audio importers unconditionally pass `-ac 1` to FFmpeg.
- Several consumers read `Wave.data` as signed 16-bit PCM regardless of the
  actual input format.

The design keeps the public compatibility surface long enough for the sibling
tasks to migrate, but makes all new code use one validated model and shared
sample accessors.

## Contract Types

Add these main-side types under `mchorse.bbs_mod.audio`:

```java
public enum PcmEncoding
{
    PCM_U8(1, 8, 1, false),
    PCM_S16_LE(1, 16, 2, false),
    PCM_S24_LE(1, 24, 3, false),
    PCM_S32_LE(1, 32, 4, false),
    IEEE_FLOAT32_LE(3, 32, 4, true);
}

public enum ChannelLayout
{
    MONO("mono", 1, true),
    STEREO("stereo", 2, true),
    SURROUND_5_1("5.1", 6, false);
}

public record PcmFormat(PcmEncoding encoding,
                        ChannelLayout layout,
                        int sampleRate)
{
    int bytesPerFrame();
    long byteRate();
}
```

Names may follow local conventions, but the responsibilities and persisted IDs
must remain stable.

`PcmEncoding` owns the legal WAV tag/bit-depth combinations. It must never
choose float merely because a sample occupies four bytes. `ChannelLayout` owns
the channel count and selectability. Only `MONO` and `STEREO` appear in setting
options. `SURROUND_5_1` reserves the persisted ID but fails
`requireImplemented()` and cannot construct a supported runtime `PcmFormat`.
HRTF is not a channel layout and is deliberately absent.

The export setting boundary uses one normalizer:

```text
missing / blank / unknown -> MONO + one bounded migration diagnostic
mono                      -> MONO
stereo                    -> STEREO
5.1                       -> MONO + unsupported-value diagnostic
```

This compatibility default preserves the current mono film export. It does not
affect source import: source mono stays mono and source stereo stays stereo.

## `Wave` Compatibility Strategy

`Wave` remains the decoded-audio carrier in this task. Add validated factories
and accessors without immediately removing the existing fields or constructor
signatures:

```java
Wave(PcmFormat format, byte[] interleavedData)
PcmFormat getFormat()
long getFrameCount()
Wave convert(PcmEncoding target)
Wave excerpt(float fromSeconds, float toSeconds)       // preserves layout
Wave convertLayout(ChannelLayout target)              // explicit only
```

The legacy constructors map `audioFormat + bitsPerSample + numChannels` through
the same factories. They derive `byteRate` and `blockAlign`, or verify supplied
legacy values exactly. Because the public fields are currently mutable,
`getFormat()` revalidates their projection rather than caching a second model
that could go stale. New and migrated consumers use the accessors. The fields
and `convertTo16()` may be deprecated, but their behavior remains compatible:
`convertTo16()` delegates to `convert(PCM_S16_LE)` and preserves layout.

All data is packed interleaved little-endian. `data.length` must be a multiple
of `format.bytesPerFrame()`. Duration is `frameCount / sampleRate`. A generic
excerpt selects whole frames (`floor` start, `ceil` end), copies every channel,
keeps LIST metadata, drops cues outside the interval, and rebases retained cue
positions to the excerpt start. The old `excerptMono()` must not remain a
misleading byte-copy operation; it should be deprecated and delegate to
`excerpt(...).convertLayout(MONO)` so downmixing is explicit in the call graph.

## Sample Conversion

Add one package-owned `PcmSamples` helper used by `Wave`, the mixer task,
waveform generation, and OpenAL preparation. It reads/writes by frame/channel
with explicit little-endian operations; no code should depend on the platform's
native `ByteBuffer` order.

The decoding rules are:

| Encoding | Decode rule |
| --- | --- |
| PCM_U8 | `(unsignedByte - 128) / 128`, with exact zero at byte 128 |
| PCM_S16_LE | signed little-endian 16-bit divided by 32768 |
| PCM_S24_LE | assemble three bytes, sign-extend bit 23, divide by 8388608 |
| PCM_S32_LE | signed little-endian 32-bit divided by 2147483648 |
| IEEE_FLOAT32_LE | little-endian IEEE float, require finite, clamp to [-1, 1] when encoding |

Integer-to-integer conversion uses a wide integer or double intermediate so
the most-negative value does not overflow. Float NaN/infinity is rejected with
the sample/frame location rather than silently becoming an arbitrary integer.
Converting to S16 preserves the exact frame count and channel order.

`convertLayout(MONO)` averages L/R once in normalized sample space and clamps
only while encoding. `convertLayout(STEREO)` duplicates a mono sample into L/R.
There is no 5.1 branch. Mixing/limiting policy remains owned by the mixer task;
this helper only performs an explicitly requested one-source layout conversion.

## Error Model and Limits

Narrow `AudioReader.read` and codec APIs to `IOException`-compatible typed
errors:

```text
AudioDecodeException
|- UnsupportedAudioFormatException  (valid container, unsupported codec/layout)
|- MalformedAudioException          (truncated/inconsistent container or samples)
`- AudioDecodeLimitException        (declared/decoded size exceeds policy)
```

Errors include the source `Link`/path when known, container kind, and a concise
reason. Optional metadata parse problems use one bounded warning per asset;
core format/data problems are thrown. Codecs never print stack traces or return
`null`. `AudioReader` throws an unsupported-extension exception rather than
using `null` as a third outcome.

Use an `AudioDecodeLimits` value passed to testable reader constructors. The
production default limits encoded input bytes, a single RIFF chunk, decoded
data bytes, and decoded frame count. Exact numeric defaults should accommodate
existing production assets while remaining below Java array/native allocation
limits (a 256 MiB decoded-data default is a reasonable initial value). Every
size calculation uses `long`/`Math.multiplyExact` before conversion to `int`.

## RIFF/WAVE Decoder

Replace ad hoc reads in `BinaryReader` with exact primitives:

```java
void readFully(InputStream in, byte[] target, int offset, int length)
int readU16LE(InputStream in)
long readU32LE(InputStream in)
void skipFully(InputStream in, long length)
String readFourCC(InputStream in)
```

`skipFully` must make progress even when `InputStream.skip()` returns zero by
reading/discarding one bounded buffer; EOF is an error. FourCC decoding uses
US-ASCII explicitly.

The reader flow is:

```text
RIFF header
  -> validate "RIFF", unsigned size, and "WAVE"
  -> establish remaining bytes = riffSize - 4
  -> for each complete chunk header inside that boundary
       validate unsigned chunk size against remaining bytes and limits
       read/parse exactly chunkSize bytes
       consume one pad byte when chunkSize is odd
  -> require exactly one fmt and one data chunk
  -> validate format + data together
  -> construct Wave
```

Bytes after the declared RIFF payload are not parsed. Premature EOF fails. A
duplicate core chunk is rejected as ambiguous. Unknown chunks are skipped
within bounds. Reading a data chunk before `fmt ` is allowed because validation
happens after chunk collection, but both remain bounded.

For `fmt `:

- tag `1` accepts only PCM_U8/S16/S24/S32;
- tag `3` accepts only IEEE_FLOAT32_LE;
- tag `0xfffe` requires the complete 22-byte extension, valid bits equal to
  container bits, and the canonical PCM or IEEE-float GUID. Its mask must be
  zero or match mono front-center / stereo front-left+front-right. Other GUIDs,
  masks, channel counts, or valid-bit packing are unsupported;
- `sampleRate > 0`; layout must be implemented; `blockAlign` equals
  channels * bytes/sample; `byteRate` equals sampleRate * blockAlign; data is
  divisible by blockAlign.

LIST and cue parsing occurs through chunk-local bounded streams. Entry sizes and
counts are checked before allocation. Known metadata is stored in existing
`WaveList`/`WaveCue` compatibility objects or a small immutable metadata owner
with compatibility projections. Unknown chunks are skipped, not copied into
unbounded memory.

## Vorbis Decoder

`VorbisReader` receives the same limits and returns canonical
`PCM_S16_LE`/mono-or-stereo `Wave`:

1. Read the complete encoded stream through a bounded reader.
2. Open stb_vorbis and always close its handle in `finally`.
3. Validate sample rate, channels (1/2 only), stream frame count, and checked
   `frames * channels * 2` before native allocation.
4. Decode interleaved samples and use the returned decoded-frame count. A
   negative result or an unexplained short decode is `MalformedAudioException`.
5. Copy only `decodedFrames * channels` samples into little-endian Java data.
6. Free encoded, sample, and intermediate native buffers in `finally` even if
   conversion/copy fails.

The format model deliberately rejects a six-channel Vorbis asset despite the
reserved `5.1` ID; accepting it would imply routing support that does not exist.

## WAV Writer

`WaveWriter` validates `Wave.getFormat()` and exact frame alignment before
writing. It calculates every chunk size and the RIFF size as `long`, rejects
values above unsigned 32-bit RIFF limits or Java stream limits, writes a pad
byte after odd-sized chunks, and includes pads/metadata in the RIFF size.

The full-file writer emits `fmt `, `data`, and supported LIST/cue metadata.
Integer formats use tag `1`; float32 uses tag `3`. Extensible output is not
needed for mono/stereo and is not emitted. Streaming `writeHeader` gains a typed
`PcmFormat` overload; the legacy integer overload validates and delegates.

Ownership is explicit:

- `write(File, Wave)` opens/closes the file with try-with-resources and removes
  a failed partial file (or writes to a sibling temporary file and atomically
  replaces the destination where that behavior is already expected).
- `write(OutputStream, Wave)` flushes if documented but never closes the
  caller-owned stream.
- Header-only callers own body and pad output; their format/data length is
  validated before the header is emitted.

## Import Flow

Introduce a small import policy value with `SOURCE` (default), `MONO`, and
`STEREO`. It maps to no `-ac`, `-ac 1`, and `-ac 2`, respectively. `5.1` is not
representable in this policy.

```text
WAV input
  -> strict WaveReader validation
  -> SOURCE: collision-safe copy preserving bytes/metadata
  -> explicit conversion: FFmpeg/shared PCM conversion to owned temp output

MP3/MP4/FLAC/AIFF/OGG input
  -> FFmpeg select first audio stream, map metadata, encode pcm_s16le WAV
  -> SOURCE: omit -ac so mono/stereo count is retained
  -> explicit MONO/STEREO: append the corresponding -ac value
  -> strict-read produced WAV before publishing it
```

The destination path is selected first, conversion writes a uniquely owned
sibling temporary file, and only a successful process plus strict validation
publishes it. Failure removes only the owned partial file and is reported to the
import caller. Extract FFmpeg argument construction into a pure helper so tests
can prove that SOURCE never includes `-ac 1` without launching FFmpeg.

## Cross-Task Contract

- `07-20-audio-mixer-render` consumes `PcmFormat`, `ChannelLayout`, and
  `PcmSamples`; it owns resampling, accumulation, limiter behavior, and master
  layout conversion.
- `07-20-audio-export-lifecycle` owns the persisted UI setting and FFmpeg/mux
  propagation, but must use this task's IDs/normalizer and compatibility default
  `MONO`.
- `07-20-premiere-audio-export` uses channel-preserving `excerpt()` and the
  resulting format descriptor; it must not call the deprecated implicit-mono
  path.
- `07-20-audio-verification` owns broad real-FFmpeg/ffprobe and end-to-end A/V
  fixtures. This task owns deterministic codec/import unit fixtures.

## Compatibility Matrix

| Existing behavior/API | Required transition |
| --- | --- |
| `new Wave(1, channels, rate, bits, data)` | Retain; validate/map to `PcmFormat` |
| Public WAV metadata fields | Retain temporarily as compatibility projection; new code uses accessors |
| `convertTo16()` | Retain/deprecate; delegate to sign-correct S16 conversion and preserve layout |
| `excerptMono()` | Retain/deprecate; explicit excerpt then mono conversion, never raw stereo relabeling |
| `AudioReader.read(...) == null` | Replace with typed error; update callers in the same change |
| Missing/unknown export layout | Resolve to MONO at the single settings boundary |
| Persisted `5.1` | Recognize ID only to reject/fallback; never expose or encode |
| WAV/ToWAV import | Preserve source channel count unless caller explicitly selects MONO/STEREO |

## Test Design

Add a focused main-side `audioFormatTest` source set/task following the existing
plain-Java test harness pattern. Keep fixtures tiny and construct RIFF bytes in
test helpers so corrupt sizes/padding can be asserted precisely. Include:

- exact min/zero/max sample vectors for U8, S16, S24, S32, float32;
- mono and stereo L/R impulses at 44.1 kHz and 48 kHz;
- channel-preserving conversion and excerpt boundaries, including empty/clamped
  ranges and cue rebasing;
- RIFF partial-read stream, truncation, padding, unknown chunks, duplicate or
  missing fmt/data, invalid sizes/rates/alignments, unsupported tags/widths,
  extensible GUID/mask cases, and decode limits;
- writer/read round trip, odd U8 data padding, metadata round trip, overflow,
  and non-closing output stream;
- Vorbis mono/stereo golden assets plus invalid/truncated/unsupported-channel
  cases where fixture generation is practical;
- pure importer argument/policy tests and a fake process result covering
  successful publication and partial-file cleanup;
- legacy constructor/normalization tests, including missing, unknown, stereo,
  and reserved `5.1` persisted values.

The verification task adds real FFmpeg/ffprobe assertions, but these focused
tests must run without an installed FFmpeg except for an explicitly skipped
integration subcase.
