# CampXSync – Admin RBAC Redesign Draft (v1)

**Document:** `docs/architecture/Security/CampXSync_Admin_RBAC_Redesign_Draft_v1.md`  
**Date:** 2026-11-19  
**Status:** DRAFT for owner review — **not** catalog/seed/DB change authorization  
**Extends (does not replace):** `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` (2026-11-15)  
**New matrix deliverable:** `CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v2.md`  
**Architecture companions:** IADM / CADM Architecture Design v2.0 (owner Documentation set)  
**Security companion (separate):** Security Findings Register v1

---

## 1. Purpose

Redesign only the **Administration** portion of the enterprise RBAC matrix so it matches the IADM / CADM architecture and owner decisions (2026-11-18 / 2026-11-19).

This draft:

- Keeps the existing **R01–R31** role catalogue and STM / ACD / EXM / FIN / HRM / CAM matrices as the baseline.
- Replaces outdated “Platform Admin / College Admin blob” sections with **IADM-01…08** and **CADM-01…09** ownership.
- Defines **permission ceilings** for admin roles after narrowing.
- Lists what must be covered by other roles when grants are removed.
- Does **not** rename live `adm*` keys, edit seeds, or change Postgres yet.

---

## 2. What stays vs what changes

| Keep from current enterprise matrix | Change in this redesign |
|---|---|
| Role codes R01–R31 (incl. Registrar R31) | Admin hierarchy: remove Tenant Admin above R03 |
| STM-01…STM-10 action rows | Map Platform / College Admin capability tables → IADM / CADM services |
| Hybrid RBAC + ABAC + scope rules | Narrow R03 to IADM boundary; narrow R04 to CADM boundary |
| Standard permission vocabulary (V/C/U/D/A/R/P/X/E/I/AS/RV/AU/CFG/ADM) | Add coverage map before any grant removal |
| Functional-module ownership (ACD, LMS, EXM, FIN, HRM, CAM) | College Admin must not keep **M** on Academic / Exam / Finance / HR as if CADM owned them |

---

## 3. Owner decisions that govern this draft

| ID | Decision | Effect on RBAC |
|---|---|---|
| IADM-D1 | Platform Super Admin ops described inside IADM | R01 (and where applicable R02) appear on cross-institute IADM rows |
| IADM-D2 | Institute Super Admin = Institution Admin **R03** | R03 is the primary institute-scoped IADM operator |
| IADM-D3 | Narrow Institution Admin to IADM boundary | Strip non-IADM grants from R03 (after coverage map) |
| IADM-D7 | Narrow Tenant Admin → R03; no `*` wildcard | No role may hold `*` or outrank R03 inside a tenant |
| CADM-D3 | Narrow College Admin (R04) to CADM boundary | Strip Academic/Exam/Finance/HR “own the module” grants from R04 |
| CADM-D1 | Timetable = CADM-09 (existing :8087 service) | Timetable admin rights under CADM-09, not a second admin domain |
| IADM-D5 | `adm03` API clients dormant / outside IADM-CADM | Leave R27 / API-client grants untouched for now |
| IADM-D6 / CADM-D4 | Security findings separate | Defects (SEC-*) are not fixed by this matrix draft |

---

## 4. Admin actor model (target)

| Matrix code | Display name | Scope | Ceiling after redesign |
|---|---|---|---|
| **R01** | Super Admin (Platform Super Admin) | Cross-institute | IADM cross-tenant ops + shared-platform technical admin where already assigned |
| **R02** | System Admin | Platform / technical | Platform technical configuration, ops, shared services |
| **R03** | Institution Admin | One institute (tenant) | **IADM boundary only** |
| **R04** | College Admin | One college (within IADM delegation) | **CADM boundary only** |
| *(retire / collapse)* | Tenant Admin (`*`) | — | **No longer a superior role.** Align to R03; remove `*` (IADM-D7) |
| **R05+** | Principal, Dean, HOD, Registrar, … | As today | Absorb work removed from R03/R04 where the coverage map assigns it |
| **R31** | Registrar | Assigned campus | Unchanged STM posture; not widened by this draft |

---

## 5. Target hierarchy

```
R01 Super Admin (platform / cross-institute)
 └── R02 System Admin (platform technical)
      └── R03 Institution Admin (IADM, one institute)   ← Tenant Admin collapses here
           └── R04 College Admin (CADM, one college)
                └── R05 Principal / R06 Vice Principal / …
                     └── functional roles (Dean, HOD, Faculty, Registrar R31, …)
```

Rules:

1. No `*` wildcard on any human admin role after redesign.
2. R03 cannot grant itself permissions outside IADM.
3. R04 cannot override IADM-set configuration (IADM-02 wins; fail closed).
4. Delegation cannot invent permissions the delegator does not hold.

---

## 6. Coverage-map template (required before catalog change)

| Permission key | Today’s holder | Keep on R03? | Keep on R04? | If removed → new holder | Service | Owner decision |
|---|---|---|---|---|---|---|
| `…` | R04 | No | No | R08 / R31 / … | ACD-0x | Pending |

**Hard rule:** Do not remove a live grant until the coverage map names a surviving role (or explicitly accepts “no access”).

---

## 7. Document control

| Version | Date | Change |
|---|---|---|
| Draft v1 | 2026-11-19 | Initial Admin RBAC redesign outline after IADM/CADM v2.0 and IADM-D7. |
| Draft v1 (repo) | 2026-11-20 | Checked into `CampXSync_V2.0` with companion matrix v2. |
