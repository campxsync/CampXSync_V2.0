# IADM-03 Platform Identity & Access Governance — Technical Documentation

**Service:** `iadm-03-identity-access-service`  
**Port:** `8101`  
**Route Prefix:** `/api/v1/iadm/03` (API Gateway `:8080` -> `http://localhost:8101/api/v1/iadm/03`)  
**Schema:** `iadm03` (owned governance records only; FORCE RLS on every table)  
**Permission Key Family:** `iadm03.*` (12 additive keys; existing `iam.*` and legacy `adm*.*` keys untouched)  
**Database Target:** DEV Supabase (`azulbaxszsqxymqmlwfe`)

---

## 1. Architecture & Non-Negotiable Boundaries

- **Request path:** `Admin UI (Next.js 15) -> BFF (/api/platform/identity/**, /api/institution/access/**) -> API Gateway (:8080) -> IADM-03 (:8101) -> Supabase PostgreSQL`
- **No per-request runtime dependency:** Gateway, Java microservices, and PostgreSQL RLS enforce authentication and authorization independently. Stopping IADM-03 does not affect any other service.
- **Record-only V2 grants + Coexisting Legacy V1.4 routes (D-03 owner-open):**
  - **V2 (`iadm03.*`):** Record-only (`status = RECORDED`, `"recorded, not applied"`). V2 endpoints never mutate `iam.permissions`, `iam.role_permissions`, `iam.role_template_permissions`, or `iam.role_assignments`, and never call legacy V1.4 services.
  - **Legacy V1.4 (restored until D-03 is resolved):** `/grants/appoint-r03`, `/grants/assign`, `/grants/revoke`, `/grants/r03-change/{id}/decide`, `/catalog-changes` (`GET`/`POST`), and `/catalog-changes/{id}/decide` remain marked **Legacy V1.4** in `Iadm03Controller` and inventoried in `LegacyV14Routes` (exclusive grant/catalogue mutators). Legacy services never write to `iadm03.*` tables.
  - **Permission Register label:** The 20 `iam.*` keys are labeled `IADM-03 V1.4 (legacy)` in the computed register (not Unknown).
  - **API proof:** `scripts/demo/iadm03_v14_api_hash_proof.mjs` — V2 decisions leave catalogue/grant MD5 hashes equal; only legacy mutators listed above can change grants.
- **Computed-on-request views:** The permission-key register, current holders, and R01–R39 role matrix are computed live on each request from `iam.permissions`, `iam.role_template_permissions`, `iam.role_permissions`, `iam.role_assignments`, and RBAC v3.0 — never stored as snapshots.
- **Shared audit (`audit.log_event` / `audit.events`):** Every governed read and mutation (plus every denial) writes to `audit.log_event` in the same transaction. If audit fails, the operation fails closed (`503 IADM03_AUDIT_UNAVAILABLE`) and rolls back.
- **Structured logging:** Uses `CampXLogger` with `correlationId` / `traceId`; never logs JWTs, raw auth claims, passwords, or personal data.

---

## 2. Additive Permission Keys (`iadm03.*`)

| Key | R01 | R02 | R03 (own inst.) | R04 (own college) | R28 (read-only) |
|---|---|---|---|---|---|
| `iadm03.register.view` | Y | Y | — | — | Y |
| `iadm03.matrix.view` | Y | Y | — | — | Y |
| `iadm03.holders.view` | Y | Y | Y | — | Y |
| `iadm03.grant.request.submit` | Y | Y | Y | Y | — |
| `iadm03.grant.review` | Y | Y | — | — | — |
| `iadm03.grant.approve` | Y | — | — | — | — |
| `iadm03.review.manage` | Y | Y | — | — | — |
| `iadm03.delegation.manage` | Y | — | Y | — | — |
| `iadm03.registration.submit` | Y | Y | — | — | — |
| `iadm03.registration.approve` | Y | — | — | — | — |
| `iadm03.handover.view` | Y | Y | Y | — | Y |
| `iadm03.evidence.view` | Y | Y | — | — | Y |

No wildcard key; no apply-grant key; no other role (`R05`–`R27`, `R29`–`R39`) receives any `iadm03.*` key.

---

## 3. Owned Schema (`iadm03`) — FORCE RLS

1. `iadm03.grant_requests` — Grant-change requests (`REQUESTED` -> `VALIDATED` -> `APPROVED`/`REJECTED` -> `RECORDED`)
2. `iadm03.grant_decisions` — Immutable decision + before/after diff (`recorded_only = true` enforced by CHECK)
3. `iadm03.access_review_campaigns` — Periodic access-review campaigns
4. `iadm03.access_review_items` — Per-subject review decisions (`ATTEST`, `REVOKE`, `EXCEPTION`, `OVERDUE`)
5. `iadm03.delegations` — Scoped, time-bound delegation records (expiry computed on read)
6. `iadm03.service_registrations` — Additive service permission-key manifest proposals & approvals

All 6 tables have `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY`, plus `DELETE` denied (`USING (false)`).
