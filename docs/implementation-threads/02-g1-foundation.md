# Implementation Thread

Model: GPT-5.6 Sol
Reasoning: High
Base commit: `cd5a373`
OpenSpec change: `design-primary-download-window`
Tasks: `2.5`, `2.6`, `2.7`, `2.8`, `2.9`
Thread type: top-level Codex task, no subagents

## Goal

Build the smallest G1 product foundation: the real window shell and theme source, immutable fake state foundation, a
separate Design Review Controller, shared IntelliJ run configurations, and a working project-local Compose Hot Reload
MCP loop.

## Required context

Read these before editing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/design/reviews/G0-direction/REVIEW.md`
- `docs/design/reviews/G0-direction/CODABILITY.md`
- `openspec/changes/design-primary-download-window/proposal.md`
- `openspec/changes/design-primary-download-window/design.md`
- `openspec/changes/design-primary-download-window/tasks.md`
- all specs under `openspec/changes/design-primary-download-window/specs/`

Use the repository-local `openspec-apply-change` skill. Use the installed `impeccable` skill before UI edits. G0 approval
is the confirmed shape brief, so do not rerun shape, generate alternatives, or redesign The Quiet Transfer Desk.

## Pre-staged MCP configuration

The orchestrator added `.codex/config.toml` before dispatch so this new task can load the project-local
`compose_hot_reload` MCP server at task startup. This is tool bootstrap only. Task 2.9 still owns proving the
`hotMcpServer` task, connection, tools, and running-app evidence. Make only the smallest config correction if the checked-in
entry is invalid.

Start with both checks:

1. IntelliJ MCP resolves this exact worktree when `projectPath` is passed.
2. Compose Hot Reload MCP tools are present. `status` may report disconnected before the review app launches, but the
   tool itself must be callable.

If the Compose MCP namespace is absent, stop before Kotlin edits and return the exact startup/configuration evidence. Do
not replace it with a custom MCP client or claim shell output as Compose MCP evidence.

## Required implementation order

### 1. Product shell and theme source, task 2.5

- Replace the smoke-only body with the normal product shell.
- Keep Jewel `DecoratedWindow`, decorated `IntUiTheme`, and a plain `TitleBar` containing only `Downlet` plus standard
  Windows controls.
- Create a `WindowState` near 720 by 420 with a usable minimum near 620 by 350.
- Normal launch reads `isSystemInDarkTheme()` once at startup and falls back to light if unavailable.
- A review-only explicit Light or Dark override must change the client and Jewel title bar together without recreating
  product state.
- Add only the root surface and minimum placeholder needed to prove the shell. Do not build the URL row or Ready layout.

### 2. State foundation, task 2.6

- Implement immutable product state, events, deterministic fixtures, and one tiny Compose-aware state holder.
- All six states must be representable: Empty, Resolving, Ready, Downloading, Completed, Error.
- Prevent invalid combinations where a small sealed model does so directly.
- Include normal, long-title, missing-thumbnail, long-destination, and failure fixtures.
- Add the smallest focused tests for state invariants or branching logic.
- Do not add repositories, services, interfaces, dependency injection, fake backend layers, or real I/O.

### 3. Design Review Controller, task 2.7

- Add a separate developer entry point that opens two distinct Jewel windows: the product and `Design Review Controller`.
- The controller forces Empty or Ready and explicit Light or Dark instantly.
- The controller uses ordinary labeled Jewel controls and stays visually separate from the product.
- Normal launch opens only the product window and contains no controller code path in its visible UI.

### 4. Shared IntelliJ configurations, task 2.8

- Add shared `Downlet` and `Design Review` run configurations under `.run/`.
- `Downlet` opens one normal product window.
- `Design Review` opens product plus controller through Compose Hot Reload so the MCP server connects to the exact app.
- Use Gradle tasks and the existing wrapper. Do not add packaging or OS shortcuts.
- List and execute both configurations with IntelliJ MCP.

### 5. Compose Hot Reload MCP, task 2.9

- Verify `hotMcpServer` is registered by Compose Hot Reload 1.2.0.
- Launch `Design Review` through IntelliJ MCP.
- Call Compose MCP `status` and `list_windows`.
- Identify the product window by title or stable returned ID. Never capture the controller as product evidence.
- Prove the available screenshot, semantic-tree, interaction, resize, UI-error, and log tools against the running app.
- Use controller semantics and MCP interactions to switch Empty or Ready and Light or Dark.
- Resize the product window to 720 by 420 and 620 by 350.
- Save temporary client-area PNG exports under `build/design-review/foundation/` with deterministic names containing
  state, theme, and size. Do not commit these transient captures; task 3.16 owns final G1 evidence.
- Call `get_ui_error` and `get_logs` after interaction and capture.

Compose MCP is the primary visual iteration loop. It is authoritative for client-area rendering, window bounds, semantics,
interactions, reload state, errors, and logs. It does not prove Jewel title chrome.

## Native frame proof boundary

Do not use Computer Use in this implementation task. The user temporarily paused it, and the root orchestrator owns the
same-commit full-window proof after integration. Return the exact product launch method and window title so root can
activate the Downlet HWND before `Windows.Graphics.Capture`.

Leave task 2.5 unchecked if its native-frame acceptance is the only missing item. The orchestrator will capture the frame,
record controls/drag/maximize behavior, and then check 2.5. Tasks 2.6 through 2.9 may be checked when their own acceptance
criteria pass.

## UI constraints

- Product register: Windows desktop utility, not a marketing surface.
- Use Jewel controls, typography, focus behavior, metrics, and semantic colors before any custom styling.
- Keep one stable column and flat root surface.
- No project stripe, menu, toolbar, sidebar, tabs, cards, gradients, glass, pills, giant headings, decorative icons, or
  Material substitutions.
- No URL row, media identity, quality selector, destination row, or Download action in this tranche.
- Add no animation beyond platform behavior.
- Basic accessibility remains mandatory: meaningful window/control names, logical focus order, visible focus, sufficient
  contrast, and state labels that do not rely on color alone.

## Required IntelliJ MCP use

Use the IntelliJ IDEA MCP server named `intellij`, not WebStorm, and pass the exact worktree `projectPath` on every call.
Do not use the IDE terminal or Windows UI automation for IntelliJ.

- Inspect modules, dependencies, and existing run configurations before editing.
- Inspect symbols and callers before changing the shared application entry flow.
- Reformat every changed Kotlin and Kotlin Gradle file.
- Inspect every changed Kotlin file for errors and warnings.
- Run focused tests and `build_project` after edits.
- List and execute both shared run configurations.
- Verify JBR 25 runtime and Java 21 bytecode remain unchanged.
- If IntelliJ cannot synchronize, run the smallest relevant Gradle-wrapper command through the normal shell, then retry
  IntelliJ. Wrap noisy commands with RTK.

## Checks

- The exact pinned stack remains unchanged: Kotlin 2.3.20, Compose 1.11.0, Jewel 0.39.1-262.9437.29, Hot Reload 1.2.0,
  Kotlin JVM target 21, Java release 21, JBR 25.
- Gradle build and focused tests pass.
- IntelliJ inspection, formatting, `build_project`, and both shared launches pass.
- Normal launch opens one product window.
- Design Review opens separately addressable product and controller windows.
- Review theme changes do not recreate product state.
- Compose MCP connects to the running Design Review app and can target only the product window for evidence.
- Client-area screenshots export at the two required sizes.
- Semantic tree, interactions, resize, UI error, and logs are exercised.
- No controller screenshot is presented as product evidence.
- No backend behavior or out-of-tranche G1 UI appears.

If a pinned dependency or named Jewel/Compose API cannot compile, stop. Return the exact error and source-backed evidence.
Do not change versions or approximate silently.

## Return contract

Return:

- commit SHA;
- changed files;
- OpenSpec task IDs completed and any task deliberately left pending;
- exact pinned versions, JVM target, and JBR runtime evidence;
- IntelliJ module/dependency/run-configuration inspection;
- IntelliJ inspection/reformat/build/test results;
- normal and Design Review launch results;
- Compose MCP server/configuration path and task availability;
- Compose MCP `status` and `list_windows` summary;
- product and controller window IDs/titles;
- temporary screenshot export paths and their state/theme/size matrix;
- semantic, interaction, resize, UI-error, and log results;
- known issues or `none`;
- confirmation that no Computer Use, no subagents, and no backend behavior were used.

## Dispatch record

Top-level task ID: `PENDING`
