param(
    [string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug', 'assembleRelease'),
    [switch]$Offline,
    [string]$ToolsRoot = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$toolRoot = if ($ToolsRoot) { [IO.Path]::GetFullPath($ToolsRoot) } else {
    $asciiTools = [IO.Path]::GetFullPath((Join-Path $projectRoot '..\..\.jizhang-build-tools'))
    if (Test-Path -LiteralPath $asciiTools) { $asciiTools } else { [IO.Path]::GetFullPath((Join-Path $projectRoot '..\.build-tools')) }
}
$env:JIZHANG_TOOL_ROOT = $toolRoot
$env:JIZHANG_BUILD_ROOT = Join-Path $toolRoot 'outputs'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-home'
$env:ANDROID_HOME = Join-Path $toolRoot 'android-sdk'
$temporaryRoot = Join-Path $toolRoot 'tmp'
New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
# Windows Unix-domain sockets have a short path limit. Keep all Java child
# processes on the same short, task-owned temporary directory.
$mavenCache = Join-Path $toolRoot 'maven-cache'
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=`"$temporaryRoot`" -Djava.io.tmpdir=`"$temporaryRoot`" -Dmaven.repo.local=`"$mavenCache`" -Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2 $env:JAVA_TOOL_OPTIONS"
$gradleArgs = @('--no-daemon', '--console=plain', '--stacktrace')
if ($Offline) { $gradleArgs += '--offline' }
# Java does not automatically inherit the Windows proxy used by PowerShell.
# Read the active system proxy without persisting credentials or machine-specific endpoints.
$destination = [uri]'https://dl.google.com'
$proxy = [Net.WebRequest]::GetSystemWebProxy().GetProxy($destination)
if ($proxy -and $proxy.Authority -ne $destination.Authority -and $proxy.Scheme -eq 'http') {
    $gradleArgs += "-Dhttp.proxyHost=$($proxy.Host)", "-Dhttp.proxyPort=$($proxy.Port)",
                   "-Dhttps.proxyHost=$($proxy.Host)", "-Dhttps.proxyPort=$($proxy.Port)"
    # Robolectric resolves its Android runtime inside the test JVM, so the
    # proxy must also reach Java children instead of only Gradle itself.
    $env:JAVA_TOOL_OPTIONS += " -Dhttp.proxyHost=$($proxy.Host) -Dhttp.proxyPort=$($proxy.Port) -Dhttps.proxyHost=$($proxy.Host) -Dhttps.proxyPort=$($proxy.Port)"
}
Push-Location $projectRoot
try {
    $preparedGradle = Join-Path $toolRoot 'gradle-8.11.1\bin\gradle.bat'
    $gradleCommand = if (Test-Path -LiteralPath $preparedGradle) { $preparedGradle } else { Join-Path $projectRoot 'gradlew.bat' }
    & $gradleCommand @gradleArgs @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败（退出码 $LASTEXITCODE）。请查看上方第一处错误。" }
} finally { Pop-Location }
