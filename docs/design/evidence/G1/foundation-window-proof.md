# G1 Foundation Window Proof

Status: PASS  
Date: 2026-08-28  
Commit: `9eced77e090f8ba713966131f367e79659b7c6cb`

## Implementation

- `src/main/kotlin/downlet/Main.kt` uses Jewel `DecoratedWindow`, a plain `TitleBar`, a remembered 720×420 `WindowState`, 620×350 minimum bounds, and `isSystemInDarkTheme()` at startup.
- Review Light uses `TitleBarStyle.lightWithLightHeader()`; Dark uses `TitleBarStyle.dark()`. Product state survives review-theme overrides.
- IntelliJ inspections and build passed with no problems. Root working tree was clean before this evidence note.

## Compose proof

- Product window: `e566ecfc-e295-45a9-a8e8-25a4f5b3a182`; controller: `1f86fd99-911a-4329-a703-669294249f54`.
- Empty/Ready nodes: `30`/`34`; Light/Dark nodes: `41`/`45`.
- Captures under `build/design-review/foundation/`: `empty-light-720x420.png`, `ready-dark-720x420.png`, `ready-dark-620x350.png`, and `post-reload-empty-light-720x420.png`.
- All four captures passed visual inspection. Reload returned `success=true`, `reloaded=true`; both windows reported no UI error.

## Native Windows proof

- Top-level task: `01a047fa-0daa-7b92-aadf-9f78bd5061e1` (GPT-5.6 Sol High).
- Codex Computer Use selected exactly one `Downlet` window and captured it with `Windows.Graphics.Capture`.
- Light: `build/design-review/foundation/native-empty-light-full-window.png`, 706×413 at origin (55,48).
- Dark: `build/design-review/foundation/native-ready-dark-full-window.png`, 706×413 at origin (255,208).
- Both frames showed matching content/title-bar themes, the Downlet title, standard minimize/maximize/close controls, and no foreign pixels.
- Drag moved the frame from (55,48) to (255,208).
- Maximize/restore changed bounds from (255,208,706×413) to (0,0,2560×1392) and back to (255,208,706×413).
- Final product state was non-maximized at Compose bounds 720×420 with no topmost state and no UI errors.

The PNGs remain transient build artifacts. Task 3.16 owns the committed final G1 evidence set.
