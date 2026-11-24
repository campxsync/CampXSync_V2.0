# IADM-03 S0 Build Report (US-002…007)

**Date:** 2026-11-24  
**Owner authorization:** Decision pack **Approved** (plain “Approved”, 2026-11-24)  
**Branch:** `feature/java-backend-integration`  
**Environment:** DEV Supabase `azulbaxszsqxymqmlwfe` only  
**Commits:** none yet (awaiting `APPROVED S0 COMMIT` if owner wants a local commit)

---

## Plain-language summary

IADM-03 (Platform Identity & Access Governance) now exists as its own Java service on port **8101**, reached through the API Gateway at **`/api/v1/iadm/03`**. Health is up. New permission keys (`iam.*`) and an idempotency table were applied on DEV. Existing R03 appointment, R25, Registrar delegation check, and MAC R03 persona were **not** rebuilt. The dormant `adm03` family stays unused.

---

## Story status

| Story | Plain name | API-ready | Screen-ready | Demo-verified | Evidence |
|---|---|---|---|---|---|
| US-001 | Read-only verification | n/a | n/a | **yes** (report accepted via Approved) | `S0_VERIFICATION_REPORT.md` |
| US-002 | Separate service + gateway + health | **yes** | n/a (no UI in S0) | **yes** (health 200) | `evidence/S0_HEALTH.json`, gateway route test |
| US-003 | Permission keys + grantor ceilings | **yes** | n/a | **yes** (unit matrix) | `AccessPermissionMatrixTest`, DEV catalogue |
| US-004 | Durable audit fail-closed | **yes** | n/a | **yes** (unit) | `DurableAuditFailClosedTest` |
| US-005 | Reuse iam storage + isolation helpers | **yes** | n/a | **partial** (API routes; live JWT catalogue smoke still for S1) | GET `/roles`, `/permissions` |
| US-006 | Idempotency scaffolding | **yes** | n/a | **partial** (table + begin route; full mutation replay in S2) | `iam.governance_idempotency` |
| US-007 | DEV demo seed / fixtures | **yes** | n/a | **partial** (SQL fixtures verified; headed multi-persona login → S1) | seed migrations + SQL checks |

Screens are intentionally **out of S0** (foundation, no UI). S1 owns role catalogue / profile screens.

---

## What was built

| Item | Location |
|---|---|
| Service module | `services/administration-services/iadm-03-identity-access-service/` |
| Port / prefix | **8101** / `/api/v1/iadm/03` |
| Gateway route | `GatewayConfig` → `http://localhost:8101/api/v1/iadm/03` |
| Stack scripts | `start_stack.ps1`, `check_stack.ps1`, `restart_service.ps1` (iadm-03) |
| Foundation migration | `database/migrations/20261124100000_iadm03_foundation.sql` (+ rollback) |
| Demo seed | `database/migrations/20261124100100_iadm03_demo_seed.sql` (+ rollback) |
| Permission matrix | `AccessPermissionMatrix` (20 `iam.*` keys; R27 deny; no `adm03`) |
| Grantor ceilings | `GrantorCeiling` (§21.1) |
| Durable audit | `DurableAuditWriter` → `audit.log_event` fail-closed |
| Idempotency table | `iam.governance_idempotency` |

---

## Owner-approved defaults applied

B1–B8 and D1–D6 / D11 / D13 / D15 recommendations from `S0_DECISION_PACK.md` (P1 = R02+R03 profile default; US-020 = remove/change only; port 8101; proposed `iam.*` keys).

**Not done in S0 (as planned):** FORCE RLS on all `iam.*`; access_requests table; gateway R03→college_admin fix (S2/US-021); UI desks; break-glass / two-person appointment / R27 create.

---

## Tests run

| Suite | Result |
|---|---|
| `mvn test` IADM-03 module | **PASS** (scaffold + matrix + audit fail-closed) |
| `mvn test -pl api-gateway -Dtest=Iadm03GatewayRouteTest` | **PASS** |
| Live health `GET :8101/api/v1/iadm/03/health` | **200** UP |
| 56-cell persona matrix (IADM-02 cfg keys) | **Not re-run this step** — additive grants only; **must re-run before S1 demo** |

---

## DEV SQL verification

- `iam.governance_idempotency` exists  
- 20 new `iam.*` governance keys present (including `iam.delegation.create` / `.revoke`)  
- Unprofiled invitee email `iadm03.invitee.unprofiled@campxsync.test` has **no** profile  
- Platform admins: 2× SUPER_ADMIN, 1× SYSTEM_ADMIN  
- R03 `maharani.campx@gmail.com` on MAC retained  

---

## Stop gate

S0 foundation is ready for **S1** (US-008…014 screens) after owner checkpoint.

Reply:

- `APPROVED S0` — proceed to S1  
- `APPROVED S0 COMMIT` — local commit then S1  
- `CHANGES S0: …` — rework  

**No push.**
