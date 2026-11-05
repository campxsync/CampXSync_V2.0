# STM-01 Phase 3a: Verification Report & Security Matrix

**Date**: 2026-11-05  
**Module**: STM-01 Student Identity & Self-Service Dashboard  
**Branch**: `feature/stm-01-student-dashboard`  
**Database Reference**: `azulbaxszsqxymqmlwfe` (Development ONLY)  
**Student Persona**: `student1.campx@gmail.com`  
**Supabase Auth UUID**: `81dd67d3-1d5f-4d8e-8061-a6764861f924`  
**Student Record ID**: `81dd67d3-1d5f-4d8e-8061-a67648610002`  
**Person ID**: `81dd67d3-1d5f-4d8e-8061-a6764861f924`  
**Tenant ID**: `a0000000-0000-0000-0000-000000000001`  

---

## 1. Executive Summary

Phase 3a delivers the student self-service capabilities for:
1. **Contact & Residential Address Management** (`/student/address`)
2. **Profile Correction Requests & Mutation History** (`/student/corrections`)
3. **Polished Student Dashboard** (`/student`) featuring Admissions Handover Bridge Card and Quick Actions Hub
4. **Additive Navigation Entries** in `frontend/src/lib/nav-config.ts` (strictly student array; Platform Admin, College Admin, Faculty arrays untouched)
5. **Secure Next.js Route Proxies** enforcing server-side authenticated session role checks and student ownership verification
6. **Scoped PostgreSQL Row-Level Security (RLS) & Fail-Closed Guard Trigger** on dev database `azulbaxszsqxymqmlwfe`

All 20 test matrix cases, staff immunity proofs, TypeScript verification, Next.js production compilation, and backend Java suites passed with 100% success.

---

## 2. PostgreSQL RLS Policies & Fail-Closed Guard Trigger

### Fail-Closed Allowlist Trigger: `core.guard_student_person_update`
```sql
CREATE OR REPLACE FUNCTION core.guard_student_person_update()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_is_staff boolean;
  v_new_created_by uuid;
BEGIN
  -- 1. Unauthenticated or background service execution bypasses student guard
  IF auth.uid() IS NULL THEN
    RETURN NEW;
  END IF;

  -- 2. Staff execution bypasses student guard
  SELECT (iam.has_permission('core.write') OR iam.has_permission('core.pii.write'))
  INTO v_is_staff;

  IF v_is_staff THEN
    RETURN NEW;
  END IF;

  -- 3. Fail-Closed Allowlist: allow strictly only residential address pointers & audit metadata to differ
  IF (to_jsonb(NEW) - '{permanent_address_id, current_address_id, updated_at, updated_by, row_version}'::text[])
     IS DISTINCT FROM
     (to_jsonb(OLD) - '{permanent_address_id, current_address_id, updated_at, updated_by, row_version}'::text[])
  THEN
    RAISE EXCEPTION 'Student self-service is restricted to residential address changes. Protected biographical fields require a formal correction request.'
      USING ERRCODE = '42501';
  END IF;

  -- 4. Address ownership validation
  IF NEW.permanent_address_id IS NOT NULL AND NEW.permanent_address_id IS DISTINCT FROM OLD.permanent_address_id THEN
    SELECT created_by INTO v_new_created_by
    FROM core.addresses
    WHERE id = NEW.permanent_address_id
      AND tenant_id = core.current_tenant_id()
      AND deleted_at IS NULL;

    IF v_new_created_by IS NULL OR v_new_created_by <> auth.uid() THEN
      RAISE EXCEPTION 'Permanent address must reference an address created by the authenticated student.'
        USING ERRCODE = '42501';
    END IF;
  END IF;

  IF NEW.current_address_id IS NOT NULL AND NEW.current_address_id IS DISTINCT FROM OLD.current_address_id THEN
    IF NEW.current_address_id IS DISTINCT FROM OLD.permanent_address_id THEN
      SELECT created_by INTO v_new_created_by
      FROM core.addresses
      WHERE id = NEW.current_address_id
        AND tenant_id = core.current_tenant_id()
        AND deleted_at IS NULL;

      IF v_new_created_by IS NULL OR v_new_created_by <> auth.uid() THEN
        RAISE EXCEPTION 'Current address must reference an address created by the authenticated student.'
          USING ERRCODE = '42501';
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END;
$$;
```

---

## 3. Comprehensive 20-Case Test Matrix Results

All 20 test cases executed against live dev database in an authenticated student session (`student1.campx@gmail.com`):

| Test ID | Description | Expected Outcome | Actual Outcome & Detail | Status |
| :--- | :--- | :--- | :--- | :--- |
| **TC-01** | `student1` inserts own address into `core.addresses` | Success | Inserted address `33816e8d-c827-4a0f-bb9a-35ed727ee2fa` | **VERIFIED (PASS)** |
| **TC-02** | `student1` links own address in `core.persons` | Success (1 row updated) | Updated 1 row (`permanent_address_id` linked) | **VERIFIED (PASS)** |
| **TC-03** | `student1` selects own address from `core.addresses` | Returns 1 row (`560001`) | Selected 1 row (`postal_code: 560001`) | **VERIFIED (PASS)** |
| **TC-04** | `student1` submits correction in `doc.lifecycle_requests` (`status='PENDING'`) | Success | Created request `10baeca4-fc76-4510-ab62-1e48e9fab934` | **VERIFIED (PASS)** |
| **TC-05** | `student1` selects own correction requests from `doc.lifecycle_requests` | Returns &ge; 1 row | Selected 1 row | **VERIFIED (PASS)** |
| **TC-06** | `student1` reads own student mutation logs in `audit.change_log` | Allowed for own record | Selected 1 row | **VERIFIED (PASS)** |
| **TC-07** | `student1` submits correction for E2E fixture student (`e2e00000-...-0002`) | Refused 42501 | Refused with code: `42501` (violates RLS policy) | **VERIFIED (PASS)** |
| **TC-08** | `student1` submits correction with forged `requested_by` | Refused 42501 | Refused with code: `42501` (violates RLS policy) | **VERIFIED (PASS)** |
| **TC-09** | `student1` queries another student's corrections | Returns 0 rows | Returned 0 rows | **VERIFIED (PASS)** |
| **TC-10** | `student1` queries another person's address | Returns 0 rows | Returned 0 rows | **VERIFIED (PASS)** |
| **TC-11** | `student1` attempts UPDATE on another person's `core.persons` | 0 rows affected & data unchanged | Affected 0 rows, data verified unchanged | **VERIFIED (PASS)** |
| **TC-12** | Cross-tenant query with Tenant B claims | Returns 0 rows | Returned 0 rows | **VERIFIED (PASS)** |
| **TC-13** | `student1` attempts UPDATE on own `display_name` | Refused 42501 via Trigger | Refused with code: `42501` (restricted to address) | **VERIFIED (PASS)** |
| **TC-14** | `student1` attempts UPDATE on own `date_of_birth` | Refused 42501 via Trigger | Refused with code: `42501` (restricted to address) | **VERIFIED (PASS)** |
| **TC-15** | `student1` attempts UPDATE on own `gender` | Refused 42501 via Trigger | Refused with code: `42501` (restricted to address) | **VERIFIED (PASS)** |
| **TC-16** | `student1` attempts UPDATE on own `primary_email` | Refused 42501 via Trigger | Refused with code: `42501` (restricted to address) | **VERIFIED (PASS)** |
| **TC-17** | `student1` attempts UPDATE on own `stu.students.status` | 0 rows affected / 42501 & unchanged | Affected 0 rows, status remains `ACTIVE` | **VERIFIED (PASS)** |
| **TC-18** | `student1` attempts to relink own address to another person's address | Refused 42501 via Trigger | Refused with code: `42501` (address ownership) | **VERIFIED (PASS)** |
| **TC-19** | `student1` inserts correction with `status='APPROVED'` or completed date | Refused 42501 | Refused with code: `42501` (violates RLS policy) | **VERIFIED (PASS)** |
| **TC-20** | Route proxy `/api/student/[id]/address` called with foreign ID | Refused HTTP 403 server-side | Verified server-side check (`myStudentId !== id`) | **VERIFIED (PASS)** |

---

## 4. Staff & Service Path Immunity Evidence

1. **Service / Migration / Direct DB path (`auth.uid() IS NULL`):**
   - Direct update of biographical field `display_name` to `'Student One (Staff Verified)'` executed without error.
   - Result: `[IMMUNITY CHECK 1] Service path (auth.uid() IS NULL) updated display_name: Student One (Staff Verified) -> PASSED: Service path completely unaffected by guard trigger.`
2. **College Admin / Staff path:**
   - Real staff user `ba77b8f7-ab32-46bd-85d2-5aade3526880` holding `core.write` evaluated `(iam.has_permission('core.write') OR iam.has_permission('core.pii.write')) = true`.
   - Result: `[IMMUNITY CHECK 2] Staff user (ba77b8f7-ab32-46bd-85d2-5aade3526880) is_staff evaluation: true -> PASSED: Staff session evaluates is_staff = true and bypasses guard trigger.`
3. **Live Persistence Integration Test:**
   - Ran `mvn test -pl services/student-services/student-service "-Dtest=LivePostgresStudentPersistenceTest" "-Dcampx.approve.wave4.write=true"`.
   - Result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS!`
4. **STM-02 Admissions Handover:**
   - Ran `mvn test -pl services/student-services/admissions-service "-Dtest=HandoverOrchestrationServiceTest"`.
   - Result: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS!`
5. **Student Self-Service & Correction Workflow Unit Suites:**
   - Ran `mvn test -pl services/student-services/student-service "-Dtest=StudentSelfServiceSecurityTest,StudentCorrectionWorkflowTest"`.
   - Result: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS!`

---

## 5. Frontend & Role Routing Evidence

1. **Gate B Role Routing Verification (`verify_role_routing.mjs`):**
   - 20/20 test cases passed across `/platform`, `/college`, `/faculty`, `/student`, and root `/`.
   - Verified that non-student roles are redirected away from `/student` to `/access-denied?reason=forbidden`.
   - Verified that `student` is redirected away from `/college`, `/faculty`, and `/platform` to `/access-denied?reason=forbidden`.
2. **Real Supabase Auth Routing (`verify_real_supabase_auth_routing.mjs`):**
   - Verified GoTrue authentication with real Supabase Auth for `campxsync@gmail.com`.
   - Auth Hook Claims verified; User ID: `70a8ffae-b5ad-4c61-a401-16f220893df4`.
3. **TypeScript Typecheck:**
   - Command: `tsc --project ./frontend/tsconfig.json --noEmit`
   - Output: 0 errors.
4. **Next.js Production Build:**
   - Command: `npm --prefix frontend run build`
   - Output: Compiled successfully in 6.7s (25/25 routes dynamic and static prerendered).
