<#
.SYNOPSIS  Runs the Python tests (Windows PowerShell).
.EXAMPLE   .\scripts\test.ps1                      # everything that needs no database
.EXAMPLE   .\scripts\test.ps1 -Fast                # skips the ~1 minute OCR benchmark
.EXAMPLE   .\scripts\test.ps1 -Integration -MongoUri "mongodb://localhost:27018"
.NOTES     Needs: pip install -r requirements-dev.txt, and the Tesseract OCR program on PATH for the OCR tests.
#>
param(
    [switch]$Integration,
    [switch]$Fast,
    [string]$MongoUri
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if ($Integration) {
    if ($MongoUri) { $env:TEST_MONGODB_URI = $MongoUri }
    if (-not $env:TEST_MONGODB_URI) { throw "Set -MongoUri (or `$env:TEST_MONGODB_URI) to a throw-away MongoDB." }
    if ($env:MONGODB_URI -and ($env:TEST_MONGODB_URI.Trim() -eq $env:MONGODB_URI.Trim())) {
        throw "TEST_MONGODB_URI equals MONGODB_URI. Refusing to run tests against it."
    }
} else {
    Remove-Item Env:TEST_MONGODB_URI -ErrorAction SilentlyContinue
}

$pytestArgs = @("-m", "pytest", "tests", "-q", "-rs")
if ($Fast) { $pytestArgs += @("-m", "not slow") }
& python @pytestArgs
exit $LASTEXITCODE
