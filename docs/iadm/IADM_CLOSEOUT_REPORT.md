# IADM Close-Out Report (one pass)

**Date:** 2026-11-28  
**DEV project only:** `azulbaxszsqxymqmlwfe`  
**Branch:** `feature/java-backend-integration` — local commits only; **not pushed**  
**Rule:** No new features; Super Admin (R01) and System Admin (R02) may still pass an institute.

## Plain-language label key

| Code | Plain-language label |
|---|---|
| IADM-01 | Institute & Tenant Lifecycle (`:8099`) |
| IADM-02 | Platform Config (`:8100`) |
| IADM-03 | Identity & Access Governance (`:8101`) — V2 record-only + legacy V1.4 |
| IADM-04 | Subscription Billing & Entitlements (`:8102`) |
| IADM-05 | Data Governance & Privacy (`:8103`) |
| IADM-06 | Platform Analytics (`:8104`) |
| IADM-07 | Security & Compliance (`:8105`) |
| IADM-08 | Audit, Health & Ops (`:8106`) |
| CADM / college-admin-service | College Admin ADM-02 (`:8082`) |
| R01 | Super Admin |
| R02 | System Admin |
| R03 | Institution Admin |
| RLS | Row-level security (PostgreSQL) |
| `WITH CHECK` | RLS rule that must pass before INSERT/UPDATE is kept |
| `tsc --noEmit` | Frontend TypeScript compile check |

---

## A. By service — what was fixed, evidence, commit

| Service | What was fixed | Named evidence | Commit |
|---|---|---|---|
| **IADM-01** (Tenant Lifecycle) | `createCollege` compared path institute to **original** claims **before** `withTenant` rewrite; foreign → `IADM01_OWN_TENANT_ONLY` | Unit suite **8/8** (`TenantLifecycleRbacTest` incl. `createCollegeTrustedInstituteRefusesForeign`); DB proof colleges cross-insert **REFUSED** | `1af216a` |
| **IADM-02** (Platform Config) | No client institute surface in sweep — no code change | Sweep §A3 | — |
| **IADM-03** (Identity Access) | Phase 1 already restored legacy V1.4 beside V2 (prior session). Phase 2 re-check: legacy `assign` / revoke / catalog already refuse foreign institute before write — **no further code change** | Prior: `969fc14`, `086f3c8`; suite **46/46** this pass | (prior) `969fc14` / `086f3c8` |
| **IADM-04** (Billing) | `TrustedInstituteScope` on `recordUsage`, R03 `reports.view` paths (`getUsageAgainstQuota`, `billingReport`, `getAnalyticsAggregates`), `accessGateProbe` | Unit suite **19/19** (`TrustedInstituteScopeTest` 4 cases) | `180926a` |
| **IADM-05** (Governance) | Non-EXPORT (and sharing) `scopeTenantId` same foreign refuse as EXPORT | Unit suite **26/26** (`trustedScopeTenantRefusesForeignForNonPlatform`) | `fa36f39` |
| **IADM-06** (Analytics) | Already `resolveOwnScope` — no change in this pass | Sweep A2#11 | — |
| **IADM-07** (Security) | `enforcementIntake` uses trusted resolve + institute must exist; alerts list already uses `resolveScope` for R03 | Unit suite **31/31** (`crossInstituteWriteRefusedForNonPlatform`) | `c27ebda` |
| **IADM-08** (Ops) | Alerts scope already mitigated — no change | Sweep A2#12 | — |
| **CADM** (college-admin `:8082`) | Department collegeId validated against claim tenant; program college always from parent dept; BFF stops injecting body `collegeId`; batch-event tenant from trusted claims | Suite **150** run, **0** failures (**31** skipped live); `TrustedScopeCloseoutTest` 2/2; `tsc --noEmit` **0** | `1542b06` |
| **Defence-in-depth (DB)** | New forward migration + rollback: tenant guard on plat/iam write policies lacking `current_tenant_id` | Applied DEV `iadm_rls_write_tenant_guard`; proof: usage / governance_requests / colleges cross-institute **REFUSED** as `authenticated` | `86c4e76` |
| **Housekeeping** | Tenant-parameter sweep doc alone | `docs/iadm/TENANT_PARAMETER_SWEEP.md` | `0b8387d` |

---

## B. Sweep table — Resolved column

| # | Finding (plain label) | Severity | Resolved? | Notes |
|---|---|---|---|---|
| A1#1 | IADM-01 createCollege `withTenant` rewrite | cross-institute write | **Yes** | `1af216a` |
| A1#2 | IADM-04 R03 `reports.view` treated as platform | cross-institute read | **Yes** | `180926a` |
| A1#3 | IADM-04 accessGateProbe client institute | cross-institute read | **Yes** | `180926a` |
| A1#4 | IADM-07 enforcementIntake body institute | cross-institute write | **Yes** | `c27ebda` |
| A1#5–#7 | CADM department/program collegeId + BFF inject | cross-institute write | **Yes** | `1542b06` |
| A1#8 | CADM batch-event body tenant | cross-institute write (metadata) | **Yes** | `1542b06` |
| A1#9 | IADM-04 recordUsage + usage_write RLS | write + defence | **Yes** | Java `180926a` + RLS `86c4e76` |
| A1#10 | IADM-05 non-EXPORT scopeTenantId | defence-in-depth | **Yes** | `fa36f39` |
| A1#11 / §B | plat.* WITH CHECK gaps | defence-in-depth | **Yes** | `86c4e76` |
| A2#5–#8 | IADM-03 legacy / V2 (already claims-bound) | re-checked | **Yes (no code)** | Legacy assign already refuses foreign |
| Remaining owner items | See §D | open | **No** | Explicitly out of scope |

---

## C. Final git status (local; not pushed)

**Branch:** `feature/java-backend-integration` — **ahead of origin by 7** (this close-out; not pushed).

| Hash | Message (plain) |
|---|---|
| `0b8387d` | docs: tenant-parameter sweep |
| `180926a` | fix(iadm04): trusted institute scope on usage, reports, and access gate |
| `fa36f39` | fix(iadm05): refuse foreign scopeTenantId on all governance requests |
| `1af216a` | fix(iadm01): compare createCollege path to original claims before withTenant |
| `c27ebda` | fix(iadm07): trusted institute on enforcementIntake with institute existence check |
| `86c4e76` | fix(iadm): tenant guard on write policies |
| `1542b06` | fix(cadm): trusted scope on college admin service |

**Related already on remote (not part of the 7):** IADM-03 legacy restore `969fc14` + hash proof `086f3c8`.

**Untracked / dirty left alone:** scratch `classpath.txt`, demo evidence PNGs/webm, `_verify_*` logs, and pre-existing edits to `PROGRESS.md` / `CURSOR_START_HERE.md` / `IADM-08-user-stories.csv` (not identified as close-out deliverables — not discarded).

**Regression this pass**

| Check | Result |
|---|---|
| IADM-01 unit | 8 / 0 fail |
| IADM-03 unit | 46 / 0 fail |
| IADM-04 unit | 19 / 0 fail |
| IADM-05 unit | 26 / 0 fail |
| IADM-07 unit | 31 / 0 fail |
| CADM unit | 150 / 0 fail (31 skipped live) |
| Frontend `tsc --noEmit` | **0 errors** |
| Headed Playwright (IADM-03/04/05/07/08/01) | **Not run** — stack down (ports 3000/8080/8099–8106 not listening). API-level / unit equivalents used instead (named above). Prior headed evidence for IADM-03 V2 / IADM-07 remains under `docs/iadm/iadm-03/evidence` and prior IADM-07 demo commits. |
| DEV RLS proof | usage, governance_requests, colleges cross-institute inserts **REFUSED** — `docs/iadm/IADM_RLS_WRITE_TENANT_GUARD.md` |

---

## D. Owner-open items (unchanged — NOT fixed here)

1. **D-03** — IADM-03 apply versus record-only (legacy V1.4 vs V2 coexistence stays until owner decides).  
2. **D1** — IADM-08 Auditor export.  
3. **IADM-06** — production small-group threshold.  
4. **IADM-07 / IADM-08** — real telemetry, frameworks, and SLAs.  
5. **IADM-05 follow-ups** — reporting an outcome without a request id for views; a propose path for export policies.  
6. **CADM redesign** — broader product redesign beyond the three security fixes above.

---

## E. Stop gate

Await **one** owner approval. Do **not** push until instructed (`APPROVED …` / `APPROVED … COMMIT` / push phrase as you prefer).
