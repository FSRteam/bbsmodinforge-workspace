# Port BBS Structure Addon implementation plan

## Checklist

1. Preparation
   - Read Trellis specs for `new/backend`, `frontend`, and shared guides.
   - Confirm `new` status with explicit `safe.directory`.
   - Locate existing form model, registry, renderer, editor, icon, and localization patterns.

2. Source/reference analysis
   - Use `old` only for Fabric-to-NeoForge mapping clues.
   - Use public CurseForge/Modrinth pages for behavior and release/license facts.
   - If the user later explicitly approves external source retrieval, inspect it under `docs/temp` without modifying it.

3. Form model and registration
   - Add `StructureForm` with a serialized structure id value.
   - Register it in the same form registry/category path as existing forms.
   - Choose an id that matches the expected addon behavior and avoids collisions.

4. Editor UI
   - Add a Structure form editor panel.
   - Provide a text field for `namespace:path`.
   - Reuse existing UI labels, row layout, dirty tracking, and undo patterns.

5. Renderer
   - Add Structure form renderer.
   - Resolve structure id through 1.21.1 `StructureTemplateManager`.
   - Render block states with existing Minecraft/BBS render helpers.
   - Cache resolved templates or render data carefully and invalidate on id change.
   - Skip gracefully on empty/missing structures.

6. Localization/resources
   - Add English and Chinese labels at minimum, following existing string files.
   - Reuse `Icons.STRUCTURE` if available.

7. Validation
   - Run focused compile if available, then `.\gradlew.bat build` from `new/`.
   - Inspect `git -c safe.directory=... -C new diff`.
   - Confirm no unrelated `old/` changes.

8. Finish
   - Update specs if a reusable migration convention is learned.
   - Ask the user before committing.
   - Commit only `new/` content after confirmation.

## Validation Commands

```powershell
git -c safe.directory=D:/bbsinneoforge/bbsmodinforge/bbsmodinforge/new -C new status --short
.\gradlew.bat build
```

Run the Gradle command with working directory:

```text
D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new
```

## Notes

The first implementation should prioritize a compiling, serializable, non-crashing Structure form with visible block-state rendering. If block entities or advanced optimization require more invasive rendering work, record the limitation and keep the initial scope stable.
