## Purpose

Defines how Downlet's fixed-width, non-resizable primary desktop window fits the current task stage while preserving accessibility and deterministic reviewability.

## ADDED Requirements

### Requirement: State-driven presentation tiers
The primary window SHALL use exactly two automatic presentation tiers. Empty and Resolving SHALL use the compact tier; Ready, Downloading, Completed, and Error SHALL use the expanded tier.

#### Scenario: Launch in compact tier
- **WHEN** Downlet opens normally
- **THEN** the primary window uses the compact tier and displays only the title bar, YouTube-link row, and current helper or validation text
- **THEN** no work plane, later-state controls, or intentionally reserved blank body area is visible

#### Scenario: Resolving remains compact
- **WHEN** a valid link enters Resolving
- **THEN** the helper is replaced by the compact resolving status without expanding the outer window

#### Scenario: Successful resolution expands once
- **WHEN** Resolving transitions to Ready
- **THEN** the primary window grows to the expanded tier and reveals the media work surface

#### Scenario: Later states keep stable outer geometry
- **WHEN** Ready transitions among Downloading, Completed, or Error
- **THEN** the outer window remains in the expanded tier and only state-specific content changes

#### Scenario: Return to link entry shrinks
- **WHEN** Reset, Download Another, or a new link edit returns an expanded window to Empty
- **THEN** the primary window shrinks to the compact tier

### Requirement: Content-fit automatic bounds
The primary window SHALL use one calibrated, non-user-resizable width and adjust only height for the target tier. Preferred bounds MUST be derived from current Jewel content, standard spacing, title-bar insets, scaling, and available work area rather than treating prior `720×420` or `620×350` dimensions as fixed product requirements.

#### Scenario: Compact content fit
- **WHEN** the compact tier reaches its final bounds at ordinary Windows scaling
- **THEN** the link row and helper, validation, or resolving status fit without clipping or a blank work-plane region

#### Scenario: Expanded content fit
- **WHEN** the expanded tier reaches its final preferred bounds
- **THEN** the visible media identity, choices, destination, status, and actions fit without unnecessary empty vertical space or scrolling under normal conditions

#### Scenario: Width remains fixed during a tier change
- **WHEN** the app grows or shrinks the window between tiers
- **THEN** the calibrated window width is unchanged and the URL field does not reflow

### Requirement: Stable anchor and on-screen placement
Automatic resizing SHALL keep the URL field and upper-left content origin at a stable screen position whenever the target bounds fit the active monitor work area. If they do not fit, the window MUST make the smallest position or size adjustment needed to remain usable and visible.

#### Scenario: Growth fits below the current position
- **WHEN** an automatically managed compact window can grow to its expanded target within the active work area
- **THEN** its top and left edges stay fixed and the lower edge grows downward

#### Scenario: Growth would cross the work area
- **WHEN** the preferred expanded bounds would extend outside the active monitor work area
- **THEN** the window shifts or caps its bounds only as much as needed to remain visible
- **THEN** every essential control remains reachable through the bounded layout or an overflow fallback

#### Scenario: Ordinary Windows scaling
- **WHEN** the app is reviewed at 100%, 125%, or 150% Windows scaling
- **THEN** compact and expanded content remain legible, unclipped, and reachable within the active monitor work area

### Requirement: Brief interruptible motion
App-driven tier changes SHALL use one brief, non-bouncy size transition coordinated with the existing state-body reveal. The transition MUST remain interactive, MUST support mid-flight retargeting from the current rendered size, and MUST finish at the target bounds without visible stepping or overshoot.

#### Scenario: Expand with motion enabled
- **WHEN** a compact automatically managed window enters Ready with motion enabled
- **THEN** the outer height and incoming work surface transition as one coherent reveal
- **THEN** the URL field remains visible and usable throughout

#### Scenario: State changes during an active resize
- **WHEN** the target tier changes before an automatic resize completes
- **THEN** motion continues from the currently rendered bounds toward the newest target without snapping through an obsolete size

#### Scenario: Reduced motion
- **WHEN** the effective motion-duration scale is zero
- **THEN** the final tier bounds and content are applied immediately without spatial animation

### Requirement: App-owned non-resizable window
The primary window SHALL remain non-resizable and app-owned. Manual resizing, maximize, snap-resize, and full-screen enlargement SHALL be unavailable so state transitions always converge to the current tier's bounds. Ordinary minimize and close behavior SHALL remain available.

#### Scenario: Manual resize is unavailable
- **WHEN** the user points at an edge or corner of the primary window
- **THEN** the window does not expose a resize affordance and its width or height cannot be dragged

#### Scenario: Maximize is unavailable
- **WHEN** the user inspects or activates the title-bar window controls
- **THEN** maximize/full-screen enlargement is disabled or absent
- **THEN** minimize and close remain available

#### Scenario: Tier sizing remains deterministic
- **WHEN** the state changes between Compact and Expanded repeatedly
- **THEN** the app reaches the calibrated fixed width and target tier height without ownership or platform-placement overrides

### Requirement: Focus, semantics, and status remain stable
Window resizing MUST NOT remove keyboard focus, reorder the logical task flow, or become the only indication of a state change. Existing visible text and semantics SHALL continue to communicate validation, resolving, progress, completion, and errors.

#### Scenario: Focus survives expansion
- **WHEN** Resolving expands to Ready
- **THEN** focus is not moved solely because the window resized
- **THEN** newly revealed controls follow the YouTube-link field in logical keyboard and accessibility traversal order

#### Scenario: Reset restores compact focus
- **WHEN** Download Another or Reset returns the app to Empty
- **THEN** the window returns to compact behavior and the YouTube-link field receives focus as already specified by the download flow

#### Scenario: Assistive technology receives state meaning
- **WHEN** any tier or state transition completes
- **THEN** persistent status text and semantics identify the state without relying on motion, size, or color alone

### Requirement: Deterministic review and evidence
The design-review harness SHALL reproduce both tiers and all transitions without network or process integration. Final evidence MUST prove client content and native outer-window behavior from the same exact reviewed commit.

#### Scenario: Forced-state tier coverage
- **WHEN** the review controller forces each of the six states
- **THEN** Empty and Resolving render compact and Ready, Downloading, Completed, and Error render expanded using deterministic fixtures

#### Scenario: Normal transition coverage
- **WHEN** the ordinary fake happy, failure, retry, cancel, edit, reset, and Download Another flows are exercised
- **THEN** tier changes match the state mapping and no stale transition changes the final bounds

#### Scenario: Evidence package
- **WHEN** a coded gate is prepared for human review
- **THEN** evidence records exact window bounds, theme, scale, motion mode, fixed-window behavior, screenshots, semantics, interactions, UI errors, logs, tests, and the reviewed commit
- **THEN** native Windows full-window captures prove title bar, outer bounds, disabled resize/maximize behavior, and available minimize/close controls that client-area captures cannot show
