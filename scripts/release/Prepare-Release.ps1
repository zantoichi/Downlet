[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string] $Version,

    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string] $Repository,

    [Parameter(Mandatory)]
    [string] $PortablePath,

    [Parameter(Mandatory)]
    [string] $MsiPath,

    [Parameter(Mandatory)]
    [string] $OutputDirectory,

    [string] $ReleaseTag,

    [switch] $SkipSignatureCheck,

    [switch] $SkipPackageManagerMetadata
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExpectedUpgradeCode = '{D94DD278-F441-4D0C-8BE5-37F2116498F1}'
$ManifestVersion = '1.12.0'

function Write-Utf8File {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Content
    )

    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Get-MsiScalar {
    param(
        [Parameter(Mandatory)] [object] $Database,
        [Parameter(Mandatory)] [string] $Query
    )

    $view = $Database.OpenView($Query)
    try {
        [void] $view.Execute()
        $record = $view.Fetch()
        if ($null -eq $record) {
            return $null
        }
        $value = $record.StringData(1)
        [void] [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($record)
        return $value
    }
    finally {
        [void] $view.Close()
        [void] [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)
    }
}

function Get-MsiMetadata {
    param([Parameter(Mandatory)] [string] $Path)

    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $null
    try {
        $database = $installer.OpenDatabase($Path, 0)
        $metadata = [ordered]@{
            ProductName = Get-MsiScalar $database "SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'ProductName'"
            ProductVersion = Get-MsiScalar $database "SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'ProductVersion'"
            Manufacturer = Get-MsiScalar $database "SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'Manufacturer'"
            ProductCode = Get-MsiScalar $database "SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'ProductCode'"
            UpgradeCode = Get-MsiScalar $database "SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'UpgradeCode'"
            InstallDirectoryParent = Get-MsiScalar $database "SELECT ``Directory_Parent`` FROM ``Directory`` WHERE ``Directory`` = 'INSTALLDIR'"
            StartMenuShortcut = Get-MsiScalar $database "SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'JP_INSTALL_STARTMENU_SHORTCUT'"
            Platform = $database.SummaryInformation(0).Property(7)
        }

        $registryView = $database.OpenView('SELECT `Root` FROM `Registry`')
        try {
            [void] $registryView.Execute()
            $roots = [System.Collections.Generic.List[int]]::new()
            while ($record = $registryView.Fetch()) {
                $roots.Add($record.IntegerData(1))
                [void] [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($record)
            }
            $metadata.RegistryRoots = @($roots | Sort-Object -Unique)
        }
        finally {
            [void] $registryView.Close()
            [void] [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($registryView)
        }

        return [pscustomobject] $metadata
    }
    finally {
        if ($null -ne $database) {
            [void] [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($database)
        }
        [void] [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer)
    }
}

function Assert-TimestampedSignature {
    param([Parameter(Mandatory)] [string] $Path)

    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
        throw "Authenticode signature is not valid: $Path ($($signature.Status))."
    }
    if ($null -eq $signature.SignerCertificate) {
        throw "Authenticode signer certificate is missing: $Path."
    }
    if ($null -eq $signature.TimeStamperCertificate) {
        throw "Authenticode timestamp is missing: $Path."
    }
}

function Get-Sha256 {
    param([Parameter(Mandatory)] [string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

if ([string]::IsNullOrWhiteSpace($ReleaseTag)) {
    $ReleaseTag = "v$Version"
}

$portableSource = (Resolve-Path -LiteralPath $PortablePath).Path
$msiSource = (Resolve-Path -LiteralPath $MsiPath).Path
$output = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $output -Force | Out-Null

if (-not $SkipSignatureCheck) {
    Assert-TimestampedSignature $portableSource
    Assert-TimestampedSignature $msiSource
}
else {
    Write-Warning 'Signature verification was explicitly skipped. Do not submit these artifacts to package managers.'
}

$portableVersion = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($portableSource).ProductVersion
if ($portableVersion -ne $Version) {
    throw "Portable ProductVersion '$portableVersion' does not match release version '$Version'."
}

$msi = Get-MsiMetadata $msiSource
if ($msi.ProductName -ne 'Downlet') {
    throw "Unexpected MSI ProductName '$($msi.ProductName)'."
}
if ($msi.ProductVersion -ne $Version) {
    throw "MSI ProductVersion '$($msi.ProductVersion)' does not match release version '$Version'."
}
if ($msi.Manufacturer -ne 'Downlet') {
    throw "Unexpected MSI Manufacturer '$($msi.Manufacturer)'."
}
if ($msi.UpgradeCode -ne $ExpectedUpgradeCode) {
    throw "MSI UpgradeCode '$($msi.UpgradeCode)' does not match the stable Downlet UpgradeCode."
}
if ($msi.ProductCode -notmatch '^\{[0-9A-Fa-f-]{36}\}$') {
    throw "MSI ProductCode '$($msi.ProductCode)' is not a GUID."
}
if ($msi.InstallDirectoryParent -ne 'LocalAppDataFolder') {
    throw "MSI is not user-scoped: INSTALLDIR parent is '$($msi.InstallDirectoryParent)'."
}
if (@($msi.RegistryRoots).Count -eq 0 -or @($msi.RegistryRoots | Where-Object { $_ -ne 1 }).Count -ne 0) {
    throw "MSI contains non-HKCU registry roots: $($msi.RegistryRoots -join ', ')."
}
if ($msi.StartMenuShortcut -ne '1') {
    throw 'MSI does not create the required Start Menu entry.'
}
if ($msi.Platform -notmatch '^x64;') {
    throw "MSI platform '$($msi.Platform)' is not x64."
}

$portableName = "Downlet-$Version-windows-x64-portable.exe"
$msiName = "Downlet-$Version-windows-x64-user.msi"
$portableOutput = Join-Path $output $portableName
$msiOutput = Join-Path $output $msiName
Copy-Item -LiteralPath $portableSource -Destination $portableOutput -Force
Copy-Item -LiteralPath $msiSource -Destination $msiOutput -Force

$portableHash = Get-Sha256 $portableOutput
$msiHash = Get-Sha256 $msiOutput
$checksumPath = Join-Path $output 'SHA256SUMS.txt'
Write-Utf8File $checksumPath "$portableHash *$portableName`n$msiHash *$msiName`n"

$projectUrl = "https://github.com/$Repository"
$releaseUrl = "$projectUrl/releases/tag/$ReleaseTag"
$msiUrl = "$projectUrl/releases/download/$ReleaseTag/$msiName"
$licenseUrl = "$projectUrl/blob/$ReleaseTag/LICENSE"

if (-not $SkipPackageManagerMetadata) {
    $chocolateyRoot = Join-Path $output 'package-managers\chocolatey'
    $chocolateyTools = Join-Path $chocolateyRoot 'tools'
    New-Item -ItemType Directory -Path $chocolateyTools -Force | Out-Null

    $nuspec = @"
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.microsoft.com/packaging/2015/06/nuspec.xsd">
  <metadata>
    <id>downlet</id>
    <version>$Version</version>
    <title>Downlet</title>
    <authors>Downlet maintainers</authors>
    <owners>Downlet maintainers</owners>
    <projectUrl>$projectUrl</projectUrl>
    <packageSourceUrl>$projectUrl</packageSourceUrl>
    <licenseUrl>$licenseUrl</licenseUrl>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <projectSourceUrl>$projectUrl</projectSourceUrl>
    <releaseNotes>$releaseUrl</releaseNotes>
    <description>Downlet is a focused Windows desktop app for downloading one accessible YouTube video or audio item. The app provisions pinned yt-dlp and FFmpeg tools after explicit first-use consent and repairs damaged Downlet-managed copies.</description>
    <summary>Focused YouTube video and audio downloader for Windows.</summary>
    <tags>downloader youtube video audio windows desktop</tags>
  </metadata>
</package>
"@
    Write-Utf8File (Join-Path $chocolateyRoot 'downlet.nuspec') ($nuspec.Trim() + "`n")

    $chocolateyInstall = @"
`$ErrorActionPreference = 'Stop'

`$packageArgs = @{
    packageName    = `$env:ChocolateyPackageName
    fileType       = 'msi'
    url64bit       = '$msiUrl'
    checksum64     = '$msiHash'
    checksumType64 = 'sha256'
    silentArgs     = '/qn /norestart ALLUSERS=2 MSIINSTALLPERUSER=1'
    validExitCodes = @(0, 1641, 3010)
}

Install-ChocolateyPackage @packageArgs
"@
    Write-Utf8File (Join-Path $chocolateyTools 'chocolateyinstall.ps1') ($chocolateyInstall.Trim() + "`n")

    $verification = @"
VERIFICATION

The Chocolatey package downloads the signed MSI from the immutable Downlet GitHub release:
$msiUrl

SHA-256:
$msiHash  $msiName

The checksum is generated from the signed MSI during the protected GitHub release workflow.
"@
    Write-Utf8File (Join-Path $chocolateyTools 'VERIFICATION.txt') ($verification.Trim() + "`n")

    $wingetRoot = Join-Path $output 'package-managers\winget'
    New-Item -ItemType Directory -Path $wingetRoot -Force | Out-Null

    $versionManifest = @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.version.$ManifestVersion.schema.json

PackageIdentifier: Downlet.Downlet
PackageVersion: $Version
DefaultLocale: en-US
ManifestType: version
ManifestVersion: $ManifestVersion
"@
    Write-Utf8File (Join-Path $wingetRoot 'Downlet.Downlet.yaml') ($versionManifest.Trim() + "`n")

    $installerManifest = @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.installer.$ManifestVersion.schema.json

PackageIdentifier: Downlet.Downlet
PackageVersion: $Version
InstallerType: wix
Scope: user
UpgradeBehavior: install
InstallModes:
  - interactive
  - silent
  - silentWithProgress
Installers:
  - Architecture: x64
    InstallerUrl: $msiUrl
    InstallerSha256: $msiHash
    ProductCode: '$($msi.ProductCode)'
    AppsAndFeaturesEntries:
      - DisplayName: Downlet
        Publisher: Downlet
        ProductCode: '$($msi.ProductCode)'
        UpgradeCode: '$($msi.UpgradeCode)'
        InstallerType: wix
ManifestType: installer
ManifestVersion: $ManifestVersion
"@
    Write-Utf8File (Join-Path $wingetRoot 'Downlet.Downlet.installer.yaml') ($installerManifest.Trim() + "`n")

    $localeManifest = @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.defaultLocale.$ManifestVersion.schema.json

PackageIdentifier: Downlet.Downlet
PackageVersion: $Version
PackageLocale: en-US
Publisher: Downlet
PublisherUrl: $projectUrl
PublisherSupportUrl: $projectUrl/issues
PackageName: Downlet
PackageUrl: $projectUrl
License: 0BSD
LicenseUrl: $licenseUrl
ShortDescription: Focused YouTube video and audio downloader for Windows.
Description: Downlet downloads one accessible YouTube video or audio item at a time. It shows real transfer progress, supports cancellation and recovery, and provisions pinned yt-dlp and FFmpeg tools after explicit first-use consent.
Tags:
  - audio
  - downloader
  - video
  - windows
  - youtube
ReleaseNotes: Initial public release with portable and per-user MSI distributions, truthful transfer telemetry, taskbar progress, managed-tool repair, and exact saved-file reveal.
ReleaseNotesUrl: $releaseUrl
ManifestType: defaultLocale
ManifestVersion: $ManifestVersion
"@
    Write-Utf8File (Join-Path $wingetRoot 'Downlet.Downlet.locale.en-US.yaml') ($localeManifest.Trim() + "`n")
}

$metadata = [ordered]@{
    version = $Version
    releaseTag = $ReleaseTag
    repository = $Repository
    signaturesVerified = -not $SkipSignatureCheck
    portable = [ordered]@{
        fileName = $portableName
        sha256 = $portableHash
        sizeBytes = (Get-Item -LiteralPath $portableOutput).Length
        productVersion = $portableVersion
    }
    msi = [ordered]@{
        fileName = $msiName
        sha256 = $msiHash
        sizeBytes = (Get-Item -LiteralPath $msiOutput).Length
        productName = $msi.ProductName
        productVersion = $msi.ProductVersion
        manufacturer = $msi.Manufacturer
        productCode = $msi.ProductCode
        upgradeCode = $msi.UpgradeCode
        installDirectoryParent = $msi.InstallDirectoryParent
        registryRoots = @($msi.RegistryRoots)
        platform = $msi.Platform
    }
}
Write-Utf8File (Join-Path $output 'release-metadata.json') (($metadata | ConvertTo-Json -Depth 6) + "`n")

Write-Host "Prepared Downlet $Version release artifacts in $output"
