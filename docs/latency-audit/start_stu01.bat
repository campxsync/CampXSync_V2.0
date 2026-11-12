@echo off
set BASE=D:\CampXSync\CampXSync_V2.0
set COMMON=C:\Users\niran\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar;C:\Users\niran\.m2\repository\org\checkerframework\checker-qual\3.42.0\checker-qual-3.42.0.jar;C:\Users\niran\.m2\repository\com\zaxxer\HikariCP\4.0.3\HikariCP-4.0.3.jar;C:\Users\niran\.m2\repository\org\slf4j\slf4j-api\1.7.30\slf4j-api-1.7.30.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;%BASE%\logger\target\classes

cd /d "%BASE%\services\student-services\student-service"
start "stu-01" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.student.StudentApplication 8093 > "%BASE%\docs\latency-audit\logs\stu-01.log" 2>&1

