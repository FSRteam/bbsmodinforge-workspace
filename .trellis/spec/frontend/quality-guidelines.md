# Quality Guidelines

> Code quality standards for frontend development.

---

## Overview

<!--
Document your project's quality standards here.

Questions to answer:
- What patterns are forbidden?
- What linting rules do you enforce?
- What are your testing requirements?
- What code review standards apply?
-->

(To be filled by the team)

---

## Forbidden Patterns

<!-- Patterns that should never be used and why -->

(To be filled by the team)

---

## Required Patterns

<!-- Patterns that must always be used -->

### Canvas media command isolation

Browser media elements are asynchronous external resources. A Film, Replay, or
preview surface can become temporarily non-drawable while a WebRTC track starts,
switches, or falls back, even after an earlier readiness check succeeded.

- Every command-local `CanvasRenderingContext2D.save()` must have a matching
  `restore()` in `finally` when image/video drawing can throw.
- A failed dynamic `SURFACE_QUAD` draw must be treated as "not painted": keep
  drawing the remaining UI commands and retry when a later media frame requests
  another Canvas draw.
- Static UI resource failures may still propagate to the normal error path;
  dynamic-surface tolerance must not hide unrelated renderer defects.

---

## Testing Requirements

<!-- What level of testing is expected -->

- Surface renderer regressions must simulate a transient `drawImage()` failure,
  assert that the Canvas save stack is restored, and prove a later drawable
  surface succeeds without replacing the UI session.

---

## Code Review Checklist

<!-- What reviewers should check -->

(To be filled by the team)
