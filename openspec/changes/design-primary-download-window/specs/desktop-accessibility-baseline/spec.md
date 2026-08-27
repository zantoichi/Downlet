## Purpose

Defines the basic keyboard, focus, semantics, contrast, scaling, and resilient-content behavior expected from a first-class Windows desktop utility.

## ADDED Requirements

### Requirement: Keyboard focus follows the task

The product SHALL provide a logical focus order that follows visual reading order, skip absent or disabled controls, and place initial focus in the YouTube-link field when Empty is shown.

#### Scenario: Keyboard-only user starts the app

- **WHEN** the product opens in Empty and the user navigates with Tab and Shift+Tab
- **THEN** focus begins in the YouTube-link field and moves through only the currently visible actionable controls in visual order

#### Scenario: Flow resets

- **WHEN** Download Another returns the product to Empty
- **THEN** keyboard focus returns to the YouTube-link field

### Requirement: Focus and interaction states are visible

Every interactive control SHALL retain a visible Jewel focus treatment and distinguish hover, pressed, selected, and disabled states without using color as the only signal.

#### Scenario: User traverses controls by keyboard

- **WHEN** focus moves across the YouTube-link field, buttons, radio choices, and quality selector
- **THEN** the focused control is visually identifiable in both light and dark themes

### Requirement: Controls and status expose meaningful semantics

Interactive controls SHALL expose meaningful names, roles, values, and enabled or selected state. Resolving, progress, completion, validation, and error changes SHALL expose status semantics suitable for assistive technology.

#### Scenario: Ready semantics are inspected

- **WHEN** the semantic tree is captured in Ready
- **THEN** YouTube link, Paste, Video, Audio, quality, destination change, and Download have unambiguous accessible names and state

#### Scenario: Downloading semantics are inspected

- **WHEN** fake progress changes
- **THEN** the semantic tree exposes a progress value and a concise downloading status rather than only visual motion

#### Scenario: Outcome semantics are inspected

- **WHEN** Completed or Error is shown
- **THEN** the outcome message is represented as meaningful status text and is not communicated by color or icon alone

### Requirement: Keyboard activation uses desktop conventions

Buttons and choices SHALL support their native Enter or Space activation behavior. The primary window SHALL NOT add a global Escape shortcut that unexpectedly exits or discards work; native popups SHALL retain their standard Escape behavior.

#### Scenario: User activates a focused action

- **WHEN** a keyboard user presses the native activation key on Paste, Download, Cancel, Retry, Open Folder, or Download Another
- **THEN** the same action occurs as with pointer activation

#### Scenario: User presses Escape in the primary surface

- **WHEN** no popup is open and the user presses Escape
- **THEN** the window remains open and no download or selection is silently discarded

### Requirement: Long and missing content remains usable

Long titles, long destination paths, and missing thumbnails SHALL preserve the composition and essential actions at default and minimum window sizes.

#### Scenario: Title is very long

- **WHEN** the extremely long title fixture is active
- **THEN** the title uses no more than two lines and truncates without overlapping metadata or controls

#### Scenario: Destination path is very long

- **WHEN** the long path fixture is active
- **THEN** the visible path truncates safely, its full value remains available to semantics, and Change remains reachable

#### Scenario: Thumbnail is missing

- **WHEN** no thumbnail is available
- **THEN** a non-decorative fallback communicates unavailable preview content without shifting the media layout

### Requirement: Light, dark, contrast, and scaling remain credible

The product SHALL use Jewel theme values for control states and text contrast, SHALL support light and dark themes, and SHALL keep essential actions reachable under common Windows scaling up to 150 percent, allowing vertical overflow handling only when needed.

#### Scenario: Theme changes

- **WHEN** the reviewer switches between light and dark
- **THEN** text, controls, status treatments, and focus remain legible and no hard-coded light-only or dark-only color appears

#### Scenario: Windows scaling increases

- **WHEN** the interface is reviewed at 125 or 150 percent scaling
- **THEN** text is not clipped and essential actions remain reachable even if overflow handling becomes necessary

### Requirement: Essential actions are not hover-only

All actions required to complete, cancel, retry, reset, or locate a fake download SHALL remain visibly discoverable without hover.

#### Scenario: Pointer has not hovered the surface

- **WHEN** Ready, Downloading, Completed, or Error first appears
- **THEN** every essential action for that state is visible with a text label
