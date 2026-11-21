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
$targetPorts = @(8080, 8081, 8093, 8098, 8096, 8085, 8088, 8092, 3000) + (8099..8106)
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

# 3a1. Launch IADM microservices on ports 8099..8106
$iadmServices = @(
    @{ Port = 8099; Dir = "iadm-01-tenant-lifecycle-service"; Main = "com.campx.iadm.lifecycle.Iadm01Application"; Log = "iadm-01"; Name = "IADM-01 Tenant Lifecycle" },
    @{ Port = 8100; Dir = "iadm-02-platform-config-service"; Main = "com.campx.iadm.config.Iadm02Application"; Log = "iadm-02"; Name = "IADM-02 Platform Config" },
    @{ Port = 8101; Dir = "iadm-03-identity-access-service"; Main = "com.campx.iadm.identity.Iadm03Application"; Log = "iadm-03"; Name = "IADM-03 Identity Access" },
    @{ Port = 8102; Dir = "iadm-04-billing-entitlements-service"; Main = "com.campx.iadm.billing.Iadm04Application"; Log = "iadm-04"; Name = "IADM-04 Billing Entitlements" },
    @{ Port = 8103; Dir = "iadm-05-data-governance-service"; Main = "com.campx.iadm.privacy.Iadm05Application"; Log = "iadm-05"; Name = "IADM-05 Data Governance" },
    @{ Port = 8104; Dir = "iadm-06-platform-analytics-service"; Main = "com.campx.iadm.analytics.Iadm06Application"; Log = "iadm-06"; Name = "IADM-06 Platform Analytics" },
    @{ Port = 8105; Dir = "iadm-07-security-compliance-service"; Main = "com.campx.iadm.securitygov.Iadm07Application"; Log = "iadm-07"; Name = "IADM-07 Security Compliance" },
    @{ Port = 8106; Dir = "iadm-08-audit-ops-service"; Main = "com.campx.iadm.audit.Iadm08Application"; Log = "iadm-08"; Name = "IADM-08 Audit Ops" }
)
$iadmDirs = @{}
$iadmCps = @{}
$iadmMains = @{}
$iadmLogs = @{}
foreach ($svc in $iadmServices) {
    Write-Host "Starting $($svc.Name) on port $($svc.Port) (-Xms32m -Xmx96m)..." -ForegroundColor Cyan
    $svcDir = "$baseDir\services\administration-services\$($svc.Dir)"
    $svcCp = (Get-Content "$svcDir\classpath.txt" -Raw).Trim()
    $iadmDirs[$svc.Port] = $svcDir
    $iadmCps[$svc.Port] = $svcCp
    $iadmMains[$svc.Port] = $svc.Main
    $iadmLogs[$svc.Port] = $svc.Log
    $iadmProc = Start-Process -FilePath "java" -ArgumentList @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=true",
        "-cp",
        "target\classes;$svcCp",
        $svc.Main,
        "$($svc.Port)"
    ) -WorkingDirectory $svcDir -RedirectStandardOutput "$logDir\$($svc.Log).log" -RedirectStandardError "$logDir\$($svc.Log).err" -WindowStyle Minimized -PassThru
    Write-Host "$($svc.Name) launched with PID $($iadmProc.Id)" -ForegroundColor DarkGray
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

    # Watch IADM services (8099..8106)
    Ensure-Service 8099 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8099])",
            $iadmMains[8099], "8099"
        ) -WorkingDirectory $iadmDirs[8099] -RedirectStandardOutput "$logDir\$($iadmLogs[8099]).log" -RedirectStandardError "$logDir\$($iadmLogs[8099]).err" -WindowStyle Minimized
    } "IADM-01"
    Ensure-Service 8100 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8100])",
            $iadmMains[8100], "8100"
        ) -WorkingDirectory $iadmDirs[8100] -RedirectStandardOutput "$logDir\$($iadmLogs[8100]).log" -RedirectStandardError "$logDir\$($iadmLogs[8100]).err" -WindowStyle Minimized
    } "IADM-02"
    Ensure-Service 8101 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8101])",
            $iadmMains[8101], "8101"
        ) -WorkingDirectory $iadmDirs[8101] -RedirectStandardOutput "$logDir\$($iadmLogs[8101]).log" -RedirectStandardError "$logDir\$($iadmLogs[8101]).err" -WindowStyle Minimized
    } "IADM-03"
    Ensure-Service 8102 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8102])",
            $iadmMains[8102], "8102"
        ) -WorkingDirectory $iadmDirs[8102] -RedirectStandardOutput "$logDir\$($iadmLogs[8102]).log" -RedirectStandardError "$logDir\$($iadmLogs[8102]).err" -WindowStyle Minimized
    } "IADM-04"
    Ensure-Service 8103 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8103])",
            $iadmMains[8103], "8103"
        ) -WorkingDirectory $iadmDirs[8103] -RedirectStandardOutput "$logDir\$($iadmLogs[8103]).log" -RedirectStandardError "$logDir\$($iadmLogs[8103]).err" -WindowStyle Minimized
    } "IADM-05"
    Ensure-Service 8104 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8104])",
            $iadmMains[8104], "8104"
        ) -WorkingDirectory $iadmDirs[8104] -RedirectStandardOutput "$logDir\$($iadmLogs[8104]).log" -RedirectStandardError "$logDir\$($iadmLogs[8104]).err" -WindowStyle Minimized
    } "IADM-06"
    Ensure-Service 8105 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8105])",
            $iadmMains[8105], "8105"
        ) -WorkingDirectory $iadmDirs[8105] -RedirectStandardOutput "$logDir\$($iadmLogs[8105]).log" -RedirectStandardError "$logDir\$($iadmLogs[8105]).err" -WindowStyle Minimized
    } "IADM-07"
    Ensure-Service 8106 {
        Start-Process -FilePath "java" -ArgumentList @(
            "-Xms32m", "-Xmx96m",
            "-Dcampx.persistence.mode=postgres",
            "-Dcampx.internal.auth.enabled=true",
            "-cp", "target\classes;$($iadmCps[8106])",
            $iadmMains[8106], "8106"
        ) -WorkingDirectory $iadmDirs[8106] -RedirectStandardOutput "$logDir\$($iadmLogs[8106]).log" -RedirectStandardError "$logDir\$($iadmLogs[8106]).err" -WindowStyle Minimized
    } "IADM-08"

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
