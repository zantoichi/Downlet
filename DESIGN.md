# Design: Downlet

## Direction

**The Quiet Transfer Desk:** a compact Windows utility opened for one short task. The interface combines JetBrains New UI precision with familiar Windows behavior while avoiding IDE chrome.

Normal launch follows the host system's light or dark preference, with light as the fallback. A compact title-bar toggle switches themes without leaving the task. The product uses one stable column, compact desktop rhythm, progressive disclosure, and one low-chroma work plane after media resolves.

## Layout

- Keep one fixed `760` logical-pixel width.
- Empty and Previewing use Compact `760 × 188`.
- All other states use measured content height, including setup phases, errors, and expanded guidance, with no scroll container.
- Terms and audio help show one section per page with Previous and Next. If the active work area is smaller than the content, fit the content proportionally so all actions remain visible.
- Keep the URL row anchored while automatic sizing changes height only.
- Use one inset tonal work plane for resolved media and later states. No nested cards or shadows.
- Center the Downlet icon and wordmark in the title bar independently of the trailing theme control.
- Stack the YouTube label above its field. In Ready, keep media identity full-width, place Download as beside Format & quality, and show at most two lines of supporting format detail. Keep the Save to icon, label, location, and Change action on one line. Permission stays full-width with feedback and actions at the bottom.

## Visual System

- Use Jewel `IntUiTheme`, native controls, focus behavior, metrics, and semantic colors as the source of truth.
- Use Compose's native Windows sans-serif stack (`Segoe UI`, with platform fallback) for all visible UI text. Body and controls are `16/22sp` Regular; legal prose is `15/21sp`; form labels and metadata are `14/18sp`; media titles are `16/21sp` SemiBold; section headings are `17/22sp` SemiBold. Text buttons are at least `36dp` tall so glyphs retain vertical clearance.
- Use tabular figures for durations and progress data, and disable ligatures only for URLs and filesystem paths.
- Use accent and semantic colors only for actions, focus, selection, progress, success, warning, and error.
- Keep the window background flat; shadows belong only to platform-managed windows, menus, popups, and dialogs.
- Use one type family and only Regular or SemiBold. Media titles use SemiBold and at most two lines; labels stay sentence case.
- Keep essential actions visible, text-labelled, keyboard reachable, and understandable without color.
- Pair labelled sections and key actions with Jewel icons. Icons beside text are decorative in accessibility semantics; essential controls remain text-labelled.

## Components

- The YouTube-link field fills its row and uses native text editing; there is no visible Paste or Analyze button.
- Ready reveals media identity, Video or Audio, format and quality, destination, and one primary Download action. Video choices pair resolution with bitrate and may add FPS, friendly codec/container names, estimated size, and HDR. Audio choices distinguish source-preserving original audio from MP3 conversion and explain the quality ceiling.
- Preserve media identity while Downloading, Completed, or showing a recoverable error.
- Missing previews keep the same 16:9 geometry and clearly communicate `Preview unavailable`.
- Downloading uses Jewel progress colors in a `6.dp` pill rail. Determinate transfer adds a decorative `2.dp` leading cap in the indeterminate-highlight color; Preparing, unknown totals, and processing use the same rail indeterminately.
- Keep transfer telemetry in one compact row: bytes and speed on the left, approximate ETA on the right, with unavailable values omitted.

## Motion

State-body replacement uses one `180–220 ms` fade with at most `6.dp` of vertical rise and `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`. Determinate progress uses the same easing over `200 ms`. Compact-to-Expanded height motion uses `250 ms`; Expanded-to-Compact uses `167 ms`. The success icon may fade and scale from `0.92` to `1.0` over `160 ms`.

Width stays fixed, the URL anchor stays stable, and zero duration applies final content and bounds immediately. With motion disabled, indeterminate progress uses a static centered highlight. No bounce, overshoot, staggered choreography, decorative loops, or motion that carries status meaning by itself.

## Avoid

- IDE tool windows, sidebars, tabs, consoles, menus, toolbars, or breadcrumbs.
- Generic SaaS cards, hero layouts, ornamental metrics, glass effects, or decorative gradients.
- Telemetry dashboards, format tables, codec pickers, flag editors, raw logs, or speculative product surfaces.
- Icon-only essential actions, hover-only controls, or playful completion treatments.
