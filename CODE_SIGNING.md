# Code-signing policy

Downlet releases are currently not code-signed. No signing service is active. Each release instead publishes SHA-256 checksums and GitHub artifact attestations, so anyone can confirm a file was built by this repository's release workflow:

```powershell
gh attestation verify Downlet-<version>-windows-x64-user.msi --repo zantoichi/Downlet
```

Signing turns on automatically, with no workflow change, once SignPath credentials are configured (see below). Until then, Windows may show an unknown-publisher or SmartScreen warning.

## Scope

When signing is configured, the protected release workflow signs only these Downlet-produced Windows artifacts:

- `Downlet-<version>-windows-x64-portable.exe`
- `Downlet-<version>-windows-x64-user.msi`

yt-dlp, FFmpeg, and FFprobe are downloaded from their upstream publishers after user consent. Downlet does not redistribute or sign them.

## Roles

- Committers: repository maintainers with write access. The initial maintainer is `zantoichi`.
- Reviewers: repository maintainers responsible for reviewing release changes, dependency changes, and release workflow changes.
- SignPath submitters: the GitHub Actions identity for this repository.
- SignPath approvers: named maintainers configured in the SignPath organization. The initial approver is `zantoichi`.

Changes from outside contributors require maintainer review before merge. Release approval is separate from committing or tagging.

## Release policy

1. Stable tags use `v<major>.<minor>.<patch>` and must match `downletVersion` in `gradle.properties`.
2. The protected workflow runs tests, smoke checks, portable packaging, MSI packaging, payload checks, and MSI identity checks.
3. The workflow uploads the exact portable EXE and MSI as one short-lived signing input.
4. While `SIGNPATH_ORGANIZATION_ID` is unset, stable releases ship unsigned and say so in the release notes and Chocolatey verification file.
5. When it is set, every stable release requires a manual SignPath approval, and both returned artifacts must have valid timestamped Authenticode signatures.
6. Checksums and Chocolatey and WinGet metadata are generated from the final published files.
7. GitHub artifact attestations bind the signed files to the release workflow.
8. Published release assets are immutable. A correction requires a new patch release.

The exact `v0.0.1-rc.1` tag is the only unsigned bootstrap exception. It serves as a feedback demo and the initial downloadable release for a future signing application. It uses the unprotected `bootstrap-release` environment, is marked as a prerelease, and must never be submitted to Chocolatey or WinGet.

## Privacy policy

Downlet has no telemetry, analytics, account, or persistent history. It contacts YouTube only after a user provides a supported link. It downloads media only after the user chooses Download. It downloads missing external tools only after explicit consent and may repair damaged Downlet-managed copies from the same pinned upstream artifacts. QuickJS repair is local.

This program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it.

## SignPath configuration (future)

To enable signing, the GitHub repository uses a protected `public-release` environment and these release settings:

- Secret: `SIGNPATH_API_TOKEN`
- Variable: `SIGNPATH_ORGANIZATION_ID`
- Variable: `SIGNPATH_PROJECT_SLUG`
- Variable: `SIGNPATH_SIGNING_POLICY_SLUG`
- Variable: `SIGNPATH_ARTIFACT_CONFIGURATION_SLUG`

The SignPath artifact configuration must return the two input files with unchanged names. The signing policy must use the SignPath Foundation certificate and timestamp both PE and MSI signatures.
