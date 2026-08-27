# G0 — Direction

Gate: G0  
Status: AWAITING_USER  
Decision: PENDING  
OpenSpec change: `design-primary-download-window`  
Code status: NOT STARTED — G0 approval required  
Compose MCP evidence: NOT APPLICABLE AT G0  
Independent review: PENDING TOP-LEVEL GPT-5.6 SOL HIGH REVIEW

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
- Open Folder performs no OS action and visibly says folder opening is simulated in this design build.
- Download Another resets to Empty and restores focus to the YouTube-link field.

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
3. Fake Change/Open Folder actions could appear broken → destination visibly changes and Open Folder shows a deterministic acknowledgement.
4. The 620 × 350 minimum is tight → specified one compact-height metric branch plus bounded vertical overflow only when scaling requires it.

## Strongest decisions

- State transitions replace navigation, keeping source and media context stable.
- Jewel owns visual language, focus behavior, controls, and semantic colors.
- Ready shows the actual resolved best quality rather than vague “Best available.”
- The separate Design Review Controller makes every state, theme, and edge fixture deterministic without polluting product screenshots.
- Backend boundaries stay strict, preventing a design gate from becoming an architecture project.

## Known compromises and proof still required

- `DecoratedWindow` is source-backed and directly codable, but Windows drag, maximize/restore, scale, and theme parity remain runtime proof obligations for G1.
- The minimum height needs a compact metric branch and may need vertical overflow at 125–150 percent scaling.
- The design-only Open Folder acknowledgement is temporary; the later real adapter replaces it with Explorer integration.
- G0 proves direction and codability only. G1–G3 require exact-commit IntelliJ build/inspection and real Compose MCP evidence.

## Manual review

1. Confirm the single-window, single-column direction matches the product you want.
2. Confirm `YouTube link`, Paste-as-submit, and no Analyze/Enter behavior.
3. Confirm the Ready information and choices are neither missing nor excessive.
4. Confirm the plain Jewel title bar is preferable to OS-owned chrome for whole-frame light/dark parity.
5. Confirm the fake Change and Open Folder feedback is acceptable during the design-only phase.
6. Inspect `CODABILITY.md` for component feasibility and remaining runtime checks.

## Inspect these closely

- Overall density at 720 × 420 and the planned 620 × 350 fallback.
- Two-line title, long destination, and missing-thumbnail behavior.
- Primary-action hierarchy in Ready and Completed.
- Whether `YouTube link` and the empty-state hint are ordinary enough for a nontechnical user.
- Whether Jewel-decorated chrome feels like a focused utility rather than an IDE.
- Keyboard order and semantics planned for Paste, mode, quality, destination, progress, outcomes, and reset.

## Gate response

Approve exactly:

`APPROVE G0`

Request changes with:

`REVISE G0: <feedback>`

No G1 application code begins before explicit approval.

AWAITING USER: APPROVE G0 or REVISE G0: <feedback>
