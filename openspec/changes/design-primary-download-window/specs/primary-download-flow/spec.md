## Purpose

Defines the complete visible journey for downloading YouTube audio or video through one compact, stable Windows desktop window.

## ADDED Requirements

### Requirement: Stable primary window

The product SHALL present one primary Jewel `DecoratedWindow` with one content column. Its plain title bar SHALL contain only the Downlet title and standard window controls. Its initial size SHALL be approximately 720 by 420 logical pixels, its minimum usable size SHALL be approximately 620 by 350 logical pixels, and changing product state SHALL NOT open another product screen or change the window geometry automatically.

#### Scenario: Application starts

- **WHEN** the product launches normally
- **THEN** one primary window opens near 720 by 420 logical pixels with the Empty state visible

#### Scenario: Window reaches minimum size

- **WHEN** the user resizes the primary window to approximately 620 by 350 logical pixels
- **THEN** all essential controls remain reachable and long content truncates or wraps without expanding the window

#### Scenario: Product state changes

- **WHEN** the flow moves among Empty, Resolving, Ready, Downloading, Completed, and Error
- **THEN** the same primary window and URL context remain in place without navigation, tabs, a sidebar, or a settings surface

#### Scenario: Normal application chooses its startup theme

- **WHEN** Downlet starts normally and the Windows theme preference is available
- **THEN** the Jewel content and title bar use that light or dark preference together without adding a project stripe, menu, toolbar, breadcrumbs, or IDE actions

#### Scenario: Windows theme preference is unavailable

- **WHEN** Downlet starts normally and Compose cannot determine the Windows theme preference
- **THEN** the product uses light theme

#### Scenario: Windows theme changes while Downlet remains open

- **WHEN** the Windows theme preference changes during this design phase
- **THEN** live switching is not required and the next normal launch reads the new preference

#### Scenario: Design review forces a theme

- **WHEN** the design-review harness selects Light or Dark
- **THEN** that explicit choice overrides the normal startup preference for the product window

### Requirement: URL input submits without an extra command

The product SHALL keep a field visibly labeled `YouTube link` throughout the flow and SHALL NOT show a separate Paste or Analyze action. A valid URL pasted through the operating-system text-field shortcut SHALL begin fake resolution immediately, while a manually typed valid URL SHALL begin fake resolution after a short idle debounce without requiring Enter. The product SHALL NOT proactively read or monitor clipboard contents.

#### Scenario: Operating-system paste shortcut receives a valid URL

- **WHEN** the user pastes a parseable YouTube URL into the field with the Windows paste shortcut
- **THEN** the product enters Resolving as soon as the pasted edit is applied

#### Scenario: User types a valid URL

- **WHEN** the user manually types a parseable YouTube URL and pauses for the defined idle debounce
- **THEN** the product enters Resolving without requiring Enter

#### Scenario: Input is invalid

- **WHEN** pasted or typed text is not a parseable YouTube URL
- **THEN** the text remains editable, `Enter a valid YouTube link.` appears as restrained inline validation, and no fake resolution starts

### Requirement: Empty state remains quiet

The Empty state SHALL show only the persistent YouTube-link row and the sentence `Paste or type a YouTube link. Downlet checks it automatically.` It SHALL NOT show media, format, quality, destination, progress, or download controls before they are useful.

#### Scenario: No URL has been submitted

- **WHEN** the product is in Empty
- **THEN** focus and visual emphasis are placed on the YouTube-link task, the fixed explanatory sentence is visible, and no illustration, cards, advanced options, or inactive downstream controls appear

### Requirement: Resolving preserves context

The Resolving state SHALL preserve the YouTube-link row, show `Checking this YouTube link…` with a restrained activity treatment, and avoid disruptive reflow.

#### Scenario: Valid URL starts resolving

- **WHEN** fake resolution begins
- **THEN** the submitted URL remains visible and an indeterminate progress treatment plus `Checking this YouTube link…` replaces the quiet body region

### Requirement: State changes use restrained motion

The state body SHALL change in place with a short `180–220 ms` fade and at most `6` logical pixels of vertical rise. Motion SHALL use standard Compose duration scaling, SHALL NOT block interaction, and SHALL NOT bounce, loop decoratively, or carry status meaning by itself.

#### Scenario: Product state changes with normal animation scale

- **WHEN** Empty, Resolving, or Ready replaces the prior state body
- **THEN** one restrained fade-and-rise transition clarifies the change without moving the persistent URL field or window geometry

#### Scenario: System animation scale is zero

- **WHEN** Compose reports zero duration scale
- **THEN** the state body reaches the same final layout immediately without losing any status text or meaning

### Requirement: Ready confirms media identity

The Ready state SHALL identify the resolved item with a 16:9 thumbnail or missing-thumbnail fallback, title, channel, duration, and provider inside one subtly bounded, low-chroma tonal work plane. The thumbnail SHALL use a modest rounded clip and thin theme-aware boundary. The title SHALL support two lines before truncating, and low-value extractor or encoding metadata SHALL remain absent.

#### Scenario: Typical media resolves

- **WHEN** fake resolution succeeds with the normal fixture
- **THEN** the Ready state shows thumbnail, title, channel, duration, and YouTube provider context in one compact media region

#### Scenario: Thumbnail is unavailable

- **WHEN** fake resolution uses the missing-thumbnail fixture
- **THEN** the media region keeps the same geometry and shows a clearly distinct tonal fallback with `Preview unavailable` rather than a broken or empty image

### Requirement: Ready exposes only useful choices

The Ready state SHALL expose mutually exclusive Video and Audio choices, one quality selector, one destination row, and one Download primary action. No codec, format ID, extractor, account, history, or advanced-option control SHALL appear.

#### Scenario: Ready opens in video mode

- **WHEN** the normal Ready fixture first appears
- **THEN** Video is selected and the quality control displays `Best available — 2160p` without requiring the user to open it

#### Scenario: User selects audio

- **WHEN** the user selects Audio
- **THEN** the quality control switches to concise source-derived audio choices and its selected label names the resolved best quality

#### Scenario: User inspects destination

- **WHEN** Ready is visible
- **THEN** the current destination begins as Downloads, long paths truncate safely, and a secondary Change action is available

#### Scenario: User changes the destination in the design-only build

- **WHEN** the user activates Change
- **THEN** the destination cycles to the next deterministic fixture and `Save location changed to {destination}.` appears without opening a native picker

#### Scenario: User starts the download

- **WHEN** the user activates Download in Ready
- **THEN** the product enters fake Downloading using the visible mode, quality, and destination selections

#### Scenario: Download is unavailable

- **WHEN** the deterministic disabled-action fixture is active
- **THEN** Download uses its native disabled treatment and the existing feedback area shows one concise visible reason that does not depend on color alone

### Requirement: Downloading communicates progress calmly

The Downloading state SHALL preserve the URL and media identity, lock choices that must not change, show determinate progress with a percentage and concise speed/time text, and expose Cancel only as a secondary action.

#### Scenario: Fake download is active

- **WHEN** the fake download advances
- **THEN** the progress bar, percentage, speed, and remaining-time text update deterministically without adding a telemetry dashboard

#### Scenario: User cancels

- **WHEN** the user activates Cancel
- **THEN** fake progress stops and the product returns to Ready with the prior selections and destination preserved

### Requirement: Completed keeps the outcome in place

The Completed state SHALL replace the progress/status region in place, show `Saved to {destination}`, present Open Folder as the primary action, and present Download Another as a secondary reset action.

#### Scenario: Fake download completes

- **WHEN** fake progress reaches completion
- **THEN** the product shows `Saved to {destination}` with Open Folder and Download Another and no celebratory page or animation

#### Scenario: Open Folder is activated in the design-only build

- **WHEN** the user activates Open Folder
- **THEN** no operating-system folder action occurs, the Completed context remains in place, and `Folder opening is unavailable in this design preview.` appears

#### Scenario: User downloads another item

- **WHEN** the user activates Download Another
- **THEN** the flow resets to Empty, clears resolved media state, and returns focus to the URL field

### Requirement: Recoverable failure is actionable

The Error state SHALL preserve useful source and media context, show `Couldn't download this media.` and `Check that the YouTube link is available and try again.`, and expose Retry without stack traces, process output, yt-dlp terminology, or backend details.

#### Scenario: Fake download fails

- **WHEN** the deterministic failure fixture is triggered
- **THEN** an inline error treatment shows the fixed error title and body and offers Retry

#### Scenario: User retries

- **WHEN** the user activates Retry
- **THEN** the product restarts fake Downloading with the previous choices and destination

### Requirement: Design phase has no backend side effects

All resolution, download, cancel, retry, open-folder, and destination-change outcomes in this change SHALL be simulated locally and deterministically.

#### Scenario: Any designed action is exercised

- **WHEN** a reviewer traverses the product flow
- **THEN** no network request, subprocess, yt-dlp call, ffmpeg call, file write, persistence, telemetry, update, or packaging behavior occurs
