# G2 Review Corrections

Model: GPT-5.6 Sol  
Reasoning: High  
Planning base: `2516e1710fcc5c9f42e46fcf9b0db1c315254488`  
Expected dispatch HEAD: the exact clean commit containing this packet, supplied in the launch prompt  
OpenSpec change: `design-primary-download-window`  
Tasks: reopened `4.2`, `4.3`, `4.3a`, `4.7`, `4.9`, plus `4.11`  
Review task: `01a04b8b-9efe-7be0-a029-ef182814c326`  
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Close the four accepted findings in `docs/design/reviews/G2-state-system/IMPLEMENTATION_REVIEW.md` in one coherent correction pass. Preserve every other approved G1/G2 behavior and visual decision.

Stop after one clean correction commit and the required focused verification. Do not run another independent review, capture canonical G2 evidence, create the G2 package, or start G3.

## Gate before mutation

Before editing, building, testing, launching, or generating anything:

1. verify branch `main`;
2. verify HEAD exactly matches the dispatch commit supplied by the root prompt;
3. verify the shared worktree is clean;
4. stop and report the exact mismatch if any check differs.

No READY/RELEASE handshake is required when the exact clean dispatch HEAD matches.

## Required context

Read before editing:

- repository and global `AGENTS.md` instructions;
- this packet;
- `docs/design/reviews/G2-state-system/IMPLEMENTATION_REVIEW.md`;
- `docs/implementation-threads/05-g2-state-system.md`;
- `PRODUCT.md`, `DESIGN.md`, and the G1 review package;
- the proposal, design, tasks, and capability specs under `openspec/changes/design-primary-download-window/`;
- `docs/design/concepts/G2/SELECTION.md`;
- the current state holder, UI composition, focused tests, smoke test, and Compose resources.

Use the repository-local `openspec-apply-change` skill. Use Impeccable only for the two bounded visual/API corrections. The selected generated-resource study is already committed; do not invoke image generation or create a new direction.

Use the running IntelliJ MCP server named `intellij`, never WebStorm, with `C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call. Use normal shell plus RTK for noisy commands. Use `apply_patch` for edits. Do not use Computer Use.

## Required correction 1 — stale progress boundary

Owning tasks: `4.2`, `4.9`.

- Add the smallest monotonic download-generation token that makes progress events belong to one specific download run.
- Capture the generation in every scheduled progress event and reject mismatches before mutation.
- Invalidate the generation on link edit, Cancel, Retry replacement, Reset/Download Another, every forced state, completion, error, and holder close.
- Serialize machine event submission with the smallest coroutine-native mechanism. Do not add a dispatcher abstraction, machine wrapper, actor, channel, repository, service, or second state engine.
- Preserve the exact fake timeline, resolution behavior, controller fixtures, and selection/destination retention.
- Add one deterministic regression that proves a same-fixture forced Downloading state cannot be overwritten by progress from the prior run under production-like scheduling. Use the smallest test seam only if scheduling cannot be controlled through the existing holder API.

Acceptance:

- stale queued progress cannot cross a force/cancel/reset/edit/close boundary;
- normal and retry downloads still follow `0/18/43/68/87/100` every `350 ms`;
- all existing state tests and smoke flows pass without flaky sleeps.

## Required correction 2 — reveal the shared work plane once

Owning task: `4.3`.

- Keep one `WorkPlane` instance for Ready, Downloading, Completed, and Error.
- Animate only the transition from Empty/Resolving into the work-plane family with the approved 200 ms fade/rise.
- When moving among later states, update only the inner state/action region; do not fade, rise, resize, or recreate the plane shell.
- Keep the URL field, window geometry, media identity, compact behavior, and existing action hierarchy fixed.
- Reuse current components. Add no animation framework, design-token layer, duplicate shell, or decorative effect.

Acceptance:

- Empty has no work plane;
- Resolving has no work plane or premature controls;
- Ready reveals once;
- Ready → Downloading → Completed/Error changes content in place with no shell replay.

## Required correction 3 — use Jewel banner actions

Owning task: `4.7`.

- Inspect the pinned Jewel `InlineErrorBanner` signature in IntelliJ/dependency source.
- Move Retry into the native `linkActions` slot.
- Keep only `Check that the YouTube link is available and try again.` in the banner body.
- Preserve the exact title, full-width layout, polite error semantics, and Retry behavior.

Do not hand-roll banner action layout or add a wrapper.

## Required correction 4 — restore the selected missing-preview direction

Owning task: `4.3a`.

- Change visible fallback copy to `Preview unavailable`; allow two compact lines when needed.
- Redraw `preview_unavailable.svg` as a simple monochrome frame-and-signal motif derived from the committed selection note, not a slashed landscape/broken-image icon.
- Keep the resource theme-tintable, text-free, bounded, and dependency-free.
- Verify the mark at 96 and 128 dp in light and dark without committing a persistent gallery or preview harness. Also verify the actual product fallback at 620×350 and 720×420.
- Preserve 16:9 geometry, border, fill, semantics, and the deterministic thumbnail.

Do not generate another image, add branding, gradients, glass, noise, or a second icon family.

## Verification boundary

Run once after the complete correction:

1. reformat changed Kotlin/Kotlin Gradle files in IntelliJ;
2. IntelliJ problems for every changed source/test/resource reference/OpenSpec file: zero unresolved errors or warnings;
3. focused `DownloadStateTest`;
4. `gradlew.bat smokeTest`;
5. `gradlew.bat check`;
6. IntelliJ `build_project`;
7. launch `Downlet` and `Design Review` on JBR 25, then stop only task-owned processes;
8. one bounded Compose Hot Reload affected-state pass: Ready → Downloading → Completed, failure Error → Retry, missing preview light/dark at 620×350 and 720×420, UI errors, and logs.

Attempt no whole-product semantic tree that has already shown the known Hot Reload stall. Use targeted/controller semantics only where needed. Store correction-only transient captures under `build/design-review/g2-corrections/`; they are not canonical G2 evidence.

## Scope boundaries

- No unrelated refactor, dependency/version/build-script change, backend, network, subprocess, filesystem action, packaging, G3 work, new fixture family, or broad evidence recapture.
- No new abstraction beyond the smallest generation/serialization mechanism required for correctness.
- No arbitrary file splitting or file-line gate.
- No full design pass, new generated assets, or second correction loop.

## Completion and return contract

After all acceptance checks pass:

1. mark reopened tasks `4.2`, `4.3`, `4.3a`, `4.7`, and `4.9` complete again;
2. mark task `4.11` complete with the implementation task ID and correction commit;
3. leave tasks `4.12+` pending;
4. run strict OpenSpec validation;
5. commit all corrections on shared `main` with a Conventional Commit message;
6. confirm the worktree is clean.

Return:

- full commit SHA and exact files changed;
- concise mapping from each review finding to its fix;
- generation/serialization choice and regression-test behavior;
- IntelliJ inspection, test, smoke, check, build, and launch results;
- Compose window IDs, transient capture paths, UI-error result, and log summary;
- strict OpenSpec result and exact tasks rechecked;
- clean worktree status;
- confirmation that no subagent, worktree, Computer Use, WebStorm, new image generation, unrelated redesign/refactor, backend, canonical evidence, or G3 scope was used.
