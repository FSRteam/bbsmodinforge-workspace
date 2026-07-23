# Design

## Boundaries

This task touches three implementation areas in `new`:

- Particle rendering parity:
  - `UIParticleSchemeRenderer`
  - `ParticleFormRenderer`
  - `ParticleEmitter`
  - `ParticleComponentAppearanceBillboard`
- General UI input:
  - `UIScreen`
  - `UIBaseMenu` / `UIContext`
  - `UITextbox` / `Textbox`
- Particle curve editor:
  - `UICurve`
  - `UICurveEditor`
  - `UIParticleSchemeCurvesSection`
  - `ParticleScheme` parser variable state

## Data Flow

### Textbox Key Repeat

GLFW key event -> `UIScreen.keyPressed()` -> `UIBaseMenu.handleKey(...)` -> focused element -> `UITextbox.subKeyPressed(...)` -> `Textbox.keyPressed(...)`.

Current problem: `UIScreen` filters all `GLFW_REPEAT` before the focused textbox sees it.

Planned contract: focused textboxes receive repeat key events; non-text UI controls keep current single-press behavior unless they explicitly opt into repeat.

### Curve State

Curve editor UI -> `ParticleScheme.curves` -> `ParticleScheme.setup()` -> parser variables -> `ParticleEmitter.setupVariables()` -> `ParticleScheme.updateCurves()`.

Current problem: removing a curve mutates only `curves`; the parser variable can retain its previous value and the emitter is only partially refreshed.

Planned contract: removing or renaming a curve updates both the curve map and the parser variable state, then refreshes emitter bindings and preview runtime state.

### Curve Viewport

Graph coordinate mapping should be centralized inside `UICurve`:

- curve space x: normalized time or node index fraction
- curve space y: node value
- screen space: graph area plus pan/zoom transform

Rendering, hit testing, adding, and dragging must use the same transform to avoid drift.

### Particle Camera Facing

Editor preview uses BBS `Camera`; outside editor uses Minecraft `Camera`. Both feed `ParticleEmitter.cYaw/cPitch/cX/cY/cZ`, then `ParticleComponentAppearanceBillboard` derives billboard transforms.

Planned contract: both camera setup overloads produce equivalent yaw/pitch semantics for the same visual camera, and 3D editor preview should use the same particle render path as outside rendering.

## Compatibility

- Do not alter saved particle JSON unless required by existing Bezier chain compatibility.
- Keep non-text global UI shortcuts protected from repeat events.
- Preserve current left-click point editing unless explicitly removed for Bezier Ctrl+left-click add behavior.

## Rollback

Each child task can be reverted independently:

- Text repeat: revert `UIScreen` / focused-textbox repeat routing.
- Curve viewport: revert `UICurve` coordinate transform changes.
- Curve removal: revert parser variable cleanup/rebuild changes.
- Camera parity: revert camera setup/render path changes.
