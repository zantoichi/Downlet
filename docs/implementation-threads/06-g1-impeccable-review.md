# Review Thread

Model: GPT-5.6 Sol  
Reasoning: High  
Base commit: `28a140cd7b01655266deb927076a0776a4d58107`  
Reviewed product commit: `4444ee7949e29b8c8432f3274e186addb9267164`  
OpenSpec change: `design-primary-download-window`  
Assigned task: `3.17`  
Thread type: separate top-level Codex review task, read-only, no worktree, no subagents

## Goal

Run one rigorous Impeccable critique of the completed G1 core surface. Judge the approved design as a Windows-first product,
not as a marketing page and not as a request for a new visual direction. Return findings only. Root may orchestrate at most
one coherent correction pass after the verdict.

## Required Impeccable setup

Read the complete installed `impeccable` skill and these references before assessment:

- `reference/product.md`
- `reference/critique.md`
- `reference/cognitive-load.md`
- `reference/heuristics-scoring.md`
- `reference/personas.md`

Run the installed `load-context.mjs` against this repository and consume its full JSON. Confirm `PRODUCT.md` and `DESIGN.md`
are present and non-placeholder. Register is `product`; shape is not required for a critique. State a read-only preflight:

`IMPECCABLE_PREFLIGHT: context=pass product=pass command_reference=pass shape=not_required image_gate=skipped:committed coded evidence mutation=closed:read-only`

The Impeccable CLI/browser detector does not support Kotlin Compose Desktop markup, and this target is not a browser page.
Record that precise reason; do not run an irrelevant `npx impeccable` scan or browser overlay.

Impeccable normally requests independent subagent assessments. The user explicitly forbids all subagents. Perform the two
tracks sequentially in this Sol High task instead: complete the design-director assessment first, then perform a separate
source/evidence checklist audit without using the first track as the checklist answer. Synthesize only after both are done.

## Required context

Read before reviewing:

- `D:\Downloads\INITIAL_DESIGN_ORCHESTRATOR_PROMPT.md`
- `PRODUCT.md`
- `DESIGN.md`
- `docs/design/reviews/G0-direction/REVIEW.md`
- `docs/design/evidence/G1/integrated-review.md`
- `docs/design/evidence/G1/product-evidence.md`
- all G1 PNG/JSON evidence under `docs/design/evidence/G1/`
- all artifacts under `openspec/changes/design-primary-download-window/`
- current `Main.kt`, `DownloadState.kt`, `DesignReview.kt`, focused tests, and Jewel/resource build setup

Use IntelliJ MCP named `intellij` with `C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call. Never use
WebStorm. Confirm HEAD/worktree before review. Do not edit, reformat, commit, mark tasks, or mutate evidence.

## Assessment A — design director

Inspect every final G1 screenshot at original resolution:

- Empty Light/Dark 720×420;
- Ready Light/Dark 720×420;
- Ready Light 620×350;
- native Empty Light and Ready Dark full-window captures.

Also inspect the live Design Review edge fixtures at minimum size when available: long title, missing preview, long path, and
disabled Download. Use Compose MCP only for read-only state selection and inline inspection; save no new files. Do not repeat
the known product semantic-tree call that times out. Controller semantics, existing semantic fallback records, screenshots,
UI errors, and logs are enough.

Evaluate:

- anti-pattern/AI-slop verdict against all Impeccable bans and Downlet anti-references;
- hierarchy, eye flow, task clarity, progressive disclosure, rhythm, alignment, density, typography, color, native feel;
- Light/Dark parity, 720×420 and 620×350 behavior, long/missing/disabled seams;
- interaction discoverability, primary-action clarity, copy, feedback, and emotional journey;
- the eight-item cognitive-load checklist and visible decision-option counts;
- Nielsen's ten heuristics, each scored 0–4 with specific evidence;
- persona walkthroughs for Jordan (first-timer), Sam (keyboard/accessibility), and Alex (impatient power user).

## Assessment B — source and evidence audit

Independently inspect source and committed evidence for mismatches with the visible UI and approved design contract. Check:

- Jewel/native component vocabulary and absence of custom imitation controls;
- focusability, labeled actions, grouped media semantics, live validation/status communication, disabled state;
- stable layout and truncation at default/minimum size;
- state/copy consistency between Empty, Resolving, Ready and deterministic edge fixtures;
- evidence accuracy, including no controller pixels, foreign overlays, stale screenshots, or claim/file mismatch;
- zero unexplained IntelliJ problems, build/test status, Compose UI errors, and logs.

Do not treat the known 120-second Jewel product semantic serialization timeout as a design defect by itself. Judge the
underlying accessibility implementation and the documented fallback evidence.

## Verdict contract

Return one synthesized critique with:

- exact reviewed commit and read-only scope confirmation;
- design-health table for all ten heuristics and total `/40`;
- AI-slop verdict and detector-not-applicable note;
- cognitive-load result (`0–1` low, `2–3` moderate, `4+` critical) and decision-option counts;
- 2–3 specific strengths;
- 0–5 prioritized findings, each `P0`–`P3`, exact evidence/source location, user consequence, concrete minimal remedy,
  suggested Impeccable command, and owning OpenSpec task IDs if a fix is warranted;
- Jordan/Sam/Alex red flags or explicit `none`;
- minor observations and final recommendation: `READY`, `READY WITH DOCUMENTED P2/P3`, or `FIX REQUIRED`.

Do not ask the user questions inside the review task. The user already fixed scope: preserve the approved G0 direction,
basic accessibility only, and at most one coherent fix/reverification pass. If no P0/P1 exists, recommend no implementation
churn. If P0/P1 exists, group only causally related findings into one proposed correction; do not edit.

## Dispatch record

Top-level review task ID: `01a048c8-db5f-72c1-b399-008945854580`
