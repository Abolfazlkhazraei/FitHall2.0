# Starts the FitHall server for local development (Windows PowerShell):  .\server\run-dev.ps1
# The Android emulator reaches it at http://10.0.2.2:8080, which is the app's default.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if (-not $env:ANTHROPIC_API_KEY) {
    # Hidden input: the key isn't echoed, stored, or logged.
    $secure = Read-Host "Anthropic API key (input hidden)" -AsSecureString
    $env:ANTHROPIC_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $env:ANTHROPIC_API_KEY) { throw "No API key entered." }

# Keep the token secret between runs so the app stays signed in (file is git-ignored).
$secretFile = Join-Path $PSScriptRoot ".dev-token-secret"
if (-not (Test-Path $secretFile)) {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    [Convert]::ToBase64String($bytes) | Set-Content -NoNewline -Encoding ascii $secretFile
}
$env:TOKEN_SECRET = (Get-Content -Raw $secretFile).Trim()
if (-not $env:INTEGRITY_MODE) { $env:INTEGRITY_MODE = "off" }

Write-Host "Starting FitHall server on http://localhost:8080 (emulator: http://10.0.2.2:8080). Ctrl+C to stop."
Set-Location $root
& .\gradlew.bat :server:run
