# Finish Structure plugin and fix saved structure lookup

## Goal

Continue the unfinished Structure Addon/plugin work by splitting it into a standalone plugin project, like `bbs-premiere-export`, and fix the lookup mismatch where structures saved by BBS/Minecraft exist on disk but the Structure form/plugin picker or renderer cannot find them.

This continues the archived `05-31-port-bbs-structure-addon` task. The immediate bug is not that saving fails; it is that saved structures are unavailable from the plugin/form workflow after saving.

## Requirements

- Structure feature implementation should live in a new standalone plugin project, not as an embedded addon package under `new/`.
- `new/` changes should be limited to reusable extension points needed by external plugins and removal of the accidental embedded Structure implementation.
- Preserve unrelated uncommitted changes in `new/`; do not reset or revert broad files.
- Replies to the user must stay in Chinese.
- Structure form registration should go through the current addon/register-event path from the standalone plugin.
- Structures saved by `/bbs structures save` must appear in the Structure picker without manual copying.
- Structure picker values must be loadable by `StructureFormRenderer` using the same `structure_file` value.
- Missing or invalid structures must fail gracefully without crashing editor or preview rendering.

## Acceptance Criteria

- [ ] A structure saved by `/bbs structures save ...` appears in the Structure picker after refresh/reopen.
- [ ] The selected saved structure is stored in `StructureForm.structureFile` in a form the renderer resolves.
- [ ] The renderer can load saved world structures and BBS asset structures.
- [ ] Source/assets entries and liked structures keep working.
- [ ] Structure renderer and editor panel are registered for `StructureForm`.
- [ ] The plugin project follows the same shape as `bbs-premiere-export` where practical.
- [ ] `new/` compiles, or any unrelated blocker is reported clearly.

## Out Of Scope

- Reworking the save command UI beyond the lookup path needed by the plugin/form picker.
- Reverting unrelated particle editor, renderer, or Premiere export changes.
