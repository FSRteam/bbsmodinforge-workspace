# Cross-Layer Thinking Guide

> **Purpose**: Think through data flow across layers before implementing.

---

## The Problem

**Most bugs happen at layer boundaries**, not within layers.

Common cross-layer bugs:

- API returns format A, frontend expects format B
- Database stores X, service transforms to Y, but loses data
- Multiple layers implement the same logic differently

---

## Before Implementing Cross-Layer Features

### Step 1: Map the Data Flow

Draw out how data moves:

```
Source → Transform → Store → Retrieve → Transform → Display
```

For each arrow, ask:

- What format is the data in?
- What could go wrong?
- Who is responsible for validation?

### Step 2: Identify Boundaries

| Boundary              | Common Issues                     |
| --------------------- | --------------------------------- |
| API ↔ Service         | Type mismatches, missing fields   |
| Service ↔ Database    | Format conversions, null handling |
| Backend ↔ Frontend    | Serialization, date formats       |
| Component ↔ Component | Props shape changes               |

### Step 3: Define Contracts

For each boundary:

- What is the exact input format?
- What is the exact output format?
- What errors can occur?

### Model rendering -> procedural simulation boundary

When render code feeds IK, bone physics, constraints, particles, or another
stateful simulation, explicitly identify all three before editing:

- **render space**: may include camera-relative/view/item transforms;
- **semantic simulation space**: world or stable model-local space without the observer;
- **simulation owner**: the concrete entity, preview, or placement that owns history.

Also write the solver order down. A later generic pose pass can silently erase
an earlier quaternion/procedural result even when both systems work alone.

For a procedural solver hot path, classify every value before optimizing it:

- immutable topology/config derivative -> compile/cache by source data;
- mutable per-frame scratch -> retain under renderer + simulation owner;
- cross-frame history -> retain under renderer + simulation owner + model/chain;
- render/view state -> never use as simulation history or a bend fallback axis.

After the change, audit both CPU work and allocation after warm-up. Rebuilding
chain order, wanted-bone sets, frame maps, chain-frame lists, or stiffness
arrays each render is a boundary mistake even if the mathematical solver is
correct. Do not trade away fixed substeps, configured iterations, collisions,
or constraints to hide that overhead.

For BBS model forms, follow `.trellis/spec/new/backend/quality-guidelines.md`
section "Model simulation space, ownership, and solver-order contract".

---

## Common Cross-Layer Mistakes

### Mistake 1: Implicit Format Assumptions

**Bad**: Assuming date format without checking

**Good**: Explicit format conversion at boundaries

### Mistake 2: Scattered Validation

**Bad**: Validating the same thing in multiple layers

**Good**: Validate once at the entry point

### Mistake 3: Leaky Abstractions

**Bad**: Component knows about database schema

**Good**: Each layer only knows its neighbors

### Mistake 4: Every Consumer Parses The Same Payload

**Bad**: A command reads JSONL events and casts fields inline:

```typescript
const thread = (ev as { thread?: string }).thread;
const labels = (ev as { labels?: string[] }).labels;
```

This looks local, but it means every consumer owns a private version of the
event contract. The next field change will update one command and miss another.

**Good**: Decode once at the event boundary, then export typed projections:

```typescript
if (!isThreadEvent(ev)) return false;
return ev.thread === filter.thread;
```

**Rule**: For append-only logs, JSON streams, RPC payloads, or config files,
create one owner for:

- event / payload type definitions
- type guards and normalization from `unknown`
- metadata projections used by UI commands
- reducers that replay state from the source of truth

Rendering code may format fields, but it must not redefine the payload contract.

### Mistake 5: Dropping The Owner Before Releasing Its Resources

Long-running UI sessions often span UI, controller, process, and filesystem
layers. If a disconnect or world-unload handler clears the UI owner first, it
may make the session unreachable before the session releases temporary files,
processes, or render state.

**Bad**: clear the dashboard, then stop only the low-level recorder.

**Good**: while the owner is still reachable, invoke the session's normal
cancel/teardown path; only then clear the owner reference.

**Review rule**: for every session-owned resource, enumerate all terminal
events (failure, manual cancel, natural completion, disconnect, world unload)
and verify that each event reaches the same idempotent teardown owner.

### Live value-tree mutation filters

When an editor sends only selected `BaseValue` paths to a live runtime, treat
the selection predicate as a cross-layer protocol rather than a UI detail:

```
value pre-callback -> undo batch -> client path serialization
                   -> server path lookup -> runtime refresh decision
```

Adding a runtime-significant value requires updating every path filter and the
server refresh classification together. Add one regression that starts at the
editor predicate and reaches the exact server runtime mutation call; otherwise
the saved Film can be correct while the currently playing Film remains stale.

### Gesture terminal propagation

When a press crosses widget, menu, screen, and remote-input layers, trace two
different terminal flows: physical **release** may commit, while lifecycle
**cancel** must only retire ownership. Check every adapter and wrapper for the
same `(button, generation)` and add an integration test that reaches the real
owner. See the UI gesture contract in
`.trellis/spec/new/backend/quality-guidelines.md`.

---

## Checklist for Cross-Layer Features

Before implementation:

- [ ] Mapped the complete data flow
- [ ] Identified all layer boundaries
- [ ] Defined format at each boundary
- [ ] Decided where validation happens

After implementation:

- [ ] Tested with edge cases (null, empty, invalid)
- [ ] Verified error handling at each boundary
- [ ] Checked data survives round-trip
- [ ] Checked that consumers import shared decoders / projections instead of
      casting payload fields locally
- [ ] Checked that derived state points back to the source event identifier
      (`seq`, `id`, `version`) instead of inventing a second cursor
- [ ] Checked that disconnect/world-unload releases session-owned resources
      before UI/controller ownership is discarded

---

## Cross-Platform Template Consistency

In Trellis, command templates (e.g., `record-session.md`) exist in **multiple platforms** with identical or near-identical content. This is a cross-layer boundary.

### Checklist: After Modifying Any Command Template

- [ ] Find all platforms with the same command: `find src/templates/*/commands/trellis/ -name "<command>.*"`
- [ ] Update all platform copies (Markdown `.md` and TOML `.toml`)
- [ ] For Gemini TOML: adapt line continuations (`\\` vs `\`) and triple-quoted strings
- [ ] Run `/trellis:check-cross-layer` to verify nothing was missed

**Real-world example**: Updated `record-session.md` in Claude to use `--mode record`, but forgot iFlow, Kilo, OpenCode, and Gemini — caught by cross-layer check.

---

## Pinned Migration Baseline Preflight

Before migrating code between FS and FSR:

- [ ] Read `.trellis/spec/new/backend/fs-migration-baseline.md`
- [ ] Fetch the recorded immutable master and same-Minecraft commit hashes
      explicitly; do not trust stale local branch names
- [ ] Fetch current moving branch tips only after the fixed commits are local
- [ ] Validate ancestry before calculating `fixed_commit..current_tip`
- [ ] Advance the Trellis marker only after the target build and review pass

If the fixed commit cannot be fetched or is no longer an ancestor of the moving
tip, treat it as an upstream history rewrite and stop for a new reviewed mapping.

---

## Generated Runtime Template Upgrade Consistency

Some generated files are both documentation and runtime input. In Trellis,
`.trellis/workflow.md` is parsed by `get_context.py`, `workflow_phase.py`,
SessionStart filters, and per-turn hooks. Template changes must be validated
against both fresh init and upgrade paths.

### Checklist: After Modifying A Runtime-Parsed Template

- [ ] Identify every runtime parser that reads the template, not just the file
      writer that installs it
- [ ] Check whether relevant syntax lives outside obvious managed regions
      such as tag blocks
- [ ] Verify fresh `init` output and a versioned `update` scenario that writes
      the older `.trellis/.version`
- [ ] Add an upgrade regression using an older pristine template fixture, then
      assert the installed file reaches the current packaged shape
- [ ] Update the backend spec that owns the runtime contract

---

## Versioned Documentation Boundary

Versioned documentation is a cross-layer boundary: source paths, `docs.json`
version routing, and the rendered version selector must all describe the same
release line.

### Checklist: Before Editing Versioned Docs

- [ ] Identify the target release line: stable, beta, or RC
- [ ] Verify the edited MDX path matches that line:
  - stable: `docs-site/{start,advanced,...}` and `docs-site/zh/{start,advanced,...}`
  - beta: `docs-site/beta/**` and `docs-site/zh/beta/**`
  - RC: `docs-site/rc/**` and `docs-site/zh/rc/**`
- [ ] Verify `docs.json` navigation points the version label to the same paths
- [ ] Grep the opposite tree for release-line-specific terms before committing
- [ ] Treat beta content appearing under root release paths as a source-path bug,
      not a rendering bug

**Real-world example**: A beta-only task workflow change documented
`prd.md` + `design.md` + `implement.md`, task-creation consent, and Codex
mode banners under root `start/` and `advanced/` paths. The docs site then
served 0.6 beta behavior under the Release selector. The fix was to restore root
release docs, move the 0.6 content to `beta/` and `zh/beta/`, and add a grep
audit for beta markers against the root release tree.

**Real-world example**: Codex inline mode changed workflow platform markers from
`[Codex]` / `[Kilo, Antigravity, Windsurf]` to `[codex-sub-agent]` /
`[codex-inline, Kilo, Antigravity, Windsurf]`. Fresh init was correct, but
`trellis update` only merged `[workflow-state:*]` blocks and preserved stale
markers outside those blocks. Result: upgraded projects got new hook scripts
but old workflow routing, so `get_context.py --mode phase --platform codex`
could return empty Phase 2.1 detail.

---

## Mode-Detection Probe Checklist

When a CLI auto-detects a mode by probing a remote resource (e.g., checking if `index.json` exists to decide marketplace vs direct download):

### Before implementing:

- [ ] Probe runs in **ALL** code paths that use the result (interactive, `-y`, `--flag` combos)
- [ ] 404 vs transient error are distinguished — don't treat both as "not found"
- [ ] Transient errors **abort or retry**, never silently switch modes
- [ ] Shared state (caches, prefetched data) is **reset** when context changes (e.g., user switches source)
- [ ] **Shortcut paths** (e.g., `--template` skipping picker) must have the same error-handling quality as the probed path — check that downstream functions don't call catch-all wrappers

### After implementing:

- [ ] Trace every path from probe result to the mode-decision branch — no fallthrough
- [ ] External format contracts (giget URI, raw URLs) are tested or at least documented as comments
- [ ] Metadata reads consume a complete response or use a streaming parser — never parse a fixed-size prefix as full JSON
- [ ] When reconstructing a composite identifier from parsed parts, verify **all** fields are included and in the **correct position** (e.g., `provider:repo/path#ref` not `provider:repo#ref/path`)
- [ ] Verify that **action functions** called after a shortcut don't internally use the old catch-all fetch — they must use the probe-quality variant when error distinction matters

**Real-world example**: Custom registry flow had 8 bugs across 3 review rounds: (1) probe only ran in interactive mode, (2) transient errors fell through to wrong mode, (3) giget URI had `#ref` in wrong position, (4) prefetched templates leaked across source switches, (5) `--template` shortcut bypassed probe but `downloadTemplateById` internally used catch-all `fetchTemplateIndex`, turning timeouts into "Template not found".

**Real-world example**: Agent-session update hints fetched npm `latest` metadata with `response.read(4096)` and then parsed it as complete JSON. The `@mindfoldhq/trellis` package metadata exceeded 4 KB, so the JSON was truncated, parse failed silently, and the first session injection showed no update hint. Fix: read the complete response before parsing, and add a regression where `version` is followed by an 8 KB metadata tail.

---

## When to Create Flow Documentation

Create detailed flow docs when:

- Feature spans 3+ layers
- Multiple teams are involved
- Data format is complex
- Feature has caused bugs before

---

## Event Log / Projection Boundary

Append-only logs are cross-layer contracts. A single event travels through:

```
CLI input → event writer → events.jsonl → reader → filter → reducer → display
```

### Checklist: After Adding A New Event Kind Or Field

- [ ] Add the event kind to the central event taxonomy
- [ ] Add a typed event variant or type guard at the event layer
- [ ] Add normalization helpers for array/object fields that come from
      user input or JSON
- [ ] Keep `seq` / `id` assignment in the event writer only
- [ ] Make filters and reducers consume the typed event guard, not local casts
- [ ] Make display code consume reducer output or typed events, not raw JSON
- [ ] Add at least one regression that proves history replay and live filtering
      use the same filter model

**Real-world example**: Thread channels added `kind: "thread"`, `description`,
`context`, labels, and `lastSeq`. The first implementation replayed thread
state correctly, but several commands still re-parsed event payload fields with
local casts. The fix was to make the core event layer own `ThreadChannelEvent`
and `isThreadEvent`, make `reduceChannelMetadata` the only channel metadata
projection, and make `reduceThreads` the only thread replay reducer.

---

## GPU Picking Identity Boundary

A viewport selection crosses several layers:

```
CPU id allocation → vertex/instance data → picker shader → framebuffer RGB
→ integer decode → id map lookup → active UI consumer
```

- Define one tested composition contract for global and local ID components,
  and keep every geometry backend plus the shader and CPU lookup map in the
  same order. Do not rewrite a working composition boundary without evidence.
- Verify ordinary geometry independently from debug/gizmo overlays. An overlay
  that writes complete RGB IDs can work while the underlying model path is
  entirely broken.
- When a downstream selection handler is correct but receives nothing, inspect
  the pixel-to-map contract before adding more event-dispatch fallbacks.
- Cover every geometry backend and verify that optional overlays only append
  IDs; toggling a feature must not renumber or disable unrelated pick targets.
- Derive every non-owner ID range from the owner’s maximum constant. Also trace
  who consumes the raw decoded ID first: a high-priority gizmo handler can
  intercept an otherwise valid form-map entry before lookup occurs.

### Capture is not presentation

For framebuffer workflows, list every output explicitly:

```
world render → private framebuffer → encoder texture
                                 ↘ player window
                                 ↘ bounded editor preview
```

Updating the encoder texture does not update the window, and rebinding the
window framebuffer does not draw the private framebuffer into it. Define which
consumer receives overlays, when the copy happens, and whether presentation is
full-screen or owned by a UI region.
