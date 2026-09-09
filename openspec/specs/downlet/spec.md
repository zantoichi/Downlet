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

Downlet SHALL keep a visibly labelled YouTube-link field throughout the flow. A valid pasted link SHALL begin Previewing immediately, and a valid manually typed link SHALL begin it after a short idle delay. Watch, short, embed, live, `youtu.be`, and privacy-enhanced embed links for one video SHALL normalize to `https://www.youtube.com/watch?v=<id>` before preview; playlist-only, channel, profile, search, homepage, malformed, and unsupported links SHALL remain invalid. Previewing SHALL request only lightweight YouTube identity metadata and a bounded thumbnail, SHALL download no media, and SHALL not require yt-dlp. Invalid text SHALL remain editable with concise inline validation that is visually and semantically distinct without relying on color alone. No separate Paste or Analyze action SHALL be required.

#### Scenario: User enters a valid link

- **WHEN** the user pastes a valid YouTube URL or pauses after typing one
- **THEN** Downlet enters Previewing without requiring Enter or another command

#### Scenario: Alternate video link canonicalizes

- **WHEN** the user enters a supported single-video URL with playlist, timestamp, tracking, or fragment context
- **THEN** Downlet replaces it with the canonical HTTPS watch URL and uses that value throughout preview, caching, resolution, and download

#### Scenario: Preview succeeds

- **WHEN** Downlet receives the media title, channel, and optional thumbnail
- **THEN** it enters Setup when required tools are missing or Resolving when they are already available

#### Scenario: Preview fails

- **WHEN** the lightweight metadata request fails or does not identify media
- **THEN** Downlet uses a neutral YouTube placeholder and continues to tool discovery and authoritative yt-dlp resolution; missing tools still require setup consent

#### Scenario: User enters an invalid link

- **WHEN** the field does not contain a valid YouTube URL
- **THEN** Downlet remains editable in Empty and explains the validation problem without starting resolution

### Requirement: Ready exposes only useful choices

Ready SHALL identify the resolved media with a thumbnail or stable missing-preview fallback, title, channel, duration, and provider. It SHALL expose visibly labelled sections for Video or Audio, an understandable format and quality choice, the current destination with a Change action, a concise per-download authorization confirmation, a Read full terms action, and one Download action. Video choices SHALL show resolution with bitrate and MAY add FPS, friendly codec and container names, estimated size, and HDR when available. Supporting detail SHALL remain concise and SHALL NOT expose format IDs or backend syntax. Media identity, Save to, and Permission SHALL remain full-width, while Download as and Format & quality SHALL share one row. The Save to icon, label, path, and Change action SHALL share one line. Download SHALL remain disabled until the user selects that confirmation. It SHALL NOT expose extractor details, raw logs, or advanced command-line options.

#### Scenario: Media resolves

- **WHEN** resolution succeeds
- **THEN** Ready shows the media identity and the choices required to start a download

#### Scenario: User chooses audio output

- **WHEN** the user selects Audio
- **THEN** Original audio without conversion is selected by default and shows its source codec, container, and average bitrate, while MP3 remains available at high-quality VBR around 190 kbps, 160 kbps, and 128 kbps quality
- **AND** Downlet explains that MP3 re-encodes the source for compatibility, cannot restore missing detail, and may add quality loss

#### Scenario: User reads audio quality help

- **WHEN** the user opens Audio quality explained beside the audio format selector
- **THEN** Downlet shows paged explanations of Original, MP3, FLAC's source-quality ceiling, playback loudness, clipping limits, and ReplayGain's metadata-only adjustment and player dependence, with Previous and Next navigation and no scrolling
- **AND** the help states that Downlet does not boost or normalize exports and does not currently add ReplayGain tags
- **AND** Back to download restores the user's format, destination, and authorization without starting a download

#### Scenario: User changes the destination

- **WHEN** the user activates Change
- **THEN** Downlet opens the native Windows folder picker, updates the destination after a selection, and preserves the current destination after cancellation

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

Downloading SHALL preserve media context, lock choices that must not change, and expose Cancel as a secondary action. Each attempt SHALL begin in Preparing. Transfer progress SHALL aggregate downloaded bytes across selected video and audio streams in completion order, use exact totals before estimates, remain indeterminate when no trustworthy total exists, and never move the visible fraction backward when totals change or an unexpected stream appears. It SHALL show downloaded bytes, available total, speed, and approximate ETA without fabricated placeholders. After transfer it SHALL identify Merging, Converting, or other Finalizing work with indeterminate progress. Audio conversion SHALL show elapsed time without presenting a fabricated percentage or remaining-time estimate. A displayed 100 percent SHALL represent transferred bytes only and SHALL NOT cause completion; only successful process exit and transactional publication SHALL enter Completed. The Windows taskbar SHALL mirror active progress when supported and otherwise remain a no-op. Completed SHALL show the exact published path with Show in folder and Download another; Show in folder SHALL open the containing folder with that exact file selected, including paths with spaces or Unicode characters. Error SHALL categorize authentication, availability, network, storage, processing, tool, and unknown failures without raw diagnostics. Authentication recovery SHALL use warning severity and expose Firefox, Chrome, and Edge session actions; other failures SHALL expose Retry, and storage failures SHALL additionally expose Change folder.

#### Scenario: User cancels a download

- **WHEN** the user activates Cancel
- **THEN** Downlet returns to Ready with the previous choices and destination preserved, removes files owned by the cancelled attempt, and leaves pre-existing destination files unchanged

#### Scenario: Download completes

- **WHEN** progress completes successfully
- **THEN** Downlet publishes one final media file into the chosen destination, enters Completed with its exact path, and offers Show in folder and Download another

#### Scenario: Destination filename already exists

- **WHEN** publication finds the requested filename already present
- **THEN** Downlet leaves every existing file unchanged and atomically publishes as `name (2).ext`, incrementing the suffix until an unused name succeeds

#### Scenario: User reveals the completed file

- **WHEN** the user activates Show in folder
- **THEN** Downlet asks Windows Explorer to select the exact path stored by Completed without reconstructing it from media metadata

#### Scenario: Transfer total is unavailable

- **WHEN** yt-dlp reports downloaded bytes or speed without a trustworthy aggregate total
- **THEN** Downlet keeps the rail indeterminate, shows only available telemetry, and does not invent a percentage

#### Scenario: Separate media streams transfer

- **WHEN** video finishes and audio begins reporting bytes from zero
- **THEN** Downlet adds completed video bytes to current audio bytes and keeps visible progress monotonic

#### Scenario: Download fails

- **WHEN** downloading fails
- **THEN** Downlet removes files owned by the failed attempt, enters category-specific Error guidance, and Retry restarts the download with the previous choices

#### Scenario: Known diagnostics map to actionable categories

- **WHEN** diagnostics identify path-length failures, HTTP 404 or DRM availability failures, or DNS and abruptly closed network failures
- **THEN** Downlet maps them to Storage, Availability, or Network respectively while unknown and stale-format failures remain Unknown

#### Scenario: YouTube requires authentication

- **WHEN** diagnostics require age confirmation or browser cookies
- **THEN** Downlet offers Firefox, Chrome, and Edge, explains that the selected profile must be able to watch the video while signed in, and does not expose a redundant unauthenticated Retry
- **AND** selecting a browser gives yt-dlp temporary cookie access for resolution, download, and retries across videos until the app closes without Downlet storing or displaying cookie values

#### Scenario: YouTube challenges a public-video session

- **WHEN** YouTube asks the user to confirm they are not a bot
- **THEN** Downlet explains that this can affect public videos without implying an age restriction, offers Retry and browser-session recovery, and asks the user to complete any YouTube check in that browser first

#### Scenario: Storage recovery changes destination

- **WHEN** a storage failure is visible and the user activates Change folder
- **THEN** Downlet remains in Error with the updated destination and Retry uses that destination with the previous mode and quality

### Requirement: Optional work does not delay readiness

After the first main-content draw and a serviced UI event, Downlet SHALL initialize its download runtime and prevalidate installed tools in the background without downloading tools. Preview metadata and thumbnails SHALL have a combined three-second budget and SHALL run independently of tool validation and resolution. Ready SHALL appear as soon as formats resolve. Late previews SHALL preserve resolved identity, mode, quality, destination, and consent; obsolete requests SHALL not change the current state. Resolving SHALL stop after thirty seconds with a recoverable network error and terminate its process tree. This deadline SHALL NOT cap media transfer or conversion.

After a successful resolution, Downlet MAY prepare at most one selected download CLI process after a 250 ms debounce. It SHALL withhold media information until explicit Download authorization, expire unused preparation after sixty seconds, cancel obsolete preparation, and start normally if preparation is unavailable. Cancellation or expiry SHALL not automatically respawn it. Tool setup MAY download the two independent assets concurrently only after existing consent requirements are satisfied, SHALL show per-tool progress, and SHALL verify each asset before activation. Fragment downloads SHALL use bounded concurrency and SHALL fail rather than publish an incomplete result.

### Requirement: Downloads use yt-dlp

Normal product operation SHALL use a local `yt-dlp` process alongside optional Previewing and after any required Setup to resolve authoritative YouTube metadata and download the selected Video or Audio format and quality. Resolution SHALL obtain media identity and available formats in one process call, select explicit stream IDs, and use those IDs for download so displayed details match the selected streams. MP3 downloads SHALL transfer audio-only input and high-quality VBR SHALL use quality level 2. Each attempt SHALL use an isolated staging directory inside the chosen destination, and Downlet SHALL publish the final media file only after `yt-dlp` exits successfully. It SHALL provide bundled QuickJS-NG to `yt-dlp` for YouTube JavaScript support and FFmpeg for merging and audio processing. Resolving SHALL use `--skip-download`, preserve preview identity while checking available formats, and download no media. After an explicit browser choice, resolution and download SHALL add only `--cookies-from-browser` with the selected Firefox, Chrome, or Edge identifier. Downlet SHALL translate transfer and processing events into its existing product states, SHALL stop and await the active process tree when Cancel is activated, SHALL remove attempt-owned staging files after success, failure, or cancellation, and SHALL keep command output and cookie values out of the interface. The deterministic fake runtime MAY remain available only for tests and the Design Review app.

Successful preview and resolution results SHALL be cached only for the current application session. Resolution entries SHALL be keyed by canonical video URL and selected browser, including anonymous requests. Resolution entries SHALL expire after five minutes or a changed tool identity. Sanitized information JSON MAY be retained in memory and sent through stdin to the selected download, with yt-dlp allowed one fresh extraction fallback. The cache SHALL retain at most 16 entries, 16 MiB of compressed thumbnail data, and 64 MiB of information strings, evict least-recently-used complete entries when any limit is exceeded, and SHALL NOT cache failures, download choices, progress, chosen destinations, cookie values, or downloaded files.

#### Scenario: A link resolves through yt-dlp

- **WHEN** preview succeeds and required tools are available
- **THEN** Downlet resolves its title, channel, duration, and identity through `yt-dlp` before entering Ready

#### Scenario: A real download runs

- **WHEN** the user activates Download in Ready
- **THEN** Downlet starts `yt-dlp` with the selected mode, format, and quality in an isolated staging directory, reflects reported transfer and processing phases, and publishes the final file only after successful process completion

#### Scenario: A real download is cancelled

- **WHEN** the user activates Cancel while `yt-dlp` is running
- **THEN** Downlet terminates and awaits the process tree, removes the attempt's staged and newly published artifacts, and returns to Ready with the prior choices preserved

### Requirement: Tool setup is explicit and verified

Downlet SHALL bundle a pinned QuickJS-NG Windows executable and its required notices, but SHALL NOT include yt-dlp or FFmpeg in its distribution. After Previewing finishes, including a failed optional preview, Setup SHALL appear when yt-dlp or FFmpeg is unavailable or when a previously installed Downlet-managed copy requires repair. Tool discovery SHALL check valid explicit environment overrides, then integrity-verified Downlet-managed tools under local application data, then `PATH`, and SHALL NOT scan other folders or drives. Invalid overrides and invalid managed copies SHALL fall through to the next source, and FFmpeg SHALL count as available only when its co-located `ffmpeg` and `ffprobe` executables match their pinned SHA-256 hashes. Setup for missing tools SHALL keep the media preview visible, name only the missing tools, state their approximate download size and purpose, clarify that tool setup does not download the media, leave consent unselected, and download nothing until the user selects consent and activates Download and continue. A damaged or incomplete managed installation with no valid external fallback SHALL enter automatic Repair without renewed consent, replace only the affected managed tools with the same pinned and verified versions, and SHALL NOT alter overrides or `PATH` tools. Bundled QuickJS-NG SHALL repair locally without network access. Setup SHALL expose Read full terms, which replaces the work area with preview-network, tool-license, media-responsibility, and liability information and provides Back to setup without losing the URL or consent state. Downlet SHALL retrieve pinned upstream artifacts with a fixed upper byte limit, verify their SHA-256 hashes before installation, delete partial artifacts after failure or cancellation, store verified tools under the user's local application-data directory, and invoke them as separate processes. Editing the URL SHALL remain available as a way to leave or cancel Setup or Repair.

#### Scenario: User declines tool setup

- **WHEN** Setup appears and the user does not select consent or activate Download and continue
- **THEN** Downlet does not retrieve or run a missing third-party tool

#### Scenario: User chooses tool setup

- **WHEN** the user selects consent and activates Download and continue
- **THEN** Downlet downloads only the named pinned tools, verifies each artifact, installs them locally, and continues resolving the submitted URL

#### Scenario: Existing tools are discoverable

- **WHEN** valid yt-dlp and co-located FFmpeg and ffprobe executables are available through an override, Downlet-managed storage, or `PATH`
- **THEN** Downlet bypasses Setup and continues resolving without downloading replacement tools

#### Scenario: Managed tool is damaged

- **WHEN** an existing Downlet-managed yt-dlp or FFmpeg installation fails integrity validation and no valid override or `PATH` fallback exists
- **THEN** Downlet enters automatic Repair, downloads only the affected pinned tool, verifies it before replacement, re-resolves the media, preserves the destination, and returns to Ready with default choices and fresh download authorization

#### Scenario: Bundled QuickJS is damaged

- **WHEN** the managed QuickJS executable fails integrity validation
- **THEN** Downlet restores it from the bundled verified resource without network access or consent

#### Scenario: External tool fails

- **WHEN** an override or `PATH` tool cannot run
- **THEN** Downlet reports a Tool error without changing, deleting, or replacing that external tool

#### Scenario: Verification or installation fails

- **WHEN** a tool download, hash verification, or installation fails
- **THEN** Setup reports a concise failure and allows the user to try again without exposing backend output

#### Scenario: Automatic repair fails repeatedly

- **WHEN** automatic Repair fails or the repaired tool fails again in the same operation
- **THEN** Downlet allows an explicit repair retry after installation failure and otherwise reports a recoverable Tool error without looping or restarting the media download

### Requirement: Windows distributions are self-contained and user-scoped

Downlet SHALL be distributed for Windows 10 and 11 x64 as both one portable `Downlet.exe` and one per-user MSI. Neither distribution SHALL require a system Java runtime or administrator elevation. The application window, taskbar presence, portable executable, installed launcher, Start Menu entry, and Apps & Features registration SHALL use the Downlet identity and brand icon. Package and extracted sizes SHALL be reported, with measured startup speed taking priority over fixed size targets. Both packages SHALL use a compressed, jlink-trimmed JBR 25 runtime and an application-trained AOT cache created from their final runtime and application jars. The cache SHALL support relocated installation paths and normal JVM startup when unusable. It MAY extract and cache its bundled, jlink-trimmed JetBrains Runtime and application payload under the user's local application-data directory. It SHALL verify the cached payload, recover from incomplete or invalid contents, coordinate simultaneous first launches, forward command-line arguments, return the application's exit code, and best-effort remove inactive stale caches created by lease-aware launchers without delaying or failing startup. Current, active, unrecognized, and locked payload caches SHALL remain untouched. The portable distribution SHALL create no registry entries, shortcuts, services, PATH changes, uninstaller, or administrator prompt. The MSI SHALL install under local application data, create one Start Menu entry and Apps & Features registration, offer no directory chooser or desktop shortcut, and support silent per-user installation. Upgrade and uninstall SHALL preserve downloaded media, Downlet-managed tools, and other user-created data. yt-dlp, FFmpeg, and FFprobe SHALL remain external to both distributions.

The portable launcher SHALL begin payload preparation immediately and keep its native startup panel hidden until 250 ms after launcher entry. It SHALL skip the panel if the per-launch Windows readiness event has already signaled the first main-content draw followed by service on the UI event queue. Otherwise it SHALL show a responsive, non-activating 320 × 112 logical-pixel panel centered on the primary work area, with the existing 32-pixel icon, a 16-pixel Segoe UI Semibold Downlet title, and 14-pixel status text. It SHALL follow Windows application light/dark preference, default to light, and use system colors in high contrast. It SHALL request native rounded corners and shadow where supported, retaining a bordered fallback on Windows 10. Status SHALL read “Starting…”, “Preparing first launch…” for a missing build cache, or “Preparing Downlet…” during invalid-cache recovery, returning to “Starting…” after activation. Readiness, startup failure, and exit SHALL permanently cancel delayed visibility and dismiss the panel. Startup failure SHALL retain the native error dialog. No artificial progress, decorative animation, minimum display time, or delay to payload preparation SHALL be introduced. The MSI SHALL continue launching directly.

Windows packages SHALL use IPv4-only Java networking for VPN split-tunneling compatibility without changing VPN routing, exclusions, or firewall configuration. IPv6-only hosts are not supported by Java requests. Optional preview failures SHALL fall through to yt-dlp resolution and existing consent-based tool setup. Resolution failures SHALL retain their categorized reason, including network failures, and remain recoverable.

#### Scenario: Portable startup feedback

- **WHEN** the user starts the portable, with or without a valid cached payload
- **THEN** payload preparation starts immediately; if the main window is not ready after 250 ms, the native panel appears without taking focus and stays responsive until readiness or startup failure

#### Scenario: Preview connection is denied

- **WHEN** a preview request fails because its network connection is denied
- **THEN** Downlet continues to yt-dlp; if resolution also fails, its failure reason determines recovery guidance instead of treating the link as invalid

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

#### Scenario: An older portable payload cache is inactive

- **WHEN** a lease-aware older cache is not current or in use
- **THEN** Downlet removes it without delaying startup, while cleanup failure leaves it available for a later retry

#### Scenario: User installs the MSI

- **WHEN** the user runs the MSI interactively or silently
- **THEN** Downlet installs for that user without elevation, appears in the Start Menu and Apps & Features, and creates no desktop shortcut or PATH entry

#### Scenario: User upgrades or uninstalls the MSI

- **WHEN** the user installs a newer MSI or removes Downlet
- **THEN** Windows updates or removes the registered application files while preserving downloads, managed tools, and other user-created data

### Requirement: Window size follows task stage

The primary window SHALL initially appear centered on its screen, then preserve user movement and existing resize anchoring. The primary window SHALL use a fixed `760` logical-pixel width and content-driven height with a minimum of `188` logical pixels. Setup phases, Resolving, Ready, Downloading, Completed, Error, and help pages SHALL fit their measured content without scrolling. Full terms and audio help SHALL show one section per page with Previous and Next controls. Manual resize and maximize SHALL be unavailable while ordinary minimize and close remain available. Height changes SHALL keep the URL anchor stable, remain within the active work area, and use brief interruptible motion with an equivalent instant result when motion duration is disabled. If the work area is too small, content SHALL fit proportionally so actions remain visible. The Downlet icon and wordmark SHALL remain centered in the title bar independently of the trailing theme control.

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

Downlet SHALL provide logical keyboard order, visible focus, meaningful control and status semantics, understandable progress, and status communication that does not rely on color or motion alone. The progress rail SHALL expose determinate or indeterminate semantics matching its visible state. A separate polite announcement SHALL change only for Preparing, download start, 10-percent milestones through 90 percent, and Finalizing stage changes; it SHALL NOT announce transfer 100 percent. Completed and Error SHALL retain their existing polite announcements. On launch, Downlet SHALL follow the host system's light or dark preference with light as the fallback, and it SHALL expose a title-bar control that switches between light and dark without changing the download state. The visible interface SHALL use the native Windows sans-serif stack at no less than `14sp`, use `16sp` for body text and controls, keep controls Regular, provide text buttons at least `36` logical pixels of height, and reserve SemiBold for the brand, headings, form labels, media identity, numeric progress, and important result labels. Format supporting text SHALL remain available to accessibility services and wrap to at most two lines. Label and action icons, including the progress leading cap, SHALL remain decorative to accessibility services. Essential actions SHALL remain visible in light and dark themes, with long content, missing previews, and common Windows scaling through 150 percent.

#### Scenario: User operates Downlet by keyboard

- **WHEN** the user navigates and activates controls without a pointer
- **THEN** focus follows reading order, native activation works, and reset returns focus to the YouTube-link field

#### Scenario: Theme or scale changes

- **WHEN** Downlet is shown in light or dark theme at common Windows scaling
- **THEN** text, focus, status, and essential actions remain legible and reachable

#### Scenario: User switches theme

- **WHEN** the user activates the title-bar theme control
- **THEN** Downlet switches between light and dark immediately without restarting or changing the entered URL, current download state, or selections
- **AND** the icon-only control has a `32dp` target and a `20dp` sun in light mode or crescent moon with a selected background in dark mode; its tooltip names the next action and accessibility semantics expose the current mode
- **AND** the sun and moon morph into one another over `160 ms`, reversing from the current shape when interrupted, without delaying the window colors; disabled motion shows the final shape immediately

#### Scenario: Motion is disabled

- **WHEN** the effective motion duration is zero
- **THEN** every state reaches the same visible and semantic result immediately and indeterminate progress uses a static centered highlight

The theme toggle tooltip SHALL remain available below the button, outside its pointer hit area. Repeated mouse clicks without moving the pointer SHALL toggle the theme without waiting for tooltip dismissal.

### Requirement: Deferred runtime initialization and unpacked yt-dlp

After the main window becomes visible, Downlet SHALL initialize Windows-folder discovery and the HTTP client once on Dispatchers.IO. Preview and tool operations SHALL await the same initialization. Editing a link SHALL cancel obsolete requests without cancelling initialization. Closing Downlet SHALL cancel initialization and active work. Initialization failure SHALL surface as a recoverable Tool error, with a fresh attempt available through Retry. Opening Downlet alone SHALL NOT download tools, contact YouTube, or start yt-dlp.

Managed yt-dlp SHALL use the official `2026.08.19` `yt-dlp_win.zip`, pinned by archive SHA-256 and a complete file/size/SHA-256 manifest. Installation SHALL reject unsafe paths, unexpected or duplicate files, extraction overflows, and incomplete or corrupted contents. It SHALL stage and verify the complete tree before activation into a distinct versioned directory. The next preparation of a legacy managed single EXE SHALL migrate it using neutral “Preparing tools” wording and existing retry behavior, preserving the legacy installation until activation succeeds. Override and PATH precedence SHALL remain unchanged. No system Python or idle yt-dlp process SHALL be required.
