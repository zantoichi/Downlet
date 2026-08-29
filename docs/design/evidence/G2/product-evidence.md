# G2 Final Product Evidence

Date: 2026-08-29
Status: PASS WITH KNOWN JEWEL SMOKE-LOG DIAGNOSTIC AND DEFERRED LATER-STATE SEMANTICS
OpenSpec tasks: `4.12`, `4.13`
Product commit: `cd0d342ed92b76bb2ec00f067287b62779a46d62`

## Scope and lineage

This is the single canonical post-review G2 capture. It contains no controller screenshot, generated mockup,
production-source edit, test edit, resource edit, build edit, dependency change, G3 work, or prior G1 evidence change.

- G2 implementation: `97e25d586c61ae0dfdae1896048d3b2a72a30dc3`.
- Combined read-only review: task `01a04b8b-9efe-7be0-a029-ef182814c326`, reviewed HEAD
  `c0700e0921b84e6079e9a2cf3dcbe3247185b825`, verdict `CORRECTION REQUIRED`.
- Correction task: `01a04ba2-9dba-7111-91a1-52606e0c225b`, commit `bd683366a8deb04b737652809bf7bad24571ec77`.
- Review-correction integration: product commit `cd0d342ed92b76bb2ec00f067287b62779a46d62`.
- Review record: `docs/design/reviews/G2-state-system/IMPLEMENTATION_REVIEW.md`.

## Exact-commit gates

- Branch and worktree before capture: clean `main` at exact product commit.
- IntelliJ `build_project`: PASS, zero problems, `0.956 s` observed tool duration.
- `gradlew.bat smokeTest`: PASS, exit `0`, `5.049 s` command duration, configuration cache reused.
- Happy smoke flow: PASS, `2.396241700 s` reported wall time, reached Completed.
- Recoverable smoke flow: PASS, `206.331800 ms` reported wall time, reached Error and Retry returned to Downloading.
- Accepted prior focused checks were not rerun: `gradlew.bat check` and focused state tests remain owned by correction
  commit `bd683366a8deb04b737652809bf7bad24571ec77`.

## Fresh Compose attachment

- Compose status: connected, `reloadState=ok`, no failed reloads.
- Product window: `6bace43d-807e-40cb-b371-e813a2b46352`, title `Downlet`.
- Controller window: `08362fd9-24e8-4e90-b5f6-09d27b3a4add`, title `Design Review Controller`.
- Default bounds: product `(8,48) 720x420`; controller `(800,48) 560x520`.
- Minimum evidence bounds: product `(8,48) 620x350`.
- Window IDs remained unchanged through the accepted state/theme sequence.

Compose captures contain only the product window. The requested 720x420 windows produce 704x412 PNGs; 620x350 windows
produce 604x342 PNGs because the capture is the Compose-rendered window content.

## Canonical screenshot matrix

Every accepted PNG was opened at original detail and visually inspected after capture.

| State / fixture | Theme |  Window | File                                   | Observation                                                                       |
|-----------------|-------|--------:|----------------------------------------|-----------------------------------------------------------------------------------|
| Empty           | Light | 720x420 | `g2-empty-light-720x420.png`           | Focused link field and helper only; no work plane or later-state controls.        |
| Empty           | Dark  | 720x420 | `g2-empty-dark-720x420.png`            | Same restrained hierarchy and geometry; no work plane.                            |
| Resolving       | Light | 720x420 | `g2-resolving-light-720x420.png`       | Persistent URL row plus compact spinner/status only.                              |
| Resolving       | Dark  | 720x420 | `g2-resolving-dark-720x420.png`        | Same disclosure and alignment; no media/options plane.                            |
| Ready           | Light | 720x420 | `g2-ready-light-720x420.png`           | Work plane revealed with media, choices, destination, and one primary action.     |
| Ready           | Dark  | 720x420 | `g2-ready-dark-720x420.png`            | Theme parity with clear hierarchy and restrained surface contrast.                |
| Downloading 43% | Light | 720x420 | `g2-downloading-light-720x420.png`     | Determinate progress, speed/time copy, Cancel, disabled URL/choices/destination.  |
| Downloading 43% | Dark  | 720x420 | `g2-downloading-dark-720x420.png`      | Same progress and locked-choice treatment with dark parity.                       |
| Completed       | Light | 720x420 | `g2-completed-light-720x420.png`       | Saved status is textual; Open Folder is primary; Download Another is secondary.   |
| Completed       | Dark  | 720x420 | `g2-completed-dark-720x420.png`        | Same hierarchy and stable media context in dark mode.                             |
| Error           | Light | 720x420 | `g2-error-light-720x420.png`           | Full-width Jewel error banner, exact recovery copy, native Retry link action.     |
| Error           | Dark  | 720x420 | `g2-error-dark-720x420.png`            | Error remains legible without relying on color; Retry is visible.                 |
| Ready           | Light | 620x350 | `g2-ready-light-620x350.png`           | Compact branch remains aligned and unclipped; primary action reachable.           |
| Missing preview | Light | 620x350 | `g2-missing-preview-light-620x350.png` | Exact `Preview unavailable` copy, frame-and-signal mark, 16:9 frame, no clipping. |
| Missing preview | Dark  | 620x350 | `g2-missing-preview-dark-620x350.png`  | Same copy, mark, geometry, and reachability with dark parity.                     |

No stale, wrong-window, controller, or composited frame was accepted.

## Interaction evidence

- Controller clicks forced all six states, both themes, Normal, and No preview while the product window remained
  separately targeted.
- A Compose `type_text` call replaced the Empty product field with `https://youtu.be/g2-evidence`; after the bounded
  observation wait, controller semantics reported `Current state: Ready normal`. Exact debounce and resolution timings
  are asserted by the passing smoke/state evidence, not inferred from this manual observation.
- Ready, Downloading, Completed, and Error captures retain the same product-window bounds, URL-row position, work-plane
  boundary, media identity placement, and destination row. Static evidence shows the stable shell with changed inner
  status/action content; it makes no unsupported animation-duration claim.
- Downloading visibly shows `43%`, a determinate progress bar, `5.1 MB/s · About 11 seconds remaining`, disabled
  choices, and Cancel.
- Completed visibly shows `Saved to Downloads`, secondary `Download Another`, and primary `Open Folder`.
- Error visibly shows Jewel `InlineErrorBanner` treatment with `Couldn't download this media.`,
  `Check that the YouTube link is available and try again.`, and its native `Retry` link action.

## Semantic evidence

- `g2-controller-semantics.json`: PASS. Exposes all six state controls, Reset, seven fixture controls, and Light/Dark
  controls.
- `g2-empty-semantics.json`: PASS on the single allowed product attempt. The field is focused and named, status copy is
  present, and no work-plane or premature-control node exists.
- Later-state whole-product semantic trees were not attempted because Compose Hot Reload 1.2.0 has a documented Jewel
  editor serialization stall. G3 task `5.8` owns complete Ready, Downloading, Completed, and Error semantic proof.

## UI errors and logs

- Product `get_ui_error`: `hasError=false`.
- Controller `get_ui_error`: `hasError=false`.
- Recent Compose application logs after all interactions contain expected semantic capture, click, resize, screenshot,
  and SetText entries only. No runtime exception or reload failure appears.
- Smoke output is not clean: the recoverable flow logs a known Jewel standalone `SEVERE` resource lookup diagnostic for
  `expui/general/chevronDown.svg(...LinkState...)` not found. Both smoke assertions still pass and Gradle exits `0`;
  neither live Compose window reports a UI error. Classification: `PASS_WITH_KNOWN_JEWEL_RESOURCE_ERROR`, not a Downlet
  production exception and not hidden.
- Smoke also reports JDK terminal-deprecation warnings from Jewel's `UnsafeAccessing`; these do not fail the task.

## Impeccable evidence audit

| Dimension                   |     Score | Evidence finding                                                                                                                                         |
|-----------------------------|----------:|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Accessibility               |       3/4 | Empty semantics, visible labels, textual status/error/success, and locked-state presentation pass. Complete later-state semantic proof remains G3-owned. |
| Performance                 |       3/4 | Both smoke flows complete quickly and later states retain stable geometry. No frame-time or animation-timing measurement was made.                       |
| Theming                     |       4/4 | All six material states have accepted Light/Dark evidence; missing-preview fallback also passes both themes.                                             |
| Responsive desktop behavior |       3/4 | Full state matrix passes at 720x420; Ready and missing-preview pass at 620x350. Complete later-state minimum-size coverage remains G3-owned.             |
| Anti-patterns               |       4/4 | No card grid, nested cards, gradients, glass, hero metric, IDE chrome, mobile pattern, or decorative control system appears.                             |
| **Total**                   | **17/20** | **Good**                                                                                                                                                 |

Anti-pattern verdict: PASS. Downlet reads as a compact Windows utility, not a generated dashboard. No new P0, P1, or P2
visual finding blocks G2.

## Known limitations

- No native Windows `Windows.Graphics.Capture` proof is added at G2 because this task explicitly excludes Computer Use
  and production frame behavior was not changed.
- Full later-state product semantics, complete 620x350 later-state coverage, scaling, and keyboard order remain assigned
  to G3 tasks `5.2` through `5.9`.
- Static accepted screenshots verify stable geometry and hierarchy, not transition frame timing.

## Canonical files

- `docs/design/evidence/G2/product-evidence.md`
- `docs/design/evidence/G2/g2-empty-semantics.json`
- `docs/design/evidence/G2/g2-controller-semantics.json`
- Fifteen PNGs listed in the matrix above
- `docs/design/reviews/G2-state-system/EVIDENCE.md`
- `docs/design/reviews/G2-state-system/REVIEW.md`
- `docs/design/reviews/G2-state-system/manifest.json`
