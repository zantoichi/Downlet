# Implementation Thread

Model: GPT-5.6 Sol  
Reasoning: High  
Base commit: `8e00c1aa7bff90e3a5681a51563b0fa91e768add`  
OpenSpec change: `design-primary-download-window`  
Tasks: `3.8`, `3.9`, `3.10`, `3.11`, `3.12`, `3.13`, `3.14`  
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Build the smallest complete G1 Ready composition: deterministic media identity, Video/Audio choice, useful quality,
destination, one Download action, compact-height behavior, and focused state tests. Keep every interaction fake and local.

## Required context

Read these before editing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/design/reviews/G0-direction/REVIEW.md`
- `docs/design/reviews/G0-direction/CODABILITY.md`
- `docs/design/evidence/G1/foundation-window-proof.md`
- `docs/implementation-threads/03-g1-url-flow.md`
- `openspec/changes/design-primary-download-window/proposal.md`
- `openspec/changes/design-primary-download-window/design.md`
- `openspec/changes/design-primary-download-window/tasks.md`
- all specs under `openspec/changes/design-primary-download-window/specs/`
- current `Main.kt`, `DownloadState.kt`, `DesignReview.kt`, focused tests, build files, and resources

Use the repository-local `openspec-apply-change` skill. Load the installed `impeccable` skill before UI edits. The approved
G0 direction, The Quiet Transfer Desk, is fixed. Do not generate alternatives, rerun shape, or redesign it. Use Impeccable
for one bounded preflight and one production-craft check against that direction.

## Starting baseline

- Tasks 2.2-2.9 and 3.2-3.6 pass at the base commit.
- The product is a Jewel `DecoratedWindow` with matching Light/Dark title bars, 720x420 target bounds, and 620x350 minimum.
- The persistent YouTube-link row, validation, Windows Paste adapter, Ctrl+V intent, 350 ms manual debounce, Empty body,
  and fixed 550 ms Resolving transition are implemented and tested.
- `DownloadStateHolder` owns explicit deterministic fixtures and enters `Ready(fixture)` after resolution.
- Ready still renders only a placeholder label.
- Design Review pins the product window at `(8.dp, 48.dp)` so the controller cannot contaminate client-area captures.
- IntelliJ run configurations and Compose Hot Reload MCP are working. Keep pinned versions, Java 21 bytecode, and JBR 25.

## Required implementation order

### 1. Ready media identity, task 3.8

- Replace the Ready placeholder with one compact media identity row.
- Use Compose `Image` with one bundled deterministic local resource. Prefer a small hand-authored local SVG loaded through
  the already-available Compose Desktop resource API if that API compiles at the pinned versions. Do not use OpenAI image
  generation, network loading, copyrighted remote media, or a new image dependency.
- Keep the image in fixed 16:9 geometry. Use restrained clipping only if the current Compose/Jewel stack already supports it
  cleanly; do not create a thumbnail component system.
- Show the fixture title at medium emphasis, `maxLines = 2`, with ellipsis. Show one concise metadata line:
  `{channel} · {duration} · YouTube`.
- When `thumbnailResource` is null or unavailable, preserve identical geometry with one quiet fallback labeled
  `Preview unavailable`; expose meaningful grouped semantics and no broken-image ornament.
- The normal, long-title, and missing-thumbnail fixtures must fit at 720x420 and 620x350 without moving later controls.
- Do not show views, dates, codec, extractor, format IDs, or encoding metadata.

### 2. Video/Audio choice, task 3.9

- Add the smallest state needed for mutually exclusive `Video` and `Audio` modes. A tiny enum/data value in the existing
  holder is enough; do not introduce ViewModel, reducer, repository, service, or form framework layers.
- Use Jewel `RadioButtonRow` for both choices. The whole labeled row must be pointer-clickable, keyboard reachable,
  semantically selected/unselected, and disableable.
- Video is selected whenever a fresh Ready fixture appears. Changing the source URL or resetting must not retain stale Ready
  choices against new media.
- Keep the control understated: no segmented pills, icons, cards, or custom radio visuals.

### 3. Quality selector, task 3.10

- Use Jewel `ListComboBox`; inspect the exact 0.39.1 source signature before coding. Do not build a custom popup.
- Keep deterministic, ordinary-user options only:
  - Video: `Best available — 2160p`, `1440p`, `1080p`, `720p`, `480p`.
  - Audio: `Best available — 251 kbps audio`, `160 kbps audio`, `128 kbps audio`.
- The selected value must expose the resolved best without opening the popup. Switching mode resets selection to that mode's
  best option. The selector must have stable bounded width and remain usable at 620x350.
- Do not expose format IDs, codecs, containers, bitrate modes, search, or advanced options.

### 4. Destination row, task 3.11

- Show `Downloads` initially. Use Jewel `Text` plus Jewel `Link` labeled `Change…`.
- `Change…` cycles only deterministic fixtures, beginning with the existing long destination fixture. It must not access the
  filesystem or open a native picker.
- Long visible destination text truncates with ellipsis before it can move or cover `Change…`. Preserve the full destination
  as semantics when truncation occurs.
- After activation, show exactly `Save location changed to {destination}.` in a compact status region without shifting the
  primary action off-screen.
- Reset/new media restores the fixture destination and clears stale acknowledgement.

### 5. Ready Download action, task 3.12

- Use one Jewel `DefaultButton` labeled `Download`, aligned as the sole primary action.
- Enabled state follows valid Ready data plus the deterministic disabled-action fixture. Do not add another primary action.
- Pointer or keyboard activation stays in Ready and shows exactly `Design preview: Download action received.`
- A mode, quality, destination, URL, fixture, or reset change clears stale Download acknowledgement.
- Do not implement Downloading, progress, Cancel, real download behavior, filesystem writes, or backend preparation. G2 owns
  the deterministic Downloading transition.

### 6. Compact-height and overflow behavior, task 3.13

- Keep one root column and one Ready body. No cards, nested panels, tabs, sidebar, toolbar, or alternate mobile composition.
- Use one `BoxWithConstraints` metric switch below roughly 380 dp client height:
  - default: approximately 20 dp outer padding, 16 dp major gaps, 8 dp control gaps;
  - compact: approximately 16 dp outer padding, 12 dp major gaps, smaller 16:9 thumbnail.
- Keep the existing 96 dp form-label alignment for `YouTube link`, `Download as`, `Quality`, and `Save to` where practical.
- At 720x420 and 620x350, every essential Ready control and Download must remain visible and usable. At a modestly larger
  size, the composition must stay compact instead of stretching into dashboard-like whitespace.
- Add bounded vertical overflow only if the exact minimum-size/scale proof requires it. No scrollbar at default size and font.

### 7. Focused tests and review fixtures, task 3.14

- Add the smallest deterministic tests that protect:
  - Video default and mutually exclusive mode changes;
  - mode-specific best-quality reset and explicit selected labels;
  - quality selection changes;
  - destination cycling and acknowledgement;
  - Download enabled/disabled behavior and G1 acknowledgement;
  - source edit, new resolution, fixture change, and Reset clearing stale Ready selections/feedback;
  - existing URL validation, paste intent, debounce, and resolving tests remaining green.
- Do not add screenshot goldens, a UI-test framework, generic fixtures, or speculative production abstractions.
- Extend the developer-only controller only as needed to force G1 Ready fixtures: normal, long title, missing thumbnail, long
  destination, and disabled Download. Use one compact native control arrangement; keep it out of product captures.

## Product and Impeccable constraints

- Primary user: a nontechnical Windows user downloading YouTube audio or video locally.
- Register: compact native desktop utility, not a marketing surface, SaaS dashboard, mobile app, or IDE.
- Ready is the principal composition, but hierarchy comes from media identity, alignment, spacing, and one primary action.
- Use Jewel components, semantic colors, typography, metrics, focus, disabled states, and accessibility behavior first.
- No cards, nested panels, gradients, glass, pills, giant headings, toolbar, tabs, sidebar, project stripe, decorative badges,
  ornamental icons, or excessive empty space.
- Basic accessibility is mandatory: logical focus order, visible focus, meaningful native roles/names/values, selected and
  enabled state, grouped media semantics, full destination semantics, sufficient contrast, and feedback not conveyed by color.
- Keep copy ordinary and exact. Do not expose `yt-dlp`, extractor, process, codec, format ID, or backend terminology.
- Run one bounded Impeccable critique after the first complete Ready render. Batch related defects into one coherent pass,
  verify once, and stop. Do not enter a micro-polish loop.

## Architecture and scope boundaries

- DESIGN ONLY: no yt-dlp, subprocess, network, remote metadata, download, ffmpeg, filesystem destination logic, persistence,
  settings, telemetry, updates, packaging, installer, or backend architecture.
- Do not add dependencies unless the pinned stack cannot express an explicit requirement. Prefer Kotlin stdlib, Compose,
  Jewel, current resource APIs, and the existing state holder.
- Keep Ready interaction state in the existing holder or one directly owned minimal state value. No DI, service/repository,
  fake backend, generic option framework, navigation model, or reusable design-system layer.
- Do not implement Downloading, Completed, Error presentation, Cancel, Retry, Open Folder, Download Another, progress, G2
  timers, G3 hardening, or final review packaging.

## Required IntelliJ MCP use

Use the running IntelliJ IDEA MCP server named `intellij`, not WebStorm, and pass
`C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call. Do not use the IDE terminal.

- Inspect current symbols/callers before changing `ProductSurface`, `DownloadStateHolder`, fixtures, events, or controller.
- Verify exact pinned signatures for Jewel `RadioButtonRow`, `ListComboBox`, `Link`, `DefaultButton`, and needed Compose
  `Image`/resource APIs from dependency sources before coding.
- Reformat every changed Kotlin/Kotlin Gradle file.
- Inspect every changed Kotlin file for errors and warnings.
- Run focused tests and IntelliJ `build_project`.
- Execute both `Downlet` and `Design Review`; normal launch must still open one product window.
- Keep JBR 25 runtime, Java 21 bytecode, dependencies, and run-configuration names unchanged.
- If IntelliJ synchronization fails, use the smallest Gradle-wrapper check through normal shell with RTK, then retry IntelliJ.

## Required Compose Hot Reload MCP proof

Use the running `Design Review` app. Target only the product window for product evidence.

- Call `status` and `list_windows`; record exact product/controller IDs.
- Force normal Ready and capture Light and Dark at 720x420, Light at 620x350, and one modestly larger Light size.
- Capture long title, missing thumbnail, long destination, disabled Download, and destination acknowledgement at the smallest
  size that proves each condition. Verify no controller pixels enter any product capture.
- Inspect Ready semantics for the YouTube link, Paste, grouped media identity, Video, Audio, selected quality, destination,
  Change, Download, selected/enabled state, and visible acknowledgement.
- Pointer-click and keyboard-activate Video/Audio, quality options, Change, and Download where the MCP supports native
  activation. Open the quality popup and choose at least one Video and one Audio option, including at minimum size.
- Verify Video defaults selected; Audio changes the quality label to `Best available — 251 kbps audio`; changing back resets
  to `Best available — 2160p`.
- Verify Change cycles deterministic destinations and keeps `Change…` reachable with the long path. Verify Download feedback
  and the disabled fixture without entering Downloading.
- Verify editing the URL from Ready removes stale media/options immediately and a new valid submission returns with defaults.
- Resize to exact 720x420, 620x350, and a modestly larger size; record client dimensions and clipping/scrollbar observations.
- Check `get_ui_error`, reload, and logs after interactions. No unexplained runtime failure may remain.
- Save transient captures under `build/design-review/ready-surface/`; do not commit them. Task 3.16 owns final G1 evidence.
- The current state-based Jewel text editor may stall whole-product semantic-tree serialization. Retry after reset/reload, then
  use direct control/subtree semantics if needed and report the exact remaining MCP limitation. Do not claim evidence not seen.
- Do not use Computer Use in this implementation task. Return any native-only proof gap to root.

## Checks

- Tasks 3.8-3.14 are complete and no G2/G3 UI or backend behavior appears.
- Ready contains exactly media identity, Video/Audio, quality, destination, and one Download primary action.
- Normal, compact, long-title, missing-thumbnail, long-destination, and disabled fixtures remain coherent.
- Native Jewel controls preserve focus, roles, selected/enabled state, keyboard behavior, and light/dark contrast.
- Tests cover the real Ready state transitions without a new framework or speculative architecture.
- IntelliJ inspections, formatting, build, focused tests, normal launch, and Design Review launch pass.
- Compose screenshots, semantics, interactions, resize checks, UI errors, and logs agree on one exact commit.
- OpenSpec strict validation passes before completion.

If a named Jewel/Compose API cannot compile at the pinned versions, stop and return exact source-backed evidence. Do not
change versions, add a substitute dependency, or approximate silently.

## Return contract

Return:

- commit SHA and changed files;
- OpenSpec task IDs completed and any task deliberately left pending;
- exact implementation choices for media resource/fallback, mode, quality, destination, Download feedback, compact metrics,
  and review fixtures;
- focused test names/results;
- IntelliJ symbol/source inspection, reformat, file-problem, build, and launch results;
- Compose status/window IDs, screenshot paths, semantic node names, interaction results, size table, UI-error result, and logs;
- exact limitation if any semantic or keyboard proof could not be obtained without Computer Use;
- bounded Impeccable findings and the one coherent fix pass applied, if any;
- OpenSpec strict-validation result;
- known issues or `none`;
- confirmation that no subagent, worktree, Computer Use, WebStorm, backend behavior, OpenAI image generation, or G2/G3 UI
  was used.

## Dispatch record

Top-level task ID: pending
