# scripts/dev/check_stack.ps1
$ErrorActionPreference = "Continue"

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "       CampXSync Service Stack Status Check      " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# 1. Check RAM
try {
    $freeMem = (Get-CimInstance Win32_OperatingSystem -ErrorAction SilentlyContinue).FreePhysicalMemory
    if (-not $freeMem) {
        $freeMem = (Get-WmiObject Win32_OperatingSystem -ErrorAction SilentlyContinue).FreePhysicalMemory
    }
    if ($freeMem) {
        $freeRamGb = [math]::Round($freeMem / 1048576, 2)
        if ($freeRamGb -lt 3.0) {
            Write-Host "WARNING: Free RAM is $freeRamGb GB (< 3 GB threshold)." -ForegroundColor Yellow
        } else {
            Write-Host "Memory Check: Free RAM = $freeRamGb GB (OK)" -ForegroundColor Green
        }
    } else {
        Write-Host "Memory Check: Memory metrics unavailable." -ForegroundColor DarkGray
    }
} catch {
    Write-Host "Memory Check: Could not read memory metrics." -ForegroundColor DarkGray
}

# 2. Check Services
$services = @(
    @{ Name = "Frontend (Next.js)"; Port = 3000; HealthUrl = "http://127.0.0.1:3000/login" },
    @{ Name = "API Gateway"; Port = 8080; HealthUrl = "http://127.0.0.1:8080/actuator/health" },
    @{ Name = "Document Service (STM-06)"; Port = 8098; HealthUrl = "http://127.0.0.1:8098/health" },
    @{ Name = "Lifecycle Service (STM-04)"; Port = 8096; HealthUrl = "http://127.0.0.1:8096/health" },
    @{ Name = "Subject Service (ACD-03)"; Port = 8085; HealthUrl = "http://127.0.0.1:8085/metrics" },
    @{ Name = "Attendance Service (ACD-06)"; Port = 8088; HealthUrl = "http://127.0.0.1:8088/metrics" },
    @{ Name = "Analytics Service (ACD-10)"; Port = 8092; HealthUrl = "http://127.0.0.1:8092/metrics" }
)

$allHealthy = $true

foreach ($s in $services) {
    $conn = Get-NetTCPConnection -LocalPort $s.Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conn) {
        Write-Host "[-] $($s.Name) on port $($s.Port): NOT LISTENING" -ForegroundColor Red
        $allHealthy = $false
        continue
    }

    try {
        $resp = Invoke-WebRequest -Uri $s.HealthUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
            Write-Host "[+] $($s.Name) on port $($s.Port): HEALTHY ($($resp.StatusCode))" -ForegroundColor Green
        } else {
            Write-Host "[!] $($s.Name) on port $($s.Port): RESPONDED WITH $($resp.StatusCode)" -ForegroundColor Yellow
            $allHealthy = $false
        }
    } catch {
        Write-Host "[-] $($s.Name) on port $($s.Port): LISTENING but Health check failed ($($_.Exception.Message))" -ForegroundColor Red
        $allHealthy = $false
    }
}

Write-Host "-------------------------------------------------" -ForegroundColor Cyan
Write-Host "Application URLs:" -ForegroundColor White
Write-Host "  College Documents Desk:  http://localhost:3000/college/documents" -ForegroundColor White
Write-Host "  Login Portal:            http://localhost:3000/login" -ForegroundColor White
Write-Host "  API Gateway:             http://localhost:8080" -ForegroundColor White
Write-Host "  Document Microservice:   http://localhost:8098" -ForegroundColor White

Write-Host "`nTest Accounts (.env.audit):" -ForegroundColor White
Write-Host "  College Admin:           campxsync@gmail.com (Role: COLLEGE_ADMIN)" -ForegroundColor White
Write-Host "  Student 1:               student1.campx@gmail.com (Role: STUDENT)" -ForegroundColor White
Write-Host "  Faculty:                 faculty.campx@gmail.com (Role: FACULTY)" -ForegroundColor White
Write-Host "  Platform Admin:          platformadmin.campx@gmail.com (Role: PLATFORM_ADMIN)" -ForegroundColor White
Write-Host "=================================================" -ForegroundColor Cyan

if (-not $allHealthy) {
    exit 1
}

