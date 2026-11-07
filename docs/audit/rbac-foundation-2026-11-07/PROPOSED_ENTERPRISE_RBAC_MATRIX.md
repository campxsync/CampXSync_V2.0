# CampXSync — Proposed Enterprise RBAC Matrix (Decision-Ready Draft)

**Status:** **PROPOSAL ONLY — NOT APPROVED**  
**Audit ID:** `RBAC-FOUNDATION-2026-11-07`  
**Traceability:** Every change cites matrix, validated CSV, Scripts_V3.0, or an open decision ID.

Legend for row status:

- **EXISTING** — already in authoritative R01–R30 / Scripts seed  
- **PROPOSED ADD** — new grant or role; needs approval  
- **PROPOSED REMOVE** — remove unsafe/invented mapping  
- **PROPOSED SCOPE CHANGE** — keep role, tighten/loosen scope  
- **UNRESOLVED** — cannot assign without D-RBAC decision  

---

## 1. Canonical roles (baseline EXISTING)

Retain R01–R30 exactly as published in `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` §Enterprise Role Catalogue.

| Code | Role | Status |
| :---: | :--- | :--- |
| R01–R30 | As documented | EXISTING |

### Proposed catalogue addendum (UNRESOLVED until D-RBAC-01)

| Proposed code | Proposed name | Why | Alternative if rejected |
| :--- | :--- | :--- | :--- |
| R31 | Registrar | STM matrix principal for STM-01–07,09 | Map duties to R04 College Admin + scoped `stu*` perms |
| R32 | Admissions Officer | STM-02 staff operations (if not R31) | Fold into R31 or R04 |
| R33 | Applicant | Pre-enrolment identity ≠ R25 | Keep APPLICANT claim without student grants |

> These codes match strings already invented in STM-02 Java. That invention is **not** approval; it is evidence of pressure for D-RBAC-01/04.

### Proposed removals / freezes

| Item | Status | Rationale |
| :--- | :--- | :--- |
| Treat `REGISTRAR` string as authorized without catalogue row | PROPOSED REMOVE (runtime assumption) | Not in R01–R30 |
| Faculty **Approve** on STM-03 enrolment | PROPOSED REMOVE / deny | Matrix Faculty posture is V roster only (supports FLAG-004 “no advisor A” unless overturned) |
| Scripts EXTRA roles without mapping | UNRESOLVED (D-RBAC-06) | Dual catalogue risk |

---

## 2. Proposed permission namespace (PROPOSED ADD — D-RBAC-02)

| Permission family | Example codes | Domain |
| :--- | :--- | :--- |
| `stu01.*` | `stu01.view.own`, `stu01.write.own`, `stu01.verify` | Identity/profile |
| `stu02.*` | `stu02.intake.manage`, `stu02.application.review`, `stu02.decision.approve` | Admissions |
| `stu03.*` | `stu03.term.cfg`, `stu03.window.cfg`, `stu03.enrolment.create`, `stu03.enrolment.approve`, `stu03.register.execute`, `stu03.roster.view`, `stu03.waitlist.join`, `stu03.waitlist.promote`, `stu03.section.allocate` | Enrolment |
| `stu04.*`–`stu10.*` | Parallel families | Lifecycle → portal |

Action verbs map to matrix vocabulary: view→V, write/create→C/U, approve→A, review→RV, execute→X, configure→CFG, audit→AU.

**Existing Scripts permissions (acd/fin/…)** remain authoritative for their domains — PROPOSED ADD does not replace them.

---

## 3. STM-03 role–permission–scope proposal

| Role | Permission | Scope | Conditions / denials | Status | Source |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R25 Student | `stu03.enrolment.view.own` | Own enrolment | Deny other students | EXISTING intent / PROPOSED code | Matrix Table 17 |
| R25 Student | `stu03.register.execute` | Own enrolment | Window, prereq, capacity, credits; deny admin create | EXISTING intent / PROPOSED code | Matrix Student X |
| R25 Student | `stu03.waitlist.join` | Own + full section | Deny if capacity remains | EXISTING intent / PROPOSED code | US-012 |
| R10 Faculty | `stu03.roster.view` | Assigned section only | Deny enrolment record browse; deny A | EXISTING intent / PROPOSED code | Matrix Faculty V |
| R10 Faculty | Approve enrolment | — | **Denied** | PROPOSED REMOVE of any A | FLAG-004 / Table 17 |
| Registrar **or** mapped R0x | `stu03.term.cfg`, `stu03.window.cfg` | Tenant/college | Deny R25/R10 | UNRESOLVED | US-015/016; GAP-003/004 |
| Registrar **or** mapped R0x | `stu03.enrolment.create` | Tenant | Idempotent handover; deny cross-tenant | UNRESOLVED | US-001; GAP-005 |
| Registrar **or** mapped R0x | `stu03.enrolment.approve` | Delegated term/programme | SoD; no self-approve | UNRESOLVED | US-004; GAP-006 |
| R07/R08 **and/or** Registrar | `stu03.section.allocate` | Dept/course/section | Capacity enforced | UNRESOLVED | US-011; GAP-007 |
| Registrar **or** mapped R0x | `stu03.waitlist.promote` | Section | Ordered promotion | UNRESOLVED | US-013; GAP-008 |
| R26 Parent | STM-03 enrolment data | — | **No default STM-03 grant in matrix table** | EXISTING deny | Table 17 omits Parent on STM-03 |
| R28 Auditor | STM-03 AU | — | UNRESOLVED beyond STM-06 | FLAG-006 | GAP-017 |

---

## 4. Cross-domain authorization requirements (proposal)

| Workflow | Owning domain | STM may | STM must not |
| :--- | :--- | :--- | :--- |
| Course catalogue / prereq definitions | ACD-01/03 | Read authorized catalogue | Own parallel course master |
| Section capacity physical seat | ACD-07 / batch | Request allocation via contract | Bypass ACD constraints |
| Official results | EXM | Compose read models (STM-08) | Publish/alter marks |
| Fees gating enrolment | FIN | Check status via contract | Own ledger |
| Identity/roles | IAM | Consume role assignments | Self-assign roles (STM-07 deny) |
| Documents blobs | Storage + STM-06 | Metadata + signed URLs | Store binaries in Postgres |

---

## 5. Denials to lock (EXISTING matrix principles)

1. Student never verifies, approves, publishes official results, assigns roles, or edits audit.  
2. Uploader ≠ verifier (STM-06).  
3. Requester ≠ approver where approval required (STM-09).  
4. Tenant boundary never bypassed by College Admin.  
5. Portal is not a source of truth and not an authZ bypass (STM-10).  
6. API clients need explicit scopes (R27).  

---

## 6. Workflow dependencies

```
STM-02 offer accept → handover event
  → STM-03 enrolment.create (admin permission UNRESOLVED)
  → R25 register.execute
  → optional waitlist.join / waitlist.promote (promote UNRESOLVED)
  → roster.view (R10)
  → Intelligence student.enrolled.v1 (no extra role grant)
```

STM-04+ remain blocked on FLAG-002/007 and catalogue decisions even after STM-03 student paths exist.

---

## 7. What is explicitly not claimed

- This file does **not** change the authoritative `.docx` or validated `.csv`.  
- This file does **not** authorize applying migrations.  
- Generating this proposal does **not** approve it.
