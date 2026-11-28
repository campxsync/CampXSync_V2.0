# Tenant-parameter sweep (read-only)

**Date:** 2026-11-28  
**Scope:** IADM-01 … IADM-08 Java services + CADM built so far  
**DB:** DEV Supabase `azulbaxszsqxymqmlwfe`  
**Rules:** No code changes in this pass. Plain-language label next to every code.

## Legend

| Code / term | Plain-language label |
|---|---|
| `tenantId` / `instituteId` | Institute identifier (same UUID in CampXSync) |
| `collegeId` | College within an institute |
| Trusted claims | Institute from verified auth (`UserSecurityContext` / JWT after gateway) — **not** from the browser alone |
| Client-supplied | Value from request body, query string, or URL path |
| `core.current_tenant_id()` | Database helper that reads the session’s trusted institute |
| `WITH CHECK` | Row-level security rule that must pass before an INSERT/UPDATE is kept |
| Cross-institute write | Caller could change another institute’s data if Java + RLS both fail |
| Cross-institute read | Caller could see another institute’s data if Java + RLS both fail |
| Harmless platform-only | Only Super Admin / System Admin (or equivalent platform keys) may pass another institute id by design |

**CADM status:** College Admin Java is **`college-admin-service`** (ADM-02, `:8082`, `/api/v1/college-admin/**`). IADM-01 still holds interim CADM handover rows (`plat.college_cadm_handovers`). Findings from [CADM tenant sweep](a8062979-8f33-43ef-8d0b-5ff2fad2f782) and [IADM tenant sweep](1d70bd77-76a6-4a57-9697-ade204d9b4de) are merged below.

**Important pattern:** If Java calls `ctx.withTenant(clientInstituteId)` **before** ownership checks, session claims become the client id and RLS **will not** catch a mismatch.

---

## A. Endpoint / service findings (client institute used before or instead of claims)

### A1. High interest (must review)

| # | File · method | Client param | Roles that can reach | Trusted claims compared? | RLS still refuse mismatch? | Severity | Plain label | Minimal proposed fix |
|---|---|---|---|---|---|---|---|---|
| 1 | `iadm-01…/TenantLifecycleService.createCollege` | path `/institutes/{instituteId}/colleges` | R03 (`adm01`/`adm06` manage); R01/R02 platform | **Broken** — `withTenant(instituteId)` then manage check on **scoped** ctx (tautology) | **No** (claims rewritten to path id) | **cross-institute write** | Create college under path institute | Compare path id to **original** claims before `withTenant`; only platform may cross |
| 2 | `iadm-04…/BillingUsageReportService.getUsageAgainstQuota` · `billingReport` · `getAnalyticsAggregates` | query `tenantId` | R01/R02/R28; **R03** has `iadm04.reports.view` | **R03 treated as platform** via `reports.view` → client filter honored | Unclear | **cross-institute read** | Usage/billing/analytics reports for another institute | Treat `reports.view` alone as own-scope; only R01/R02 (or true platform keys) may filter |
| 3 | `iadm-04…/BillingDomainService.accessGateProbe` | query `instituteId` / `tenantId` | R01/R02/SERVICE; **R03** via `subscriptions.view.own` | **No** — uses client id if present | Unclear (SQL uses client id; claims stay caller) | **cross-institute read** | Access-gate probe for any institute | Force non-platform to `ctx.getTenantId()` |
| 4 | `iadm-07…/SecurityDomainService.enforcementIntake` | body `instituteId` | R01/R02/R03 (`register.write` or `signal.intake`) | **No** — unlike `createFinding` (skips `resolveWriteInstituteId`) | Unclear / may fail on CHECK | **cross-institute write** | Enforcement status intake | Call `resolveWriteInstituteId` before INSERT |
| 5 | `college-admin-service…/createDepartment` (`PostgresDepartmentRepository`) | body `collegeId` | BFF `college_admin`; Java: any HMAC principal for tenant | **No** — body college drives insert (else first college) | Cross-tenant **yes** (composite FK); same-tenant wrong college **unclear** (`perm_colleges`) | **cross-institute write** (multi-college same tenant) | Accepts caller-chosen college for new departments | Ignore body `collegeId`; resolve via `CollegeResolver` / membership |
| 6 | `college-admin-service…/createProgram` | body `collegeId` | Same as #5 | **No** — body college **skips** parent department’s college | Same as #5 | **cross-institute write** | Caller collegeId overrides department college on program create | Always set college from parent department under claim tenant |
| 7 | `frontend…/api/college/departments` POST | injects session `collegeId` into body | `college_admin` | Session-derived; amplifies #5 | Same as #5 | amplifier of #5 | BFF forwards session college into Java body sink | Stop sending `collegeId` in payload; let Java resolve |
| 8 | `college-admin-service…/processBatchApprovalEvent` | body `tenantId` / `institutionId` | Gateway callers (weak Java RBAC) | **No** — stored on in-memory workflow | N/A today (in-memory) | **cross-institute write** (metadata; unsafe if persisted) | Batch workflow trusts event envelope tenant | Overwrite from HMAC claims; reject mismatch |
| 9 | `iadm-04…/BillingUsageReportService.recordUsage` | body `tenantId` / `instituteId` | **R01**, **SERVICE** via `iadm04.usage.record` | **No** — inserts client UUID as-is | **No** — `usage_write` permission-only | **cross-institute write** (defence / SERVICE) | Usage meter write for an institute | Bind non-platform to claims; add `current_tenant_id` to WITH CHECK |
| 10 | `iadm-05…/GovernanceRequestService.submit` / `submitSharing` | body `scopeTenantId` | R01/R02 (R03 cannot request); EXPORT has deny | Non-EXPORT / sharing: client can win over claims | Unclear | **harmless platform-only** missing defence-in-depth (or write if role matrix slips) | Governance / sharing scope tenant | Same cross-tenant deny as EXPORT for all types |
| 11 | Several `plat.*` IADM-04 write policies (see §B) | N/A (policy layer) | IADM-04 manage/payment keys | N/A | **No** tenant guard on WITH CHECK | **cross-institute write** (defence-in-depth) | Billing tables open to permission without institute match | Add `tenant_id = core.current_tenant_id() OR iam.is_platform_admin()` |

### A2. Client param present but Java already binds to claims (or refuses foreign)

| # | File · method | Client param | Roles | Claims compared? | RLS refuse? | Severity | Plain label | Minimal proposed fix |
|---|---|---|---|---|---|---|---|---|
| 5 | `iadm-03…/GrantGovernanceService.assignGrant` | body `tenantId`, `collegeId` | R01/R02/R03 (legacy V1.4 grant) | **Yes** — non-platform must match `ctx.getTenantId()` | Yes for `iam.role_assignments` (`current_tenant_id`) | harmless if claims win; residual risk if session claims wrong | Legacy assign grant to a role | Prefer ignoring body tenant for R03; always `scopeTenant = ctx.getTenantId()` for non-platform |
| 6 | `iadm-03…/GrantGovernanceService.appointInstitutionAdmin` | body `tenantId` (required) | Platform appoint path | Platform-oriented; tenant is the **target** institute being appointed | Platform / catalog | **harmless platform-only** | Appoint first Institution Admin for an institute | Keep; document that body tenant is the subject institute, not the caller’s home |
| 7 | `iadm-03…/RecordOnlyGrantWorkflowService.submitRequest` / `listRequests` | body/query `tenantId` | R01–R04 / R28 per `iadm03.*` | **Yes** — R03/R04 foreign → `IADM03_CROSS_TENANT_FORBIDDEN` | Yes (`iadm03.*` JWT tenant WITH CHECK) | harmless when deny path holds | V2 grant request / list | Optional: drop client tenant for institute roles entirely |
| 8 | `iadm-03…/CatalogueReadService.getKeyHolders` · handover · access-reviews list | query `tenantId` | R01/R02/R28 (+ R03 holders view) | Service filters; platform may pass any | Read path | **harmless platform-only** when gated; verify R03 cannot pass foreign | Holders / handover / reviews filtered by institute | Enforce resolveOwnScope pattern for non-platform |
| 9 | `iadm-07…/SecurityDomainService.resolveWriteInstituteId` (+ create finding/risk/alert/exception/evidence) | body `instituteId` | R01/R02 platform; R03 register.write | **Yes** — foreign → “Cross-institute write refused” | **Yes** after IADM-07 fix (`institute_id = current_tenant_id`) | mitigated | Security register write for an institute | Keep; regression-test foreign body id |
| 10 | `iadm-07…` **signal** intake / simulator | body `instituteId` | SERVICE (`iadm07.signal.intake`); humans denied | Service-trusted path | Unclear | **harmless platform-only** / service | Signal intake (service) | Optional allow-list; **do not confuse with enforcementIntake (A1#4)** |
| 11 | `iadm-06…/AnalyticsDomainService.resolveOwnScope` · `instituteOverview` · `entitlementInsight` · `trends` · export | query/body `tenantId` | R01/R02 platform view; R03 institute view | **Yes** for non-platform | Mostly read aggregation | **harmless platform-only** for R01/R02; R03 forced to claims | Analytics overview / entitlement / trends | Keep `resolveOwnScope`; refuse silent fall-through |
| 12 | `iadm-08…/OpsAlertsScope.resolve` · `OpsDomainService.alerts` | query `tenantId` | R01/R02/R28 select; R03 own | **Yes** — R03 foreign → `IADM08_SCOPE_DENIED` | Downstream alerts source | mitigated | Ops alerts for an institute | Keep; extend same helper to other IADM-08 list endpoints that take `tenantId` |
| 13 | `iadm-01…` path reads / lifecycle request create | path institute UUID | R01 platform; R03 with `adm01.*` | **Yes** on reads / `createLifecycleRequest` (original ctx) | Mixed | mostly mitigated; **createCollege is A1#1** | Institute reads / lifecycle requests | Keep; fix createCollege separately |
| 14 | `iadm-01…/suspendOrReactivate` | path institute id | Platform admin only | Platform gate | Platform | **harmless platform-only** | Suspend / reactivate / archive institute | None |
| 15 | `iadm-04` list invoices / subscriptions with `tenantFilter` | query `tenantId` | Platform vs `.own` keys | Own path uses claims; platform uses filter | Partial (§B) | **harmless platform-only** if role matrix holds | List invoices / subscriptions | Align WITH CHECK with claims; keep Java gates |

### A3. No / low client-tenant surface

| Service | Port | Note |
|---|---|---|
| **IADM-02** Platform Config | 8100 | No client `tenantId` / `instituteId` / `collegeId` on routes scanned |
| **CADM / college-admin** | 8082 | **Built** — see A1#5–#8 (not “missing”) |
| **IADM-01 handover** | 8099 | Interim `cadm01`/`cadm02` ack columns remain; not a separate CADM API |

---

## B. RLS `WITH CHECK` on institute-scoped tables **without** `current_tenant_id` / JWT tenant guard

Filter: table has `tenant_id` or `institute_id`, and WITH CHECK does **not** reference `core.current_tenant_id()` or `request.jwt.claims` → `tenant_id`.

| Schema.table | Policy | Cmd | Plain label | Why it matters | Minimal proposed fix |
|---|---|---|---|---|---|
| `iam.governance_idempotency` | `governance_idempotency_insert` / `_update` | INSERT/UPDATE | Idempotency keys for governance APIs | Checks actor user only; `tenant_id` column ungarded | Add `tenant_id = core.current_tenant_id() OR iam.is_platform_admin()` |
| `plat.usage_measurements` | `usage_write` | ALL | Usage meter rows | Permission-only; pairs with finding A1 | Add tenant guard (platform OR match) |
| `plat.invoices` | `invoices_write` | ALL | Subscription invoices | Permission-only overlay (older `invoices_insert/update` *do* guard) | Prefer single policy with tenant OR platform; drop ungarded ALL if redundant |
| `plat.invoice_lines` | `invoice_lines_write` | ALL | Invoice line items | Same pattern | Same as invoices |
| `plat.billing_notices` | `billing_notices_platform` | ALL | Billing notices | Permission-only | Add tenant match for non-platform |
| `plat.offline_payment_evidence` | `offline_evidence_insert` | INSERT | Offline payment proof | Permission-only | Bind to invoice’s tenant or `current_tenant_id` |
| `plat.offline_payment_reconciliations` | `offline_recon_insert` | INSERT | Offline payment reconciliation | Permission-only | Same |
| `plat.online_payment_intents` | `online_intent_write` | ALL | Online payment intent | Permission-only | Same |
| `plat.online_payment_reconciliations` | `online_recon_insert` | INSERT | Online payment reconciliation | Permission-only | Same |
| `plat.plan_change_requests` | `plan_change_update` | UPDATE | Decide plan change | Decide permission without tenant match on CHECK | Require row `tenant_id = current_tenant_id()` unless platform |
| `plat.renewal_actions` | `renewal_action_insert` | INSERT | Subscription renewal action | View permission used for write CHECK | Tighten to manage/attach + tenant guard |
| `plat.subscription_history` | `subscription_history_insert` | INSERT | Subscription history ledger | Attach permission only | Add tenant guard |
| `plat.institute_lifecycle_requests` | `institute_lifecycle_requests_update` | UPDATE | Decide lifecycle request | Platform / `adm01.manage` without row tenant match | Add `tenant_id = current_tenant_id() OR is_platform_admin()` |

**Intentionally platform-catalog (not listed as gaps):** `iam.permissions`, `iam.role_templates`, `cfg.setting_definitions`, `sec.controls`, `sec.security_standards`, `core.tenants`, etc. — platform-admin WITH CHECK with no institute column semantics.

**Already guarded (examples):** `iadm03.*` (JWT tenant), `sec.findings/risks/alerts/…` (post–IADM-07 `current_tenant_id`), most `iam.role_assignments` / `core.*` institute tables.

---

## C. Summary counts

| Bucket | Count |
|---|---|
| Cross-institute **write** (Java and/or RLS) | **~8** primary (A1#1, #4–#6, #8–#9, #11 / §B) |
| Cross-institute **read** | **~3** primary (A1#2 R03 reports.view, A1#3 accessGateProbe, related aggregates) |
| Harmless platform-only / mitigated | **15+** |
| CADM / college-admin | **Present** (`college-admin-service` :8082) |

---

## D. Recommended fix order (do not implement in this pass)

1. **IADM-01 `createCollege`** — stop `withTenant` before ownership compare (claims rewrite).  
2. **IADM-04 R03 + `reports.view` / `accessGateProbe`** — force own-scope for institute roles.  
3. **IADM-07 `enforcementIntake`** — reuse `resolveWriteInstituteId`.  
4. **CADM department/program `collegeId`** — resolve college from claims / parent dept; stop BFF body injection.  
5. **IADM-04 `recordUsage` + `usage_write` WITH CHECK** and remaining plat billing WITH CHECK gaps.  
6. **IADM-05** non-EXPORT / sharing `scopeTenantId` defence-in-depth.  
7. **`institute_lifecycle_requests_update`** WITH CHECK tenant match.

---

## E. Evidence sources

- Live DEV policy inventory via SQL on `pg_policy` / `pg_get_expr(polwithcheck, …)`  
- Java controllers/services under `services/administration-services/iadm-0{1-8}-*/`  
- Prior IADM-07 RLS tenant-guard work (reference pattern for fixes)

**STOP** — report only; await owner authorization before any code or DDL change.
