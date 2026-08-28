# G1 — Core Surface

Gate: G1  
Status: REVISION_IN_PROGRESS
Commit: `4444ee7949e29b8c8432f3274e186addb9267164`  
Evidence source commit: `c80f48c03b64db26221d5a9c18a3f026a65b30a7`  
OpenSpec change: `design-primary-download-window`  
OpenSpec tasks: historical `2.1–2.9`, `3.1–3.18`; revision `3.19–3.23`
Run configuration: `Design Review`  
IntelliJ build: PASS  
Tests: PASS — 17/17  
Compose MCP: CONNECTED  
UI errors: NONE  
Date: 2026-08-28

The evidence source differs from the reviewed product commit only through committed orchestration and evidence documents. Application source, resources, build configuration, and run configurations are identical. This package is retained as historical evidence and is superseded by the approved G1 revision now in progress.

## Approved G1 revision

User feedback, recorded verbatim:

> Design is solid.
>
> 1. remove the "Paste" button ideally since this should be automatic.
> 2. I would like the design to be much more fancy and polished, using subtle cool animations, and design elements. Mimicking extremely modern similar designs. And to be minimal at the same time.

User decision, recorded verbatim:

`APPROVE G1 REVISION`

The revision removes the visible Paste action while preserving native `Ctrl+V` immediate resolution and typed-link debounce. It adds one low-chroma tonal work plane, stronger hierarchy, a refined preview/fallback, concise disabled-action explanation, initial/reset focus, and one short fade/rise state transition. It does not add cards, a visual framework, a dependency, backend behavior, G2, or G3 scope.

## What changed

- Added the standalone Windows-first Jewel `DecoratedWindow` shell, matching light/dark title-bar treatment, and fixed default/minimum window behavior.
- Added a developer-only `Design Review` controller for deterministic state, theme, and edge-fixture inspection.
- Added the persistent YouTube-link row, Paste action, immediate Ctrl+V handling, 350 ms typing debounce, validation, Empty state, and 550 ms fake Resolving transition.
- Added the Ready surface: bundled thumbnail, media identity, Video/Audio choice, quality selector, deterministic destination Change action, and one Download primary action.
- Added compact-height behavior for 720×420 and 620×350, deterministic fixtures, generated Compose resources, and 17 focused state/timing tests.
- Completed separate Sol High integrated verification, exact-app evidence capture, native Windows frame proof, and a read-only Impeccable critique.

## Intentionally not implemented

- No real YouTube lookup, yt-dlp, network, subprocess, ffmpeg, file transfer, destination picker, folder opening, persistence, telemetry, updates, packaging, or installer work.
- No G2 fake download timeline, progress UI, deterministic failure branch, Completed flow, or final Error recovery flow.
- The G1 Download action remains in Ready and shows `Design preview: Download action received.`; G2 replaces it with the deterministic Downloading transition.
- No settings, history, library, onboarding, advanced format table, codec picker, terminal, or provider-general architecture.

## Superseded manual review

1. Open `C:\Users\SVall\IdeaProjects\Downlet` in IntelliJ IDEA. Current committed `main` contains the same application tree as reviewed product commit `4444ee7949e29b8c8432f3274e186addb9267164`.
2. Run the shared `Design Review` configuration. Confirm one `Downlet` product window and one `Design Review Controller` window open.
3. In the controller, inspect Empty in Light and Dark. Confirm one persistent `YouTube link` row, the Paste action, restrained empty copy, and matching client/title-bar theme.
4. Paste a valid URL such as `https://youtu.be/g1-review-paste`. Confirm Resolving begins immediately and reaches Ready without Enter. Reset, type a valid URL, pause, and confirm the 350 ms debounce plus 550 ms fake resolution path.
5. In Ready, switch Video/Audio, inspect quality choices, activate Change, and activate Download. Confirm selection state, destination acknowledgement, and `Design preview: Download action received.`
6. Resize the product window to exactly 620×350. Inspect normal Ready, long title, long destination, missing preview, and disabled Download fixtures. Confirm every essential action remains reachable.
7. Inspect the native frame: drag the window, maximize, restore, and confirm minimize/maximize/close controls remain normal. Run the shared `Downlet` configuration separately and confirm it opens only one product window.

## Strongest decisions

- Progressive disclosure keeps Empty focused and reveals only the media/options needed in Ready.
- Jewel owns the control vocabulary, focus visuals, colors, typography, and decorated frame instead of custom imitation controls.
- The composition remains stable across light/dark, default/minimum sizes, long title/path, and missing-preview fixtures.
- Paste and typing require no Analyze action or Enter key.
- The developer-only controller makes the design reproducible without leaking review controls into normal launch.

## Historical compromises being resolved

- Product semantic-tree serialization timed out once in Empty and once in Ready because of the known Jewel text-editor tooling stall. The committed JSON records the exact timeout plus controller semantics, native frame accessibility, and visible product labels/actions from the same observed states. No successful product semantic-tree claim is made.
- Initial focus, disabled-action explanation, and missing-preview emphasis are now included in revision task `3.20` instead of being deferred.
- The complete peak-end experience cannot be judged until G2 adds Downloading and Completed.

## Inspect these closely

- Overall density and hierarchy at 720×420 and 620×350.
- Light/dark parity across the Jewel client area and decorated Windows frame.
- Paste-as-submit, typing debounce, and lack of an unnecessary Analyze/Enter step.
- Ready hierarchy: media identity, Video/Audio, quality, destination, and one primary action.
- Long title, long destination, missing-preview, and disabled-action behavior at minimum size.
- Native keyboard order and whether the documented initial-focus follow-up should block a later gate.

## Independent review

- Integrated review: READY at `4444ee7949e29b8c8432f3274e186addb9267164` after two narrow correction passes.
- Impeccable review task: `01a048c8-db5f-72c1-b399-008945854580`, GPT-5.6 Sol High, read-only.
- Impeccable score: 32/40 — Good.
- Final verdict: READY WITH DOCUMENTED P2/P3.
- No P0/P1 finding and no G1 correction re-dispatch.

## Gate response

Approve exactly:

`APPROVE G1`

Request changes with:

`REVISE G1: <feedback>`

G1 revision in progress. A new code-backed package will replace this one before the gate asks for approval again.
