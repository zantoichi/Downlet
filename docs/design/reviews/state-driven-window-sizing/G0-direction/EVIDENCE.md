# G0 Evidence

Date: 2026-08-29
Change: `add-state-driven-window-sizing`

## Exact Commits

- `cd31877` — reconciled live product/design requirements, preserved historical gate evidence, added the P0 dependency, and validated both active changes.
- `b4dc4c1` — recorded measured Compact/Expanded profiles, runtime probes, scaling assumptions, and retained captures.
- Package commit — the commit containing this file and `manifest.json`; reported with the G0 handoff.

## Planning And IDE Checks

- `openspec validate add-state-driven-window-sizing --type change --strict --json --no-interactive`: PASS, 1/1 valid, zero issues.
- `openspec validate design-primary-download-window --type change --strict --json --no-interactive`: PASS, 1/1 valid, zero issues at reconciliation commit `cd31877`.
- IntelliJ inspections: zero unresolved problems in the reconciled planning files, `DIRECTION.md`, `MEASUREMENTS.md`, and the task list.
- IntelliJ unchanged-code build before G0 documentation: PASS, zero problems.
- `git diff --check`: PASS before each G0 evidence commit.

## Runtime Evidence

- Baseline requested outer window: `720 × 420`.
- Baseline semantic/client root: `704 × 412`.
- Empty visible content ends at semantic `y=133`; measured Compact target is `720 × 168`, minimum `620 × 156`.
- Ready fits at `720 × 380`.
- Error at `720 × 380` hides Retry; Error at `720 × 400` and `620 × 400` keeps required content visible.
- Reference monitor: `2560 × 1440`, work area `2560 × 1392`, `96 DPI`, 100% scaling.
- Compose runtime status was healthy, reload state `ok`, with no product UI exception observed.

## Capture Inventory

State/theme baselines at current `720 × 420`:

- `captures/empty-light-native-720x420.jpg`
- `captures/resolving-light-native-720x420.jpg`
- `captures/ready-light-native-720x420.jpg`
- `captures/empty-dark-native-720x420.jpg`
- `captures/resolving-dark-native-720x420.jpg`
- `captures/ready-dark-native-720x420.jpg`
- `captures/empty-light-720x420.png` — verified Compose client-area baseline.

Height calibration:

- `captures/ready-light-native-720x380.jpg`
- `captures/error-light-native-720x380.jpg` — Retry absent.
- `captures/error-light-native-720x400.jpg` — Retry visible.
- `captures/error-light-native-620x400.jpg` — required content visible at minimum width.
- `captures/error-light-native-720x420.jpg` — preferred breathing room.

All 12 files exist and decode as images. Native `720 × 420` captures contain about `706 × 413` pixels; the verified Compose client capture is `704 × 412`.

## Evidence Boundary

G0 proves the direction and calibrated targets only. It does not prove implemented resizing, animation, ownership, work-area clamping, scaling behavior, or accessibility behavior. Those remain gated G1/G2 work. Corrupt post-mutation Compose screenshots and timed-out dynamic semantic-tree attempts were deleted and are not cited.
