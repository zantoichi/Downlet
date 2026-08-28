# Implementation Thread

Model: GPT-5.6 Sol  
Reasoning: High  
Base commit: `21e400e84a0acfab9b29232de596851ce28892dd`  
OpenSpec change: `design-primary-download-window`  
Reopened tasks: `3.5`, `3.8`, `3.14`  
Review task: `01a04865-278b-7893-9bfb-2ccc102170fb`  
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Resolve the two bounded G1 integration-review findings without changing approved visuals or product behavior:

1. make the actual URL edit/debounce/resolution coroutine path executable under focused tests;
2. replace the suppressed deprecated thumbnail loader with generated Compose Resources accessors.

## Required context

Read before editing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/design/reviews/G0-direction/REVIEW.md`
- `docs/implementation-threads/03-g1-url-flow.md`
- `docs/implementation-threads/04-g1-ready-surface.md`
- all artifacts under `openspec/changes/design-primary-download-window/`
- current `Main.kt`, `DownloadState.kt`, `DesignReview.kt`, tests, build file, and resource directories
- the review findings quoted below

Use the repository-local `openspec-apply-change` skill. Load Impeccable because this remains a coded design gate, but this
task has no redesign authority: use it only to confirm visual parity after the code/test correction.

## Review findings to close

### Finding 1 — coverage illusion, tasks 3.5 and 3.14

The current test checks `linkSubmissionFor()` values only. It does not execute the production `snapshotFlow` /
`collectLatest` orchestration, one-shot paste intent, cancellation of a superseded 350 ms edit, or the 550 ms automatic
resolution completion path. Those behaviors can regress while all tests stay green.

### Finding 2 — suppressed deprecated resource API, task 3.8

`Main.kt` suppresses deprecation and uses `androidx.compose.ui.res.painterResource(String)` for the bundled thumbnail.
This greenfield G1 code should use generated Compose Resources accessors instead of hiding the warning.

## Required correction

### 1. Test the real edit and timing pipeline

- Extract only the smallest internal suspend helper(s) needed so `ProductSurface` still drives the same production behavior
  but tests can execute the actual flow collector and automatic-resolution delay.
- The production link collector must still consume the `snapshotFlow { linkFieldState.text.toString() }` stream with
  `collectLatest`, call the existing state holder, resolve paste edits immediately, and delay ordinary valid edits 350 ms.
- Preserve the one-shot paste-intent rule and its existing stale-intent timeout. Do not add a ViewModel, reducer, service,
  repository, coordinator class hierarchy, or new event bus.
- Preserve automatic fake resolution at exactly 550 ms and cancellation when state/edit changes make the pending completion
  stale.
- Add focused executable coroutine tests proving at minimum:
  - a valid paste edit enters Resolving without advancing virtual time;
  - paste intent is consumed once, so the next valid edit is manual/debounced;
  - a manual valid edit remains unresolved at 349 ms and resolves at 350 ms;
  - a newer edit cancels the prior pending debounce and resolves only after 350 ms from the newer edit;
  - automatic Resolving remains Resolving at 549 ms and becomes Ready at 550 ms;
  - cancelling the pending automatic-resolution job after a source edit cannot restore stale Ready content.
- Prefer the actual `TextFieldState` plus `snapshotFlow` in the focused test. If virtual-time support is needed, add only
  `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")`, matching the existing coroutine line. Add no
  production dependency or UI-test framework.

### 2. Migrate the thumbnail to Compose Resources

- Move the deterministic thumbnail to the standard Compose Resources drawable source set and load it through generated
  `Res.drawable` accessors with `org.jetbrains.compose.resources.painterResource`.
- Remove the deprecated `androidx.compose.ui.res.painterResource` import and the suppression.
- Keep the current local Jewel chevron resource/path handling unchanged unless compilation proves it is part of the same
  deprecation. It exists to satisfy Jewel `ListComboBox` styling, not the thumbnail API finding.
- Simplify the fixture seam if useful: one deterministic bundled thumbnail plus an explicit available/missing marker is enough.
  Do not introduce a generic image-loader abstraction, remote-loading seam, or resource registry.
- Preserve the exact 16:9 image, missing-preview fallback, grouped semantics, light/dark rendering, and current screenshots.

## Scope boundaries

- No visual redesign, copy change, layout change, new Ready option, controller expansion, G2/G3 state UI, or backend work.
- No network, remote image, yt-dlp, filesystem behavior, download logic, persistence, packaging, or dependency upgrades.
- Keep Java 21 bytecode, JBR 25 runtime, pinned Kotlin/Compose/Jewel/Hot Reload versions, and run configurations unchanged.
- Do not touch task 3.15 or later checkboxes. Recheck only tasks 3.5, 3.8, and 3.14 after their full acceptance is restored.

## Required IntelliJ MCP verification

Use IntelliJ MCP named `intellij`, never WebStorm, with
`C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call.

- Inspect exact callers before extracting coroutine helpers.
- Inspect generated Compose Resources API/package after the source-set move.
- Reformat every changed Kotlin/Kotlin Gradle file.
- Report all problems for every changed Kotlin/Kotlin Gradle file; zero unresolved errors/warnings required.
- Run the focused test configuration and `build_project`.
- Launch `Downlet` and `Design Review`; window counts and current Ready rendering must remain unchanged.

## Required Compose MCP parity check

- Connect to `Design Review`; record product/controller IDs.
- Capture normal Ready Light at 720x420 and missing-preview Ready Light at 620x350 from the final commit.
- Confirm the thumbnail/fallback, combo chevron, layout, copy, and controls are visually unchanged and contain no foreign pixels.
- Check UI errors and logs. Whole-product semantic-tree serialization may retain the known Jewel text-editor stall; report it
  precisely rather than claiming unavailable evidence.
- Do not use Computer Use.

## Return contract

Return:

- commit SHA and changed files;
- exact production helper/resource migration choices;
- focused test names with virtual-time checkpoints and PASS result;
- IntelliJ source inspection, formatting, file-problem, test, build, and launch results;
- Compose window IDs, two screenshot paths, UI-error result, and log summary;
- OpenSpec strict-validation result and confirmation tasks 3.5, 3.8, and 3.14 are rechecked;
- known issues or `none`;
- confirmation that visuals/behavior stayed unchanged and no subagent, worktree, Computer Use, WebStorm, backend, or G2/G3
  scope was used.

## Dispatch record

Top-level task ID: pending
