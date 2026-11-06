$env:ENABLE_TEST_SESSION = "true"
$env:NODE_ENV = "development"
$proc = Start-Process -FilePath "npm.cmd" -ArgumentList "run","dev" -WorkingDirectory "frontend" -PassThru -NoNewWindow
try {
  $ready = $false
  for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
      $r = Invoke-WebRequest -Uri "http://localhost:3000/login" -UseBasicParsing -TimeoutSec 2
      if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
  }
  if (-not $ready) { throw "Server did not start in time" }
  node scripts/verify_role_routing.mjs
} finally {
  Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
}

