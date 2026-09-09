# Downlet

Downlet is a Windows app for downloading one accessible YouTube video or audio item at a time.

It shows the available qualities, projected size, transfer speed, and approximate time left. It can save the source audio or convert it to MP3, and it can merge a separate video and audio stream when needed.

<p align="center">
  <img src="docs/images/downlet-light-ready.png" width="49%" alt="Downlet ready to download a video">
  <img src="docs/images/downlet-dark-downloading.png" width="49%" alt="Downlet showing download progress">
</p>

## Download

The current preview is [v0.0.1-rc.1](https://github.com/zantoichi/Downlet/releases/tag/v0.0.1-rc.1).

- Use the portable EXE if you want to run Downlet without installing it.
- Use the per-user MSI if you want a Start Menu entry.

The preview is unsigned, so Windows may show an unknown-publisher or SmartScreen warning. It runs on Windows 10 and 11 x64 and does not need system Java.

On the first download, Downlet asks for permission to get the pinned yt-dlp and FFmpeg tools it needs. It checks their hashes before using them. These tools are stored under your local application data directory.

## Use Downlet

Paste a YouTube link, choose Video or Audio, select a quality and folder, then click Download. Original audio keeps the source codec and container. MP3 converts the source at the selected quality.

If YouTube asks for verification, choose a browser where the video plays. Downlet can use that browser's current session for the link while the app is open. It does not save browser cookies.

## Privacy and responsibility

Downlet has no telemetry, analytics, account, or persistent download history. Media details are cached only in memory for the current session. It contacts YouTube for the link and the required upstream tool hosts after you request those actions.

Download media only when you own it or have permission to do so. Downlet does not bypass sign-in, access controls, geographic restrictions, or private-media permissions.

## Build from source

The project uses the Gradle wrapper and resolves its required JDK toolchain automatically.

```powershell
.\gradlew.bat --no-daemon --console=plain check smokeTest
```

To build the Windows portable EXE and per-user MSI:

```powershell
.\gradlew.bat --no-daemon --console=plain packageWindowsSingleExe packageMsi '-PdownletVersion=0.0.1'
```

See [third-party notices](THIRD_PARTY_NOTICES.md) for bundled and downloaded components.

## License

Downlet is licensed under the [Zero-Clause BSD license](LICENSE).
