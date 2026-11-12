$baseDir = "D:\CampXSync\CampXSync_V2.0"
$commonJars = "C:\Users\niran\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar;C:\Users\niran\.m2\repository\org\checkerframework\checker-qual\3.42.0\checker-qual-3.42.0.jar;C:\Users\niran\.m2\repository\com\zaxxer\HikariCP\4.0.3\HikariCP-4.0.3.jar;C:\Users\niran\.m2\repository\org\slf4j\slf4j-api\1.7.30\slf4j-api-1.7.30.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;$baseDir\logger\target\classes"

$argsList = @(
    "-Xms32m",
    "-Xmx96m",
    "-Dcampx.persistence.mode=postgres",
    "-Dcampx.internal.auth.enabled=false",
    "-cp",
    "target\classes;$commonJars",
    "com.campx.student.StudentApplication",
    "8093"
)

$p = Start-Process -FilePath "java" -ArgumentList $argsList -WorkingDirectory "$baseDir\services\student-services\student-service" -RedirectStandardOutput "$baseDir\docs\latency-audit\logs\stu-01.log" -RedirectStandardError "$baseDir\docs\latency-audit\logs\stu-01.err" -WindowStyle Hidden -PassThru
Write-Host "Started STU-01 with PID $($p.Id)"

while ($true) {
    Start-Sleep -Seconds 60
}

