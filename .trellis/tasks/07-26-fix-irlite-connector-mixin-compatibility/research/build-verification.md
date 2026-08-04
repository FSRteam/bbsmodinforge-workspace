# IRLite compatibility build verification

Date: 2026-07-27

## Result

The patched IRLite source builds successfully for Minecraft 1.21.1 with Java 21. The output
contains the upstream `WorldBlockChangeMixin` fix and one nested `irl-core 1.1.3` JAR.

This is a reproducible source-build result, not yet an exact reproduction of the failing
runtime artifact. The supplied crash log proves that artifact nested `irl-core 1.1.2`, but the
old instance path is gone and neither the workspace nor the public core history contains that
unpublished artifact.

## Inputs

| Input | Identity |
| --- | --- |
| IRLite build file | Blob-identical to `f7e08e7a^:build.gradle` |
| IRLite Mixin source | Blob-identical to `f7e08e7a:WorldBlockChangeMixin.java` |
| Fabric BBS compile JAR | `bbs-fs@6a4d2fa19643a601981f18c22a2884983a08594b`, built as 2.2.1 |
| Fabric BBS SHA-256 | `BB1DCE152CE4A6064264F37E588737A6D33ABECF736A7B04BD115AEE2D70C4FE` |
| IR core source | `3624676e80833ac8f6c8beb961adea339d261ceb` |
| IR core version | `1.1.3` |
| IR core SHA-256 | `CBF7017F5E41C55B8798B58633E8EAC42FF8244F49C24DA1791C368DBC0B062B` |

## Output

Path:
`temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1/build/libs/irlite-1.1.2-connector-fix1+mc1.21.1.jar`

- Size: 620,580 bytes
- SHA-256: `68A75F380ACECB8E98CEC6602E5F9D7EE94C64154E00AE467DA4DD42F3E8F311`
- Outer metadata version: `1.1.2-connector-fix1+mc1.21.1`
- Nested JAR count: 1
- Nested JAR: `META-INF/jars/irl-core-1.1.3.jar`

## Bytecode evidence

`WorldBlockChangeMixin.class` is Java class version 65. Its Mixin target is intermediary
`net/minecraft/class_638`, and its injection target is `method_8413`. Yarn 1.21.1 maps these
to `ClientWorld.updateListeners`; the local layered mapping maps the same method to Mojmap
`ClientLevel.sendBlockUpdated`. The handler descriptor contains two `BlockState` parameters,
one `int`, and `CallbackInfo`; it no longer has the `setBlockState` handler shape or
`CallbackInfoReturnable<Boolean>`.

## Commands and retained evidence

- Clean online build: passed.
- Offline repeat build: passed and reproduced the same SHA-256.
- Retained output: `research/build-2026-07-27.log`.

## Pending runtime gate

Do not close the task until a minimal NeoForge + Connector + Forgified Fabric API + BBS FSR +
IRLite + Iris instance regenerates the mapped JAR and passes startup, addon registration,
editor Form, shader, and block-shadow refresh checks. The core 1.1.2 to 1.1.3 difference must
remain visible when interpreting that result.
