# STM-02 Phase B — B1 Step Skipped

**Status:** SKIPPED PER PROTOCOL  
**Gate Status:** `GATE = FAIL` in B0  
**Condition:** B1 is approved only if `GATE = PASS`.  

### Reason for Skip:
Step 1 of B0 identified a hard SQL DELETE statement in `services/student-services/admissions-service/src/main/java/com/campx/admissions/repository/PostgresApplicationDocumentRepository.java`, line 116:
```java
String sql = "DELETE FROM stu.application_documents WHERE tenant_id = ? AND id = ?";
```
Because `GATE = FAIL`, the REVOKE statements for B1 were NOT executed. Proceeded directly to B2 as instructed.

