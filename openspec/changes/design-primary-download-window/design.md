## Context

See `proposal.md` for motivation and the three capability specs for observable behavior. The repository currently contains design and OpenSpec files only; IntelliJ IDEA reports one empty Java module with no dependencies or run configurations. G0 is therefore theoretical but API-grounded. No Kotlin application code may be added before explicit G0 approval.

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
- JetBrains Runtime / toolchain 25

Jewel `0.39.1` is the current published standalone line and fixes the standalone transitive icon dependencies. Jewel `0.40` exists for the IntelliJ Platform line but does not publish a standalone version in the current release table. Compose Hot Reload `1.2.0` supports Compose Multiplatform 1.10 or newer and exposes the MCP review tools required by the prompt.

Alternative considered: follow Jewel `0.40` source APIs. Rejected because this product needs a published standalone artifact, not an IntelliJ Platform bridge.

### 2. Use a plain Jewel-decorated Windows frame

Use Jewel `DecoratedWindow` with a Compose `WindowState` near `720.dp × 420.dp`, then set the underlying window minimum size near `620 × 350` logical pixels. Configure the theme through the `IntUiTheme` styling overload with `ComponentStyling.default().decoratedWindow()`.

The title bar contains only the Downlet title and Jewel/JBR-managed Windows controls. Do not add a project stripe, menu, toolbar, breadcrumbs, or IDE actions. This keeps the whole frame in sync when the design-review harness forces light or dark mode while relying on Jewel's JBR-backed drag, resize, maximize, minimize, and close behavior.

Alternative considered: Compose Desktop `Window` with native OS chrome. Rejected because the review harness must switch the complete app frame between light and dark independently of the current Windows theme; native chrome can leave a light title bar around dark Jewel content.

### 3. Keep one pure product surface and one small state holder

Create one immutable `DownloadUiState` model with the six explicit product states and one small Compose-aware state holder that accepts UI events. Use sealed state/event types only where they make invalid combinations impossible; do not add repositories, services, factories, dependency injection, or a fake backend interface.

Platform clipboard access stays at the application edge as a function callback. The product composable receives state and event callbacks and contains no AWT type.

Alternative considered: MVVM plus service interfaces. Rejected as unnecessary for a single deterministic design surface.

### 4. Treat paste as intent while preserving normal text editing

Use Jewel's state-based `TextField(TextFieldState, ...)` API. The Paste button reads Windows clipboard text through a tiny AWT adapter, updates the field, validates with `java.net.URI`, and immediately starts fake resolution for supported YouTube hosts.

For keyboard paste, apply a Compose key-event modifier that records Ctrl+V intent before the text field handles the edit. Observe `TextFieldState.text`; the next valid pasted value resolves immediately. Other valid edits resolve after a `350 ms` idle debounce. Invalid text remains in the field and sets a restrained inline validation state.

Supported fake-validation hosts are `youtube.com`, `www.youtube.com`, `m.youtube.com`, and `youtu.be`. Validation proves only interaction behavior; it does not contact the provider.

Alternative considered: resolve every valid edit immediately. Rejected because it would make manual typing feel jumpy and would not satisfy the agreed debounce behavior.

### 5. Use a stable vertical composition with one compact-height metric switch

The product content is a single `Column`:

1. Persistent URL form row.
2. Horizontal Jewel `Divider`.
3. One state body occupying the remaining space.

Default metrics use approximately 20 dp outer padding, 16 dp major gaps, 8 dp control gaps, and a 96 dp label column. Below roughly 380 dp of client height, one `BoxWithConstraints` branch reduces outer padding and major gaps to 16/12 dp and reduces the media thumbnail while preserving the same information hierarchy. This is desktop resize hardening, not a mobile layout.

If common Windows scaling causes true overflow, the body may use a simple vertical scroll state so essential actions remain reachable. No scrollbar or adaptive branch should appear at the default window and font settings.

Alternative considered: allow the minimum-size view to clip or force window expansion. Rejected because the minimum size is an explicit review requirement.

### 6. Keep the YouTube-link row visually ordinary

Map the row to Jewel `Text`, state-based `TextField`, and `OutlinedButton`:

- `YouTube link` uses a fixed label width aligned with later form rows.
- The text field takes remaining width and shows `Paste a YouTube link…` as placeholder text.
- Paste uses an `OutlinedButton`, not a primary button or icon-only action.
- Validation appears as one compact text line directly beneath the field rather than a card or modal.

The YouTube-link row remains present and editable in every state. Editing it during Downloading is disabled; in other states, a new valid URL restarts fake resolution.

### 7. Make Ready the reference composition

Ready uses four regions without cards:

- Media identity row: a local deterministic 16:9 image or fixed-size fallback on the left; title and one metadata line on the right.
- Format row: two Jewel `RadioButtonRow` controls for Video and Audio.
- Quality row: Jewel `ListComboBox` with an explicit fixed/fill width. The selected first item names the actual resolved best, such as `Best available — 2160p` or `Best available — 251 kbps audio`.
- Destination row: truncated Jewel `Text` plus a Jewel `Link` labeled `Change…`; the fake callback cycles between deterministic destination fixtures.

The bottom-right Download action uses Jewel `DefaultButton`. No other primary action appears.

The thumbnail uses Compose `Image` and a bundled local resource because Jewel does not need to own ordinary media imagery. The fallback uses a quiet Compose `Box`, Jewel `Icon` or text, and explicit semantics. This is the only small custom visual primitive in the main composition.

### 8. Map remaining states to native Jewel treatments

- **Empty:** YouTube-link row plus one short explanatory sentence; no placeholder illustration.
- **Resolving:** Jewel `CircularProgressIndicator` beside concise status text. Use the horizontal indeterminate bar only if the first coded review proves the spinner too weak; do not use skeletons.
- **Downloading:** Jewel `HorizontalProgressBar(progress)` plus percentage and one metadata line. Choices are disabled. Cancel is a Jewel `Link` and returns to Ready with selections preserved.
- **Completed:** a Jewel success `Icon` plus saved-location text. `DefaultButton("Open Folder")` is primary and a Jewel `Link("Download Another")` resets to Empty. In this design-only build, Open Folder leaves the state in place and shows the concise acknowledgement `Folder opening is simulated in this design build.`
- **Error:** `InlineErrorBanner` with `Modifier.fillMaxWidth()` and a banner link action labeled Retry. Preserve media context. Use the non-deprecated `InlineErrorBanner` API available in 0.39.1.

Fake timing is fixed: resolution completes after `550 ms`; download progress advances through `0, 18, 43, 68, 87, 100` at `350 ms` intervals. The failure fixture enters Error at 68 percent. Controller-forced states bypass timers.

### 9. Let Jewel supply the visual system

Use Jewel typography, component metrics, semantic colors, icons, focus outlines, disabled appearance, and light/dark theme definitions. Product-specific values are limited to:

- window target and minimum size;
- outer/major/control spacing;
- form label width;
- thumbnail dimensions and corner size;
- media/status region minimum height.

Do not define a token hierarchy, card component, generic form framework, or alternate button system. Use named parameters when calling Jewel APIs because its documented source-compatibility policy favors them.

### 10. Add semantics only where native components do not carry enough meaning

Jewel controls retain their native focus and role behavior. Add Compose semantics to:

- the media fallback;
- validation and outcome status regions;
- progress value and status text;
- full destination path when the visible text is truncated;
- grouped media metadata where individual fragments would be noisy.

Use a polite live region for Resolving, Completed, and Error status changes. Do not make decorative dividers or thumbnail decoration focusable. Do not add a global Escape handler; native popup Escape behavior remains intact.

### 11. Isolate the deterministic review controller

Provide two entry points in the same application module:

- normal product entry point: product window only;
- design-review entry point: product window plus a small `Design Review Controller` window.

The controller uses ordinary Jewel controls to force state, theme, and fixtures. It has no visual influence on the product surface and no shared layout component beyond the state holder. The `Design Review` IntelliJ run configuration launches the review entry point.

Compose Hot Reload MCP must be configured through its `hotMcpServer` Gradle task during G1. Gate evidence targets the product window ID returned by `list_windows`; the controller window is excluded from product screenshots.

### 12. Preserve the four-gate implementation topology

The root task owns OpenSpec, dispatch packets, integration, and human gates. Actual Kotlin implementation and any independent review are dispatched only to explicit top-level Codex tasks using GPT-5.6 Sol with High reasoning. Project subagents are not used.

Each coded gate records an exact commit and is rejected as NOT READY unless IntelliJ MCP inspection/build, relevant tests, run configuration, Compose MCP connection, screenshots, semantic trees, interactions, resize checks, UI errors, and logs all agree on that commit.

## Risks / Trade-offs

- **Jewel 0.40 is newer than the standalone artifact** → Pin the published 0.39.1 standalone coordinate and use its extracted source signatures for G0; make G1's first task a minimal IntelliJ build smoke test.
- **Custom decoration can expose platform-specific drag, scale, or window-control defects** → Use Jewel's JBR-backed `DecoratedWindow` and `TitleBar` without custom hit regions, then prove drag, maximize/restore, 125/150 percent scaling, and light/dark frame parity on Windows before a coded gate passes.
- **Minimum height is tight in Ready** → Use one compact-height metric branch and prove exactly 620 by 350 through Compose MCP before G1 review.
- **Paste intent and text-field edits can race** → Keep one short-lived paste-intent flag, clear it after the next edit, and add a small state-holder test for paste-immediate versus type-debounced behavior.
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
