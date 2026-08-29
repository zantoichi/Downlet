# G1 Implementation Checks

Date: 2026-08-29

## Commits

- `491de88` — pure presentation-tier and ownership policy (task 2.2)
- `205e85f` — runtime sizing, motion, review controls, and regression coverage (tasks 2.3–2.8)
- `932c2f7` — startup ownership correction after real-product clear-link reproduction
- `22aa700` — review correction for startup event ordering and platform-placement ownership

## Approved bounds and motion

- Compact launch: `720 × 168` — PASS.
- AutoManaged Ready: `720 × 420` — PASS.
- AutoManaged cleared resolved link: `720 × 420 → 720 × 168` — PASS; Empty restored and link field focused.
- UserManaged Ready then cleared link: `800 × 560 → 800 × 560` — PASS; Empty restored and link field focused without auto-shrink.
- UserManaged undersized growth near bottom/right: `x=1800, y=1041, 720 × 168 → x=1800, y=992, 720 × 400` — PASS; minimum growth and smallest vertical correction.
- Left snapped Ready then Empty: `x=-7, y=0, 1294 × 703` throughout — PASS; platform placement preserved.

- Expansion uses the approved `250 ms` decelerating tween.
- Collapse uses the approved `167 ms` accelerating tween.
- Rapid retargeting starts from the rendered height and cancels the stale target.
- Zero-duration review mode snaps to the same final bounds.

## Compose runtime

- Product and controller remained separate windows.
- Runtime window IDs for the final native-placement pass:
  - product: `365b04b0-e6c0-4f6c-8933-4117683aba86`
  - controller: `74bc7c5e-6056-4153-a101-294eb58dfce6`
- Empty, invalid Empty, Resolving, and Ready were checked in light and dark themes.
- Ready, Downloading, Completed, and Error remained Expanded.
- Reset and Download Another returned to Compact Empty.
- Normal motion, zero duration, AutoManaged reset, manual resize, resize during animation, and rapid Ready→Empty retargeting reached stable final bounds.
- Final Compose UI-error check: `hasError=false`.
- Runtime logs contained inspector actions only; no product exception was reported.

## Native Windows checks

- Native product window ID: `37488992`.
- Compact full-window capture included the Jewel title bar and minimize, maximize, and close controls.
- Maximize changed the native capture from `706 × 161` client pixels to the `2560 × 1392` monitor work area.
- Restore returned to the original `706 × 161` client capture and `720 × 168` outer bounds.
- Dragging to the left edge produced a `1280 × 696` native half-screen capture; Ready and Empty did not replace that placement.
- Dragging away from the edge restored the floating `720 × 168` bounds.
- Near-bottom/right Ready growth produced a `706 × 393` native client capture at origin `1807,992`, matching the `720 × 400` outer minimum and work-area correction.

### Clear-link correction

- The first real-product pass after integration reproduced the user report: clearing the resolved URL restored Empty content but preserved the expanded height.
- Root cause: startup animation copied the transient native `136 × 39` placeholder width; Windows clamped it to the `620` minimum and that app-caused resize was classified as user ownership.
- The correction preserves the intended `720` startup width and accepts the first native startup bounds before classifying later unmatched bounds as user-driven.
- Computer Use repeated the real `Downlet` flow twice on native window `6817238`: `706 × 161` client → `706 × 413` → `706 × 161`.
- Focus remained in the URL field after deletion. No temporary diagnostic logging remains.
- Independent review found the first correction remained timing-sensitive and that maximize/snap changed ownership permanently; `22aa700` supersedes `932c2f7` as the accepted code candidate.
- Post-review Computer Use repeated floating collapse four times at `706 × 161 → 706 × 413 → 706 × 161` and verified `Ready → maximize → clear → restore` at `706 × 161`.

Native Computer Use screenshots were point-in-time tool observations and were not written into the repository. Task 2.11 should recapture canonical full-window evidence after review.

## Saved client-area captures

- `captures/empty-light-compose.png`
- `captures/invalid-empty-light-compose.png`
- `captures/resolving-light-compose.png`
- `captures/ready-light-compose.png`
- `captures/empty-dark-compose.png`
- `captures/invalid-empty-dark-compose.png`
- `captures/resolving-dark-compose.png`
- `captures/ready-dark-compose.png`

## Verification

- IntelliJ inspections: no Kotlin errors or warnings in production files; only two existing test-only debugger suggestions for timing `println` calls.
- IntelliJ full rebuild: PASS, no problems.
- `gradlew.bat check`: PASS.
- `gradlew.bat smokeTest`: PASS.
- Focused clear-link smoke regression: PASS.
- `git diff --check`: PASS.
- IntelliJ `Downlet` and `Design Review` run configurations: launched successfully.

## Known limitations

- The G1 native pass verified the current Windows scale and active monitor only. The complete `100%`/`125%`/`150%`, multi-edge, and multi-monitor matrix remains assigned to G2 task 3.3.
- Snap classification is intentionally a small tolerance-based heuristic for standard full, half, and third work-area regions; the native pass verified left-half snap behavior.
- Headless smoke output includes Jewel's known missing `expui/general/chevronDown.svg` resource log. Tests pass and runtime UI-error checks remain clean.
