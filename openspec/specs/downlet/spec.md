# Downlet Specification

## Purpose

Defines the durable user-visible behavior of Downlet, a small Windows desktop utility for downloading YouTube audio or video without exposing command-line complexity.

## Requirements

### Requirement: Single-window download flow

Downlet SHALL present one primary Windows desktop window and keep the user's URL context in place while moving through Empty, Previewing, Setup, Resolving, Ready, Downloading, Completed, and Error states. It SHALL NOT require navigation, tabs, a sidebar, or a settings surface for the primary task.

#### Scenario: Application starts

- **WHEN** Downlet opens
- **THEN** the primary window shows the Empty state with the YouTube-link field ready for input

#### Scenario: Product state changes

- **WHEN** the flow advances to another state
- **THEN** the same primary window updates in place without opening another product screen

### Requirement: URL input starts preview directly

Downlet SHALL keep a visibly labelled YouTube-link field throughout the flow. A valid pasted link SHALL begin Previewing immediately, and a valid manually typed link SHALL begin it after a short idle delay. Previewing SHALL request only lightweight YouTube identity metadata and a bounded thumbnail, SHALL download no media, and SHALL not require yt-dlp. Invalid text SHALL remain editable with concise inline validation. No separate Paste or Analyze action SHALL be required.

#### Scenario: User enters a valid link

- **WHEN** the user pastes a valid YouTube URL or pauses after typing one
- **THEN** Downlet enters Previewing without requiring Enter or another command

#### Scenario: Preview succeeds

- **WHEN** Downlet receives the media title, channel, and optional thumbnail
- **THEN** it enters Setup when required tools are missing or Resolving when they are already available

#### Scenario: Preview fails

- **WHEN** the lightweight metadata request fails or does not identify media
- **THEN** Downlet enters a recoverable resolution error without checking, downloading, or installing tools

#### Scenario: User enters an invalid link

- **WHEN** the field does not contain a valid YouTube URL
- **THEN** Downlet remains editable in Empty and explains the validation problem without starting resolution

### Requirement: Ready exposes only useful choices

Ready SHALL identify the resolved media with a thumbnail or stable missing-preview fallback, title, channel, duration, and provider. It SHALL expose Video or Audio, an understandable quality choice, the current destination with a Change action, a concise per-download authorization confirmation, a Read full terms action, and one Download action. Download SHALL remain disabled until the user selects that confirmation. It SHALL NOT expose format IDs, codecs, extractor details, raw logs, or advanced command-line options.

#### Scenario: Media resolves

- **WHEN** resolution succeeds
- **THEN** Ready shows the media identity and the choices required to start a download

#### Scenario: User authorizes one media download

- **WHEN** Ready first appears for a media item
- **THEN** Download remains disabled until the user confirms ownership or permission and accepts responsibility for following applicable law and YouTube's terms

- **WHEN** a different media item enters Ready
- **THEN** Downlet requires a fresh confirmation

#### Scenario: User reads full terms from Ready

- **WHEN** the user activates Read full terms
- **THEN** Downlet replaces the work area with detailed tool-license, media-responsibility, and liability information and provides Back to download without losing the URL or Ready choices

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

### Requirement: Downloads use yt-dlp

Normal product operation SHALL use a local `yt-dlp` process after Previewing and any required Setup to resolve authoritative YouTube metadata and download the selected Video or Audio quality into the chosen destination. It SHALL provide bundled QuickJS-NG to `yt-dlp` for YouTube JavaScript support and FFmpeg for merging and audio processing. Resolving SHALL use `--skip-download`, preserve preview identity while checking available formats, and download no media. Downlet SHALL translate process progress and failures into its existing product states, SHALL stop the active process when Cancel is activated, and SHALL keep command output and backend options out of the interface. The deterministic fake runtime MAY remain available only for tests and the Design Review app.

#### Scenario: A link resolves through yt-dlp

- **WHEN** preview succeeds and required tools are available
- **THEN** Downlet resolves its title, channel, duration, and identity through `yt-dlp` before entering Ready

#### Scenario: A real download runs

- **WHEN** the user activates Download in Ready
- **THEN** Downlet starts `yt-dlp` with the selected mode, quality, and destination and reflects reported progress until completion or failure

#### Scenario: A real download is cancelled

- **WHEN** the user activates Cancel while `yt-dlp` is running
- **THEN** Downlet terminates the process and returns to Ready with the prior choices preserved

### Requirement: Tool setup is explicit and verified

Downlet SHALL bundle a pinned QuickJS-NG Windows executable and its required notices, but SHALL NOT include yt-dlp or FFmpeg in its distribution. After Previewing succeeds, Setup SHALL appear only when yt-dlp or FFmpeg is unavailable. Tool discovery SHALL check valid explicit environment overrides, then Downlet-managed tools under local application data, then `PATH`, and SHALL NOT scan other folders or drives. Invalid overrides SHALL fall through to the next source, and FFmpeg SHALL count as available only when `ffmpeg` and `ffprobe` are regular files in the same directory. Setup SHALL keep the media preview visible, name only the missing tools, state their approximate download size and purpose, clarify that tool setup does not download the media, leave consent unselected, and download nothing until the user selects consent and activates Download and continue. Setup SHALL expose Read full terms, which replaces the work area with preview-network, tool-license, media-responsibility, and liability information and provides Back to setup without losing the URL or consent state. Downlet SHALL retrieve pinned upstream artifacts, verify their SHA-256 hashes before installation, store them under the user's local application-data directory, and invoke them as separate processes. Editing the URL SHALL remain available as a way to leave or cancel Setup.

#### Scenario: User declines tool setup

- **WHEN** Setup appears and the user does not select consent or activate Download and continue
- **THEN** Downlet does not retrieve or run a missing third-party tool

#### Scenario: User chooses tool setup

- **WHEN** the user selects consent and activates Download and continue
- **THEN** Downlet downloads only the named pinned tools, verifies each artifact, installs them locally, and continues resolving the submitted URL

#### Scenario: Existing tools are discoverable

- **WHEN** valid yt-dlp and co-located FFmpeg and ffprobe executables are available through an override, Downlet-managed storage, or `PATH`
- **THEN** Downlet bypasses Setup and continues resolving without downloading replacement tools

#### Scenario: Verification or installation fails

- **WHEN** a tool download, hash verification, or installation fails
- **THEN** Setup reports a concise failure and allows the user to try again without exposing backend output

### Requirement: Windows distribution is portable and self-contained

Downlet SHALL be distributed for Windows 10 and 11 x64 as one downloadable `Downlet.exe` that requires neither installation nor a system Java runtime. The application window, taskbar presence, and distributed executable SHALL use the same Downlet brand icon. The executable SHALL NOT exceed 90 MiB and SHOULD remain at or below the 65 MiB strong target. It MAY extract and cache its bundled, jlink-trimmed JetBrains Runtime and application payload under the user's local application-data directory. It SHALL verify the cached payload, recover from incomplete or invalid contents, coordinate simultaneous first launches, forward command-line arguments, and return the application's exit code. It SHALL create no registry entries, shortcuts, services, PATH changes, uninstaller, or administrator prompt. yt-dlp and FFmpeg SHALL remain external to the distributed executable.

#### Scenario: Downlet starts on a machine without Java

- **WHEN** the user starts the distributed `Downlet.exe` without Java installed or available on `PATH`
- **THEN** Downlet opens using its bundled trimmed runtime without installation or elevation

#### Scenario: Downlet starts for the first time

- **WHEN** no valid cached payload exists
- **THEN** Downlet validates and extracts its payload atomically under local application data before opening

#### Scenario: Downlet starts again

- **WHEN** a valid cached payload already exists
- **THEN** Downlet reuses that payload without reinstalling the application

#### Scenario: Cached payload is incomplete or invalid

- **WHEN** payload verification fails
- **THEN** Downlet replaces the invalid cache with a verified payload or shows a native error message if recovery fails

### Requirement: Window size follows task stage

The primary window SHALL use a fixed width and two automatic height tiers: Compact for Empty and Previewing, and Expanded for Setup, Resolving, Ready, Downloading, Completed, and Error. Manual resize and maximize SHALL be unavailable while ordinary minimize and close remain available. Height changes SHALL keep the URL anchor stable, remain within the active work area, and use brief interruptible motion with an equivalent instant result when motion duration is disabled.

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
