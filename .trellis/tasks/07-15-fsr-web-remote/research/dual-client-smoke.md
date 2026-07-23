# Dual Minecraft / NeoForge collaboration smoke

Date: 2026-07-15/16 (Asia/Shanghai)

## Verdict

**Not fully accepted yet.** A real NeoForge dedicated server and two distinct
client processes were launched, but the clients did not both reach the world
before the root agent requested that all instances be stopped for the serial
quality gate. Therefore `create/join` and the live Film collaboration matrix
remain manual acceptance items.

The run did prove the following runtime boundaries:

- NeoForge 21.1.222 can load FSR 2.3.1 and BBS Web Remote 0.1.0 together on a
  dedicated server.
- The server registered the API 2.0 addon and the `bbswebremote:collab_c2s`
  broker on frozen BBS C2S payload `s15`.
- Client A loaded both mods, registered both collaboration directions, started
  the client bridge, and bound the loopback web service.
- Client B started with a separate offline UUID and reached valid mod discovery
  and `forgeclientdev` launch before shutdown.
- All test processes were terminated afterward; port 25565 was closed and no
  smoke `BootstrapLauncher` process remained.

## Runtime configuration audit

Both `new/` and `bbs-web-remote/` expose generated `runServer` and `runClient`
JavaExec tasks. The addon tasks originally use fixed `runs/server` and
`runs/client` directories. `runClient --args` is available, but replacing all
generated arguments is brittle, so the isolated init script
`research/smoke-runs.init.gradle` instead adds:

- a unique game directory per instance;
- a unique offline username and UUID;
- `--quickPlayMultiplayer 127.0.0.1:25565`;
- a bounded heap;
- a distinct `bbs.webRemote.port` per local client.

The disposable runtime is under `research/dual-client-runtime/`:

- server: `127.0.0.1:25565`, offline mode, creative flat world;
- client A: `SmokeA`, UUID
  `90e9227f-8f30-3f4d-8c2f-e46583b71766`, web port 27165;
- client B: `SmokeB`, UUID
  `2f39b3cd-4582-3dba-aa91-b9bc57fa925b`, web port 27166;
- both identities are level-4 operators in the disposable `ops.json`, because
  collaboration USE/CREATE defaults require permission level 2.

No Mojang account, destructive account action, public listener, or external
server was used. The server was bound only to loopback.

### Important same-host detail

Every client defaults to web port 27165. Two local clients must use different
`-Dbbs.webRemote.port` values. Otherwise the second client's HTTP service logs
a bind failure. Collaboration initialization happens before the HTTP bind, but
the port collision would invalidate web-remote acceptance and obscure logs.

## Actual executions and evidence

### 1. Runtime manifest failure found

The first real `runServer` exposed a build/test gap. NeoForge rejected:

```text
[[features.bbswebremote]]
```

with:

```text
The configuration path [features, bbswebremote] is invalid.
I wasn't expecting a multi-object list - remove one of the [[ ]].
```

Evidence:

- `research/dual-client-runtime/server-gradle.out.log`
- `research/dual-client-runtime/server/crash-reports/crash-2026-07-15_23.51.51-fml.txt`

The runtime-manifest agent fixed production source during this audit to:

```text
[features.${mod_id}]
```

and `processResources` now expands it to `[features.bbswebremote]`. This is why
an actual NeoForge launch must remain in the release gate; `check build` alone
did not parse this descriptor.

### 2. Dedicated server reached ready state

To continue diagnosis before the production fix landed, a test-only thin addon
JAR with only that manifest line corrected was used. Scratch JARs were removed
afterward. The server log records:

```text
BBS FS mod 2.3.1 (bbs)
BBS Web Remote 0.1.0 (bbswebremote)
accepted API 2.0 addon 'bbswebremote'
net.addon_broker ... direction=c2s ... id=bbswebremote:collab_c2s
Starting Minecraft server on 127.0.0.1:25565
Done (...)! For help, type "help"
```

Evidence: `research/dual-client-runtime/server/logs/latest.log`.

UserDev already puts Jetty libraries on its run classpath. Loading the
production `-all.jar` inside that same UserDev run creates duplicate JPMS Jetty
modules; the diagnostic run therefore used the thin JAR. This duplication is a
UserDev harness concern, not the normal production installation layout where
the `-all.jar` supplies Jetty through Jar-in-Jar.

### 3. Client A reached initialized state

Client A was launched with explicit user, UUID, game directory, heap, and
quick-play server arguments. Its log records:

```text
Setting user: SmokeA
BBS Web Remote 0.1.0 (bbswebremote)
accepted API 2.0 addon 'bbswebremote'
net.addon_broker ... direction=s2c ... id=bbswebremote:collab_s2c
net.addon_broker ... direction=c2s ... id=bbswebremote:collab_c2s
BBS Web Remote client bridge initialized (wire 1.1)
BBS Web Remote listening on http://127.0.0.1:27165
```

Evidence: `research/dual-client-runtime/client-a/logs/latest.log`.

Client A had not yet emitted a multiplayer login line when shutdown was
requested, so this is client initialization evidence, not create/join evidence.

### 4. Client B reached launch state

Client B used its own directory, name, and UUID. Its log records a valid mod
list containing FSR and BBS Web Remote and launch of `forgeclientdev` as
`SmokeB`. It was still in bootstrap/data-fixer work when stopped.

Evidence: `research/dual-client-runtime/client-b/logs/latest.log`.

The server log contains no `SmokeA`/`SmokeB` login and no collaboration command
result. Consequently no live Film assertion is marked passed.

## Automated evidence versus remaining real-client checks

The configured protocol/core test mains had prior green evidence in
`implement.md`; they were inspected here but not rerun while the root quality
gate was serializing builds.

| Scenario | Deterministic coverage already present | Real-client status |
|---|---|---|
| Create/join/readiness | `CollaborationProtocolTestMain.testReadinessAndRejoinState`; client pending-Film and `CLIENT_READY` tests | Pending |
| Ordinary semantic edit / no echo | client revision-separation, own-broadcast and remote-apply test; core local coalescing test | Pending visual/UI hook check |
| Undo/Redo | `UIFilmUndoHandler` enters the same committed-value/path capture path | Pending; no direct two-client runtime assertion |
| Same-path revision conflict | `testRevisionAndClientSequenceCAS` | Pending live last-server-sequence check |
| Oversized checkpoint | protocol large-batch checkpoint test and client authoritative-checkpoint test | Pending live structural edit |
| Join during continuous edits | protocol snapshot/delta state machine and client queued-delta catch-up tests | Pending live reconnect test |
| Film close / disconnect / presence clear | client closing-tail, terminal-state, replacement-Film and disconnect-race tests; core presence watermark tests | Pending live overlay/cleanup check |

These harnesses validate codec and state-machine contracts but cannot prove
Minecraft event-bus registration, native UI mutation capture, actual screen
lifecycle, visual presence, or a real client/server packet path. They do not
replace the short manual matrix below.

## Shortest serial rerun

Run only after both final builds pass and no other Gradle process is rewriting
`new/build/libs` or addon outputs.

1. Start the isolated server from `bbs-web-remote/`:

   ```powershell
   .\gradlew.bat runServer --no-daemon `
     -I ..\.trellis\tasks\07-15-fsr-web-remote\research\smoke-runs.init.gradle `
     -PsmokeDir=..\.trellis\tasks\07-15-fsr-web-remote\research\dual-client-runtime\server `
     -PsmokeHeap=2g
   ```

2. After the server prints `Done`, start client A:

   ```powershell
   .\gradlew.bat runClient --no-daemon `
     -I ..\.trellis\tasks\07-15-fsr-web-remote\research\smoke-runs.init.gradle `
     -PsmokeDir=..\.trellis\tasks\07-15-fsr-web-remote\research\dual-client-runtime\client-a `
     -PsmokeHeap=1500m -PsmokeName=SmokeA `
     -PsmokeUuid=90e9227f-8f30-3f4d-8c2f-e46583b71766 `
     -PsmokeConnect=127.0.0.1:25565 -PsmokeWebPort=27165
   ```

3. After A reaches the world, start client B with the same command but use
   `client-b`, `SmokeB`, UUID
   `2f39b3cd-4582-3dba-aa91-b9bc57fa925b`, and web port `27166`.

Starting the three instances serially avoids NeoForm/build-output races and
makes failure attribution clear.

## Shortest manual collaboration matrix

1. In both clients press `0`, open the Film panel, and open/create the same
   disposable Film. Keep both Film panels active.
2. In A run `/bbswebremote collab create fsr-smoke`; in B run
   `/bbswebremote collab join fsr-smoke`; run `... collab status` in both and
   confirm both are ready at the same revision.
3. A changes one small scalar Film/Replay property. Confirm B changes once and
   A does not receive an echo edit. Then B changes another property and confirm
   the inverse direction.
4. In A use Ctrl+Z and Ctrl+Y on that change. Confirm B follows both Undo and
   Redo.
5. Change the same scalar on both clients in quick succession. Confirm both
   converge to the value with the later server sequence and neither leaves the
   session.
6. Make a structural Film edit large enough to exceed one normal broker body
   (for example a replay/list subtree with many keyframes). Confirm the
   checkpoint completes and both Films remain byte/visually equivalent.
7. Disconnect B. Continue editing A, reconnect B and join while A keeps making
   small edits. Confirm B applies the join snapshot and every subsequent delta
   before reporting ready.
8. Move the remote timeline cursor/selection, then close B's Film and finally
   disconnect B. Confirm A's remote presence disappears immediately in both
   cases and no held/ghost state remains.

Record each command result, both client logs, the server log, and screenshots
of the final equal Film state. Shut down clients and use the server's `stop`
command before running another build.

## Explicit blockers at handoff

- The root agent paused instances until manifest/surface/input fixes and the
  full quality gate are stable. This prevented the two clients from both
  entering the world in this run.
- Real UI actions cannot be inferred from protocol tests; create/join,
  Undo/Redo, visible presence and Film equality still require the matrix above.
- Concurrent Gradle work caused a real NeoForm output deletion race in one
  attempted `new/runServer`; all final instance launches must be serialized
  after builds finish.
- The startup artifact used for successful runtime proof preceded the final
  boundary fixes. Rebuild before the serial rerun; do not treat this partial
  launch as final release acceptance.
