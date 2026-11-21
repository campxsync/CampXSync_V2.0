# Next Task

**STATUS: Wave UI-1 live Platform desks shipped (await approve). Wave F still pending approve; then CADM. Under-18 / R27 / Kafka remain deferred.**

**Owner:** Niranjan V.G. — `APPROVED IADM-03-US-009` + `APPROVED IADM-03-US-008` 2026-11-21; authorized IADM Live Platform Desks plan (Wave UI-1).

## Just completed

| ID | Status |
|---|---|
| IADM-03-US-009 two-person appoint | **APPROVED** — commit `128833a` |
| IADM-03-US-008 break-glass | **APPROVED** — commit `41f46be` |
| **Wave UI-1** Identity & Access + Ops & Audit Search | **completed** (implementation) — STOP for `APPROVED WAVE-UI-1` |

### Wave UI-1 evidence (DEV)

- Nav: **Identity & Access** → `/platform/identity`; **Ops & Audit Search** → `/platform/ops` (IADM Stories removed).
- `/platform/iadm` redirects to `/platform/identity`.
- BFF: `/api/platform/identity/**`, `/api/platform/ops/**` → Gateway → IADM-03 / IADM-08.
- Smoke (Platform Admin test-session): quorum/break-glass/ops health/audit-search/self-check all **200**; desks render without auth errors; tsc clean.

## Next authorized options

1. `APPROVED WAVE-UI-1` (or `APPROVED WAVE-UI-1 COMMIT`) then Wave UI-2 (Tenant Lifecycle + Platform Config).
2. `APPROVED WAVE-F` then CADM Step 0.

## Still deferred

| ID | Status |
|---|---|
| IADM-05-US-007 under-18 consent | Legal (D-RBAC-M17) |
| R27 / adm03 / Kafka | Unchanged |

## Rules

DEV only. Permission keys. Local commits; push on request.
