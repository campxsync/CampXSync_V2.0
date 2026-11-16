# STM-CROSS-EN-010 stack runner — starts IAM + gateway + STM-03..06, runs E2E harness, stops ports.
# Existing infrastructure only. Does not print secrets.
$ErrorActionPreference = "Continue"
$baseDir = "D:\CampXSync\CampXSync_V2.0"
$logDir = "$baseDir\docs\latency-audit\logs\cross-en-010"
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

# Load .env.audit into process env (values never echoed)
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

$env:CAMPX_PERSISTENCE_MODE = "postgres"
$env:CAMPX_INTERNAL_AUTH_ENABLED = "true"
$env:CAMPX_IAM_DELEGATION_BASE_URL = "http://127.0.0.1:8081"
$env:Path = "C:\Java\jdk\bin;C:\Users\niran\Downloads\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin;" + $env:Path

function Stop-Port([int]$port) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

function Wait-Listen([int]$port, [int]$timeoutSec = 60) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($c) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Start-JavaService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$WorkDir,
        [string]$MainClass,
        [string]$ClasspathFile,
        [string]$Xmx = "96m"
    )
    Write-Host "Starting $Name on $Port ..." -ForegroundColor Cyan
    Stop-Port $Port
    Start-Sleep -Milliseconds 400
    if (-not (Test-Path $ClasspathFile)) {
        Write-Host "FAIL: missing classpath $ClasspathFile" -ForegroundColor Red
        return $false
    }
    $cp = (Get-Content $ClasspathFile -Raw).Trim()
    $fullCp = "target\classes;$cp"
    $out = Join-Path $logDir "$Name.out.log"
    $err = Join-Path $logDir "$Name.err.log"
    $args = @(
        "-Xms32m", "-Xmx$Xmx",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-Dcampx.iam.delegation.baseUrl=http://127.0.0.1:8081",
        "-cp", $fullCp,
        $MainClass, "$Port"
    )
    $proc = Start-Process -FilePath "java" -ArgumentList $args -WorkingDirectory $WorkDir `
        -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Minimized -PassThru
    Write-Host "  PID $($proc.Id)" -ForegroundColor DarkGray
    if (-not (Wait-Listen $Port 70)) {
        Write-Host "FAIL: $Name did not listen on $Port" -ForegroundColor Red
        if (Test-Path $err) { Get-Content $err -Tail 30 | ForEach-Object { Write-Host $_ -ForegroundColor Yellow } }
        return $false
    }
    Write-Host "  LISTEN OK" -ForegroundColor Green
    return $true
}

$ports = @(8080, 8081, 8095, 8096, 8097, 8098)
# Stop only CROSS-EN-010 ports (leave admissions 8094 alone if unused)
foreach ($p in $ports) { Stop-Port $p }
# Also free document if orphaned from prior stack
Stop-Port 8098
Start-Sleep -Seconds 1

$free = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 2)
Write-Host "Free RAM before start: $free GB" -ForegroundColor White

$ok = Start-JavaService -Name "institute-admin" -Port 8081 `
    -WorkDir "$baseDir\services\administration-services\institute-admin-service" `
    -MainClass "com.campx.admin.institute.InstituteAdminApplication" `
    -ClasspathFile "$baseDir\services\administration-services\institute-admin-service\classpath.txt" `
    -Xmx "128m"
if (-not $ok) { throw "institute-admin failed to start" }

$ok = Start-JavaService -Name "api-gateway" -Port 8080 `
    -WorkDir "$baseDir\api-gateway" `
    -MainClass "com.campx.gateway.ApiGatewayApplication" `
    -ClasspathFile "$baseDir\api-gateway\classpath.txt" `
    -Xmx "192m"
if (-not $ok) { throw "api-gateway failed to start" }

$env:CAMPX_IAM_DELEGATION_BASE_URL = "http://127.0.0.1:8081"

$stm = @(
    @{ Name="enrollment"; Port=8095; Dir="$baseDir\services\student-services\enrollment-service"; Main="com.campx.enrollment.EnrollmentApplication"; Cp="$baseDir\services\student-services\enrollment-service\classpath.txt" },
    @{ Name="lifecycle"; Port=8096; Dir="$baseDir\services\student-services\lifecycle-service"; Main="com.campx.lifecycle.LifecycleApplication"; Cp="$baseDir\services\student-services\lifecycle-service\classpath.txt" },
    @{ Name="guardian"; Port=8097; Dir="$baseDir\services\student-services\guardian-service"; Main="com.campx.guardian.GuardianApplication"; Cp="$baseDir\services\student-services\guardian-service\classpath.txt" },
    @{ Name="document"; Port=8098; Dir="$baseDir\services\student-services\document-service"; Main="com.campx.document.DocumentApplication"; Cp="$baseDir\services\student-services\document-service\classpath.txt" }
)

foreach ($s in $stm) {
    $ok = Start-JavaService -Name $s.Name -Port $s.Port -WorkDir $s.Dir -MainClass $s.Main -ClasspathFile $s.Cp -Xmx "96m"
    if (-not $ok) { Write-Host "WARN: $($s.Name) failed" -ForegroundColor Yellow }
    Start-Sleep -Seconds 1
}

Write-Host "Running STM-CROSS-EN-010 E2E harness..." -ForegroundColor Cyan
Push-Location $baseDir
try {
    node "scripts\demo\run_stm_cross_en_010_e2e.mjs"
    $e2eExit = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host "Stopping CROSS-EN-010 services..." -ForegroundColor Yellow
foreach ($p in $ports) { Stop-Port $p }
Start-Sleep -Seconds 1
$freeAfter = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 2)
Write-Host "Free RAM after stop: $freeAfter GB" -ForegroundColor White
Write-Host "E2E exit code: $e2eExit" -ForegroundColor $(if ($e2eExit -eq 0) { "Green" } else { "Red" })
exit $e2eExit
