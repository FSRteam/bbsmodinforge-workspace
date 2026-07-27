# S6 implementation plan

## Implementation

- [x] Port the mini-window framework classes, rename all addon packages/member prefixes, and
  add `MiniWindowUIKeys` plus English/Chinese labels.
- [x] Port `UIBaseMenuFloatLayerMixin` and `UIOverlayPanelResizeMixin`; preserve modal ordering and
  ordinary overlay event behavior.
- [x] Port `UIDockLayoutMiniWindowMixin` and `UIDashboardPanelMiniWindowMixin` against current FSR
  descriptors and existing S2 dock accessors.
- [x] Port `UIFilmPanelMiniWindowMixin`, including independent camera/replay windows, preview input
  forwarding, shared-main ordering, and inspector-parent ownership.
- [x] Register exactly five S6 Mixins in `bbs_snow.client.mixins.json`.
- [x] Extend `BBSSnowRuntimeVerification` with S6 target loading, Mixin-config verification,
  deterministic window/manager lifecycle checks, float-layer ordering, overlay handles, and
  ordinary-layout isolation.

## Validation

- [x] Run `./gradlew verifyBbsCoreJar --console=plain` and `./gradlew build --console=plain` with
  real exit codes.
- [x] Inspect the jar for all S6 classes/resources and no `mchorse/bbs_mod/**` entries.
- [x] Scan for Fabric, old `bbs_fslovecml`, refmap, and unexpected `require = 0` drift.
- [x] Run strict client verification; require the S6 pass, five S6 Mixins, transformed constructor
  checks, lifecycle assertions, and automatic exit 0.
- [ ] S10 manual: detach/restore generic dock and Film panels, drag/resize/merge/collapse/focus,
  preview click/scroll/release orbit, and inspect controls after selection changes.
- [x] Restore any temporary run configuration override, then run `git diff --check` and source
  boundary checks.

## Validation record (2026-07-27)

- `gradlew verifyBbsCoreJar build --console=plain`: exit 0 after the final accessor, logging, and
  verifier changes.
- Strict `runClient`: exit 0; 87 Mixins prepared; S6 verifier passed with `targets=42`,
  `s5Mixins=6`, and `s6Mixins=5`.
- The strict verifier now drives merge-target highlighting, two-window tab merge, merged redock,
  east-handle resize to `UIResizeHandles.MIN_W`, preview click/scroll/release delivery exactly once,
  and ordinary dock isolation.
- Packaged jar: 166 entries; all 15 audited S6 classes/resources present; 0 forbidden BBS classes,
  Fabric metadata, old namespace, or refmap entries.
- `git diff --check` and the source forbidden-pattern scan pass. `earlyWindowControl = true` remains
  restored. The known missing `config/bbs/settings/categories.json` startup trace is BBS runtime
  noise and does not prevent the verifier or client from completing.
- Manual report: client launch and most settings/features are visible; concrete interaction
  usability is unknown and remains an explicit S10 gate.
- Spec review: no update required. Existing external-addon integration rules already cover the
  reusable strict Mixin/accessor, runtime, and artifact-isolation contracts; S6 behavior stays in
  this task's PRD/design.

## Risk and rollback points

- `UIFilmPanelMiniWindowMixin` is the highest-risk path because it coordinates shared containers,
  selected editor state, inspector ownership, orbit input, layout persistence, and render order.
- A full-size generic dock mini-layer would invisibly block the dock; keep it zero-size and rely on
  non-culled child rendering.
- Floating panels must be hidden from layout rebuild iteration only temporarily. Failure to restore
  the registration map makes panels impossible to redock; failure to stash makes rebuild reclaim
  them from their windows.
- Preview release forwarding is mandatory even when a child consumes release, or middle-button pan
  can remain active and steal later buttons.
- Do not revive the source audio-drop implementation under S6; it predates current FSR's validated
  publication and failure semantics.
- Stage only S6 addon files and S6 Trellis artifacts; leave parallel `new/`, source-tree, and root
  work untouched.
