# S8 Manual Acceptance Queue

These checks require representative Film projects, visual comparison, multiple
preview/export sizes, or an addon-disabled launch. S8 automation and strict-client
startup do not claim them as passed.

- Hotbar HUD pixel review at multiple GUI scales: slots, selected slot, offhand,
  hearts/absorption/hardcore, armor, food, air and experience.
- Hotbar layout review for non-default position, alpha and scale, including clipping
  near each viewport edge.
- Multiple overlapping Hotbar clips: stable `context.count` draw order and no stale
  state after seeking, stopping, replay switching or Film close.
- Multiple overlapping Color and Cinematic clips: queue order, envelopes, combined
  overlay/grade/cinematic output and no one-frame residue after seeking or stopping.
- Film preview, normal playback and recording/export at both window-sized and custom
  dimensions; verify the same `ClipContext` owns each visible effect.
- Save/reload representative legacy Hotbar Films and compare migrated layout/hardcore
  behavior with the source implementation.
- Addon-disabled BBS FSR baseline: ordinary Films, HUD, color grading and cinematic
  rendering remain unchanged and the three addon clip types are absent.
