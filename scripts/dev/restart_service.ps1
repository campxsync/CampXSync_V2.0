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
    Write-Host "Waiting for $url to become healthy..." -ForegroundColor Gray
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        try {
            $req = [System.Net.WebRequest]::Create($url)
            $req.Timeout = 2000
            $req.Proxy = $null
            $resp = $req.GetResponse()
            $code = [int]$resp.StatusCode
            $resp.Close()
            if ($code -ge 200 -and $code -lt 400) {
                Write-Host "[+] Service is HEALTHY on $url (HTTP $code)" -ForegroundColor Green
                return $true
            }
        } catch {
            if ($_.Exception.Response) {
                $code = [int]$_.Exception.Response.StatusCode
                if ($code -ge 200 -and $code -lt 400) {
                    Write-Host "[+] Service is HEALTHY on $url (HTTP $code)" -ForegroundColor Green
                    return $true
                }
            }
        }
        Start-Sleep -Milliseconds 600
    }
    Write-Host "[-] Timed out waiting for $url" -ForegroundColor Red
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
            "-Dcampx.internal.auth.enabled=false",
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
            "-Dcampx.internal.auth.enabled=false",
            "-Dcampx.gateway.connect.timeout.local.ms=3000",
            "-cp",
            "target\classes;$cp",
            "com.campx.gateway.ApiGatewayApplication",
            "8080"
        ) -WorkingDirectory $gwDir -RedirectStandardOutput "$baseDir\logs\api-gateway.log" -RedirectStandardError "$baseDir\logs\api-gateway.err" -WindowStyle Minimized -PassThru
        Write-Host "Started api-gateway PID $($gwProc.Id)" -ForegroundColor DarkGray

        Wait-HealthCheck "http://127.0.0.1:8080/api/v1/documents/health"
    }

    "frontend" {
        Write-Host "1. Stopping port 3000 listener..." -ForegroundColor Cyan
        Stop-PortListener 3000
        Start-Sleep -Milliseconds 500

        Write-Host "2. Launching Next.js frontend on port 3000..." -ForegroundColor Cyan
        $frontendDir = "$baseDir\frontend"
        $pinfo = New-Object System.Diagnostics.ProcessStartInfo
        $pinfo.FileName = "cmd.exe"
        $pinfo.Arguments = "/c set PORT=3000&& set NEXT_PUBLIC_ENABLE_TEST_SESSION=true&& npm run dev"
        $pinfo.WorkingDirectory = $frontendDir
        $pinfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Minimized
        [System.Diagnostics.Process]::Start($pinfo) | Out-Null

        Wait-HealthCheck "http://localhost:3000/login" 30
    }

    default {
        Write-Host "Unknown service: $Service. Supported services: document-service, api-gateway, frontend" -ForegroundColor Red
        exit 1
    }
}
