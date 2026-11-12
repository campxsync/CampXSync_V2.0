@echo off
set BASE=D:\CampXSync\CampXSync_V2.0
set COMMON=C:\Users\niran\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar;C:\Users\niran\.m2\repository\org\checkerframework\checker-qual\3.42.0\checker-qual-3.42.0.jar;C:\Users\niran\.m2\repository\com\zaxxer\HikariCP\4.0.3\HikariCP-4.0.3.jar;C:\Users\niran\.m2\repository\org\slf4j\slf4j-api\1.7.30\slf4j-api-1.7.30.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;%BASE%\logger\target\classes

echo Starting ADM-01...
cd /d "%BASE%\services\administration-services\institute-admin-service"
start "adm-01" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.admin.institute.InstituteAdminApplication 8081 > "%BASE%\docs\latency-audit\logs\adm-01.log" 2>&1

echo Starting ADM-02...
cd /d "%BASE%\services\administration-services\college-admin-service"
start "adm-02" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.admin.college.CollegeAdminApplication 8082 > "%BASE%\docs\latency-audit\logs\adm-02.log" 2>&1

echo Starting ACD-03...
cd /d "%BASE%\services\academic-services\subject-management-service"
start "acd-03" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.academic.subject.SubjectManagementApplication 8085 > "%BASE%\docs\latency-audit\logs\acd-03.log" 2>&1

echo Starting ACD-04...
cd /d "%BASE%\services\academic-services\batch-management-service"
start "acd-04" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.academic.batch.BatchManagementApplication 8086 > "%BASE%\docs\latency-audit\logs\acd-04.log" 2>&1

echo Starting ACD-05...
cd /d "%BASE%\services\academic-services\timetable-management-service"
start "acd-05" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.academic.timetable.TimetableManagementApplication 8087 > "%BASE%\docs\latency-audit\logs\acd-05.log" 2>&1

echo Starting ACD-06...
cd /d "%BASE%\services\attendance-services\attendance-service"
start "acd-06" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.academic.attendance.AttendanceManagementApplication 8088 > "%BASE%\docs\latency-audit\logs\acd-06.log" 2>&1

echo Starting ACD-10...
cd /d "%BASE%\services\analytics-services\analytics-service"
start "acd-10" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.academic.analytics.AnalyticsApplication 8092 > "%BASE%\docs\latency-audit\logs\acd-10.log" 2>&1

echo Starting STU-01...
cd /d "%BASE%\services\student-services\student-service"
start "stu-01" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.student.StudentApplication 8093 > "%BASE%\docs\latency-audit\logs\stu-01.log" 2>&1

echo Starting STM-02...
cd /d "%BASE%\services\student-services\admissions-service"
start "stm-02" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.admissions.AdmissionsApplication 8094 > "%BASE%\docs\latency-audit\logs\stm-02.log" 2>&1

echo Starting STM-03...
cd /d "%BASE%\services\student-services\enrollment-service"
start "stm-03" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.enrollment.EnrollmentApplication 8095 > "%BASE%\docs\latency-audit\logs\stm-03.log" 2>&1

echo Starting STM-04...
cd /d "%BASE%\services\student-services\lifecycle-service"
start "stm-04" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.lifecycle.LifecycleApplication 8096 > "%BASE%\docs\latency-audit\logs\stm-04.log" 2>&1

echo Starting STM-05...
cd /d "%BASE%\services\student-services\guardian-service"
start "stm-05" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.guardian.GuardianApplication 8097 > "%BASE%\docs\latency-audit\logs\stm-05.log" 2>&1

echo Starting STM-06...
cd /d "%BASE%\services\student-services\document-service"
start "stm-06" /b java -Xms32m -Xmx96m -Dcampx.persistence.mode=postgres -Dcampx.internal.auth.enabled=false -cp "target\classes;%COMMON%" com.campx.document.DocumentApplication 8098 > "%BASE%\docs\latency-audit\logs\stm-06.log" 2>&1

