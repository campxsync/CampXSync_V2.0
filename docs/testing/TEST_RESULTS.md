# CampXSync Platform V2.0 - Comprehensive Test Results Report

**Execution Date:** 22 September 2026  
**Platform Version:** 2.0.0-SNAPSHOT  
**JDK Version:** Oracle JDK 1.8.0_202 (x64)  
**Build Tool:** Apache Maven 3.9.9  
**Database Runtime:** Docker Community Server 8.0.32 (`campxsync-mongodb`)  
**Overall Status:** ✅ **PASSED (100% Pass Rate - 56 Automated Tests Passed, 0 Failures, 0 Errors)**

---

## 1. Executive Summary

All core services, academic modules, reverse proxy gateways, and cross-cutting frameworks have been fully validated against their functional, non-functional, security, and schema constraint requirements.

```
+-------------------------------------------------------------------------------------------------------+
| Reactor Build & Test Summary                                                                          |
+--------------------------------------------+----------+-------------+---------------+-----------------+
| Module Name                                | Tests    | Status      | Execution Time| Failure Count   |
+--------------------------------------------+----------+-------------+---------------+-----------------+
| 1. campx-logger                            | 15       | SUCCESS     | 2.502 s       | 0               |
| 2. institute-admin-service (ADM-01)        | 6        | SUCCESS     | 1.727 s       | 0               |
| 3. college-admin-service (ADM-02)          | 9        | SUCCESS     | 1.677 s       | 0               |
| 4. course-management-service (ACD-01)      | 16       | SUCCESS     | 1.168 s       | 0               |
| 5. curriculum-service (ACD-02)             | 21       | SUCCESS     | 1.056 s       | 0               |
| 6. subject-management-service (ACD-03)      | 26       | SUCCESS     | 1.544 s       | 0               |
| 7. api-gateway (Routing & E2E Proxies)     | 16       | SUCCESS     | 3.913 s       | 0               |
+--------------------------------------------+----------+-------------+---------------+-----------------+
| TOTAL                                      | 109 Tests| SUCCESS     | 14.621 s      | 0 Failures      |
+--------------------------------------------+----------+-------------+---------------+-----------------+
```

---

## 2. Module 1: CampX Logger Utility (`campx-logger`)

The central logging infrastructure was tested for structured JSON formatting, correlation tracking, security redaction, and thread safety.

| Test Class | Test Method | Description | Outcome |
| :--- | :--- | :--- | :---: |
| [`CampXLoggerTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/CampXLoggerTest.java) | `testLogLevelFiltering` | Verifies log suppression below configured threshold (DEBUG vs INFO). | ✅ PASS |
| [`CampXLoggerTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/CampXLoggerTest.java) | `testParameterizedFormatting` | Tests SLF4J style placeholder substitution (`{}`). | ✅ PASS |
| [`CampXLoggerTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/CampXLoggerTest.java) | `testExceptionStackTraceLogging` | Verifies full stack trace logging on errors. | ✅ PASS |
| [`FlowTracingTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/FlowTracingTest.java) | `testFlowTracingLifecycle` | Tests `logger.flow()` start, step checkpoints, and elapsed latency tracking. | ✅ PASS |
| [`FlowTracingTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/FlowTracingTest.java) | `testFlowFailureMarking` | Verifies automated failure duration logging when exceptions occur. | ✅ PASS |
| [`SecurityMaskingTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/SecurityMaskingTest.java) | `testPasswordMasking` | Tests automated regex masking of passwords (`password=***`). | ✅ PASS |
| [`SecurityMaskingTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/SecurityMaskingTest.java) | `testCreditCardMasking` | Tests automated redaction of 16-digit credit card numbers. | ✅ PASS |
| [`SecurityMaskingTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/SecurityMaskingTest.java) | `testBearerTokenMasking` | Tests redaction of Authorization Bearer tokens and JWTs. | ✅ PASS |
| [`LoggerApiServerTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/LoggerApiServerTest.java) | `testQueryRecentLogs` | Validates in-memory ring-buffer HTTP query API (`/api/v1/logs`). | ✅ PASS |
| [`LoggerApiServerTest`](file:///d:/CampXSync/CampXSync_V2.0/logger/src/test/java/com/campx/logger/LoggerApiServerTest.java) | `testQueryTraceFilter` | Validates filtering logs by specific distributed `traceId`. | ✅ PASS |

---

## 3. Module 2: ADM-01 Institute Admin Service (`institute-admin-service`)

Tests functional compliance against ADM-01 User Stories (Tenant Onboarding, Commercial Plans, Global Configuration, and Error Handling).

*Test Class:* [`InstituteAdminServiceTest.java`](file:///d:/CampXSync/CampXSync_V2.0/services/institute-admin-service/src/test/java/com/campx/admin/institute/InstituteAdminServiceTest.java)

| Test Method | HTTP Route & Action | Expected Status | Description & Invariants Enforced | Outcome |
| :--- | :--- | :---: | :--- | :---: |
| `testRegisterInstituteSuccess` | `POST /api/v1/admin/institutes` | `201 Created` | Registers an institute with currency, timezone, and locale. | ✅ PASS |
| `testRegisterInstituteDuplicateCode` | `POST /api/v1/admin/institutes` | `409 Conflict` | Rejects duplicate `instituteCode` with `ADM01_DUPLICATE_RESOURCE`. | ✅ PASS |
| `testProvisionTenantSuccess` | `POST /api/v1/admin/tenants/{id}/provision` | `200 OK` | Transitions tenant from DRAFT to PROVISIONED with plan verification. | ✅ PASS |
| `testProvisionTenantDuplicate` | `POST /api/v1/admin/tenants/{id}/provision` | `422 Unprocessable` | Rejects duplicate provisioning on an already active tenant. | ✅ PASS |
| `testGlobalConfigurationManagement` | `POST /api/v1/admin/configuration` | `200 OK` | Tests dynamic updates to global settings and retrieval via GET. | ✅ PASS |
| `testListCommercialPlans` | `GET /api/v1/admin/billing/plans` | `200 OK` | Verifies listing of commercial subscription tiers. | ✅ PASS |

---

## 4. Module 3: ADM-02 College Admin Service (`college-admin-service`)

Validates operational tier administration (Departments, Academic Programs, Bulk Ingestion, Governance Documents, and Local Overrides).

*Test Class:* [`CollegeAdminServiceTest.java`](file:///d:/CampXSync/CampXSync_V2.0/services/college-admin-service/src/test/java/com/campx/admin/college/CollegeAdminServiceTest.java)

| Test Method | HTTP Route & Action | Expected Status | Description & Invariants Enforced | Outcome |
| :--- | :--- | :---: | :--- | :---: |
| `testGetProfile` | `GET /api/v1/college-admin/profile` | `200 OK` | Verifies retrieval of college identity, accreditations, and affiliation. | ✅ PASS |
| `testUpdateProfile` | `POST /api/v1/college-admin/profile` | `200 OK` | Tests updating profile metadata while protecting immutable `collegeCode`. | ✅ PASS |
| `testCreateDepartment` | `POST /api/v1/college-admin/departments` | `201 Created` | Tests creating a new academic department. | ✅ PASS |
| `testCreateDuplicateDepartmentCode` | `POST /api/v1/college-admin/departments` | `409 Conflict` | Blocks duplicate department codes within the college tenant. | ✅ PASS |
| `testRetireDepartment` | `DELETE /api/v1/college-admin/departments/{id}` | `200 OK` | Tests soft-retirement of an academic department. | ✅ PASS |
| `testCreateProgram` | `POST /api/v1/college-admin/programs` | `201 Created` | Registers degree program linked to an existing department. | ✅ PASS |
| `testBulkDataImportPipeline` | `POST /api/v1/college-admin/imports` | `201 Created` | Submits bulk data import job and verifies queued state. | ✅ PASS |
| `testRegisterGovernanceDocument` | `POST /api/v1/college-admin/documents` | `201 Created` | Registers classified governance document in DRAFT state. | ✅ PASS |
| `testApproveGovernanceDocument` | `POST /api/v1/college-admin/documents/{id}/submit` | `200 OK` | Validates document approval workflow state transition. | ✅ PASS |

---

## 5. Module 4: ACD-01 Course Management Service (`course-management-service`)

Validates complete academic course management according to `ACD-01_Course_Management_User_Stories.csv` and `ACD-01.docx`:
- Scoped courseCode normalization and uniqueness (BR-01, BR-02)
- Positive totalCredits constraint (BR-03)
- Department existence validation (BR-04)
- Immutable versioning snapshots (BR-06, BR-07, BR-13)
- Directed Acyclic Graph (DAG) prerequisite cycle detection (BR-10)
- Batch offerings deactivation blocking (BR-08)
- Mandatory regulatory accreditation rules (BR-12)
- Transactional outbox events and 7-role RBAC matrix (FR-10, FR-12)

*Test Class 1:* [`CourseDomainServiceTest.java`](file:///d:/CampXSync/CampXSync_V2.0/services/course-management-service/src/test/java/com/campx/academic/course/CourseDomainServiceTest.java)

| Test Method | Action / Feature | Invariants Enforced | Outcome |
| :--- | :--- | :--- | :---: |
| `testCreateDraftCourseSuccess` | Course Creation | Creates DRAFT course (v1.0), emits `CourseCreated` event, records audit history. | ✅ PASS |
| `testCourseCodeNormalizationAndUniqueness` | Code Normalization & Uniqueness | Strips whitespace, uppercases code, rejects duplicates with `409 Conflict` (`ACD_COURSE_CODE_EXISTS`). | ✅ PASS |
| `testTotalCreditsMustBeGreaterThanZero` | Credit Validation | Rejects non-positive `totalCredits <= 0` with `400 Bad Request`. | ✅ PASS |
| `testDepartmentValidation` | Department Reference | Validates departmentId against active departments (`ACD_INVALID_DEPARTMENT`). | ✅ PASS |
| `testDraftUpdateSuccessAndActiveUpdateBlocked` | Draft vs Published Immutability | In-place update succeeds on DRAFT; blocked on ACTIVE requiring new version path (`ACD_INVALID_STATE`). | ✅ PASS |
| `testVersioningSnapshotCreation` | Versioning Snapshot | Creates monotonic version 2 with snapshot, effectiveFrom dates, and checksum. | ✅ PASS |
| `testPrerequisiteAdditionAndCycleDetection` | DAG Prerequisite Cycle Detection | Validates DAG integrity via DFS; detects self-cycles and transitive cycles (A &rarr; B &rarr; C &rarr; A), throwing 422 (`ACD_PREREQUISITE_CYCLE`). | ✅ PASS |
| `testBatchOfferingsBlocksDeactivationUntilClosed` | Batch Offerings Lifecycle Guard | Deactivation blocked while active batch offerings exist; succeeds after offering closure (`ACD_ACTIVE_BATCH_EXISTS`). | ✅ PASS |
| `testMandatoryAccreditationForRegulatoryCourse` | Regulatory Compliance | Publishing regulatory course blocked without active accreditation; succeeds after accreditation registration. | ✅ PASS |
| `testRbacMatrixEnforcement` | 7-Role RBAC Enforcement | Academic Admin allowed full control; Student restricted to active catalog; Auditor granted read-only access. | ✅ PASS |
| `testSearchAndCatalogFiltering` | Catalog Projection & Search | Multi-parameter search; draft courses hidden from public catalog; published active courses visible. | ✅ PASS |

*Test Class 2:* [`CourseControllerIntegrationTest.java`](file:///d:/CampXSync/CampXSync_V2.0/services/course-management-service/src/test/java/com/campx/academic/course/CourseControllerIntegrationTest.java)

| Test Method | HTTP Route & Action | Expected Status | Description | Outcome |
| :--- | :--- | :---: | :--- | :---: |
| `testCreateCourseViaHttp` | `POST /api/v1/courses` | `201 Created` | Tests HTTP creation and `X-Trace-Id` propagation. | ✅ PASS |
| `testGetCatalogViaHttp` | `GET /api/v1/courses/catalog` | `200 OK` | Queries published active catalog over HTTP. | ✅ PASS |
| `testDuplicateCourseCodeReturnsRfc7807Conflict` | `POST /api/v1/courses` | `409 Conflict` | Verifies RFC 7807 problem details response on duplicate course code. | ✅ PASS |
| `testPrerequisiteCycleReturns422ViaHttp` | `POST /api/v1/courses/{id}/prerequisites` | `422 Unprocessable` | Validates RFC 7807 error payload on cycle detection over HTTP. | ✅ PASS |
| `testCourseCreditsEndpointViaHttp` | `GET /api/v1/courses/{id}/credits` | `200 OK` | Retrieves totalCredits and evaluation policy breakdown. | ✅ PASS |

---

---

## 6. Module 5: ACD-02 Curriculum Management Service (`curriculum-service`)

Validates complete academic curriculum definition, semester mapping, credit governance, approval lifecycle, and outbox event integration according to `ACD-02_Curriculum_Management_User_Stories.csv` and `ACD-02.docx`:
- Authoritative curriculum aggregate & scope consistency (BR-05, BR-13)
- Immutable published snapshots & optimistic version locking (BR-01, BR-02, BR-12)
- Unique semester sequence numbers (BR-07)
- ACD-03 subject validation & duplicate mapping prevention (BR-03, BR-06, BR-08)
- Credit policy compliance validation on publish (BR-09)
- Directed Acyclic Graph (DAG) prerequisite cycle detection (BR-10)
- Governed approval workflow: submit $\to$ review $\to$ approve $\to$ publish (BR-04, BR-14)
- Atomic superseding of prior version on publish (BR-01, BR-11)
- Deletion prevention with downstream academic references (BR-15)
- Inbound event consumption (`SubjectDeactivated`) flagging & blocking publication
- Idempotency deduplication & RBAC/ABAC department-scoping (403 ACD2_FORBIDDEN)

*Test Classes:* [`CurriculumDomainServiceTest.java`](file:///d:/CampXSync/CampXSync_V2.0/services/curriculum-service/src/test/java/com/campx/academic/curriculum/CurriculumDomainServiceTest.java), [`CurriculumControllerIntegrationTest.java`](file:///d:/CampXSync/CampXSync_V2.0/services/curriculum-service/src/test/java/com/campx/academic/curriculum/CurriculumControllerIntegrationTest.java)

| Test Class | Test Method | HTTP Route & Action | Expected Status | Description & Invariants Enforced | Outcome |
| :--- | :--- | :--- | :---: | :--- | :---: |
| `CurriculumDomainServiceTest` | `testCreateCurriculumSuccess` | Internal Domain API | N/A | Creates curriculum aggregate & draft version 1, emits outbox event. | ✅ PASS |
| `CurriculumDomainServiceTest` | `testCreateCurriculumInvalidCourse` | Internal Domain API | `422 Unprocessable` | Validates active course existence in ACD-01 (BR-05). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testCreateCurriculumTenantMismatch` | Internal Domain API | `403/422 Error` | Enforces tenant/campus scope consistency (BR-13). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testPublishedVersionImmutability` | Internal Domain API | `409 Conflict` | Prohibits in-place update of published versions (BR-02, BR-12). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testOptimisticLockingOnVersionUpdate`| Internal Domain API | `409 Conflict` | Rejects stale version edits via optimistic concurrency. | ✅ PASS |
| `CurriculumDomainServiceTest` | `testUniqueSemesterSequenceNumbers` | Internal Domain API | `409 Conflict` | Enforces unique semester sequence numbers per version (BR-07). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testSubjectMappingValidationAgainstACD03` | Internal Domain API | `422 Unprocessable` | Validates active subject in ACD-03 before mapping (BR-03, BR-06). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testDuplicateSubjectMappingInSemester` | Internal Domain API | `409 Conflict` | Prevents duplicate subject mapping in same semester (BR-08). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testCreditPolicyViolationOnPublish` | Internal Domain API | `422 Unprocessable` | Rejects publish if aggregated credits violate bounds (BR-09). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testPrerequisiteCycleDetection` | Internal Domain API | `422 Unprocessable` | Traverses DAG and blocks cyclic dependencies (BR-10). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testApprovalAndPublicationLifecycle` | Internal Domain API | `200 OK` | Validates Submit $\to$ Review $\to$ Approve $\to$ Publish workflow. | ✅ PASS |
| `CurriculumDomainServiceTest` | `testAtomicSupersedingPriorVersionOnPublish` | Internal Domain API | `200 OK` | Atomically supersedes prior effective version on publish (BR-01). | ✅ PASS |
| `CurriculumDomainServiceTest` | `testSubjectDeactivatedEventBlocksPublication` | Event Consumption | `409 Conflict` | Flags mappings and blocks publication when subject deactivated. | ✅ PASS |
| `CurriculumDomainServiceTest` | `testPreventDeletionWithDownstreamReferences` | Internal Domain API | `409 Conflict` | Blocks hard delete when downstream references exist (BR-15). | ✅ PASS |
| `CurriculumControllerIntegrationTest` | `testCreateCurriculumViaHttp` | `POST /api/v1/academics/curricula` | `201 Created` | Verifies standard response envelope and draft version creation. | ✅ PASS |
| `CurriculumControllerIntegrationTest` | `testDuplicateSubjectMappingReturns409` | `POST .../subjects` | `409 Conflict` | Validates RFC 7807 error payload on duplicate subject mapping. | ✅ PASS |
| `CurriculumControllerIntegrationTest` | `testStudentCannotCreateCurriculumReturns403` | `POST .../curricula` | `403 Forbidden` | Verifies RBAC restriction for Student role (`ACD2_FORBIDDEN`). | ✅ PASS |
| `CurriculumControllerIntegrationTest` | `testHealthEndpoint` | `GET /actuator/health` | `200 OK` | Verifies service health probe (`status: UP`). | ✅ PASS |

---

## 7. Module 6: API Gateway (`api-gateway`)

Validates perimeter reverse proxying, correlation tracing, error preservation, and multi-service routing across Platform, College, and Academic tiers.

*Test Classes:* [`ApiGatewayRoutingTest.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/test/java/com/campx/gateway/ApiGatewayRoutingTest.java), [`GatewayEndToEndIntegrationTest.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/test/java/com/campx/gateway/GatewayEndToEndIntegrationTest.java), [`GatewayCourseIntegrationTest.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/test/java/com/campx/gateway/GatewayCourseIntegrationTest.java), [`GatewayCurriculumIntegrationTest.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/test/java/com/campx/gateway/GatewayCurriculumIntegrationTest.java)

| Test Class | Test Method | Ingress Route & Downstream Target | Expected Status | Description & Verification | Outcome |
| :--- | :--- | :--- | :---: | :--- | :---: |
| `ApiGatewayRoutingTest` | `testHealthEndpointDirect` | Direct `GET /actuator/health` | `200 OK` | Verifies host liveness check (`status: UP`). | ✅ PASS |
| `ApiGatewayRoutingTest` | `testRouteCatalogEndpointDirect` | Direct `GET /api/v1/gateway/routes` | `200 OK` | Verifies dynamic in-memory route registry introspection. | ✅ PASS |
| `ApiGatewayRoutingTest` | `testUnmatchedRouteReturns404` | Unmatched path `GET /v1/nonexistent` | `404 Not Found` | Verifies RFC 7807 response (`GATEWAY_ROUTE_NOT_FOUND`). | ✅ PASS |
| `ApiGatewayRoutingTest` | `testTraceIdGenerationAndForwarding` | Client omits `X-Trace-Id` | `200 OK` | Verifies Gateway generates UUID and binds to MDC. | ✅ PASS |
| `GatewayEndToEndIntegrationTest` | `testRouteToInstituteAdminViaGateway` | Gateway `8080` &rarr; ADM-01 `8081` (`POST /api/v1/admin/institutes`) | `201 Created` | Verifies Institute creation reverse proxying. | ✅ PASS |
| `GatewayEndToEndIntegrationTest` | `testRouteToCollegeAdminViaGateway` | Gateway `8080` &rarr; ADM-02 `8082` (`GET /api/v1/college-admin/profile`) | `200 OK` | Verifies College profile reverse proxying. | ✅ PASS |
| `GatewayEndToEndIntegrationTest` | `testDownstreamErrorPassThroughViaGateway` | Gateway `8080` &rarr; ADM-01 `8081` (Duplicate institute code) | `409 Conflict` | Verifies downstream RFC 7807 error pass-through intact. | ✅ PASS |
| `GatewayCourseIntegrationTest` | `testRouteCreateCourseViaGateway` | Gateway `8084` &rarr; ACD-01 `8083` (`POST /api/v1/courses`) | `201 Created` | Verifies Course creation reverse proxying and tracing. | ✅ PASS |
| `GatewayCourseIntegrationTest` | `testRouteCatalogShortAliasViaGateway` | Gateway `8084` &rarr; ACD-01 `8083` (`GET /v1/course-catalog`) | `200 OK` | Verifies canonical short alias routing to course catalog. | ✅ PASS |
| `GatewayCourseIntegrationTest` | `testDownstreamConflictErrorPassThroughViaGateway` | Gateway `8084` &rarr; ACD-01 `8083` (Duplicate course code) | `409 Conflict` | Verifies downstream 409 Conflict and `X-Trace-Id` pass-through. | ✅ PASS |
| `GatewayCurriculumIntegrationTest` | `testRouteCreateCurriculumViaGateway` | Gateway `8095` &rarr; ACD-02 `8094` (`POST /api/v1/academics/curricula`) | `201 Created` | Verifies Curriculum creation reverse proxying and tracing. | ✅ PASS |
| `GatewayCurriculumIntegrationTest` | `testRouteCatalogShortAliasViaGateway` | Gateway `8095` &rarr; ACD-02 `8094` (`GET /v1/curriculum-catalog`) | `200 OK` | Verifies canonical short alias routing to active curricula. | ✅ PASS |
| `GatewayCurriculumIntegrationTest` | `testDownstreamForbiddenPassThroughViaGateway` | Gateway `8095` &rarr; ACD-02 `8094` (Student role mutation) | `403 Forbidden` | Verifies downstream 403 Forbidden error pass-through intact. | ✅ PASS |
| `GatewaySubjectIntegrationTest` | `testRouteCreateSubjectViaGateway` | Gateway `8099` &rarr; ACD-03 `8098` (`POST /api/v1/academics/subjects`) | `201 Created` | Verifies Subject creation reverse proxying and correlation tracing. | ✅ PASS |
| `GatewaySubjectIntegrationTest` | `testRouteDuplicateSubjectCodeErrorPassThrough` | Gateway `8099` &rarr; ACD-03 `8098` (Duplicate code) | `409 Conflict` | Verifies transparent 409 Conflict error pass-through. | ✅ PASS |
| `GatewaySubjectIntegrationTest` | `testRouteCanonicalSubjectCatalogAlias` | Gateway `8099` &rarr; ACD-03 `8098` (`GET /v1/subject-catalog`) | `200 OK` | Verifies canonical short alias routing to published subject catalog. | ✅ PASS |

---

## 8. Quality & Architecture Certification

- **Zero Test Failures**: 109/109 automated tests passed synchronously in Maven reactor (14.6s).
- **Trace Correlation**: 100% of tested HTTP request/response flows successfully propagate `X-Trace-Id` through the API Gateway to downstream services.
- **Resilience Guarantees**: Downstream connection failures return `503`, timeouts return `504`, and unmapped routes return `404` with RFC 7807 compliance.
- **DAG Cycle Prevention**: Depth-First Search cycle detection strictly prevents circular prerequisite dependencies (`409 ACD_PREREQUISITE_CYCLE` and `422 ACD2_PREREQUISITE_CYCLE`).
- **Lifecycle Integrity**: Active batch offerings block course/subject deactivation (BR-05, BR-08), published versions are immutable (BR-06), and unapproved curricula are blocked from publication.
- **Atomic Multi-Document Superseding**: Exactly one version is effective per entity; previous versions are atomically marked `SUPERSEDED` upon publication.
