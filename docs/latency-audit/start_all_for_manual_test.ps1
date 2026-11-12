$ErrorActionPreference = "Continue"

$baseDir = "D:\CampXSync\CampXSync_V2.0"
$logDir = "$baseDir\docs\latency-audit\logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$commonJars = "C:\Users\niran\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar;C:\Users\niran\.m2\repository\org\checkerframework\checker-qual\3.42.0\checker-qual-3.42.0.jar;C:\Users\niran\.m2\repository\com\zaxxer\HikariCP\4.0.3\HikariCP-4.0.3.jar;C:\Users\niran\.m2\repository\org\slf4j\slf4j-api\1.7.30\slf4j-api-1.7.30.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;$baseDir\logger\target\classes"

# 1. Clean up existing listeners on ports 8080..8098 and 3000
$ports = (8080..8098) + 3000
foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($p in $pids) {
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
}
Start-Sleep -Seconds 1

# 2. Launch 13 Persona Microservices
$services = @(
    @{ Name = "adm-01"; Port = 8081; Dir = "services\administration-services\institute-admin-service"; Main = "com.campx.admin.institute.InstituteAdminApplication" },
    @{ Name = "adm-02"; Port = 8082; Dir = "services\administration-services\college-admin-service";   Main = "com.campx.admin.college.CollegeAdminApplication" },
    @{ Name = "acd-03"; Port = 8085; Dir = "services\academic-services\subject-management-service";   Main = "com.campx.academic.subject.SubjectManagementApplication" },
    @{ Name = "acd-04"; Port = 8086; Dir = "services\academic-services\batch-management-service";     Main = "com.campx.academic.batch.BatchManagementApplication" },
    @{ Name = "acd-05"; Port = 8087; Dir = "services\academic-services\timetable-management-service"; Main = "com.campx.academic.timetable.TimetableManagementApplication" },
    @{ Name = "acd-06"; Port = 8088; Dir = "services\attendance-services\attendance-service";         Main = "com.campx.academic.attendance.AttendanceManagementApplication" },
    @{ Name = "acd-10"; Port = 8092; Dir = "services\analytics-services\analytics-service";           Main = "com.campx.academic.analytics.AnalyticsApplication" },
    @{ Name = "stu-01"; Port = 8093; Dir = "services\student-services\student-service";               Main = "com.campx.student.StudentApplication" },
    @{ Name = "stm-02"; Port = 8094; Dir = "services\student-services\admissions-service";            Main = "com.campx.admissions.AdmissionsApplication" },
    @{ Name = "stm-03"; Port = 8095; Dir = "services\student-services\enrollment-service";            Main = "com.campx.enrollment.EnrollmentApplication" },
    @{ Name = "stm-04"; Port = 8096; Dir = "services\student-services\lifecycle-service";             Main = "com.campx.lifecycle.LifecycleApplication" },
    @{ Name = "stm-05"; Port = 8097; Dir = "services\student-services\guardian-service";              Main = "com.campx.guardian.GuardianApplication" },
    @{ Name = "stm-06"; Port = 8098; Dir = "services\student-services\document-service";              Main = "com.campx.document.DocumentApplication" }
)

Write-Host "--- Launching 13 Persona Microservices (-Xms32m -Xmx96m) ---" -ForegroundColor Cyan
$spawnedProcs = @()
foreach ($svc in $services) {
    $workingDir = Join-Path $baseDir $svc.Dir
    $outLog = "$logDir\$($svc.Name).log"
    $errLog = "$logDir\$($svc.Name).err"
    
    $argsList = @(
        "-Xms32m",
        "-Xmx96m",
        "-Dcampx.persistence.mode=postgres",
        "-Dcampx.internal.auth.enabled=false",
        "-cp",
        "target\classes;$commonJars",
        $svc.Main,
        $svc.Port
    )
    
    $p = Start-Process -FilePath "java" -ArgumentList $argsList -WorkingDirectory $workingDir -RedirectStandardOutput $outLog -RedirectStandardError $errLog -WindowStyle Hidden -PassThru
    $spawnedProcs += $p
    Write-Host "Started $($svc.Name) (PID $($p.Id)) on port $($svc.Port)..."
}

# 3. Launch API Gateway
Write-Host "`n--- Launching API Gateway on port 8080 ---" -ForegroundColor Cyan
$gwDir = "$baseDir\api-gateway"
$gwCpTxt = Get-Content "$gwDir\classpath.txt" -Raw
$gwCp = "target\classes;$gwCpTxt"
$gwArgs = @(
    "-Xms64m",
    "-Xmx192m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=false",
    "-cp",
    $gwCp,
    "com.campx.gateway.ApiGatewayApplication",
    "8080"
)
$gwProc = Start-Process -FilePath "java" -ArgumentList $gwArgs -WorkingDirectory $gwDir -RedirectStandardOutput "$logDir\api-gateway.log" -RedirectStandardError "$logDir\api-gateway.err" -WindowStyle Hidden -PassThru
$spawnedProcs += $gwProc
Write-Host "Started API Gateway (PID $($gwProc.Id)) on port 8080..."

# 4. Launch Next.js Development Server
Write-Host "`n--- Launching Next.js Development Server on port 3000 ---" -ForegroundColor Cyan
$feDir = "$baseDir\frontend"
$feProc = Start-Process -FilePath "node.exe" -ArgumentList "node_modules\next\dist\bin\next dev -p 3000" -WorkingDirectory $feDir -RedirectStandardOutput "$logDir\frontend.log" -RedirectStandardError "$logDir\frontend.err" -WindowStyle Hidden -PassThru
$spawnedProcs += $feProc
Write-Host "Started Next.js (PID $($feProc.Id)) on port 3000..."

# 5. Wait for Next.js and Gateway to be ready
Write-Host "`nWaiting for Next.js http://127.0.0.1:3000 and Gateway http://127.0.0.1:8080..." -ForegroundColor Yellow
$feReady = $false
$gwReady = $false
$timeout = [DateTime]::Now.AddSeconds(60)

while ([DateTime]::Now -lt $timeout -and (-not $feReady -or -not $gwReady)) {
    Start-Sleep -Seconds 2
    if (-not $feReady) {
        try {
            $resp = Invoke-WebRequest -Uri "http://127.0.0.1:3000/login" -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp -and $resp.StatusCode -eq 200) {
                $feReady = $true
                Write-Host "Next.js is READY on port 3000!" -ForegroundColor Green
            }
        } catch {}
    }
    if (-not $gwReady) {
        try {
            $gwConn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
            if ($gwConn) {
                $gwReady = $true
                Write-Host "API Gateway is READY on port 8080!" -ForegroundColor Green
            }
        } catch {}
    }
}

if (-not $feReady -or -not $gwReady) {
    Write-Host "FATAL: Stack failed to initialize within timeout! feReady=$feReady, gwReady=$gwReady" -ForegroundColor Red
    exit 1
}

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "  CAMPXSYNC FULL LOCAL STACK IS RUNNING AND READY!      " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host "Frontend Application: http://localhost:3000" -ForegroundColor White
Write-Host "API Gateway URL:      http://localhost:8080" -ForegroundColor White
Write-Host "13 Microservices:     ports 8081, 8082, 8085-8088, 8092-8098" -ForegroundColor White
Write-Host "`nReady for manual testing in your browser." -ForegroundColor Cyan

# Keep process alive to sustain the local services
while ($true) {
    Start-Sleep -Seconds 60
}

