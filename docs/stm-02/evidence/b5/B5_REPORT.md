# STM-02 Phase B — B5 Build and Test Reruns Report

**Execution Timestamp:** 2026-11-02T13:17:00Z  
**Branch:** `feature/java-backend-integration`  

---

## 1. Java Microservice Test Accounting (`admissions-service`)

**Command Executed:**
```bash
mvn test -pl services/student-services/admissions-service "-Dtest=!PostgresAdmissionsRepositoryLiveTest"
```
**Exit Code:** `0` (BUILD SUCCESS)  
**Total Tests Accounting:**
- **Total Tests Defined:** 50
- **Executed / Run:** 49
- **Passed:** 49
- **Failed:** 0
- **Errors:** 0
- **Skipped:** 1 (`PostgresAdmissionsRepositoryLiveTest` cleanly skipped per instruction to ensure no unapproved live DB writes occur)

### Suite-Level Breakdown:
1. `AdmissionsExceptionHandlerTest`: 9 / 9 PASS (0s)
2. `AdmissionsIntelligenceIntegrationTest`: 6 / 6 PASS (0.015s)
3. `AdmissionsLoggingServiceTest`: 5 / 5 PASS (0.012s)
4. `AdmissionsStateEngineTest`: 5 / 5 PASS (0.003s)
5. `ApplicationControllerTest`: 5 / 5 PASS (0.025s)
6. `ApplicationSecurityAndTenantIsolationTest`: 6 / 6 PASS (0.146s)
7. `EligibilityEvaluationEngineTest`: 4 / 4 PASS (0.001s)
8. `HandoverOrchestrationServiceTest`: 3 / 3 PASS (0.003s)
9. `PostgresAdmissionsRepositoryUnitTest`: 3 / 3 PASS (0.000s)
10. `ReviewAndDecisionServiceTest`: 3 / 3 PASS (0.001s)
11. `PostgresAdmissionsRepositoryLiveTest`: **SKIPPED** (Live DB execution)

### API Gateway Route Verification:
**Command Executed:**
```bash
mvn test -pl api-gateway "-Dtest=AdmissionsGatewayRouteTest"
```
**Exit Code:** `0` (BUILD SUCCESS)  
**Tests Run:** 1, **Passed:** 1, **Failed:** 0, **Skipped:** 0

---

## 2. Frontend Static Typecheck (`tsc --noEmit`)

**Command Executed:**
```bash
cd frontend && npx tsc --noEmit
```
**Exit Code:** `0`  
**Output:** Clean; zero TypeScript compilation or typing errors across all Next.js admissions pages, components, and API routes.

---

## 3. Frontend Next.js Production Build (`npm run build`)

**Command Executed:**
```bash
cd frontend && npm run build
```
**Exit Code:** `0`  
**Output Summary:**
- Compiled successfully in 9.4s.
- 57 total routes compiled and emitted (24 static pages, 33 server-rendered dynamic API/dashboard routes).
- Zero warnings or webpack compilation failures.

---

## 4. Saved Raw Artifacts
- Maven test log: [`docs/stm-02/evidence/b5/mvn_test_output.txt`](file:///d:/CampXSync/CampXSync_V2.0/docs/stm-02/evidence/b5/mvn_test_output.txt)
- TypeScript check log: [`docs/stm-02/evidence/b5/tsc_output.txt`](file:///d:/CampXSync/CampXSync_V2.0/docs/stm-02/evidence/b5/tsc_output.txt)
- Next.js build log: [`docs/stm-02/evidence/b5/npm_build_output.txt`](file:///d:/CampXSync/CampXSync_V2.0/docs/stm-02/evidence/b5/npm_build_output.txt)

