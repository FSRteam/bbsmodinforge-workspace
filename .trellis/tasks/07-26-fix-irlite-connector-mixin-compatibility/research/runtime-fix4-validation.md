# Connector fix4 runtime validation

Date: 2026-07-27

## Scope

This run validates Connector transformation, mod discovery, Mixin application, BBS addon
bridging, resource startup, world entry, and clean shutdown for the fix4 artifact. It does
not validate beam-strength pixels because the fixture has no active shaderpack.

## Inputs

- NeoForge `21.1.235`
- Connector `2.0.0-beta.15+1.21.1`
- Forgified Fabric API `0.116.14+2.3.0+1.21.1`
- BBS FSR `0.0.10`
- IRLite `1.1.2-connector-fix4+mc1.21.1`
- IRL Core `1.1.3-connector-beamtex1`
- Iris `1.8.12+1.21.1-neoforge`
- Sodium `0.6.13-neoforge`
- Java `21.0.11`

The launch used the fixture's verified clean Minecraft artifact through
`-Dconnector.clean.path=.../steps/rename/outputs.jar`. The cached Minecraft assets were
reused by excluding the failed download-only task; Connector transformation and `runClient`
were not excluded.

## Results

- Connector read and transformed both the fix4 outer JAR and the nested
  `connector-beamtex1` core.
- The mapped outer JAR is 650,370 bytes, SHA-256
  `65C6ADF73113FFD03896A1201F1C58374D74CE54634B581F54FA23E893CEC348`.
- The mapped core JAR is 173,417 bytes, SHA-256
  `98D6D305ADA08B28C48565B7C6AABC90A6801BEB18EB87C4059B37C0BDA040C9`.
- The mapped core contains `BeamStrengthTexture`, `LightBuffer`, `IrlSamplers`, and the Iris
  sampler binding class. `javap` confirms the texture protocol's public lifecycle methods.
- `WorldBlockChangeMixin` applied to `ClientLevel` with the five-argument
  `(BlockPos, oldState, newState, int, CallbackInfo)` handler.
- BBS imported `qualet.irlite.IrlightsAddon`, bridged it into the internal event bus, and
  posted `RegisterSettingsEvent` to the registered subscribers.
- The client reached a responsive singleplayer world, completed the BBS network handshake,
  and created the Iris overworld pipeline.
- Closing the window produced `Stopping!`, saved all dimensions, and let Gradle finish with
  `BUILD SUCCESSFUL in 5m 47s`.
- There was no `InvalidInjectionException`, Mixin apply failure, Connector discovery failure,
  IRLite error, sampler error, or fatal error.

## Fixture-only warnings

The launch skipped a download-only task because the Mojang endpoint timed out. NeoGradle then
passed the development placeholder asset index name, causing missing sound/icon warnings. The
client still reached and exited a world normally. These warnings neither reference IRLite nor
exercise its beam texture.

## Remaining acceptance gate

`config/iris.properties` has `enableShaders=true` but an empty `shaderPack`. Final acceptance
therefore still requires a freshly generated v4 patched pack and a fixed-camera comparison of
beam strength `0`, `1`, and `10` for point lights, spotlights, and replay interpolation.

Retained logs:

- `runtime-fix4-success-debug-2026-07-27.log`
- `runtime-fix4-success-latest-2026-07-27.log`
