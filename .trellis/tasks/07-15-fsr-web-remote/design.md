# FSR 网页预览与远程控制：技术设计

## 1. Boundaries

系统分为两个可独立发布的边界：

1. `new/`（FSR core）拥有 UI/渲染与输入真源，只新增通用 client-only UI Mirror API。
2. `bbs-web-remote/`（独立 addon）拥有网络服务、鉴权、差量编码、资源缓存、surface 编码和浏览器应用。

核心 API 不认识 HTTP、WebSocket、浏览器或插件配置；插件不直接持有 FSR 内部 UI 元素、manager 或 GL id。

## 2. End-to-end flow

```text
UIScreen frame
  -> Batcher2D terminal primitives + explicit raster surfaces
  -> immutable BBS UI frame
  -> bounded latest-frame queue
  -> resource interning + block delta + binary encoding
  -> WebSocket per-message compression
  -> browser frame store
  -> Canvas2D/WebGL composition

browser pointer/key/touch
  -> validated binary input + RemoteInputState
  -> bounded input queue
  -> Minecraft client thread
  -> current UIScreen/UIBaseMenu native input path
```

## 3. FSR client API

Public types live under a client-only package such as `mchorse.bbs_mod.api.client.ui`:

- `BBSUiMirrorSink`: `onOpen`, `onFrame`, `onClose` callbacks.
- `BBSUiMirrorSubscription extends AutoCloseable`: subscription ownership, control lease request/release, input submission and asset resolution.
- `BBSUiSession`: opaque id, logical width/height, GUI scale and screen kind.
- `BBSUiFrame`: protocol major/minor, session id, frame sequence, dimensions, timestamp and immutable ordered commands.
- `BBSUiDrawCommand`: sealed command variants for clip, quad, texture, glyph/text, mesh, surface and cursor.
- `BBSUiAssetRef` / `BBSUiAsset`: opaque content-addressed resources; never raw paths or GL ids.
- `BBSUiInputEvent` / `BBSUiInputAck`: pointer, wheel, key, text and held-state contracts.

`BBSClientApi` exposes descriptor-scoped subscription. Registration checks `CLIENT_UI`; raster surface access checks `CLIENT_RENDER`. Existing API version constants and compatibility facades remain unchanged.

Internally, a bridge owns subscriptions, session ids, recorder state, control lease and client-thread input queue. `UIScreen` begins/ends sessions and frames. `Batcher2D` records only terminal primitives to avoid duplicate commands from helpers. Direct GL/`GuiGraphics` paths register bounded raster regions rather than leaking rendering internals.

## 4. Frame and resource model

The frame is painter-ordered and expressed in FSR logical coordinates. Browser viewport scaling never changes the FSR layout; pointer coordinates are transformed back through the inverse canvas transform.

Static texture/font assets use an opaque id plus content hash and generation. The transport sends an asset only when the browser lacks that hash. Dynamic Film/Replay content is a `surfaceId` with independent sequence, dimensions and encoded image payload.

Text layout remains authoritative in Java. A text/glyph command carries positions/advances; browser `measureText()` is not used to determine layout. A session glyph atlas or raster fallback provides visual parity for Minecraft and CJK glyphs.

## 5. Low-bandwidth wire protocol

The WebSocket subprotocol identifies a protocol major/minor. Every binary message starts with kind, version, session id and sequence. Payload lengths and collection counts are bounded before allocation.

UI frame encoding:

- Serialize commands into deterministic 4–16 KiB chunks at stable clip/flush boundaries.
- Compute XXHash64 plus length per chunk, and compare bytes before reusing a handle so hash collisions cannot corrupt a frame.
- `CHUNK_DEF` sends only new/changed chunks; every `UI_FRAME` carries the complete ordered handle manifest. This makes intermediate UI frames freely droppable without a fragile delta chain.
- Browser requests missing handles explicitly; the server responds with a strong keyframe containing all active definitions.
- Resize, session/resource epoch change, reconnect and periodic recovery emit a keyframe.
- Per-message deflate is negotiated at WebSocket level when supported.

Resource ids and repeated strings/colors/transforms are interned. Coordinates and indexes use bounded varints or fixed-point values where lossless for UI coordinates. The first implementation may use fixed-width fields behind the same codec interface, then optimize without changing the semantic contract.

Backpressure is latest-wins for UI/surface frames: one queued/in-flight frame per client, replacing stale unsent frames. Reliable input transitions and control messages use a separate bounded queue; pointer moves and held-state snapshots may be coalesced.

Surface stream uses GPU downscale, a three-slot PBO/fence ring on the render thread, two pooled CPU RGB buffers, a single background LWJGL STB JPEG encoder per fixed lane, and capacity-one latest-frame queues. UI commands continue even if a surface frame is skipped. One encoded JPEG is shared across WS viewers. Presets are bounded at 540p@30/Q68, 720p@60/Q78 and 1080p@120/Q90; a `custom` media handshake may request bounded width, height, JPEG quality and 30–120 FPS. The server validates literal query fields before allocation and aggregates active viewers component-wise without exceeding Core's limits.

WebRTC v1 adds a preferred media plane without replacing the UI wire. HTTPS/WSS
continues to own page delivery, pairing, authorization, reliable UI/input and
SDP/ICE signaling. Each authenticated UI peer may own one bounded libwebrtc
PeerConnection. Every active physical capture lane receives one VideoTrack;
reliable `mid`, binding epoch and physical handle messages map those tracks back
to existing `SURFACE_QUAD` commands. The initial bridge decodes Core's already
published JPEG once per lane into I420 and pushes it to a shared
`CustomVideoSource`; it is capped conservatively at 720p30/60. A later additive
Core raw-frame sink will branch before STB JPEG and remove this transitional
decode/re-encode cost without changing signaling or browser contracts.

Browser negotiation starts while WS/JPEG remains visible. Only the first bound,
drawable WebRTC frame may retire the JPEG transport; ICE failure, a three-second
disconnected state or native WebRTC unavailability reopens JPEG exactly once.
Manual `WS_ONLY` disables negotiation, `RELAY_ONLY` sets the ICE policy to relay,
and AUTO tries host/srflx before TURN. DataChannel v1 is unordered/zero-retry and
may carry pointer/presence/held snapshots only; reliable transitions always stay
on WebSocket and pass through the existing co-control authorization/lease owner.

External coturn uses short-lived REST credentials generated only for an
authenticated browser. The long-term HMAC secret remains host-side and is never
serialized into static resources, WELCOME, normal logs or status text. FRP may
proxy HTTPS/WSS signaling but is not a substitute for ICE or TURN relay ports.

## 6. Server, authentication and roles

The addon runs one embedded HTTP/WebSocket port using Jetty 12.1.11's core server/WebSocket stack. Minecraft's bundled Netty 4.1.97 omits `netty-codec-http`; adding that old HTTP module exposes known request-smuggling/OOM advisories, while mixing only a new HTTP module with the old Netty core is not a supported version set. Jetty adds roughly 2.5 MiB, runs on Java 21, provides RFC 7692 per-message deflate and explicit demand/backpressure. Static web assets are served from the addon jar.

`/ws/ui` carries UI/control binary messages and negotiates per-message deflate. `/ws/media` carries already-compressed JPEG frames with WebSocket compression disabled so media cannot waste CPU or head-of-line block UI control. Static resources use content-addressed URLs, ETag and immutable caching; build-time Brotli/gzip files may be served from the jar according to `Accept-Encoding`.

Default binding is `127.0.0.1`/`::1`. LAN mode is an explicit config switch. A short-lived one-time pairing code exchanges for a random bearer session token. WebSocket upgrade validates Host/Origin, token, protocol and rate limits. Tokens are stored in memory for MVP and never logged.

Multiple `VIEWER` sessions may subscribe. Only one `CONTROLLER` lease exists. Session/screen changes revoke the lease and synthesize releases for all remotely held keys/buttons to prevent stuck input.

The controller model is extended for co-control without pretending that one native `UIScreen` has multiple independent focus states. Multiple authenticated browser participants may hold controller permission, but a server-owned gesture lease serializes mouse drag, wheel bursts, and text focus. Presence/cursors remain concurrent. Held state is tracked per participant and release-all is scoped to that participant before ownership is discarded.

## 6.1 NeoForge collaboration layer

True simultaneous editing uses separate Minecraft clients, each with its own native BBS UI. A common/server portion of the addon coordinates versioned collaboration payloads independently from the browser wire protocol.

The common portion reuses API 2.0's frozen addon broker (`bbs:s15` C2S and `bbs:c18` S2C) with only two plugin message ids, `bbswebremote:collab_c2s` and `bbswebremote:collab_s2c`. The broker body owns collaboration protocol versioning; BBS frozen payload ids and API version `2.0` remain unchanged. C2S bodies remain below 28 KiB, so large structural commits use the checkpoint flow below.

```text
local Film mutation
  -> normalized path/value operation + client operation id
  -> NeoForge payload
  -> server permission/session validation
  -> server monotonic sequence and path conflict ordering
  -> broadcast to other session members
  -> client-thread apply with nested echo suppression
  -> targeted UI refresh/presence update
```

Presence packets carry editor type, Film/Replay identity, playhead, semantic cursor coordinates and selection. They are coalesced and rate-limited; reliable mutation transitions are not silently dropped. Join requests receive a server-approved snapshot source or stored snapshot, followed by a sequence watermark. Gaps request resynchronization.

FSR's Film revision and the server collaboration revision are deliberately separate. The FSR revision protects one client's current Film instance from stale asynchronous apply. The collaboration revision orders participants' commits. The client bridge maps between them and never assumes they have equal numeric values.

One normal collaboration mutation message contains the complete atomic FSR mutation batch: `baseCollaborationRevision`, local operation id, and bounded repeated `{kind, path segments, refresh hint, value}` items. The server performs one compare-and-set and advances its revision once for the whole batch.

When a stable parent subtree cannot fit the C2S broker limit, the client requests a checkpoint at its current collaboration revision. The server authorizes one transfer, requests a full Film snapshot from that member, and commits only after all chunks arrive while the watermark is still current. Snapshot bodies use bounded JDK GZIP when beneficial, carry declared uncompressed length and SHA-256, and are shared for join/resync; already-compressed JPEG media never enters this path.

Collaboration never auto-joins. Operators explicitly use `bbswebremote collab create|join|leave|status` or the equivalent versioned client request. The default permission policy requires server permission level 2 for both use and creation; NeoForge permission providers may override it.

This is a clean protocol informed by Together's behavior, not a copy of its Fabric packet ids, mixins or decompiled encodings. Server state owns membership, authorization and sequencing. Disconnect has an explicit hook; it does not rely on lazy pruning.

## 7. Browser renderer and mobile controls

The web app uses a Canvas2D renderer for clips, quads, images and glyph atlas draws; WebGL2 is optional for mesh-heavy paths. It maintains a versioned frame store and asset cache. A surface layer is drawn at the ordered command position. Media preferences include automatic/preset tiers plus a persistent custom width, height, JPEG quality and 30–120 FPS form. Only the `/ws/media` URL changes when these values change; binary wire `1.1`, `/ws/ui`, participant identity and held input remain untouched.

The expandable connection-details projection is browser-local and does not
change wire `1.1`. It samples UI and media application payload bytes separately,
uses bounded one-second receive-FPS windows, and exposes only a sanitized URL
origin. UI bitrate is post-WebSocket-inflate application data; media bitrate is
JPEG application data. Neither is labeled as TCP/TLS wire throughput.

Browser media telemetry retains only bounded numeric samples. It distinguishes
receive, decode, renderer application and a Canvas draw that actually consumed a
`SURFACE_QUAD`. Additive JSON `LINK_PING`/`LINK_PONG` exchanges establish a
four-timestamp monotonic clock mapping; `PAINT_ACK` is emitted only after that
Canvas draw and identifies the exact published surface tuple. The server accepts
feedback from viewers as well as controllers, validates it against a bounded
five-second publication ledger, and returns `LINK_STATS` at no more than 1 Hz.
No JPEG/image references are retained by diagnostics.

`LINK_STATS v:1` may contain additive `delivery.ui` for the current UI socket and
`delivery.mediaAggregate` for server-wide media fan-out. The latter cannot be
treated as the current viewer because UI and media sockets have no unambiguous
cross-socket identity. Replaced/evicted values mean bounded server latest-wins
coalescing; send completion means only that the asynchronous send completed.

Automatic quality is a browser-local three-tier state machine. It evaluates
non-overlapping three-second numeric windows after warm-up, drops quickly under
decode/apply/paint pressure, waits 30 seconds of health before recovery, and
enforces a 15-second change cooldown. Manual modes disable it. Page visibility,
active painted surface, media connectivity and intentional reconnect state gate
all decisions. Delivery ratios are relative to frames actually received, so a
120 FPS request does not classify a healthy render-limited 60 FPS source as loss.

Desktop maps mouse, wheel, keyboard and IME. A printable keydown on the focused
canvas emits both the physical key transition and a bounded TEXT event because
FSR textboxes consume `BBSUiTextEvent`, not printable GLFW keys. Ctrl/Alt/Meta
shortcuts do not synthesize text. Mobile uses landscape-first canvas fit, pinch
zoom/pan, single-finger pointer drag, configurable right/middle-click gestures,
virtual mouse buttons and modifier toggles. The hidden input remains the sole
source of committed Unicode composition/IME text so it cannot duplicate canvas
text events.

## 8. Native FSR settings integration

The addon declares `SETTINGS` and `SOURCE_PACKS`, registers an independent
`bbswebremote` module through `BBSSettingsRegistry`, and supplies localized
strings through an `assets` source pack. Persisted values own automatic startup,
port, LAN mode, bind host and the exact Host allowlist. LAN validation remains
fail closed.

FSR has no public addon facade for dynamic settings buttons, so the client uses
one narrow `UIValueMap` marker-value adapter to inject live status plus Apply,
Stop and Pair actions into that module. Actions submit to the existing lifecycle
executor; UI/render threads never wait on Jetty startup or shutdown. Editing a
value alone does not alter network exposure. Apply starts a primed replacement,
disconnects the old browser sessions only after success, and requires new LAN
pairing sessions.

An optional persisted `public_origin` explicitly authorizes one browser-visible
HTTP(S) origin for FRP/reverse-proxy port translation. Direct LAN Host validation
continues to use `allowed_hosts` and Jetty's port. Public Host and Origin match
the configured scheme/host/effective port exactly; forwarding headers are never
trusted. A matching HTTPS public Origin sets the pairing cookie `Secure` even
when FRP terminates TLS before the plain-HTTP Jetty listener.

## 9. Threading and lifecycle

- Capture and GL/PBO operations occur only on the Minecraft render/client thread.
- Mirror callbacks perform bounded immutable handoff only; hashing, image encoding and network writes run off-thread.
- Network threads never call BBS UI directly; input is validated then scheduled on the client thread.
- Subscription close, screen close, disconnect, world unload and client stop converge on idempotent teardown.
- No subscriber means recorder and surface capture are disabled.
- Runtime Web transport stop/restart is distinct from final client shutdown.
  Transport stop disconnects authenticated browser peers and releases their
  input while retaining the current native UI session, mirror subscription and
  coordinators. Final client shutdown additionally closes those retained owners.
- Port replacement validates the existing loopback/LAN policy, starts and
  primes a candidate server before swapping it into service, and leaves the old
  server untouched if candidate bind/start fails. Current-session immutable UI
  asset bytes remain bounded and replayable until the native UI session resets.

## 10. Compatibility and rollout

- API 2.0 version remains unchanged; all new interfaces are additive, opt-in and client-only.
- Existing `CLIENT_UI` and `CLIENT_RENDER` capability enum values are reused.
- Dedicated servers do not load mirror classes.
- Core and plugin changes are kept in separate commits/rollback units.
- Implementation pins the current `new` state after FS migration; internal capture hooks are isolated so future FS sync conflicts stay localized.

## 11. Risks and mitigations

- Raw rendering paths missing from `Batcher2D`: use explicit raster surface fallback and coverage diagnostics.
- GPU stalls: PBO ring, independent rate cap, drop surface frames rather than block render.
- Font mismatch: authoritative glyph positions plus atlas/raster fallback.
- Slow/malicious clients: strict bounds, rate limits, latest-wins queues and loopback default.
- Local/remote input conflict: single control lease, held-state snapshots and synthetic release on teardown.
- FS sync drift: narrow hooks at `UIScreen`/`Batcher2D`, no deep UI tree exposure.
- Shared-host co-control conflicts: serialize gestures/text focus while allowing concurrent presence.
- Semantic collaboration conflicts: server sequence provides deterministic last-write-wins per normalized path; full snapshot remains a recovery mechanism, not the normal edit path.
- Decompiled Together fragility: use only observed behavior as research evidence; do not copy packet formats, mixin overwrites or obfuscated access paths.
