# STM-02 Phase B — B3 Approval Gate

**Status:** STOPPED AT PRE-CHECK GATE PER USER SPECIFICATION  
**Gate Condition:** *"If the journey needs a NEW tenant, a NEW auth user, a password or any credential change, STOP B3, write the exact SQL/API calls needed to b3/NEEDS_APPROVAL.md and continue with B4/B5."*

---

## 1. Pre-Check Findings: User & Tenant Inventory

### Existing Database Inventory on `azulbaxszsqxymqmlwfe`:
1. **`auth.users`:**
   Only **1 user** exists:
   - `ba77b8f7-ab32-46bd-85d2-5aade3526880` (`dev-admin@campx.com`)
2. **`core.tenants`:**
   Only **2 tenants** exist:
   - `a0000000-0000-0000-0000-000000000001` (`DEV_TEST_TENANT` - "CampX Architecture Dev Test Institute")
   - `b0000000-0000-0000-0000-000000000002` (`T_FOREIGN` - "Foreign Test Tenant")

### Requirement vs Reality Mismatch:
The required full-lifecycle browser journey:
`Applicant applies -> document verification -> faculty review -> approval (SoD) -> offer -> acceptance -> STM-01 student creation`
specifies:
- **Tenant Scope:** *"All test data prefixed E2E-STM02- in one dedicated test tenant."*
- **Role Actors (Minimum 4 Distinct Identities):**
  1. **Applicant:** Submits draft application and accepts offer (Role: `student` or public applicant).
  2. **Admissions Officer / Verifier:** Verifies uploaded documents (Role: `college_admin`).
  3. **Faculty Reviewer:** Evaluates application and submits rubric scores (Role: `faculty`).
  4. **Admissions Approver:** Issues final binding decision (Role: `college_admin`; must be a distinct UUID from the reviewer to satisfy hard Segregation of Duties rules in `ReviewAndDecisionService`).

Because executing this journey requires a **NEW dedicated test tenant**, **multiple NEW user identities**, and associated role assignments, B3 is **STOPPED** per protocol. Zero unapproved provisioning writes were executed.

---

## 2. Exact SQL Statements Needed for User Approval

If authorized by the user, the following minimal SQL script provisions the dedicated test tenant, college, academic program, intake, and personas on `azulbaxszsqxymqmlwfe`:

```sql
-- ============================================================================
-- PROPOSED FIXTURE PROVISIONING FOR B3 BROWSER JOURNEY (E2E-STM02-)
-- ============================================================================

-- 1. Dedicated Test Tenant & College
INSERT INTO core.tenants (id, code, name, status, currency_code, timezone, locale)
VALUES (
    'e2e00000-0000-0000-0000-000000000001',
    'E2E-STM02-TENANT',
    'E2E STM-02 Dedicated Test Institute',
    'ACTIVE', 'INR', 'Asia/Kolkata', 'en-IN'
) ON CONFLICT (code) DO NOTHING;

INSERT INTO core.colleges (tenant_id, id, name, code, status)
VALUES (
    'e2e00000-0000-0000-0000-000000000001',
    'e2e00000-0000-0000-0000-000000000002',
    'E2E STM-02 Engineering College',
    'E2E-ENG',
    'ACTIVE'
) ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO core.academic_years (tenant_id, id, college_id, name, code, start_date, end_date, status)
VALUES (
    'e2e00000-0000-0000-0000-000000000001',
    'e2e00000-0000-0000-0000-000000000003',
    'e2e00000-0000-0000-0000-000000000002',
    'Academic Year 2026-2027',
    'AY-2026-27',
    '2026-06-01', '2027-05-31',
    'ACTIVE'
) ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO acd.programs (tenant_id, id, college_id, name, code, duration_years, status)
VALUES (
    'e2e00000-0000-0000-0000-000000000001',
    'e2e00000-0000-0000-0000-000000000004',
    'e2e00000-0000-0000-0000-000000000002',
    'Bachelor of Technology in Computer Science',
    'BTECH-CS',
    4,
    'ACTIVE'
) ON CONFLICT (tenant_id, code) DO NOTHING;

-- 2. Persons for Pre-Matriculation Identity & Workflow Actors
INSERT INTO core.persons (tenant_id, id, first_name, last_name, primary_email, status)
VALUES
    ('e2e00000-0000-0000-0000-000000000001', 'e2e00000-0000-0000-0000-000000000010', 'Alex', 'Applicant', 'e2e-applicant@campx.com', 'APPLICANT'),
    ('e2e00000-0000-0000-0000-000000000001', 'e2e00000-0000-0000-0000-000000000020', 'Dana', 'DocVerifier', 'e2e-verifier@campx.com', 'ACTIVE'),
    ('e2e00000-0000-0000-0000-000000000001', 'e2e00000-0000-0000-0000-000000000030', 'Prof', 'Reviewer', 'e2e-reviewer@campx.com', 'ACTIVE'),
    ('e2e00000-0000-0000-0000-000000000001', 'e2e00000-0000-0000-0000-000000000040', 'Dean', 'Approver', 'e2e-approver@campx.com', 'ACTIVE')
ON CONFLICT (tenant_id, primary_email) DO NOTHING;

-- 3. STM-02 Intake Offering
INSERT INTO stu.admission_intakes (
    tenant_id, id, college_id, program_id, academic_year_id,
    intake_name, intake_code, application_window_start, application_window_end,
    capacity, is_active, fee_required, fee_amount
) VALUES (
    'e2e00000-0000-0000-0000-000000000001',
    'e2e00000-0000-0000-0000-000000000050',
    'e2e00000-0000-0000-0000-000000000002',
    'e2e00000-0000-0000-0000-000000000004',
    'e2e00000-0000-0000-0000-000000000003',
    'E2E-STM02-Intake-2026',
    'E2E-INT-2026',
    now() - interval '1 day',
    now() + interval '30 days',
    100, true, false, 0.00
) ON CONFLICT (tenant_id, academic_year_id, intake_code) DO NOTHING;
```

---

## 3. Playwright E2E Isolation Setup

As specified, Playwright configuration and runner scripts must reside strictly in:
`docs/stm-02/e2e/`
with its own isolated `package.json`, preserving root and `frontend/package.json` with zero modifications.

