# scripts/dev/start_stack.ps1
$ErrorActionPreference = "Stop"

$baseDir = "D:\CampXSync\CampXSync_V2.0"
$logDir = "$baseDir\docs\latency-audit\logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "       Starting CampXSync Core Service Stack     " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# 1. Free RAM Check
try {
    $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
    $freeRamGb = [math]::Round($os.FreePhysicalMemory / 1048576, 2)
    Write-Host "System Check: Available RAM = $freeRamGb GB" -ForegroundColor White
    if ($freeRamGb -lt 3.0) {
        Write-Host "WARNING: Free RAM ($freeRamGb GB) is below 3.0 GB threshold." -ForegroundColor Yellow
        Write-Host "Cleaning up existing microservice listeners to free memory..." -ForegroundColor Yellow
        & "$baseDir\scripts\dev\stop_stack.ps1"
        Start-Sleep -Seconds 2
        $osAfter = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
        $freeRamGbAfter = [math]::Round($osAfter.FreePhysicalMemory / 1048576, 2)
        Write-Host "Available RAM after cleanup: $freeRamGbAfter GB" -ForegroundColor White
        if ($freeRamGbAfter -lt 3.0) {
            Write-Host "CRITICAL: Free RAM ($freeRamGbAfter GB) is still below 3.0 GB. Please close high-memory applications." -ForegroundColor Red
        }
    }
} catch {
    Write-Host "System Check: Free RAM detection unavailable, proceeding..." -ForegroundColor DarkGray
}

# 2. Stop old listeners on core + academic ports
$targetPorts = @(8080, 8098, 8096, 8085, 8088, 8092, 3000)
foreach ($p in $targetPorts) {
    $conns = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}
Start-Sleep -Seconds 1

# 3. Launch Student Documents Service (STM-06) on port 8098
Write-Host "Starting STM-06 Student Documents Service on port 8098 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$docDir = "$baseDir\services\student-services\document-service"
$docCp = (Get-Content "$docDir\classpath.txt" -Raw).Trim()

$docProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m",
    "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp",
    "target\classes;$docCp",
    "com.campx.document.DocumentApplication",
    "8098"
) -WorkingDirectory $docDir -RedirectStandardOutput "$logDir\stm-06.log" -RedirectStandardError "$logDir\stm-06.err" -WindowStyle Minimized -PassThru

Write-Host "STM-06 launched with PID $($docProc.Id)" -ForegroundColor DarkGray

# 3b. Launch Lifecycle Management Service (STM-04) on port 8096
Write-Host "Starting STM-04 Student Lifecycle Service on port 8096 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$lifeDir = "$baseDir\services\student-services\lifecycle-service"
$lifeCp = (Get-Content "$lifeDir\classpath.txt" -Raw).Trim()

$lifeProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m",
    "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp",
    "target\classes;$lifeCp",
    "com.campx.lifecycle.LifecycleApplication",
    "8096"
) -WorkingDirectory $lifeDir -RedirectStandardOutput "$logDir\stm-04.log" -RedirectStandardError "$logDir\stm-04.err" -WindowStyle Minimized -PassThru

Write-Host "STM-04 launched with PID $($lifeProc.Id)" -ForegroundColor DarkGray

# 3c. Launch ACD-03 Subject Management Service on port 8085
Write-Host "Starting ACD-03 Subject Management Service on port 8085 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$subjDir = "$baseDir\services\academic-services\subject-management-service"
$subjCp = (Get-Content "$subjDir\classpath.txt" -Raw).Trim()

$subjProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m",
    "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp",
    "target\classes;$subjCp",
    "com.campx.academic.subject.SubjectManagementApplication",
    "8085"
) -WorkingDirectory $subjDir -RedirectStandardOutput "$logDir\acd-03.log" -RedirectStandardError "$logDir\acd-03.err" -WindowStyle Minimized -PassThru

Write-Host "ACD-03 launched with PID $($subjProc.Id)" -ForegroundColor DarkGray

# 3d. Launch ACD-06 Attendance Management Service on port 8088
Write-Host "Starting ACD-06 Attendance Management Service on port 8088 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$attDir = "$baseDir\services\attendance-services\attendance-service"
$attCp = (Get-Content "$attDir\classpath.txt" -Raw).Trim()

$attProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m",
    "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp",
    "target\classes;$attCp",
    "com.campx.academic.attendance.AttendanceManagementApplication",
    "8088"
) -WorkingDirectory $attDir -RedirectStandardOutput "$logDir\acd-06.log" -RedirectStandardError "$logDir\acd-06.err" -WindowStyle Minimized -PassThru

Write-Host "ACD-06 launched with PID $($attProc.Id)" -ForegroundColor DarkGray

# 3e. Launch ACD-10 Reporting & Analytics Service on port 8092
Write-Host "Starting ACD-10 Reporting & Analytics Service on port 8092 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$anlDir = "$baseDir\services\analytics-services\analytics-service"
$anlCp = (Get-Content "$anlDir\classpath.txt" -Raw).Trim()

$anlProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m",
    "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp",
    "target\classes;$anlCp",
    "com.campx.academic.analytics.AnalyticsApplication",
    "8092"
) -WorkingDirectory $anlDir -RedirectStandardOutput "$logDir\acd-10.log" -RedirectStandardError "$logDir\acd-10.err" -WindowStyle Minimized -PassThru

Write-Host "ACD-10 launched with PID $($anlProc.Id)" -ForegroundColor DarkGray

# 4. Launch API Gateway on port 8080
Write-Host "Starting API Gateway on port 8080 (-Xms64m -Xmx192m)..." -ForegroundColor Cyan
$gwDir = "$baseDir\api-gateway"
$gwCp = (Get-Content "$gwDir\classpath.txt" -Raw).Trim()

$gwProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms64m",
    "-Xmx192m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-Dcampx.gateway.connect.timeout.local.ms=3000",
    "-cp",
    "target\classes;$gwCp",
    "com.campx.gateway.ApiGatewayApplication",
    "8080"
) -WorkingDirectory $gwDir -RedirectStandardOutput "$logDir\api-gateway.log" -RedirectStandardError "$logDir\api-gateway.err" -WindowStyle Minimized -PassThru

Write-Host "API Gateway launched with PID $($gwProc.Id)" -ForegroundColor DarkGray

# 5. Launch Next.js Frontend on port 3000
Write-Host "Starting Next.js Development Server on port 3000 with test session support..." -ForegroundColor Cyan
$feDir = "$baseDir\frontend"

# We set ENABLE_TEST_SESSION=true so test sessions and personas are enabled in dev
$env:ENABLE_TEST_SESSION = "true"

$feProc = Start-Process -FilePath "node.exe" -ArgumentList @(
    "node_modules\next\dist\bin\next",
    "dev",
    "-p",
    "3000"
) -WorkingDirectory $feDir -RedirectStandardOutput "$logDir\frontend.log" -RedirectStandardError "$logDir\frontend.err" -WindowStyle Minimized -PassThru

Write-Host "Next.js launched with PID $($feProc.Id)" -ForegroundColor DarkGray

# 6. Wait for health checks
Write-Host "Waiting for services to become healthy..." -ForegroundColor Yellow
$docReady = $false
$lifeReady = $false
$subjReady = $false
$attReady = $false
$anlReady = $false
$gwReady = $false
$feReady = $false
$timeout = [DateTime]::Now.AddSeconds(60)

while ([DateTime]::Now -lt $timeout -and (-not $docReady -or -not $lifeReady -or -not $subjReady -or -not $attReady -or -not $anlReady -or -not $gwReady -or -not $feReady)) {
    Start-Sleep -Seconds 2

    if (-not $docReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8098/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) {
                $docReady = $true
                Write-Host "[+] Document Service (8098) is HEALTHY" -ForegroundColor Green
            }
        } catch {}
    }

    if (-not $lifeReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8096/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) {
                $lifeReady = $true
                Write-Host "[+] Lifecycle Service (8096) is HEALTHY" -ForegroundColor Green
            }
        } catch {}
    }

    if (-not $subjReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8085/metrics" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) {
                $subjReady = $true
                Write-Host "[+] Subject Service (8085) is HEALTHY" -ForegroundColor Green
            }
        } catch {}
    }

    if (-not $attReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8088/metrics" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) {
                $attReady = $true
                Write-Host "[+] Attendance Service (8088) is HEALTHY" -ForegroundColor Green
            }
        } catch {}
    }

    if (-not $anlReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8092/metrics" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) {
                $anlReady = $true
                Write-Host "[+] Analytics Service (8092) is HEALTHY" -ForegroundColor Green
            }
        } catch {}
    }

    if (-not $gwReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) {
                $gwReady = $true
                Write-Host "[+] API Gateway (8080) is HEALTHY" -ForegroundColor Green
            }
        } catch {}
    }

    if (-not $feReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:3000/login" -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) {
                $feReady = $true
                Write-Host "[+] Frontend (3000) is HEALTHY" -ForegroundColor Green
            }
        } catch {}
    }
}

if (-not $docReady -or -not $lifeReady -or -not $subjReady -or -not $attReady -or -not $anlReady -or -not $gwReady -or -not $feReady) {
    Write-Host "WARNING: Stack may still be initializing (doc=$docReady, life=$lifeReady, subj=$subjReady, att=$attReady, anl=$anlReady, gw=$gwReady, fe=$feReady)" -ForegroundColor Yellow
} else {
    Write-Host "ALL REQUIRED SERVICES READY!" -ForegroundColor Green
}

# Run check_stack to show summary
& "$baseDir\scripts\dev\check_stack.ps1"

Write-Host "`n[Daemon] Supervisor keeping services alive with active health watchdog..." -ForegroundColor Cyan

function Ensure-Service([int]$port, [scriptblock]$startBlock, [string]$name) {
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if (-not $conn) {
        Write-Host "[Watchdog] Port $port ($name) is down. Launching..." -ForegroundColor Yellow
        & $startBlock
        Start-Sleep -Seconds 2
    }
}

while ($true) {
    Start-Sleep -Seconds 5
    
    # Watch Lifecycle Service (8096)
    Ensure-Service 8096 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$lifeCp",
            "com.campx.lifecycle.LifecycleApplication", "8096"
        ) -WorkingDirectory $lifeDir -RedirectStandardOutput "$logDir\stm-04.log" -RedirectStandardError "$logDir\stm-04.err" -WindowStyle Minimized
    } "Lifecycle Service"

    # Watch Document Service (8098)
    Ensure-Service 8098 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$docCp",
            "com.campx.document.DocumentApplication", "8098"
        ) -WorkingDirectory $docDir -RedirectStandardOutput "$logDir\stm-06.log" -RedirectStandardError "$logDir\stm-06.err" -WindowStyle Minimized
    } "Document Service"

    # Watch Subject Service (8085)
    Ensure-Service 8085 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$subjCp",
            "com.campx.academic.subject.SubjectManagementApplication", "8085"
        ) -WorkingDirectory $subjDir -RedirectStandardOutput "$logDir\acd-03.log" -RedirectStandardError "$logDir\acd-03.err" -WindowStyle Minimized
    } "Subject Service"

    # Watch Attendance Service (8088)
    Ensure-Service 8088 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$attCp",
            "com.campx.academic.attendance.AttendanceManagementApplication", "8088"
        ) -WorkingDirectory $attDir -RedirectStandardOutput "$logDir\acd-06.log" -RedirectStandardError "$logDir\acd-06.err" -WindowStyle Minimized
    } "Attendance Service"

    # Watch Analytics Service (8092)
    Ensure-Service 8092 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$anlCp",
            "com.campx.academic.analytics.AnalyticsApplication", "8092"
        ) -WorkingDirectory $anlDir -RedirectStandardOutput "$logDir\acd-10.log" -RedirectStandardError "$logDir\acd-10.err" -WindowStyle Minimized
    } "Analytics Service"

    # Watch API Gateway (8080)
    Ensure-Service 8080 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms64m", "-Xmx192m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-Dcampx.gateway.connect.timeout.local.ms=3000",
            "-cp", "target\classes;$gwCp",
            "com.campx.gateway.ApiGatewayApplication", "8080"
        ) -WorkingDirectory $gwDir -RedirectStandardOutput "$logDir\api-gateway.log" -RedirectStandardError "$logDir\api-gateway.err" -WindowStyle Minimized
    } "API Gateway"

    # Watch Frontend (3000)
    Ensure-Service 3000 {
        $env:ENABLE_TEST_SESSION = "true"
        Start-Process -FilePath "node.exe" -ArgumentList @(
            "node_modules\next\dist\bin\next", "dev", "-p", "3000"
        ) -WorkingDirectory $feDir -RedirectStandardOutput "$logDir\frontend.log" -RedirectStandardError "$logDir\frontend.err" -WindowStyle Minimized
    } "Frontend"
}
