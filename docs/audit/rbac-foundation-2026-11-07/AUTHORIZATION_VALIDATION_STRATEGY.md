# CampXSync — Authorization Validation Strategy

**Audit ID:** `RBAC-FOUNDATION-2026-11-07`  
**Status:** Plan only. Execute after RBAC decisions are approved.  
**Rule:** A scenario is verified only with recorded test evidence.

---

## 1. Objectives

1. Prove every **approved** grant works (positive).  
2. Prove every **denied / unassigned** path fails closed (negative).  
3. Prove scope boundaries: tenant, college, department, programme, course, section, own-record.  
4. Prove parity across Gateway → Java → PostgreSQL RLS.  
5. Prevent authorization bypasses (HMAC, header spoofing, UI-only checks, event consumers).

---

## 2. Test layers

| Layer | Tooling | Required for STM unfreeze |
| :--- | :--- | :---: |
| L1 Unit domain authZ | JUnit (role × permission × resource) | Yes |
| L2 Gateway | Route + JWT rejection + HMAC rejection tests | Yes |
| L3 Service HTTP | Controller tests with forged/missing identity headers | Yes |
| L4 Live PostgreSQL RLS | HikariCP tests on `azulbaxszsqxymqmlwfe` with rollback | Yes (after migration approval) |
| L5 Cross-tenant / cross-section IDOR | Paired synthetic tenants A/B | Yes |
| L6 Browser E2E | Playwright role personas | Yes for student/faculty paths; admin after D-RBAC-01 |
| L7 Regression | Existing ADM/ACD/STM-01/02 suites | Yes |

---

## 3. Mandatory matrices

### 3.1 Role coverage

For each approved catalogue role touching STM (minimum R04, R07, R08, R10, R25, R26, R28, plus any approved Registrar/Applicant addendum):

- Allowed operation fixtures  
- Denied operation fixtures  
- Wrong-tenant fixtures  
- Missing claim fixtures  

### 3.2 STM-03 operation matrix (minimum)

| Operation | Positive actor | Negative actors (must 401/403) |
| :--- | :--- | :--- |
| View own enrolment | R25 owner | Other R25, R10, unauthenticated, other tenant |
| Register courses | R25 owner in window | R25 out of window; R10; Registrar-unapproved string; missing idempotency replay safe |
| Join waitlist | R25 when full | R25 when seats remain; other student |
| View roster | R10 assigned | R10 other section; R25; cross-tenant |
| Configure term/window | Approved admin only | R25, R10, unassigned roles |
| Enrolment create/approve | Approved admin only | R25, R10, self-approve |
| Section allocate | Approved scoped role | Wrong dept/section; capacity exceeded |
| Waitlist promote | Approved admin | R25, R10 |

Until D-RBAC-01, negatives for admin rows are satisfied by `POLICY_UNASSIGNED` / 403 — **not** by inventing allows.

### 3.3 Unassigned permission suite

For every GAP marked Unassigned:

- Attempt operation with each of R10, R25, R04, R08, R07, raw `REGISTRAR` string  
- Expect deny unless that actor was explicitly approved  
- Record response code + problem `code` field  

### 3.4 Bypass suite

| Attack | Expect |
| :--- | :--- |
| Missing HMAC with secret configured | 401 |
| Stale HMAC timestamp | 401 |
| Client-supplied `X-User-Role` without gateway signature | Rejected at service |
| Direct DB role without claims | 0 rows / RLS deny |
| Cross-tenant UUID enumeration | 404/0 rows (no leakage) |
| Intelligence/outbox consumer mutating without authZ | Rejected / no privilege escalation |

---

## 4. RLS evidence requirements

For each STM table:

1. Policy names and roles (`authenticated`) recorded  
2. Positive insert/select under claims  
3. Cross-tenant select returns 0  
4. FORCE RLS status recorded for Tier-1 tables  
5. Unconditional transaction rollback (no residual rows)

Dev target only: `azulbaxszsqxymqmlwfe`. Production refs remain forbidden.

---

## 5. Regression gates before unfreezing STM-04+

1. All L1–L5 STM-03 suites green under **approved** matrix version ID.  
2. STM-02 evaluator no longer depends on unapproved invented codes (or codes ratified).  
3. Scripts seed contains `stu*` permissions matching approved matrix.  
4. Documentation (matrix, CSV stamps, STM-03 spec) updated to the same version.  
5. Wave/progress note records authorization contract version.

---

## 6. Current evidence baseline (pre-approval)

| Suite | Result | Limitation |
| :--- | :--- | :--- |
| `EnrollmentDomainServiceTest` | 16/16 PASS | In-memory; admin paths assert POLICY_UNASSIGNED |
| `EnrollmentGatewayRouteTest` | 1/1 PASS | Routing only |
| Live STM-03 RLS | Not run | Migration unapplied |
| Full R01–R30 STM matrix | Not run | Catalogue decisions pending |

This baseline is **insufficient** to declare the RBAC foundation complete.
