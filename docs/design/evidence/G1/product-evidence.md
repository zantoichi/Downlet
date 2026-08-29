# G1 Final Product Evidence

Status: PASS WITH DOCUMENTED READY-SEMANTICS TOOLING LIMITATION
Date: 2026-08-29
OpenSpec task: 3.22
Reviewed product commit: `4fd87b6fa40b25981bdd0ec02a5253a48db61bc1`

## Verification

- `gradlew.bat check`: PASS. Ktlint, Detekt, build, and 18/18 `DownloadStateTest` cases passed.
- IntelliJ MCP: `ProductSurface.kt` has zero problems; focused project build passed with zero problems.
- Run configuration: `Design Review` opened one Downlet product window and one controller window.
- Compose status: connected, reload state `ok`, no failed reload.
- Product and controller `get_ui_error`: `hasError=false`.
- Logs: expected reload, state/theme actions, resizing, screenshot, and semantic activity only; no runtime exception.
- No visible Paste button exists. The field remains focused in Empty, and the existing tests cover immediate paste resolution, 350 ms typed debounce, 550 ms automatic completion, supersession, and cancellation.

## Canonical visual matrix

All client captures were created from the reviewed product commit and visually inspected after bringing the exact Downlet HWND forward. This prevents an occluding desktop window from contaminating screen-backed Compose captures.

- `g1-empty-light-720x420.png`
- `g1-empty-dark-720x420.png`
- `g1-ready-light-720x420.png`
- `g1-ready-dark-720x420.png`
- `g1-ready-light-620x350.png`
- `g1-missing-preview-light-620x350.png`
- `g1-disabled-light-620x350.png`

The default client raster is 704×412 for a 720×420 window. The minimum client raster is 604×342 for a 620×350 window. Empty, Ready, missing-preview, and disabled states remain aligned, unclipped, and usable in the captured sizes.

## Native Windows frame

Exact-current-window native captures were made from the Downlet HWND with `PrintWindow(PW_RENDERFULLCONTENT)`:

- `g1-native-empty-light-full-window.png` — 720×420.
- `g1-native-ready-dark-full-window.png` — 720×420.

Both show the Downlet title, standard minimize/maximize/close controls, matching title/content theme, and no foreign pixels. Existing drag/maximize/restore behavior evidence remains valid because commit `4fd87b6` changes only decorative placeholder semantics, not window code.

## Semantic results

- Empty product tree: PASS in under two seconds on one verified project MCP server. The `YouTube link field` node is focused and actionable; the decorative placeholder is excluded; status text remains present. See `g1-empty-semantics.json`.
- Ready product tree: no response within a strict five-second cutoff. Earlier isolated attempts reached the Hot Reload 1.2.0 120-second transport timeout. No successful Ready-tree claim is made. Controller semantics, exact-commit screenshots, interaction tests, UI-error checks, and logs cover the state. See `g1-ready-semantics.json`.

## Interaction and design checks

- Automatic URL handling: PASS through 18 focused tests; no Analyze/Paste action or Enter key is required.
- Initial/reset focus: PASS in the Empty semantic tree.
- Ready hierarchy: PASS visually in light/dark and default/minimum sizes.
- Missing preview: PASS visually at 620×350 with readable normal foreground text.
- Disabled action: PASS visually at 620×350 with an explicit reason and legible disabled button.
- Motion: the bounded 200 ms fade/rise implementation remains unchanged from the accepted Sol High revision review.
- Code health: ktlint and Detekt are part of `check`; the responsibility refactor and narrow suppressions remain green.

## Review lineage

- G1 revision review: task `01a0498c-abad-7ab3-8b77-e31a8ff7d74b`, GPT-5.6 Sol High, 35/40. Its only P2 missing-preview contrast finding was corrected.
- Code-health review: task `01a04a87-f126-7e22-b4b9-19adf8a041ba`, GPT-5.6 Sol High. Its only P2 Detekt-suppression finding was corrected.
- Empty semantics correction: `4fd87b6fa40b25981bdd0ec02a5253a48db61bc1`.

No backend, G2, G3, generated mockup, subagent, branch, or worktree work is included.
