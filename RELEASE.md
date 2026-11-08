# Downlet release guide

This guide separates automated release work from account-bound approval and publication work. The workflow must not present an unsigned or unreviewed artifact as stable.

## Release outputs

| Target | Identity | Artifact |
| --- | --- | --- |
| GitHub Releases | Portable | `Downlet-<version>-windows-x64-portable.exe` |
| Shared installer | Downlet | `Downlet-<version>-windows-x64-user.msi` |
| Chocolatey | `downlet` | Thin package that downloads the released MSI |
| WinGet | `Downlet.Downlet` | User-scoped WiX/MSI manifest |

Microsoft Store packaging, bundled yt-dlp or FFmpeg, application self-update, extra architectures, and automated package-manager submission are out of scope.

## Local release check

Build with an explicit numeric version:

```powershell
.\gradlew.bat --no-daemon --console=plain check smokeTest packageWindowsSingleExe packageMsi '-PdownletVersion=0.1.0'
```

Prepare unsigned metadata for local inspection only:

```powershell
.\scripts\release\Prepare-Release.ps1 `
  -Version 0.1.0 `
  -Repository OWNER/REPOSITORY `
  -PortablePath build\compose\binaries\main\windows-single-exe\Downlet.exe `
  -MsiPath build\compose\binaries\main\msi\Downlet-0.1.0.msi `
  -OutputDirectory build\release-local `
  -SkipSignatureCheck
```

`-SkipSignatureCheck` records `signaturesVerified: false` and writes unsigned wording into the Chocolatey verification file. Submit only the output of the release workflow, never a local build.

## First preview: repository setup

The unsigned demo needs only a public repository with GitHub Actions enabled and permission for the workflow token to publish releases. No SignPath credentials or protected environment are required. The workflow uses an unprotected `bootstrap-release` environment, separate from `public-release`.

The workflow bootstraps Gradle with Java 21, resolves JetBrains Runtime 25 through the existing toolchain resolver, and checks the Windows runner for MSVC, makecab, and WiX before packaging.

Create/connect `zantoichi/Downlet`, push the reviewed source and workflow, then follow **Unsigned bootstrap** below. Share the exact prerelease URL, since GitHub's latest-release link is intended for stable releases.

## Repository setup

Releases are unsigned until SignPath credentials exist. The workflow checks `SIGNPATH_ORGANIZATION_ID`: when it is empty, stable tags skip SignPath, publish unsigned files with checksums and attestations, and still generate Chocolatey and WinGet metadata. Completed setup:

1. Done: public `origin` remote connected and pushed.
2. Done (2026-11-08): `public-release` environment requires `zantoichi` approval and accepts only `v*` tags.
3. Done (2026-11-08): immutable releases are enabled.
To add signing later, apply to SignPath Foundation, add the secret and variables listed in [CODE_SIGNING.md](CODE_SIGNING.md) to `public-release`, and configure the artifact rule to return both files with unchanged names. The next stable tag is then signed with no workflow change.

## Unsigned bootstrap

The one-time bootstrap is a usable feedback demo and validates the public workflow before trusted signing exists:

```powershell
git tag -a v0.0.1-rc.1 -m "Downlet 0.0.1 release candidate"
git push origin v0.0.1-rc.1
```

The workflow builds internal Windows version `0.0.1`, skips SignPath, omits package-manager metadata, and publishes a prominent unsigned prerelease warning. Normal development remains at `0.1.0`. Test its portable and MSI behavior, but do not submit it to package managers.

For the local bootstrap build, run the local release command with `-PdownletVersion=0.0.1`. Prepare it with `-Version 0.0.1 -ReleaseTag v0.0.1-rc.1 -Repository zantoichi/Downlet` and both `-SkipSignatureCheck -SkipPackageManagerMetadata`.

Before sharing, download both published assets, verify their checksums, and test on clean Windows without Java: portable launch; MSI install, launch, and uninstall; first-use tool setup; one video download and one MP3 conversion. Confirm uninstall preserves downloads. Ask for feedback on setup, confusing steps, and failed downloads.

This release also supplies the existing downloadable artifacts required by [SignPath's application conditions](https://signpath.org/terms.html). Approval and package-manager onboarding remain separate later steps.

## Stable release

1. Set `downletVersion` in `gradle.properties` to the stable numeric version.
2. Update README sizes and screenshots if the UI or packaging changed.
3. Run the local release check and `git diff --check`.
4. Commit the release changes and create an annotated stable tag:

```powershell
git tag -a v0.1.0 -m "Downlet 0.1.0"
git push origin main v0.1.0
```

5. Approve the `public-release` environment deployment.
6. If signing is configured, review and manually approve the SignPath signing request.
7. Confirm the workflow writes checksums, creates attestations, and publishes the immutable release (and verifies timestamped signatures when signing is configured).
8. Download the published files and run `gh attestation verify <file> --repo zantoichi/Downlet` and the checksum check from a clean machine.

Corrections use a new patch version. Never replace an asset attached to a published stable release.

## Chocolatey submission

The stable workflow uploads `package-manager-submissions-<version>` containing a generated `.nupkg`. It has no yt-dlp, FFmpeg, Java, or runtime dependency. Its install script downloads the immutable released MSI, verifies the exact SHA-256 hash, and passes:

```text
/qn /norestart ALLUSERS=2 MSIINSTALLPERUSER=1
```

Before first submission:

1. Inspect the nuspec, install script, verification file, URL, and checksum.
2. Test install, upgrade, launch, and uninstall from an elevated Chocolatey session on clean Windows 10 and 11 machines.
3. Confirm the MSI installs for the invoking user, appears in Apps & Features, and can be detected and removed by Chocolatey.
4. Confirm uninstall preserves downloads and `%LOCALAPPDATA%\Downlet\tools`.
5. Submit manually with the Chocolatey account API key.

If elevated Chocolatey cannot reliably manage the per-user MSI, do not add a machine-wide MSI. Replace the Chocolatey package implementation with the signed portable EXE, suppress command shims, and manage one Start Menu shortcut. Re-run moderation tests before submission.

## WinGet submission

The stable workflow uploads three generated WinGet 1.12 manifests. Before first submission:

1. Confirm `Downlet.Downlet` is still available.
2. Run `winget validate --manifest <manifest-directory>` with current WinGet tooling.
3. Copy the manifests to `manifests/d/Downlet/Downlet/<version>` in a fork of `microsoft/winget-pkgs`.
4. Open the first manifest pull request manually.
5. Wait for URL checks and SandboxTest. Resolve findings without replacing published release assets.
6. On clean Windows 10 and 11 machines, test install, upgrade, launch, and uninstall through WinGet.

## Account-bound steps

Only these steps need the maintainer's identity, accounts, or agreement. Do them in order:

1. Tag `v0.1.0`, push it, and approve the `public-release` deployment.
2. Optional: test install, launch, and uninstall of the published MSI in Windows Sandbox (a Windows feature you enable yourself).
3. Chocolatey: create a community account, download `package-manager-submissions-<version>` from the workflow run, and `choco push` the `.nupkg` with your API key.
4. WinGet: in your fork of `microsoft/winget-pkgs`, copy the three manifests to `manifests/d/Downlet/Downlet/<version>` and open the pull request.

## Clean-machine acceptance

For the local `0.0.2` candidate, run `gradlew.bat --no-daemon --console=plain check smokeTest packageWindowsSingleExe packageMsi -PdownletVersion=0.0.2`. Do not retag or replace `v0.0.1-rc.1` assets. Verify the portable splash on fresh extraction and cached launches, its handoff to the main window, concurrent launches, and startup failure. Verify previews, tool setup, video download, and MP3 conversion with Mullvad split tunneling enabled, including launch from an excluded browser. Both package configurations must contain `-Djava.net.preferIPv4Stack=true`; this preserves VPN routing but cannot reach IPv6-only hosts.

Run these checks before package-manager submission:

Use `scripts/release/Test-PortableStartup.ps1 -PortablePath <candidate.exe>` for the splash timing and handoff check. It leaves the app open for visual inspection. Run once with a fresh version cache and again with the cache populated; a delayed startup should show the panel between 250 and 500 ms of launcher startup. Fast launches may correctly skip it; the probe discovers the main window independently and reports skipped panels.

- Portable launch without Java, installation, or elevation.
- Interactive and silent per-user MSI installation.
- Upgrade from bootstrap `0.0.1` to stable `0.1.0`.
- Separate video and audio stream merge and one MP3 conversion.
- Real bytes, speed, ETA, taskbar states, reduced motion, cancellation cleanup, and categorized failures.
- Exact completed path and **Show in folder** selection.
- Corrupt or remove managed yt-dlp, FFmpeg, FFprobe, and QuickJS separately; verify one bounded repair attempt and no modification of override or `PATH` tools.
- Uninstall preserves downloads and managed tools.
- No persistent history or session cache files.
- Portable and MSI payloads contain no yt-dlp, FFmpeg, or FFprobe executable.

## Final manual pieces

- [x] Public GitHub remote connected and pushed.
- [x] `public-release` environment protected.
- [x] Immutable releases enabled.
- [ ] Unsigned bootstrap tested and kept out of package managers.
- [ ] Optional later: SignPath accepted and its secret and variables installed.
- [ ] Stable release verified from a clean machine.
- [ ] Chocolatey per-user MSI behavior confirmed, then package submitted.
- [ ] WinGet identifier confirmed, manifests validated, then pull request submitted.

The performance candidate uses the official unpacked yt-dlp ZIP (2026.08.19) and starts download-runtime initialization after window display. Build it into `build/release-performance-0.0.2`; leave published assets unchanged. With this candidate, manually check splash handoff and centered startup, immediate link entry, sequential previews, Original audio, and MP3. Existing tools migrate on preparation; opening the app alone performs no tool downloads. Keep local-fixture benchmark timings separate from YouTube results, which may be challenged or network-dependent.

For the polished-startup candidate, use `build/release-startup-panel-0.0.2`. Native console checks run as part of portable packaging and cover timing, dismissal, status messages, fresh extraction, cached verification, invalid-cache recovery, and concurrent extraction in isolated test directories, without showing windows or launching the app. User-operated visual checks cover light/dark mode, high contrast, Windows 10 fallback, 100–200% scaling, centered placement, no focus theft, and handoff. Do not run the interactive startup probe automatically on the user's desktop.

The performance packaging tasks now train JBR 25 AOT caches after the final application jars and compressed runtime image are built. `trainStartupCache` writes the shared image to `build/startup-cache/Downlet`; both `packageWindowsSingleExe` and `packageMsi` consume it. The training script briefly launches an empty application window, closes its owned JVM window, waits for cache assembly, and removes the temporary Java launcher before packaging. Run packaging on a Windows desktop-capable runner. The production launcher uses an APPDIR-relative cache path and JVM fallback when the cache cannot be used. Rebuild the cache whenever runtime or jar inputs change; do not copy a cache between builds.

Inspect the recorded package sizes rather than enforcing the old 65/90 MiB targets. Validate startup readiness (first content draw plus UI event service), a relocated path containing spaces, an absent or incompatible AOT cache, and portable extraction/cache recovery. Read-only tool validation starts after readiness. The local opt-in CLI integration test uses `DOWNLET_YT_DLP_TEST=<path to pinned yt-dlp.exe>` and verifies that preparation makes no media request before Download.

The ProGuard experiment reduced jar size but failed focused launch checks involving reflected JBR/Jewel classes. Its experimental rules were discarded. Standard portable/MSI packages use the checked unshrunk application with AOT caching.

AOT also requires jar timestamps to match. Training rounds them to CAB's two-second precision, and portable staging preserves them. After packaging, run `pwsh -NoProfile -File scripts/release/Measure-Startup.ps1 -Executable build/compose/binaries/main/windows-single-exe/Downlet.exe -Portable -JvmOptions '-XX:AOTMode=on'`. This fails if the package silently falls back. Run the same probe without `-Portable` against an administratively extracted MSI image, including on a machine in a different time zone before release. Use `-XX:AOTMode=off` for a paired baseline; absent/incompatible-cache fallback must be checked without strict mode.
