# Port BBS Structure Addon to NeoForge

## Goal

Port the existing BBS Structure Addon concept from the old Fabric 1.20.4 ecosystem to the `new/` NeoForge 1.21.1 project, compatible with the current BBS addon protocol and form/rendering systems.

The migrated feature should let users create a Structure form in BBS, select a saved Minecraft structure, and render it as a replay/form element in the current NeoForge build.

## Requirements

- Implementation changes must stay under `new/`.
- `old/`, external mod pages, and any downloaded references are research-only.
- Follow Trellis workflow: plan before implementation, check before completion, update specs if useful, and commit only after explicit user confirmation.
- Replies to the user must stay in Chinese.
- Only commit `new/` content after user confirmation.
- Preserve other Codex/user work; do not revert unrelated changes.
- Use the existing BBS addon registration protocol in `new/src/main/java/mchorse/bbs_mod/addon`.
- Use the existing BBS form, form editor, renderer, localization, and resource patterns instead of adding unrelated frameworks.
- The feature should target NeoForge 1.21.1 names and APIs, including 1.21.1 structure template and rendering APIs.
- The Structure form should be serializable through existing BBS form data APIs.
- Existing BBS forms and addons must continue to load and render unchanged.

## Acceptance Criteria

- [ ] `new` exposes a Structure form in the BBS forms selection UI with the existing structure/tree icon or a consistent fallback icon.
- [ ] The Structure form stores at least the structure id/path needed to load a saved Minecraft structure.
- [ ] The form editor lets users edit or pick the structure id using existing BBS UI controls.
- [ ] Rendering loads and displays the selected structure in editor/world form rendering on NeoForge 1.21.1.
- [ ] Missing or invalid structure ids fail gracefully without crashing the editor or renderer.
- [ ] The implementation uses `new`'s addon bridge/registration path where appropriate, rather than Fabric entrypoints.
- [ ] Serialization/deserialization round-trips Structure form data.
- [ ] `.\gradlew.bat build` passes from `new/`.
- [ ] `git -C new diff` contains only intended Structure Addon migration changes.
- [ ] A local git commit is created only after explicit user confirmation, and only for `new/` changes.

## Research Notes

- Current `old/src` and `new/src` both already include a BBS `/bbs structures save` command, but no Structure form implementation was found by a focused `rg` search.
- `new` already contains a NeoForge addon protocol:
  - `mchorse.bbs_mod.addon.BBSAddonRegisterEvent`
  - `mchorse.bbs_mod.addon.BBSAddonCollector`
  - `mchorse.bbs_mod.addon.BBSAddonBridge`
  - `mchorse.bbs_mod.events.BBSAddonMod`
- CurseForge and Modrinth describe BBS Structure Addon as a client/server addon that lets saved Minecraft structures be used as BBS replay/form elements.
- CurseForge lists project id `1474890`, MIT license, latest 1.20.4 Fabric release on March 24, 2026, and source link to GitHub.
- External `git clone` to `docs/temp` was requested but rejected by the sandbox approval reviewer; use read-only web/source metadata and local source inspection unless the user explicitly approves another route.

## Open Questions

- If the external source remains unavailable locally, implementation will be based on the public behavior description plus the current BBS form architecture.
- The exact old addon class names and UI labels may differ from the public standalone addon; match current BBS conventions over reproducing private naming.
