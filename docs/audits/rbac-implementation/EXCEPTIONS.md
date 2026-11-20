# RBAC Implementation Exceptions Register (Retain When Uncertain)

**Document:** `docs/audits/rbac-implementation/EXCEPTIONS.md`  
**Governing Decision:** `CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` §22.1 Rule 3 ("Retain when uncertain")  
**Execution Timestamp:** 2026-11-20T16:33:00+05:30  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`)  

---

## Plain-Language Rule & Context

Per matrix §22.1 Rule 3:
> "Retain when uncertain. Remove a key from R03/R04 only when (a) the matrix names another holder and (b) no live IADM/CADM service uses it. Otherwise keep it and log it on the exceptions list. Uncertainty is never a reason to stop."

Below is the definitive register of permission keys retained on **R04 College Admin** and **R03 Institution Admin** during Phase 2.

---

## Retained Keys Register

| Permission Key | Plain-Language Meaning | Role Retaining Key | Who Still Needs It / Why Retained | Evidence / Code Location | Review Timing |
|---|---|---|---|---|---|
| `adm01.view`, `adm01.view.all` | Institute & Tenant Lifecycle: view & view all | **R04 College Admin** | College Admin needs to view parent institute details on college profile desk; also acts as fallback permission in `stu.application_documents` RLS policies. | `stu.application_documents` RLS policy in `20261105000200_stm01_applicant_documents_rls.sql:26, 61` [CONFIRMED] | Revisit during STM-06 Documents Phase 4 cleanup. |
| `adm02.manage`, `adm02.write` | Campus & Rooms: manage & write | **R04 College Admin** | CADM-05 Campus Resources & Operations provisional room master. Room master ownership is deferred against Facilities Manager (R24). RLS on `core.rooms` requires `adm02.write` and `adm02.manage`. | Matrix §9 line 234; `60_rls.sql:415, 417` [CONFIRMED] | Revisit when CADM-05 Facilities service is built. |
| `adm03.view`, `adm03.view.all`, `adm03.write` | External API Clients & Messaging: view & write | **R04 College Admin** & **R03 Institution Admin** | Messaging providers (`cam.providers`) and integration clients (`iam.api_clients`). Governed by owner decision IADM-D5: `adm03` remains dormant outside IADM/CADM until activation. | Matrix §2 line 43, §14 line 429; `60_rls.sql:1821-1832, 1948-1956` [CONFIRMED] | Revisit upon activation of API Client module (R27). |
| `adm04.view`, `adm04.view.all` | College Profile & Directory Attributes: view & view all | **R04 College Admin** & **R03 Institution Admin** | Directory attributes and college profile operational reading under CADM-01 / CADM-04. | `PostgresCollegeProfileRepository.java` [CONFIRMED] | Permanent CADM operational view. |
| `adm05.manage` | Configuration Settings: manage | **R04 College Admin** | Granted provisional to R04 to configure college-level feature overrides on `cfg.feature_overrides` and `cfg.setting_values`. | Matrix §22.2 finding (line 691); `60_rls.sql:176, 187` [CONFIRMED] | Permanent CADM configuration authority. |
| `adm06.manage`, `adm06.write`, `adm06.review` | Colleges, Accreditations & Sequences: manage, write, review | **R04 College Admin** | CADM-01 College Profile and operational number sequence management on `core.number_sequences` and `core.college_accreditations`. | `60_rls.sql:26-28, 96-98` [CONFIRMED] | Permanent CADM college operations. |
| `adm07.manage`, `adm07.write`, `adm07.review` | Departments & Academic Units: manage, write, review | **R04 College Admin** | Department organizational setup and maintenance for college courses under CADM-01/04. | `core.departments` in `60_rls.sql:31-33` [CONFIRMED] | Permanent CADM college operations. |
| `adm08.manage`, `adm08.write`, `adm08.review` | User Accounts & Profiles: manage, write, review | **R04 College Admin** | College User Administration (CADM-02) and Student & Faculty Admin Directory (CADM-04) within college boundary. | `iam.user_profiles` in `60_rls.sql:126-128`; `UserProfileRepository.java:238` [CONFIRMED] | Permanent CADM user admin. |
| `adm09.manage`, `adm09.write`, `adm09.review` | Roles, Permissions & Delegations: manage, write, review | **R04 College Admin** | College Roles & Access Admin (CADM-03) and departmental delegations within CADM ceiling. | `iam.role_assignments`, `iam.delegations` in `60_rls.sql:151-153, 396` [CONFIRMED] | Permanent CADM role admin. |
| `*.view`, `*.view.all` (ACD, LMS, EXM, FIN, HRM, CAM) | Domain Operational Views | **R04 College Admin** | College Admin operational desks require visibility across enrolled courses, attendance statistics, exam schedules, fee receipts, staff directory, and announcements. Module `manage` and `write` are stripped; `view` is preserved. | Matrix §10, §11.1–§11.6, §22.1 Rule 3; `Part-B_permission-map.csv` [CONFIRMED] | Permanent operational oversight view. |
| `core.directory.view`, `cfg.view`, `wf.view`, `core.comment`, `rpt.*`, `core.pii.read` | Shared Core & Reporting Keys | **R04 College Admin** | Core operational functions, audit comments, student PII read (college-scoped and audited), and college reporting (CADM-06). | Matrix §22.3 shared-key defaults table [CONFIRMED] | Permanent shared defaults. |
| `core.pii.write`, `core.write`, `wf.write`, `core.view.all` | Shared Institution-Wide Keys | **R03 Institution Admin** | Institution Admin operates institution-wide; retains broad core write, PII write, workflow write, and global views. (Removed from R04). | Matrix §22.3 shared-key defaults table [CONFIRMED] | Permanent IADM institution operations. |
| `doc.view` | Document Verification View | **R03 Institution Admin** | Institution Admin retains read-only visibility over institutional document repositories after transferring `doc.admin`, `doc.manage`, `doc.write` to R31 Registrar. | Matrix §22.3 shared-key defaults table [CONFIRMED] | Permanent IADM supervisory view. |

---

## Conclusion

Every key retained on R04 College Admin and R03 Institution Admin is traced to:
1. Matrix v3.0 §22.3 shared-key defaults.
2. CADM-01…CADM-09 service operational boundaries.
3. Live RLS policy dependencies on `core.*`, `iam.*`, `cfg.*`, and `stu.*` tables.

Zero keys were kept arbitrarily; all additions before removals ensure no functional regression across institutional and college administrative desks.
