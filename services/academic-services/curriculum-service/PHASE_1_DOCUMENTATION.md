# ACD-02 Curriculum Management Service — Phase 1 Documentation

**Service Name:** CampXSync Curriculum Management Service (ACD-02)  
**Service Path:** `services/curriculum-service`  
**Phase:** Phase 1 — Critical Functional Fixes  
**Date:** 23 September 2026  
**Status:** Completed & Verified  

---

## 1. Overview of Phase 1

Phase 1 addresses critical functional defects and missing capabilities identified in the ACD-02 Gap Analysis:
1. **Story 29:** Syllabus module/topic JSON request body parsing defect in `CurriculumController`.
2. **Story 62:** Accreditation Portal compliance view endpoint (`GET /api/v1/academics/curricula/{id}/compliance-view`) delivering composite audit data.
3. **Story 76:** Formal machine-readable OpenAPI 3.0 specification (`openapi.yaml`).

---

## 2. Implemented Features & Code Changes

### 2.1 Task 1.1 — Fix Syllabus Body Parsing (Story 29)
- **Problem:** `CurriculumController.handleUpdateSyllabus()` previously discarded request payloads and substituted a hardcoded single `SyllabusModule`.
- **Solution:**
  - Implemented `parseSyllabusModules(String body)` using tokenized regex parsing (supporting both standard `{ "modules": [...] }` envelopes and direct `[...]` arrays).
  - Extracts each module's `moduleId`, `title`, `order`, `hours`, and `topics` string array.
  - Enforced validation: empty or invalid module payloads throw `CurriculumValidationException` (HTTP 400 with `ACD2_VALIDATION_ERROR`).
  - Updated `handleGetSyllabus()` to serialize complete module topic arrays into the JSON response.
- **Files Modified:**
  - `com.campx.academic.curriculum.controller.CurriculumController`
  - `com.campx.academic.curriculum.exception.CurriculumValidationException` (New)

### 2.2 Task 1.2 — Add Accreditation Portal Compliance View (Story 62)
- **Problem:** Accreditation Team roles had `CURRICULUM_VIEW` and `CURRICULUM_EXPORT` permissions, but lacked a consolidated view to audit curricula, versions, outcomes, subject mappings, and change history in one call.
- **Solution:**
  - Added `ComplianceView` composite DTO in `CurriculumModels`.
  - Added `getComplianceView(String curriculumId, String actorId, String actorRole)` to `CurriculumDomainService` with strict permission checking (`CURRICULUM_VIEW` and `CURRICULUM_EXPORT`).
  - Added route `GET /api/v1/curricula/{id}/compliance-view` and `GET /api/v1/academics/curricula/{id}/compliance-view` in `CurriculumController` to return full composite compliance data.
  - Verified RBAC enforcement: `ACCREDITATION_TEAM` and `ACADEMIC_ADMIN` succeed (200 OK); unauthorized roles (e.g. `STUDENT`) receive HTTP 403 `ACD2_FORBIDDEN`.
- **Files Modified:**
  - `com.campx.academic.curriculum.model.CurriculumModels.ComplianceView` (New DTO)
  - `com.campx.academic.curriculum.service.CurriculumDomainService`
  - `com.campx.academic.curriculum.controller.CurriculumController`

### 2.3 Task 1.3 — Formal OpenAPI 3.0 Contract (Story 76)
- **Problem:** No machine-readable API contract existed for the ACD-02 service.
- **Solution:**
  - Created `services/curriculum-service/openapi.yaml` conforming to OpenAPI 3.0.3.
  - Documented all 24+ REST endpoints, request/response models, query parameters, header schemas (`X-Tenant-Id`, `X-User-Role`, `X-Trace-Id`, `X-API-Key`, `Idempotency-Key`), and the complete 11-code error taxonomy.
- **File Created:**
  - `services/curriculum-service/openapi.yaml`

---

## 3. Verification & Test Results

### 3.1 Automated Tests Executed
- `CurriculumDomainServiceTest.testGetComplianceViewSuccess`: Validates domain logic and composite DTO compilation.
- `CurriculumDomainServiceTest.testGetComplianceViewForbiddenForStudent`: Validates student rejection with HTTP 403.
- `CurriculumControllerIntegrationTest.testUpdateSyllabusWithValidBody`: Validates end-to-end HTTP PUT with multiple modules and topics.
- `CurriculumControllerIntegrationTest.testUpdateSyllabusEmptyModulesReturns400`: Validates HTTP 400 validation error on empty modules.
- `CurriculumControllerIntegrationTest.testComplianceViewEndpointSuccess`: Validates HTTP GET compliance view with `ACCREDITATION_TEAM`.
- `CurriculumControllerIntegrationTest.testComplianceViewEndpointForbiddenForStudent`: Validates HTTP 403 response over HTTP for unauthorized roles.

### 3.2 Test Run Output Summary
```
[INFO] Running com.campx.academic.curriculum.CurriculumControllerIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.campx.academic.curriculum.CurriculumDomainServiceTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS - Total time: 3.210 s
```
All 27 service tests passed with **0 failures**. Full reactor test (`mvn test`) across all 7 modules passed with **0 failures**.
