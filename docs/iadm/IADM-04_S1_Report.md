# IADM-04 S1 — Build Report (US-005 … US-016)

**Status:** **`APPROVED S1 COMMIT`** (owner 2026-11-25).  
**DEV only:** `azulbaxszsqxymqmlwfe`  
**Service:** `iadm-04-billing-entitlements-service` port **8102**, gateway prefix `/api/v1/iadm/04`

## Owner decisions applied

| Decision | Applied |
|---|---|
| Port 8102 / `/api/v1/iadm/04` | Yes |
| `iadm04.*` keys | Yes (foundation migration + matrix) |
| One current TRIAL\|PENDING\|ACTIVE | Unique index `uq_subscriptions_one_current` + service reject |
| No entitlement cache | Authoritative DB check; `cached=false` |
| A — access gate at SUSPENDED choke | `resolve-role.ts` + `subscription_inactive` access-denied |
| B — DEMO ACTIVE for every institute | 13 DEMO ACTIVE rows on STANDARD |
| C — wizard catalogue + attach | Live plans API + attach after register |
| D — leave monolith billing | Untouched |

## Stories

| Story | Result | Evidence |
|---|---|---|
| US-005 Demo data | **completed** | Plans BASIC/STANDARD/PREMIUM; flags; 13 ACTIVE subs; two R01 already on DEV (`platformadmin` / `platformadmin2`) |
| US-006 Plan catalogue view | **completed** | Desk + `GET /plans`; headed screenshot |
| US-007 Plan create/edit/deprecate | **completed** (API) | `POST/PATCH /plans`; UI list (create form deferred to S2 polish if owner wants) |
| US-008 Entitlement mapping | **completed** (API) | `POST /plans/{id}/entitlements`; DEMO flags seeded |
| US-009 Trial policy | **completed** | `billing.*` keys in `cfg.setting_definitions`; `GET /trial-policy` |
| US-010 Onboarding attach | **completed** | Wizard → BFF → `POST /subscriptions/attach`; never activates institute |
| US-011 One-current rule | **completed** | DB unique index + Java conflict |
| US-012 Platform subscription list | **completed** | Desk + API |
| US-013 System Admin operational view | **completed** | Nav + same desk (view keys); commercial manage denied by permission |
| US-014/015 Institution desk | **completed** | `/institution/billing` + `/subscriptions/own` |
| US-016 Entitlement check + dual gate | **completed** | `POST /entitlements/check`; PENDING → `PENDING_ZERO_ENTITLEMENTS`; D15 **284 ms** |

## Migrations (DEV applied)

| Name | Purpose |
|---|---|
| `iadm04_plat_evolve` | PENDING/GRACE/EXPIRED; price_minor_units; history; one-current |
| `iadm04_demo_seed` | DEMO plans/flags/policy/ACTIVE seed + `plat.subscription_allows_access` |
| `iadm04_billing_rls` | RLS for `iadm04.*` + platform admin |
| (prior) `iadm04_foundation_keys_v2` | Permission keys |

Rollbacks: `database/migrations/2026112512*_rollback__*.sql`

## Verification

| Check | Result |
|---|---|
| `mvn test -Dtest=BillingPermissionMatrixTest` | PASS |
| `npx tsc --noEmit` (frontend) | 0 errors |
| `node scripts/demo/iadm04_s1_headed.mjs` (with PENDING flip) | **PASS** — `docs/iadm/iadm-04/evidence/s1/S1_HEADED_RESULTS.json` |
| D15 lag (PENDING reflect) | **284 ms** (no cache; open D15 closed for S1) |
| Screenshots | `s1_platform_billing_desk.png`, `s1_entitlement_probe.png` |

## Stack

- `start_stack.ps1` / `restart_service.ps1` / `check_stack.ps1` include **8102**
- Gateway route `/api/v1/iadm/04` → `:8102`

## Explicit non-claims

- Monolith `/api/v1/admin/billing/*` still present (cutover later).
- Invoices / offline payment / plan-change SoD = **S2+**.
- US-007 create UI form not filmed (API path present).
- Local commit on owner `APPROVED S1 COMMIT`. **No push** unless owner says.

## Next

S2 (plan-change, invoices, offline payment) only when newly authorized.
