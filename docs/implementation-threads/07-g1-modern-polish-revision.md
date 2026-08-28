# Implementation Thread

Model: GPT-5.6 Sol
Reasoning: High
Accepted G1 package base: `d80502ec57e4e1975ce180accab9ed2ea03ebe6d`
Release base commit: supplied by the root task in `RELEASE` after the dispatch record is committed
OpenSpec change: `design-primary-download-window`
Assigned task: `3.20`
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Implement the user-approved G1 revision: remove the redundant visible Paste action and make Empty/Ready feel substantially more modern, polished, and refined through one restrained tonal work plane, stronger hierarchy, and subtle purposeful motion. Preserve the small native Windows utility character and all fake-only boundaries.

## Required context

Read before editing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/design/reviews/G0-direction/REVIEW.md`
- `docs/design/reviews/G1-core-surface/REVIEW.md`
- `docs/design/reviews/G1-core-surface/EVIDENCE.md`
- `docs/design/evidence/G1/impeccable-review.md`
- all artifacts under `openspec/changes/design-primary-download-window/`
- current `Main.kt`, `DownloadState.kt`, `DesignReview.kt`, `DownloadStateTest.kt`, build file, resources, and run configurations

Use the repository-local `openspec-apply-change` skill. Use Impeccable in product mode with its product, polish, and animate references. The user has approved the shape below, so begin mutation only after reporting:

`IMPECCABLE_PREFLIGHT: context=pass product=pass command_reference=pass shape=pass image_gate=skipped:existing-coded-screenshots-and-live-harness-are-the-visual-probes mutation=open`

## Approved product decisions

- Remove the visible `Paste` button. The link field fills the freed width.
- Delete the AWT clipboard callback/imports and `pasteLink` state-holder method if caller inspection proves they are unused after the button removal.
- Do not read or monitor the clipboard proactively. Native Windows text-field paste is the only clipboard behavior.
- Preserve `Ctrl+V` intent detection so a valid pasted edit enters Resolving immediately.
- Preserve the `350 ms` debounce for an ordinary typed valid edit, the `550 ms` fake resolution completion, one-shot/stale paste-intent handling, and cancellation behavior.
- Keep no Analyze action and no Enter requirement.
- Empty copy is `Paste or type a YouTube link. Downlet checks it automatically.`
- Request focus for the YouTube-link field on initial Empty and whenever Reset/Download Another later returns to Empty. Do not steal focus while the user is editing or when Ready is shown.
- The disabled-action fixture shows `Download is unavailable for this item.` in the existing feedback/status area while keeping Jewel's native disabled control treatment.

## Approved visual direction

- Keep one stable column and the existing 720×420 default / 620×350 minimum behavior.
- Add one inset work plane for the changing state body. It uses a low-chroma cool theme-aware fill, a thin boundary, modest corner radius, and no decorative shadow.
- The work plane is the only new grouping surface. Do not create a card grid, nested cards, tiles, hero block, toolbar, sidebar, tabs, or settings surface.
- Strengthen Empty/Ready hierarchy through spacing, typography, alignment, and one existing Jewel accent role. Keep light and dark modes equally deliberate.
- Clip the 16:9 preview to a modest rounded rectangle with a thin theme-aware boundary.
- Make the missing-preview fixture visibly intentional: same geometry, distinct tonal fill, and concise `Preview unavailable` copy with explicit semantics.
- Keep native Jewel radio rows, combo box, links, buttons, typography, focus, and disabled behavior. Do not imitate controls.
- No glassmorphism, neon, gradient text, gratuitous gradients, giant radii, pill-shaped everything, or generic AI-dashboard styling.

## Approved motion

- Use the smallest native Compose animation API that fits the existing product surface. Do not add a dependency or generic animation framework.
- State-body replacement uses one `180–220 ms` fade plus at most `6.dp` of vertical rise with `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`.
- Keep the persistent URL field and window geometry fixed during the transition.
- No bounce, spring overshoot, staggered choreography, infinite decorative loop, or motion-delayed interaction.
- Motion supplements persistent status text; it is never the only status signal.
- Respect standard Compose duration scaling. A zero duration scale must yield the same final UI immediately.

## Scope boundaries

- DESIGN ONLY. No yt-dlp, network, subprocess, ffmpeg, filesystem product behavior, destination picker, folder opening, persistence, telemetry, updates, packaging, or backend architecture.
- No G2 Downloading/Completed/Error implementation and no G3 scope.
- No new dependency, token framework, generic component library, ViewModel, repository, service, factory, event bus, or broad refactor.
- Reuse current Jewel/Compose primitives and existing state flow. Delete obsolete clipboard code instead of replacing it with another abstraction.
- Preserve pinned toolchain versions, generated-resource access, run configurations, deterministic fixtures, and the developer-only controller.
- Change only files required by task `3.20` plus its focused evidence and checkbox. Mark no other task complete.

## Tests

Retain all 17 existing tests and add only the smallest checks needed for changed logic. At minimum prove:

- the removed button/AWT path leaves native paste-immediate behavior intact;
- manual typing remains unresolved at 349 ms and resolves at 350 ms;
- automatic resolution remains Resolving at 549 ms and becomes Ready at 550 ms;
- one-shot/stale paste intent and superseded work still cancel correctly;
- any extracted copy/reason logic is deterministic if it contains non-trivial branching.

Do not add a screenshot-golden framework or broad UI-test dependency.

## Required IntelliJ MCP use

Use IntelliJ MCP named `intellij`, never WebStorm, with `C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call.

- Inspect callers of the clipboard callback and `pasteLink` before deleting them.
- Inspect current Jewel/theme/animation APIs before choosing colors or transition primitives.
- Reformat every changed Kotlin/Kotlin Gradle file.
- Report all problems for every changed Kotlin/Kotlin Gradle file; zero unresolved errors or warnings required.
- Run the focused `DownloadStateTest` configuration and `build_project`.
- Launch `Downlet` and confirm one product window.
- Launch `Design Review` and confirm one product plus one controller window.
- Stop only processes launched by this task.

## Required Compose Hot Reload MCP use

- Connect to `Design Review` and record product/controller window IDs.
- Exercise native `Ctrl+V`, manual typing, Video/Audio, quality, destination Change, Download, Reset, missing-preview, and disabled-action fixtures where G1 currently supports them.
- Capture at minimum Empty Light 720×420, Ready Light 720×420, Ready Dark 720×420, Ready Light 620×350, missing preview 620×350, and disabled Download 620×350 from the final implementation commit.
- Inspect transition behavior at normal and zero duration scale when the exposed tooling permits it; otherwise report the exact code/inspection evidence and limitation.
- Check relevant semantics, `get_ui_error`, and logs. The known Jewel text-editor semantic-tree timeout must be reported precisely rather than retried repeatedly or mislabeled as success.
- Do not use Computer Use for this implementation task.

## Completion and return contract

After all acceptance checks pass:

1. mark only task `3.20` complete;
2. run strict OpenSpec validation;
3. commit all task changes on shared `main` using a Conventional Commit message;
4. confirm the worktree is clean.

Return:

- commit SHA and exact files changed;
- concise explanation of deleted Paste/AWT code and preserved URL timing behavior;
- concise explanation of the final tonal, hierarchy, preview, fallback, disabled, focus, and motion choices;
- exact focused test names/result, IntelliJ inspections/format/build/run results, and strict OpenSpec result;
- Compose window IDs, interaction results, screenshot paths, semantic result, UI-error result, and log summary;
- known issues or `none`;
- confirmation that only task `3.20` was checked and no subagent, worktree, Computer Use, WebStorm, backend, G2, or G3 scope was used.

## Dispatch protocol

Before any edit, commit, build, test, launch, or other mutation:

1. verify the exact RELEASE base commit and clean shared `main`;
2. read this packet and the required committed context;
3. return `READY FOR DISPATCH RECORD` with the exact task ID, HEAD, branch, and clean status;
4. wait for the root task to record the task ID and send `RELEASE` with the dispatch-record commit SHA.

## Dispatch record

Top-level task ID: `01a0496b-1c81-7b03-982f-fec33d4ed76a`
