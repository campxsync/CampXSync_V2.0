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
$targetPorts = @(8080, 8081, 8093, 8098, 8096, 8085, 8088, 8092, 8099, 8100, 8101, 8102, 8103, 8104, 3000)
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

# 3a. Launch Institute Admin Service (ADM-01 / IAM Path C) on port 8081
Write-Host "Starting ADM-01 Institute Admin Service on port 8081 (-Xms32m -Xmx128m)..." -ForegroundColor Cyan
$iamDir = "$baseDir\services\administration-services\institute-admin-service"
$iamCp = (Get-Content "$iamDir\classpath.txt" -Raw).Trim()

$iamProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m",
    "-Xmx128m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp",
    "target\classes;$iamCp",
    "com.campx.admin.institute.InstituteAdminApplication",
    "8081"
) -WorkingDirectory $iamDir -RedirectStandardOutput "$logDir\adm-01.log" -RedirectStandardError "$logDir\adm-01.err" -WindowStyle Minimized -PassThru

Write-Host "ADM-01 launched with PID $($iamProc.Id)" -ForegroundColor DarkGray

# 3a1. Launch IADM-01 Institute & Tenant Lifecycle on port 8099
Write-Host "Starting IADM-01 Tenant Lifecycle Service on port 8099 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$iadm01Dir = "$baseDir\services\administration-services\iadm-01-tenant-lifecycle-service"
if (Test-Path "$iadm01Dir\classpath.txt") {
    $iadm01Cp = (Get-Content "$iadm01Dir\classpath.txt" -Raw).Trim()
    $iadm01Proc = Start-Process -FilePath "java" -ArgumentList @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-cp",
        "target\classes;$iadm01Cp",
        "com.campx.iadm.lifecycle.Iadm01Application",
        "8099"
    ) -WorkingDirectory $iadm01Dir -RedirectStandardOutput "$logDir\iadm-01.log" -RedirectStandardError "$logDir\iadm-01.err" -WindowStyle Minimized -PassThru
    Write-Host "IADM-01 launched with PID $($iadm01Proc.Id)" -ForegroundColor DarkGray
} else {
    Write-Host "IADM-01 classpath.txt missing — skip launch (build module first)" -ForegroundColor Yellow
}

# 3a1b. Launch IADM-02 Platform Configuration on port 8100
Write-Host "Starting IADM-02 Platform Configuration Service on port 8100 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$iadm02Dir = "$baseDir\services\administration-services\iadm-02-platform-config-service"
if (Test-Path "$iadm02Dir\classpath.txt") {
    $iadm02Cp = (Get-Content "$iadm02Dir\classpath.txt" -Raw).Trim()
    $iadm02Proc = Start-Process -FilePath "java" -ArgumentList @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-cp",
        "target\classes;$iadm02Cp",
        "com.campx.iadm.config.Iadm02Application",
        "8100"
    ) -WorkingDirectory $iadm02Dir -RedirectStandardOutput "$logDir\iadm-02.log" -RedirectStandardError "$logDir\iadm-02.err" -WindowStyle Minimized -PassThru
    Write-Host "IADM-02 launched with PID $($iadm02Proc.Id)" -ForegroundColor DarkGray
} else {
    Write-Host "IADM-02 classpath.txt missing — skip launch (build module first)" -ForegroundColor Yellow
}

# 3a1c. Launch IADM-03 Identity & Access Governance on port 8101
Write-Host "Starting IADM-03 Identity Access Service on port 8101 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$iadm03Dir = "$baseDir\services\administration-services\iadm-03-identity-access-service"
if (Test-Path "$iadm03Dir\classpath.txt") {
    $iadm03Cp = (Get-Content "$iadm03Dir\classpath.txt" -Raw).Trim()
    $iadm03Proc = Start-Process -FilePath "java" -ArgumentList @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-cp",
        "target\classes;$iadm03Cp",
        "com.campx.iadm.access.Iadm03Application",
        "8101"
    ) -WorkingDirectory $iadm03Dir -RedirectStandardOutput "$logDir\iadm-03.log" -RedirectStandardError "$logDir\iadm-03.err" -WindowStyle Minimized -PassThru
    Write-Host "IADM-03 launched with PID $($iadm03Proc.Id)" -ForegroundColor DarkGray
} else {
    Write-Host "IADM-03 classpath.txt missing — skip launch (build module first)" -ForegroundColor Yellow
}

# 3a1d. Launch IADM-04 Billing & Entitlements on port 8102
Write-Host "Starting IADM-04 Billing Entitlements Service on port 8102 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$iadm04Dir = "$baseDir\services\administration-services\iadm-04-billing-entitlements-service"
if (Test-Path "$iadm04Dir\classpath.txt") {
    $iadm04Cp = (Get-Content "$iadm04Dir\classpath.txt" -Raw).Trim()
    $iadm04Proc = Start-Process -FilePath "java" -ArgumentList @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-cp",
        "target\classes;$iadm04Cp",
        "com.campx.iadm.billing.Iadm04Application",
        "8102"
    ) -WorkingDirectory $iadm04Dir -RedirectStandardOutput "$logDir\iadm-04.log" -RedirectStandardError "$logDir\iadm-04.err" -WindowStyle Minimized -PassThru
    Write-Host "IADM-04 launched with PID $($iadm04Proc.Id)" -ForegroundColor DarkGray
} else {
    Write-Host "IADM-04 classpath.txt missing — skip launch (build module first)" -ForegroundColor Yellow
}

# 3a1e. Launch IADM-05 Data Governance & Privacy on port 8103
Write-Host "Starting IADM-05 Data Governance Service on port 8103 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$iadm05Dir = "$baseDir\services\administration-services\iadm-05-data-governance-service"
if (Test-Path "$iadm05Dir\classpath.txt") {
    $iadm05Cp = (Get-Content "$iadm05Dir\classpath.txt" -Raw).Trim()
    $iadm05Proc = Start-Process -FilePath "java" -ArgumentList @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-cp",
        "target\classes;$iadm05Cp",
        "com.campx.iadm.governance.Iadm05Application",
        "8103"
    ) -WorkingDirectory $iadm05Dir -RedirectStandardOutput "$logDir\iadm-05.log" -RedirectStandardError "$logDir\iadm-05.err" -WindowStyle Minimized -PassThru
    Write-Host "IADM-05 launched with PID $($iadm05Proc.Id)" -ForegroundColor DarkGray
} else {
    Write-Host "IADM-05 classpath.txt missing — skip launch (build module first)" -ForegroundColor Yellow
}

# 3a1f. Launch IADM-06 Platform Analytics & Usage Insights on port 8104
Write-Host "Starting IADM-06 Platform Analytics Service on port 8104 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$iadm06Dir = "$baseDir\services\administration-services\iadm-06-platform-analytics-service"
if (Test-Path "$iadm06Dir\classpath.txt") {
    $iadm06Cp = (Get-Content "$iadm06Dir\classpath.txt" -Raw).Trim()
    $iadm06Proc = Start-Process -FilePath "java" -ArgumentList @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-cp",
        "target\classes;$iadm06Cp",
        "com.campx.iadm.analytics.Iadm06Application",
        "8104"
    ) -WorkingDirectory $iadm06Dir -RedirectStandardOutput "$logDir\iadm-06.log" -RedirectStandardError "$logDir\iadm-06.err" -WindowStyle Minimized -PassThru
    Write-Host "IADM-06 launched with PID $($iadm06Proc.Id)" -ForegroundColor DarkGray
} else {
    Write-Host "IADM-06 classpath.txt missing — skip launch (build module first)" -ForegroundColor Yellow
}

# Path C + STM-07 IAM role port client base (student-service)
$env:CAMPX_IAM_DELEGATION_BASE_URL = "http://127.0.0.1:8081"

# 3a2. Launch Student Domain Service (STM-01 / STM-07 host) on port 8093
Write-Host "Starting STM-01 Student Domain Service on port 8093 (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
$stuDir = "$baseDir\services\student-services\student-service"
$stuCp = (Get-Content "$stuDir\classpath.txt" -Raw).Trim()

$stuProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m",
    "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-Dcampx.iam.delegation.baseUrl=http://127.0.0.1:8081",
    "-cp",
    "target\classes;$stuCp",
    "com.campx.student.StudentApplication",
    "8093"
) -WorkingDirectory $stuDir -RedirectStandardOutput "$logDir\stm-01.log" -RedirectStandardError "$logDir\stm-01.err" -WindowStyle Minimized -PassThru

Write-Host "STM-01 launched with PID $($stuProc.Id)" -ForegroundColor DarkGray

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
$iamReady = $false
$stuReady = $false
$lifeReady = $false
$subjReady = $false
$attReady = $false
$anlReady = $false
$gwReady = $false
$feReady = $false
$timeout = [DateTime]::Now.AddSeconds(90)

while ([DateTime]::Now -lt $timeout -and (-not $docReady -or -not $iamReady -or -not $stuReady -or -not $lifeReady -or -not $subjReady -or -not $attReady -or -not $anlReady -or -not $gwReady -or -not $feReady)) {
    Start-Sleep -Seconds 2

    if (-not $iamReady) {
        $conn = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
        if ($conn) {
            $iamReady = $true
            Write-Host "[+] Institute Admin / IAM (8081) is LISTENING" -ForegroundColor Green
        }
    }

    if (-not $stuReady) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8093/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($r -and ($r.StatusCode -eq 200 -or $r.StatusCode -eq 401 -or $r.StatusCode -eq 403)) {
                $stuReady = $true
                Write-Host "[+] Student Service (8093) is HEALTHY" -ForegroundColor Green
            }
        } catch {
            $code = $null
            if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
            if ($code -eq 401 -or $code -eq 403) {
                $stuReady = $true
                Write-Host "[+] Student Service (8093) is UP (auth challenge)" -ForegroundColor Green
            }
        }
    }

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

if (-not $docReady -or -not $iamReady -or -not $stuReady -or -not $lifeReady -or -not $subjReady -or -not $attReady -or -not $anlReady -or -not $gwReady -or -not $feReady) {
    Write-Host "WARNING: Stack may still be initializing (doc=$docReady, iam=$iamReady, stu=$stuReady, life=$lifeReady, subj=$subjReady, att=$attReady, anl=$anlReady, gw=$gwReady, fe=$feReady)" -ForegroundColor Yellow
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

    # Watch Institute Admin / IAM (8081)
    Ensure-Service 8081 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx128m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$iamCp",
            "com.campx.admin.institute.InstituteAdminApplication", "8081"
        ) -WorkingDirectory $iamDir -RedirectStandardOutput "$logDir\adm-01.log" -RedirectStandardError "$logDir\adm-01.err" -WindowStyle Minimized
    } "Institute Admin"

    # Watch Student Domain Service (8093)
    Ensure-Service 8093 {
        $env:CAMPX_IAM_DELEGATION_BASE_URL = "http://127.0.0.1:8081"
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-Dcampx.iam.delegation.baseUrl=http://127.0.0.1:8081",
            "-cp", "target\classes;$stuCp",
            "com.campx.student.StudentApplication", "8093"
        ) -WorkingDirectory $stuDir -RedirectStandardOutput "$logDir\stm-01.log" -RedirectStandardError "$logDir\stm-01.err" -WindowStyle Minimized
    } "Student Service"
    
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
