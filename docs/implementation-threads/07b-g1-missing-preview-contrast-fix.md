# Implementation Thread

Model: GPT-5.6 Sol
Reasoning: High
Base commit: `cc18c899977ad755ab46e1f93de519d2eeabdd38`
OpenSpec change: `design-primary-download-window`
Reopened task: `3.20`
Review task: `01a0498c-abad-7ab3-8b77-e31a8ff7d74b`
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Close the single P2 from the independent G1 revision review: make `Preview unavailable` use normal Jewel foreground contrast while preserving every other approved visual, interaction, timing, and layout decision.

## Required context

Read before editing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/implementation-threads/07-g1-modern-polish-revision.md`
- `docs/design/evidence/G1/g1-revision-review.md`
- all artifacts under `openspec/changes/design-primary-download-window/`
- current `Main.kt`, `DownloadState.kt`, `DownloadStateTest.kt`, build file, and run configurations

Use the repository-local `openspec-apply-change` skill. Use Impeccable product/polish guidance only to verify the correction; this packet grants no redesign authority.

## Finding to close

### P2 — missing-preview text contrast

At `Main.kt` near the missing-preview fallback, `JewelTheme.globalColors.text.info` produces approximately `2.89:1` contrast against the light fallback fill in the exact-commit `missing-preview-light-620x350.png`. The message is semantically present but visually too faint for the approved basic accessibility and clarity baseline.

## Required correction

- Keep the existing tonal fallback fill, rounded geometry, border, copy, semantics, typography weight, work plane, spacing, and sizes.
- Remove the explicit informational text-color override so `Preview unavailable` inherits the normal Jewel foreground from `JewelTheme.defaultTextStyle`; equivalently, use `JewelTheme.globalColors.text.normal` only if IntelliJ inspection proves inheritance is not preserved.
- Change no other product source or behavior.
- Add no dependency, token, helper, abstraction, or test for this one-line visual correction.
- Recheck only task `3.20` after every acceptance item passes. Leave task `3.21` for the root/reviewer to close.

## Scope boundaries

- No Paste/input/timing/focus/disabled-action/motion/layout/thumbnail changes.
- No backend, G2, G3, build-script, dependency, run-configuration, resource, controller, or broad formatting change.
- No Computer Use or WebStorm.

## Required IntelliJ MCP verification

Use IntelliJ MCP named `intellij`, never WebStorm, with `C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call.

- Inspect the exact Jewel text-style/color API before editing.
- Reformat `Main.kt` only if needed.
- Report all problems for `Main.kt`; zero unresolved errors or warnings required.
- Run `DownloadStateTest` and `build_project`.
- Launch `Downlet` and `Design Review`; confirm one and two windows respectively, then stop only the launched processes.

## Required Compose Hot Reload MCP verification

- Connect to the final-commit `Design Review` app and record product/controller window IDs.
- Force the missing-preview fixture at `620×350` in Light and Dark.
- Capture exact-commit screenshots to a task-specific visualization folder and visually confirm normal readable foreground contrast without changing geometry.
- Capture normal Ready Light once to prove no unrelated visual drift.
- Check product/controller UI errors and logs. Do not retry the known product semantic-tree stall.
- If screenshot capture samples an occluding surface, use robust exact-PID foregrounding and reject every false capture.

## Completion and return contract

After acceptance passes:

1. mark only task `3.20` complete again;
2. run strict OpenSpec validation;
3. commit the correction on shared `main` with a Conventional Commit message;
4. confirm a clean worktree.

Return:

- commit SHA and exact files changed;
- the final text-color choice and why it closes the P2;
- IntelliJ inspection/test/build/launch results;
- Compose window IDs, light/dark missing-preview and Ready screenshot paths, UI-error result, and log summary;
- strict OpenSpec result and confirmation only task `3.20` was rechecked;
- clean worktree status;
- confirmation that no subagent, worktree, Computer Use, WebStorm, unrelated visual change, backend, G2, or G3 scope was used.

## Dispatch protocol

Before any edit, commit, build, test, launch, or other mutation:

1. verify the release base commit and clean shared `main`;
2. read this packet and required committed context;
3. return `READY FOR DISPATCH RECORD` with exact task ID, full HEAD, branch, and clean status;
4. wait for the root task to record the task ID and send `RELEASE`.

## Dispatch record

Top-level task ID: `01a0499b-543f-7130-bd03-aa6cad3ce031`
