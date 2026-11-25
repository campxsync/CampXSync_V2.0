# IADM-06 US-016 — Preflight inventory and reconciliation

**Story:** Preflight inventory and reconciliation (IADM-06-US-016)  
**Mode:** Read-only · **Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration`  
**Environment:** DEV Supabase project `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev)

**Wording correction applied:** Blueprint line that said “IADM-05 is the first real consumer of the IADM-05 domain contract” is treated as **IADM-06 is the first real consumer of the IADM-05 domain contract**.

---

## 1. Working tree and branch

| Check | Result |
|---|---|
| Branch | `feature/java-backend-integration` (tracks origin) |
| HEAD at preflight | `b5de828` — docs(iadm05): record APPROVED S4 COMMIT hash 6d31085 |
| Dirty tracked files | None observed at session start |
| Untracked of note | `docs/user-stories/IADM-06-user-stories.csv`; prior evidence/classpath noise |
| Push | Not performed (owner rule: local commits only) |

---

## 2. Input documents (located / missing)

| Input | Location | Status |
|---|---|---|
| IADM-06 Detailed Blueprint V1.2 (Markdown) | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-06_Detailed_Blueprint_V1.2.md` | Found (document control says V1.2; title line still says V1.1) |
| IADM-06 Blueprint V1.2 (PNG) | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-06_Blueprint_V1.2.png` | Found |
| IADM-06-user-stories.csv (28 rows, S0–S4) | `docs/user-stories/IADM-06-user-stories.csv` | Found |
| CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md | `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` | Found |
| IADM_Architecture_Design.md v2.0 | `D:\CampXSync\Documentation\Platform_InstituteAdmin\IADM_Architecture_Design.md` | Found — §4 / §5.6 name **Platform Analytics & Usage Insights** |
| IADM-01 / 02 / 04 / 05 services | `services/administration-services/iadm-0{1,2,4,5}-*` | Found |
| Existing IADM-06 service / gateway / stack registration | — | **Not found** (expected) |

---

## 3. Conventions from IADM-01 / 02 / 04 / 05 → IADM-06 decision

| Concern | Convention found | IADM-06 decision |
|---|---|---|
| Directory | `services/administration-services/iadm-0N-<kebab>-service` | `iadm-06-platform-analytics-service` |
| Port | 8099 / 8100 / 8101 / 8102 / 8103 | **8104** (next free) |
| Gateway prefix | `/api/v1/iadm/0N` in `GatewayConfig.routeTable` (before legacy `/api/v1/admin`) | `/api/v1/iadm/06` → `http://localhost:8104/api/v1/iadm/06` |
| Package | `com.campx.iadm.<domain>` | `com.campx.iadm.analytics` |
| Main class | `Iadm0NApplication` | `Iadm06Application` |
| Maven | Parent `campx-sync-parent`; deps `campx-logger`, PostgreSQL, HikariCP, JUnit | Same |
| Permission keys | Newest pattern **`iadm0N.*`** (IADM-04/05) | **`iadm06.*`** |
| Migrations | `database/migrations/YYYYMMDDHHMMSS_iadm0N_*.sql` + paired rollback | Same; DEV target comment required |
| RLS style | `ENABLE` + **`FORCE ROW LEVEL SECURITY`** | Same on both IADM-06 tables |
| Request context | `GatewayHmacVerifier` + `UserSecurityContext`; `SET LOCAL ROLE authenticated` + `request.jwt.claims` | Same |
| Logging | `CampXLogger` / `CampXLoggerFactory` | Same |
| Frontend desk | `/platform/<desk>` + BFF `/api/platform/<desk>` → `fetchFromGateway`; `data-testid="iadm0N-..."` | `/platform/analytics`, `/institution/analytics`, `/platform/analytics/audit`; `iadm06-*` |
| Stack scripts | `start_stack.ps1`, `check_stack.ps1`, `restart_service.ps1` | Register IADM-06 **:8104** |

**Route collision check (compliance item 1 of 2):** `/api/v1/iadm/06` is **absent** from `GatewayConfig.routeTable`. Legacy `/api/v1/admin` and academic `/api/v1/analytics*` routes are separate namespaces. **No collision** with legacy `/api/v1/admin/...`.

---

## 4. Shared audit (D9)

| Claim | Finding |
|---|---|
| Second audit store required? | **No** — reusable capability exists |
| Store | Schema `audit`, table `audit.events` |
| Writer API | `audit.log_event(...)` (SECURITY DEFINER) |
| Java pattern | IADM-04/05 `DurableAuditWriter` → same-TX `SELECT audit.log_event(...)`; fail-closed `503 *_AUDIT_UNAVAILABLE` |
| Grant | Already granted to `authenticated` / `service_role` |

**Decision:** Reuse `DurableAuditWriter` pattern. No second audit store. **Blocker cleared.**

---

## 5. Shared identity claims

| Item | Finding |
|---|---|
| User auth | Supabase Auth → BFF Bearer / test-session |
| Gateway | HMAC (`X-Gateway-Timestamp`, `X-Gateway-Signature`); forwards `X-User-Id`, `X-Tenant-Id`, `X-User-Role` |
| Downstream | HMAC verify; `UserSecurityContext`; never trust client-only tenant |
| DB session | `SET LOCAL ROLE authenticated`; `request.jwt.claims` |
| Permission check | `iam.has_permission(?)`; platform via `iam.is_platform_admin()` / `plat.platform_admins` |

---

## 6. Database target verification (blocker gate)

| Check | Result |
|---|---|
| Supabase MCP `list_projects` | Only **`azulbaxszsqxymqmlwfe`** (CampXSync-Architecture-Dev, ACTIVE_HEALTHY) visible |
| Production ref `ekspahbsbwdpraaqfqui` | **Not** targeted; forbidden |
| Schema `anl` | **Does not exist** (verified) |
| Legacy `rpt.dashboards` | Exists (Scripts_V3.0) — **do not reuse** as IADM-06 store (D10: retire/ignore for this module) |

**Gate:** PASS for DEV migrations via MCP `project_id=azulbaxszsqxymqmlwfe` only.

---

## 7. IADM-01 / IADM-04 read contracts (aggregate fitness)

### IADM-01 (`:8099` `/api/v1/iadm/01`)

| Endpoint | Fields observed | Fit for M01–M06 |
|---|---|---|
| `GET .../institutes/summary` | `institutesByStatus`, `colleges`, pending lifecycle counts | Partial M01/M02 platform; **platform-admin only**; no per-institute college breakdown; no created-window |
| `GET .../institutes` | `id`, `code`, `name`, `status`, `created_at`, … | Row list — usable for client-side count but not an aggregate contract |
| `GET .../institutes/{id}/colleges` | college rows with `status`, `created_at` | Per-institute only |

**Gap (proven):** No single read-only aggregate contract covering colleges-per-institute, colleges-by-status across institutes, or created-in-window series for M05/M06.  
**Action authorized by kickoff:** add **minimal additive** `GET /api/v1/iadm/01/analytics-aggregates` in IADM-01 (separate commit; no behaviour change to existing endpoints).

**DEV seed snapshot (read):** institutes ACTIVE=12, ARCHIVED=2.

### IADM-04 (`:8102` `/api/v1/iadm/04`)

| Endpoint | Fields observed | Fit for M07–M10 |
|---|---|---|
| `GET .../plans` | plan catalogue (DEMO labelled) | Source for plan names; not count-by-plan |
| `GET .../subscriptions` | subscription rows with status | Platform list; not aggregate-by-plan/state |
| `GET .../usage?tenantId=` | per-tenant limits + usage; `incompleteData` | M09 per institute; requires `tenantId` |
| `GET .../reports/billing` | scoped billing summary | Not the M07–M10 catalogue shape |

**Gap (proven):** No aggregate “subscriptions by plan/state” or “subscription state trend” contract.  
**Action:** add **minimal additive** `GET /api/v1/iadm/04/analytics-aggregates` (separate commit). DEMO label must propagate for M07–M10.

**DEV seed snapshot (read):** subscriptions ACTIVE=14, CANCELLED=5 (DEMO lineage in service inserts).

---

## 8. IADM-05 domain contract (first consumer = IADM-06)

| Item | Finding |
|---|---|
| Resolve | `GET /api/v1/iadm/05/domain/resolve-policy?policyType=&domainCode=` |
| Auth keys accepted | `iadm05.domain.contract` **or** view/history keys (`classification.view`, `retention.view`, `policy.history`) |
| Success shape | `policyId`, `code`, `versionNo`, `policyType` — **exactly one** PUBLISHED; else 404 none / 409 conflict |
| Outcome report | `POST .../requests/{id}/outcomes` — tied to **governance requests**, requires `iadm05.domain.contract` |
| Policy types in schema | CLASSIFICATION, RETENTION, PRIVACY_CONSENT, EXPORT, ERASURE, SHARING |
| Domain filter | **Only RETENTION** joins `gov.retention_rules.domain_code` |

**Published policies on DEV today:** multiple PUBLISHED CLASSIFICATION (3+) and RETENTION (3: DEMO-RET-*). No EXPORT policy. Retention domain codes present: **`STM` only** — **no ANALYTICS**.

**Implications:**
1. Resolving bare CLASSIFICATION or bare RETENTION **fails closed** (multi-published conflict) — correct fail-closed behaviour for US-021.
2. Need **DEMO ONLY** published analytics-domain versions via IADM-05 data lifecycle (insert published RETENTION + `domain_code='ANALYTICS'`, and/or single PUBLISHED EXPORT policy) — **data, not IADM-05 code**.
3. **Finding (partial risk):** `reportOutcome` cannot express an analytics *dataset release* without a governance `request_id`. IADM-06 will resolve policy + write shared audit / store resolution on export metadata. Full “outcome via correlation on every dataset view” remains **partial** unless a future IADM-05 contract adds a non-request outcome path (**no IADM-05 code change in this build**).
4. Small-group **threshold** is not returned by resolve (only version identity). Threshold stays IADM-06 runtime setting gated by successful resolve (D4 DEFAULT). Production threshold remains **OWNER-OPEN**.

---

## 9. RBAC Auditor (R28) scope (matrix §8)

| Check | Result |
|---|---|
| IADM-06 cell | R01 M/E; R02 V/E; R03 V/E own institute; R04 —; **R28 V** |
| Audit-scope assignment beyond “V”? | **Not found** in matrix §8 for IADM-06 (contrast IADM-08 R28 “V/AU/E”) |
| BUILD DEFAULT | Auditor (R28) sees **platform aggregates read-only**; **no export** |

---

## 10. Existing ADM / analytics code (D10)

| Artifact | Disposition |
|---|---|
| Academic analytics service `:8092` `/api/v1/analytics*` | **Ignore** — college academic analytics, not IADM-06 |
| Legacy `rpt.dashboards` / `rpt.kpi_*` | **Do not reuse** for IADM-06; college/report schema |
| Monolith `/api/v1/admin` | **Do not modify** (owner rule) |
| New IADM-06 `anl.*` tables | **Create** — metric catalogue + export-request metadata only |

---

## 11. Service name check (D11)

| Source | Wording |
|---|---|
| IADM Architecture Design §4 / §5.6 | **Platform Analytics & Usage Insights** |
| Blueprint V1.2 title | Platform Analytics & Usage Insights |
| Older “Analytics & Monitoring” | **Not** in architecture v2.0 body examined |

**Report:** Retain **Platform Analytics & Usage Insights**.

---

## 12. Demo personas (reuse; invent no emails)

| Persona | Pattern from IADM-05 headed demo | Email default in scripts |
|---|---|---|
| Super Admin (R01) | `.env.audit` / seed `r01` | `campxsync@gmail.com` is College Admin in check_stack — R01 uses seed/`PLATFORM_ADMIN` vars (**TBC exact email in `.env.audit` at demo time; never print password**) |
| System Admin (R02) | `systemadmin.campx@gmail.com` | Found in `scripts/demo/iadm05_headed.mjs` |
| Institution Admin (R03) | `institution.iadm04.demo@campx.local` (+ second institute **TBC** at S1 seed) | Found |
| College Admin (R04) | `campxsync@gmail.com` | Found |
| Auditor (R28) | `auditor.iadm02.dev@campx.local` | Found |

Passwords: `.env.audit` / `.local/seed_accounts.json` only — never committed or printed.

---

## 13. Proposed permission keys + audit events (D6 — closes compliance item when seeded)

| Capability | Permission key | Audit event name |
|---|---|---|
| Platform aggregate view | `iadm06.platform.view` | `IADM06_PLATFORM_VIEW` |
| Own-institute view | `iadm06.institute.view` | `IADM06_INSTITUTE_VIEW` |
| Entitlement insight | `iadm06.entitlement.view` | `IADM06_ENTITLEMENT_VIEW` |
| Aggregate export request | `iadm06.export.request` | `IADM06_EXPORT_REQUEST` |
| Export status/result | `iadm06.export.view` | `IADM06_EXPORT_RESULT` |
| Read-only metric catalogue | `iadm06.catalogue.view` | `IADM06_CATALOGUE_VIEW` |
| Manage catalogue (reserved) | `iadm06.catalogue.manage` | *not implemented in V1* |

**Holders:** R01 all (incl. export); R02 view+export (no manage); R03 institute+entitlement+export+catalogue (own institute); R28 view keys only; R04/R05–R27/R29–R39 none; R27 none.

---

## 14. Proposed physical names (D5)

| Object | Name |
|---|---|
| Schema | `anl` |
| Metric catalogue | `anl.metric_catalogue` |
| Export-request metadata | `anl.export_requests` |

No analytics fact table, no snapshot, no dashboard-config table.

---

## 15. Build defaults assumed (D1–D11)

| # | Assumed for this build |
|---|---|
| D1 | Catalogue M01–M10 only |
| D2 | On-request sync reads; store catalogue + export metadata only; refresh = query time |
| D3 | Sources IADM-01 + IADM-04 only; adoption DEMO ONLY fixture |
| D4 | Suppress platform/cross-institute groups; own-institute not suppressed; threshold runtime DEMO ONLY (demo low then raise); production OWNER-OPEN |
| D5 | Names above |
| D6 | Port 8104, route `/api/v1/iadm/06`, keys/events above |
| D7 | Aggregate CSV stream; metadata only; no approval; IADM-05 export policy must resolve |
| D8 | Same JSON read endpoints; no named Intelligence consumer |
| D9 | Shared `audit.log_event` fail-closed |
| D10 | Ignore academic analytics / `rpt.*` for IADM-06 |
| D11 | Name = Platform Analytics & Usage Insights |

---

## 16. Blockers?

| Potential blocker | Status |
|---|---|
| No reusable shared audit | **Cleared** |
| DB target not DEV | **Cleared** (`azulbaxszsqxymqmlwfe`) |
| New infrastructure / tool | None |
| Boundary contradiction | None |
| IADM-05 resolve for analytics | **No published analytics-domain policy yet** — create DEMO ONLY data; not a hard stop |
| IADM-05 outcome-on-view | **Finding** — keep full bidirectional outcome story **partial** if needed; resolve gate proceeds |

**Preflight conclusion:** Proceed to S0 (IADM-06-US-017 … US-022), including separate additive read commits on IADM-01 and IADM-04.

---

*Abbreviations: RLS = row-level security; FORCE RLS = policies apply even to table owner; HMAC = gateway→service trust signature; E2E = headed browser demo; tsc = TypeScript compiler check (`npx tsc --noEmit`).*
