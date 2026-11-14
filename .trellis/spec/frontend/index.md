# Frontend Development Guidelines

> Best practices for frontend development in this project.

---

## Overview

This directory contains guidelines for frontend development. Fill in each file with your project's specific conventions.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [BBS UI Theming](./bbs-ui-theming.md) | Mod UI (new/) theme token rules: semantic colors, alpha composition, hot-path red lines | Active |
| [Film Home Remote Content](./film-home-remote-content.md) | Film home ads/news worker delivery chain, client fallback order, MarkdownBody scroll contract | Active |
| [Web Remote Browser Client](./web-browser-client-architecture.md) | bbs-web-remote web bundle: IIFE ns module contract, WebTest export, no-service code rule | Active |
| [WebRemote RTC Lock Discipline](./webremote-rtc-lock-discipline.md) | libwebrtc native calls must run outside every lock/monitor; signaling-thread handoff contract | Active |
| [UI Mirror Failure Discipline](./ui-mirror-failure-discipline.md) | FSR capture layer: Throwable accounting, worker revival, ACTIVE_FRAME lifecycle, diagnostics snapshots | Active |
| [Directory Structure](./directory-structure.md) | Module organization and file layout | To fill |
| [Component Guidelines](./component-guidelines.md) | Component patterns, props, composition | Partial |
| [Hook Guidelines](./hook-guidelines.md) | Custom hooks, data fetching patterns | To fill |
| [State Management](./state-management.md) | Local state, global state, server state | To fill |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | To fill |
| [Type Safety](./type-safety.md) | Type patterns, validation | To fill |

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
