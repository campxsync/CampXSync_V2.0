# STM-02 Admissions Technical Guide & Live Integration Manual

## 1. System Overview & Architecture
The STM-02 Admissions Module provides an enterprise-grade admissions journey compliant with single-origin multi-tenancy and PostgreSQL Row-Level Security (RLS).
The operational flow traverses:
`UI (Next.js 15 App Router) → API Gateway (ES256 Verification & Claims Injection) → Admissions Domain Service (Java 8 / Sun HttpServer on Port 8094) → PostgreSQL Dev Database (Ref: azulbaxszsqxymqmlwfe) → STM-01 Student Domain Service (Port 8093)`.

## 2. Multi-Persona Browser & API Journey
The complete lifecycle consists of 7 transactional stages:
1. **Applicant Submission:**
   - Persona: `e2e-applicant@campx.test` (Role: `STUDENT`).
   - Endpoint: `POST /api/v1/admissions/applications` & `POST /api/v1/admissions/applications/:id/documents`.
   - Result: Application created (`SUBMITTED`), document uploaded (`PENDING_VERIFICATION`).
2. **Document Verification & Reviewer Assignment:**
   - Persona: `e2e-verifier@campx.test` (Role: `COLLEGE_ADMIN`).
   - Endpoint: `POST .../documents/:docId/verify` & `POST .../assign`.
   - Result: Document marked `VERIFIED`, faculty reviewer assigned.
3. **Faculty Review:**
   - Persona: `e2e-reviewer@campx.test` (Role: `FACULTY`).
   - Endpoint: `POST .../reviews`.
   - Result: Review scores & recommendation recorded; event `admissions.application.reviewed.v1` published to Intelligence Layer.
4. **Approval & Offer Decision:**
   - Persona: `e2e-approver@campx.test` (Role: `COLLEGE_ADMIN`).
   - Endpoint: `POST .../decision`.
   - Result: Formal `OFFER_APPROVED` decision recorded in `stu.admission_decisions`.
5. **Offer Issuance:**
   - Persona: `e2e-approver@campx.test` (Role: `COLLEGE_ADMIN`).
   - Endpoint: `POST .../offers`.
   - Result: Admission offer issued in `stu.admission_offers` linked to approved decision.
6. **Offer Acceptance:**
   - Persona: `e2e-applicant@campx.test` (Role: `STUDENT`).
   - Endpoint: `POST .../offers/accept`.
   - Result: Offer accepted by candidate, application status transitions to `ACCEPTED`.
7. **Downstream Enrolment Handover (STM-01):**
   - Persona: `e2e-approver@campx.test` (Role: `COLLEGE_ADMIN`).
   - Endpoint: `POST .../handover`.
   - Call: Admissions service invokes STM-01 `POST /api/v1/students` with idempotency key and correlation ID.
   - Result: `stu.students` record created, `stu.admission_handovers` updated to `STUDENT_CREATED` with `created_student_id`, application transitions to `HANDED_OVER`.

## 3. Idempotency & Fault Tolerance
- **Idempotency Replay:** Subsequent calls with the same `Idempotency-Key` return the existing student profile with zero duplicate insertions.
- **Circuit Failure & Recovery:** When STM-01 is unreachable, handover record records `enrolment_status = 'FAILED'` with sanitized root cause in `last_error`. Upon restoring STM-01 connectivity, retry transitions handover to `STUDENT_CREATED`.

## 4. Quality Gates & Test Suites
- `admissions-service`: 53 unit & integration tests passing (`mvn test -Dtest=!PostgresAdmissionsRepositoryLiveTest`).
- `frontend`: 0 TypeScript errors (`npx tsc --noEmit`), production build passing with 24 optimized static/dynamic routes.
- Browser E2E: Automated Playwright test passing in Chromium with HAR and trace artifacts captured.

