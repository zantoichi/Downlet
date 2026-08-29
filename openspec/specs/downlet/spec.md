# Downlet Specification

## Purpose

Defines the durable user-visible behavior of Downlet, a small Windows desktop utility for downloading YouTube audio or video without exposing command-line complexity.

## Requirements

### Requirement: Single-window download flow

Downlet SHALL present one primary Windows desktop window and keep the user's URL context in place while moving through Empty, Resolving, Ready, Downloading, Completed, and Error states. It SHALL NOT require navigation, tabs, a sidebar, or a settings surface for the primary task.

#### Scenario: Application starts

- **WHEN** Downlet opens
- **THEN** the primary window shows the Empty state with the YouTube-link field ready for input

#### Scenario: Product state changes

- **WHEN** the flow advances to another state
- **THEN** the same primary window updates in place without opening another product screen

### Requirement: URL input starts resolution directly

Downlet SHALL keep a visibly labelled YouTube-link field throughout the flow. A valid pasted link SHALL begin resolution immediately, a valid manually typed link SHALL begin resolution after a short idle delay, and invalid text SHALL remain editable with concise inline validation. No separate Paste or Analyze action SHALL be required.

#### Scenario: User enters a valid link

- **WHEN** the user pastes a valid YouTube URL or pauses after typing one
- **THEN** Downlet enters Resolving without requiring Enter or another command

#### Scenario: User enters an invalid link

- **WHEN** the field does not contain a valid YouTube URL
- **THEN** Downlet remains editable in Empty and explains the validation problem without starting resolution

### Requirement: Ready exposes only useful choices

Ready SHALL identify the resolved media with a thumbnail or stable missing-preview fallback, title, channel, duration, and provider. It SHALL expose Video or Audio, an understandable quality choice, the current destination with a Change action, and one Download action. It SHALL NOT expose format IDs, codecs, extractor details, raw logs, or advanced command-line options.

#### Scenario: Media resolves

- **WHEN** resolution succeeds
- **THEN** Ready shows the media identity and the choices required to start a download

#### Scenario: Content is long or incomplete

- **WHEN** the title or destination is long or the thumbnail is unavailable
- **THEN** essential controls remain reachable and the missing or truncated content remains understandable

### Requirement: Download outcomes remain actionable

Downloading SHALL preserve media context, lock choices that must not change, show determinate progress, and expose Cancel as a secondary action. Completed SHALL show the destination with Open Folder and Download Another. Error SHALL explain the failure without backend jargon and expose Retry.

#### Scenario: User cancels a download

- **WHEN** the user activates Cancel
- **THEN** Downlet returns to Ready with the previous choices and destination preserved

#### Scenario: Download completes

- **WHEN** progress completes successfully
- **THEN** Downlet enters Completed and offers Open Folder and Download Another

#### Scenario: Download fails

- **WHEN** downloading fails
- **THEN** Downlet enters Error and Retry restarts the download with the previous choices

### Requirement: Window size follows task stage

The primary window SHALL use a fixed width and two automatic height tiers: Compact for Empty and Resolving, and Expanded for Ready, Downloading, Completed, and Error. Manual resize and maximize SHALL be unavailable while ordinary minimize and close remain available. Height changes SHALL keep the URL anchor stable, remain within the active work area, and use brief interruptible motion with an equivalent instant result when motion duration is disabled.

#### Scenario: Resolution reveals useful content

- **WHEN** Resolving becomes Ready
- **THEN** the window expands once without changing width or losing the URL context

#### Scenario: User returns to link entry

- **WHEN** reset, Download Another, or a new link edit returns the flow to Empty
- **THEN** the window returns to Compact and focuses the YouTube-link field

#### Scenario: Preferred bounds do not fit

- **WHEN** the preferred tier would exceed the active work area
- **THEN** Downlet applies the smallest adjustment needed to keep essential content reachable

### Requirement: Interaction remains accessible and resilient

Downlet SHALL provide logical keyboard order, visible focus, meaningful control and status semantics, understandable progress, and status communication that does not rely on color or motion alone. Essential actions SHALL remain visible in light and dark themes, with long content, missing previews, and common Windows scaling through 150 percent.

#### Scenario: User operates Downlet by keyboard

- **WHEN** the user navigates and activates controls without a pointer
- **THEN** focus follows reading order, native activation works, and reset returns focus to the YouTube-link field

#### Scenario: Theme or scale changes

- **WHEN** Downlet is shown in light or dark theme at common Windows scaling
- **THEN** text, focus, status, and essential actions remain legible and reachable

#### Scenario: Motion is disabled

- **WHEN** the effective motion duration is zero
- **THEN** every state reaches the same visible and semantic result immediately
