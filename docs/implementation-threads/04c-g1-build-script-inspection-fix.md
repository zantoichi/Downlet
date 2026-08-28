# Implementation Thread

Model: GPT-5.6 Sol  
Reasoning: High  
Base commit: `6e68d19a39a96327adbf9a9afc6e2cbe7d5ec42a`  
OpenSpec change: `design-primary-download-window`  
Reopened task: `3.14`  
Review task: `01a04865-278b-7893-9bfb-2ccc102170fb`  
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Remove the file-wide Gradle inspection suppression introduced by the G1 test/resource correction. Keep the same toolchain,
resource generation, runtime behavior, tests, and visuals.

## Required context

Read before editing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/implementation-threads/04b-g1-integration-fixes.md`
- all artifacts under `openspec/changes/design-primary-download-window/`
- current `build.gradle.kts`
- review finding below

Use the repository-local `openspec-apply-change` skill. This is build-script hygiene only; no Impeccable or visual redesign
work is needed because product source/resources and accepted screenshots must remain untouched.

## Review finding to close

### P3 — overbroad inspection suppression, task 3.14

`build.gradle.kts` suppresses `UnstableApiUsage` and `UsePropertyAccessSyntax` across the entire script. That can hide future
unrelated Gradle regressions and weakens the zero-warning inspection gate.

## Required correction

- Remove the file-level `@file:Suppress` declaration entirely.
- Preserve Kotlin `2.3.20`, Compose `1.11.0`, Hot Reload `1.2.0`, Jewel `0.39.1-262.9437.29`, Java 21 bytecode, JBR 25
  runtime selection, Compose resource generation, and `kotlinx-coroutines-test:1.11.0` exactly.
- Use property syntax for the JavaExec executable only if both Kotlin DSL compilation and the `Downlet` / `Design Review`
  runs prove it valid under Gradle `9.7.1`. If this API's overloaded setters make property assignment compile-invalid, keep
  `setExecutable(...)` and place `@Suppress("UsePropertyAccessSyntax")` on only that expression or the smallest enclosing
  block. Report the exact compiler evidence; do not restore a file-level suppression.
- Place `@Suppress("UnstableApiUsage")` only on the smallest expressions or blocks that IntelliJ proves require it. Do not
  suppress unrelated warnings and do not change vendor-selection behavior to avoid the warning.
- Change only `build.gradle.kts` and this task checkbox unless a generated file changes solely from validation. Do not touch
  Kotlin product/test source, resources, screenshots, run configurations, approved UI, backend, G2, or G3.
- Recheck only task `3.14` after every acceptance item passes. Do not touch task `3.15` or later.

## Required IntelliJ MCP verification

Use IntelliJ MCP named `intellij`, never WebStorm, with
`C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call.

- Reformat `build.gradle.kts`.
- Report all problems for `build.gradle.kts`; zero unresolved errors or warnings required without file-wide suppression.
- Run `build_project`.
- Run the `DownloadStateTest` configuration and confirm exit `0`.
- Launch `Downlet` and `Design Review`; confirm one and two windows respectively, then stop only the launched processes.
- Run strict OpenSpec validation and confirm progress returns to `28/61` with only task `3.14` rechecked.

## Return contract

Return:

- commit SHA and exact files changed;
- final suppression placement and why each remaining narrow suppression is unavoidable;
- IntelliJ formatting, file-problem, build, test, and both launch results;
- strict OpenSpec result and confirmation only task `3.14` was rechecked;
- clean worktree status;
- confirmation that no subagent, worktree, Computer Use, WebStorm, visual/source/resource change, backend, G2, or G3 scope
  was used.

## Dispatch record

Top-level task ID: pending
