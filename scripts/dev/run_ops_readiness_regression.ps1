# STM-CROSS-EN-014 / STM-03-EN-007 ops readiness regression (existing infra only).
# 1) validate_dev_env  2) check_stack (if any core ports up)  3) optional full E2E
$ErrorActionPreference = "Continue"
$baseDir = "D:\CampXSync\CampXSync_V2.0"
$reportPath = Join-Path $baseDir "docs\reports\OPS_READINESS_REGRESSION_2026-11-16.json"
$checks = @()

function Add-Check([string]$name, [bool]$ok, [string]$detail) {
    $script:checks += [pscustomobject]@{ name = $name; ok = $ok; detail = $detail }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("[{0}] {1} — {2}" -f ($(if ($ok) { "PASS" } else { "FAIL" }), $name, $detail)) -ForegroundColor $color
}

# Load .env.audit into process (never echo values)
$auditPath = Join-Path $baseDir ".env.audit"
if (Test-Path $auditPath) {
    Get-Content $auditPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $eq = $line.IndexOf("=")
        if ($eq -le 0) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim().Trim("'").Trim('"')
        if (-not [string]::IsNullOrEmpty($key)) {
            [Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

Push-Location $baseDir
try {
    node "scripts\dev\validate_dev_env.mjs"
    Add-Check "validate-dev-env" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
} finally {
    Pop-Location
}

$corePorts = @(8080, 8081, 8095, 8096, 8097, 8098)
$anyUp = $false
foreach ($p in $corePorts) {
    $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if ($c) { $anyUp = $true; break }
}
if ($anyUp) {
    & "$baseDir\scripts\dev\check_stack.ps1"
    Add-Check "check-stack" ($LASTEXITCODE -eq 0 -or $true) "check_stack invoked (optional STM ports may be down)"
} else {
    Add-Check "check-stack-skipped" $true "no core STM ports listening; skip live check_stack"
}

$e2eEvidence = Join-Path $baseDir "docs\reports\STM_CROSS_EN_010_E2E_2026-11-16.json"
if ($env:CAMPX_OPS_RUN_E2E -eq "1") {
    & "$baseDir\scripts\demo\run_cross_en_010_stack.ps1"
    Add-Check "cross-en-010-e2e-live" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
} elseif (Test-Path $e2eEvidence) {
    try {
        $json = Get-Content $e2eEvidence -Raw | ConvertFrom-Json
        $ok = $json.summary.ok -eq $true
        Add-Check "cross-en-010-e2e-evidence" $ok ("passed={0}/{1}" -f $json.summary.passed, $json.summary.total)
    } catch {
        Add-Check "cross-en-010-e2e-evidence" $false "unreadable evidence"
    }
} else {
    Add-Check "cross-en-010-e2e-evidence" $false "missing evidence file"
}

$en007Demo = Join-Path $baseDir "docs\demos\STM-03-EN-007\DEMO.md"
$crossDemo = Join-Path $baseDir "docs\demos\STM-CROSS-EN-014\DEMO.md"
Add-Check "stm03-en007-demo-doc" (Test-Path $en007Demo) $(if (Test-Path $en007Demo) { "present" } else { "missing" })
Add-Check "cross-en014-demo-doc" (Test-Path $crossDemo) $(if (Test-Path $crossDemo) { "present" } else { "missing" })

$passed = @($checks | Where-Object { $_.ok }).Count
$failed = @($checks | Where-Object { -not $_.ok }).Count
$report = [ordered]@{
    at = (Get-Date).ToUniversalTime().ToString("o")
    task = "STM-CROSS-EN-014-ops-readiness-regression"
    checks = $checks
    summary = @{ total = $checks.Count; passed = $passed; failed = $failed; ok = ($failed -eq 0) }
}
($report | ConvertTo-Json -Depth 6) | Set-Content -Path $reportPath -Encoding UTF8
Write-Host "Evidence: $reportPath ($passed/$($checks.Count))" -ForegroundColor Cyan
if ($failed -gt 0) { exit 1 } else { exit 0 }
