# FRP exposure for BBS Web Remote

Research date: 2026-07-18

FRP source reviewed: official `fatedier/frp` repository at commit
`fe79598ee44b94bfb1589227a65af81490be37f3`.

## Executive conclusion

The current Web Remote build needs no separate FRP proxy for its two browser
WebSockets. The page, `/api/pair`, `/ws/ui`, `/ws/media`, static files, and
content-addressed assets all share one HTTP authority. A raw FRP TCP proxy
carries HTTP and both WebSocket upgrades byte-for-byte.

The current build has an explicit `public_origin` setting for reverse proxies
and port translation. The recommended configuration is:

- enable authenticated LAN mode in Web Remote even when `frpc` runs on the
  same machine;
- bind Web Remote to `127.0.0.1` when `frpc` is local, or to an explicit LAN
  address when `frpc` is on another machine;
- set `public_origin` to the exact browser entry origin, including `http` or
  `https` and any non-default public port;
- let FRP preserve the browser's Host and Origin headers;
- use raw TCP for the simplest HTTP port forwarding, FRP HTTP virtual hosting
  for a shared domain/port, or `https2http` for HTTPS/WSS.

`public_origin` decouples the public authority from Jetty's private listening
port. It allows raw TCP with different public/private ports, ordinary FRP HTTP
on port 80 or another vhost port, and HTTPS/WSS whose TLS terminates before the
plain-HTTP Jetty listener. An accepted HTTPS public Origin also causes the
pairing cookie to be emitted with `Secure` without trusting forwarded headers.

## Three different uses of "WebSocket"

Do not confuse these layers:

1. Browser application sockets: `/ws/ui` and `/ws/media`. The page derives
   `ws://` from an `http://` base and `wss://` from an `https://` base. They
   must remain on the same authority as the page so the browser automatically
   sends the session cookie.
2. FRP proxy type: `tcp`, `http`, or `https`. This determines how frps exposes
   and routes the application.
3. `transport.protocol = "websocket"` or `"wss"`: this only encapsulates the
   frpc-to-frps control/work transport. It does not configure either browser
   WebSocket and normally should remain the default `tcp` transport.

The official FRP repository has an end-to-end test proving that a proxy of
`type = "http"` forwards a WebSocket upgrade and bidirectional frames. Raw
`type = "tcp"` is even simpler because it does not parse the application
protocol.

## Current application constraints

### Routes and browser behavior

- Pairing: `/api/pair`.
- UI/control WebSocket: `/ws/ui`, subprotocol `bbs-ui-v1`.
- JPEG/WebP media WebSocket: `/ws/media`, subprotocol `bbs-media-v1`.
- The UI socket may negotiate RFC 7692 per-message deflate.
- The media socket explicitly disables WebSocket compression because its image
  frames are already compressed.
- Browser endpoints are root-relative. Publishing the application only under
  a prefix such as `/bbsremote/` breaks the current page unless every root
  route and asset path is also rewritten. Use a dedicated hostname/root.

### Host and Origin validation

`ServerRequestValidator` currently accepts either the direct listener origin or
one configured public origin:

- Direct Host must be in the exact `allowedHosts` set and use Jetty's bound
  port.
- Direct Origin must use `http`, an exact allowed host, and Jetty's bound port.
- A proxied Host must exactly match the configured public hostname and its
  effective public port.
- A proxied Origin must exactly match the configured public scheme, hostname,
  and effective port.
- `Forwarded`, `X-Forwarded-Host`, and `X-Forwarded-Proto` are ignored.

`public_origin` must be an HTTP(S) origin only: no path, query, fragment, user
info, or dangling port delimiter. Default ports are normalized (`http` to 80,
`https` to 443), so `https://remote.example.com` and an incoming explicit
`:443` authority match. Use the exact browser-visible non-default port when one
exists, for example `http://remote.example.com:38080`.

`allowed_hosts` remains required and nonempty in LAN mode. Its entries are only
hostname/IP literals, not URLs or `host:port` authorities. When Web Remote is
bound to loopback for a local `frpc`, `127.0.0.1` is a suitable direct
allowlist entry; `public_origin` independently authorizes the public browser
authority.

HTTP requests are Host-checked before static routing. Pairing and both
WebSocket upgrades are Origin-checked. The WebSocket upgrade also requires the
exact application subprotocol; FRP must preserve `Sec-WebSocket-Protocol`.

### Pairing, Cookie, and multiple users

LAN mode requires authentication. A six-digit pairing code is valid for two
minutes by default and is one-time: each successful redemption immediately
rotates it. Redemption creates a random bearer token stored in the
`bbs_remote_session` cookie. The token lasts 15 minutes by default, is
HttpOnly, SameSite=Strict, Path=/, and is kept only in server memory. Up to 64
sessions may coexist, so every person pairs separately using the newest code.

The browser uses `credentials: "include"` for pairing/assets, and browser
WebSocket handshakes automatically send a matching same-origin cookie. Do not
put the token into a URL, FRP config, or log.

Jetty sets the cookie's `Secure` attribute when its request is secure or when
the request Origin exactly matches a configured HTTPS `public_origin`. FRP may
therefore terminate TLS and forward plain HTTP without the application trusting
`X-Forwarded-Proto`; a forged forwarded header cannot force proxy semantics.

Pairing and WebSocket rate limits use the peer address observed by Jetty, not
`X-Forwarded-For`. Depending on the FRP path, different public users can appear
as one proxy peer and share the 10 pairing attempts/minute and 60 WebSocket
upgrades/minute limits. A reconnect storm can therefore affect other users.

## Option A: raw TCP, including a different public port

### Web Remote settings

When `frpc` runs on the Minecraft computer:

```text
LAN enabled:   true
Bind host:     127.0.0.1
Port:          27165
Allowed hosts: 127.0.0.1
Public origin: http://remote.example.com:38080
```

When `frpc` runs on another LAN computer, bind Web Remote to the Minecraft
computer's explicit LAN address (or `0.0.0.0` only when necessary), point
`localIP` below to that address, and keep that private address in
`allowed_hosts`. The public browser address belongs in `public_origin`.

### `frpc.toml`

```toml
serverAddr = "frps.example.net"
serverPort = 7000

# Use the authentication method required by the frps operator.
auth.method = "token"
auth.token = "replace-with-a-long-random-frps-token"

[[proxies]]
name = "bbs-web-remote-tcp"
type = "tcp"
localIP = "127.0.0.1"
localPort = 27165

# Public and private ports may differ because public_origin defines the public
# authority accepted by the application.
remotePort = 38080

# JPEG/WebP is already compressed and /ws/ui can negotiate WebSocket deflate.
# FRP stream compression usually wastes CPU for this mixed stream.
transport.useCompression = false
```

Open:

```text
http://remote.example.com:38080/
```

No `customDomains`, special Upgrade rule, or second media port is needed. DNS
for `remote.example.com` points to the frps public address. The public firewall
must permit TCP 38080, and frps must permit the client to allocate that port.

This example provides no browser-facing TLS. FRP's frpc-to-frps transport TLS
is a different layer and does not turn the public URL into HTTPS.

## Option B: FRP HTTP virtual host

This form lets many services share the frps HTTP port. The official FRP HTTP
proxy supports WebSocket upgrades, and `customDomains` selects this proxy by
the browser's public Host.

### Required frps setting

```toml
# frps.toml
bindPort = 7000
vhostHTTPPort = 80
```

### Web Remote settings

```text
LAN enabled:   true
Bind host:     127.0.0.1
Port:          27165
Allowed hosts: 127.0.0.1
Public origin: http://remote.example.com
```

The allowlist permits direct loopback access and satisfies LAN mode's fail-closed
requirement. The public origin authorizes the browser-facing domain and port.

### `frpc.toml`

```toml
serverAddr = "frps.example.net"
serverPort = 7000

auth.method = "token"
auth.token = "replace-with-a-long-random-frps-token"

[[proxies]]
name = "bbs-web-remote-http"
type = "http"
localIP = "127.0.0.1"
localPort = 27165
customDomains = ["remote.example.com"]
transport.useCompression = false
```

Open `http://remote.example.com/`. If `vhostHTTPPort` is not 80, include that
public port in both the browser URL and `public_origin`, for example
`http://remote.example.com:8080`.

Do not set `hostHeaderRewrite` or `requestHeaders.set.origin`: FRP should
preserve the browser's exact public Host and Origin for application validation.
Keep `customDomains` exact, protect frps registration with a strong token or
OIDC, and do not expose the local Web Remote listener separately.

## Option C: HTTPS/WSS with FRP `https2http`

Plain `type = "https"` routes by TLS SNI and normally forwards TLS bytes to a
TLS backend. Web Remote is currently plain HTTP, so use FRP's official
`https2http` client plugin to terminate browser TLS and forward HTTP locally.
The certificate and private key are read by `frpc` on the Minecraft side.

### Required frps setting

```toml
# frps.toml
bindPort = 7000
vhostHTTPSPort = 443
```

### Web Remote settings

```text
LAN enabled:   true
Bind host:     127.0.0.1
Port:          27165
Allowed hosts: 127.0.0.1
Public origin: https://remote.example.com
```

### `frpc.toml`

```toml
serverAddr = "frps.example.net"
serverPort = 7000

auth.method = "token"
auth.token = "replace-with-a-long-random-frps-token"

[[proxies]]
name = "bbs-web-remote-https"
type = "https"
customDomains = ["remote.example.com"]

[proxies.plugin]
type = "https2http"
localAddr = "127.0.0.1:27165"
crtPath = "C:/frp/certs/remote.example.com.crt"
keyPath = "C:/frp/certs/remote.example.com.key"

# Keep the public side on the classic HTTP/1.1 Upgrade path used by browser
# WebSocket implementations and this application's Jetty endpoints.
enableHTTP2 = false
```

Open `https://remote.example.com/`. The page automatically chooses
`wss://remote.example.com/ws/ui` and
`wss://remote.example.com/ws/media`; no separate WSS entry is needed.

FRP's current plugin implementation uses Go's `httputil.ReverseProxy`, which
supports HTTP Upgrade forwarding. The official repository directly tests
WebSocket over an HTTP virtual host, but does not have the same explicit
end-to-end test for this exact `https2http` plus application-subprotocol
combination. Smoke-test pairing, `bbs-ui-v1`, `bbs-media-v1`, and a token expiry
before relying on it.

Current properties and caveats:

- Host and Origin remain the real public HTTPS authority and are checked
  against `public_origin`.
- An accepted HTTPS public Origin makes the pairing cookie `Secure` even though
  the plugin's downstream connection is plain HTTP.
- public client identity/rate limiting cannot safely rely on forwarded headers.
- FRP must preserve Cookie, Set-Cookie, Upgrade, Connection, Origin, Host, and
  Sec-WebSocket-Protocol.

No header rewrite or trusted-forwarded-header mode is required for this setup.

## Legacy build compatibility workaround

Plugin builds from before `public_origin` require the old workaround for FRP
HTTP/HTTPS:

```toml
# Legacy only. Do not use with the current public_origin-capable build.
hostHeaderRewrite = "127.0.0.1:27165"
requestHeaders.set.origin = "http://127.0.0.1:27165"
```

The legacy Web Remote allowlist must contain `127.0.0.1` for that rewrite. A
legacy raw TCP proxy cannot rewrite these HTTP headers, so its public port must
equal the private Web Remote port. Legacy HTTPS pairing also emits a cookie
without `Secure`, because the rewritten Origin is HTTP and Jetty sees the
downstream plain HTTP connection. Upgrade to the `public_origin` build instead
of retaining this workaround.

## Residual proxy hardening

The explicit public origin deliberately avoids trusting forwarding headers.
The remaining proxy-specific limitation is client identity: rate limits still
use Jetty's observed peer address. Supporting real public client addresses
would require an opt-in trusted-proxy source allowlist and strict parsing of a
bounded `Forwarded`/`X-Forwarded-For` chain. Do not enable generic forwarded
header trust merely to make FRP connectivity work.

## Performance and operational notes

- Do not enable FRP compression merely to improve image streaming. Media
  images are already compressed, while the UI WebSocket can use
  permessage-deflate. Double compression costs CPU and can increase latency.
- One TCP/HTTP virtual host is enough for page, pairing, control, and media.
- Do not configure a proxy timeout shorter than the application's WebSocket
  lifetime. Jetty currently has a 30-second idle timeout, so ordinary app
  traffic/keepalive must continue to flow without buffering.
- Avoid CDN/proxy response buffering on the WebSocket routes.
- Restarting or stopping Web Remote clears pairing challenges, session tokens,
  and sockets. Users must pair again.
- Public exposure is remote input access to the running client. Keep the
  pairing code private, use controller role only where needed, restrict the
  frps port/domain, and prefer a VPN or FRP STCP visitor when access does not
  need to be generally public.

## Official FRP references

- [FRP README: expose a local web service and `customDomains`](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/README.md#expose-your-service-in-public)
- [FRP README: HTTPS to local HTTP with `https2http`](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/README.md#enable-https-for-a-local-https-service)
- [FRP README: Host rewriting and request headers](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/README.md#rewriting-the-http-host-header)
- [Official FRP HTTP WebSocket end-to-end test](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/test/e2e/v1/basic/http.go#L443-L490)
- [Official full TOML example: HTTP/HTTPS proxies and plugins](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/conf/frpc_full_example.toml)
- [FRP HTTP proxy passes configured request headers to the virtual host route](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/server/proxy/http.go#L50-L65)
- [`https2http` is implemented with Go `httputil.ReverseProxy`](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/pkg/plugin/client/https2http.go)
- [FRP transport TLS is separate from browser-facing HTTPS](https://github.com/fatedier/frp/blob/fe79598ee44b94bfb1589227a65af81490be37f3/README.md#tls)

## Local code references

- `bbs-web-remote/src/main/java/mchorse/bbs_web_remote/settings/WebRemoteSettings.java`
- `bbs-web-remote/src/client/java/mchorse/bbs_web_remote/client/server/ServerAccessConfig.java`
- `bbs-web-remote/src/client/java/mchorse/bbs_web_remote/client/server/ServerRequestValidator.java`
- `bbs-web-remote/src/client/java/mchorse/bbs_web_remote/client/server/AuthenticatedHttpHandler.java`
- `bbs-web-remote/src/client/java/mchorse/bbs_web_remote/client/server/BBSWebRemoteServer.java`
- `bbs-web-remote/src/client/java/mchorse/bbs_web_remote/client/server/PairingAuthService.java`
- `bbs-web-remote/src/client/resources/assets/bbswebremote/web/app.js`
- `bbs-web-remote/src/client/resources/assets/bbswebremote/web/protocol.js`
