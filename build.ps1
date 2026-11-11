# Sestaví CardStudy.apk. Nástroje (JDK 17, Android SDK, Gradle) jsou v %USERPROFILE%\android-dev.
# Build běží v kopii mimo synchronizovanou složku, aby se nesynchronizovaly stovky MB mezisouborů.
$ErrorActionPreference = "Stop"
$tools = "$env:USERPROFILE\android-dev"
$src = $PSScriptRoot
$work = "$tools\build\CardStudy"

$env:JAVA_HOME = (Get-ChildItem -Directory "$tools\jdk-17*")[0].FullName
$env:ANDROID_HOME = "$tools\sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$gradle = (Get-ChildItem -Directory "$tools\gradle-*")[0].FullName + "\bin\gradle.bat"

robocopy $src $work /MIR /XD build .gradle .idea /XF *.apk local.properties /NFL /NDL /NJH /NJS | Out-Null
Push-Location $work
try {
    & $gradle --no-daemon --console=plain assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Gradle build selhal" }
} finally { Pop-Location }

Copy-Item "$work\app\build\outputs\apk\release\app-release.apk" "$src\CardStudy.apk" -Force
Write-Host "Hotovo: $src\CardStudy.apk"
