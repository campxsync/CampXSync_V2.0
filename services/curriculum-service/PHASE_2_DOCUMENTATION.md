# ACD-02 Curriculum Management Service — Phase 2 Documentation

**Service Name:** CampXSync Curriculum Management Service (ACD-02)  
**Service Path:** `services/curriculum-service`  
**Phase:** Phase 2 — Security & Data Governance Hardening  
**Date:** 23 September 2026  
**Status:** Completed & Verified  

---

## 1. Overview of Phase 2

Phase 2 introduces enterprise security controls, data classification, and cryptographic protection:
1. **Story 63:** External API Consumer Authentication via scoped `X-API-Key` headers, role mapping to `EXTERNAL_API`, and read-only enforcement.
2. **Story 65:** Data sensitivity classification across 4 tiers (L1 Public, L2 Internal, L3 Confidential, L4 Restricted) with automated response field filtering based on caller clearance.
3. **Story 66:** Transport Layer Security (TLS 1.2/1.3) with keystore support, log redaction of sensitive credentials, and MongoDB encryption-at-rest specifications.

---

## 2. Implemented Features & Code Changes

### 2.1 Task 2.1 — External API Consumer Authentication (Story 63)
- **Model:** Added `ApiKeyRecord` in `CurriculumModels` with `keyId`, `rawKey`, `hashedKey` (SHA-256), `tenantId`, `scopes`, and `expiresAt`.
- **Domain Service:**
  - Implemented `apiKeyRegistry` in `CurriculumDomainService` with pre-seeded sample keys for testing.
  - Implemented `validateApiKey(rawApiKey, tenantId)` verifying active status, expiration timestamp, and tenant scope.
  - Extended RBAC: `EXTERNAL_API` role receives strictly `CURRICULUM_VIEW` permission.
- **Controller Enforcement:**
  - `CurriculumController.handle()` intercepts `X-API-Key` header.
  - Invalid/expired keys return HTTP 401 Unauthorized (`ACD2_UNAUTHORIZED`).
  - Read-only enforcement: any write requests (`POST`, `PUT`, `DELETE`) by an external API key caller are blocked with HTTP 403 Forbidden (`ACD2_FORBIDDEN`).
  - Ingress logging masks API key values (`ak_liv...`).

### 2.2 Task 2.2 — Data Sensitivity Classification & Field Filtering (Story 65)
- **Model:** Added `DataClassification` enum (`L1_PUBLIC`, `L2_INTERNAL`, `L3_CONFIDENTIAL`, `L4_RESTRICTED`).
- **Clearance Resolution:** `CurriculumDomainService.getClassificationLevel(role)`:
  - `STUDENT`, `EXTERNAL_API` -> `L1_PUBLIC`
  - `FACULTY`, `DEPARTMENT_HEAD` -> `L2_INTERNAL`
  - `ACADEMIC_ADMIN`, `REGISTRAR`, `ACCREDITATION_TEAM`, `CURRICULUM_COMMITTEE` -> `L3_CONFIDENTIAL`
  - `SUPER_ADMIN`, `ADMIN`, `SYSTEM` -> `L4_RESTRICTED`
- **Field Filtering Interceptor:** `CurriculumController.filterFieldsByClassification`:
  - Strips L4 multi-tenant internals (`tenantId`, `institutionId`) for L1–L3 callers.
  - Strips L3 audit/provenance metadata (`createdBy`, `updatedBy`, `approvedBy`, `publishedBy`, `auditHistory`) for L1–L2 callers.
  - Strips L2 internal curriculum details (`syllabus`, `changeSummary`, `checksum`) for L1 Public callers.

### 2.3 Task 2.3 — TLS & Deployment Security (Story 66)
- **Server:** Updated `CurriculumServer.java` to support TLS via `HTTPS_KEYSTORE_PATH` and `HTTPS_KEYSTORE_PASSWORD`.
  - Configures `HttpsServer` with `SSLContext` (TLSv1.2/1.3) when a keystore is supplied.
  - Falls back cleanly to `HttpServer` in development mode.
- **Documentation:** Created `DEPLOYMENT_SECURITY.md` covering TLS cipher suites, MongoDB WiredTiger AES-256 encryption, backup encryption procedures, and key rotation.

---

## 3. Verification & Test Results

### 3.1 Automated Tests Executed
- `CurriculumDomainServiceTest.testDataClassificationLevels`: Verifies role-to-classification mapping.
- `CurriculumDomainServiceTest.testApiKeyValidation`: Tests valid, expired, and tenant-mismatched API key lookups.
- `CurriculumControllerIntegrationTest.testExternalApiKeyReadOnlyAccess`: Verifies HTTP 200 on GET with valid `X-API-Key`.
- `CurriculumControllerIntegrationTest.testExternalApiKeyWriteRejected`: Verifies HTTP 403 on POST write attempt with API key.
- `CurriculumControllerIntegrationTest.testInvalidApiKeyReturns401`: Verifies HTTP 401 on invalid/unknown API key.
- `CurriculumControllerIntegrationTest.testDataClassificationFieldFilteringUnit`: Verifies field filtering across L1, L2, L3, and L4 levels.

### 3.2 Test Run Output Summary
```
[INFO] Running com.campx.academic.curriculum.CurriculumControllerIntegrationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.campx.academic.curriculum.CurriculumDomainServiceTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS - Total time: 4.274 s
```
All 33 service tests passed with **0 failures**.
