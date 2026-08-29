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
- Keep window motion calm, interruptible, accessible, and subordinate to user window management.
- Reuse the current state model, Jewel composition, Compose animation APIs, and review harness.
- Produce deterministic automated and native-window evidence for all behavior.

**Non-Goals:**

- A generic responsive-layout framework, animation framework, or reusable multi-window manager.
- Different outer sizes for every one of the six states.
- Persisting window bounds or sizing ownership across launches.
- Animating width during automatic tier changes.
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

Automatic tier changes preserve the current width. G0 calibrates one preferred width and compact/expanded height profiles against actual Jewel metrics; existing `620–720 dp` widths and `420 dp` expanded height are useful starting evidence, not requirements. Each profile also owns its minimum usable height.

The upper-left content origin remains fixed, so normal growth moves the bottom edge downward and leaves the URL field in place. Width remains user-resizable and the existing compact-height branch continues to adapt spacing.

Alternative rejected: animate both width and height. It reflows the URL, media title, destination, and controls while adding motion that provides no extra task information.

### 3. Keep the existing Jewel window and Compose window state

`ProductWindow` continues to own `DecoratedWindow` and the existing `WindowState`. Add the minimum sizing policy beside this owner rather than in `DownloadStateHolder`; download state remains product behavior, while outer bounds remain window presentation.

Use one tiny pure policy mapping current UI state plus sizing ownership to a target tier/bounds request. No interface, factory, service, or new module is needed. A separate file is warranted only if the pure policy and its focused test would otherwise make `Main.kt` mixed-responsibility again.

Alternative rejected: migrate to experimental Window API v2 now. Its intrinsic sizing is useful, but Jewel compatibility and migration add unrelated risk when the current mutable state already supports the required size changes.

### 4. Use one interruptible height animation

Use one Compose `Animatable` for logical window height at the window owner. App-managed expansion uses the Windows normal duration (`250 ms`); collapse uses the fast duration (`167 ms`). Expansion decelerates into place; collapse accelerates out. No spring, bounce, overshoot, stagger, or ambient motion is introduced.

Each animation starts from the actual current height and targets the newest tier, so rapid link edits or forced review states retarget without an intermediate snap. The existing body `AnimatedContent` remains responsible only for fade/rise content treatment and is synchronized to the same tier transition. Do not stack `animateContentSize` on the root window boundary; two independent geometry animations would lag or fight.

When the effective Compose motion-duration scale is zero, snap both outer bounds and body content to the final state. Status remains represented by text and semantics.

Alternative rejected: an AWT timer or custom frame loop. Compose already provides lifecycle-aware cancellation, retargeting, and duration scaling.

### 5. Track automatic versus user sizing ownership

Sizing starts `AutoManaged` on each launch. The window owner records its latest app-issued bounds and observes actual floating-window bounds after initialization and after animations settle.

- A non-app size change switches ownership to `UserManaged`.
- `AutoManaged` tier changes animate to the calibrated profile.
- `UserManaged` tier changes never shrink. They preserve current bounds, growing only to the target tier's minimum if required for essential content.
- A manual resize during animation cancels the animation and switches to `UserManaged`.
- Maximized, snapped, or full-screen placement suspends floating-bound requests. Restoring reconciles to the current tier only when ownership is still automatic.

Use a small tolerance for platform rounding and DPI conversion so app-issued bounds are not misclassified as manual input. Ownership is intentionally not persisted; relaunch restores the product's compact default.

Alternative rejected: always force the state target. It would override a user's chosen workspace and could pull a maximized/snapped window back to floating bounds.

### 6. Bound targets to the active Windows work area

Use the current `ComposeWindow`/AWT graphics configuration and screen insets to calculate the active monitor work area. Convert once through Compose density at the window owner.

If a preferred target fits, preserve position. If growth would cross the bottom or right work-area edge, shift the window only enough to keep it visible. If the available work area cannot hold the tier's preferred or minimum height, cap the bounds and enable a vertical overflow path inside the product surface so all essential controls remain reachable.

Normal compact and expanded targets should not scroll. Overflow exists only for constrained screens, high scaling, or user-managed undersizing.

Alternative rejected: recenter on every tier change. Repositioning the whole window would break spatial continuity and move the user's pointer target.

### 7. Keep minimum-size changes synchronized with tiers

Compact needs a smaller native minimum than today's `620 × 350`. Before collapse, lower the native minimum so the animation can complete. During expansion, animate first and raise the expanded minimum after reaching the target, avoiding an operating-system jump to the minimum. When the work area is smaller than the desired minimum, use the bounded overflow fallback instead of requesting impossible dimensions.

Alternative rejected: one compact global minimum for all states. It would allow the expanded work surface to be resized into an unusable sliver during ordinary operation.

### 8. Preserve current Jewel and Compose components

- Title bar and native controls: existing Jewel `DecoratedWindow` and `TitleBar`.
- Link input: existing Jewel `TextField`, label, validation text, and resolving indicator.
- Expanded surface: existing Compose `Box` work plane and Jewel media choices/actions.
- Motion: Compose animation core only.
- Overflow fallback: the narrowest Compose/Jewel scrolling primitive that exposes a visible desktop scrollbar when overflow exists.

No new card, page, dialog, route, or decorative element is added.

### 9. Extend review controls only for deterministic proof

Existing forced-state controls already cover tier mapping. Add only the smallest development-only seams needed to force normal versus zero-duration motion and reset sizing ownership. Manual resize, maximize/restore, screen-edge behavior, and title-bar/window controls remain native interaction checks rather than simulated product controls.

Automated checks cover pure state-to-tier and ownership policy plus existing product-flow regressions. Compose Hot Reload evidence covers client layout, state transitions, focus, semantics, constrained overflow, themes, and scaling. Codex Computer Use `Windows.Graphics.Capture` covers native outer bounds, title bar, manual resize, maximize/restore, and on-screen placement from the same exact commit.

### 10. Reconcile the active fixed-window change before code

Before implementation, update `PRODUCT.md`, `DESIGN.md`, and the active `design-primary-download-window` proposal/design/spec/tasks wherever they require fixed geometry or explicitly forbid expansion. Preserve already accepted state, copy, visual language, and fake transitions; replace only geometry, motion, resize evidence, and affected acceptance criteria. This P0 change then becomes the authority for window behavior while the older change remains authority for the rest of the product surface.

## Risks / Trade-offs

- [Native bounds updates may look stepped on some Windows/JBR combinations] → Use one Compose animation driver, round consistently, inspect frame behavior on the target JBR, and fall back to the shortest acceptable transition if per-frame resizing is visibly poor.
- [Platform rounding or DPI changes may look like manual resizing] → Compare actual and issued bounds with a small tolerance and ignore initialization/active-animation events.
- [Changing native minimum size at the wrong moment can force a jump] → Lower before collapse; raise only after expansion completes; cover both orders in focused tests and native interaction evidence.
- [A window near a work-area edge may need to move] → Preserve the upper-left anchor by default and apply only the minimum corrective shift required for visibility.
- [Automatic shrinking can surprise users] → Shrink only while ownership remains automatic; any manual resize disables later auto-shrink for that launch.
- [Client and outer-window animations can drift] → Drive both from the same tier and motion constants; keep only one owner for geometric height.
- [Research guidance targets several UI stacks] → Apply platform-level principles, then verify actual Compose Desktop/Jewel behavior on Windows rather than assuming WinUI mechanics.

## Migration Plan

1. Reconcile conflicting planning and design statements, then prepare G0 adaptive-window evidence and stop for `APPROVE G0`.
2. Implement the pure tier/ownership policy and focused tests without visual changes.
3. Wire compact launch and interruptible height changes into `ProductWindow`; adjust `ProductSurface` only enough to remove reserved height and support constrained overflow.
4. Extend the review harness, run G1/G2 review and correction, then capture final G3 evidence once.
5. Rollback, if needed, removes the sizing coordinator and restores the prior fixed target/minimum constants; no data or backend migration exists.
