# R31 Registrar — Staging IAM setup (Phase A prepare-only)

**Status:** PREPARED — **DO NOT APPLY** until owner authorizes Phase B.  
**Date prepared:** 2026-11-15  
**Target:** Development Supabase project only (`azulbaxszsqxymqmlwfe`). Production forbidden.

## Purpose

Add canonical catalogue role **R31 Registrar** to IAM so real users can be assigned Registrar without using College Admin as a stand-in.

## Owner decisions (D10 / D12c)

- Registrar is R31, campus-scoped (IAM `college_id`), separate from Academic Admin.
- Approvals by delegation only; no delete; same actor cannot verify and approve in one workflow.
- Seed is prepared here; apply only after Phase B approval.

## Steps (apply only after Phase B approval)

1. Confirm authoritative catalogue row in `docs/architecture/Security/RBAC_Combined.docx`: `R31 | Registrar | College/Campus (assigned campus)`.
2. Extend IAM role template seed (source: `database/migrations/Scripts_V3.0/80_seed.sql` / `iam.seed_system_roles`) to include:
   - code: `REGISTRAR`
   - catalogue_id: `R31`
   - name: `Registrar`
   - kind: `staff`
   - scope_note: `College/Campus (assigned campus)`
3. Map **only** STM action-level permissions from the Registrar (R31) rows in RBAC_Combined (C/U/RV; A/R when delegated). **No** ACD/LMS/EXM/HRM/FIN grants. **No** delete.
4. For each pilot tenant, run seed/refresh so `iam.roles` contains `REGISTRAR` / R31.
5. Assign a pilot user via `iam.role_assignments` with **non-null** `college_id` (assigned campus).
6. Create an active `iam.delegations` row (or equivalent) covering STM A/R keys used by services (`STM-06.DOCUMENTS.A`, `STM-04.LIFECYCLE.A`, `STM-03.ENROLMENT.A`, `STM-05.GUARDIAN.A`) for that user when approvals are required.
7. Restart STM services. Confirm gateway forwards `X-User-Role=REGISTRAR` (or `R31`) and `X-College-Id=<campus>`.
8. **Temporary flag:** `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` may be set `true` **only on staging** until first real college onboarding; default in code is OFF. Owner sets expiry date at onboarding.

## Expected results

- Registrar actors with campus + delegation can perform STM A/R paths in document / lifecycle / enrollment / guardian.
- College Admin / Institution Admin **cannot** satisfy Registrar checks when the legacy flag is OFF.
- Academic Admin / ACD / LMS rights are unchanged and not granted to R31.
- Fail-closed without delegation or without campus.

## Rollback

1. Unset `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` if it was enabled.
2. Revoke `iam.role_assignments` for REGISTRAR/R31 pilot users.
3. Soft-delete or remove the REGISTRAR role row for the tenant if seed must be reversed (owner-approved SQL only).
4. Do not drop STM tables or weaken RLS as part of this rollback.

## Out of scope

- Frontend `resolve-role.ts` / BFF Registrar mapping (queue item **1c**).
- Live malware scanner; Supabase Storage Phase B.
- Non-STM approvals (exam section, faculty appointments, exits, custom).
