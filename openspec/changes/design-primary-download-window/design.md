## Context

See `proposal.md` for motivation and the three capability specs for observable behavior. G0 is approved and the repository now contains the coded G1 Jewel surface, deterministic review harness, focused state tests, and review evidence. The approved G1 revision removes the redundant visible Paste action and raises visual/motion polish before G1 is presented again.

The recommended Impeccable direction is **The Quiet Transfer Desk**: a familiar Windows utility frame, one persistent YouTube-link row, and one restrained work area that reveals only the information required by the current state.

## Goals / Non-Goals

**Goals:**

- Produce a compact Windows-first desktop composition that is directly codable with current stable Jewel standalone APIs.
- Keep product state, fake behavior, and review fixtures deterministic enough for repeatable screenshots and semantic inspection.
- Make Empty and Ready complete at G1, all states complete at G2, and resilience/polish complete at G3.
- Keep the implementation small enough that later real integration can replace fake effects without redesigning the UI.

**Non-Goals:**

- No real media resolution, download, process execution, file write, folder opening, destination picker, persistence, packaging, telemetry, update system, or backend abstraction.
- No navigation model, settings, onboarding, history, advanced options, terminal output, or provider-general architecture.
- No custom design system beyond a handful of layout dimensions and fixture-specific status choices.
- No formal accessibility certification; this change proves the specified basic desktop baseline.

## Decisions

### 1. Pin the current standalone-compatible stack

Use this G1 baseline unless a minimal scaffold build proves a hard incompatibility:

- Kotlin `2.3.20`
- Compose Multiplatform `1.11.0`
- Jewel standalone `0.39.1-262.9437.29`
- Compose Hot Reload `1.2.0`
- JVM bytecode target `21`
- JBR `25` as the development and application runtime

Jewel `0.39.1` is the current published standalone line and fixes the standalone transitive icon dependencies. Jewel `0.40` exists for the IntelliJ Platform line but does not publish a standalone version in the current release table. Compose Hot Reload `1.2.0` supports Compose Multiplatform 1.10 or newer and exposes the MCP review tools required by the prompt. Hot Reload runs on JBR 25, while its official compatibility requirement keeps project bytecode at Java 21 or earlier; G1 therefore compiles Kotlin and any Java source to target 21 and launches the app on JBR 25.

Alternative considered: follow Jewel `0.40` source APIs. Rejected because this product needs a published standalone artifact, not an IntelliJ Platform bridge.

### 2. Use a plain Jewel-decorated Windows frame

Use Jewel `DecoratedWindow` with a Compose `WindowState` near `720.dp × 420.dp`, then set the underlying window minimum size near `620 × 350` logical pixels. Configure the theme through the `IntUiTheme` styling overload with `ComponentStyling.default().decoratedWindow()`.

The title bar contains only the Downlet title and Jewel/JBR-managed Windows controls. Do not add a project stripe, menu, toolbar, breadcrumbs, or IDE actions. This keeps the whole frame in sync when the design-review harness forces light or dark mode while relying on Jewel's JBR-backed drag, resize, maximize, minimize, and close behavior.

Alternative considered: Compose Desktop `Window` with native OS chrome. Rejected because the review harness must switch the complete app frame between light and dark independently of the current Windows theme; native chrome can leave a light title bar around dark Jewel content.

Normal launch reads `androidx.compose.foundation.isSystemInDarkTheme()` and uses that value as its initial `IntUiTheme` selection. If the platform value is unavailable, Downlet uses light. Live switching after the Windows setting changes is not required in this design change; restarting reads the setting again. The Design Review entry point supplies an explicit Light or Dark override.

Compose Hot Reload 1.2.0 screenshots intentionally exclude window-title chrome. Compose MCP remains authoritative for the client area, semantics, interactions, and resize bounds. Each coded gate that judges title-bar parity also records one Codex Computer Use `Windows.Graphics.Capture` screenshot of the real running Downlet window and a manual Windows check for theme, controls, drag, and maximize/restore at the same commit.

### 3. Keep one pure product surface and one small state holder

Create one immutable `DownloadUiState` model with the six explicit product states and one small Compose-aware state holder that accepts UI events. Use sealed state/event types only where they make invalid combinations impossible; do not add repositories, services, factories, dependency injection, or a fake backend interface.

The product composable receives state and event callbacks and contains no AWT clipboard integration. Native text-field editing is the only clipboard surface.

Alternative considered: MVVM plus service interfaces. Rejected as unnecessary for a single deterministic design surface.

### 4. Let the field handle paste and submit automatically

Use Jewel's state-based `TextField(TextFieldState, ...)` API with no visible Paste button and no proactive clipboard read. Windows `Ctrl+V` remains native text editing rather than a second product action.

Apply a Compose preview key-event modifier only to record `Ctrl+V` intent before the text field handles the edit. Observe `TextFieldState.text`; the next valid pasted value resolves immediately. Other valid edits resolve after a `350 ms` idle debounce. Invalid text remains editable and sets a restrained inline validation state. The key path never reads or monitors clipboard contents itself.

Supported fake-validation hosts are `youtube.com`, `www.youtube.com`, `m.youtube.com`, and `youtu.be`. Validation proves only interaction behavior; it does not contact the provider.

Alternative considered: resolve every valid edit immediately. Rejected because it would make manual typing feel jumpy and would not satisfy the agreed debounce behavior.

### 5. Use a stable vertical composition with one quiet work plane

The product content is a single `Column`:

1. Persistent URL form row.
2. One subtly bounded, inset tonal work plane occupying the remaining space.

The work plane is a single grouping surface, not a card grid or nested-card system. It uses theme-aware low-chroma cool neutrals, a thin boundary, and no decorative shadow. Its geometry remains stable while the state content changes in place.

Default metrics use approximately 20 dp outer padding, 16 dp major gaps, 8 dp control gaps, and a 96 dp label column. Below roughly 380 dp of client height, one `BoxWithConstraints` branch reduces outer padding and major gaps to 16/12 dp and reduces the media thumbnail while preserving the same information hierarchy. This is desktop resize hardening, not a mobile layout.

If common Windows scaling causes true overflow, the body may use a simple vertical scroll state so essential actions remain reachable. No scrollbar or adaptive branch should appear at the default window and font settings.

Alternative considered: allow the minimum-size view to clip or force window expansion. Rejected because the minimum size is an explicit review requirement.

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

State-body replacement uses one short transition: a `180–220 ms` fade combined with at most `6.dp` of vertical rise and `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`. Focus, paste, and status feedback may reuse this restrained timing. There is no bounce, infinite decorative loop, staggered choreography, or motion that delays interaction. Compose duration scaling remains authoritative so a zero animation scale produces the instant end state.

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

The root task owns planning, integration, and human gates but does not edit application code. Actual Kotlin implementation and independent review run only in explicit top-level Codex tasks using GPT-5.6 Sol with High reasoning. Project subagents are not used. Work remains sequential on shared `main` unless the user changes that decision.

Fine-grained OpenSpec task IDs are traceability units, not thread or commit boundaries. One top-level implementation task may receive a small coherent set of task IDs. Its dispatch message contains the exact base commit, change name, task IDs, goal, required context, in-scope and out-of-scope boundaries, IntelliJ and Compose checks, tests, and return contract. The task starts immediately when `main` is clean at the expected commit; a second task-ID-record commit and READY/RELEASE handshake are unnecessary. A mismatch or dirty tree stops the task before mutation.

Verification has one owner at each phase:

- The implementation task inspects changed files, runs the relevant tests and IntelliJ build, launches the affected run configuration, and captures only the affected Compose states at its exact commit.
- The root verifies the commit, clean tree, artifact completeness, and any missing or risk-sensitive claim; it does not repeat a green full suite by default.
- One read-only Sol High reviewer combines technical review with the required Impeccable critique. It reuses exact-commit implementation evidence and reruns only checks needed to reproduce a finding or replace stale/incomplete proof.
- The final evidence task runs the complete coded-gate proof once after review and any correction: IntelliJ inspections/build, tests, launch, Compose connection, screenshots, semantic trees, interactions, resize checks, UI-error/log checks, reproducible run configuration, and native Windows frame proof where Compose cannot capture title chrome.

Batch all reviewer findings before correction. Each gate allows at most one coherent correction task. A micro-correction may use targeted verification when it changes no dependency, API, state behavior, layout, or interaction and touches at most ten source lines; complete gate evidence is still recaptured once afterward. Larger changes use the normal implementation path.

Known tooling failures are not blind retry loops. Attempt a known-stalling product semantic-tree capture at most once per exact commit; final evidence must either obtain the required proof or mark the gate NOT READY. Before every screenshot, foreground and verify the exact launched product PID/window, reject any occluded capture, and keep controller windows out of product evidence.

Human gates remain G0 Direction, G1 Core Surface, G2 Full State System, and G3 Hardened Final Design. Stop at each gate until the user explicitly sends `APPROVE G0`, `APPROVE G1`, `APPROVE G2`, or `APPROVE G3`; revision feedback reopens only the owning tasks.

### 13. Prioritize code health before G1 final review

Tasks 3.24–3.28 are the immediate implementation priority. Do not resume G1 final review, evidence capture, or packaging until their exact-commit verification passes.

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

## Risks / Trade-offs

- **Jewel 0.40 is newer than the standalone artifact** → Pin the published 0.39.1 standalone coordinate and use its extracted source signatures for G0; make G1's first task a minimal IntelliJ build smoke test.
- **JBR 25 can be mistaken for the project bytecode level** → Run Gradle and Downlet on JBR 25 but set Kotlin `JvmTarget.JVM_21` and Java `--release 21` explicitly.
- **Custom decoration can expose platform-specific drag, scale, or window-control defects** → Use Jewel's JBR-backed `DecoratedWindow` and `TitleBar` without custom hit regions, then prove drag, maximize/restore, 125/150 percent scaling, and light/dark frame parity on Windows before a coded gate passes.
- **Compose MCP screenshots omit title chrome** → Treat them as client-area evidence and add a same-commit Codex Computer Use `Windows.Graphics.Capture` screenshot plus manual Windows interaction record for title-bar checks.
- **Compose 1.11 does not need to promise live Windows theme updates** → Read `isSystemInDarkTheme()` at normal launch, fall back to light, and require restart after the OS preference changes; the review controller supplies deterministic overrides.
- **Minimum height is tight in Ready** → Use one compact-height metric branch and prove exactly 620 by 350 through Compose MCP before G1 review.
- **Paste intent and text-field edits can race** → Keep one short-lived key-intent flag, clear it after the next edit, never read the clipboard proactively, and retain focused tests for paste-immediate versus type-debounced behavior.
- **Long path truncation can hide useful context** → Preserve the full path in semantics and deterministic fixtures while keeping Change visibly reachable.
- **Hot Reload MCP is not available before a Gradle app exists** → G0 records the configuration target only; G1 is blocked from review, not from coding, until the server connects to the running app.
- **Fake timing can make review flaky** → Controller-forced states bypass all delays and are the source for screenshot capture.

## Migration Plan

1. After `APPROVE G0`, create a minimal single-module Compose Desktop scaffold and prove the pinned stack through IntelliJ MCP.
2. Implement G1 from narrow task packets, then stop for `APPROVE G1`.
3. Add the complete fake state system for G2, then stop for `APPROVE G2`.
4. Harden edge cases and accessibility for G3, then stop for `APPROVE G3`.
5. Archive/synchronize this design change only after G3 approval.

Rollback is commit-based: reject or revert the narrow implementation tranche that diverges from the last approved gate. Real yt-dlp integration belongs to a separate future OpenSpec change.
