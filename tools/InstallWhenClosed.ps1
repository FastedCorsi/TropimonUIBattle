param(
    [string]$LauncherRoot = $(if ($env:TROPIMON_HOME) { $env:TROPIMON_HOME } else { Join-Path $env:APPDATA '.tropimon' }),
    [int]$PollSeconds = 5,
    [switch]$CheckOnly
)
# By FastedCorsi. Standalone local delivery entry point.
$ErrorActionPreference = 'Stop'
$sources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'TropimonUIBattle-*-LOCAL.jar' -File)
if ($sources.Count -ne 1) { throw 'Expected exactly one local UI Battle JAR.' }
& (Join-Path $PSScriptRoot 'InstallManagedLocalMod.ps1') -SourceJar $sources[0].FullName -ExpectedModId 'tropimon_ui_battle' -LauncherRoot $LauncherRoot -PollSeconds $PollSeconds -CheckOnly:$CheckOnly
exit $LASTEXITCODE
