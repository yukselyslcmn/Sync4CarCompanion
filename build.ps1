# Komut satırından hızlı derleme, birim testi ve lint kontrolü.
# Kullanım:
#   .\build.ps1            -> Debug APK derler (:app:assembleDebug)
#   .\build.ps1 test       -> Birim testlerini çalıştırır + ekran görüntülerini üretir
#   .\build.ps1 lint       -> Android Lint statik analizini çalıştırır
#   .\build.ps1 release    -> İmzalı küçültülmüş Release APK üretir (:app:assembleRelease)
#   .\build.ps1 check      -> Debug APK + Birim Testleri + Lint birlikte çalıştırır
param(
    [ValidateSet("assemble", "test", "lint", "release", "check")]
    [string]$Task = "assemble"
)

$proj = $PSScriptRoot

# 1. JDK tespiti: Önce Android Studio gömülü JBR, yoksa mevcut JAVA_HOME / kullanıcı JDK'ları
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $studioJbr = "C:\Program Files\Android\Android Studio\jbr"
    $userJdks = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
        Sort-Object Name -Descending
    if (Test-Path (Join-Path $studioJbr "bin\java.exe")) {
        $env:JAVA_HOME = $studioJbr
    } elseif ($userJdks) {
        $env:JAVA_HOME = $userJdks[0].FullName
    } else {
        throw "Uyumlu JDK (17+) bulunamadı. Android Studio kurun veya JAVA_HOME tanımlayın."
    }
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
if (-not $env:GRADLE_OPTS) {
    $env:GRADLE_OPTS = "--enable-native-access=ALL-UNNAMED"
}

# 2. Android SDK ve local.properties kontrolü (mevcut geçerli dosyayı ezmez)
$localProps = Join-Path $proj "local.properties"
if (-not (Test-Path $localProps)) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        $sdkPath = $env:ANDROID_HOME
    } elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        $sdkPath = $env:ANDROID_SDK_ROOT
    } elseif (Test-Path $defaultSdk) {
        $sdkPath = $defaultSdk
    } else {
        throw "Android SDK bulunamadı. local.properties oluşturun veya ANDROID_HOME ayarlayın."
    }
    $escapedSdk = $sdkPath.Replace("\", "\\").Replace(":", "\:")
    Set-Content -Path $localProps -Value "sdk.dir=$escapedSdk" -Encoding ASCII
}

$gradleTasks = switch ($Task) {
    "test"    { @(":app:testDebugUnitTest") }
    "lint"    { @(":app:lintDebug") }
    "release" { @(":app:assembleRelease") }
    "check"   { @(":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug") }
    default   { @(":app:assembleDebug") }
}

Push-Location $proj
try {
    & cmd.exe /c "`"$(Join-Path $proj 'gradlew.bat')`" $($gradleTasks -join ' ') --console=plain"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}
