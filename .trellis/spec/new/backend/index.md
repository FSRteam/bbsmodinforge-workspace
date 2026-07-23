# Backend Development Guidelines

> Best practices for backend development in this project.

---

## Overview

This directory contains guidelines for backend development. Fill in each file with your project's specific conventions.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Module organization and file layout | To fill |
| [Database Guidelines](./database-guidelines.md) | ORM patterns, queries, migrations | To fill |
| [Error Handling](./error-handling.md) | Error types, handling strategies | To fill |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | Partial |
| [Logging Guidelines](./logging-guidelines.md) | Structured logging, log levels | To fill |
| [FS → FSR Migration Baseline](./fs-migration-baseline.md) | Exact old2.0/FS/FSR commit markers and next-range validation | Active |
| [Audio Format And Render Contract](./audio-format-render-contract.md) | PCM/WAV/Vorbis, layout conversion, routing, import, and 5.1 rejection | Active |
| [Audio Capture And Playback Contract](./audio-capture-playback-contract.md) | Capture commit fencing, final drain, voices, dispatch, and resource ownership | Active |
| [Audio Export And Premiere Contract](./audio-export-premiere-contract.md) | Typed export lifecycle, artifact identity, mux teardown, and XMEML delivery | Active |
| [Audio Verification Contract](./audio-verification-contract.md) | Hermetic, performance, strict-media, add-on, and manual-smoke gates | Active |
| [Hot Plugin Runtime Contract](./hot-plugin-runtime-contract.md) | Artifact, watcher, generation routing, ownership, refresh, and teardown contracts | Active |

---

## How to Fill These Guidelines

For each guideline file:

1. Document your project's **actual conventions** (not ideals)
2. Include **code examples** from your codebase
3. List **forbidden patterns** and why
4. Add **common mistakes** your team has made

The goal is to help AI assistants and new team members understand how YOUR project works.

---

**Language**: All documentation should be written in **English**.
