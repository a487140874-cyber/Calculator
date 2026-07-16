$ErrorActionPreference = "Stop"

$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$OutputDir = Join-Path $ProjectDir "dist\windows-x64"
$InputDir = Join-Path $ProjectDir "target\jpackage-input"

if (-not [Environment]::Is64BitOperatingSystem) {
    throw "This script must run on 64-bit Windows."
}
if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to a Windows x64 JDK 23 installation."
}
$PackageJavaHome = $env:JAVA_HOME
$BuildJavaHome = if ($env:BUILD_JAVA_HOME) { $env:BUILD_JAVA_HOME } else { $PackageJavaHome }

Remove-Item $InputDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $OutputDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item $InputDir -ItemType Directory -Force | Out-Null
New-Item $OutputDir -ItemType Directory -Force | Out-Null

Push-Location $ProjectDir
try {
    $env:JAVA_HOME = $BuildJavaHome
    .\mvnw.cmd -q package -DskipTests
    Copy-Item "target\calculator-executable.jar" (Join-Path $InputDir "Calculator.jar")

    & "$PackageJavaHome\bin\jpackage.exe" `
        --type exe `
        --name Calculator `
        --app-version 1.0.0 `
        --vendor Calculator `
        --description "Geological key-layer, collapse, energy and 3D visualization calculator" `
        --input $InputDir `
        --main-jar Calculator.jar `
        --main-class ui.Launcher `
        --dest $OutputDir `
        --win-dir-chooser `
        --win-menu `
        --win-menu-group Calculator `
        --win-shortcut `
        --java-options "-Dfile.encoding=UTF-8"
}
finally {
    $env:JAVA_HOME = $PackageJavaHome
    Pop-Location
}

Write-Host "Created Windows installer in: $OutputDir"
