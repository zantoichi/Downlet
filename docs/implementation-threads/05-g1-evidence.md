# Implementation Thread

Model: GPT-5.6 Sol  
Reasoning: High  
Base commit: `28ac4c3`  
Reviewed product commit: `4444ee7949e29b8c8432f3274e186addb9267164`  
OpenSpec change: `design-primary-download-window`  
Assigned task: `3.16`  
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Capture the final G1 product evidence efficiently from the reviewed implementation: exact client-area Compose captures,
interaction/state proof, and the native Windows frame proof that Compose MCP cannot provide.

## Why Computer Use is allowed here

Use Compose MCP for all product-state control, client-area screenshots, semantics, errors, and logs. Use Computer Use only
for the three capabilities without a Compose workaround:

1. a real Windows `Ctrl+V` chord into the Jewel text editor;
2. full-window `Windows.Graphics.Capture`, including native title bar and border;
3. native frame drag and maximize/restore interaction.

Read and follow the complete `computer-use` skill, `guidance.md`, `api.md`, and `confirmations.md` before Windows input.
Use `node_repl` with `@oai/sky`; do not launch a helper executable, use PowerShell UI Automation, automate a terminal, or
invent window handles. The user has explicitly authorized Computer Use when no workaround exists. No action in this packet
requires a risky confirmation.

## Required context

Read before acting:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/design/reviews/G0-direction/REVIEW.md`
- `docs/design/evidence/G1/foundation-window-proof.md`
- `docs/design/evidence/G1/integrated-review.md`
- all artifacts under `openspec/changes/design-primary-download-window/`
- current `DesignReview.kt`, `Main.kt`, `DownloadState.kt`, and run configurations

Use the repository-local `openspec-apply-change` skill. Do not use Impeccable in this evidence task; task 3.17 owns the
separate visual critique.

## Scope and write set

- Do not edit app source, build files, resources, run configurations, design direction, or existing accepted evidence.
- Add only final task-3.16 evidence under `docs/design/evidence/G1/`, update task `3.16`, and commit those changes.
- No backend, network, yt-dlp, filesystem product behavior, G2, G3, generated mockup, controller screenshot, subagent,
  worktree, WebStorm, or browser automation.
- Launch through IntelliJ MCP `intellij` with `C:\Users\SVall\IdeaProjects\Downlet` as `projectPath`.
- Record the exact source HEAD before capture. If it differs from this packet only by orchestration/evidence commits, record
  both the source HEAD and reviewed product commit. Stop if app/build/resource code differs from the reviewed commit.

## Required Compose MCP evidence

Launch `Design Review`, connect Compose MCP, and record product/controller window IDs. Use the controller only to select
deterministic fixture/theme states; do not capture controller screenshots.

Save these product client-area PNGs from the final source HEAD:

- `docs/design/evidence/G1/g1-empty-light-720x420.png`
- `docs/design/evidence/G1/g1-empty-dark-720x420.png`
- `docs/design/evidence/G1/g1-ready-light-720x420.png`
- `docs/design/evidence/G1/g1-ready-dark-720x420.png`
- `docs/design/evidence/G1/g1-ready-light-620x350.png`

For every capture:

- resize to the exact requested product bounds and confirm them with `list_windows`;
- wait for the intended state/theme, inspect the returned image, and reject foreign pixels, clipping, or stale state;
- query UI errors for both windows and inspect recent logs for unexplained failures.

Exercise both URL paths:

- manual typing: reset to Empty Light, use Compose text input with a valid deterministic YouTube URL, observe Resolving and
  Ready without Enter, then reset;
- native paste: seed the Windows clipboard with `https://youtu.be/g1-native-paste` using a normal non-UI shell action before
  Computer Use, focus the product field from a fresh observation, send `Control_L+v`, and confirm the field and state advance
  without Enter. Never read or transmit existing clipboard contents.

Attempt product `get_semantic_tree` once in Empty and once in Ready and save the returned JSON/text as:

- `docs/design/evidence/G1/g1-empty-semantics.json`
- `docs/design/evidence/G1/g1-ready-semantics.json`

The Jewel text editor has a known whole-product serialization stall. If either bounded attempt times out, do not retry it
repeatedly or claim success. Record the exact timeout, save the controller semantic state, and use fresh Computer Use
accessibility text for the product as the fallback evidence. The fallback must expose the visible labels/actions for the
state, and UI-error/log checks must remain clean. Document this limitation in the evidence note.

## Required native Windows proof

Select exactly one returned `Downlet` window with Computer Use before every action. Capture and save the WGC screenshot data
URL directly to these files; saving is required evidence, not redundant screenshot inspection:

- Empty Light full window: `docs/design/evidence/G1/g1-native-empty-light-full-window.png`
- Ready Dark full window: `docs/design/evidence/G1/g1-native-ready-dark-full-window.png`

Verify and record:

- content and title bar use the same Light/Dark theme;
- title is `Downlet`; minimize, maximize/restore, and close controls are visible; no foreign pixels;
- drag the non-maximized frame once and record before/after bounds;
- maximize once, record maximized bounds, restore once, and confirm original size/state returns;
- the product remains non-topmost and usable after restore.

Follow the Computer Use two-cell observe/action rule. Refresh after every state-changing action. Do not reuse coordinates,
screenshot IDs, accessibility indexes, or stale window objects.

## Evidence note and completion

Create `docs/design/evidence/G1/product-evidence.md` containing:

- status/date/source commit/reviewed product commit/task ID;
- IntelliJ build result;
- Compose window IDs, exact sizes, state/theme sequence, interaction transcript, semantic result/fallback, UI errors, logs;
- native target window identity, Ctrl+V result, WGC file dimensions/origins, drag/maximize/restore bounds, and frame checklist;
- every final evidence path and any known tooling limitation;
- confirmation no app source/resources/build files changed.

Run IntelliJ `build_project` before capture, strict OpenSpec validation after writing evidence, inspect every PNG, and verify
all evidence paths exist and are non-empty. Mark only task `3.16` complete when this contract passes. Commit with a
Conventional Commit and return commit SHA, exact files, checks, and clean status.

## Dispatch record

Top-level task ID: `01a048a3-3531-7173-884d-81a5775878a8`

## Root inspection correction — 2026-08-28

Root inspection of evidence commit `ec330351eb6137602b0819bc0e75dadd38e18d75` rejected
`docs/design/evidence/G1/g1-native-ready-dark-full-window.png`: a visible mouse pointer and blue Computer Use halo remain
near the center of the product content, violating the no-foreign-pixels requirement.

Reopen only task `3.16`. Recapture only that Ready Dark WGC file from the unchanged reviewed product, moving the pointer
fully outside the target frame before a fresh observation. Inspect the saved PNG at original resolution. Update
`product-evidence.md` and `g1-ready-semantics.json` only if the final window ID, dimensions, or origin differ. Do not alter
the accepted Compose captures, Empty native capture, app files, or other evidence. Run strict OpenSpec validation, recheck
task `3.16`, commit the bounded correction, and return the new evidence commit and clean status.
