## Why

Downlet currently reveals content progressively inside a window that remains at its full height, leaving unused space before a link resolves and weakening the product's link-first focus. The primary window should instead fit the current task stage, growing only when useful controls become available and shrinking when the user returns to link entry.

## What Changes

- **P0 / highest priority:** make adaptive window sizing the next implementation tranche and block remaining G3 polish in `design-primary-download-window` until this behavior is reviewed and accepted.
- **BREAKING:** replace the prior fixed-height window contract with two content-driven presentation tiers: compact for Empty and Resolving, expanded for Ready, Downloading, Completed, and Error.
- Launch with only the title bar, YouTube-link row, and its current helper, validation, or resolving status visible; reserve no blank work-plane area.
- Animate automatic height changes with brief Windows-aligned motion while keeping the URL field and upper-left content origin visually stable.
- Keep width stable during automatic tier changes. Derive compact and expanded height tokens from the actual Jewel content and available work area instead of preserving `720×420` and `620×350` as hard requirements.
- Shrink on Reset, Download Another, or editing a resolved link; grow after successful resolution; avoid repeated outer-window resizing among later states that share the expanded work surface.
- Keep the primary window app-sized and non-resizable: preserve one fixed width, disable maximize/full-screen/manual resize behavior, and let the two presentation tiers own height deterministically. Keep ordinary minimize and close controls.
- Keep the window within the active monitor work area, support ordinary Windows scaling, and provide a reachable overflow fallback when the preferred expanded size cannot fit.
- Respect reduced-motion or zero-duration animation settings by applying the final bounds immediately. Window motion never carries status meaning without persistent text and semantics.
- Update the design-review harness, automated checks, and exact-commit evidence to cover compact launch, growth, shrink, interruption, fixed-width/non-resizable chrome, scaling, themes, and all six states.
- Keep this change design-only. Real yt-dlp, subprocess, network, ffmpeg, persistence, packaging, update, telemetry, and backend integration remain out of scope.
- Retain four human review gates: G0 adaptive-window direction, G1 first complete coded pass, G2 refined full-state and user-control pass, and G3 final accessibility/resilience acceptance. Stop after preparing G0 until the user explicitly says `APPROVE G0`; `REVISE G0: <feedback>` reopens direction work.

## Capabilities

### New Capabilities

- `state-driven-window-sizing`: State-to-tier mapping, content-fit bounds, coordinated window motion, fixed-width/non-resizable window behavior, screen-bound handling, reduced-motion behavior, and observable review evidence.

### Modified Capabilities

None. The fixed-geometry requirement exists only in the still-active `design-primary-download-window` change, not in archived main specs; this P0 change explicitly supersedes that pending contract and must be reconciled before implementation.

## Impact

- Primary code surfaces: `src/main/kotlin/downlet/Main.kt`, `ProductSurface.kt`, and the smallest supporting window-sizing policy type; `DesignReviewApp.kt` gains deterministic review controls only where required.
- Verification surfaces: `ProductSmokeTest.kt`, one focused sizing-policy test, Compose Hot Reload evidence, and native Windows full-window captures.
- Planning/design surfaces: `PRODUCT.md`, `DESIGN.md`, and conflicting fixed-window clauses in `openspec/changes/design-primary-download-window/` require reconciliation during apply.
- Dependencies: no new runtime or animation dependency. Reuse Compose Desktop/Jewel window state, Compose animation primitives, Kotlin coroutines already present, and only the minimum AWT screen APIs needed to keep app-owned bounds visible.
