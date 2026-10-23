# Release pipeline. Each run adds 0.0.1 to versionName, adds 1 to versionCode,
# then builds a minified APK named with that version.
# A failed build puts the previous version back.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$versionFile = Join-Path $PSScriptRoot "app\version.properties"
if (-not (Test-Path $versionFile)) {
    throw "Missing $versionFile"
}

$original = [System.IO.File]::ReadAllText($versionFile)
$props = @{}
foreach ($line in ($original -split "`r?`n")) {
    if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
    $pair = $line.Split('=', 2)
    if ($pair.Count -eq 2) { $props[$pair[0].Trim()] = $pair[1].Trim() }
}

$name = $props['versionName']
$codeText = $props['versionCode']
if (-not $name -or -not $codeText) {
    throw "version.properties needs versionName and versionCode"
}

$parts = @($name.Split('.'))
if ($parts.Count -eq 2) { $parts += '0' }
if ($parts.Count -ne 3 -or $parts[0] -notmatch '^\d+$' -or $parts[1] -notmatch '^\d+$' -or $parts[2] -notmatch '^\d+$') {
    throw "versionName must look like 1.0.0 (was $name)"
}
$newName = "$($parts[0]).$($parts[1]).$([int]$parts[2] + 1)"
$newCode = [int]$codeText + 1

$updated = "# Read by the app module. release.ps1 increments versionName by 0.0.1 and versionCode by 1.`r`nversionName=$newName`r`nversionCode=$newCode`r`n"
[System.IO.File]::WriteAllText($versionFile, $updated)

Write-Host "Building Podcast $newName (versionCode $newCode)"
& .\gradlew.bat :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) {
    [System.IO.File]::WriteAllText($versionFile, $original)
    throw "Release build failed. Version restored to $name."
}

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) {
    [System.IO.File]::WriteAllText($versionFile, $original)
    throw "Build reported success but $apk is missing. Version restored to $name."
}

$destDir = Join-Path $PSScriptRoot "releases"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$dest = Join-Path $destDir "Podcast-$newName.apk"
Copy-Item $apk $dest -Force
Write-Host "Release ready: $dest"
