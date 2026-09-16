param([switch]$Apply)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -eq 'Core') {
    $legacyArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $PSCommandPath)
    if ($Apply) { $legacyArguments += '-Apply' }
    & powershell.exe @legacyArguments
    if ($LASTEXITCODE -ne 0) { throw "Icon normalization failed with exit code $LASTEXITCODE" }
    return
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$artRoot = Join-Path $projectRoot 'art/effects-v2'
$manifest = Get-Content -LiteralPath (Join-Path $artRoot 'generation-manifest.json') -Raw | ConvertFrom-Json
$generationRoot = Join-Path $artRoot $manifest.sourceDirectory
$sourceOutput = Join-Path $artRoot 'sources'
$iconOutput = Join-Path $artRoot 'icons'
$resourceOutput = Join-Path $projectRoot 'src/main/resources/assets/tropimon_ui_battle/textures/gui/effects'
$backupOutput = Join-Path $artRoot 'previous-icons'

Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class EffectSpriteNormalizer {
    public static void Normalize(string sourcePath, string targetPath) {
        using (var source = new Bitmap(sourcePath)) {
            if (!Image.IsAlphaPixelFormat(source.PixelFormat))
                throw new InvalidOperationException("Source has no alpha: " + sourcePath);
            if (source.GetPixel(0, 0).A > 1 || source.GetPixel(source.Width - 1, 0).A > 1 ||
                source.GetPixel(0, source.Height - 1).A > 1 ||
                source.GetPixel(source.Width - 1, source.Height - 1).A > 1)
                throw new InvalidOperationException("Opaque background: " + sourcePath);

            int minX = source.Width, minY = source.Height, maxX = -1, maxY = -1;
            for (int y = 0; y < source.Height; y++) {
                for (int x = 0; x < source.Width; x++) {
                    // Ignore imperceptible export fringe for framing only. Alpha is not flattened.
                    if (source.GetPixel(x, y).A <= 8) continue;
                    minX = Math.Min(minX, x); minY = Math.Min(minY, y);
                    maxX = Math.Max(maxX, x); maxY = Math.Max(maxY, y);
                }
            }
            if (maxX < minX) throw new InvalidOperationException("Empty icon: " + sourcePath);
            int width = maxX - minX + 1, height = maxY - minY + 1;
            double scale = 108.0 / Math.Max(width, height);
            int outWidth = Math.Max(1, (int)Math.Round(width * scale));
            int outHeight = Math.Max(1, (int)Math.Round(height * scale));
            using (var target = new Bitmap(128, 128, PixelFormat.Format32bppArgb)) {
                using (var graphics = Graphics.FromImage(target)) {
                    graphics.Clear(Color.Transparent);
                    graphics.CompositingMode = CompositingMode.SourceCopy;
                    graphics.InterpolationMode = InterpolationMode.NearestNeighbor;
                    graphics.PixelOffsetMode = PixelOffsetMode.Half;
                    graphics.DrawImage(source,
                        new Rectangle((128 - outWidth) / 2, (128 - outHeight) / 2, outWidth, outHeight),
                        new Rectangle(minX, minY, width, height), GraphicsUnit.Pixel);
                }
                target.Save(targetPath, ImageFormat.Png);
            }
        }
    }

    public static void Preview(string[] names, string iconDirectory, string outputPath) {
        const int columns = 5, cellWidth = 260, cellHeight = 194;
        int rows = (names.Length + columns - 1) / columns;
        using (var output = new Bitmap(columns * cellWidth, rows * cellHeight + 52, PixelFormat.Format32bppArgb))
        using (var graphics = Graphics.FromImage(output))
        using (var titleFont = new Font("Segoe UI", 18, FontStyle.Bold))
        using (var labelFont = new Font("Segoe UI", 11, FontStyle.Bold))
        using (var smallFont = new Font("Segoe UI", 9))
        using (var dark = new SolidBrush(Color.FromArgb(29, 38, 45)))
        using (var light = new SolidBrush(Color.FromArgb(226, 233, 235)))
        using (var white = new SolidBrush(Color.FromArgb(233, 241, 242)))
        using (var dim = new SolidBrush(Color.FromArgb(151, 170, 175))) {
            graphics.Clear(Color.FromArgb(16, 22, 28));
            graphics.DrawString("Tropimon UI Battle - effets v2 / PNG transparents", titleFont, white, 18, 9);
            graphics.InterpolationMode = InterpolationMode.NearestNeighbor;
            graphics.PixelOffsetMode = PixelOffsetMode.Half;
            for (int i = 0; i < names.Length; i++) {
                int x = (i % columns) * cellWidth, y = 52 + (i / columns) * cellHeight;
                graphics.DrawString(names[i], labelFont, white, x + 12, y + 5);
                graphics.FillRectangle(dark, x + 10, y + 32, 116, 116);
                graphics.FillRectangle(light, x + 134, y + 32, 116, 116);
                using (var icon = new Bitmap(System.IO.Path.Combine(iconDirectory, names[i] + ".png"))) {
                    graphics.DrawImage(icon, new Rectangle(x + 14, y + 36, 108, 108));
                    graphics.DrawImage(icon, new Rectangle(x + 138, y + 36, 108, 108));
                    graphics.DrawImage(icon, new Rectangle(x + 13, y + 158, 24, 24));
                    graphics.DrawImage(icon, new Rectangle(x + 98, y + 154, 32, 32));
                }
                graphics.DrawString("24 px", smallFont, dim, x + 43, y + 163);
                graphics.DrawString("32 px", smallFont, dim, x + 137, y + 163);
            }
            output.Save(outputPath, ImageFormat.Png);
        }
    }
}
'@

New-Item -ItemType Directory -Path $sourceOutput, $iconOutput -Force | Out-Null
$names = @($manifest.icons.PSObject.Properties.Name | Sort-Object)
if ($names.Count -ne 30) { throw 'The manifest must contain exactly 30 effects.' }

foreach ($name in $names) {
    $generatedPath = Join-Path $generationRoot $manifest.icons.$name
    if (-not (Test-Path -LiteralPath $generatedPath -PathType Leaf)) { throw "Missing source for effect: $name" }
    $sourcePath = Join-Path $sourceOutput "$name.png"
    $iconPath = Join-Path $iconOutput "$name.png"
    if ([IO.Path]::GetFullPath($generatedPath) -ne [IO.Path]::GetFullPath($sourcePath)) {
        Copy-Item -LiteralPath $generatedPath -Destination $sourcePath -Force
    }
    [EffectSpriteNormalizer]::Normalize($sourcePath, $iconPath)
}

[EffectSpriteNormalizer]::Preview([string[]]$names, $iconOutput, (Join-Path $artRoot 'preview.png'))

if ($Apply) {
    New-Item -ItemType Directory -Path $backupOutput -Force | Out-Null
    foreach ($name in $names) {
        $existing = Join-Path $resourceOutput "$name.png"
        $backup = Join-Path $backupOutput "$name.png"
        if ((Test-Path -LiteralPath $existing) -and -not (Test-Path -LiteralPath $backup)) {
            Copy-Item -LiteralPath $existing -Destination $backup
        }
        Copy-Item -LiteralPath (Join-Path $iconOutput "$name.png") -Destination $existing -Force
    }
}

Write-Output "Prepared $($names.Count) transparent 128x128 effects in $iconOutput"
Write-Output "Preview: $(Join-Path $artRoot 'preview.png')"
Write-Output "Applied to mod resources: $Apply"
