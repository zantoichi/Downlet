# G2 Full Fake State System Implementation

Model: GPT-5.6 Sol  
Reasoning: High  
Base commit: `4dea854561d55d4cbe4a4bde3406e492510f486a`  
OpenSpec change: `design-primary-download-window`  
Tasks: `4.2`–`4.9`  
Thread type: top-level Codex task on shared `main`, no worktree, no subagents

## Goal

Implement the complete deterministic G2 product flow in one coherent pass: extend the existing flat KStateMachine,
apply the approved Quiet Signal Reveal progressive disclosure, distill a bounded generated-resource study into at most
three production resource families, and prove the real product surface through focused state tests, smoke tests, IntelliJ,
and bounded Compose Hot Reload checks.

Stop after the implementation commit. Do not perform the separate G2 review, final evidence capture, or review packaging.

## Gate before mutation

Before any edit, generation, build, test, launch, or other mutation:

1. verify branch `main`;
2. verify HEAD is exactly `4dea854561d55d4cbe4a4bde3406e492510f486a`;
3. verify the shared worktree is clean;
4. stop and report exact state if any check differs.

No READY/RELEASE handshake is required when the exact clean base matches.

## Required context

Read before editing:

- repository `AGENTS.md` instructions;
- this packet;
- `PRODUCT.md` and `DESIGN.md`;
- `docs/design/reviews/G1-core-surface/REVIEW.md` and `EVIDENCE.md`;
- `openspec/changes/design-primary-download-window/proposal.md`;
- `openspec/changes/design-primary-download-window/design.md`;
- `openspec/changes/design-primary-download-window/tasks.md`;
- every spec under `openspec/changes/design-primary-download-window/specs/`;
- current production, controller, test, build, and Compose-resource files.

Use the repository-local `openspec-apply-change` skill. Load and follow `impeccable` before UI work and `imagegen`
before generated concepts or bitmap resources. Use the built-in image generation tool, not CLI/API fallback. The retired
external initial prompt is not required.

## Starting baseline

- G1 is approved. Preserve its native compact desktop register, URL behavior, accessibility baseline, and light/dark Jewel
  treatment.
- Kotlin `2.4.10`, Compose Multiplatform `1.12.0`, Gradle `9.5.0`, Jewel `0.39.1-262.9437.29`, JBR 25, JVM bytecode 21,
  KStateMachine coroutines `0.38.1`, Hot Reload `1.2.0`, ktlint, Detekt, and configuration cache are working.
- `DownloadStateHolder` owns one flat KStateMachine for Empty, Resolving, and Ready. Paste-immediate behavior, `350 ms`
  typed-input debounce, `550 ms` resolution, stale cancellation, forced-state timer bypass, and lifecycle cleanup pass.
- `ProductSurface` renders the real G1 Ready composition. The current work plane is still drawn for Empty/Resolving and must
  be corrected to the approved progressive-disclosure contract.
- `gradlew.bat check` passes 19 tests. `gradlew.bat smokeTest` passes one real-product Empty→Resolving→Ready test and reports
  elapsed wall time.
- The Skiko native-access warning is removed. Jewel's `sun.misc.Unsafe::objectFieldOffset` warning remains the known
  unsuppressed upstream standalone limitation.

## Product contract

- Primary user: a nontechnical Windows user downloading YouTube audio or video locally.
- Keep the product a compact native desktop utility, not a SaaS dashboard, mobile app, media library, or IDE.
- Keep the URL field and window geometry fixed through all states.
- Empty shows only the URL row and helper. It draws and reserves no work plane.
- Resolving shows only a compact checking row beneath the URL field. It reveals no media or controls.
- Ready, Downloading, Completed, and Error share one subtly bounded low-chroma cool tonal work plane. Once revealed, its
  geometry remains stable while state content changes in place.
- Use one coordinated `180–220 ms` fade plus at most `6.dp` vertical rise with
  `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`. Honor Compose duration scaling. No bounce, decorative loop, stagger, animation
  framework, or delayed interaction.
- Keep ordinary-user copy exact. Do not expose yt-dlp, process, extractor, codec, format ID, or backend terms.
- Basic accessibility remains mandatory: native focus/activation, meaningful names/roles/values, selected and enabled state,
  progress semantics, status text not conveyed by color alone, and focus restored to the URL field after reset.

## Required implementation order

### 1. Generate and distill resources — task 4.3a

Use the built-in image generation tool for one bounded set only:

1. one Empty full-window Quiet Signal Reveal reference;
2. one Resolving full-window reference;
3. one Ready full-window reference;
4. one app-icon study based on a folded transfer ribbon or descending signal;
5. one deterministic 16:9 thumbnail study;
6. one missing-preview mark study.

Use one call per distinct concept or study. Full-window references are `ui-mockup` concept inputs, not code targets or
shipping window images. Resource studies may use `logo-brand` or `stylized-concept` as appropriate. Every prompt must state:

- realistic shippable Windows desktop utility or focused resource study;
- restrained low-chroma cool neutrals compatible with Jewel light/dark surfaces;
- no text, letters, numbers, logos, trademarks, YouTube marks, watermark, glass, noise, giant illustration, or decorative
  background;
- clear silhouette and legibility at the intended product size.

Save the six selected generation outputs under `docs/design/concepts/G2/` with descriptive names and record their final
prompts in `docs/design/concepts/G2/SELECTION.md`. Inspect each output. Allow at most one targeted regeneration for a selected
resource when a concrete defect prevents distillation; do not generate broad variants.

Distill, redraw, or optimize ideas into at most:

- one simple Downlet app-icon family;
- one polished deterministic 16:9 thumbnail fixture;
- one monochrome missing-preview mark usable with theme tint.

Put production assets under `src/main/composeResources/drawable/`. Prefer SVG/vector for iconography and one optimized bitmap
only when the thumbnail benefits from raster detail. Generated full-window concepts never ship. Do not add a runtime image
dependency. Apply the app icon to product windows only if the exact existing Jewel/Compose window API supports it cleanly;
do not build a workaround. Replace or retain the current thumbnail/fallback only when the selected resource is materially
better and passes light/dark product-size checks.

### 2. Extend the flat KStateMachine — task 4.2

Extend the existing machine with Downloading, Completed, and Error. Keep one flat machine and the immutable
`DownloadUiState` render model. Use explicit UI/internal events and the smallest directly owned timer job.

Required normal transitions:

- Ready + Download → Downloading at `0`;
- Downloading ticks through `18`, `43`, `68`, `87`, and `100`, one step every `350 ms`;
- normal fixture at `100` → Completed;
- failure fixture at `68` → Error;
- Downloading + Cancel → Ready with mode, quality, destination, URL, and media preserved;
- Error + Retry → Downloading at `0` with prior choices preserved;
- Completed + Download Another or Reset → Empty and URL-field focus restoration;
- Completed + Open Folder stays Completed and shows
  `Folder opening is unavailable in this design preview.`;
- editing a permitted URL cancels stale progress and starts the existing resolution flow;
- controller-forced states bypass timers and cannot leak stale timer transitions.

Disable URL editing and format/quality/destination changes during Downloading. Cancel every stale progress job on cancel,
retry, reset, link change, forced state, completion, error, and holder close. Do not add hierarchy, parallel states,
serialization, undo, a machine wrapper, ViewModel, repository, service, DI, or second state engine.

### 3. Implement progressive disclosure and Downloading — tasks 4.3 and 4.4

Restructure the existing surface by stable responsibility, not by an arbitrary file-length target. Reuse existing G1 Ready
components. Extract cohesive state bodies only when that reduces mixed ownership.

- Empty: URL row plus `Paste or type a YouTube link. Downlet checks it automatically.`; no work plane or reserved body.
- Resolving: compact spinner and `Checking this YouTube link…`; no work plane or downstream controls.
- Ready and later: reveal the single work plane once.
- Downloading: preserve media identity; show Jewel `HorizontalProgressBar`, percentage, and concise deterministic speed/time
  copy; disable choices; show Cancel as a calm Jewel `Link`.
- Progress semantics expose range/value and concise status. Motion never carries meaning alone.
- Cancel returns to Ready without losing selections or destination.

Keep visual polish quiet: one tonal plane, thin boundary, restrained spacing, strong hierarchy, and no cards, gradients,
glass, glow, decorative background, telemetry layout, ornamental badges, or icon clutter.

### 4. Implement Completed actions — tasks 4.5 and 4.6

- Replace the work-plane status/action region in place.
- Show `Saved to {destination}` with a non-color-only success treatment.
- `Open Folder` is the only primary button.
- `Download Another` is a secondary Jewel link.
- Open Folder performs no OS or filesystem action and keeps Completed visible with the exact design-preview acknowledgement.
- Download Another resets to canonical Empty, clears resolved state, and restores URL-field focus.
- No celebration animation or separate completion page.

### 5. Implement recoverable Error — task 4.7

- Preserve URL, media identity, choices, and destination.
- Use the non-deprecated Jewel `InlineErrorBanner` API available in the pinned Jewel version.
- Show exactly `Couldn't download this media.` and
  `Check that the YouTube link is available and try again.`
- Expose Retry through the banner action and restart fake Downloading with prior choices.
- No stack trace, process text, backend detail, modal, or destructive reset.

### 6. Complete the Design Review Controller — task 4.8

Add direct controls for Empty, Resolving, Ready, Downloading, Completed, and Error plus the required normal, failure, long-title,
missing-thumbnail, long-destination, disabled-action, and invalid-input fixtures. Every forced state is immediate and bypasses
timers. Reset restores canonical Empty, default light theme, and normal fixtures. Keep controller layout developer-only and
separate from product captures.

### 7. Tests and smoke — task 4.9

Add the smallest deterministic checks that fail if the real flow regresses:

- exact progress sequence and `350 ms` boundaries;
- normal completion;
- failure at `68`;
- Cancel preserving choices/destination;
- Retry restarting from `0`;
- Open Folder acknowledgement without state change;
- Download Another/reset and focus-restoration state;
- stale job cancellation after link edit, cancel, retry, reset, force, and close;
- forced-state timer bypass;
- disabled edits/choices during Downloading.

Extend the existing `smokeTest`; do not create another framework or task. Render the real `ProductSurface`, use semantics and
virtual time, and cover:

- happy path: Empty→Resolving→Ready→Downloading→Completed;
- recoverable path: failure fixture→Downloading→Error→Retry→Downloading.

Keep smoke separate from `check`, perform no network/subprocess/filesystem work, report elapsed wall time, and keep the warm
ten-second target informational.

## Impeccable and iteration boundary

After the first complete six-state flow works, run one bounded Impeccable production-craft pass against Quiet Signal Reveal.
Batch only concrete hierarchy, spacing, contrast, motion, disabled-state, resource-integration, and ordinary-user-copy defects
into one coherent correction pass. Re-run affected verification once, then stop. Do not enter a micro-polish loop or generate
new visual directions.

## Required IntelliJ MCP use

Use the running IntelliJ MCP server named `intellij`, never WebStorm. Pass
`C:\Users\SVall\IdeaProjects\Downlet` as `projectPath` on every call. Do not use the IDE terminal.

- Inspect current symbols, callers, KStateMachine APIs, Jewel `HorizontalProgressBar`, `InlineErrorBanner`, link/button/icon,
  resource, and decorated-window signatures before edits.
- Reformat every changed Kotlin/Kotlin Gradle file.
- Inspect every changed source, test, resource reference, and OpenSpec file for unresolved problems.
- Run focused state tests, `smokeTest`, `check`, and IntelliJ `build_project`.
- Execute both `Downlet` and `Design Review` on JBR 25.
- Preserve JVM 21 bytecode, current dependency pins, native-access arguments, configuration-cache compatibility, and run names.

Use normal shell plus RTK for noisy Gradle, Git, search, diff, and log output. Use `apply_patch` for file edits. Do not use
Computer Use.

## Required Compose Hot Reload MCP proof

Use one bounded implementation proof against the final candidate commit. Do not create the canonical G2 evidence package.

- Connect to `Design Review`; record exact product/controller IDs and stop only task-owned processes.
- Traverse normal happy, failure, Cancel, Retry, Open Folder, Download Another, and Reset paths.
- Capture transient product-only screenshots under `build/design-review/g2-state-system/` for Empty, Resolving, Ready,
  Downloading, Completed, and Error, with representative light/dark coverage and no controller pixels.
- Verify Empty has no work plane, Resolving has no premature controls, and Ready reveals the plane without moving the URL field.
- Verify progress, locked controls, secondary/primary hierarchy, selected resources, fallback, and fixed copy.
- Inspect targeted semantics for progress, disabled/selected state, status, outcome, destination, and actions. Attempt the known
  stalling whole-product text-editor semantic tree at most once per exact commit; use targeted nodes/subtrees afterward and
  report the limitation precisely.
- Check UI errors and logs after interactions. No unexplained exception or hang may remain.
- Do not recapture the final G2 evidence set; task 4.12 owns that after review.

## Scope boundaries

DESIGN ONLY. No yt-dlp, network, subprocess, ffmpeg, file writes, native folder opening, destination picker, persistence,
settings, telemetry, update, packaging, installer, or backend architecture.

Do not add a second test framework, screenshot goldens, broad UI harness, animation library, image-loading library, design-token
system, component framework, repository/service/interface layer, DI, generic state wrapper, Git hook, CI-provider file, file-line
gate, Modulith analogue, or unrelated refactor.

Preserve the existing code-health gates. Split files only on stable responsibility boundaries; no arbitrary 100-line target.

## Completion and return contract

After all acceptance checks pass:

1. mark only tasks `4.2`–`4.9` complete; leave root-owned `4.1` and review/evidence tasks `4.10+` pending;
2. run strict OpenSpec validation;
3. commit all implementation changes once on shared `main` using a Conventional Commit;
4. confirm the worktree is clean;
5. relaunch both required IntelliJ configurations at the committed state.

Return:

- task ID, exact commit SHA, branch, and clean status;
- changed files and production resource paths;
- six generated concept/study paths, final prompts, selection/distillation note, and any one targeted regeneration;
- final state/event/transition map and timer/cancellation ownership;
- exact visible/interaction changes for progressive disclosure, Downloading, Completed, Error, Cancel, Retry, Open Folder,
  Download Another, and controller forcing;
- focused test and smoke counts/results/duration plus `check` result;
- IntelliJ source/API inspection, formatting, changed-file problems, build, dependency, and run results;
- Compose window IDs, interaction results, transient screenshots, targeted semantics, UI-error result, and log summary;
- strict OpenSpec result and tasks checked;
- known issues, including the expected upstream Jewel Unsafe warning if still present;
- confirmation that no subagent, reviewer, worktree, Computer Use, WebStorm, backend, G3, final G2 evidence, or review package
  was used.

## Dispatch record

Top-level task ID: root records this after task creation.
