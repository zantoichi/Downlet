# G1 — Core Surface

Gate: G1
Status: APPROVED
Date: 2026-08-29
Reviewed product commit: `4fd87b6fa40b25981bdd0ec02a5253a48db61bc1`
OpenSpec change: `design-primary-download-window`

## Outcome

The revised G1 desktop surface is ready for review. It is Windows-first, minimal, visually polished, and focused on the primary user: someone who wants to download audio or video from a YouTube link locally.

The approved revision is present:

- The visible Paste button and AWT clipboard path are gone.
- Paste resolves immediately; typing resolves after the 350 ms idle debounce; neither requires Enter.
- Empty focuses the YouTube-link field automatically.
- One restrained tonal work plane, rounded preview treatment, and 200 ms fade/rise transition add polish without adding decorative systems.
- Ready keeps one clear hierarchy: media, Video/Audio, quality, destination, and Download.
- Missing-preview and disabled-action states explain themselves at 620×350.
- Ktlint, Detekt, responsibility-focused source files, and 18 focused tests now enforce code health.

No backend, yt-dlp, ffmpeg, network, filesystem transfer, packaging, G2, or G3 behavior is included.

## Decision

User decision recorded on 2026-08-29: `APPROVE G1`

## Verification

- `gradlew.bat check`: PASS.
- Tests: 18 passed, 0 failed, 0 skipped.
- IntelliJ inspections/build: PASS, zero problems.
- `Design Review`: one product window plus one controller window.
- Compose reload state: `ok`; zero UI errors; clean logs.
- Seven exact-commit client views and two exact-current native full-window views were captured and visually inspected.
- Empty product semantics now passes in under two seconds and reports the focused `YouTube link field`.

Ready product-tree serialization remains a Compose Hot Reload 1.2.0 tooling limitation: it did not respond within the bounded five-second final check. No successful Ready-tree claim is made. Ready is covered by controller semantics, exact-commit visuals, 18 interaction/timing tests, UI-error checks, and logs. This does not block the product UI gate.

## Review lineage

- G1 revision review: `01a0498c-abad-7ab3-8b77-e31a8ff7d74b`, GPT-5.6 Sol High, 35/40. Its only P2 finding—missing-preview contrast—was corrected.
- Code-health review: `01a04a87-f126-7e22-b4b9-19adf8a041ba`, GPT-5.6 Sol High. Its only P2 finding—overbroad Detekt exclusions—was corrected.
- Final Empty-semantics correction: `4fd87b6fa40b25981bdd0ec02a5253a48db61bc1`.

## Inspect these closely

1. Empty Light and Dark at 720×420: focus, calm hierarchy, and absence of a Paste button.
2. Ready Light and Dark at 720×420: media identity, selector hierarchy, and one primary action.
3. Ready, missing-preview, and disabled fixtures at 620×350: clipping, readability, and action reachability.
4. Native light/dark frame captures: title-bar parity and standard Windows controls.
5. The interaction contract: paste immediately; typed URL after 350 ms; automatic fake completion after 550 ms.

## Manual check

1. Open `C:\Users\SVall\IdeaProjects\Downlet` in IntelliJ IDEA.
2. Run `Design Review`.
3. Switch Empty/Ready, Light/Dark, No preview, and Disabled from the controller.
4. Reset and paste a valid YouTube URL: Resolving should start immediately and reach Ready without Enter.
5. Reset and type a valid URL: Resolving should start after the debounce and reach Ready.
6. Resize Downlet to 620×350 and confirm every essential control remains visible.

Approve exactly:

`APPROVE G1`

Request changes with:

`REVISE G1: <feedback>`

APPROVED: APPROVE G1
