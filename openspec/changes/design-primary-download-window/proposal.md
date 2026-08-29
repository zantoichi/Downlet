## Why

Downlet needs a codable, reviewable desktop direction before implementation begins. The product must make the common act of downloading a YouTube video or its audio feel direct and trustworthy on Windows without exposing command-line concepts or creating navigation around a single task.

## What Changes

- Define one compact, stable primary window for the complete download journey.
- Define Empty, Resolving, Ready, Downloading, Completed, and Error as in-place states rather than separate screens.
- Make a valid pasted or typed link start resolution automatically; no visible Paste, Analyze, or Enter action is introduced.
- Progressively reveal the coded surface: Empty stays link-first, Resolving adds compact status, and Ready or later states reveal one low-chroma cool tonal work plane with restrained motion.
- Define the Ready state around resolved media identity, Video or Audio selection, a quality label that includes the resolved quality, destination selection, and one Download action.
- Define deterministic fake transitions through one KStateMachine state machine and a separate development-only state controller so every state can be reviewed without network or process integration.
- Define a Windows-first basic accessibility baseline for keyboard order, visible focus, semantics, status communication, contrast, scaling, long content, and missing thumbnails.
- Define normal launch theme behavior: read the current Windows preference at startup, fall back to light, and let the review harness override light/dark deterministically.
- Establish evidence requirements for IntelliJ MCP and Compose Hot Reload MCP at G1-G3.
- Add Codex Computer Use `Windows.Graphics.Capture` proof for title-bar theme and window controls because Compose MCP screenshots capture only the client area.
- Establish four mandatory human gates: G0 direction, G1 first complete pass, G2 refined pass, and G3 final acceptance.
- Stop after preparing G0 until the user explicitly says `APPROVE G0`; `REVISE G0: <feedback>` reopens direction work.
- Make the repository OpenSpec artifacts the sole planning source of truth, replace repeated orchestration checks with explicit verification ownership, and run one final evidence pass only after review and correction.
- Prioritize a behavior-preserving code-health tranche before G1 final review: split mixed-responsibility Kotlin files and add automated formatting and static analysis through the Gradle `check` lifecycle.
- Keep the build on the latest stable dependency set that is mutually supported by Kotlin, Gradle, Compose, Jewel, and Hot Reload; resolve actionable JDK warnings and record upstream-only warnings rather than hiding them.
- Add one fast local Compose smoke command that exercises the real product composition and deterministic state flow while reporting a basic elapsed-time metric.
- Use generated visual concepts only as source material for a small app icon, deterministic thumbnail fixture, and themeable missing-preview mark; do not ship raw generated screens or AI-rendered text.
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
- Requires UI implementation and independent review to run in explicit top-level Codex tasks using GPT-5.6 Sol High. Non-visual Kotlin/build/test tranches use GPT-5.6 Sol Medium. The root task remains responsible for planning, integration, and gate decisions. Project subagents are not used.
- Adds project-local `.editorconfig`, ktlint, and Detekt checks with pinned compatible versions; a Detekt prerelease is allowed only when the current Kotlin/Gradle/JDK stack has no supported stable release and the task records that rationale. No CI provider, Git-hook framework, quality baseline, or architecture framework is introduced.
- Adds KStateMachine coroutines `0.38.1` as the single state-machine dependency and Compose's desktop UI-test API for the focused smoke path; no serialization, hierarchical-state, architecture-test, or modulith framework is introduced.
- Reorganizes existing Kotlin code by stable responsibility without changing product behavior, visuals, accessibility, or public integrations.
- Does not affect external APIs, files, services, user data, or executable integrations.
