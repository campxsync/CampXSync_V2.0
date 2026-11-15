# Sequential Path C stack smoke for STM-02..06 (minimal memory).
# Starts IAM + gateway + five STM services, runs smoke_path_c_http.mjs, then stops ports.
$ErrorActionPreference = "Continue"
$baseDir = "D:\CampXSync\CampXSync_V2.0"
$logDir = "$baseDir\docs\latency-audit\logs\path-c-smoke"
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

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

function Wait-Listen([int]$port, [int]$timeoutSec = 45) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($c) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-Http([string]$url, [int]$timeoutSec = 45) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
        } catch {
            if ($_.Exception.Response) {
                $code = [int]$_.Exception.Response.StatusCode
                if ($code -ge 200 -and $code -lt 500) { return $true }
            }
        }
        Start-Sleep -Milliseconds 700
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
        [string]$Xmx = "96m",
        [string[]]$ExtraEnv = @()
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
    if (-not (Wait-Listen $Port 60)) {
        Write-Host "FAIL: $Name did not listen on $Port" -ForegroundColor Red
        if (Test-Path $err) { Get-Content $err -Tail 40 | ForEach-Object { Write-Host $_ -ForegroundColor Yellow } }
        return $false
    }
    Write-Host "  LISTEN OK" -ForegroundColor Green
    return $true
}

$startup = @()
$ports = @(8080, 8081, 8094, 8095, 8096, 8097, 8098)
foreach ($p in $ports) { Stop-Port $p }
Start-Sleep -Seconds 1

$free = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 2)
Write-Host "Free RAM before start: $free GB" -ForegroundColor White
$startup += [pscustomobject]@{ step = "ram-before"; ok = ($free -ge 3.0); detail = "$free GB" }

# 1) Institute Admin (IAM Path C)
$ok = Start-JavaService -Name "institute-admin" -Port 8081 `
    -WorkDir "$baseDir\services\administration-services\institute-admin-service" `
    -MainClass "com.campx.admin.institute.InstituteAdminApplication" `
    -ClasspathFile "$baseDir\services\administration-services\institute-admin-service\classpath.txt" `
    -Xmx "128m"
$startup += [pscustomobject]@{ step = "start-institute-admin"; ok = $ok; detail = "port 8081" }
if (-not $ok) { throw "institute-admin failed to start" }

# 2) API Gateway
$ok = Start-JavaService -Name "api-gateway" -Port 8080 `
    -WorkDir "$baseDir\api-gateway" `
    -MainClass "com.campx.gateway.ApiGatewayApplication" `
    -ClasspathFile "$baseDir\api-gateway\classpath.txt" `
    -Xmx "192m"
$startup += [pscustomobject]@{ step = "start-api-gateway"; ok = $ok; detail = "port 8080" }

# 3-7) STM services with Path C client env
$env:CAMPX_IAM_DELEGATION_BASE_URL = "http://127.0.0.1:8081"

$stm = @(
    @{ Name="admissions"; Port=8094; Dir="$baseDir\services\student-services\admissions-service"; Main="com.campx.admissions.AdmissionsApplication"; Cp="$baseDir\services\student-services\admissions-service\classpath.txt" },
    @{ Name="enrollment"; Port=8095; Dir="$baseDir\services\student-services\enrollment-service"; Main="com.campx.enrollment.EnrollmentApplication"; Cp="$baseDir\services\student-services\enrollment-service\classpath.txt" },
    @{ Name="lifecycle"; Port=8096; Dir="$baseDir\services\student-services\lifecycle-service"; Main="com.campx.lifecycle.LifecycleApplication"; Cp="$baseDir\services\student-services\lifecycle-service\classpath.txt" },
    @{ Name="guardian"; Port=8097; Dir="$baseDir\services\student-services\guardian-service"; Main="com.campx.guardian.GuardianApplication"; Cp="$baseDir\services\student-services\guardian-service\classpath.txt" },
    @{ Name="document"; Port=8098; Dir="$baseDir\services\student-services\document-service"; Main="com.campx.document.DocumentApplication"; Cp="$baseDir\services\student-services\document-service\classpath.txt" }
)

foreach ($s in $stm) {
    # Ensure child java process inherits IAM URL via process environment
    $ok = Start-JavaService -Name $s.Name -Port $s.Port -WorkDir $s.Dir -MainClass $s.Main -ClasspathFile $s.Cp -Xmx "96m"
    $startup += [pscustomobject]@{ step = "start-$($s.Name)"; ok = $ok; detail = "port $($s.Port)" }
    Start-Sleep -Seconds 1
}

# Confirm Path C client enabled in logs
foreach ($s in $stm) {
    $out = Join-Path $logDir "$($s.Name).out.log"
    $err = Join-Path $logDir "$($s.Name).err.log"
    $text = ""
    if (Test-Path $out) { $text += Get-Content $out -Raw -ErrorAction SilentlyContinue }
    if (Test-Path $err) { $text += Get-Content $err -Raw -ErrorAction SilentlyContinue }
    $enabled = $text -match "IAM delegation Path C client enabled"
    $failClosed = ($text -match "FailClosedDelegationPort") -and -not $enabled
    $startup += [pscustomobject]@{
        step = "pathc-client-$($s.Name)"
        ok = $enabled
        detail = $(if ($enabled) { "HttpIamDelegationPort enabled" } elseif ($failClosed) { "FailClosed (env missing)" } else { "no Path C log yet" })
    }
}

$startup | ConvertTo-Json -Depth 4 | Set-Content "$baseDir\docs\reports\R31_PATH_C_STARTUP_2026-11-15.json" -Encoding UTF8
Write-Host "Startup record written." -ForegroundColor Cyan

Write-Host "Running HTTP smoke..." -ForegroundColor Cyan
Push-Location $baseDir
try {
    node "scripts\demo\smoke_path_c_http.mjs"
    $smokeExit = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host "Stopping services..." -ForegroundColor Yellow
foreach ($p in $ports) { Stop-Port $p }
Start-Sleep -Seconds 1
$freeAfter = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 2)
Write-Host "Free RAM after stop: $freeAfter GB" -ForegroundColor White
Write-Host "Smoke exit code: $smokeExit" -ForegroundColor $(if ($smokeExit -eq 0) { "Green" } else { "Red" })
exit $smokeExit
