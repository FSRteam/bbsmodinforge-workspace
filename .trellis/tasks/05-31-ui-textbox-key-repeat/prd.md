# UI Textbox Key Repeat

## Goal

Focused BBS text inputs should repeat common editing keys while held, matching normal desktop/web text fields.

## Requirements

- Let focused text input controls receive `GLFW_REPEAT`.
- Preserve current single-press behavior for non-text global shortcuts and controls.
- Cover at least Backspace, Delete, Left, Right, Home, and End where supported by the textbox implementation.

## Acceptance Criteria

- [ ] Holding Backspace in a focused `UITextbox` repeatedly deletes characters.
- [ ] Holding Delete in a focused `UITextbox` repeatedly deletes forward.
- [ ] Holding Left/Right repeatedly moves the caret.
- [ ] Non-text UI shortcuts do not trigger repeatedly unless already designed to.
