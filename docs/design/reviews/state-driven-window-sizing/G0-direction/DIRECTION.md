# G0 State-Driven Window Direction

Status: PROPOSED — explicit G0 approval required
Date: 2026-08-29

## Decision

Use one stable-width primary window with exactly two automatic height tiers.

| Profile  | States                               | Preferred outer bounds | Minimum outer bounds | Estimated preferred semantic/client root | Estimated minimum semantic/client root |
|----------|--------------------------------------|------------------------|----------------------|------------------------------------------|----------------------------------------|
| Compact  | Empty, Resolving, invalid Empty      | `720 × 168`            | `620 × 156`          | `704 × 160`                              | `604 × 148`                            |
| Expanded | Ready, Downloading, Completed, Error | `720 × 420`            | `620 × 400`          | `704 × 412`                              | `604 × 392`                            |

All values are logical pixels / Compose dp. Width is never animated. `720` remains preferred because the current
704-pixel semantic root gives the label, URL field, media title, quality control, destination, and actions ordinary
desktop breathing room. `620` remains the minimum width because the current layout works at that width and the 96-pixel
label column still leaves a useful field/control width.

## Why these heights

### Compact

Current Empty semantic bounds at `720 × 420`:

- Jewel title bar plus divider: `41`.
- Compact body top padding: `16`.
- Link row including reserved validation line: `48`.
- Major gap: `12`.
- Helper/status line: `16`.
- Required root height with `15` bottom padding: `148`; adding the observed 8-pixel outer/root delta gives `156` minimum
  outer height.
- Preferred root height `160` gives `27` pixels below the helper/status; adding the same delta gives `168` preferred
  outer height.

Empty and Resolving therefore contain no work-plane allocation. The body ends after helper, validation, or resolving
status.

### Expanded

Runtime probes show:

- Ready fits at `720 × 380`.
- Error at `720 × 380` hides its Retry action.
- Error at `720 × 400` shows title, body, Retry, and bottom inset.
- Error at `620 × 400` also keeps every required control visible.
- `720 × 420` adds 20 logical pixels of normal breathing room without scrolling.

Expanded minimum is therefore `620 × 400`; preferred remains `720 × 420` because it fits the tallest current later-state
treatment without normal-condition scrolling.

## State-to-tier matrix

| Product condition                             | Tier     | Automatic request              |
|-----------------------------------------------|----------|--------------------------------|
| Launch / Empty                                | Compact  | Preferred Compact              |
| Invalid input                                 | Compact  | Stay Compact                   |
| Resolving                                     | Compact  | Stay Compact                   |
| Ready                                         | Expanded | Expand once                    |
| Downloading                                   | Expanded | No resize                      |
| Completed                                     | Expanded | No resize                      |
| Error                                         | Expanded | No resize                      |
| Edit resolved link / Reset / Download Another | Compact  | Collapse only when AutoManaged |

## Motion

- Compact→Expanded: `250 ms`, direct deceleration, no bounce or overshoot.
- Expanded→Compact: `167 ms`, direct acceleration.
- Body reveal remains the existing `200 ms` fade/rise and is keyed to the same tier change.
- Rapid retargeting starts from current rendered height.
- Effective duration scale `0`: snap bounds and content to the final state.
- Interaction remains enabled; motion never replaces text or semantics.

## Sizing ownership

| Situation                                          | Result                                                           |
|----------------------------------------------------|------------------------------------------------------------------|
| AutoManaged Compact enters Ready                   | Animate to Expanded preferred height, preserving current width.  |
| AutoManaged Expanded returns to Empty              | Lower minimum first, then collapse to Compact preferred height.  |
| User enlarges floating window to `880 × 560`       | Switch to UserManaged; later states never shrink it.             |
| UserManaged window is `620 × 300` and enters Ready | Grow only to `620 × 400`; ownership remains UserManaged.         |
| User resizes during app motion                     | Cancel motion; resulting floating bounds become UserManaged.     |
| Window is maximized, snapped, or full-screen       | Suspend floating-bound requests and preserve platform placement. |

App-issued bounds use a small DPI/rounding tolerance so normal platform conversion does not falsely claim manual
ownership. Ownership is launch-local and is not persisted.

## Work-area and scaling behavior

Reference monitor on 2026-08-29: `2560 × 1440`, work area `2560 × 1392`, `96 DPI` / `100%`.

| Scale | Effective logical work area | Compact preferred physical size | Expanded preferred physical size | Result         |
|-------|-----------------------------|---------------------------------|----------------------------------|----------------|
| 100%  | `2560 × 1392`               | `720 × 168`                     | `720 × 420`                      | Fits normally. |
| 125%  | about `2048 × 1114`         | `900 × 210`                     | `900 × 525`                      | Fits normally. |
| 150%  | about `1707 × 928`          | `1080 × 252`                    | `1080 × 630`                     | Fits normally. |

Automatic growth preserves top and left when the target fits. Near a right or bottom edge, shift only enough to keep the
target inside the active work area. If the work area cannot hold preferred bounds, cap to available bounds. If it cannot
hold Expanded minimum, keep essential controls reachable through one vertical overflow path inside the work plane; do
not create a third tier or recenter by default.

## Text sketch

```text
Compact 720×168
┌──────────────────────────────────────────────────────────┐
│ Title bar                                                │ 40
├──────────────────────────────────────────────────────────┤
│  YouTube link  [ field                                 ] │ 48
│                 helper / validation / resolving status   │ 16
└──────────────────────────────────────────────────────────┘

Expanded 720×420
┌──────────────────────────────────────────────────────────┐
│ Title bar                                                │
├──────────────────────────────────────────────────────────┤
│  YouTube link  [ field                                 ] │
│  ┌──────────────── work plane ─────────────────────────┐ │
│  │ media, choices, destination, state/status, actions  │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

## Implementation boundary

G0 adds no production code. G1 should add one pure tier/ownership policy, one window-owner coordinator, and the minimum
`ProductSurface` change needed to stop reserving work-plane height in Compact. No framework, service, width animation,
persistence, backend work, or third tier is justified.
