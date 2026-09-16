param(
    [string]$Source = (Join-Path $PSScriptRoot 'TropimonUIBattle-0.1.19+1.21.1-LOCAL.jar'),
    [string]$Version = '0.1.19',
    [string]$ExpectedHash,
    [string]$ExpectedInstalledHash,
    [switch]$AllowLauncherOpen = $true,
    [switch]$AllowMissingInstalled,
    [switch]$SelfTest
)

# By FastedCorsi. External local installer only; never embedded in the mod JAR.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Test-BlockingProcess([string]$Name, [string]$CommandLine, [bool]$IgnoreLauncher = $false) {
    if ($Name -match '(?i)tropimon|minecraftlauncher|prismlauncher|multimc|atlauncher') { return -not $IgnoreLauncher }
    if ($Name -notmatch '(?i)^java(w)?\.exe$') { return $false }
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return $true }
    if ($CommandLine -match 'org\.gradle\.(launcher\.daemon\.bootstrap\.GradleDaemon|process\.internal\.worker\.GradleWorkerMain)') { return $false }
    if ($IgnoreLauncher -and $CommandLine -match 'runtime[\\/]launcher\.jar' -and
        $CommandLine -notmatch 'KnotClient|net\.minecraft\.client\.main\.Main|net\.minecraft\.launchwrapper\.Launch|cpw\.mods\.(modlauncher|bootstraplauncher)|--gameDir') { return $false }
    # Conservatively wait for any recognizable Minecraft game, even without an instance path.
    return $CommandLine -match '(?i)runtime[\\/]launcher\.jar|KnotClient|net\.minecraft\.client\.main\.Main|net\.minecraft\.launchwrapper\.Launch|cpw\.mods\.(modlauncher|bootstraplauncher)|--gameDir|\.tropimon'
}

if ($SelfTest) {
    $cases = @(
        @('java.exe', 'java -jar C:\fixture\runtime\launcher.jar', $true),
        @('javaw.exe', 'java net.fabricmc.loader.impl.launch.knot.KnotClient --gameDir C:\fixture', $true),
        @('java.exe', 'java net.minecraft.client.main.Main', $true),
        @('java.exe', '', $true),
        @('java.exe', 'java org.gradle.launcher.daemon.bootstrap.GradleDaemon C:\fixture\.tropimon\runtime\launcher.jar', $false),
        @('java.exe', 'java org.gradle.process.internal.worker.GradleWorkerMain', $false),
        @('Tropimon.exe', '', $true),
        @('MinecraftLauncher.exe', '', $true),
        @('powershell.exe', '-File InstallWhenClosed.ps1', $false)
    )
    foreach ($case in $cases) {
        if ((Test-BlockingProcess $case[0] $case[1]) -ne $case[2]) { throw 'Synthetic process detection test failed.' }
    }
    if (Test-BlockingProcess 'java.exe' 'java -jar C:\fixture\runtime\launcher.jar' $true) { throw 'Launcher override failed.' }
    if (-not (Test-BlockingProcess 'javaw.exe' 'java net.fabricmc.loader.impl.launch.knot.KnotClient --gameDir C:\fixture\.tropimon' $true)) { throw 'Override must still protect the game.' }
    if (-not (Test-BlockingProcess 'javaw.exe' '' $true)) { throw 'Unknown Java process must still block.' }
    Write-Output "Installer self-tests OK: $($cases.Count + 3) synthetic cases."
    return
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$statusPath = Join-Path $PSScriptRoot 'install-status.json'
$mutex = $null
$ownsMutex = $false
$staged = $null
$checkpoint = 'initialization'

function Write-State([string]$State, [string]$Reason) {
    $payload = [ordered]@{
        state = $State
        reason = $Reason
        version = $Version
        sha256 = $ExpectedHash
        updatedUtc = [DateTime]::UtcNow.ToString('o')
    } | ConvertTo-Json
    [IO.File]::WriteAllText($statusPath, $payload, [Text.UTF8Encoding]::new($false))
}

function Test-Closed {
    $running = @(Get-CimInstance Win32_Process | Where-Object { Test-BlockingProcess $_.Name $_.CommandLine $AllowLauncherOpen.IsPresent })
    return $running.Count -eq 0
}

function Assert-NoRedirect([string]$Path) {
    $item = Get-Item -LiteralPath $Path
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Redirected path requires review.' }
}

function Read-JarManifest([string]$Path, [switch]$Fully) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { return $null }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($Fully) {
            $buffer = New-Object byte[] 65536
            foreach ($part in $zip.Entries) {
                $stream = $part.Open()
                try { while ($stream.Read($buffer, 0, $buffer.Length) -gt 0) { } }
                finally { $stream.Dispose() }
            }
        }
        return $metadata
    } finally { $zip.Dispose() }
}

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $sha.Dispose(); $stream.Dispose() }
}

function Assert-Source([string]$Path) {
    if ((Get-Sha256 $Path) -ne $ExpectedHash) { throw 'Source integrity mismatch.' }
    $metadata = Read-JarManifest $Path -Fully
    if ($null -eq $metadata -or $metadata.id -ne 'tropimon_ui_battle' -or $metadata.version -ne $Version -or
        @($metadata.authors).Count -ne 1 -or $metadata.authors[0] -cne 'By FastedCorsi') { throw 'Unexpected mod metadata.' }
}

function Find-InstalledTarget([string]$Mods) {
    $matches = @()
    foreach ($jar in Get-ChildItem -LiteralPath $Mods -Filter '*.jar' -File) {
        Assert-NoRedirect $jar.FullName
        $metadata = Read-JarManifest $jar.FullName
        if ($null -ne $metadata -and $metadata.id -eq 'tropimon_ui_battle') { $matches += $jar.FullName }
    }
    if ($matches.Count -gt 1) { throw 'Multiple UI Battle JARs require review.' }
    if ($matches.Count -eq 1) { return $matches[0] }
    return $null
}

try {
    $checkpoint = 'argument-validation'
    if ($Version -notmatch '^[0-9A-Za-z.+_-]+$' -or $ExpectedHash -notmatch '^[0-9a-fA-F]{64}$' -or
        $ExpectedInstalledHash -notmatch '^[0-9a-fA-F]{64}$') { throw 'Expected hashes and version required.' }
    $checkpoint = 'installer-lock'
    $mutex = [Threading.Mutex]::new($false, 'Local\TropimonUIBattleDeferredInstall')
    try { $ownsMutex = $mutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $ownsMutex = $true }
    if (-not $ownsMutex) { return } # Do not overwrite the active installer's status.

    $checkpoint = 'path-validation'
    $sourcePath = (Resolve-Path -LiteralPath $Source).Path
    $instance = (Resolve-Path -LiteralPath (Join-Path $env:APPDATA '.tropimon')).Path
    $mods = (Resolve-Path -LiteralPath (Join-Path $instance 'mods')).Path
    $target = Join-Path $mods "TropimonUIBattle-$Version+1.21.1-LOCAL.jar"
    foreach ($path in @($sourcePath, $instance, $mods)) { Assert-NoRedirect $path }
    if ([IO.File]::Exists($target)) { Assert-NoRedirect $target }
    if ($sourcePath.StartsWith($mods + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Source must stay outside loaded mods.' }
    $checkpoint = 'source-validation'
    Assert-Source $sourcePath
    $checkpoint = 'target-validation'
    $installed = Find-InstalledTarget $mods
    if ($null -eq $installed -and -not $AllowMissingInstalled) {
        throw 'Installed UI Battle JAR missing.'
    }

    while ($true) {
        if (-not (Test-Closed)) {
            $reason = if ($AllowLauncherOpen) { 'game-open-or-unknown-java-process' } else { 'launcher-or-game-open' }
            Write-State 'waiting' $reason
            Start-Sleep -Seconds 5
            continue
        }
        $checkpoint = 'replacement-preflight'
        foreach ($path in @($instance, $mods)) { Assert-NoRedirect $path }
        if ([IO.File]::Exists($target)) { Assert-NoRedirect $target }
        $installed = Find-InstalledTarget $mods
        Assert-Source $sourcePath
        if ($null -eq $installed) {
            if (-not $AllowMissingInstalled) { throw 'Installed UI Battle JAR missing.' }
            $staged = Join-Path $mods ('.ui-battle-' + [Guid]::NewGuid().ToString('N') + '.tmp')
            [IO.File]::Copy($sourcePath, $staged, $false)
            Assert-Source $staged
            if (-not (Test-Closed)) {
                [IO.File]::Delete($staged)
                $staged = $null
                continue
            }
            if ($null -ne (Find-InstalledTarget $mods)) {
                [IO.File]::Delete($staged)
                $staged = $null
                continue
            }
            [IO.File]::Move($staged, $target)
            $staged = $null
            Assert-Source $target
            if ((Find-InstalledTarget $mods) -ne $target) { throw 'Installed target verification failed.' }
            Write-State 'installed' 'missing-mod-restored-and-verified'
            break
        }
        $currentHash = Get-Sha256 $installed
        if ($currentHash -eq $ExpectedHash -and $installed -eq $target) {
            Write-State 'installed' 'verified-already-current'
            break
        }
        if ($currentHash -ne $ExpectedInstalledHash) { throw 'Installed version changed after preparation.' }
        # A lock/access error stops safely, rather than forcing replacement.
        $probe = [IO.File]::Open($installed, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $probe.Dispose()

        $archiveRoot = Join-Path $instance 'mod-archive'
        [void](New-Item -ItemType Directory -Path $archiveRoot -Force)
        Assert-NoRedirect $archiveRoot
        $backupRoot = Join-Path $archiveRoot 'ui-battle'
        [void](New-Item -ItemType Directory -Path $backupRoot -Force)
        Assert-NoRedirect $backupRoot
        $backup = Join-Path $backupRoot ("before-" + [Guid]::NewGuid().ToString('N') + '.jar')
        $staged = Join-Path $mods ('.ui-battle-' + [Guid]::NewGuid().ToString('N') + '.tmp')
        [IO.File]::Copy($sourcePath, $staged, $false)
        Assert-Source $staged
        if (-not (Test-Closed)) {
            [IO.File]::Delete($staged)
            $staged = $null
            continue
        }
        $current = Find-InstalledTarget $mods
        if ($current -ne $installed -or (Get-Sha256 $installed) -ne $ExpectedInstalledHash) {
            throw 'Target changed before replacement.'
        }
        if ($installed -eq $target) {
            [IO.File]::Replace($staged, $target, $backup)
            $staged = $null
        } else {
            $oldMoved = $false
            try {
                [IO.File]::Move($installed, $backup)
                $oldMoved = $true
                [IO.File]::Move($staged, $target)
                $staged = $null
            } catch {
                if ($oldMoved -and -not [IO.File]::Exists($installed) -and
                    [IO.File]::Exists($backup) -and -not [IO.File]::Exists($target)) {
                    [IO.File]::Move($backup, $installed)
                }
                throw
            }
        }
        Assert-Source $target
        if ((Get-Sha256 $backup) -ne $ExpectedInstalledHash) { throw 'Backup verification failed.' }
        if ((Find-InstalledTarget $mods) -ne $target) { throw 'Installed target verification failed.' }
        Write-State 'installed' 'copy-and-backup-verified'
        break
    }
} catch {
    # Exception details can contain private absolute paths; never put them in a distributable report.
    $failedCommand = if ($null -eq $_.InvocationInfo.MyCommand) { 'unknown' } else { $_.InvocationInfo.MyCommand.Name }
    Write-State 'blocked' ($checkpoint + '-' + $_.Exception.GetType().Name + '-' + $failedCommand + '-line' +
        $_.InvocationInfo.ScriptLineNumber + '-needs-review')
    exit 1
} finally {
    if ($null -ne $staged -and [IO.File]::Exists($staged)) { [IO.File]::Delete($staged) }
    if ($ownsMutex) { $mutex.ReleaseMutex() }
    if ($null -ne $mutex) { $mutex.Dispose() }
}
