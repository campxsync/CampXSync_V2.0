# CampXSync — RBAC Gap and Decision Register

**Audit ID:** `RBAC-FOUNDATION-2026-11-07`  
**Status:** All rows require product/security approval before implementation  
**Rule:** Proposed resolution is **not** an approved grant

| Gap ID | Module/domain | Workflow | Current role | Current permission | Required scope | Gap category | Evidence | Security impact | Functional impact | Proposed resolution | Approval required | Dependent modules | Test implications |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- | :--- |
| GAP-001 | Enterprise catalogue | STM principal admin identity | Registrar (prose) | None in R01–R30 | Tenant + college + programme/term | Missing | Matrix Table 3/17; FLAG-001; Scripts CSV has 0 REGISTRAR | Critical — no canonical admin actor | Blocks STM-01–07,09 staff paths | **Proposal:** Add catalogue role (e.g. R31 Registrar) **or** map duties to R04/R05 with explicit STM permissions | Yes | STM-01–10, ADM | Negative: deny unmapped REGISTRAR string; positive after mapping |
| GAP-002 | Enterprise catalogue | Department operations | Department Staff (prose) | None | Department | Missing | Matrix STM-03/09 posture | High — ambiguous staff access | Config/process without identity | **Proposal:** Map to R08/R11/R10 with scoped perms **or** new role | Yes | STM-03,09 | Department IDOR negatives |
| GAP-003 | STM-03 | Configure academic terms | Unassigned | CFG gap | Tenant/college | Unassigned / ambiguous | US-016; matrix CFG note; CSV codes R10;R25 only | High | Terms cannot be configured under catalogue | **Proposal:** Grant CFG on `stu03.terms` to approved admin role only | Yes | STM-03, ACD calendar | Deny R25/R10 CFG |
| GAP-004 | STM-03 | Configure registration windows | Unassigned | CFG gap | Tenant/term | Unassigned | US-015; FLAG-002 related | High | Windows cannot open | Same as GAP-003 for windows | Yes | STM-03 | Window outside role denied |
| GAP-005 | STM-03 | Convert admission → enrolment (staff) | Registrar C | Unassigned | Tenant + application | Unassigned | US-001; matrix Registrar C/U/RV | Critical | Handover staff API blocked (`POLICY_UNASSIGNED`) | **Proposal:** Permission `stu03.enrolment.create` to approved role; keep student from creating peers | Yes | STM-02, STM-03 | Cross-tenant create denied |
| GAP-006 | STM-03 | Approve/reject/suspend enrolment | Registrar A when delegated | Unassigned | Term/programme + delegation | Ambiguous + unassigned | US-004; matrix A when delegated | Critical | No status authority | **Proposal:** Define delegated A on R0x + SoD (no self-approve) | Yes | STM-03 | Self-approval negative |
| GAP-007 | STM-03 | Allocate students to sections | HOD/Dean scoped (ownership) vs CSV R10;R25 | Unassigned | Course/section | Conflicting sources | US-011; Table 3 vs CSV codes | High | Allocation frozen | **Proposal:** Choose R07/R08 and/or Registrar; document exclusive scope | Yes | STM-03, ACD-07 | Wrong-section allocate denied |
| GAP-008 | STM-03 | Waitlist promote | Implied staff | Unassigned | Section | Unassigned | US-013 | High | Waitlist cannot clear | **Proposal:** Assign promote permission to approved admin | Yes | STM-03 | Capacity race tests |
| GAP-009 | STM-03 | Advisor approval | Faculty/Advisor label | Matrix Faculty V only | Assigned advisees | Ambiguous / FLAG-004 | FLAG-004; Table 17 Faculty V | Medium if wrongly granted A | Workflow uncertainty | **Proposal:** Confirm **no Advisor A** for enrolment **or** add explicit RV/A with SoD | Yes | STM-03 | Ensure R10 cannot A |
| GAP-010 | STM-02 | Applicant identity | R25 overloaded / R31 invented | Invented | Own application | Conflicting / unimplemented correctly | `AdmissionsPermissionEvaluator.isApplicant`; STM-02 RBAC audit | Critical | Applicant≠Student boundary risk | **Proposal:** Approve Applicant role code or APPLICANT claim separate from R25 | Yes | STM-02, STM-07 | Applicant denied student APIs |
| GAP-011 | STM-02 | Admissions admin/approver/verifier | R32/R33/aliases invented | Invented | College/dept | Conflicting | `AdmissionsPermissionEvaluator` role sets | Critical | Runtime auth ≠ catalogue | **Proposal:** Replace invented codes with approved catalogue mapping | Yes | STM-02 | Remove R32/R33 unless approved |
| GAP-012 | Scripts_V3.0 | Permission namespace for STM | N/A | No `stu*` codes | N/A | Missing | `rbac_role_permissions.csv` prefix analysis | Critical | Cannot bind RLS/Java to codes | **Proposal:** Add `stu01`–`stu10` permission families | Yes | All STM | Permission enumeration tests |
| GAP-013 | Scripts_V3.0 | R01/R02/R27/R30 seed rows | Catalogue exists in matrix | Missing CSV rows | Platform/API/guest | Missing | Scripts CSV missing IDs | High | Platform roles incomplete in seed | **Proposal:** Seed missing catalogue mappings | Yes | Gateway/IAM | Platform role regression |
| GAP-014 | Scripts_V3.0 | EXTRA roles | TENANT_ADMIN, ACADEMIC_ADMIN, EMPLOYEE | Present | Unclear vs R01–R30 | Conflicting | Scripts CSV EXTRA | Medium | Dual catalogues | **Proposal:** Map EXTRA→R0x or retire | Yes | ADM/ACD | No silent dual grants |
| GAP-015 | STM-05/06 | Consent self-grant | Student/Guardian | Pending | Consent purpose | FLAG-003 | FLAG-003 | High | Privacy risk | **Proposal:** Keep deny self-grant; define consent owner role | Yes | STM-05,06 | Revocation tests |
| GAP-016 | STM-06 | Update/delete/retention | Verifier/Registrar | Pending | Document | FLAG-005 | FLAG-005 | High | Retention/legal | **Proposal:** Explicit D/retention policy | Yes | STM-06 | Delete denied default |
| GAP-017 | STM-06+ | Auditor beyond docs | R28 | AU only on STM-06 in matrix | Audit scope | FLAG-006 | FLAG-006; Table 17 | Medium | Over/under audit access | **Proposal:** Define R28 AU per STM service | Yes | STM-01–10 | Auditor write denied |
| GAP-018 | STM-04/07/08/09 | Open matrix policy items | Multiple | Pending | Workflow-specific | FLAG-007 | Matrix “Policy items requiring confirmation” | High | Lifecycle/provisioning/history/requests blocked | **Proposal:** Close each matrix open decision with written approval | Yes | STM-04,07,08,09 | Per-workflow suites |
| GAP-019 | CFG | Configure permission absent | Matrix silent / FLAG-002 | CFG | Service config | FLAG-002 | FLAG-002 | High | Cannot configure portals/docs/lifecycle | **Proposal:** Explicit CFG assignees per service | Yes | STM-04,06,07,09,10 | CFG deny-by-default |
| GAP-020 | Enforcement | Java hardcoded roles vs IAM | Mixed strings | Bypass of coded perms | N/A | Unimplemented | Controllers; evaluators | High | Drift and bypass risk | **Proposal:** Central permission evaluator using IAM grants | Yes | All services | Contract tests Gateway↔Java↔RLS |
| GAP-021 | RLS | Section/dept scope for STM-03 | Tenant RLS only (draft) | Incomplete | Section | Unimplemented | Unapplied STM-03 SQL; ACD contrast | High | IDOR if app check skipped | **Proposal:** After grants, add ownership/section policies | Yes | STM-03 | Live RLS negatives |
| GAP-022 | Gateway | Fine-grained STM authZ | JWT/HMAC only | N/A | Route-level | Unimplemented by design today | `ReverseProxyHandler` | Medium | Relies entirely on services | **Proposal:** Keep Gateway authN; mandate service authZ parity tests | Yes | api-gateway | Unsigned request 401 |
| GAP-023 | HMAC | Empty secret skip verify | Any | N/A | Internal | Fail-open risk | `GatewayHmacVerifier` | High | Spoofable identity headers if misconfigured | **Proposal:** Fail closed when deployed and secret missing | Yes | All services | Deployed-mode test |
| GAP-024 | STM-03 | Canonical CSV vs matrix posture | R10;R25 stamped on admin stories | Contradicts Registrar C/U/RV/A text | N/A | Conflicting | Validated CSV STM-03 rows | Critical | Implementers may wrongly grant R10 admin | **Proposal:** Re-stamp CSV after GAP-001 decision; until then treat admin as unassigned | Yes | STM-03 backlog | Tests assert R10 cannot configure |

---

## Decision pack requiring explicit user approval

1. **D-RBAC-01:** Create Registrar (and related) catalogue roles **or** map STM admin duties onto existing R0x.  
2. **D-RBAC-02:** Approve `stu*` permission namespace and seed plan.  
3. **D-RBAC-03:** Confirm STM-03 Advisor approval is **not required** (recommended by current matrix Faculty=V) **or** define Advisor A/RV.  
4. **D-RBAC-04:** Replace or ratify STM-02 invented R31/R32/R33.  
5. **D-RBAC-05:** Close matrix open decisions (guardian, admissions SoD, provisioning, corrections, service-request chains, support role).  
6. **D-RBAC-06:** Resolve EXTRA Scripts roles vs R01–R30.  
7. **D-RBAC-07:** Authorize when (if ever) to apply STM-03 DDL/RLS on `azulbaxszsqxymqmlwfe`.
