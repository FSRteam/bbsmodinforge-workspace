# Transport and media research — 2026-07-15 (updated 2026-07-16)

## Decision

Use Jetty `12.1.11` for the addon's single-port HTTP/WebSocket service:

```text
org.eclipse.jetty.websocket:jetty-websocket-jetty-server:12.1.11
```

Exclude `org.slf4j:slf4j-api` and reuse Minecraft's SLF4J. For NeoGradle UserDev, follow the repository's non-Minecraft-library pattern (`libraries` configuration plus run runtime dependencies), then package transitive libraries with jar-in-jar for production.

UI/control uses `/ws/ui` with RFC 7692 per-message deflate. Already-compressed JPEG media uses `/ws/media` with WebSocket compression disabled. Do not add WebSocket LZ4/Zstd: browsers do not negotiate them natively and a JS/WASM decoder would increase bundle/CPU cost.

## Why not Minecraft's Netty

The resolved Minecraft 1.21.1 classpath contains Netty core/buffer/codec/common/handler/resolver/transport `4.1.97.Final`, but not `netty-codec-http`. Adding only matching `netty-codec-http` is small, yet that line has HTTP request-smuggling/OOM advisories. Adding only a current HTTP module would mix incompatible Netty module versions. Shading and relocating a full current Netty stack is larger and more complex than the selected Jetty core stack.

## Frame bandwidth model

- Wire protocol is independently versioned and currently `1.1`; Addon API remains exactly `2.0`. Wire `1.1` adds bounded authoritative UTF-8 text, whole-run bounds, and shadow metadata to `GLYPH_RUN` so browsers can render readable fallback text when no glyph atlas is available.
- Canonical draw commands are grouped into 4–16 KiB chunks.
- XXHash64+length finds candidates; byte equality is required before handle reuse.
- `CHUNK_DEF` sends new chunks, while every `UI_FRAME` sends the complete ordered handle list. Old frames can be dropped without breaking a delta chain.
- Resource bodies use SHA-256 and immutable HTTP caching.
- Per client: one latest pending UI frame and one latest pending frame per media surface; reliable control messages remain separately bounded.
- Media presets are `smooth` 960×540@30/Q68, `high` 1280×720@60/Q78 and
  `original` 1920×1080@120/Q90, plus a bounded custom 30–120 FPS request.
  These are addon media-demand values; browser
  wire remains `1.1` and Addon API remains exactly `2.0`.

## Media quality negotiation

The page exposes `自动` / `流畅` / `高清` / `原画`. Explicit preference is stored
in `localStorage`; storage denial or an unknown value falls back to `自动`.
Automatic mode selects a bounded tier from device/network hints: constrained
connections, phone-sized screens and low reported hardware prefer `smooth`;
only a fine-pointer high-DPI/high-memory/high-core device on an unrestricted
4G connection selects `original`; missing or ordinary hints use `high`.

The resolved tier is sent only as the `/ws/media?profile=...` query; custom also
sends literal `width`, `height`, `fps` and `quality`. Changing quality
closes/reopens the media socket without reconnecting `/ws/ui`, so input,
controller identity and UI frame state remain intact. Server parsing accepts
literal `smooth`, `high`, `original`, plus compatibility aliases
`mobile -> smooth` and `desktop -> high`; missing, repeated, encoded or unknown
values fail closed to `high`. All viewers share one encoded Core surface, so
width, height, FPS and quality aggregate component-wise within the Core bounds.
Zero media viewers returns no surface demand.

## Media capture

FSR already carries LWJGL STB 3.3.3. Use `stbi_write_jpg_to_func`, so the MVP needs no new native image encoder. Capture after GPU downscale with two fixed physical lanes: WORLD for Film/Replay/Morph logical aliases and UI for the visible form-preview union. Each lane owns a three-slot PBO/fence ring, a two-buffer CPU RGB pool, an independent generation/latest slot and one lazy long-lived bounded JPEG worker. Both lanes share the client-lifetime media sequence. The existing `VideoRecorder` double-PBO logic is useful evidence but cannot be reused whole because it still writes synchronously from the render thread.

`BBSRendering.getTexture()` is the Film panel's actual composed camera/Replay picture. General full-screen Replay has no separate framebuffer; capture the active main target at `BBSRendering.onWorldRenderEnd()` after Replay/subtitles and before HUD/UI.

## Sources

- Jetty 12.1 documentation and Java baseline: https://jetty.org/docs/jetty/12.1/index.html
- Jetty WebSocket demand/backpressure: https://jetty.org/docs/jetty/12.1/programming-guide/server/websocket.html
- Jetty 12.1.11 Maven metadata: https://repo.maven.apache.org/maven2/org/eclipse/jetty/websocket/jetty-websocket-jetty-server/maven-metadata.xml
- Jetty RFC 7692 implementation: https://github.com/jetty/jetty.project/blob/jetty-12.1.11/jetty-core/jetty-websocket/jetty-websocket-core-common/src/main/java/org/eclipse/jetty/websocket/core/internal/PerMessageDeflateExtension.java
- RFC 7692: https://www.rfc-editor.org/rfc/rfc7692
- Netty advisories: https://github.com/netty/netty/security/advisories/GHSA-38f8-5428-x5cv and https://github.com/netty/netty/security/advisories/GHSA-5jpm-x58v-624v
