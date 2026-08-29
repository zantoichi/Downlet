# G1 Implementation Review

Date: 2026-08-29

- Reviewed evidence commit: `1eaaebd`
- Reviewed code commit: `932c2f7`
- Scope: `0c2a0bf..1eaaebd`
- Reviewer task: `01a04d79-424c-7be0-9db8-d490665d9f07`
- Verdict: **REVISE G1 — gate rejected**

## Findings

### P1 — Clear-link collapse remains unreliable

Native floating flow reproduced `706 × 161 → 706 × 413 → 706 × 176`; it remained `706 × 176` after another 700 ms. Empty content and focus returned, but approved Compact bounds did not.

Startup tracking clears `awaitingInitialBounds` only for an unmatched event, while animation bounds are recorded before mutation. Matching startup events can leave the latch armed; a later unmatched collapse event can then be misclassified. The regression test does not reproduce real ordering because it never records managed animation frames before observing startup bounds.

Required correction: make initialization an explicit completed phase independent of matched/unmatched resize classification, and add coordinator coverage matching runtime ordering.

### P1 — Platform placement permanently steals automatic ownership

Native `Ready → maximize → clear link → restore` returned Empty at `706 × 413`, not Compact.

Every transition into `PlatformManaged` unconditionally changes ownership to `UserManaged`. After restore, Empty therefore preserves expanded height. Approved design requires platform placement to suspend requests and restore prior ownership before reconciling the current tier.

Required correction: track placement separately from ownership. Preserve prior AutoManaged/UserManaged state while maximized, snapped, or full-screen; reconcile after returning to floating.

### P2 — Constrained overflow lacks a visible affordance

The work plane applies vertical scrolling without rendering a desktop scrollbar. This remains a constrained-layout refinement finding; task 2.10 will not expand into unrelated layout work.

### P2 — DPI behavior remains unproved

AWT bounds and work-area integers cross Compose density conversion during mutation, but no targeted non-100% native proof exists. This remains assigned to G2 task 3.3 and must not be claimed by G1 evidence.

## Accepted risk

- Two-tier mapping and later-state stability are coherent.
- Height animation uses approved durations/easing and cancellable effects.
- User-managed floating shrink suppression is represented correctly in pure policy.
- Focus and persistent status semantics passed targeted inspection.
- Left-snap heuristic is acceptable for the tested G1 configuration; broader snap and multi-monitor coverage remains G2 work.

## Verification

- IntelliJ production inspections were clean.
- `WindowSizingTest` passed.
- Focused clear-link and rapid-retarget smoke tests passed but did not cover native ownership/event ordering.
- Repository remained clean during the read-only review.
- Downlet relaunched at baseline `706 × 161` after native checks.

## Disposition

- Accept both P1 findings for the single task 2.10 correction pass.
- Defer visible constrained-overflow affordance to G2 refinement.
- Defer 125%/150% DPI proof to G2 task 3.3.
- Do not package final G1 evidence from `1eaaebd`.

## Correction

- Correction commit: `22aa700`.
- Startup ownership now ends through an explicit sizing-completion signal, independent of matching native resize events.
- Platform placement now suspends sizing without changing prior AutoManaged/UserManaged ownership.
- Coordinator tests cover matching startup event order and ownership preservation across platform placement.
- Native floating verification repeated four times: `706 × 161 → 706 × 413 → 706 × 161` each time.
- Native `Ready → maximize → clear → restore` verification returned Empty at `706 × 161`.
- `gradlew.bat check`, `gradlew.bat smokeTest`, IntelliJ build/inspections, and strict OpenSpec validation passed.
- P1 findings: resolved. Corrected G1 candidate accepted for final evidence capture.
