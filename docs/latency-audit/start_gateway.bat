@echo off
cd /d "D:\CampXSync\CampXSync_V2.0\api-gateway"
set /p CP=<classpath.txt
set FULL_CP=target\classes;%CP%
java -Xms64m -Xmx192m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "%FULL_CP%" com.campx.gateway.ApiGatewayApplication 8080

