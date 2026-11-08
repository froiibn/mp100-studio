param(
    [string] $FfmpegDirectory = $env:MP100_FFMPEG_DIR,
    [string] $Destination
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') {
    throw 'Run this packaging script on Windows.'
}
if ([string]::IsNullOrWhiteSpace($FfmpegDirectory)) {
    $FfmpegDirectory = Join-Path $PSScriptRoot '.local\ffmpeg'
}
$ffmpegPath = (Resolve-Path -LiteralPath $FfmpegDirectory).Path
$tools = @('ffmpeg.exe', 'ffprobe.exe', 'LICENSE')
foreach ($file in $tools) {
    if (-not (Test-Path -LiteralPath (Join-Path $ffmpegPath $file) -PathType Leaf)) {
        throw "Missing $file in $ffmpegPath."
    }
}

$name = 'MP-100 Studio'
$pom = [xml](Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'pom.xml'))
$version = $pom.project.version
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $PSScriptRoot "dist\$version"
}
$destinationPath = (New-Item -ItemType Directory -Force -Path $Destination).FullName
$image = Join-Path $destinationPath $name
if (Test-Path -LiteralPath $image) {
    throw "An app image already exists at $image. Choose another -Destination to rebuild."
}

Push-Location -LiteralPath $PSScriptRoot
try {
    & mvn -B -ntp -DskipTests package dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/package-classpath.txt'
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    $jar = (Resolve-Path -LiteralPath "target/$($pom.project.artifactId)-$version.jar").Path
    $classpath = (Get-Content -Raw -LiteralPath 'target/package-classpath.txt').Trim()
    $javafxModules = @($classpath -split ';' | Where-Object { $_ -match '-win(?:-aarch64)?\.jar$' })
    if ($javafxModules.Count -ne 3) {
        throw 'Could not resolve the Windows JavaFX base, graphics and controls modules.'
    }
    $modulePath = (@($jar) + $javafxModules) -join ';'

    $payload = (New-Item -ItemType Directory -Force -Path 'target/package-input/ffmpeg').FullName
    foreach ($file in $tools) {
        Copy-Item -LiteralPath (Join-Path $ffmpegPath $file) -Destination $payload -Force
    }

    $jpackagePath = (Get-Command jpackage.exe -CommandType Application | Select-Object -First 1).Source
    $jdk = Split-Path -Parent (Split-Path -Parent $jpackagePath)
    $nativePayload = (New-Item -ItemType Directory -Force -Path 'target/package-input/audio').FullName
    $nativeLibrary = Join-Path $nativePayload 'mp100-wasapi.dll'
    $nativeArguments = @('-std=c11', '-Wall', '-Wextra', '-Werror', '-O2', '-shared',
        '-I', (Join-Path $jdk 'include'), '-I', (Join-Path $jdk 'include/win32'),
        '-o', $nativeLibrary, 'src/main/native/wasapi.c', '-lole32', '-luuid', '-lksguid')
    & gcc @nativeArguments
    if ($LASTEXITCODE -ne 0) {
        throw "WASAPI library build failed with exit code $LASTEXITCODE."
    }

    $packageArguments = @(
        '--type', 'app-image',
        '--name', $name,
        '--app-version', $version,
        '--icon', (Resolve-Path -LiteralPath 'src/main/packaging/studio-icon.ico').Path,
        '--dest', $destinationPath,
        '--input', (Split-Path -Parent $payload),
        '--module-path', $modulePath,
        '--module', 'mpstudio.player/mp100.v2.ui.Mp100App',
        '--java-options', '--enable-native-access=javafx.graphics,mpstudio.player',
        '--java-options', '-Dmp100.wasapi.dll=$APPDIR/audio/mp100-wasapi.dll',
        '--java-options', '-Dmp100.ffmpeg.dir=$APPDIR/ffmpeg'
    )
    & $jpackagePath @packageArguments
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }
    Get-Item -LiteralPath (Join-Path $image "$name.exe")
} finally {
    Pop-Location
}
