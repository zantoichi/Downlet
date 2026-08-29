# G2 State System Review

Gate: G2
Status: READY FOR USER DECISION
Date: 2026-08-29
Product commit: `cd0d342ed92b76bb2ec00f067287b62779a46d62`
OpenSpec change: `design-primary-download-window`
OpenSpec tasks: `4.12`, `4.13`

## Outcome

The complete deterministic G2 state system is ready for user review. Empty, Resolving, Ready, Downloading, Completed, and Error now read as one stable Windows utility rather than separate screens. The final package uses only exact-commit product evidence captured after the accepted correction pass.

## Review and correction lineage

- Implementation commit: `97e25d586c61ae0dfdae1896048d3b2a72a30dc3`.
- Combined read-only review task: `01a04b8b-9efe-7be0-a029-ef182814c326`.
- Reviewed HEAD: `c0700e0921b84e6079e9a2cf3dcbe3247185b825`.
- Review verdict: `CORRECTION REQUIRED` for stale progress, repeated work-plane composition, banner action placement, and missing-preview direction.
- Correction task: `01a04ba2-9dba-7111-91a1-52606e0c225b`.
- Correction commit: `bd683366a8deb04b737652809bf7bad24571ec77`.
- Integrated review-correction record and exact accepted product commit: `cd0d342ed92b76bb2ec00f067287b62779a46d62`.
- Detailed review: `docs/design/reviews/G2-state-system/IMPLEMENTATION_REVIEW.md`.

## Proof

- IntelliJ build passed with zero problems.
- Product smoke passed both normal happy and recoverable failure flows.
- Fifteen product-only screenshots cover all six states in Light and Dark, plus Ready and missing-preview minimum-size cases.
- Controller semantics passed and exposes all states, fixtures, and themes.
- The single Empty product semantic-tree attempt passed with a focused, named link field.
- Product and controller report no UI error.
- Live Compose logs contain expected evidence interactions only.
- Smoke output contains the known Jewel `chevronDown.svg` missing-resource `SEVERE`; it is classified and retained, so the package does not claim clean smoke logs.
- Strict file list and hashes are recorded in `manifest.json`.

## Strengths

- Progressive disclosure is clear: Empty and Resolving remain quiet; Ready reveals useful controls once.
- Later states preserve media context and stable geometry.
- Downloading communicates determinate progress and locks choices without adding telemetry clutter.
- Completed has correct primary/secondary hierarchy and textual success.
- Error uses Jewel's native banner Retry action and plain recovery language.
- Light/Dark parity is strong across every material G2 state.
- Missing-preview treatment matches the selected frame-and-signal direction and remains readable at 620x350.
- The surface avoids dashboard, card-grid, IDE-chrome, mobile, gradient, glass, and decorative-animation patterns.

## Compromises and limits

- Whole-product later-state semantic trees are not captured because Compose Hot Reload 1.2.0 has a documented Jewel editor serialization stall. G3 task `5.8` owns full later-state semantics.
- Full minimum-size coverage for every later state, keyboard order, scaling, and extreme content remain G3 work.
- Accepted static screenshots prove geometry and hierarchy, not animation frame timing.
- No new native Windows frame capture is included because this bounded task forbade Computer Use and did not change frame behavior.

## Inspect these closely

1. Empty and Resolving in both themes: no work plane, media, choices, destination, or premature action.
2. Ready in both themes: media hierarchy, one work plane, choice clarity, destination, and single primary Download action.
3. Ready to Downloading to Completed: unchanged URL/window/work-plane/media geometry with only state content and actions changing.
4. Downloading: `43%` determinate progress, speed/time copy, locked choices, and calm Cancel link.
5. Completed and Error: primary/secondary hierarchy, non-color-only status, full-width error banner, and native Retry action.
6. Ready and missing preview at 620x350: exact `Preview unavailable` copy, frame-and-signal mark, no clipping, and reachable actions.

## Approval syntax

Approve exactly:

`APPROVE G2`

Request changes with:

`REVISE G2: <feedback>`

AWAITING USER: APPROVE G2 or REVISE G2: <feedback>
