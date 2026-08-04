# IRLite fix3 Connector runtime validation

Date: 2026-07-27

## Result

The final `1.1.2-connector-fix3` artifact was rebuilt with a Java 21 Mixin compatibility
level, transformed from a cleared Connector cache, and reached normal client resource loading.
The prior `WorldBlockChangeMixin` descriptor crash did not recur. BBS collected and bridged the
IRLite addon, and the localization Mixin applied before the GUI atlas finished loading.

This automated launch does not replace the remaining in-game visual matrix. In particular,
`UIKeyframeSheetMixin` is transformed and packaged but is only applied when the replay editor
loads `UIKeyframeSheet`.

## Artifact identity

- Current BBS JAR: `new/build/libs/BBS_FSR_beta_v0.0.10.jar`
  - Size: 8,063,112 bytes
  - SHA-256: `00CB8A50DE67A4254DD20F2A2DE48CDE0C32A882315395A25A5B45B44AAB519D`
- BBS JAR used by the retained Connector launch:
  `temp/irlite-connector-runtime/runs/client/mods/BBS_FSR_beta_v0.0.10.jar`
  - Size: 8,051,101 bytes
  - SHA-256: `D5F53BEB54E3AEF3FE739EEF6549EDCA7F196FAEB70517727DCD31EF31ED0BAC`
- IRLite JAR:
  `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1/build/libs/irlite-1.1.2-connector-fix3+mc1.21.1.jar`
  - Size: 646,539 bytes
  - SHA-256: `D66693F5565102ABFF818F7881013C2CEE20C4EDC56B406EB377AF4A09C305F9`
- Connector mapped IRLite SHA-256:
  `B7D1DE38FD425C2E84EE01B6A1924A26B1CE32C8F49C6D3B6117C60F21DB331C`
- Clean Minecraft input SHA-256:
  `ECC41A0972FCB2234C78EE36E442CC34109C4B9D94ED0CE3D54265E8E37B988D`

The IRLite JAR contains `JAVA_21` in `irlite.client.mixins.json`, both language JSON files,
all three localization Mixins, seven shader patches, and exactly one nested
`META-INF/jars/irl-core-1.1.3.jar`.

The current BBS JAR was rebuilt after the retained launch by another active workspace task.
The two BBS artifacts are byte-identical for the Connector/IRLite and model-block surface checked
here: `BBSMod`, `L10n`, `Form`, `UIKeyframeSheet`, `InternalAssetsSourcePack`, both
`ModelBlockItemRenderer` classes, `models/item/model.json`, and `model_block.png`. The current
artifact also passed `testClientStructuralRegistration`, `compileClientJava`, and `jar`; a second
Connector launch would only exercise unrelated newly packaged BBS changes.

## Runtime evidence

Evidence file: `runtime-fix3-java21-debug-2026-07-27.log`.

- Lines 685-695 read the final fix3 source JAR and write a fresh mapped outer JAR.
- Line 4318 applies `WorldBlockChangeMixin` to `ClientLevel`.
- Line 4616 applies `FormDisplayNameMixin` to BBS `Form`.
- Lines 5743 and 5776 show BBS collecting and bridging addon id `irlite`.
- Line 5832 applies `L10nMixin` to BBS `L10n`.
- Line 6041 completes the GUI texture atlas.

Searches found no IRLite Mixin class-version warning, `InvalidInjectionException`, Mixin apply
failure, missing `irlite` translation asset, clean-artifact error, or fatal launch error. The
fixture could not open an OpenAL device, so Minecraft disabled sound and continued resource
loading; this is unrelated to IRLite or the model-block item change.

Retained logs:

- `runtime-fix3-java21-debug-2026-07-27.log` SHA-256
  `8203F2724A501DF196902EE73D61B0AE0EF8A96A385999AB7277B3A8286572F2`
- `runtime-fix3-java21-latest-2026-07-27.log` SHA-256
  `C558E2BFBDA93E00156C8B367E1EBD3ECB6720B0C34F4A95B10382D58CECE151`

## Remaining manual matrix

- Render a saved model-block item before and after entering a world.
- Open BBS and create point-light and spotlight Forms.
- Regenerate the selected shaderpack from a clean original package.
- Compare `beam_strength` values `0`, `1`, and `10`, including replay interpolation.
- Verify Chinese form panels, settings, Shader Patcher text, and replay keyframe tracks,
  including nested Form path prefixes.
- Place, break, and modify blocks and confirm shadow-cache refresh without a crash.
