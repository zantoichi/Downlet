# G0 Review: State-Driven Window Sizing

Status: PROPOSED — user approval required
Date: 2026-08-29

## Recommendation

Adopt one stable-width primary window with two state-driven height tiers:

| Profile  | Product states                         | Preferred bounds | Minimum bounds |
|----------|----------------------------------------|------------------|----------------|
| Compact  | Empty, invalid Empty, Resolving        | `720 × 168`      | `620 × 156`    |
| Expanded | Ready, Downloading, Completed, Error   | `720 × 420`      | `620 × 400`    |

Width stays unchanged during app-driven transitions. Compact→Expanded uses `250 ms`; Expanded→Compact uses `167 ms`. Empty and Resolving allocate no blank work plane. Ready and later states remain Expanded.

## Strengths

- Removes the current empty lower region at launch and while resolving.
- Keeps the URL row anchored because only height changes.
- Fits the tallest current Error treatment at the Expanded minimum without normal scrolling.
- Preserves user authority: manual enlargement prevents later automatic shrink; undersized user-managed windows grow only to the minimum needed for the current tier.
- Preserves platform authority while maximized, snapped, or full-screen.
- Uses two profiles and one narrow overflow fallback; no third tier, width animation, persistence, or new framework is needed.

## Trade-offs And Known Limits

- The floating window moves at task-stage boundaries, so interruption and ownership detection must avoid fighting manual resize.
- Near a work-area edge, growth may require a minimal corrective shift to remain visible.
- Measurements were captured at 100% scaling. The 125% and 150% rows are arithmetic targets that require native Windows verification during G1/G2.
- Jewel decoration and DPI rounding require a small tolerance between app-requested and observed bounds.
- Compose screenshots became unreliable after state/theme mutation during G0. Verified native captures are the visual source of truth for this package; G1 must re-check the client-area tool after implementation.

## Standards Sources

- Microsoft responsive layout and Windows visual-layout guidance: content-appropriate defaults, usable minimums, progressive disclosure, and stable upper-left origin.
- Windows motion timing and easing: brief direct durations including `250 ms` and `167 ms`.
- Compose Desktop window management and Compose animation guidance: mutable bounds and interruptible animation primitives.
- WCAG 2.2 animation-from-interactions guidance: zero-duration/reduced-motion path with identical final state.
- Nielsen Norman Group progressive disclosure guidance: resize at task-stage boundaries, not every state update.

Exact source URLs and the applied design interpretation are recorded in `openspec/changes/add-state-driven-window-sizing/design.md`.

## Inspection Points For G1

1. Launch, invalid Empty, and Resolving render at Compact bounds without reserved work-plane space.
2. First Ready expands once; Downloading, Completed, and Error do not resize again.
3. Edit/reset collapses only while sizing remains AutoManaged.
4. Manual resize cancels motion and prevents automatic shrink; minimum-required growth remains allowed.
5. Maximized, snapped, and full-screen placement suppress floating-bound requests.
6. Near-edge growth stays inside the active work area with the smallest corrective shift.
7. Zero-duration mode snaps to the same final bounds and content without stealing focus.
8. Light/dark, 100%/125%/150%, constrained height, UI errors, logs, and full-window bounds are recorded at one accepted commit.

## Package

- Direction and rationale: `DIRECTION.md`
- Measurements and capture notes: `MEASUREMENTS.md`
- Requirement reconciliation: `CLAUSE_RECONCILIATION.md`
- Evidence inventory: `EVIDENCE.md`
- Machine-readable manifest: `manifest.json`

No production implementation is authorized by this package. Only the exact response `APPROVE G0` unlocks section 2 of the change tasks.

AWAITING USER: APPROVE G0 or REVISE G0: <feedback>
