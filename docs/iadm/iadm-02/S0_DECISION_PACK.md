# IADM-02 S0 Decision Pack (open decisions only)

**Companion:** `docs/iadm/iadm-02/S0_VERIFICATION_REPORT.md`  
**Date:** 2026-11-23  
**Purpose:** Evidence + options + labelled **RECOMMENDATION** for owner decisions that the build engineer must **not** decide alone.  
**Database writes:** None in this pack.

---

## Already decided by owner (apply; do not reopen)

| ID | Decision | Binding default |
|---|---|---|
| **D2** | Institute overrides | **Not allowed.** Platform defaults only. Story `IADM-02-US-031` stays **PARKED**. |
| **D4** | Approval | Change needs **draft → validation → authorized review → publish**. |
| **D5** | Same person draft + approve | **Denied.** Demo needs **two separate Super Admin (R01)** test users. |
| **D8** | Stale configuration | No hidden fallback; consumers **fail closed** unless an approved last-known-good rule exists. |
| **D11** | Rollback | Always a **new immutable version** that references the prior one. |
| **D12** | Consumer refresh / notify | Consumers **pull**; COM notifications **best effort**; **no Kafka**. |

---

## D1 — Initial list of configuration keys

### What exists today (evidence)

DEV `cfg.setting_definitions` (4 keys), from seed `80_seed.sql`:

| Key | Type | Default | Domain (inferred from name) |
|---|---|---|---|
| `attendance.min_pct` | INTEGER | 75 | Academic / attendance |
| `exam.grace_marks_max` | INTEGER | 5 | Examination |
| `fees.late_fee_grace_days` | INTEGER | 7 | Fees |
| `security.session_timeout_min` | INTEGER | 30 | Security / session |

Hardcoded behaviours **not** in the catalogue today (candidates to *promote* later, not invent silently):

| Behaviour | Where | Suggested future key (proposal only) |
|---|---|---|
| IADM-01 R03 second-approver required | JVM prop / `plat.iadm01_feature_flags` | `iadm01.r03.second_approver_required` (BOOLEAN) |
| API client max validity days | Hardcoded `90` in `PostgresApiClientRepository` | `iam.api_client.max_validity_days` (INTEGER) |

Illustrative fixture names in blueprint §15 (`CFG-PLATFORM-001`, `CFG-PLATFORM-002`) are **labels for demo**, not real key strings.

### Options

| Option | Description |
|---|---|
| **A** | Seed demo only on the **4 existing keys**; map `CFG-PLATFORM-001/002` demo labels to two of them. |
| **B** | Option A **plus** add 1–2 platform-ops keys that replace hardcoded constants (second-approver, API client max days). |
| **C** | Invent a large multi-domain catalogue up front. |

### RECOMMENDATION (D1)

**Option B (narrow).**

1. **Initial catalogue for S0/S1 demo = the four existing keys** (do not invent academic/finance keys beyond what already exists).
2. **Demo label mapping (proposed):**
   - `CFG-PLATFORM-001` → `security.session_timeout_min` (safe, typed INTEGER, platform-relevant).
   - `CFG-PLATFORM-002` → `attendance.min_pct` (typed INTEGER with validation bounds in demo: e.g. valid 0–100, invalid −1 or 101).
3. **Optional NEW keys for owner approval before seed** (only if you want US-026 / IAM coupling demonstrated early):
   - `iadm01.r03.second_approver_required` (BOOLEAN, default false) — migrates IADM-01 off local flag.
   - `iam.api_client.max_validity_days` (INTEGER, default 90) — migrates hardcoded 90.
4. Domains for UI filters (proposed labels, not new tables): `security`, `attendance`, `exam`, `fees`, plus `platform` if the optional keys are approved.
5. **Do not** seed institute/college override rows under D2.

**Owner action:** Approve key list and demo mapping (and whether optional platform-ops keys are in or out).

---

## D3 — Exact role ceilings (read / draft / approve / publish)

### What exists today (evidence)

**RBAC Matrix v3.0 §8 (IADM-02 row):**

| Role | Matrix posture |
|---|---|
| R01 Super Admin | M (manage) |
| R02 System Admin | M |
| R03 Institution Admin | W within allowed institute settings |
| R04 College Admin | V effective only |
| R28 Auditor | V |

**Tension:** Matrix R03 “W within allowed institute settings” **conflicts** with owner **D2 (no overrides)** and CSV `IADM-02-US-029` (R03/R04 cannot change platform defaults). Until D3 is decided for IADM-02, treat **D2 + CSV as the stronger operational constraint for this build**.

**Permission catalogue (DEV):** only `cfg.view` / `cfg.view.own` for the cfg family. No `cfg.draft` / `cfg.approve` / `cfg.publish` / `cfg.manage`.  
`adm05.write` / `adm05.manage` are wired into `cfg.setting_values` RLS and are granted to Institution Admin / College Admin templates — **unsafe to reuse** for platform publish.

**Java today:** IADM-01 checks `adm01.*` keys. Monolith configuration HTTP does **not** mirror that permission-key discipline for cfg mutations.

### Options

| Option | Description |
|---|---|
| **A** | Reuse `adm05.*` / `plat.write` for IADM-02 mutations. |
| **B** | Add a small **new `cfg.*` mutation family** and map ceilings explicitly; leave `adm05.*` for college settings (CADM) later. |
| **C** | Collapse everything under `adm01.manage` (wrong domain; pollutes IADM-01). |

### RECOMMENDATION (D3)

**Option B.** Propose **NEW** permission keys (owner must approve before any catalogue migration):

| Proposed key | Plain meaning | Suggested ceiling |
|---|---|---|
| `cfg.view` | View catalogue / effective / history | **REUSE.** R01, R02, R28; R03 **only if** you explicitly want read of platform catalogue (recommend **yes, read-only**); R04 **no** IADM-02 admin UI (effective values only via owning services) |
| `cfg.draft` | Create/update/validate/submit draft | **NEW.** R01; R02 **technical keys only** (see below) |
| `cfg.approve` | Approve/reject draft | **NEW.** R01 only for S0–S4 (second R01 for SoD); R02 **denied** |
| `cfg.publish` | Publish / corrective publish | **NEW.** R01 only |
| `cfg.policy.draft` | Propose policy-definition draft | **NEW.** R01 only |
| `cfg.consumer.read` | Service identity effective read | **NEW.** Granted to approved service identities only (D7), not humans |

**Proposed role ceilings (IADM-02 only):**

| Actor | Read | Draft | Approve | Publish | Notes |
|---|---|---|---|---|---|
| R01 Super Admin | Yes | Yes | Yes | Yes | Two distinct R01 users for demo (D5) |
| R02 System Admin | Yes | Technical keys only (e.g. `security.*`, optional `iam.*`) | **No** | **No** | Matches CSV `IADM-02-US-015` |
| R03 Institution Admin | Optional catalogue read (`cfg.view`) | **No** | **No** | **No** | Overrides parked (D2) |
| R04 College Admin | **No** IADM-02 admin access | **No** | **No** | **No** | Consume via owning services only |
| R28 Auditor | History / versions read | **No** | **No** | **No** | Mutations deny with “Auditor access is read-only” |
| R27 API Client | Out of scope | — | — | — | Dormant |

**Also propose (with D6):** stop using `adm05.write` for platform-default mutation paths; restrict or retire override writes while D2 holds.

**Owner action:** Accept key names + ceilings (or amend). Confirm whether R03 may **read** the platform catalogue.

---

## D6 — Table names, RLS, version storage

### What exists today (evidence)

**REUSE now:**

- `cfg.setting_definitions` — catalogue + default JSON
- `cfg.setting_values` — historically institute/college overrides (0 rows on DEV)
- `cfg.feature_flags`, `cfg.feature_flag_rules`, `cfg.feature_overrides`
- `cfg.policies`, `cfg.data_retention_policies`, `cfg.data_classifications`

**NOT FOUND on DEV (would be NEW if approved):**

- Draft table
- Immutable published-version table / snapshot store
- `cfg.setting_delegations` / `cfg.setting_value_versions` (exist only in rolled-back archive docs)

**RLS today:** ENABLE on all `cfg.*` inspected; **FORCE RLS = false**.  
`setting_definitions` SELECT is open to all `authenticated`.  
`setting_values` writes keyed to `adm05.*`.

### Options

| Option | Description |
|---|---|
| **A** | Stretch existing columns only (e.g. status inside `cfg.policies.rules` JSON) — weak immutability. |
| **B** | REUSE catalogue tables + **propose two new tables** for draft and published version envelopes. |
| **C** | Rebuild a brand-new schema namespace (`iadm02.*`) and abandon `cfg.*`. |

### RECOMMENDATION (D6)

**Option B.** Keep `cfg` schema ownership under IADM-02 for platform defaults/policy.

**Proposed NEW tables (names for owner approval — not created):**

1. `cfg.config_drafts`  
   - draft_id, setting_key or policy_code, proposed_value jsonb, reason, status (`DRAFT|VALIDATED|PENDING_APPROVAL|APPROVED|REJECTED`), author_user_id, validator metadata, idempotency_key, correlation_id, timestamps  
   - RLS: platform-admin / permission-key gated; **never** visible to consumer roles/services.

2. `cfg.config_versions`  
   - version_id, version_label, status (`PUBLISHED|SUPERSEDED`), effective_from, release_notes, published_by, supersedes_version_id, checksum, payload jsonb (published snapshot), idempotency_key, correlation_id  
   - Immutable after publish (no UPDATE of payload; supersede by insert).

3. Optional link table `cfg.config_version_entries` if one version publishes many keys — only if needed after first publish design.

**Proposed RLS / grants (proposal only):**

- `ALTER TABLE … FORCE ROW LEVEL SECURITY` on all IADM-02-touched `cfg.*` tables.
- Consumer / non-publisher SELECT never sees drafts or non-published versions.
- Under D2: **deny** authenticated institute/college writes to `cfg.setting_values` for platform-default keys (or freeze override path entirely until overrides are un-parked).
- Platform definition writes: permission keys from D3, not open `using (true)` for mutations.

**Owner action:** Approve table names + FORCE RLS + freeze of override writes; or amend names before any migration script is written.

---

## D7 — Consumer (service) identity contract

### What exists today (evidence)

- Tables `iam.api_clients` / `iam.api_client_grants` exist; Java repository in institute-admin; 90-day max hardcoded.
- Path C IAM delegation check exists for human STM flows (`CAMPX_IAM_DELEGATION_BASE_URL` → institute-admin `:8081`).
- **No** IADM-02 consumer effective-config endpoint.
- R27 API Client remains **out of scope** for IADM-02 stories.

### Options

| Option | Description |
|---|---|
| **A** | Invent a second consumer registry inside IADM-02. |
| **B** | Authenticate with existing gateway/HMAC + **service claims**, authorize domains/keys via **`iam.api_client_grants` (or equivalent grant rows)** without treating the caller as human R27 UI. |
| **C** | Share a static shared secret per service in env only (no DB grant). |

### RECOMMENDATION (D7)

**Option B.**

1. Consumer identity comes from **verified credentials/claims after gateway auth**, never from a bare `consumerId` path parameter alone (blueprint rule).
2. Reuse `iam.api_clients` + grants for **permitted domains/keys + validity**; IADM-02 only **reads** grants, does not become a second IAM.
3. Human operators use D3 keys; machines use `cfg.consumer.read` (proposed) scoped by grant rows.
4. Demo seed `SVC-CONSUMER-01`: one DEV client granted `security` (+ maybe `attendance`) domain, denied `fees` for negative test.
5. Do **not** activate R27 product surface; keep adm03 dormant per architecture IADM-D5.

**Owner action:** Confirm grant table/columns to use and whether a new grant row shape is allowed (if existing columns suffice, prefer no DDL).

---

## D9 — How publication and audit stay atomic

### What exists today (evidence)

- IADM-01: **same database transaction** — mutate + `audit.log_event` + fail closed if audit returns no id.
- `logger` module: `PostgresAuditRepository`, `AuditToOutboxBridge` (outbox pattern exists in code).
- Monolith config: in-memory audit / best-effort DB — **unacceptable** for IADM-02 success contract.

### Options

| Option | Description |
|---|---|
| **A** | Same transaction as IADM-01 (publish + `audit.log_event`). |
| **B** | Approved transactional outbox in same TX as publish; async ship to audit store. |
| **C** | Best-effort audit after HTTP 200. |

### RECOMMENDATION (D9)

**Option A for S0–S4.**

- Mirror IADM-01: publish/draft/decision success **only** if `audit.log_event` succeeds on the **same connection/transaction**.
- Keep Option B as a later optimization only if audit function latency becomes a proven problem — would need a separate owner approval.
- Reject Option C.

**Owner action:** Confirm same-TX `audit.log_event` as the IADM-02 durability rule.

---

## D10 — Payload versioning and deprecation policy

### What exists today (evidence)

- No versioned IADM-02 consumer wire schema in repo (**NOT FOUND**).
- Blueprint requires versioned payload/schema, freshness timestamp, explicit errors, backward-compatible changes until agreed.

### Options

| Option | Description |
|---|---|
| **A** | Unversioned JSON “whatever the UI needs”. |
| **B** | Explicit contract version field from day one (`schemaVersion` / `configSchema: 1`) with additive-only changes until a major bump. |
| **C** | Full OpenAPI + multiple concurrent majors immediately. |

### RECOMMENDATION (D10)

**Option B (minimal but explicit).**

1. Every consumer effective-config response includes: `schemaVersion` (start at `1`), `configVersionId`, `fetchedAt` (freshness), `keys[]` with typed values, `status` (`OK|STALE|UNAVAILABLE`).
2. Additive fields allowed without bump; removing/renaming fields requires `schemaVersion` major bump and owner-approved deprecation window.
3. Admin draft/publish APIs similarly carry `schemaVersion: 1`.
4. Document deprecation in IADM-02 README only after first publish ships — no Kafka version broadcast (D12).

**Owner action:** Accept `schemaVersion` starting at 1 and additive-only rule.

---

## Cross-cutting proposals that need owner “yes” before S0 build continues

### Proposed service coordinates

| Item | Proposal | Clash check |
|---|---|---|
| Module name | `iadm-02-platform-config-service` (under `services/administration-services/`) | New |
| Gateway prefix | `/api/v1/iadm/02` | No clash with `/api/v1/admin` or `/api/v1/iadm/01` |
| Port | **8100** (matches Phase 0 reservation; blueprint says assign at build time) | Free vs current stack |
| Health | `GET /api/v1/iadm/02/health` | Pattern from IADM-01 |

### Proposed DEV demo seed list (US-007) — run only after approval

| Fixture | Proposed content |
|---|---|
| Keys | Per D1 recommendation (4 existing + optional 2) |
| `VER-001` | Published `cfg.config_versions` snapshot containing current defaults for seeded keys |
| `DRAFT-001` | Valid draft on `security.session_timeout_min` (e.g. 45) with reason |
| `DRAFT-002` | Invalid draft (e.g. timeout −5 or attendance 150) for field-level validation fail |
| Personas | R01-A (drafter), R01-B (approver), R02, R03, R04, R28 — **account emails from `.env.audit` only; never print passwords** |
| `SVC-CONSUMER-01` | One DEV service identity with limited domain grant |
| Policies | Prefer **new clean DEV policy fixture**; do not treat existing `POL_SEC_*` forged rows as catalogue truth (clean only if owner approves DELETE in DEV) |

### Proposed permission-key migration (with D3)

Insert into `iam.permissions` (DEV only, after approval):  
`cfg.draft`, `cfg.approve`, `cfg.publish`, `cfg.policy.draft`, `cfg.consumer.read`  
+ role-template grants per ceiling table above  
+ **no** grant of mutation keys to R03/R04/R28.

---

## Owner reply template (copy/paste)

Please reply with decisions (edit as needed):

```
IADM-02 S0 DECISIONS
D1: APPROVE keys = [list] ; demo map CFG-PLATFORM-001=... CFG-PLATFORM-002=... ; optional keys IN/OUT
D3: APPROVE keys = [cfg.draft,...] ; ceilings as recommended / AMEND: ...
D6: APPROVE tables = [cfg.config_drafts, cfg.config_versions, ...] ; FORCE RLS = YES/NO ; freeze setting_values writes = YES/NO
D7: APPROVE consumer contract = iam.api_clients+grants / AMEND: ...
D9: APPROVE same-TX audit.log_event / OR outbox
D10: APPROVE schemaVersion=1 additive-only / AMEND: ...
PORT: 8100 APPROVED / OTHER: ...
SEED: APPROVE US-007 DEV seed as listed / AMEND: ...
AUTHORIZE: build IADM-02-US-002..007 after these decisions  YES/NO
```

---

## Stop

No S0 implementation (`US-002`+) and no migrations/seeds until the owner returns decisions on this pack.
