param([Parameter(Mandatory=$true)][string]$JavaHome)
$ErrorActionPreference = 'Stop'
$taskJava = Join-Path $JavaHome 'bin\java.exe'
if (!(Test-Path -LiteralPath $taskJava)) { throw '找不到指定 JDK 的 java.exe' }
$versionText = (& $taskJava -version 2>&1 | Out-String)
if ($versionText -notmatch 'version "(?:17|21)\.') { throw '請使用 JDK 17 或 21' }
$previousJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    & "$PSScriptRoot\gradlew.bat" -p $PSScriptRoot --gradle-user-home "$PSScriptRoot\work\gradle-home" --no-daemon assembleDebug testDebugUnitTest lintDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle 建置失敗：$LASTEXITCODE" }
} finally { $env:JAVA_HOME = $previousJava }
