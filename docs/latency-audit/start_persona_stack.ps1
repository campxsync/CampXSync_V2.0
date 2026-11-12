$ErrorActionPreference = "Continue"

$baseDir = "D:\CampXSync\CampXSync_V2.0"
$logDir = "$baseDir\docs\latency-audit\logs"

$commonJars = "C:\Users\niran\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar;C:\Users\niran\.m2\repository\org\checkerframework\checker-qual\3.42.0\checker-qual-3.42.0.jar;C:\Users\niran\.m2\repository\com\zaxxer\HikariCP\4.0.3\HikariCP-4.0.3.jar;C:\Users\niran\.m2\repository\org\slf4j\slf4j-api\1.7.30\slf4j-api-1.7.30.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;$baseDir\logger\target\classes"

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

Write-Host "--- Launching 13 Persona Domain Microservices (-Xms32m -Xmx96m) ---" -ForegroundColor Cyan
$procs = @()
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
    $procs += $p
    Write-Host "Started $($svc.Name) (PID $($p.Id)) on port $($svc.Port)..."
}

# Wait for domain services to start listening
Write-Host "Waiting for domain services to listen..." -ForegroundColor Yellow
$timeout = [DateTime]::Now.AddSeconds(30)
$allListening = $false
while ([DateTime]::Now -lt $timeout -and -not $allListening) {
    Start-Sleep -Seconds 2
    $allListening = $true
    foreach ($svc in $services) {
        $conn = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
        if (-not $conn) {
            $allListening = $false
            break
        }
    }
}

foreach ($svc in $services) {
    $conn = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Write-Host "[$($svc.Port)] $($svc.Name) is LISTENING" -ForegroundColor Green
    } else {
        Write-Host "[$($svc.Port)] $($svc.Name) FAILED TO LISTEN" -ForegroundColor Red
    }
}

Write-Host "`nAll 13 persona microservices initialized. Keeping supervisor daemon alive..." -ForegroundColor Cyan
# Keep supervisor process alive indefinitely
while ($true) {
    Start-Sleep -Seconds 60
}
