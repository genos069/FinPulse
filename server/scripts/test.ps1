<#
.SYNOPSIS  Runs the Java tests (Windows PowerShell).
.EXAMPLE   .\scripts\test.ps1                      # unit tests only; real-database tests are reported as skipped
.EXAMPLE   .\scripts\test.ps1 -Integration -MongoUri "mongodb://localhost:27018"
.NOTES     -Integration runs the real-MongoDB tests too (IntegrationTestSupport refuses a production URI) and
           FAILS if any test was skipped, so a missing database can never look like a green build.
#>
param(
    [switch]$Integration,
    [string]$MongoUri
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if ($Integration) {
    if ($MongoUri) { $env:TEST_MONGODB_URI = $MongoUri }
    if (-not $env:TEST_MONGODB_URI) {
        throw "Set -MongoUri (or `$env:TEST_MONGODB_URI) to a throw-away MongoDB, e.g. mongodb://localhost:27018"
    }
    if ($env:MONGODB_URI -and ($env:TEST_MONGODB_URI.Trim() -eq $env:MONGODB_URI.Trim())) {
        throw "TEST_MONGODB_URI equals MONGODB_URI. Refusing to run tests against it."
    }
    if (-not $env:JWT_SECRET) { $env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 })) }
} else {
    Remove-Item Env:TEST_MONGODB_URI -ErrorAction SilentlyContinue
}

& .\mvnw.cmd clean test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$reports = Get-ChildItem "target\surefire-reports\*.txt" -ErrorAction SilentlyContinue
$totals = @{ run = 0; failures = 0; errors = 0; skipped = 0 }
foreach ($r in $reports) {
    $m = Select-String -Path $r.FullName -Pattern "Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)" | Select-Object -First 1
    if ($m) {
        $totals.run += [int]$m.Matches[0].Groups[1].Value; $totals.failures += [int]$m.Matches[0].Groups[2].Value
        $totals.errors += [int]$m.Matches[0].Groups[3].Value; $totals.skipped += [int]$m.Matches[0].Groups[4].Value
    }
}
Write-Host ("Tests run: {0}  Failures: {1}  Errors: {2}  Skipped: {3}" -f $totals.run, $totals.failures, $totals.errors, $totals.skipped)
if ($Integration -and $totals.skipped -gt 0) {
    Write-Error "$($totals.skipped) test(s) were skipped although -Integration was requested: the real-MongoDB tests did not run."
    exit 1
}
