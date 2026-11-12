# scripts/dev/stop_stack.ps1
$ErrorActionPreference = "SilentlyContinue"

Write-Host "Stopping CampXSync stack services..." -ForegroundColor Yellow

$targetPorts = @(8080, 8098, 3000) + (8081..8097)

foreach ($port in $targetPorts) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if ($p) {
                Write-Host "Killing process $($p.ProcessName) (PID $procId) on port $port" -ForegroundColor DarkGray
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

Start-Sleep -Seconds 1
Write-Host "CampXSync services stopped." -ForegroundColor Green

