# Rule 11 environment gate. Prints only True/False per check, never values.
# Exit code 0 = all checks pass, 1 = at least one failed.
# Usage:  .\scripts\rule11-check.ps1; if ($LASTEXITCODE -eq 0) { mvn test ... }

$prodRef          = 'ekspahbsbwdpraaqfqui'
$legacyStagingRef = 'kpwdvvcschankgggitnj'
$authorizedDevRef = 'azulbaxszsqxymqmlwfe'

function Get-Env([string]$name) { [System.Environment]::GetEnvironmentVariable($name) }

$user    = Get-Env 'SUPABASE_USER'
$issuer  = Get-Env 'CAMPX_SUPABASE_JWT_ISSUER'
$jdbc    = Get-Env 'SUPABASE_JDBC_URL'
$testEnv = Get-Env 'CAMPX_TEST_ENV'

$userOk   = [bool]$user   -and $user.Contains($authorizedDevRef)   -and -not $user.Contains($prodRef) -and -not $user.Contains($legacyStagingRef)
$issuerOk = [bool]$issuer -and $issuer.Contains($authorizedDevRef) -and -not $issuer.Contains($prodRef) -and -not $issuer.Contains($legacyStagingRef)
$jdbcOk   = [bool]$jdbc   -and $jdbc.Contains('pooler.supabase.com') -and -not $jdbc.Contains($prodRef) -and -not $jdbc.Contains($legacyStagingRef)
$envOk    = ($testEnv -eq 'development' -or $testEnv -eq 'staging')

Write-Output "SUPABASE_USER_ok: $userOk"
Write-Output "CAMPX_SUPABASE_JWT_ISSUER_ok: $issuerOk"
Write-Output "SUPABASE_JDBC_URL_ok: $jdbcOk"
Write-Output "CAMPX_TEST_ENV_ok: $envOk"

$all = $userOk -and $issuerOk -and $jdbcOk -and $envOk
Write-Output "Rule11_overall: $all"
if ($all) { exit 0 } else { exit 1 }
