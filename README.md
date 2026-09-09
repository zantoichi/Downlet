# Downlet

Downlet is a focused Windows desktop app for downloading one accessible YouTube video or audio item at a time. It keeps yt-dlp and FFmpeg details out of the interface while preserving truthful progress, explicit consent, and a recoverable download flow.

Windows 10 or 11 x64 is required. Downlet includes its own trimmed runtime, so system Java is not required.

<p align="center">
  <img src="docs/images/downlet-light-ready.png" width="49%" alt="Downlet ready to download a video in the light theme">
  <img src="docs/images/downlet-dark-downloading.png" width="49%" alt="Downlet downloading with byte, speed, and ETA progress in the dark theme">
</p>

## Install

| Channel | Command or artifact | Status |
| --- | --- | --- |
| GitHub Releases | `Downlet-<version>-windows-x64-portable.exe` | Recommended: run without installing |
| GitHub Releases | `Downlet-<version>-windows-x64-user.msi` | Optional per-user installer |

Get both downloads from the [first preview release](https://github.com/zantoichi/Downlet/releases/tag/v0.0.1-rc.1). WinGet and Chocolatey distribution is planned for a later signed release and is not available yet.

The portable EXE runs without installation or elevation and caches its runtime under local application data. The MSI installs for the current user, adds one Start Menu entry, and appears in Apps & Features. It creates no desktop shortcut and does not modify `PATH`.

The first `v0.0.1-rc.1` preview is unsigned and intended for feedback. Windows may show an unknown-publisher or SmartScreen warning. It also validates the pipeline for future signed releases. Do not submit it to package managers. Stable releases remain blocked until trusted signing works.

## What Downlet does

- Resolves one YouTube video and presents useful video or audio choices without backend format IDs.
- Downloads video with its selected quality and source audio, then merges separate streams when needed.
- Saves original audio without conversion or converts it to MP3 at the selected quality.
- Shows transferred bytes, total size when known, current speed, approximate ETA, and native Windows taskbar progress.
- Keeps transfer progress monotonic across separate video and audio streams.
- Supports cancellation with transactional cleanup. A completed file is published only after the download and processing command succeeds.
- Categorizes availability, network, storage, processing, tool, and unknown failures with focused recovery actions.
- Shows the exact completed path and selects the saved file in Explorer through **Show in folder**.

## Local 0.0.2 candidate: startup and VPN compatibility

The local `0.0.2` portable shows a compact, theme-aware **Downlet** startup panel only if startup takes longer than 250 ms. It displays **Starting…**, **Preparing first launch…** during a new build’s extraction, or **Preparing Downlet…** during cache recovery. It disappears after the main content draws and the UI event queue responds; fast launches skip it entirely. These changes are not in the published `v0.0.1-rc.1` download yet.

The candidate initializes Windows download folders and its HTTP client in one shared background operation after the main window appears. Entering a link early waits without blocking input. Opening Downlet alone does not download tools or contact YouTube.

Managed yt-dlp uses the official self-contained `2026.08.19` unpacked Windows distribution (about 18 MB), with no system Python required. At the next tool preparation, an older managed single EXE migrates to `yt-dlp/2026.08.19-unpacked`. Downlet verifies the ZIP and all 140 installed files before activation; the legacy copy remains intact. “Preparing tools” can be retried after a failure. Environment overrides and PATH fallbacks keep their existing precedence.

Both candidate packages use IPv4-only Java sockets to avoid a Windows VPN split-tunneling incompatibility. Downlet respects your VPN routes and exclusions and never changes firewall or VPN settings. This setting cannot connect to IPv6-only hosts and does not change yt-dlp's networking.

If Downlet cannot connect, check your connection and VPN or firewall settings, then retry. An app opened from an excluded browser may inherit that browser's split-tunneling exclusion. For the original preview, opening the downloaded EXE from Explorer instead of the browser may help. A network error does not necessarily mean the YouTube link is invalid.

## External tools

Downlet does not bundle yt-dlp, FFmpeg, or FFprobe. On first use it explains why they are needed and downloads pinned copies only after explicit consent. Every managed download is checked against a pinned SHA-256 hash.

If a Downlet-managed tool is damaged, Downlet repairs that pinned copy once without asking for consent again. Bundled QuickJS support repairs locally without a network request. Downlet never replaces invalid override or `PATH` tools.

See [third-party notices](THIRD_PARTY_NOTICES.md) for versions, licenses, and upstream sources.

## Privacy

In the local 0.0.2 candidate, the main window opens centered. If YouTube asks for verification, choose a browser where the video plays. Downlet reuses that browser for later links until you close the app; yt-dlp reads its current cookies without Downlet saving them. Successful metadata lookups are cached separately for each browser choice. A failed lightweight preview no longer blocks yt-dlp from trying the video. YouTube can still require a fresh browser check.

Downlet has no telemetry, analytics, account, or persistent download history. Resolved media details are cached only in memory for the current session.

After a valid link is entered, Downlet requests lightweight title, channel, and thumbnail data from YouTube. Media transfer starts only after the user chooses Download. Missing yt-dlp and FFmpeg tools are downloaded only after consent; repair uses the same pinned upstream artifacts.

This program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it.

## Release size

Each release includes `release-metadata.json` with exact artifact sizes, signature-verification status, and MSI identity. The local unsigned v0.1.0 baseline is:

| Artifact | Size |
| --- | ---: |
| Portable EXE | 58.6 MiB |
| Per-user MSI | 73.7 MiB |
| Extracted application payload | 160.8 MiB |

The performance build uses a compressed, trimmed JBR 25 runtime and an application-trained AOT cache. Package and extracted sizes are reported; measured startup improvements take priority over the old size targets. See [performance measurements](docs/performance-2026-09-09.md).

## Verify a release

Download both the artifact and `SHA256SUMS.txt` from the same release. In PowerShell:

```powershell
Get-FileHash .\Downlet-0.0.1-windows-x64-portable.exe -Algorithm SHA256
```

Compare the hash with `SHA256SUMS.txt`; use the MSI filename to verify the installer. The preview is unsigned. Future stable artifacts must also have a valid timestamped Authenticode signature.

With GitHub CLI installed:

```powershell
gh attestation verify .\Downlet-0.0.1-windows-x64-portable.exe --repo zantoichi/Downlet
```

SignPath Foundation signing is planned, subject to approval; it is not enabled for this preview.

See the [code-signing policy](CODE_SIGNING.md) and [release guide](RELEASE.md) for the full trust and publication process.

## Responsible use

Downlet grants no rights to media. Download only content you own or have permission to download, and follow applicable law and YouTube's terms. Downlet does not bypass sign-in, access controls, geographic restrictions, or private-media permissions.

## License

Downlet's original source code is available under the [Zero-Clause BSD license](LICENSE). Redistributed and downloaded components retain their own licenses.
