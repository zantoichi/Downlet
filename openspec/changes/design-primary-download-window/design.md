## Context

See `proposal.md` for motivation and the three capability specs for observable behavior. G0 and G1 are approved. The repository contains the coded G1 Jewel surface, deterministic review harness, focused state tests, automated code-health checks, and exact-commit review evidence. Before G2 visual work, one technical tranche modernizes the compatible stack, introduces KStateMachine, and adds a fast smoke path without changing approved G1 visuals.

The recommended Impeccable direction is **The Quiet Transfer Desk**: a familiar Windows utility frame, one persistent YouTube-link row, and one restrained work area that reveals only the information required by the current state.

## Goals / Non-Goals

**Goals:**

- Produce a compact Windows-first desktop composition that is directly codable with current stable Jewel standalone APIs.
- Keep product state, fake behavior, and review fixtures deterministic enough for repeatable screenshots and semantic inspection.
- Make Empty and Ready complete at G1, all states complete at G2, and resilience/polish complete at G3.
- Keep the implementation small enough that later real integration can replace fake effects without redesigning the UI.
- Progressively disclose only controls and surfaces useful to the current state.
- Keep local verification fast enough for routine use and report basic smoke duration.

**Non-Goals:**

- No real media resolution, download, process execution, file write, folder opening, destination picker, persistence, packaging, telemetry, update system, or backend abstraction.
- No navigation model, settings, onboarding, history, advanced options, terminal output, or provider-general architecture.
- No custom design system beyond a handful of layout dimensions and fixture-specific status choices.
- No formal accessibility certification; this change proves the specified basic desktop baseline.

## Decisions

### 1. Pin the latest stable, mutually compatible stack

Use this post-G1 target unless the implementation probe proves a hard Jewel compatibility issue:

- Kotlin `2.4.10`
- Compose Multiplatform `1.12.0`, falling back only to the newest verified compatible stable `1.11.x` release if current Jewel cannot build or launch against `1.12.0`
- Jewel standalone `0.39.1-262.9437.29`
- Compose Hot Reload `1.2.0`
- Gradle `9.5.0`
- JVM bytecode target `21`
- JBR `25` as the development and application runtime

Kotlin `2.4.10` is the latest stable compiler line and is fully supported through Gradle `9.5.0`; use that fully supported pair instead of retaining the numerically newer but out-of-matrix Gradle `9.7.1`. Compose `1.12.0` is the current stable desktop line. Jewel `0.39.1` remains the published standalone artifact, so its compile and launch compatibility is the controlling probe. Hot Reload stays on stable `1.2.0`; do not adopt the `1.3.0` alpha. Keep the bytecode/runtime split: target Java 21 and launch on JBR 25.

Alternative considered: update every coordinate to its numerically newest release, including prereleases or unsupported Gradle combinations. Rejected because a stable, fully supported matrix is more valuable than version-number maximalism.

### 2. Use a plain Jewel-decorated Windows frame

Use Jewel `DecoratedWindow` with one Compose `WindowState` and two content-driven height profiles. Preferred width is `720.dp`, with `620.dp` as the minimum usable width. Compact is `168.dp` preferred / `156.dp` minimum for Empty and Resolving; Expanded is `420.dp` preferred / `400.dp` minimum for Ready, Downloading, Completed, and Error. Configure the theme through the `IntUiTheme` styling overload with `ComponentStyling.default().decoratedWindow()`.

The title bar contains only the Downlet title and Jewel/JBR-managed Windows controls. Do not add a project stripe, menu, toolbar, breadcrumbs, or IDE actions. This keeps the whole frame in sync when the design-review harness forces light or dark mode while relying on Jewel's JBR-backed drag, resize, maximize, minimize, and close behavior.

Alternative considered: Compose Desktop `Window` with native OS chrome. Rejected because the review harness must switch the complete app frame between light and dark independently of the current Windows theme; native chrome can leave a light title bar around dark Jewel content.

Normal launch reads `androidx.compose.foundation.isSystemInDarkTheme()` and uses that value as its initial `IntUiTheme` selection. If the platform value is unavailable, Downlet uses light. Live switching after the Windows setting changes is not required in this design change; restarting reads the setting again. The Design Review entry point supplies an explicit Light or Dark override.

Compose Hot Reload 1.2.0 screenshots intentionally exclude window-title chrome. Compose MCP remains authoritative for the client area, semantics, interactions, and resize bounds. Each coded gate that judges title-bar parity also records one Codex Computer Use `Windows.Graphics.Capture` screenshot of the real running Downlet window and a manual Windows check for theme, controls, drag, and maximize/restore at the same commit.

### 3. Keep one pure product surface and one KStateMachine-backed state holder

Keep the immutable `DownloadUiState` render model with the six explicit product states and one small Compose-aware state holder. Add `io.github.nsk90:kstatemachine-coroutines:0.38.1` and let one machine own normal phase transitions. Existing UI events become machine events; resolution and download timers emit internal events. Context such as fixture, mode, quality, destination, and progress remains ordinary immutable or holder state rather than nested machine data structures.

Use only flat states, guarded or conditional transitions where needed, and coroutine-aware event processing. Do not use hierarchical or parallel states, persistence/serialization, undo, export tooling, generated diagrams, a wrapper interface, or a second state-machine abstraction. The Design Review Controller may keep a deterministic forced-state override that bypasses timers; normal user flow must go through the machine.

The product composable receives state and event callbacks and contains no AWT clipboard integration. Native text-field editing is the only clipboard surface.

Alternative considered: MVVM, a handwritten reducer, or service interfaces. Rejected because the user selected KStateMachine and one direct machine now provides the transition contract without adding application layers.

### 4. Let the field handle paste and submit automatically

Use Jewel's state-based `TextField(TextFieldState, ...)` API with no visible Paste button and no proactive clipboard read. Windows `Ctrl+V` remains native text editing rather than a second product action.

Apply a Compose preview key-event modifier only to record `Ctrl+V` intent before the text field handles the edit. Observe `TextFieldState.text`; the next valid pasted value resolves immediately. Other valid edits resolve after a `350 ms` idle debounce. Invalid text remains editable and sets a restrained inline validation state. The key path never reads or monitors clipboard contents itself.

Supported fake-validation hosts are `youtube.com`, `www.youtube.com`, `m.youtube.com`, and `youtu.be`. Validation proves only interaction behavior; it does not contact the provider.

Alternative considered: resolve every valid edit immediately. Rejected because it would make manual typing feel jumpy and would not satisfy the agreed debounce behavior.

### 5. Use a stable vertical composition with progressive disclosure

The product content is a single `Column` with a persistent link field and state-appropriate disclosure:

1. Empty shows the URL form row and one helper line only; it does not reserve or draw the work plane.
2. Resolving keeps the field fixed and reveals one compact status row directly beneath it.
3. Ready, Downloading, Completed, and Error reveal one subtly bounded, inset tonal work plane for useful media and action content.

The work plane is a single grouping surface, not a card grid or nested-card system. It uses theme-aware low-chroma cool neutrals, a thin boundary, and no decorative shadow. Once visible, its geometry remains stable while later state content changes in place. Automatic tier changes adjust height only and preserve the upper-left content origin and URL-field anchor whenever the active work area permits.

Default metrics use approximately 20 dp outer padding, 16 dp major gaps, 8 dp control gaps, and a 96 dp label column. The Compact profile uses 16 dp outer padding and 12 dp major gaps so the link row plus helper, validation, or resolving status define the whole body. Expanded keeps the existing compact-height metric branch where needed while preserving the same information hierarchy. This is desktop resize hardening, not a mobile layout.

If the active monitor work area, Windows scaling, or user-managed bounds cannot hold the Expanded minimum, the body may use a simple vertical scroll state so essential actions remain reachable. No scrollbar or adaptive branch should appear at either preferred profile under ordinary conditions.

Alternative considered: keep one fixed minimum or allow constrained content to clip. Rejected because each tier needs its own usable minimum and impossible work-area cases need reachable overflow.

### 6. Keep the YouTube-link row direct and automatic

Map the row to Jewel `Text` and the state-based `TextField`:

- `YouTube link` uses a fixed label width aligned with later form rows.
- The text field takes all remaining width and shows `Paste a YouTube link…` as placeholder text.
- No visible Paste or Analyze action competes with the field; native paste and typed input trigger the automatic behavior.
- Validation appears as one compact text line directly beneath the field rather than a card or modal.

The YouTube-link row remains present and editable in every state. Editing it during Downloading is disabled; in other states, a new valid URL restarts fake resolution.

### 7. Make Ready the reference composition

Ready uses four regions inside the one work plane, without nested cards:

- Media identity row: a local deterministic 16:9 image or fixed-size fallback on the left; title and one metadata line on the right.
- Format row: two Jewel `RadioButtonRow` controls for Video and Audio.
- Quality row: Jewel `ListComboBox` with an explicit fixed/fill width. The selected first item names the actual resolved best, such as `Best available — 2160p` or `Best available — 251 kbps audio`.
- Destination row: truncated Jewel `Text` plus a Jewel `Link` labeled `Change…`; the fake callback cycles between deterministic destination fixtures and shows `Save location changed to {destination}.`

The bottom-right Download action uses Jewel `DefaultButton`. No other primary action appears.

The thumbnail uses Compose `Image` and a bundled local resource because Jewel does not need to own ordinary media imagery. It is clipped to a modest rounded rectangle with a thin theme-aware boundary. The fallback uses the same geometry, a distinct tonal fill, concise `Preview unavailable` copy, and explicit semantics. This is the only small custom visual primitive in the main composition.

### 8. Map remaining states to native Jewel treatments

- **Empty:** YouTube-link row plus one short explanatory sentence; no placeholder illustration.
- **Resolving:** Jewel `CircularProgressIndicator` beside concise status text. Use the horizontal indeterminate bar only if the first coded review proves the spinner too weak; do not use skeletons.
- **Downloading:** Jewel `HorizontalProgressBar(progress)` plus percentage and one metadata line. Choices are disabled. Cancel is a Jewel `Link` and returns to Ready with selections preserved.
- **Completed:** a Jewel success `Icon` plus saved-location text. `DefaultButton("Open Folder")` is primary and a Jewel `Link("Download Another")` resets to Empty. In this design-only build, Open Folder leaves the state in place and shows `Folder opening is unavailable in this design preview.`
- **Error:** `InlineErrorBanner` with `Modifier.fillMaxWidth()` and a banner link action labeled Retry. Preserve media context. Use the non-deprecated `InlineErrorBanner` API available in 0.39.1.

Provisional visible copy is fixed for G1/G2 implementation:

- Empty hint: `Paste or type a YouTube link. Downlet checks it automatically.`
- Invalid link: `Enter a valid YouTube link.`
- Resolving: `Checking this YouTube link…`
- Destination change acknowledgement: `Save location changed to {destination}.`
- G1-only Download acknowledgement: `Design preview: Download action received.` This is replaced by the real fake Downloading transition at G2.
- Disabled Download reason: `Download is unavailable for this item.`
- Downloading label: `Downloading`
- Completed: `Saved to {destination}`
- Design-only Open Folder acknowledgement: `Folder opening is unavailable in this design preview.`
- Error title: `Couldn't download this media.`
- Error body: `Check that the YouTube link is available and try again.`

Fake timing is fixed: resolution completes after `550 ms`; download progress advances through `0, 18, 43, 68, 87, 100` at `350 ms` intervals. The failure fixture enters Error at 68 percent. Controller-forced states bypass timers.

### 9. Let Jewel supply the visual system and keep motion purposeful

Use Jewel typography, component metrics, semantic colors, icons, focus outlines, disabled appearance, and light/dark theme definitions. Add only one low-chroma cool secondary surface and one existing theme accent role. Do not add glass, neon, gradient text, bespoke shadows, or ornamental color.

Disclosure and state-body replacement use one coordinated short transition: a `180–220 ms` fade combined with at most `6.dp` of vertical rise and `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`. Empty-to-Resolving reveals only compact status; Resolving-to-Ready reveals the work plane once. Focus, paste, and status feedback may reuse this restrained timing. There is no bounce, infinite decorative loop, staggered choreography, or motion that delays interaction. Compose duration scaling remains authoritative so a zero animation scale produces the instant end state.

Product-specific values are limited to:

- window target and minimum size;
- outer/major/control spacing;
- form label width;
- thumbnail dimensions and corner size;
- media/status region minimum height.
- the single work-plane fill/boundary;
- the single state-transition duration, offset, and easing.

Do not define a token hierarchy, card component, generic form framework, or alternate button system. Use named parameters when calling Jewel APIs because its documented source-compatibility policy favors them.

### 10. Add semantics and focus only where native components do not carry enough meaning

Jewel controls retain their native focus and role behavior. Add Compose semantics to:

- the media fallback;
- validation and outcome status regions;
- progress value and status text;
- full destination path when the visible text is truncated;
- grouped media metadata where individual fragments would be noisy.

Use a polite live region for Resolving, Completed, and Error status changes. Do not make decorative dividers or thumbnail decoration focusable. Do not add a global Escape handler; native popup Escape behavior remains intact.

Request focus for the YouTube-link field when Empty first appears and whenever reset returns to Empty. A disabled Download fixture keeps the native disabled treatment and adds one concise visible explanation in the existing feedback/status area. Motion never carries status meaning by itself.

### 11. Isolate the deterministic review controller

Provide two entry points in the same application module:

- normal product entry point: product window only;
- design-review entry point: product window plus a small `Design Review Controller` window.

The controller uses ordinary Jewel controls to force state, theme, and fixtures. It has no visual influence on the product surface and no shared layout component beyond the state holder. The `Design Review` IntelliJ run configuration launches the review entry point.

Compose Hot Reload MCP must be configured through its `hotMcpServer` Gradle task during G1. Gate evidence targets the product window ID returned by `list_windows`; the controller window is excluded from product screenshots. Compose captures document the client area. A separate Codex Computer Use `Windows.Graphics.Capture` screenshot documents the complete product frame when title-bar behavior is under review.

### 12. Make OpenSpec authoritative and keep gate proof lean

The repository OpenSpec proposal, design, capability specs, tasks, and approved gate artifacts are the sole planning source of truth. The retired external initial prompt is not required after this decision.

The root task owns planning, integration, and human gates but does not edit application code. UI implementation and independent review run in explicit top-level GPT-5.6 Sol High tasks. Non-visual build, state-engine, and test tranches may use GPT-5.6 Sol Medium. Project subagents are not used. Work remains sequential on shared `main` unless the user changes that decision.

Fine-grained OpenSpec task IDs are traceability units, not thread or commit boundaries. One top-level implementation task may receive a small coherent set of task IDs. Its dispatch message contains the exact base commit, change name, task IDs, goal, required context, in-scope and out-of-scope boundaries, IntelliJ and Compose checks, tests, and return contract. The task starts immediately when `main` is clean at the expected commit; a second task-ID-record commit and READY/RELEASE handshake are unnecessary. A mismatch or dirty tree stops the task before mutation.

Verification has one owner at each phase:

- The implementation task inspects changed files, runs the relevant tests and IntelliJ build, launches the affected run configuration, and captures only the affected Compose states at its exact commit.
- The root verifies the commit, clean tree, artifact completeness, and any missing or risk-sensitive claim; it does not repeat a green full suite by default.
- One read-only Sol High reviewer combines technical review with the required Impeccable critique. It reuses exact-commit implementation evidence and reruns only checks needed to reproduce a finding or replace stale/incomplete proof.
- The final evidence task runs the complete coded-gate proof once after review and any correction: IntelliJ inspections/build, tests, launch, Compose connection, screenshots, semantic trees, interactions, resize checks, UI-error/log checks, reproducible run configuration, and native Windows frame proof where Compose cannot capture title chrome.

Batch all reviewer findings before correction. Each gate allows at most one coherent correction task. A micro-correction may use targeted verification when it changes no dependency, API, state behavior, layout, or interaction and touches at most ten source lines; complete gate evidence is still recaptured once afterward. Larger changes use the normal implementation path.

Known tooling failures are not blind retry loops. Attempt a known-stalling product semantic-tree capture at most once per exact commit; final evidence must either obtain the required proof or mark the gate NOT READY. Before every screenshot, foreground and verify the exact launched product PID/window, reject any occluded capture, and keep controller windows out of product evidence.

Human gates remain G0 Direction, G1 Core Surface, G2 Full State System, and G3 Hardened Final Design. Stop at each gate until the user explicitly sends `APPROVE G0`, `APPROVE G1`, `APPROVE G2`, or `APPROVE G3`; revision feedback reopens only the owning tasks.

### 13. Keep code health as an enforceable baseline

Tasks 3.24–3.28 completed the first code-health tranche before G1 approval. Preserve their responsibility boundaries and automated checks through G2 and G3.

File length is a diagnostic signal, not a quality target. Do not enforce a 100-line maximum or split cohesive files into shallow wrappers. Split when a file has multiple stable reasons to change, leaks ownership, or forces unrelated code to be read together. Keep `DesignReview.kt` intact while it remains one coherent controller responsibility.

Refactor the current mixed responsibilities without changing behavior:

- keep application startup, theme selection, and the decorated product window together;
- isolate link-field effects, automatic resolution orchestration, and general state-body composition;
- isolate Ready-specific form and media composition;
- separate immutable product models and deterministic fixtures from mutable state transitions;
- align tests with those ownership boundaries only where the split improves navigation.

Do not add repositories, services, interfaces, dependency injection, a generic component framework, or public abstractions. Preserve internal behavior, timing, focus, semantics, visual output, run configurations, and the existing deterministic tests.

Add one formatter and one analyzer:

- `.editorconfig` and the `org.jlleitschuh.gradle.ktlint` plugin enforce Kotlin official style and expose check/format tasks;
- the official Detekt Gradle plugin runs maintainability analysis with default rules plus Compose-aware narrow exceptions and generated/build directories excluded;
- pin compatible non-dynamic plugin and engine versions; prefer stable releases, but if no stable Detekt release supports the existing Kotlin/Gradle/JDK stack, use the newest compatible official prerelease and record the compatibility evidence and rationale;
- use no Detekt baseline in this greenfield repository and no file-wide suppression; fix findings or use the narrowest documented rule/symbol suppression;
- wire formatter, analyzer, and tests into `gradlew.bat check`, which becomes the portable local quality gate and the future CI entry point.

Alternative considered: enforce a universal file-length limit, add Git hooks, or create a CI-provider workflow now. Rejected because responsibility and complexity are stronger signals than raw line count, Git hooks are not reliably shared, and the repository has no configured remote or CI provider.

### 14. Add one bounded technical foundation before G2 UI work

Run one Sol Medium implementation tranche before G2 visual work:

- upgrade to the stable compatible stack from Decision 1 and keep already-current ktlint, Detekt, coroutines, Foojay, Jewel, and Hot Reload versions unchanged;
- add `--enable-native-access=ALL-UNNAMED` to application/test Java launches that load Skiko so JBR 25 does not warn about restricted native loading;
- do not suppress Jewel's `sun.misc.Unsafe` warning or vendor Jewel; record it as an upstream standalone-release limitation until a published Jewel build contains the merged fix;
- enable Gradle configuration cache only after two successful runs prove reuse for `check`, `smokeTest`, and the Hot Reload launch tasks without warning mode;
- add one `smokeTest` command using the Compose desktop UI-test API against the real product composable, with virtual time for deterministic transitions and an elapsed-time report. Target a warm run under ten seconds on the reference Windows host, but keep host timing informational rather than a portable correctness failure.

Keep `gradlew.bat check` as the portable formatter/analyzer/unit-test gate. `smokeTest` is the fast in-process product-flow gate used before implementation handoff and review. Do not add Git hooks, CI-provider files, Selenium/Appium, screenshot-golden infrastructure, a second test framework, or a persistent process-launch harness.

### 15. Treat generated imagery as concept input, not a second visual system

Before the G2 UI task, generate a small concept set for the selected **Quiet Signal Reveal** direction: Empty, Resolving, and Ready full-window references plus focused app-icon, thumbnail, and missing-preview studies. Generated full-window concepts are review inputs only and are not shipped.

Distill the selected ideas into at most three production resource families:

- a simple Downlet app icon based on a folded transfer ribbon or descending signal, without YouTube branding;
- one polished deterministic 16:9 thumbnail fixture;
- one minimal missing-preview mark redrawn as a themeable SVG or Compose vector.

Do not ship AI-rendered text, glass/noise backgrounds, giant illustrations, copied YouTube marks, or multiple illustration styles. Prefer SVG/vector resources for iconography and one optimized bitmap only when the thumbnail benefits from raster detail.

## Risks / Trade-offs

- **Jewel 0.40 is newer than the standalone artifact** → Pin the published 0.39.1 standalone coordinate and use its extracted source signatures for G0; make G1's first task a minimal IntelliJ build smoke test.
- **JBR 25 can be mistaken for the project bytecode level** → Run Gradle and Downlet on JBR 25 but set Kotlin `JvmTarget.JVM_21` and Java `--release 21` explicitly.
- **Custom decoration can expose platform-specific drag, scale, or window-control defects** → Use Jewel's JBR-backed `DecoratedWindow` and `TitleBar` without custom hit regions, then prove drag, maximize/restore, 125/150 percent scaling, and light/dark frame parity on Windows before a coded gate passes.
- **Compose MCP screenshots omit title chrome** → Treat them as client-area evidence and add a same-commit Codex Computer Use `Windows.Graphics.Capture` screenshot plus manual Windows interaction record for title-bar checks.
- **Compose 1.11 does not need to promise live Windows theme updates** → Read `isSystemInDarkTheme()` at normal launch, fall back to light, and require restart after the OS preference changes; the review controller supplies deterministic overrides.
- **Later-state content has different minimum-height pressure** → Use `400` logical pixels as the Expanded minimum because Error clips its Retry action at `380`; use constrained overflow only when the work area cannot hold that minimum.
- **Paste intent and text-field edits can race** → Keep one short-lived key-intent flag, clear it after the next edit, never read the clipboard proactively, and retain focused tests for paste-immediate versus type-debounced behavior.
- **Long path truncation can hide useful context** → Preserve the full path in semantics and deterministic fixtures while keeping Change visibly reachable.
- **Hot Reload MCP is not available before a Gradle app exists** → G0 records the configuration target only; G1 is blocked from review, not from coding, until the server connects to the running app.
- **Fake timing can make review flaky** → Controller-forced states bypass all delays and are the source for screenshot capture.
- **Compose `1.12.0` may expose a Jewel standalone incompatibility** → Probe compile, launch, controls, and Hot Reload first; fall back only to the newest verified stable `1.11.x` and record the exact incompatibility.
- **KStateMachine can become architecture theater** → Use one flat machine for phase transitions only; keep data models and effects ordinary and add no wrapper framework.
- **Jewel still emits a JDK Unsafe warning** → Keep the published standalone artifact, track the merged upstream fix, and do not hide or vendor around the warning.
- **Compose desktop UI testing is experimental** → Keep one narrow semantics-driven smoke path and ordinary state tests; do not build a broad UI-test framework.
- **Generated concepts can introduce visual noise or branding risk** → Use them only for direction, redraw final resources, and reject generated text or provider marks.

## Migration Plan

1. After `APPROVE G0`, create a minimal single-module Compose Desktop scaffold and prove the pinned stack through IntelliJ MCP.
2. Implement and approve G1.
3. Run one Sol Medium technical-foundation tranche for compatible upgrades, KStateMachine, warning cleanup, configuration-cache proof, and the fast smoke path.
4. Use one Sol High G2 task for generated-resource direction, progressive disclosure, and the complete fake state system, then stop for `APPROVE G2`.
5. Complete and approve the P0 `add-state-driven-window-sizing` change before resuming this change's remaining G3 work.
6. Harden edge cases and accessibility for G3, then stop for `APPROVE G3`.
7. Archive/synchronize this design change only after G3 approval.

Rollback is commit-based: reject or revert the narrow implementation tranche that diverges from the last approved gate. Real yt-dlp integration belongs to a separate future OpenSpec change.
