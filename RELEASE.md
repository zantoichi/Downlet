# Downlet release guide

This guide separates automated release work from account-bound approval and publication work. The workflow must not present an unsigned or unreviewed artifact as stable.

## Release outputs

| Target | Identity | Artifact |
| --- | --- | --- |
| GitHub Releases | Portable | `Downlet-<version>-windows-x64-portable.exe` |
| Shared installer | Downlet | `Downlet-<version>-windows-x64-user.msi` |
| Chocolatey | `downlet` | Thin package that downloads the signed MSI |
| WinGet | `Downlet.Downlet` | User-scoped WiX/MSI manifest |

Windows Store packaging is deferred. Bundled yt-dlp or FFmpeg, application self-update, extra architectures, and automated package-manager submission are out of scope.

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

`-SkipSignatureCheck` marks generated metadata as unverified. Never submit that output to Chocolatey or WinGet.

## One-time repository setup

These steps require the public GitHub repository owner:

1. Add and push the public `origin` remote.
2. Create a protected GitHub environment named `public-release` with required reviewers.
3. Enable immutable releases in repository settings before the first stable publication.
4. Add the SignPath secret and variables listed in [CODE_SIGNING.md](CODE_SIGNING.md).
5. Apply to SignPath Foundation with this repository, the code-signing policy, the privacy statement, and named maintainer roles.
6. Configure the SignPath artifact rule to sign both final-named files and return those names unchanged.

If SignPath Foundation rejects the project, stop the stable release. Evaluate an open-source Certum certificate as the fallback. Do not weaken the stable signing requirement.

## Unsigned bootstrap

The one-time bootstrap validates the public workflow before trusted signing exists:

```powershell
git tag -a v0.0.1-rc.1 -m "Downlet 0.0.1 release candidate"
git push origin v0.0.1-rc.1
```

The workflow builds internal Windows version `0.0.1`, skips SignPath, omits package-manager metadata, and publishes a prominent unsigned prerelease warning. Test its portable and MSI behavior, but do not submit it to package managers.

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
6. Review and manually approve the SignPath signing request.
7. Confirm the workflow verifies both timestamped signatures, writes checksums, creates attestations, and publishes the immutable release.
8. Download the published files and repeat signature, checksum, and attestation verification from a clean machine.

Corrections use a new patch version. Never replace an asset attached to a published stable release.

## Chocolatey submission

The stable workflow uploads `package-manager-submissions-<version>` containing a generated `.nupkg`. It has no yt-dlp, FFmpeg, Java, or runtime dependency. Its install script downloads the immutable signed MSI, verifies the exact SHA-256 hash, and passes:

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

## Clean-machine acceptance

Run these checks before package-manager submission:

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

- [ ] Public GitHub remote connected and pushed.
- [ ] `public-release` environment protected.
- [ ] Immutable releases enabled.
- [ ] SignPath Foundation project accepted and configured.
- [ ] GitHub SignPath secret and variables installed.
- [ ] Unsigned bootstrap tested and kept out of package managers.
- [ ] Stable signing request manually approved.
- [ ] Stable release verified from a clean machine.
- [ ] Chocolatey per-user MSI behavior confirmed, then package submitted.
- [ ] WinGet identifier confirmed, manifests validated, then pull request submitted.
