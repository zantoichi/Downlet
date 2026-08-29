## Context

See `proposal.md` for motivation. Current `ProductWindow` creates one Jewel `DecoratedWindow` at `720.dp × 420.dp` and sets an AWT minimum of `620 × 350`; `ProductSurface` fills those bounds and progressively swaps only its body. The state model already has the six required states and the body already groups them naturally into pre-work-plane and work-plane states.

Compose Multiplatform 1.12 allows runtime window-size changes through `WindowState` and supports content-fit initial sizing. The current Jewel window accepts the existing Compose window state, so this change does not need a window API migration or a new dependency.

Research reviewed on August 29, 2026:

- Nielsen Norman Group distinguishes staged disclosure—showing the next task subset in a linear flow—from optional progressive disclosure. Downlet's link → resolve → configure/download flow is staged disclosure, so window changes should occur at task-stage boundaries rather than every state update: <https://www.nngroup.com/articles/progressive-disclosure/>.
- Microsoft recommends responsive show/hide behavior, content-appropriate default sizes, minimum usable sizes, progressively more information in larger windows, and a stable upper-left content origin during resize: <https://learn.microsoft.com/en-us/windows/apps/design/layout/responsive-design> and <https://learn.microsoft.com/en-us/windows/win32/uxguide/vis-layout>.
- Windows motion guidance defines brief platform durations of 250 ms, 167 ms, and 83 ms and favors direct, contextual motion: <https://learn.microsoft.com/en-us/windows/apps/design/motion/timing-and-easing>.
- Compose documents mutable window size, content-fit initial sizing, interruptible value animation, and size-animation primitives: <https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html>, <https://developer.android.com/develop/ui/compose/animation/value-based>, and <https://developer.android.com/develop/ui/compose/animation/composables-modifiers>.
- WCAG 2.2 guidance requires interaction-triggered nonessential motion to be disableable and recommends honoring operating-system reduced-motion preferences: <https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html>.

## Goals / Non-Goals

**Goals:**

- Make normal launch visibly link-first with no reserved work-plane height.
- Grow and shrink the native outer window at meaningful task-stage boundaries.
- Keep window motion calm, interruptible, accessible, and deterministic inside an app-owned non-resizable window.
- Reuse the current state model, Jewel composition, Compose animation APIs, and review harness.
- Produce deterministic automated and native-window evidence for all behavior.

**Non-Goals:**

- A generic responsive-layout framework, animation framework, or reusable multi-window manager.
- Different outer sizes for every one of the six states.
- Persisting window bounds or sizing ownership across launches.
- Animating width during automatic tier changes.
- Manual window resizing, maximize/full-screen enlargement, or snap-resize behavior.
- Real media resolution/download integration or any backend work.

## Decisions

### 1. Use two presentation tiers

Add one small `WindowPresentationTier` model:

| Tier | States | Visible structure |
| --- | --- | --- |
| Compact | Empty, Resolving | Title bar, link row, helper/validation/resolving status |
| Expanded | Ready, Downloading, Completed, Error | Compact structure plus the existing work plane |

Invalid input remains Empty and therefore compact. Editing a resolved link, Reset, and Download Another return to Compact. Ready-to-Downloading-to-Completed/Error transitions remain Expanded.

This is the smallest mapping that meets staged disclosure without producing distracting outer-window jitter as progress and outcomes change. Alternative rejected: six state-specific heights; differences among later-state content are too small to justify repeated native-window movement.

### 2. Preserve width and animate height only

Tier changes preserve one calibrated fixed width. G0 calibrates that width and compact/expanded heights against actual Jewel metrics; existing `620–720 dp` widths and `420 dp` expanded height are useful starting evidence, not requirements.

The upper-left content origin remains fixed, so normal growth moves the bottom edge downward and leaves the URL field in place. The primary window is non-resizable; only app-owned height changes are allowed.

Alternative rejected: animate both width and height. It reflows the URL, media title, destination, and controls while adding motion that provides no extra task information.

### 3. Keep the existing Jewel window and Compose window state

`ProductWindow` continues to own `DecoratedWindow` and the existing `WindowState`. Add the minimum sizing policy beside this owner rather than in `DownloadStateHolder`; download state remains product behavior, while outer bounds remain window presentation.

Use one tiny pure policy mapping current UI state to a target tier height. No ownership model, interface, factory, service, or generic window manager is needed. A separate file is warranted only if the pure mapping and its focused test would otherwise make `Main.kt` mixed-responsibility again.

Alternative rejected: migrate to experimental Window API v2 now. Its intrinsic sizing is useful, but Jewel compatibility and migration add unrelated risk when the current mutable state already supports the required size changes.

### 4. Use one interruptible height animation

Use one Compose `Animatable` for logical window height at the window owner. App-managed expansion uses the Windows normal duration (`250 ms`); collapse uses the fast duration (`167 ms`). Expansion decelerates into place; collapse accelerates out. No spring, bounce, overshoot, stagger, or ambient motion is introduced.

Each animation starts from the actual current height and targets the newest tier, so rapid link edits or forced review states retarget without an intermediate snap. The existing body `AnimatedContent` remains responsible only for fade/rise content treatment and is synchronized to the same tier transition. Do not stack `animateContentSize` on the root window boundary; two independent geometry animations would lag or fight.

When the effective Compose motion-duration scale is zero, snap both outer bounds and body content to the final state. Status remains represented by text and semantics.

Alternative rejected: an AWT timer or custom frame loop. Compose already provides lifecycle-aware cancellation, retargeting, and duration scaling.

### 5. Keep the primary window app-owned and non-resizable

Set the primary Jewel window non-resizable. Do not install native resize listeners, sizing ownership state, maximize/restore reconciliation, snap detection, or full-screen handling. The existing title bar keeps minimize and close; maximize is disabled or unavailable through the non-resizable window contract.

The state tier is the sole sizing authority. Every compact/expanded transition therefore converges to the calibrated fixed width and target height without racing native resize events.

Alternative rejected: retain manual resize and platform-placement precedence. It adds ownership/event-order machinery that is unnecessary for this focused utility and made the required clear-link collapse nondeterministic.

### 6. Bound targets to the active Windows work area

Use the current `ComposeWindow`/AWT graphics configuration and screen insets to calculate the active monitor work area. Convert once through Compose density at the window owner.

If a preferred target fits, preserve position. If growth would cross the bottom or right work-area edge, shift the window only enough to keep it visible. If the available work area cannot hold the tier's preferred or minimum height, cap the bounds and enable a vertical overflow path inside the product surface so all essential controls remain reachable.

Normal compact and expanded targets should not scroll. Overflow exists only for constrained screens or high scaling.

Alternative rejected: recenter on every tier change. Repositioning the whole window would break spatial continuity and move the user's pointer target.

### 7. Apply exact tier bounds without user-resize minimums

Because the primary window is non-resizable, each tier applies its exact calibrated fixed width and target height. No dynamic native minimum-size ordering or user undersize recovery is needed. When the work area is smaller than the preferred expanded height, cap to the available work area and use the bounded overflow fallback.

Alternative rejected: retain dynamic minimum-size coordination. It exists only to support user resizing and creates extra native geometry events without product value.

### 8. Preserve current Jewel and Compose components

- Title bar and native controls: existing Jewel `DecoratedWindow` and `TitleBar`, with resize/maximize unavailable and minimize/close retained.
- Link input: existing Jewel `TextField`, label, validation text, and resolving indicator.
- Expanded surface: existing Compose `Box` work plane and Jewel media choices/actions.
- Motion: Compose animation core only.
- Overflow fallback: the narrowest Compose/Jewel scrolling primitive that exposes a visible desktop scrollbar when overflow exists.

No new card, page, dialog, route, or decorative element is added.

### 9. Extend review controls only for deterministic proof

Existing forced-state controls already cover tier mapping. Add only the smallest development-only seam needed to force normal versus zero-duration motion. Fixed-width/non-resizable behavior and title-bar controls remain native interaction checks rather than simulated product controls.

Automated checks cover pure state-to-tier policy plus existing product-flow regressions. Compose Hot Reload evidence covers client layout, state transitions, focus, semantics, constrained overflow, themes, and scaling. Codex Computer Use `Windows.Graphics.Capture` covers native outer bounds, title bar, unavailable resize/maximize behavior, available minimize/close controls, and on-screen placement from the same exact commit.

### 10. Reconcile the active fixed-window change before code

Before implementation, update `PRODUCT.md`, `DESIGN.md`, and the active `design-primary-download-window` proposal/design/spec/tasks wherever they require fixed geometry or explicitly forbid expansion. Preserve already accepted state, copy, visual language, and fake transitions; replace only geometry, motion, resize evidence, and affected acceptance criteria. This P0 change then becomes the authority for window behavior while the older change remains authority for the rest of the product surface.

## Risks / Trade-offs

- [Native bounds updates may look stepped on some Windows/JBR combinations] → Use one Compose animation driver, round consistently, inspect frame behavior on the target JBR, and fall back to the shortest acceptable transition if per-frame resizing is visibly poor.
- [Platform rounding or DPI conversion may miss the exact target] → Convert in one place, round consistently, and verify final fixed-width bounds at representative scaling.
- [A window near a work-area edge may need to move] → Preserve the upper-left anchor by default and apply only the minimum corrective shift required for visibility.
- [Removing manual resize reduces workspace flexibility] → Prefer deterministic staged disclosure for this focused utility; revisit only if real product usage requires persistent larger workspaces.
- [Client and outer-window animations can drift] → Drive both from the same tier and motion constants; keep only one owner for geometric height.
- [Research guidance targets several UI stacks] → Apply platform-level principles, then verify actual Compose Desktop/Jewel behavior on Windows rather than assuming WinUI mechanics.

## Migration Plan

1. Reconcile conflicting planning and design statements, then prepare G0 adaptive-window evidence and stop for `APPROVE G0`.
2. Implement the pure state-to-tier policy and focused tests without ownership or platform-placement machinery.
3. Make the primary window non-resizable, wire compact launch and interruptible height changes into `ProductWindow`, and adjust `ProductSurface` only enough to remove reserved height and support constrained overflow.
4. Extend the review harness, run G1/G2 review and correction, then capture final G3 evidence once.
5. Rollback, if needed, removes the sizing coordinator and restores the prior fixed target/minimum constants; no data or backend migration exists.
