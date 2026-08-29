# G1 — Final Evidence

Date: 2026-08-29
Reviewed product commit: `4fd87b6fa40b25981bdd0ec02a5253a48db61bc1`
Status: PASS WITH DOCUMENTED READY-SEMANTICS TOOLING LIMITATION

## Build and code health

- `gradlew.bat check`: PASS.
- Ktlint: PASS.
- Detekt: PASS with narrow Compose exceptions only.
- `DownloadStateTest`: 18 passed, 0 failed, 0 skipped.
- IntelliJ `ProductSurface.kt` inspection: zero problems.
- IntelliJ focused build: PASS, zero problems.
- Strict OpenSpec validation: PASS.

Current production sources:

- `src/main/kotlin/downlet/Main.kt`
- `src/main/kotlin/downlet/ProductSurface.kt`
- `src/main/kotlin/downlet/ReadyContent.kt`
- `src/main/kotlin/downlet/DownloadModels.kt`
- `src/main/kotlin/downlet/DownloadStateHolder.kt`
- `src/main/kotlin/downlet/DesignReviewApp.kt`
- `src/main/composeResources/drawable/thumbnail_normal.svg`
- `src/test/kotlin/downlet/DownloadStateTest.kt`

## Runtime

- Run configuration: `Design Review`.
- Product window: Downlet, 720×420 default and 620×350 minimum evidence sizes.
- Controller window: Design Review Controller.
- Compose status: connected, reload state `ok`, no failed reload.
- Product/controller UI errors: none.
- Logs: expected reload, state/theme, resize, screenshot, and semantic actions only.

Before final capture, four leaked completed-task `hotMcpServer` process trees were stopped. One verified project server remained. Each screen-backed Compose capture brought the exact Downlet HWND forward before saving; all accepted images were opened and visually checked.

## Canonical screenshots

Client/window-content evidence:

- `docs/design/evidence/G1/g1-empty-light-720x420.png`
- `docs/design/evidence/G1/g1-empty-dark-720x420.png`
- `docs/design/evidence/G1/g1-ready-light-720x420.png`
- `docs/design/evidence/G1/g1-ready-dark-720x420.png`
- `docs/design/evidence/G1/g1-ready-light-620x350.png`
- `docs/design/evidence/G1/g1-missing-preview-light-620x350.png`
- `docs/design/evidence/G1/g1-disabled-light-620x350.png`

Exact-current native full-window evidence:

- `docs/design/evidence/G1/g1-native-empty-light-full-window.png`
- `docs/design/evidence/G1/g1-native-ready-dark-full-window.png`

No generated mockup or controller screenshot is used as product evidence.

## Semantics

- `docs/design/evidence/G1/g1-empty-semantics.json`: PASS. The Empty product tree returned in under two seconds. The link field is focused and named; its decorative placeholder is pruned; status text remains available.
- `docs/design/evidence/G1/g1-ready-semantics.json`: DOCUMENTED TOOLING LIMITATION. Ready did not return within the strict five-second final cutoff on one verified server. Earlier isolated calls reached Hot Reload's 120-second timeout. Controller semantics and same-commit visual/interaction evidence cover Ready.

## Interaction matrix

- No visible Paste action: source and screenshots PASS.
- Paste starts resolution immediately: focused tests PASS.
- Typing starts resolution after 350 ms: focused tests PASS.
- Automatic fake resolution completes after 550 ms: focused tests PASS.
- Superseded/cancelled work does not complete stale state: focused tests PASS.
- Initial/reset focus: Empty semantic tree PASS.
- Video/Audio and matching quality models: focused state tests and Ready visuals PASS.
- Missing preview: minimum-size screenshot PASS.
- Disabled Download: minimum-size screenshot and explicit reason PASS.
- Light/dark parity: default-size screenshots PASS.
- Native title bar and controls: full-window screenshots PASS.

## Evidence index

- `docs/design/evidence/G1/product-evidence.md`
- `docs/design/evidence/G1/g1-revision-review.md`
- `docs/design/evidence/G1/g1-code-health-review.md`
- `docs/design/evidence/G1/g1-empty-semantics.json`
- `docs/design/evidence/G1/g1-ready-semantics.json`

AWAITING USER: APPROVE G1 or REVISE G1: <feedback>
