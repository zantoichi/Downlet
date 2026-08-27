# G0 — Direction

Gate: G0  
Status: AWAITING_USER
Decision: PENDING  
OpenSpec change: `design-primary-download-window`  
Code status: NOT STARTED — G0 approval required  
Compose MCP evidence: NOT APPLICABLE AT G0  
Independent review: PASS — TOP-LEVEL GPT-5.6 SOL HIGH RECHECK OF `c475ecf`

## Recommended direction

**The Quiet Transfer Desk**

Downlet is one compact Windows-first utility window for one short job. A persistent `YouTube link` row anchors the flow. The body changes in place through Empty, Resolving, Ready, Downloading, Completed, and Error. Information appears only when it becomes useful.

The shell uses Jewel `DecoratedWindow` with a plain title bar containing only the Downlet title and standard window controls. It does not use a project stripe, menu, toolbar, sidebar, tabs, cards, or IDE actions. The title bar follows the forced Jewel light/dark theme, avoiding a mismatched native frame during deterministic review.

## Reference composition

```text
┌─ Downlet ────────────────────────────────────────── — □ × ┐
│                                                            │
│ YouTube link  [ https://youtube.com/watch?v=... ] [ Paste ]│
│ ────────────────────────────────────────────────────────── │
│                                                            │
│ [ 16:9 thumbnail ]  A realistic media title that may       │
│                     occupy two lines                       │
│                     Channel · 12:34 · YouTube              │
│                                                            │
│ Download as   ● Video     ○ Audio                          │
│ Quality       [ Best available — 2160p                 ▾ ] │
│ Save to       Downloads                           Change…   │
│                                                            │
│                                             [ Download ]    │
└────────────────────────────────────────────────────────────┘
```

This is hierarchy and geometry guidance, not pixel law. The target window is approximately 720 × 420 logical pixels with a usable minimum near 620 × 350.

## Interaction model

- Paste is submission. The Paste button or Windows Ctrl+V starts fake resolution immediately for a valid YouTube URL.
- Manual typing starts fake resolution after a 350 ms idle debounce. Enter and Analyze are absent.
- Invalid input stays editable and receives restrained inline validation.
- Ready exposes only media identity, Video or Audio, useful quality, destination, and one Download action.
- Fake resolution takes 550 ms.
- Fake progress uses 0, 18, 43, 68, 87, and 100 percent at 350 ms intervals.
- The failure fixture enters Error at 68 percent.
- Change cycles deterministic destination fixtures; it does not open a picker.
- G1 Download stays in Ready and visibly says `Design preview: Download action received.`; G2 replaces that with fake Downloading.
- Open Folder performs no OS action and visibly says `Folder opening is unavailable in this design preview.`
- Download Another resets to Empty and restores focus to the YouTube-link field.

## Provisional visible copy

- Empty: `Paste a YouTube link to choose video or audio.`
- Invalid link: `Enter a valid YouTube link.`
- Resolving: `Checking this YouTube link…`
- Destination change: `Save location changed to {destination}.`
- G1-only Download acknowledgement: `Design preview: Download action received.`
- Downloading label: `Downloading`
- Completed: `Saved to {destination}`
- Open Folder acknowledgement: `Folder opening is unavailable in this design preview.`
- Error: `Couldn't download this media.`
- Error guidance: `Check that the YouTube link is available and try again.`

## Why one direction

The canonical brief already fixes the macro-layout: one window, one column, stable geometry, no navigation, and six in-place states. Additional layout concepts would vary decoration more than product behavior. Impeccable shaping found no meaningful unresolved macro choice, so no generated concepts were produced.

## What changed

- Defined the Windows-first user, purpose, brand character, anti-references, and accessibility baseline in `PRODUCT.md`.
- Seeded the restrained Jewel design system in `DESIGN.md`.
- Created the OpenSpec proposal, design, three capability specs, and fine-grained implementation tasks.
- Verified current standalone Jewel, Compose, Hot Reload, and JBR assumptions against official material and extracted 0.39.1 source signatures.
- Replaced visible `URL` copy with `YouTube link` while keeping URL terminology inside technical behavior.
- Chose a plain Jewel-decorated frame so light/dark mode covers the complete window.
- Added visible deterministic feedback for fake Change and Open Folder actions.
- Separated Java 21 bytecode targeting from the JBR 25 development/runtime JDK.
- Defined normal launch theme behavior: read Windows preference at startup, fall back to light, and require restart after an OS-theme change.
- Split G1 foundation tasks into ordered top-level implementation packets and assigned every code-writing test task.
- Added Codex Computer Use `Windows.Graphics.Capture` full-window evidence because Compose Hot Reload screenshots capture only the client area.
- Added a visible temporary G1 Download acknowledgement and fixed provisional copy for all unclear states/actions.
- Recorded that project subagents are forbidden; implementation and independent review use separate top-level GPT-5.6 Sol High tasks.

## Intentionally not implemented

- No Kotlin, Gradle, Compose, or Jewel application scaffold.
- No real yt-dlp, subprocess, network, metadata, ffmpeg, filesystem, folder opening, destination picker, persistence, telemetry, update, packaging, or installer work.
- No app screenshots, semantic trees, interaction recordings, or Compose MCP evidence.
- No settings, onboarding, history, library, advanced options, terminal, format table, codec picker, or provider-general architecture.

## Impeccable critique

Assessment type: theoretical direction and codability review; no rendered UI exists.

- AI-slop check: PASS. The direction avoids hero copy, giant cards, pills, gradients, ornamental metrics, and dashboard chrome.
- Cognitive load: LOW. One task, one persistent context row, progressive disclosure, and one primary action per state.
- Provisional Nielsen score: 32/40. Strongest areas are visibility, recognition, and minimalist design. Help/documentation is intentionally light because the workflow should explain itself.
- Automated markup/browser critique: NOT APPLICABLE. There is no HTML, frontend markup, or running UI at G0.

Critique fixes already applied:

1. `URL` was too technical as visible copy → changed to `YouTube link`.
2. Native title-bar color could diverge from a forced dark Jewel theme → changed to plain Jewel `DecoratedWindow`.
3. Fake Change/Open Folder actions could appear broken → destination visibly changes with exact feedback and Open Folder shows a deterministic acknowledgement.
4. The 620 × 350 minimum is tight → specified one compact-height metric branch plus bounded vertical overflow only when scaling requires it.

## Strongest decisions

- State transitions replace navigation, keeping source and media context stable.
- Jewel owns visual language, focus behavior, controls, and semantic colors.
- Ready shows the actual resolved best quality rather than vague “Best available.”
- The separate Design Review Controller makes every state, theme, and edge fixture deterministic without polluting product screenshots.
- Backend boundaries stay strict, preventing a design gate from becoming an architecture project.

## Known compromises and proof still required

- `DecoratedWindow` is source-backed and directly codable, but Windows drag, maximize/restore, scale, and theme parity remain runtime proof obligations for G1. Compose MCP proves the client area; a same-commit Codex Computer Use `Windows.Graphics.Capture` screenshot and manual Windows check prove title chrome.
- The minimum height needs a compact metric branch and may need vertical overflow at 125–150 percent scaling.
- The design-only Open Folder acknowledgement is temporary; the later real adapter replaces it with Explorer integration.
- Normal launch reads the Windows theme at startup only; changing Windows theme while Downlet is open requires restart in this design change.
- G0 proves direction and codability only. G1–G3 require exact-commit IntelliJ build/inspection and real Compose MCP evidence.

## Independent review record

Top-level review task: `Review Downlet G0 direction`
Model: GPT-5.6 Sol
Reasoning: High
Reviewed commit: `9f6b9b1e2d9e71d42ab8bf63f7cc91587291bbc6`
First verdict: NOT READY
Rechecked commit: `c475ecfebae95fc8be556270f482900cd9c11837`
Recheck verdict: READY

First-pass findings and resolutions:

1. Missing/misordered implementation packet ownership → split scaffold and foundation packets; assigned all implementation and test-writing tasks.
2. Compose screenshot cannot prove title bar → added Codex Computer Use `Windows.Graphics.Capture` and manual Windows interaction evidence.
3. JBR 25 and JVM target were conflated → fixed target at Java 21 and runtime at JBR 25.
4. Normal theme behavior was undefined → specified startup system-theme read, light fallback, restart behavior, and review override.
5. G1 Download could appear broken → added exact visible acknowledgement until G2 replaces it.
6. Requested copy did not exist → fixed provisional copy for Empty, validation, Resolving, destination change, G1 Download, Completed, Open Folder, and Error.

## Manual review

1. Confirm the single-window, single-column direction matches the product you want.
2. Confirm `YouTube link`, Paste-as-submit, and no Analyze/Enter behavior.
3. Confirm the Ready information and choices are neither missing nor excessive.
4. Confirm the plain Jewel title bar is preferable to OS-owned chrome for whole-frame light/dark parity and that Compose client-area plus native-window evidence is sufficient.
5. Confirm the fake Change and Open Folder feedback is acceptable during the design-only phase.
6. Inspect `CODABILITY.md` for component feasibility and remaining runtime checks.

## Inspect these closely

- Overall density at 720 × 420 and the planned 620 × 350 fallback.
- Two-line title, long destination, and missing-thumbnail behavior.
- Primary-action hierarchy in Ready and Completed.
- Whether `YouTube link` and the fixed state/action copy are ordinary enough for a nontechnical user.
- Whether Jewel-decorated chrome feels like a focused utility rather than an IDE.
- Keyboard order and semantics planned for Paste, mode, quality, destination, progress, outcomes, and reset.

## Gate response

Approve exactly:

`APPROVE G0`

Request changes with:

`REVISE G0: <feedback>`

No G1 application code begins before explicit approval.

AWAITING USER: APPROVE G0 or REVISE G0: <feedback>
