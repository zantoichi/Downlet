# G0 Measurements And Capture Notes

Date: 2026-08-29
Planning baseline commit: `cd31877`

## Source inspection

- `Main.kt`: fixed launch `720 × 420`; fixed AWT minimum `620 × 350`.
- `ProductSurface.kt`: `BoxWithConstraints`; below `400.dp` client height uses `16.dp` outer padding and `12.dp` major
  gap. Link field is `28.dp` high with a `20.dp` validation slot. Empty/Resolving do not use `weight(1f)`.
- `ReadyContent.kt`: compact work plane uses a `96.dp` 16:9 preview, `8.dp` control gaps, `96.dp` labels, `300.dp`
  quality control, and state-specific action regions.
- `DesignReviewApp.kt`: deterministic controls force all six states and light/dark themes in a separate product window.
- IntelliJ build before planning edits: PASS, zero problems.

## Runtime bounds

Compose window listing at the baseline:

- Downlet outer request: `720 × 420`.
- Semantic root: `704 × 412`.
- Jewel title bar: `704 × 40`; divider: `1`.
- Product body: `704 × 371`.
- Windows.Graphics.Capture content: about `706 × 413`; decoration/shadow differences explain the small delta from
  requested outer bounds.

Empty semantic nodes:

- Label: `x=16, y=62, 96 × 16`.
- URL field: `x=112, y=57, 576 × 28`.
- Helper/status: `x=16, y=117, 672 × 16`; visible content ends at `y=133`.

This yields Compact root heights `148` minimum and `160` preferred, or outer heights `156` and `168` using the observed
8-pixel outer/root delta.

## Expanded height probes

| Probe             | Observation                                             |
|-------------------|---------------------------------------------------------|
| Ready `720 × 380` | Fits with no clipping.                                  |
| Error `720 × 380` | Retry is not visible; insufficient minimum.             |
| Error `720 × 400` | Error title, body, Retry, and bottom inset are visible. |
| Error `620 × 400` | Same required content remains visible at minimum width. |
| Error `720 × 420` | Fits with normal breathing room.                        |

## Monitor probe

- Device: `\\.\DISPLAY1`.
- Physical bounds: `2560 × 1440`.
- Work area: `2560 × 1392`.
- Window DPI: `96`; scale: `1.0`.

125% and 150% cases are calculated from the same logical profile tokens and physical work area. G1/G2 must verify those
scales on Windows because DPI rounding and Jewel metrics can differ from arithmetic estimates.

## Captures retained

All paths are relative to this directory.

### State/theme baseline at current `720 × 420`

- `captures/empty-light-native-720x420.jpg`
- `captures/resolving-light-native-720x420.jpg`
- `captures/ready-light-native-720x420.jpg`
- `captures/empty-dark-native-720x420.jpg`
- `captures/resolving-dark-native-720x420.jpg`
- `captures/ready-dark-native-720x420.jpg`
- `captures/empty-light-720x420.png` — Compose client-area capture before state mutation.

### Height calibration

- `captures/ready-light-native-720x380.jpg`
- `captures/error-light-native-720x380.jpg`
- `captures/error-light-native-720x400.jpg`
- `captures/error-light-native-620x400.jpg`
- `captures/error-light-native-720x420.jpg`

## Capture-tool limitation

Compose state control, window listing, status, build, UI-error, and log calls worked. After a state/theme mutation,
Compose screenshot payloads intermittently contained stale pixels from an unrelated foreground surface, and dynamic
semantic-tree calls timed out. Those mismatched files were deleted and are not evidence. The one verified pre-mutation
Compose Empty capture is retained; Windows.Graphics.Capture supplied the state/theme and height-calibration images. G1
must re-check Compose capture after implementation and must not treat this G0 limitation as proof of coded behavior.

## Runtime health

- Compose connection: healthy, reload state `ok`.
- UI error for verified Empty baseline: none.
- Recent logs: semantic/screenshot requests only; no product exception observed.
- Production Kotlin source changed during G0: no.
