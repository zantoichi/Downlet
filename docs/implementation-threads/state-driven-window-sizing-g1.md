# G1 Implementation Packet: State-Driven Window Sizing

Status: DISPATCHED

## Assignment

Implement OpenSpec change `add-state-driven-window-sizing`, tasks 2.2–2.8 only, in one explicit top-level Codex task using GPT-5.6 Sol with High reasoning.

- G0 approval: `APPROVE G0`
- Approval record: `docs/design/reviews/state-driven-window-sizing/G0-direction/DECISION.md`
- Approved package commit: `df93b1a`
- Approval-record commit: `3a822a4`
- Dispatch base: `0c2a0bf`
- Dispatched task ID: `01a04d3d-28a4-7532-9b00-d547eb15b0a7`
- Setup client ID: `client-new-thread:6c55ee1a-abdb-4f00-ac00-5c67f08710d9`
- Project subagents: forbidden
- Stop after tasks 2.2–2.8. Do not perform task 2.9 review or package G1 evidence.

## Approved Profiles

| Profile  | States                                      | Preferred bounds | Minimum bounds |
|----------|---------------------------------------------|------------------|----------------|
| Compact  | Empty, invalid Empty, Resolving             | `720 × 168`      | `620 × 156`    |
| Expanded | Ready, Downloading, Completed, Error        | `720 × 420`      | `620 × 400`    |

- Preserve the current width during automatic transitions.
- Compact→Expanded: `250 ms`, direct deceleration, interruptible.
- Expanded→Compact: `167 ms`, direct acceleration, interruptible.
- Effective duration scale `0`: snap to the same final bounds/content.
- Ready expands once. Downloading, Completed, and Error stay Expanded.
- Edit resolved link, Reset, and Download Another collapse only in AutoManaged mode.

## Required Reading

Read before editing:

- `openspec/changes/add-state-driven-window-sizing/{proposal.md,design.md,tasks.md}`
- `openspec/changes/add-state-driven-window-sizing/specs/state-driven-window-sizing/spec.md`
- `docs/design/reviews/state-driven-window-sizing/G0-direction/{DECISION.md,DIRECTION.md,MEASUREMENTS.md,REVIEW.md,EVIDENCE.md,manifest.json}`
- `PRODUCT.md`
- `DESIGN.md`

## Expected Source Surface

- `src/main/kotlin/downlet/Main.kt`
- `src/main/kotlin/downlet/ProductSurface.kt`
- `src/main/kotlin/downlet/ReadyContent.kt` only if the constrained overflow needs a local adjustment
- `src/main/kotlin/downlet/DesignReviewApp.kt`
- one focused sizing policy/coordinator file near the product window
- `src/test/kotlin/downlet/ProductSmokeTest.kt`
- one focused sizing-policy test file

Reuse the existing Compose/Jewel window state, transition, layout, and deterministic review-harness patterns. Add no dependency, service layer, persistence, settings UI, backend seam, width animation, third tier, or generic screen-management abstraction.

## Implementation Sequence

### Task 2.2 — Pure policy

Add the smallest pure model for:

- state→Compact/Expanded mapping;
- AutoManaged/UserManaged ownership;
- suspended requests for maximized/snapped/full-screen placement;
- shrink suppression in UserManaged mode;
- minimum-required growth for undersized UserManaged bounds.

Leave one focused deterministic test file covering every branch.

### Task 2.3 — Profiles and compact layout

- Launch at Compact preferred bounds.
- Use Compact minimum bounds until growth begins.
- Remove reserved work-plane allocation from Empty, invalid Empty, and Resolving.
- Enter Expanded preferred bounds on first Ready.
- Keep all later states Expanded.
- Capture Empty, invalid Empty, Resolving, and Ready in light/dark after the coded pass.

### Task 2.4 — Interruptible height motion

- Animate height only.
- Start retargeting from the currently rendered height.
- Cancel stale targets.
- Keep the URL field anchored and interactive.
- Synchronize the existing body reveal with the tier change; do not add a second animation system.
- Honor zero-duration mode.

### Task 2.5 — User and platform precedence

- Detect non-app floating resize using a small DPI/rounding tolerance.
- Manual input cancels app motion and switches to UserManaged.
- Never automatically shrink UserManaged bounds.
- Grow UserManaged bounds only to the minimum usable current-tier size.
- Suspend floating requests while maximized, snapped, or full-screen.
- Restore without replacing the user's floating placement.

### Task 2.6 — Work area, minimum ordering, overflow

- Determine the active monitor work area with existing AWT APIs.
- Preserve top/left when the target fits.
- Near right/bottom edges, apply only the smallest corrective shift.
- Cap preferred bounds to available work area.
- If Expanded minimum cannot fit, expose one vertical overflow path inside the work plane.
- Raise Expanded minimum after growth; lower to Compact minimum before collapse.
- Keep normal approved bounds scrollbar-free.

### Task 2.7 — Deterministic review controls

Extend the existing separate Design Review Controller with only:

- normal versus zero-duration motion;
- restore AutoManaged sizing;
- Reset returning to canonical Compact Empty.

Keep all existing six state/theme/fixture controls working. Do not simulate native maximize or snap in product UI.

### Task 2.8 — Regression coverage

Cover:

- all six state-tier mappings and invalid Empty;
- ownership decisions and shrink suppression;
- stale-target cancellation / rapid retargeting;
- Compact→Expanded→Completed;
- failure/retry staying Expanded;
- Download Another returning Compact;
- work-area clamp and minimum ordering where they are pure enough to test deterministically.

## Required Verification

Use RTK for noisy shell output. Required checks:

1. IntelliJ inspection of every edited Kotlin/Markdown file.
2. IntelliJ project build.
3. `gradlew.bat check`.
4. `gradlew.bat smokeTest`.
5. Launch both Downlet and Design Review configurations.
6. Compose runtime status, UI-error, and log checks.
7. Compose client-area checks for Compact Empty, invalid Empty, Resolving, Expanded Ready, later-state stability, collapse, normal motion, zero-duration, and rapid retargeting.
8. Native Windows full-window proof for manual resize, resize during animation, minimum-required growth, near-edge correction, maximize/restore, available snap behavior, title-bar theme, and window controls.

## Evidence Rules

- Commit coherent task slices and mark tasks 2.2–2.8 complete immediately after their acceptance checks pass.
- Tie every claim to the exact commit that produced it.
- Store implementation evidence under `docs/design/evidence/state-driven-window-sizing/G1/working/` or a narrower equivalent that task 2.11 can later curate.
- Keep failed or exploratory captures out of canonical evidence.
- Record tool limitations honestly; use native Windows capture when outer-window behavior is the claim.
- Do not create final G1 `REVIEW.md`, `EVIDENCE.md`, or `manifest.json`; task 2.11 owns the post-review canonical package.

## Stop Condition

Stop when tasks 2.2–2.8 are implemented, checked, committed, and their working evidence is recorded. Report changed files, commits, verification results, known limitations, and the exact candidate commit for task 2.9 review.
