# R31 Registrar — Staging IAM setup (Phase A prepare-only)

**Status:** SUPERSEDED for pilot apply — use `docs/runbooks/R31_PILOT_REGISTRAR_ACTIVATION.md` (Path C Phase 2 is implemented in `268c036`). This file remains historical prepare-only context.  
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
6. Prepare the required active `iam.delegations` row(s) covering STM A/R via Path C M1 (active REGISTRAR/R31 delegation). Live adapter is implemented (`HttpIamDelegationPort`); set `CAMPX_IAM_DELEGATION_BASE_URL`. Exact pilot SQL: `docs/runbooks/R31_PILOT_REGISTRAR_ACTIVATION.md`.
7. Restart STM services. Confirm gateway forwards `X-User-Role=REGISTRAR` (or `R31`) and `X-College-Id=<campus>`.
8. **Temporary flag:** `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` may be set `true` **only on staging**; default in code is OFF. It is a temporary compatibility mechanism for the explicitly approved legacy Registrar stand-in checks only. It must never grant permissions beyond the applicable STM action-level matrix, and must never bypass assigned-campus checks, active-delegation checks for A/R, same-actor verification/approval segregation, tenant isolation, or any other authorization check. It must not create ACD/LMS/EXM/HRM/FIN permissions or confer Academic Admin rights.
9. **Flag expiry:** the flag must be disabled at the earlier of (a) the first real-college onboarding or (b) **2026-12-31**. Record the actual disablement date and remove the staging environment setting. Do not extend the deadline without a new explicit owner decision.

## Expected results

- Registrar actors with campus + delegation can perform STM A/R paths in document / lifecycle / enrollment / guardian.
- College Admin / Institution Admin **cannot** satisfy Registrar checks when the legacy flag is OFF.
- Academic Admin / ACD / LMS rights are unchanged and not granted to R31.
- Fail-closed without delegation or without campus.
- The legacy stand-in flag does not bypass delegation, campus, tenant, or segregation-of-duties checks.
- The flag is disabled at the earlier of first real-college onboarding or 2026-12-31.

## Pre-apply authorization gates

Before any apply action, obtain a separate explicit owner authorization that identifies the development project and the exact IAM seed/user-assignment steps approved. Review the delegation design note first; approval of this runbook alone does not approve an adapter or any cross-schema/service-contract work. Confirm the legacy flag is OFF by default and, if temporarily enabled, that its only effect is the approved legacy role-recognition path while all other authorization controls remain enforced.

## Rollback

1. Unset `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` if it was enabled.
2. Revoke `iam.role_assignments` for REGISTRAR/R31 pilot users.
3. Soft-delete or remove the REGISTRAR role row for the tenant if seed must be reversed (owner-approved SQL only).
4. Do not drop STM tables or weaken RLS as part of this rollback.

## Out of scope

- Frontend `resolve-role.ts` / BFF Registrar mapping (queue item **1c**).
- Live malware scanner; Supabase Storage Phase B.
- Building or enabling the live IAM delegation adapter before separate owner approval of its design note.
- Non-STM approvals (exam section, faculty appointments, exits, custom).
