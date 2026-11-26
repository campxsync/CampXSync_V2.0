# scripts/dev/restart_service.ps1
# Fast single-service compile & restart tool for CampXSync
# Usage: powershell -File scripts/dev/restart_service.ps1 <service-name>
# Examples:
#   .\scripts\dev\restart_service.ps1 document-service
#   .\scripts\dev\restart_service.ps1 api-gateway
#   .\scripts\dev\restart_service.ps1 frontend

param (
    [Parameter(Mandatory=$true)]
    [string]$Service
)

$ErrorActionPreference = "Stop"
$baseDir = "D:\CampXSync\CampXSync_V2.0"

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "       Fast Restart Service: $Service            " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

function Stop-PortListener([int]$port) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            Write-Host "Stopping existing process PID $procId on port $port..." -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

function Wait-HealthCheck([string]$url, [int]$timeoutSec = 20) {
    # Hard wall-clock timeout — WebRequest.Create can hang past its Timeout on Windows;
    # use Invoke-WebRequest -TimeoutSec and always exit the wait loop.
    Write-Host "Waiting for $url to become healthy (max ${timeoutSec}s)..." -ForegroundColor Gray
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        try {
            $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            $code = [int]$resp.StatusCode
            if ($code -ge 200 -and $code -lt 500) {
                # 401/403 still prove the listener is up (HMAC challenge)
                Write-Host "[+] Service is HEALTHY on $url (HTTP $code)" -ForegroundColor Green
                return $true
            }
        } catch {
            $ex = $_.Exception
            if ($ex.Response -and [int]$ex.Response.StatusCode -ge 200 -and [int]$ex.Response.StatusCode -lt 500) {
                Write-Host "[+] Service is HEALTHY on $url (HTTP $([int]$ex.Response.StatusCode))" -ForegroundColor Green
                return $true
            }
            # connection refused / timeout — keep polling until wall clock expires
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Host "[-] Timed out waiting for $url after ${timeoutSec}s" -ForegroundColor Red
    return $false
}

switch ($Service.ToLower()) {
    "document-service" {
        Write-Host "1. Building document-service quickly..." -ForegroundColor Cyan
        & "$baseDir\scripts\dev\build_document_service.ps1"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Build failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8098 listener..." -ForegroundColor Cyan
        Stop-PortListener 8098
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching document-service in background..." -ForegroundColor Cyan
        $docDir = "$baseDir\services\student-services\document-service"
        $cp = Get-Content "$docDir\classpath.txt" -Raw
        $fullCp = "$docDir\target\classes;$cp"
        
        $docProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.document.DocumentApplication",
            "8098"
        ) -WorkingDirectory $docDir -RedirectStandardOutput "$baseDir\logs\stm-06.log" -RedirectStandardError "$baseDir\logs\stm-06.err" -WindowStyle Minimized -PassThru
        Write-Host "Started document-service PID $($docProc.Id)" -ForegroundColor DarkGray

        Wait-HealthCheck "http://127.0.0.1:8098/health"
    }

    "api-gateway" {
        Write-Host "1. Building api-gateway quickly..." -ForegroundColor Cyan
        $gwDir = "$baseDir\api-gateway"
        $cp = (Get-Content "$gwDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$gwDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$gwDir\target\classes")) {
            New-Item -ItemType Directory -Path "$gwDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$cp" -d "$gwDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "api-gateway compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8080 listener..." -ForegroundColor Cyan
        Stop-PortListener 8080
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching api-gateway..." -ForegroundColor Cyan
        $gwProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms64m",
            "-Xmx192m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-Dcampx.gateway.connect.timeout.local.ms=3000",
            "-cp",
            "target\classes;$cp",
            "com.campx.gateway.ApiGatewayApplication",
            "8080"
        ) -WorkingDirectory $gwDir -RedirectStandardOutput "$baseDir\logs\api-gateway.log" -RedirectStandardError "$baseDir\logs\api-gateway.err" -WindowStyle Minimized -PassThru
        Write-Host "Started api-gateway PID $($gwProc.Id)" -ForegroundColor DarkGray

        Wait-HealthCheck "http://127.0.0.1:8080/actuator/health"
    }

    "lifecycle-service" {
        Write-Host "1. Building lifecycle-service quickly..." -ForegroundColor Cyan
        & "$baseDir\scripts\dev\build_lifecycle_service.ps1"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Build failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8096 listener..." -ForegroundColor Cyan
        Stop-PortListener 8096
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching lifecycle-service in background..." -ForegroundColor Cyan
        $lifeDir = "$baseDir\services\student-services\lifecycle-service"
        $cp = (Get-Content "$lifeDir\classpath.txt" -Raw).Trim()

        $lifeProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.lifecycle.LifecycleApplication",
            "8096"
        ) -WorkingDirectory $lifeDir -RedirectStandardOutput "$baseDir\logs\stm-04.log" -RedirectStandardError "$baseDir\logs\stm-04.err" -WindowStyle Minimized -PassThru
        Write-Host "Started lifecycle-service PID $($lifeProc.Id)" -ForegroundColor DarkGray

        Wait-HealthCheck "http://127.0.0.1:8096/health"
    }

    "frontend" {
        Write-Host "1. Stopping port 3000 listener..." -ForegroundColor Cyan
        Stop-PortListener 3000
        Start-Sleep -Milliseconds 500

        Write-Host "2. Launching Next.js frontend on port 3000..." -ForegroundColor Cyan
        $frontendDir = "$baseDir\frontend"
        $env:PORT = "3000"
        $env:ENABLE_TEST_SESSION = "true"
        $env:NEXT_PUBLIC_ENABLE_TEST_SESSION = "true"

        $feProc = Start-Process -FilePath "node.exe" -ArgumentList @(
            "node_modules\next\dist\bin\next",
            "dev",
            "-p",
            "3000"
        ) -WorkingDirectory $frontendDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\frontend.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\frontend.err" -WindowStyle Minimized -PassThru

        Write-Host "Started frontend PID $($feProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://localhost:3000/login" 30
    }

    { $_ -in @("attendance-service", "acd-06") } {
        Write-Host "1. Building attendance-service..." -ForegroundColor Cyan
        $attDir = "$baseDir\services\attendance-services\attendance-service"
        $cp = (Get-Content "$attDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$attDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$attDir\target\classes")) {
            New-Item -ItemType Directory -Path "$attDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$attDir\target\classes;$cp" -d "$attDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "attendance-service compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8088 listener..." -ForegroundColor Cyan
        Stop-PortListener 8088
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching attendance-service..." -ForegroundColor Cyan
        $attProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.academic.attendance.AttendanceManagementApplication",
            "8088"
        ) -WorkingDirectory $attDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\acd-06.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\acd-06.err" -WindowStyle Minimized -PassThru
        Write-Host "Started attendance-service PID $($attProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8088/metrics"
    }

    { $_ -in @("subject-management-service", "subject-service", "acd-03") } {
        Write-Host "1. Building subject-management-service..." -ForegroundColor Cyan
        $subjDir = "$baseDir\services\academic-services\subject-management-service"
        $cp = (Get-Content "$subjDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$subjDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$subjDir\target\classes")) {
            New-Item -ItemType Directory -Path "$subjDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$subjDir\target\classes;$cp" -d "$subjDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "subject-management-service compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8085 listener..." -ForegroundColor Cyan
        Stop-PortListener 8085
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching subject-management-service..." -ForegroundColor Cyan
        $subjProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.academic.subject.SubjectManagementApplication",
            "8085"
        ) -WorkingDirectory $subjDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\acd-03.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\acd-03.err" -WindowStyle Minimized -PassThru
        Write-Host "Started subject-management-service PID $($subjProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8085/metrics"
    }

    { $_ -in @("analytics-service", "acd-10", "reporting-analytics") } {
        Write-Host "1. Building analytics-service..." -ForegroundColor Cyan
        $anlDir = "$baseDir\services\analytics-services\analytics-service"
        $cp = (Get-Content "$anlDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$anlDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$anlDir\target\classes")) {
            New-Item -ItemType Directory -Path "$anlDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$anlDir\target\classes;$cp" -d "$anlDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "analytics-service compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8092 listener..." -ForegroundColor Cyan
        Stop-PortListener 8092
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching analytics-service..." -ForegroundColor Cyan
        $anlProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.academic.analytics.AnalyticsApplication",
            "8092"
        ) -WorkingDirectory $anlDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\acd-10.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\acd-10.err" -WindowStyle Minimized -PassThru
        Write-Host "Started analytics-service PID $($anlProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8092/metrics"
    }

    { $_ -in @("iadm-01", "iadm-01-tenant-lifecycle-service", "iadm01") } {
        Write-Host "1. Building iadm-01-tenant-lifecycle-service..." -ForegroundColor Cyan
        $iadmDir = "$baseDir\services\administration-services\iadm-01-tenant-lifecycle-service"
        $cp = (Get-Content "$iadmDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$iadmDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$iadmDir\target\classes")) {
            New-Item -ItemType Directory -Path "$iadmDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$iadmDir\target\classes;$cp" -d "$iadmDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "iadm-01 compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8099 listener..." -ForegroundColor Cyan
        Stop-PortListener 8099
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching iadm-01..." -ForegroundColor Cyan
        $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.iadm.lifecycle.Iadm01Application",
            "8099"
        ) -WorkingDirectory $iadmDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\iadm-01.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\iadm-01.err" -WindowStyle Minimized -PassThru
        Write-Host "Started iadm-01 PID $($iadmProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8099/api/v1/iadm/01/health"
    }

    { $_ -in @("iadm-02", "iadm-02-platform-config-service", "iadm02") } {
        Write-Host "1. Building iadm-02-platform-config-service..." -ForegroundColor Cyan
        $iadmDir = "$baseDir\services\administration-services\iadm-02-platform-config-service"
        $cp = (Get-Content "$iadmDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$iadmDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$iadmDir\target\classes")) {
            New-Item -ItemType Directory -Path "$iadmDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$iadmDir\target\classes;$cp" -d "$iadmDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "iadm-02 compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8100 listener..." -ForegroundColor Cyan
        Stop-PortListener 8100
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching iadm-02..." -ForegroundColor Cyan
        $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.iadm.config.Iadm02Application",
            "8100"
        ) -WorkingDirectory $iadmDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\iadm-02.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\iadm-02.err" -WindowStyle Minimized -PassThru
        Write-Host "Started iadm-02 PID $($iadmProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8100/api/v1/iadm/02/health"
    }

    { $_ -in @("iadm-03", "iadm-03-identity-access-service", "iadm03") } {
        Write-Host "1. Building iadm-03-identity-access-service..." -ForegroundColor Cyan
        $iadmDir = "$baseDir\services\administration-services\iadm-03-identity-access-service"
        $cp = (Get-Content "$iadmDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$iadmDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$iadmDir\target\classes")) {
            New-Item -ItemType Directory -Path "$iadmDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$iadmDir\target\classes;$cp" -d "$iadmDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "iadm-03 compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8101 listener..." -ForegroundColor Cyan
        Stop-PortListener 8101
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching iadm-03..." -ForegroundColor Cyan
        $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.iadm.access.Iadm03Application",
            "8101"
        ) -WorkingDirectory $iadmDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\iadm-03.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\iadm-03.err" -WindowStyle Minimized -PassThru
        Write-Host "Started iadm-03 PID $($iadmProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8101/api/v1/iadm/03/health"
    }

    { $_ -in @("iadm-04", "iadm-04-billing-entitlements-service", "iadm04") } {
        Write-Host "1. Building iadm-04-billing-entitlements-service..." -ForegroundColor Cyan
        $iadmDir = "$baseDir\services\administration-services\iadm-04-billing-entitlements-service"
        $cp = (Get-Content "$iadmDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$iadmDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$iadmDir\target\classes")) {
            New-Item -ItemType Directory -Path "$iadmDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$iadmDir\target\classes;$cp" -d "$iadmDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "iadm-04 compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8102 listener..." -ForegroundColor Cyan
        Stop-PortListener 8102
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching iadm-04..." -ForegroundColor Cyan
        $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.iadm.billing.Iadm04Application",
            "8102"
        ) -WorkingDirectory $iadmDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\iadm-04.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\iadm-04.err" -WindowStyle Minimized -PassThru
        Write-Host "Started iadm-04 PID $($iadmProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8102/api/v1/iadm/04/health"
    }

    { $_ -in @("iadm-05", "iadm-05-data-governance-service", "iadm05") } {
        Write-Host "1. Building iadm-05-data-governance-service..." -ForegroundColor Cyan
        $iadmDir = "$baseDir\services\administration-services\iadm-05-data-governance-service"
        if (-not (Test-Path "$iadmDir\classpath.txt")) {
            Copy-Item "$baseDir\services\administration-services\iadm-04-billing-entitlements-service\classpath.txt" "$iadmDir\classpath.txt"
        }
        $cp = (Get-Content "$iadmDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$iadmDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$iadmDir\target\classes")) {
            New-Item -ItemType Directory -Path "$iadmDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$iadmDir\target\classes;$cp" -d "$iadmDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "iadm-05 compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8103 listener..." -ForegroundColor Cyan
        Stop-PortListener 8103
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching iadm-05..." -ForegroundColor Cyan
        $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.iadm.governance.Iadm05Application",
            "8103"
        ) -WorkingDirectory $iadmDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\iadm-05.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\iadm-05.err" -WindowStyle Minimized -PassThru
        Write-Host "Started iadm-05 PID $($iadmProc.Id)" -ForegroundColor DarkGray
        Wait-HealthCheck "http://127.0.0.1:8103/api/v1/iadm/05/health"
    }

    { $_ -in @("iadm-06", "iadm-06-platform-analytics-service", "iadm06") } {
        Write-Host "1. Building iadm-06-platform-analytics-service..." -ForegroundColor Cyan
        $iadmDir = "$baseDir\services\administration-services\iadm-06-platform-analytics-service"
        if (-not (Test-Path "$iadmDir\classpath.txt")) {
            Copy-Item "$baseDir\services\administration-services\iadm-05-data-governance-service\classpath.txt" "$iadmDir\classpath.txt"
        }
        $cp = (Get-Content "$iadmDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$iadmDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$iadmDir\target\classes")) {
            New-Item -ItemType Directory -Path "$iadmDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$iadmDir\target\classes;$cp" -d "$iadmDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "iadm-06 compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8104 listener..." -ForegroundColor Cyan
        Stop-PortListener 8104
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching iadm-06..." -ForegroundColor Cyan
        $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.iadm.analytics.Iadm06Application",
            "8104"
        ) -WorkingDirectory $iadmDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\iadm-06.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\iadm-06.err" -WindowStyle Minimized -PassThru
        Write-Host "Started iadm-06 PID $($iadmProc.Id)" -ForegroundColor DarkGray
        if (-not (Wait-HealthCheck "http://127.0.0.1:8104/api/v1/iadm/06/health" 25)) {
            Write-Host "iadm-06 health check timed out — aborting." -ForegroundColor Red
            exit 1
        }
    }

    { $_ -in @("iadm-07", "iadm-07-security-compliance-service", "iadm07") } {
        Write-Host "1. Building iadm-07-security-compliance-service..." -ForegroundColor Cyan
        $iadmDir = "$baseDir\services\administration-services\iadm-07-security-compliance-service"
        if (-not (Test-Path "$iadmDir\classpath.txt")) {
            Copy-Item "$baseDir\services\administration-services\iadm-06-platform-analytics-service\classpath.txt" "$iadmDir\classpath.txt"
        }
        $cp = (Get-Content "$iadmDir\classpath.txt" -Raw).Trim()
        $srcFiles = Get-ChildItem -Path "$iadmDir\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
        if (-not (Test-Path "$iadmDir\target\classes")) {
            New-Item -ItemType Directory -Path "$iadmDir\target\classes" -Force | Out-Null
        }
        & javac -cp "$iadmDir\target\classes;$cp" -d "$iadmDir\target\classes" $srcFiles
        if ($LASTEXITCODE -ne 0) {
            Write-Host "iadm-07 compilation failed! Aborting restart." -ForegroundColor Red
            exit 1
        }

        Write-Host "2. Stopping port 8105 listener..." -ForegroundColor Cyan
        Stop-PortListener 8105
        Start-Sleep -Milliseconds 500

        Write-Host "3. Launching iadm-07..." -ForegroundColor Cyan
        $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m",
            "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp",
            "target\classes;$cp",
            "com.campx.iadm.security.Iadm07Application",
            "8105"
        ) -WorkingDirectory $iadmDir -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\iadm-07.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\iadm-07.err" -WindowStyle Minimized -PassThru
        Write-Host "Started iadm-07 PID $($iadmProc.Id)" -ForegroundColor DarkGray
        if (-not (Wait-HealthCheck "http://127.0.0.1:8105/api/v1/iadm/07/health" 25)) {
            Write-Host "iadm-07 health check timed out — aborting." -ForegroundColor Red
            exit 1
        }
    }

    default {
        Write-Host "Unknown service: $Service. Supported: document-service, api-gateway, lifecycle-service, frontend, attendance-service, subject-management-service, analytics-service, iadm-01, iadm-02, iadm-03, iadm-04, iadm-05, iadm-06, iadm-07" -ForegroundColor Red
        exit 1
    }
}
