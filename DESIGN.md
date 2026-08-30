# Design: Downlet

## Direction

**The Quiet Transfer Desk:** a compact Windows utility opened for one short task. The interface combines JetBrains New UI precision with familiar Windows behavior while avoiding IDE chrome.

Normal launch follows the current Windows light or dark preference, with light as the fallback. The product uses one stable column, compact desktop rhythm, progressive disclosure, and one low-chroma work plane after media resolves.

## Layout

- Keep one primary content column with a `720` logical-pixel preferred width and `620` minimum width.
- Empty and Resolving use Compact `720 × 168` preferred / `620 × 156` minimum.
- Ready, Downloading, Completed, and Error use Expanded `720 × 420` preferred / `620 × 400` minimum.
- Keep the URL row anchored while automatic sizing changes height only.
- Use one inset tonal work plane for resolved media and later states. No nested cards or shadows.

## Visual System

- Use Jewel `IntUiTheme`, native controls, focus behavior, metrics, and semantic colors as the source of truth.
- Use Mona Sans `14sp` Regular with `18sp` line height throughout. Reserve SemiBold for the Downlet title, media titles, headings, form labels, and important result labels.
- Use accent and semantic colors only for actions, focus, selection, progress, success, warning, and error.
- Keep the window background flat; shadows belong only to platform-managed windows, menus, popups, and dialogs.
- Use one type family and only Regular or SemiBold. Media titles use SemiBold and at most two lines; labels stay sentence case.
- Keep essential actions visible, text-labelled, keyboard reachable, and understandable without color.

## Components

- The YouTube-link field fills its row and uses native text editing; there is no visible Paste or Analyze button.
- Ready reveals media identity, Video or Audio, format and quality, destination, and one primary Download action.
- Preserve media identity while Downloading, Completed, or showing a recoverable error.
- Missing previews keep the same 16:9 geometry and clearly communicate `Preview unavailable`.

## Motion

State-body replacement uses one `180–220 ms` fade with at most `6.dp` of vertical rise and `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`. Compact-to-Expanded height motion uses `250 ms`; Expanded-to-Compact uses `167 ms`.

Width stays fixed, the URL anchor stays stable, and zero duration applies final content and bounds immediately. No bounce, overshoot, staggered choreography, decorative loops, or motion that carries status meaning by itself.

## Avoid

- IDE tool windows, sidebars, tabs, consoles, menus, toolbars, or breadcrumbs.
- Generic SaaS cards, hero layouts, ornamental metrics, glass effects, or decorative gradients.
- Telemetry dashboards, format tables, codec pickers, flag editors, raw logs, or speculative product surfaces.
- Icon-only essential actions, hover-only controls, or playful completion treatments.
