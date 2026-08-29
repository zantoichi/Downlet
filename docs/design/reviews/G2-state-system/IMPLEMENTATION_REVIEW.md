# G2 Combined Implementation Review

Date: 2026-08-29  
Status: CORRECTED — READY FOR FINAL EVIDENCE
Review task: `01a04b8b-9efe-7be0-a029-ef182814c326`  
Reviewer: GPT-5.6 Sol High  
Reviewed HEAD: `c0700e0921b84e6079e9a2cf3dcbe3247185b825`  
Implementation commit: `97e25d586c61ae0dfdae1896048d3b2a72a30dc3`  
Packet commit: `c283fe939bdcc0f2bef905770ca56e80f634992d`  
Mode: read-only, shared clean `main`  
Brooks health score: 89/100

## Verdict

Do not capture final G2 evidence or request G2 approval until the four findings below are corrected in one bounded Sol High implementation pass and focused regression checks pass. Optional items remain deferred to G3.

## Correction outcome

Correction task: `01a04ba2-9dba-7111-91a1-52606e0c225b`

Correction commit: `bd683366a8deb04b737652809bf7bad24571ec77`
Result: all four findings corrected in one implementation pass; tasks `4.2`, `4.3`, `4.3a`, `4.7`, `4.9`, and `4.11` rechecked.

- State-machine submissions are mutex-serialized and progress events carry an atomic generation that is invalidated at every required boundary.
- A production-like single-lane dispatcher regression proves stale same-fixture progress cannot overwrite a forced Downloading state.
- Compose 1.12 `AnimatedContent.contentKey` now maps all work-plane states to one key, preserving one composition and suppressing later-state shell animation.
- Retry uses Jewel `InlineErrorBanner.linkActions`.
- Visible fallback copy is `Preview unavailable`, and the SVG now follows the selected monochrome frame-and-signal direction.

Verification passed: 27/27 focused state tests, 2/2 smoke flows, `gradlew.bat check`, IntelliJ build and changed-file inspections, both IntelliJ launch configurations, strict OpenSpec validation, controller UI-error check, and runtime logs. Smoke timings were 2.922315700 seconds for the happy flow and 196.869700 milliseconds for recovery.

Correction-only product screenshots were rejected because Compose MCP remained attached to an older stale product session while the current `Design Review` process owned both new native windows. No invalid screenshot is evidence. Task `4.12` must establish fresh exact-commit product attachment and visually prove the shared plane, Error action, and missing-preview fallback before G2 approval.

## Required findings

### 1. Stale progress can cross a forced-state boundary

Severity: Warning  
Owning tasks reopened: `4.2`, `4.9`

Evidence: `src/main/kotlin/downlet/DownloadStateHolder.kt` around machine setup, event validation, event submission, and progress scheduling; `src/test/kotlin/downlet/DownloadStateTest.kt` test-dispatcher setup.

Machine events are submitted from separate coroutines on multi-threaded `Dispatchers.Default`. KStateMachine queues events received while another event is processing. The progress guard compares only the fixture, so a same-fixture progress event already queued can execute after `ForceDownloading`. Existing tests use `Dispatchers.Unconfined`, hiding this scheduling boundary.

Consequence: a stale timer event can overwrite forced progress. Forced fixtures, cancellation, and reset behavior become timing-dependent, violating the deterministic state contract.

Required correction: add a monotonic download-generation token, capture it in scheduled progress events, reject stale generations, and invalidate the generation on cancel, edit, reset, force, completion, error, and close. Serialize machine submissions with limited parallelism or a narrow lock. Add one production-like same-fixture force-during-active-timer regression test.

### 2. Shared work plane is recreated and reanimated

Severity: Warning  
Owning task reopened: `4.3`

Evidence: `src/main/kotlin/downlet/ProductSurface.kt` in `ProductBody`.

`AnimatedContent` is keyed by the concrete state class. Ready and later branches each instantiate `WorkPlane`, causing the full plane to fade and rise again across Ready → Downloading → Completed/Error.

Consequence: this breaks Quiet Signal Reveal's “reveal once, update in place” contract, adds unnecessary motion, and duplicates the stable shell.

Required correction: render one shared `WorkPlane` for Ready, Downloading, Completed, and Error. Animate only its initial reveal from pre-ready states; replace only the inner state/action region afterward.

### 3. Retry bypasses Jewel's banner-action API

Severity: Suggestion, accepted as required G2 correction  
Owning task reopened: `4.7`

Evidence: `src/main/kotlin/downlet/ReadyContent.kt` in `ErrorActionRegion`.

The banner places a generic `Link("Retry")` inside its content body although Jewel `InlineErrorBanner` provides the native `linkActions` slot.

Consequence: Downlet manually owns action layout and semantics that belong to the component API, allowing styling, spacing, accessibility behavior, and future Jewel changes to diverge.

Required correction: move Retry into `InlineErrorBanner.linkActions`. Keep only the explanatory error message in the banner body.

### 4. Missing-preview copy and resource regress from the selected direction

Severity: P2 Impeccable  
Owning task reopened: `4.3a`

Evidence: `src/main/kotlin/downlet/ReadyContent.kt` missing-preview branch, `src/main/composeResources/drawable/preview_unavailable.svg`, and `docs/design/concepts/G2/SELECTION.md`.

The visible fallback says `No preview`, while the approved baseline, specification, and semantic description use `Preview unavailable`. The SVG is a generic slashed-landscape icon despite the selected frame-and-signal direction.

Consequence: visible and accessibility language disagree, and the production resource regresses from the selected design direction.

Required correction: restore visible copy to `Preview unavailable`, using two compact lines if needed. Redraw the monochrome SVG around the selected frame-and-signal motif, then verify it at 96 and 128 dp in light and dark themes.

## Strengths verified

- Six explicit flat states and the required 0/18/43/68/87/100 timeline are present.
- Success, failure, Cancel, Retry, Open Folder, Download Another, and Reset preserve the required data and outcomes.
- G1 paste, typing debounce, resolution timing, validation, and download-time locking remain intact.
- No backend, I/O, DI, repository/service layer, ViewModel, second state engine, broad suppression, or runtime image dependency was added.
- Progressive disclosure, hierarchy, light/dark treatment, and production-resource bounds are otherwise strong.

## Checks and reused evidence

- IntelliJ production inspections: no warnings or errors.
- `DownloadStateTest`: 26 passed, 0 failed.
- Exact-implementation smoke evidence: 2 passed, 0 failed; happy path 2.6796 s; recoverable path 383.49 ms.
- Existing G2 state captures, contact sheet, source, tests, resources, OpenSpec artifacts, and selection record were reviewed.
- Compose Hot Reload session was healthy; controller semantics, UI errors, logs, and targeted state/theme checks were inspected.
- Whole-product semantics may stall under Compose Hot Reload 1.2.0; no unsupported successful-tree claim is made.

## Deferred to G3

- Extreme-title validation remains task `5.2`.
- Complete 620×350 later-state coverage, 125%/150% scaling, keyboard order, and full semantic proof remain tasks `5.2`–`5.9`.
- A stale/composited targeted 620×350 capture was rejected and is not evidence.

## Correction boundary

Completed in one top-level GPT-5.6 Sol High implementation task. No second review loop, unrelated redesign, or final evidence capture was used.
