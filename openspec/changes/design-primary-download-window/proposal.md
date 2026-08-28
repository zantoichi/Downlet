## Why

Downlet needs a codable, reviewable desktop direction before implementation begins. The product must make the common act of downloading a YouTube video or its audio feel direct and trustworthy on Windows without exposing command-line concepts or creating navigation around a single task.

## What Changes

- Define one compact, stable primary window for the complete download journey.
- Define Empty, Resolving, Ready, Downloading, Completed, and Error as in-place states rather than separate screens.
- Make a valid pasted or typed link start resolution automatically; no visible Paste, Analyze, or Enter action is introduced.
- Refine the coded G1 surface with one low-chroma cool tonal work plane, clearer hierarchy, and restrained state motion while preserving the compact native utility character.
- Define the Ready state around resolved media identity, Video or Audio selection, a quality label that includes the resolved quality, destination selection, and one Download action.
- Define deterministic fake transitions and a separate development-only state controller so every state can be reviewed without network or process integration.
- Define a Windows-first basic accessibility baseline for keyboard order, visible focus, semantics, status communication, contrast, scaling, long content, and missing thumbnails.
- Define normal launch theme behavior: read the current Windows preference at startup, fall back to light, and let the review harness override light/dark deterministically.
- Establish evidence requirements for IntelliJ MCP and Compose Hot Reload MCP at G1-G3.
- Add Codex Computer Use `Windows.Graphics.Capture` proof for title-bar theme and window controls because Compose MCP screenshots capture only the client area.
- Establish four mandatory human gates: G0 direction, G1 first complete pass, G2 refined pass, and G3 final acceptance.
- Stop after preparing G0 until the user explicitly says `APPROVE G0`; `REVISE G0: <feedback>` reopens direction work.
- Keep all work design-only. Real yt-dlp, subprocess, network, ffmpeg, persistence, packaging, update, telemetry, and backend integration remain out of scope.

## Capabilities

### New Capabilities

- `primary-download-flow`: The stable single-window composition and observable behavior of all six download states.
- `design-review-harness`: Deterministic fake content, state transitions, developer controls, and review evidence needed to inspect every designed condition.
- `desktop-accessibility-baseline`: Basic Windows desktop keyboard, focus, semantics, status, contrast, scaling, and resilient-content behavior.

### Modified Capabilities

None. This is a greenfield product with no existing capability specifications.

## Impact

- Adds design guidance, OpenSpec contracts, gate review artifacts, and later a minimal Kotlin/JVM Compose Desktop scaffold.
- Introduces JetBrains Jewel standalone and Compose Hot Reload only when G1 implementation is approved.
- Separates JVM bytecode target 21 from the JBR 25 development/runtime JDK required by Jewel and Hot Reload.
- Constrains implementation to a one-window, one-column desktop utility with deterministic fake state.
- Requires actual UI implementation and independent review to run in explicit top-level Codex tasks using GPT-5.6 Sol High; the root task remains responsible for planning, integration, and gate decisions. Project subagents are not used.
- Does not affect external APIs, files, services, user data, or executable integrations.
