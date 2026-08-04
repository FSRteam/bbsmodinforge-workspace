# Remove Bezier Ctrl-Click Add Points

## Goal

Remove the Ctrl+left-click behavior that adds keyframes/segments for regular Bezier curves.

## Requirements

- Disable Ctrl+left-click add behavior in `UICurve.bezierMouseClicked(...)`.
- Preserve normal Bezier point selection and dragging.
- Preserve Bezier chain Ctrl+left-click node creation unless the curve editor task changes it separately.

## Acceptance Criteria

- [ ] Ctrl+left-click on empty space in regular Bezier mode does not add points.
- [ ] Clicking and dragging existing Bezier handles still works.
- [ ] Bezier chain Ctrl+left-click behavior is unchanged.
