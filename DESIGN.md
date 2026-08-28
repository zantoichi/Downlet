<!-- SEED — re-run $impeccable document once there's code to capture the actual tokens and components. -->
---
name: Downlet
description: A quiet native desktop utility for clear media-download decisions.
---

# Design System: Downlet

## Overview

**Creative North Star: "The Quiet Transfer Desk"**

Downlet should resemble a well-made Windows utility opened for one short task. A person pastes a YouTube URL at their Windows desktop in ordinary daytime or evening light, expects immediate confirmation, and wants a local audio or video file without learning a download tool. Normal launch adopts the current Windows light or dark preference at startup through Compose `isSystemInDarkTheme()`, with light as the fallback. The Design Review Controller may override this deterministically. Live switching after Windows changes theme is not promised in this design change; restarting the app picks up the new preference.

The interface combines JetBrains New UI precision with familiar Windows utility behavior. It uses one stable column, compact desktop rhythm, and progressive disclosure. One low-chroma cool work plane adds depth without becoming a card layout. State motion clarifies change without becoming decoration.

**Key Characteristics:**

- Native and task-first
- Compact but comfortable
- Quiet until information is useful
- Precise alignment and hierarchy
- Stable geometry across states
- Keyboard-aware and semantically explicit

## Colors

Use Jewel's `IntUiTheme` semantic palette as the source of truth in both light and dark modes. Add only one low-chroma cool secondary surface and one existing theme accent role when the running composition needs clearer grouping.

**The Restrained Color Rule.** Tinted neutrals carry the surface. Accent and semantic colors appear only for the primary action, focus, selection, progress, success, warning, and error.

**The Meaning Rule.** No color exists solely to decorate. Error and success must remain understandable without color.

## Typography

Use Jewel typography throughout, with one native UI family and no display face. Exact font metrics remain owned by the active Jewel theme.

**Character:** Technical enough to feel precise, ordinary enough to disappear into the task.

### Hierarchy

- **Title:** Media identity only; medium emphasis and at most two lines in the normal Ready state.
- **Body:** Metadata, progress copy, and explanatory text; compact and readable.
- **Label:** Form labels and status labels; sentence case, never decorative uppercase.
- **Action:** Jewel button typography with one visually dominant primary action per state.

**The No-Hero Rule.** Downlet has no marketing headline. Media identity, current status, and the next action own the hierarchy.

## Elevation

The window background stays flat. The changing state body sits in one inset work plane with a restrained tonal fill, thin theme-aware boundary, and no shadow. Shadows belong only to platform-managed windows, popups, menus, and dialogs where elevation communicates interaction.

**The One-Plane Rule.** If the composition needs another card, nested container, or shadow to look organized, its grouping or spacing is wrong.

## Components

Jewel components are the visual and behavioral source of truth. The YouTube field uses native text editing and fills the row; there is no visible Paste or Analyze button. Product-specific additions stay limited to content width, major-region spacing, work-plane treatment, thumbnail geometry, and status treatment Jewel cannot express directly.

## Motion

State-body replacement uses one `180–220 ms` fade with at most `6.dp` of vertical rise and `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`. Focus, paste, and status feedback may reuse this timing. Respect Compose duration scaling so zero scale produces the instant final state.

No bounce, spring overshoot, staggered choreography, infinite decorative loop, or motion that blocks interaction. Motion never carries status meaning without persistent text or control-state changes.

## Do's and Don'ts

### Do:

- **Do** use `IntUiTheme`, Jewel typography, native controls, semantic colors, focus behavior, and desktop metrics before custom styling.
- **Do** keep one primary content column and stable window geometry near 720 × 420, with a usable minimum near 620 × 350.
- **Do** reveal quality, destination, and download controls only when media is Ready.
- **Do** use the single tonal work plane, rounded bordered preview, and compact state transition to make Ready feel finished without adding more containers.
- **Do** preserve media identity while Downloading, Completed, or showing a recoverable error.
- **Do** make every essential action visible, labeled, keyboard reachable, and semantically meaningful.
- **Do** use short state transitions that communicate change without choreography.

### Don't:

- **Don't** build an IntelliJ clone with tool-window chrome, sidebars, tabs, consoles, or configuration density.
- **Don't** build a generic SaaS or AI-generated dashboard with giant headings, hero layouts, repeated cards, or ornamental metrics.
- **Don't** use oversized cards, nested cards, excessive whitespace, giant corner radii, or pill-shaped controls everywhere.
- **Don't** use glassmorphism, gratuitous gradients, neon accents, decorative effects, or color without meaning.
- **Don't** add a generic animation framework, ambient loop, card grid, or nested card treatment for this one-window utility.
- **Don't** enlarge mobile Material patterns for desktop.
- **Don't** add a telemetry dashboard, terminal panel, format table, codec picker, flag editor, or raw backend logs.
- **Don't** add settings, onboarding, account, cloud, history, or library surfaces without an approved product need.
- **Don't** use confetti, celebration pages, giant success treatments, or playful motion that delays the task.
- **Don't** use decorative icons where text is clearer, hide essential actions behind hover, or ship unlabeled icon-only actions.
