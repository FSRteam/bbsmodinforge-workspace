# Remote streaming upstream study

Date: 2026-07-18

This note records source-level research for improving BBS Web Remote's current
Canvas command stream plus independently decodable JPEG surfaces. It is not a
proposal to copy upstream code. The conclusions below separate transport and
scheduling techniques that fit the current architecture from techniques that
depend on an inter-frame video codec, RTP/WebRTC, or an upstream-specific
protocol.

## 1. Reproducible source snapshot

All repositories were shallow-cloned with `--depth 1` under
`temp/web-remote-upstream/`. Sparse checkout and `--filter=blob:none` were used
where practical.

| Project | Repository | Studied commit | Local directory | License |
| --- | --- | --- | --- | --- |
| Sunshine | https://github.com/LizardByte/Sunshine.git | `9d2409f71b60f1812f482e6dd807dc52e2f72fe7` | `Sunshine` | GPL-3.0 |
| Moonlight common | https://github.com/moonlight-stream/moonlight-common-c.git | `82e25148549d249a80aaf4afc68e2abb07072edf` | `moonlight-common-c` | GPL-3.0 |
| Moonlight Qt | https://github.com/moonlight-stream/moonlight-qt.git | `c0c4d6056569bba40ac4458a3c225c05ff86df6d` | `moonlight-qt` | GPL-3.0 |
| RustDesk | https://github.com/rustdesk/rustdesk.git | `96e2a330b86f1b2ffd31a70a9bb9565cace0d68f` | `rustdesk` | AGPL-3.0 |
| Apache Guacamole server | https://github.com/apache/guacamole-server.git | `6719b20d09b767d30915c5e1c626a7a72fc75a59` | `guacamole-server` | Apache-2.0 |
| Apache Guacamole client | https://github.com/apache/guacamole-client.git | `0b65b785afa3de481d08b90c44c9a748c6f5c316` | `guacamole-client` | Apache-2.0 |
| noVNC | https://github.com/novnc/noVNC.git | `7c36fabe599e053c5a81e98e091ac636f6c1e174` | `noVNC` | MPL-2.0 core |

Because several upstreams are strong-copyleft, use these repositories as
behavioral and architectural evidence. Do not paste implementation code into
the addon without a separate license review.

## 2. Current BBS Web Remote baseline

The existing implementation already includes several low-latency patterns
that upstream projects validate:

- UI and already-compressed media use separate WebSockets. UI may negotiate
  per-message deflate; the JPEG media endpoint explicitly disables it.
- Core surface capture is deadline-paced and skips missed periods instead of
  issuing catch-up bursts (`BBSRenderSurfaceRuntime.nextCaptureDeadline()`).
- GPU readback uses a ring, and `StbJpegSurfaceEncoder` has one replaceable
  latest RGB slot. An encoder that falls behind does not grow an RGB queue.
- `LatestSurfaceBroadcaster` permits only one send in flight per viewer and a
  bounded latest pending frame per surface handle.
- Browser `SurfaceCache` keeps one pending decode per surface and discards an
  asynchronously decoded image if a newer sequence has already arrived.
- UI command frames use complete manifests with reusable chunk definitions,
  and `LatestUiFrameDispatcher` retains the newest not-yet-started frame.

The main missing piece is not another queue. It is an observable feedback loop
from "captured" through "encoded", "sent", "decoded", and "painted", followed
by conservative adaptation of JPEG quality, dimensions, and effective FPS.

## 3. Apache Guacamole

### 3.1 Painted acknowledgement and processing lag

Guacamole's most directly useful mechanism is its end-to-end `sync` boundary.
The browser does not acknowledge a timestamp immediately upon receipt. It first
waits until every preceding asynchronous image task is ready and the display
frame has flushed, then echoes the timestamp:

- `guacamole-common-js/.../modules/Client.js`, the `sync` instruction handler
  around line 1672.
- `guacamole-common-js/.../modules/Display.js`, `Frame.isReady()`,
  `Frame.flush()`, and `Display.flush()` around lines 333-470.
- `guacamole-server/src/libguac/user-handlers.c`, `__guac_handle_sync()` around
  line 107, which derives round-trip and accumulated client processing lag.

This maps cleanly to the current media wire. A browser can acknowledge a
surface sequence only after `createImageBitmap()`/`Image.decode()` has
completed, the sequence is still current, the renderer has installed it, and
at least one animation-frame boundary has committed the Canvas update. The
server can then distinguish network/write delay from browser decode/paint lag.

Guacamole exposes statistics derived from these boundaries in
`Display.js` `notifyFlushed()` around lines 250-325: client FPS, server FPS,
desktop FPS, processing lag, and logical frame drops. This is a suitable model
for the requested detailed link panel.

### 3.2 Pending-state coalescing

Guacamole accumulates new display changes into a pending frame while the
current frame is being planned/encoded. It does not preserve every intermediate
desktop state:

- `guacamole-server/src/libguac/display-flush.c`,
  `guac_display_end_multiple_frames()` around line 304.
- `guacamole-server/src/libguac/display-worker.c`, worker completion and next
  flush around line 395.

The addon already follows the same principle at RGB, encoded media, browser
decode, and UI-frame boundaries. The useful addition is to count each replaced
state at each boundary, so a user can tell whether loss occurred during capture,
encode, server fan-out, or browser decode.

### 3.3 Lag-aware pacing and JPEG quality

When explicit protocol frame markers are unavailable, Guacamole groups changes
with a 10 ms minimum and 100 ms maximum frame duration. It also delays the next
flush according to:

```text
required wait = client processing lag - time since last frame
```

with bounded compensation. See
`guacamole-server/src/libguac/display-render-thread.c` around lines 29-166.

`guac_display_suggest_quality()` in
`guacamole-server/src/libguac/display-worker.c` around line 125 maps processing
lag linearly from JPEG/WebP quality 90 at 20 ms to quality 30 at 80 ms, clamped
to `[30, 90]`.

The exact thresholds should not be copied blindly, but the control signal is
valid for this addon. Use a smoothed painted-ACK delay and hysteresis. Degrade
quickly when the newest-frame slot is repeatedly replaced or paint delay grows;
recover slowly after several healthy windows.

### 3.4 Dirty regions and content-aware encoding

Guacamole compares the current and prior surfaces, partitions work into coarse
cells, reduces solid areas to rectangle commands, detects copyable content,
and merges neighboring updates. Relevant sources:

- `guacamole-server/src/libguac/display-flush.c`, display planning around line
  326.
- `guacamole-server/src/libguac/display-plan.h`, JPEG/WebP thresholds around
  lines 77-105.
- `guacamole-server/src/libguac/display-worker.c`,
  `LFR_guac_display_layer_png_optimality()`,
  `LFR_guac_display_layer_should_use_jpeg()`, and
  `LFR_guac_display_layer_should_use_webp()` around lines 150-300.

This is useful only after the Core surface contract can describe dirty regions
or the addon retains and compares decoded/raw pixels. With today's one full
JPEG surface per frame, the addon cannot send a JPEG sub-rectangle without an
additive wire and Canvas composition contract. A cheap unchanged-frame hash or
GPU-side change signal can still suppress identical frames, but reading all RGB
solely to discover no changes may erase the benefit.

Guacamole's encoder selection is content-aware: low-change/repetitive areas use
lossless operations, while sufficiently large, frequently changing,
photographic regions use JPEG/WebP. This is more relevant to a future surface
tile protocol than to the existing full-surface JPEG.

## 4. noVNC

### 4.1 Ordered asynchronous rendering

noVNC maintains an ordered render queue. If the head image has not loaded, it
stops processing rather than allowing later fills, copies, or frames to overtake
it. It also closes consumed `VideoFrame` objects and clears image sources. See
`noVNC/core/display.js`, `_scan_renderQ()` around line 482.

It renders into a backbuffer, tracks damage bounds, and copies only the damaged
rectangle to the visible Canvas at a frame boundary (`flip()` around line 235).
The addon's `SurfaceCache` already protects per-surface decode order, but a
single painter-order commit boundary remains important when a UI manifest and
one or more asynchronously decoded surfaces jointly form a visible frame.

### 4.2 Consumption gate

Before parsing another FramebufferUpdate, noVNC checks whether the prior display
queue has flushed. If not, it pauses message processing until `Display.flush()`
resolves (`noVNC/core/rfb.js`, `_framebufferUpdate()` around line 2632). In
non-continuous mode, it requests the next incremental update only after the
current one finishes (around line 2574).

This validates a one-frame consumption gate but is not full network congestion
control. `noVNC/core/websock.js` `flush()` around line 221 sends without using
`WebSocket.bufferedAmount`, and browser receive buffers may still grow.

### 4.3 Protocol-specific limits

noVNC's Tight/JPEG/ZRLE/Hextile negotiation, four persistent zlib streams, and
0-9 quality/compression pseudo-encodings are RFB-specific. They are not a
drop-in compressor for this addon's binary manifests.

Its H.264 path uses WebCodecs with `optimizeForLatency`, retains only the last
decoded frame from a payload, and explicitly closes discarded frames. See
`noVNC/core/decoders/h264.js` around lines 118-235. Those lifecycle practices
are valuable for a later WebCodecs path, but SPS/PPS, keyframes, decode config,
and frame reordering do not apply to independent JPEGs.

## 5. RustDesk

### 5.1 Frame acknowledgement and producer pacing

RustDesk's `VideoFrameController::try_wait_next()` waits for target connections
to acknowledge/fetch the prior frame, with bounded waits, then fills the
remaining frame period. Relevant sources:

- `src/server/video_service.rs`, `VideoFrameController` around lines 119-190
  and the main loop around lines 875-895.
- `src/server/video_service.rs`, `handle_one_frame()` around lines 1137-1205.
- `src/server/connection.rs` around lines 982-991 and 3498-3503. Clients that
  require explicit video acknowledgement, including the web client described
  by the nearby comments, send `VideoReceived`.

For this addon, use one in-flight surface sequence per viewer and one latest
pending sequence per surface. Unlike a strict stop-and-wait transport, capture
and input must continue while a slow viewer waits; only media state is
replaceable.

### 5.2 RTT baseline and queue-delay estimate

RustDesk periodically exchanges `TestDelay`. Its `RttCalculator` retains recent
samples and uses a longer-window minimum RTT as the path baseline. QoS then uses
recent RTT minus minimum RTT as an approximation of queuing delay:

- `src/server/connection.rs` around lines 1082-1101 and 2790-2804.
- `src/server/video_qos.rs`, `RttCalculator` around lines 57-85 and 540-594.

This is directly reusable without WebRTC. A WebSocket PING/PONG alone may be
hidden from browser JavaScript, so the addon can use a small application-level
timestamp echo on the UI channel. Combine its network RTT baseline with the
painted surface ACK to estimate browser-side decode/paint time separately.

### 5.3 Conservative adaptation

RustDesk adjusts FPS using delay bands, caps severely stalled sessions, and
uses the slowest relevant participant for a shared stream. It adjusts bitrate
only every three seconds, raises in small steps under healthy dynamic content,
and lowers more aggressively under delay:

- `src/server/video_qos.rs` around lines 246-341 for FPS.
- `src/server/video_qos.rs` around lines 358-507 for bitrate ratio.
- `src/server/video_qos.rs` around lines 510-535 for multi-user aggregation.
- `src/server/video_service.rs` around lines 1318-1353 for applying QoS.

For JPEG, bitrate is an observed result rather than a direct encoder setting.
The equivalent knobs are JPEG quality, pixel count, and capture cadence. Keep
the product's requested range at 30-120 FPS, but report requested and effective
FPS separately; backpressure can make effective FPS lower. A useful adaptation
order is:

1. Stop increasing and replace stale pending frames.
2. Lower JPEG quality within the accepted 30-95 range.
3. Step down pixel dimensions if delay remains high.
4. Let effective cadence fall toward 30 FPS under persistent pressure.
5. Recover dimensions/quality slowly, one step per healthy window.

With one shared encode for all viewers, degrading to the slowest viewer may
unnecessarily penalize fast LAN viewers. Per-viewer encoded ladders are costly.
The first implementation should expose the tradeoff and use a documented
global policy; a later two-tier encode (for example 540p and requested maximum)
is a bounded compromise.

### 5.4 Video-only encoder settings

RustDesk negotiates VP8/VP9/AV1/H.264/H.265 and hardware encoders. VPX/AOM use
real-time, one-pass CBR-style configurations, no lookahead, drop-frame/error
resilience, and hot bitrate/quantizer changes:

- `libs/scrap/src/common/codec.rs` around lines 250-312.
- `libs/scrap/src/common/vpxcodec.rs` around lines 48-264.
- `libs/scrap/src/common/aom.rs` around lines 60-151 and 279-299.
- `libs/scrap/src/common/hwcodec.rs` around lines 173-201.

These settings depend on inter-frame encoder state. JPEG has no GOP, reference
frames, CBR buffer, keyframe request, temporal error propagation, or lookahead.
Do not describe these as current JPEG optimizations.

## 6. Sunshine and Moonlight

Sunshine/Moonlight are video-streaming references rather than Canvas/JPEG
implementations. They do not provide a WebRTC-style continuously adapting
congestion controller. The client selects a session bitrate, and the encoder is
configured around that target:

- `Sunshine/src/video.cpp`, `make_avcodec_encode_session()` around lines
  1980-2181 sets bitrate/rate-control limits and an approximately one-frame VBV
  buffer.
- `moonlight-qt/app/streaming/streamingpreferences.cpp`,
  `StreamingPreferences::getDefaultBitrate()` derives a default from resolution
  and FPS; `Session` places it into `STREAM_CONFIGURATION.bitrate`.

Loss feedback primarily drives recovery and poor-network warnings, not a
continuous bitrate decrease. The addon's JPEG adaptation therefore needs its
own feedback loop.

### 6.1 Bounded decode/render queues

Moonlight uses strict queue limits and discards accumulated stale work:

- `moonlight-common-c/src/VideoDepacketizer.c`,
  `initializeVideoDepacketizer()` limits the decode queue to 15 units, and
  `reassembleFrame()` around lines 469-531 clears overflow and requests an IDR.
- `moonlight-qt/app/streaming/video/ffmpeg-renderers/pacer/pacer.cpp` uses
  `MAX_QUEUED_FRAMES = 3`. `Pacer::handleVsync()` around lines 201-259 and
  `Pacer::renderFrame()` around lines 332-391 discard old frames when the
  render queue persistently grows; `dropFrameForEnqueue()` around lines 394-400
  drops the oldest frame at the cap.

The current addon's one in-flight plus one latest replaceable pending JPEG is
more appropriate than copying Moonlight's numeric limits. Independent JPEGs do
not require preservation of a reference chain, so queue capacity one provides
lower latency.

### 6.2 Pipeline timestamps

Sunshine carries capture time through encode and records distinct pipeline
costs:

- `Sunshine/src/video.cpp`, `captureThread()` and `encode_run()` propagate the
  frame timestamp.
- `Sunshine/src/stream.cpp`, `videoBroadcastThread()` around lines 1477-1538
  records capture-to-packet, FEC, and network-send timing.
- Moonlight Qt `Pacer::renderFrame()` around lines 332-343 measures pacer queue
  age from the packet timestamp.

This supports retaining the addon's existing capture timestamp and adding
timestamps/counters for JPEG-ready, send-admitted, send-complete, browser
decode, and paint. Percentiles of frame age are more diagnostic than FPS alone.

### 6.3 UDP burst pacing is not WebSocket congestion control

`Sunshine/src/stream.cpp` `videoBroadcastThread()` around lines 1605-1768
computes a per-millisecond packet budget and sleeps within a frame to spread RTP
packets rather than burst them. TCP/WebSocket does not expose equivalent packet
pacing to the application. The useful analogue is simply to avoid admitting
multiple large writes concurrently, which `LatestSurfaceBroadcaster` already
does.

### 6.4 Video-codec-only latency and recovery

Sunshine's encoder setup disables B-frames, uses low-delay/closed-GOP flags,
limits reference frames, and combines an effectively unbounded GOP with
on-demand IDR. Encoder-specific tables add zero-latency tuning, async depth one,
and low-delay rate control. See `Sunshine/src/video.cpp`,
`make_avcodec_encode_session()` around lines 1987-2016 and the surrounding
encoder configuration tables.

Moonlight reports lost reference ranges and asks for reference-frame
invalidation or IDR recovery:

- `moonlight-common-c/src/ControlStream.c`,
  `queueFrameInvalidationTuple()`, `requestInvalidateReferenceFrames()`,
  `requestIdrFrame()`, and `referenceFrameControlFunc()` around lines 386-421
  and 1582-1620.
- `moonlight-common-c/src/VideoDepacketizer.c`, `notifyFrameLost()`.
- `Sunshine/src/video.cpp`, `encode_run()` around lines 2390-2406 consumes
  invalidation/IDR requests.

JPEG has no B-frame reorder, GOP, IDR, long-term reference, or decoder reference
state. None of these mechanisms should be added to the current JPEG wire.

### 6.5 UDP/RTP-only loss handling

Sunshine splits large video frames into FEC blocks and generates Reed-Solomon
parity in `Sunshine/src/stream.cpp` `videoBroadcastThread()` around lines
1543-1649. Moonlight reconstructs out-of-order/lost RTP data in
`moonlight-common-c/src/RtpVideoQueue.c` `reconstructFrame()`, and reports
per-frame loss/FEC state from `ControlStream.c` `lossStatsThreadFunc()`.

WebSocket/TCP is already reliable and ordered. Adding application FEC or RTP
reordering above it would waste bandwidth and worsen latency. These techniques
become relevant only with WebRTC/RTP or another UDP transport.

## 7. What can be implemented on the current wire

### 7.1 Detailed link telemetry

Expose rolling windows, totals, and current configuration rather than one
ambiguous "FPS" value:

| Group | Measurements |
| --- | --- |
| Requested | profile, max dimensions, JPEG quality, requested FPS |
| Capture | source/effective dimensions, capture FPS, readback drops, capture-to-encode wait |
| Encode | encoded FPS, JPEG bytes/frame average and p95, encode ms average and p95, RGB latest-slot replacements |
| Server media | bytes/s, frames/s, viewers, send callback latency, in-flight count, pending replacements per viewer |
| Browser receive | wire bytes/s, received FPS, sequence gaps |
| Browser decode | decoded/applied FPS, decode ms average and p95, pending-decode replacements |
| Browser paint | painted FPS, capture-to-paint age when clocks permit, animation-frame delay |
| Link | application RTT average/min/p95, estimated queue delay, reconnect count |
| UI/control | UI frames/s, UI bytes/s, chunk definitions/reuse, input ACK latency |

Use monotonic timestamps for durations. Do not claim one-way network latency
from unsynchronized Java and browser clocks. A capture timestamp can still be
used for relative frame age only after a clock-offset handshake, or reported as
server pipeline age up to send plus browser decode-to-paint duration.

The panel should distinguish:

- requested FPS: user preference;
- captured FPS: Core actually captured;
- encoded FPS: JPEGs completed;
- sent FPS: media writes completed/accepted;
- decoded FPS: browser decode completed;
- painted FPS: newest decoded surface reached Canvas.

### 7.2 Additive painted-ACK feedback

An additive control message can carry:

```text
surface handle, surface sequence, media session generation,
receive timestamp delta, decode duration, animation-frame paint duration,
number of newer frames discarded before paint
```

Validate sequence/generation and rate-limit the message. It is telemetry and
adaptation feedback, not reliable delivery: the next independent JPEG remains
a recovery point. ACK loss must never stall capture indefinitely.

### 7.3 Feedback controller

Use a small state machine rather than a formula that changes settings on every
sample:

- sample over 1-second windows;
- treat pending replacement, send callback p95, painted-ACK p95, and sequence
  gaps as congestion signals;
- step down after one or two bad windows;
- step up only after at least three healthy windows;
- change one dimension at a time and clamp to user/server bounds;
- never turn on LAN, alter auth, or change the user's persisted preference;
- report adaptive effective values separately from requested values.

Manual custom settings remain authoritative upper bounds. "Automatic" may opt
into adaptation; a fixed custom profile should remain fixed except for natural
latest-frame dropping under backpressure.

### 7.4 Unchanged-frame suppression

If Core can cheaply identify that the rendered surface did not change, do not
read back or encode another identical JPEG. Input cursors and UI commands remain
on their own channel and continue normally. If change detection requires a full
CPU pixel read solely for hashing, benchmark it before adoption.

## 8. What needs an additive surface protocol

These improvements fit Canvas but not today's full independent JPEG message:

- dirty rectangles/tiles with destination coordinates and a frame commit;
- solid-fill and copy-rectangle operations for raster surfaces;
- negotiated PNG/WebP for low-entropy or lossless regions;
- a frame-level painted ACK that atomically covers one UI manifest plus all
  referenced asynchronous surface updates;
- optional two-tier shared encodes for heterogeneous viewers.

The browser must preserve painter order and may not reveal half of a logical
frame while another required tile is still decoding. Guacamole/noVNC's ordered
render queues are the applicable model.

## 9. What needs H.264/AV1 and WebCodecs

Inter-frame video becomes worthwhile when full-surface JPEG bandwidth is the
dominant limit. It enables:

- prediction across frames and substantially lower bytes for moving 3D video;
- CBR/VBR targets instead of indirectly controlling bytes with JPEG quality;
- hardware encode/decode;
- reference-frame management, periodic keyframes, and explicit decoder reset;
- WebCodecs low-latency decoding in supporting browsers.

It also adds requirements absent from JPEG: codec capability negotiation,
extradata/SPS/PPS or decoder config, monotonically increasing timestamps,
keyframe recovery after loss/reconnect, bounded decode queues, `VideoFrame`
closure, and fallback for unsupported browsers. Sending H.264/AV1 over reliable
WebSocket still suffers TCP head-of-line blocking.

## 10. What specifically needs WebRTC/RTP

WebRTC is justified only when the project needs browser-native real-time media
transport features such as:

- UDP-based pacing and congestion control;
- RTCP receiver reports and bandwidth estimation;
- NACK/PLI/keyframe feedback, optional retransmission/FEC;
- jitter buffering and A/V synchronization;
- NAT traversal through ICE/STUN/TURN.

Neither an application-level WebSocket RTT echo nor a painted JPEG ACK requires
WebRTC. They should be implemented first because they improve observability and
adaptation without replacing the current authenticated HTTP/WebSocket service.

## 11. Recommended sequence

1. Add the requested detailed link panel and collect trustworthy counters.
2. Add rate-limited application RTT echo and painted surface ACK.
3. Count replacements/drops at every existing latest-wins boundary.
4. Add an opt-in automatic JPEG controller with hysteresis, keeping manual
   custom settings as upper bounds.
5. Add cheap unchanged-frame suppression if Core can provide a change signal.
6. Prototype dirty surface tiles plus WebP only if telemetry shows substantial
   low-entropy raster traffic.
7. Prototype WebCodecs H.264/AV1 behind capability negotiation when JPEG bytes
   remain the dominant bottleneck.
8. Consider WebRTC only when TCP head-of-line blocking, public NAT traversal,
   or real video loss recovery becomes a measured requirement.

This ordering preserves the current Canvas instruction architecture and makes
each later codec/transport decision evidence-based.
