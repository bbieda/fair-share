# Builds FairShare (debug), installs it on an Android phone connected by USB and starts it.
#
#   scripts\uruchom-na-telefonie.bat          double-click: build + install + start
#   scripts\zapisz-log.bat                    after a test: saves the app's log from the phone to phone-log.txt
#   powershell -File scripts\uruchom-na-telefonie.ps1 -NoBuild    install the last build again
#   powershell -File scripts\uruchom-na-telefonie.ps1 -Clean      clean build
#
# Needs a JDK 17+ (java on PATH or JAVA_HOME) and the Android SDK (Android Studio installs both).
# Everything the script prints also goes to run-phone.log in the project folder.
param(
    [switch]$NoBuild,
    [switch]$Clean,
    [switch]$SaveLog
)
# adb and Gradle write normal progress to stderr; with "Stop" Windows PowerShell 5.1 would treat it as an error
$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$log = Join-Path $root "run-phone.log"
if (-not $SaveLog) { "=== $(Get-Date -Format s) ===" | Out-File $log -Encoding utf8 }

function Say([string]$text) {
    Write-Host $text
    if (-not $SaveLog) { $text | Out-File $log -Append -Encoding utf8 }
}
function Fail([string]$text) {
    Say ""
    Say "BLAD: $text"
    exit 1
}

# ---- Android SDK: local.properties, then ANDROID_HOME, then the usual folders ----
$props = Join-Path $root "local.properties"
$sdkFromProps = $null
if (Test-Path $props) {
    $line = Get-Content $props | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
    if ($line) { $sdkFromProps = (($line -replace '^\s*sdk\.dir\s*=\s*', '') -replace '\\:', ':') -replace '\\\\', '\' }
}
$sdk = @($sdkFromProps, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "D:\Android\sdk", "$env:LOCALAPPDATA\Android\Sdk") |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "platform-tools\adb.exe")) } | Select-Object -First 1
if (-not $sdk) {
    Fail "Nie znalazlem Android SDK (platform-tools\adb.exe). Zainstaluj Android Studio albo ustaw ANDROID_HOME."
}
$env:ANDROID_HOME = $sdk
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not $sdkFromProps) {
    # Gradle needs sdk.dir; local.properties is not in git (it is per computer)
    $escaped = ($sdk -replace '\\', '\\') -replace ':', '\:'
    $existing = if (Test-Path $props) { [IO.File]::ReadAllText($props) } else { "" }
    $newline = if ($existing -and -not $existing.EndsWith("`n")) { "`r`n" } else { "" }
    [IO.File]::AppendAllText($props, "$newline" + "sdk.dir=$escaped`r`n")
}
# Gradle cache next to the SDK when it was set up that way (keeps C: free)
if (-not $env:GRADLE_USER_HOME -and (Test-Path "D:\gradle\home")) { $env:GRADLE_USER_HOME = "D:\gradle\home" }

# ---- Phone ----
function Get-Phone {
    & $adb start-server *> $null
    while ($true) {
        $lines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' -and $_ -notmatch '^emulator-' })
        $ready = @($lines | Where-Object { $_ -match "`tdevice$" })
        if ($ready.Count -gt 0) { return ($ready[0] -split "`t")[0] }
        if ($lines | Where-Object { $_ -match "`tunauthorized$" }) {
            Say "Telefon jest podlaczony, ale nie zezwolil na debugowanie: odblokuj go i zaakceptuj okno 'Zezwolic na debugowanie USB?'."
        } else {
            Say "Nie widze telefonu. Podlacz go kablem USB i wlacz debugowanie USB (opis w docs\skaner.md)."
        }
        Read-Host "Nacisnij Enter, aby sprawdzic jeszcze raz (Ctrl+C konczy)" | Out-Null
    }
}

if ($SaveLog) {
    $serial = Get-Phone
    $out = Join-Path $root "phone-log.txt"
    # lines of the scanner, crashes and ML Kit since the app was last started by this script
    $pattern = 'FairShareScan|AndroidRuntime|FATAL|com\.fairshare|MlKit|mlkit|TextRecognizer|Vision'
    & $adb -s $serial logcat -d -v threadtime 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -match $pattern } |
        Out-File $out -Encoding utf8
    Write-Host "Zapisano log telefonu: $out ($((Get-Content $out).Count) linii)."
    exit 0
}

# ---- Build ----
$geminiInProps = (Test-Path $props) -and ((Get-Content $props) -match '^\s*gemini\.apiKey\s*=\s*\S')
if ($geminiInProps) { Say "Klucz Gemini: z local.properties (gemini.apiKey). Skaner uzyje AI, gdy suma sie nie zgadza." }
elseif ($env:GEMINI_API_KEY) { Say "Klucz Gemini: ze zmiennej GEMINI_API_KEY. Skaner uzyje AI, gdy suma sie nie zgadza." }
else { Say "Brak klucza Gemini: skaner zadziala tylko z ML Kit + regulami (opis w docs\skaner.md)." }

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not $NoBuild) {
    Say "Buduje aplikacje. Za pierwszym razem trwa to kilka minut, bo Gradle pobiera biblioteki..."
    $tasks = @("assembleDebug", "--console=plain")
    if ($Clean) { $tasks = @("clean") + $tasks }
    & .\gradlew.bat @tasks 2>&1 | ForEach-Object { Say "$_" }
    if ($LASTEXITCODE -ne 0) { Fail "Budowanie nie powiodlo sie. Caly przebieg jest w run-phone.log." }
}
if (-not (Test-Path $apk)) { Fail "Brak $apk. Uruchom bez -NoBuild." }

# ---- Install + start ----
$serial = Get-Phone
$model = (& $adb -s $serial shell getprop ro.product.model 2>$null | Out-String).Trim()
Say "Instaluje na telefonie $model ($serial)..."
function Install-Apk { @(& $adb -s $serial install -r $apk 2>&1 | ForEach-Object { "$_" }) }
$result = Install-Apk
$result | ForEach-Object { Say $_ }
if ($result -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match") {
    $answer = Read-Host "Na telefonie jest FairShare podpisany innym kluczem. Odinstalowac go (usunie dane tej aplikacji) i zainstalowac nowy? [t/N]"
    if ($answer -match '^[tTyY]') {
        & $adb -s $serial uninstall com.fairshare | Out-Null
        $result = Install-Apk
        $result | ForEach-Object { Say $_ }
    }
}
if ($result -match "INSTALL_FAILED_USER_RESTRICTED") {
    Fail "Telefon zablokowal instalacje. Na Xiaomi/Redmi/POCO wlacz w Opcjach programisty 'Instaluj przez USB' i potwierdz instalacje na ekranie telefonu."
}
if (-not ($result -match "^Success")) { Fail "Instalacja nie powiodla sie (komunikat adb wyzej)." }

& $adb -s $serial logcat -c 2>$null
& $adb -s $serial shell am start -n com.fairshare/.MainActivity 2>&1 | Out-Null
Say ""
Say "Gotowe: FairShare dziala na telefonie."
Say "Skaner: przycisk '+' (Dodaj wydatek) -> wiersz 'Paragon: Zeskanuj paragon' -> 'Zrob zdjecie' albo 'Wybierz z galerii'."
Say "Po tescie uruchom scripts\zapisz-log.bat: zapisze odczyty i ewentualne bledy z telefonu do phone-log.txt."
