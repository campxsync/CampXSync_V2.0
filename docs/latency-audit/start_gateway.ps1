$gwDir = "D:\CampXSync\CampXSync_V2.0\api-gateway"
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
Start-Process -FilePath "java" -ArgumentList $gwArgs -WorkingDirectory $gwDir -RedirectStandardOutput "D:\CampXSync\CampXSync_V2.0\docs\latency-audit\logs\api-gateway.log" -RedirectStandardError "D:\CampXSync\CampXSync_V2.0\docs\latency-audit\logs\api-gateway.err" -WindowStyle Hidden
Start-Sleep -Seconds 3
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Format-Table -AutoSize

