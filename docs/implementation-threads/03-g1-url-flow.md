# Implementation Thread

Model: GPT-5.6 Sol  
Reasoning: High  
Base commit: `a9e7c5bd35f72ffb4368e0f98efb979a26214d02`  
OpenSpec change: `design-primary-download-window`  
Tasks: `3.2`, `3.3`, `3.4`, `3.5`, `3.6`  
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Build the smallest complete URL tranche for G1: one persistent YouTube-link row, local validation, Windows clipboard Paste,
paste-immediate/manual-debounced fake submission, and quiet Empty/Resolving bodies. Do not build Ready media or options.

## Required context

Read these before editing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/design/reviews/G0-direction/REVIEW.md`
- `docs/design/reviews/G0-direction/CODABILITY.md`
- `docs/design/evidence/G1/foundation-window-proof.md`
- `openspec/changes/design-primary-download-window/proposal.md`
- `openspec/changes/design-primary-download-window/design.md`
- `openspec/changes/design-primary-download-window/tasks.md`
- all specs under `openspec/changes/design-primary-download-window/specs/`
- current `Main.kt`, `DownloadState.kt`, `DesignReview.kt`, and focused tests

Use the repository-local `openspec-apply-change` skill. Load the installed `impeccable` skill before UI edits. The approved
G0 direction, The Quiet Transfer Desk, is the confirmed shape: do not generate alternatives, rerun shape, or redesign it.
Use Impeccable only for a bounded task preflight and production-craft check against the approved direction.

## Starting baseline

- Foundation tasks 2.5-2.9 pass at the base commit.
- The product is a Jewel `DecoratedWindow` with matching Light/Dark title bars, 720×420 target bounds, and 620×350 minimum.
- `DownloadStateHolder` owns explicit product state and deterministic fixtures. The product body is still a state-label placeholder.
- `Design Review Controller` can force Empty/Ready and Light/Dark.
- IntelliJ run configurations and Compose Hot Reload MCP are working.
- Keep all pinned versions, JVM target 21, and JBR 25 unchanged.

## Required implementation order

### 1. Persistent link row, task 3.2

- Replace the product placeholder with one stable content column.
- Keep a visible `YouTube link` label, Jewel's state-based `TextField(TextFieldState, ...)`, placeholder
  `Paste a YouTube link…`, and Jewel `OutlinedButton` labeled `Paste`.
- The row stays visible in Empty, Resolving, and the current Ready placeholder.
- Use a compact fixed form-label width and let the field take remaining width. Prove the row at 720×420 and 620×350.
- Preserve Jewel focus, keyboard, disabled, typography, metrics, and semantics. Do not build a custom text editor.
- Do not add Enter, Analyze, URL-history, clear-icon, provider selector, advanced options, or decorative iconography.

### 2. URL validation, task 3.3

- Add one small pure stdlib predicate using `java.net.URI`.
- Accept only parseable `http` or `https` URLs whose normalized host is exactly `youtube.com`, `www.youtube.com`,
  `m.youtube.com`, or `youtu.be`.
- Reject blank, malformed, unsupported-scheme, and unsupported-host values. Do not use network validation, redirects,
  provider-general parsing, regex-only parsing, or a URL library.
- Invalid text remains editable, starts no resolution, and shows exactly `Enter a valid YouTube link.` below the field.
- Keep validation geometry restrained so the body does not jump. Add targeted status semantics; do not make color the message.
- Add the smallest table-driven tests covering every allowed host plus malformed and unsupported cases.

### 3. Windows clipboard Paste, task 3.4

- Keep platform clipboard access at the application edge. No AWT type may enter the product composable or state model.
- Use `Toolkit.getDefaultToolkit().systemClipboard` and string flavor through one tiny callback/function.
- Catch clipboard unavailable, busy, unsupported, non-text, headless, and security failures so Paste never crashes.
- Valid clipboard text updates the field and enters Resolving immediately. Invalid text remains editable and shows validation.
- Empty/unreadable clipboard content leaves the current product stable and starts no resolution.
- Do not add clipboard history, permission UI, polling, persistence, logging of clipboard contents, or a clipboard abstraction hierarchy.

### 4. Paste intent and debounce, task 3.5

- Use Compose key preview to record Ctrl+V intent before the state-based field applies its next edit.
- Observe `TextFieldState.text` with the smallest `snapshotFlow`/`LaunchedEffect` coroutine loop that is correct.
- The next valid Ctrl+V edit resolves immediately; clear paste intent after that one edit.
- Other valid edits resolve only after 350 ms idle. Any newer edit cancels the pending debounce or in-flight fake resolution.
- Enter is unnecessary. Editing a prior Ready/Resolving URL must not leave stale resolved content paired with the new text.
- Keep one short-lived intent flag; do not add a reducer, stream framework, ViewModel, service, repository, or fake backend.
- Leave one runnable focused check for paste-immediate versus type-debounced timing/intent behavior.

Compose MCP currently has no documented key-chord action. Do not silently claim `type_text` proves Ctrl+V. Prove the pure
intent/timing path in tests and the Paste-button immediate path in Compose. If no non-Computer-Use field-paste route exists,
record that exact limitation for root and leave only the native Ctrl+V interaction proof to the later integrated G1 evidence task.

### 5. Empty and Resolving bodies, task 3.6

- Empty shows exactly `Paste a YouTube link to choose video or audio.` below the persistent row.
- Empty shows no media, format, quality, destination, progress, or download controls.
- Resolving preserves the submitted link and shows Jewel `CircularProgressIndicator` with
  `Checking this YouTube link…` for exactly 550 ms before entering the normal Ready fixture.
- Keep body geometry stable and activity quiet. No skeletons, cards, illustrations, animation system, or horizontal progress bar.
- Add the smallest review-only way to force Resolving instantly if needed for deterministic Compose capture. Normal launch
  must expose no controller UI, and controller-forced state must bypass timers.
- The Ready body remains a minimal placeholder in this tranche; tasks 3.8-3.13 own media and choices.

## Product and Impeccable constraints

- Primary user: a nontechnical Windows user downloading YouTube audio or video locally.
- Register: compact native desktop utility, not a marketing surface, SaaS dashboard, or IDE.
- Use one flat root surface and one stable column. State change supplies interest; decoration does not.
- Use Jewel controls, semantic colors, typography, metrics, focus, and accessibility behavior before custom styling.
- No cards, nested panels, gradients, glass, pills, giant headings, toolbar, tabs, sidebar, project stripe, or decorative icons.
- Basic accessibility is mandatory: logical focus order, visible focus, meaningful names, inline validation semantics,
  sufficient contrast, and status communication that does not rely on color alone.
- Keep visible copy ordinary and exact. Do not expose `yt-dlp`, extractor, process, codec, or backend terminology.

## Architecture and scope boundaries

- DESIGN ONLY: no yt-dlp, subprocess, network, download, ffmpeg, filesystem destination logic, persistence, settings,
  telemetry, updates, packaging, or backend architecture.
- Do not add dependencies unless the existing pinned stack cannot express an explicit requirement. Prefer stdlib, Compose,
  Jewel, AWT clipboard edge code, and already-resolved coroutines.
- Reuse the existing state holder and fixtures. Change the shared model only where the persistent field and valid state
  transitions require it.
- Do not implement Ready media identity, Video/Audio, quality, destination, Download, G2 states, or final hardening.

## Required IntelliJ MCP use

Use the IntelliJ IDEA MCP server named `intellij`, not WebStorm, and pass
`C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call. Do not use the IDE terminal.

- Inspect current symbols/callers before changing `ProductWindow`, `ProductSurface`, `DownloadStateHolder`, or events.
- Verify the exact pinned Jewel signatures for state-based `TextField`, `OutlinedButton`, and `CircularProgressIndicator`
  from dependency sources before coding.
- Reformat every changed Kotlin/Kotlin Gradle file.
- Inspect every changed Kotlin file for errors and warnings.
- Run focused tests and IntelliJ `build_project`.
- Execute `Design Review`; normal `Downlet` launch must still open one product window.
- Keep JBR 25 runtime and Java 21 bytecode unchanged.
- If IntelliJ synchronization fails, use the smallest Gradle-wrapper check through normal shell with RTK, then retry IntelliJ.

## Required Compose Hot Reload MCP proof

Use the running `Design Review` app and target only the product window for product evidence.

- Call `status` and `list_windows`; record exact product/controller IDs.
- Verify Empty in Light and Dark at 720×420.
- Verify the link row at 720×420 and 620×350 with semantic names for label, field, Paste, validation, and status.
- Type invalid text; confirm it stays editable, validation appears, and state does not resolve.
- Prepare valid and invalid clipboard text through a normal non-UI shell method, activate Paste, and record immediate
  Resolving/validation behavior without exposing clipboard content in logs.
- Type a valid URL; verify it does not resolve before the 350 ms idle boundary, then reaches Resolving and Ready.
- Capture deterministic Resolving through the review controller if the 550 ms live state is too short for reliable capture.
- Check `get_ui_error` and logs after interactions and reload.
- Save transient captures under `build/design-review/url-flow/`; do not commit them. Task 3.16 owns final G1 evidence.
- Do not use Computer Use in this implementation task. Return any exact proof gap to root.

## Checks

- Tasks 3.2-3.6 behavior is present and no out-of-tranche Ready/G2 UI appears.
- Allowed-host validation is pure, deterministic, and tested.
- Paste failures are non-crashing; clipboard data is never logged or persisted.
- Paste intent clears after one edit; debounce and resolution jobs cancel on newer edits/reset.
- Empty and Resolving use fixed copy and stable geometry in both themes.
- Default/minimum sizes remain usable with no clipped essential control.
- IntelliJ inspections, formatting, build, focused tests, normal launch, and Design Review launch pass.
- Compose screenshots, semantics, timing interactions, UI errors, and logs agree on one exact commit.
- OpenSpec strict validation passes before completion.

If a named Jewel/Compose API cannot compile at the pinned versions, stop and return exact source-backed evidence. Do not
change versions or approximate silently.

## Return contract

Return:

- commit SHA and changed files;
- OpenSpec task IDs completed and any task deliberately left pending;
- exact implementation choices for field state, validation, clipboard edge, paste intent, debounce, and 550 ms transition;
- focused test names/results;
- IntelliJ symbol/source inspection, reformat, file-problem, build, and launch results;
- Compose status/window IDs, screenshot paths, semantic node names, timing observations, UI-error results, and log summary;
- exact limitation if native Ctrl+V could not be proven without Computer Use;
- OpenSpec strict-validation result;
- known issues or `none`;
- confirmation that no subagent, worktree, Computer Use, backend behavior, or out-of-scope Ready UI was used.

## Dispatch record

Top-level task ID: `01a04805-e329-7572-8198-af4a7120644d`
