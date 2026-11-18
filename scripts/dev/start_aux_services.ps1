# scripts/dev/start_aux_services.ps1
$ErrorActionPreference = "Continue"
$baseDir = "D:\CampXSync\CampXSync_V2.0"
$logDir = "$baseDir\docs\latency-audit\logs"
$env:Path = "C:\Java\jdk\bin;C:\Users\niran\Downloads\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin;" + $env:Path
$env:CAMPX_PERSISTENCE_MODE = "postgres"
$env:CAMPX_INTERNAL_AUTH_ENABLED = "true"
$env:CAMPX_IAM_DELEGATION_BASE_URL = "http://127.0.0.1:8081"

# 1. Start enrollment (8095)
$enrDir = "$baseDir\services\student-services\enrollment-service"
$enrCp = (Get-Content "$enrDir\classpath.txt" -Raw).Trim()
$enrProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m", "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp", "target\classes;$enrCp",
    "com.campx.enrollment.EnrollmentApplication", "8095"
) -WorkingDirectory $enrDir -RedirectStandardOutput "$logDir\stm-03.log" -RedirectStandardError "$logDir\stm-03.err" -WindowStyle Minimized -PassThru

# 2. Start guardian (8097)
$grdDir = "$baseDir\services\student-services\guardian-service"
$grdCp = (Get-Content "$grdDir\classpath.txt" -Raw).Trim()
$grdProc = Start-Process -FilePath "java" -ArgumentList @(
    "-Xms32m", "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=true",
    "-cp", "target\classes;$grdCp",
    "com.campx.guardian.GuardianApplication", "8097"
) -WorkingDirectory $grdDir -RedirectStandardOutput "$logDir\stm-05.log" -RedirectStandardError "$logDir\stm-05.err" -WindowStyle Minimized -PassThru

Write-Host "Launched Enrollment PID $($enrProc.Id) on 8095, Guardian PID $($grdProc.Id) on 8097"

while ($true) {
    Start-Sleep -Seconds 10
}
