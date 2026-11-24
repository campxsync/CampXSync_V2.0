# IADM-03 S0 Decision Pack

**Companion:** `docs/iadm/iadm-03/S0_VERIFICATION_REPORT.md`  
**Date:** 2026-11-24  
**Environment:** DEV only (`azulbaxszsqxymqmlwfe`)  
**Purpose:** Owner confirms PROPOSED defaults, answers OPEN decisions, and approves (or rejects) proposed keys / objects / seed **before** any S0 build write.

---

## How to answer

For each item, reply with one of:

- **APPROVE** (as written)
- **APPROVE WITH CHANGE:** …
- **REJECT — do this instead:** …

Do **not** treat silence as approval (`docs/AGREEMENT_V2.1.md`).

---

## A. Binding owner decisions already recorded (2026-11-23) — no re-ask

| Topic | Binding decision |
|---|---|
| Profile ownership | IADM-03 owns institute-level administrator profiles (invite/create, Auth link, active/inactive). College users → CADM-02; applicants R32 → STM-02; students/parents R25/R26 → STM-07 |
| Runtime path | IADM-03 is **not** on the per-request path; gateway + services verify claims |
| PARKED | R01 break-glass; two-person **appointment** of R03; R27 create/elevate (denial tests only); `adm03` stays dormant |
| Delegation default | 90 days owned by IADM-02 key `iam.delegation.max_validity_days`; IADM-03 reads it; fail closed if unavailable |

---

## B. PROPOSED defaults — please confirm or change (S0 checkpoint)

| # | Proposed default | Apply in build if APPROVE? |
|---|---|---|
| B1 | R01 (Super Admin) assigns only R02 (System Admin) and R03 (Institution Admin). R27 stays parked. | Yes |
| B2 | R03 assigns tenant business roles inside own institute under RBAC §21.1 ceiling; cannot assign R01/R02/R03 | Yes |
| B3 | Independent approval required for: removing an R03, changing R03 grants, and every access-request decision. Other assignments are direct within grantor ceiling; no self-grant | Yes |
| B4 | Access request approved by R01 or a **different** R03 of the same institute; if only one R03 exists, R01 is fallback | Yes |
| B5 | Role↔permission mapping changes: proposed only by R01, approved by a **different** R01 | Yes |
| B6 | Profile population for IADM-03 defaults to **R02 and R03 only** until P1 expands the list | Yes |
| B7 | Access reviews start **manually only**; no cadence; no automatic revocation | Yes |
| B8 | Guard-3 reading (PROPOSED): initial R03 **appointment** by R01 proceeds on R01 authority (D9 parked); removal/change of R03 grants still needs R01 or different R03 | Yes — see OPEN “guard 3 vs D9” |

**Owner action:** Confirm B1–B8 as a set, or list changes.

---

## C. OPEN decisions — evidence + RECOMMENDATION

### P1 — Which roles are “institute-level administrators” for IADM-03 profile governance?

**Evidence:** RBAC matrix §3 primary scopes (Documentation copy / repo copy). Code today creates R03 via IADM-01; R01/R02 live in `plat.platform_admins` (dual store). Default in blueprint until confirm: **R02 + R03 only**.

| Code | Role name | Home scope (matrix §3) | RECOMMENDATION for IADM-03 profiles |
|---|---|---|---|
| R01 | Super Admin | Platform / cross-institute | **Platform store** (`plat.platform_admins`) — **out of IADM-03 profile population** (already dual-store); IADM-03 may list for governance UI later only if owner expands |
| R02 | System Admin | Platform technical | **IN** default population (confirm) — note: today often in `plat.platform_admins`, not `iam.user_profiles`; unify vs keep dual is separate |
| R03 | Institution Admin | One institute (tenant) | **IN** (primary) |
| R04 | College Admin | One college | **OUT** → CADM-02 |
| R05 | Principal | Institution / college | **OUT** of default; college/institute ops → CADM later |
| R06 | Vice Principal | Institution | **OUT** |
| R07 | Dean | Academic / institution | **OUT** |
| R08 | HOD | Department | **OUT** |
| R09 | Program Coordinator | Program / course | **OUT** |
| R10 | Faculty | Assigned subjects | **OUT** |
| R11 | Class / Section Coordinator | Section | **OUT** |
| R12 | Controller of Examinations | Examination | **OUT** |
| R13 | Exam Cell Officer | Examination | **OUT** |
| R14 | Valuator / Examiner | Evaluation | **OUT** |
| R15 | Finance Manager | Finance | **OUT** |
| R16 | Finance Officer / Accountant | Finance | **OUT** |
| R17 | HR Manager | HR | **OUT** |
| R18 | HR Officer | HR | **OUT** |
| R19 | Communication Manager | Communication | **OUT** |
| R20 | Placement Officer | Placement | **OUT** |
| R21 | Librarian | Library | **OUT** |
| R22 | Hostel Warden | Hostel | **OUT** |
| R23 | Transport Manager | Transport | **OUT** |
| R24 | Facility Manager | Facilities | **OUT** |
| R25 | Student | Own data | **OUT** → STM-07 |
| R26 | Parent / Guardian | Linked student | **OUT** → STM-07 |
| R27 | API Client | Machine | **OUT** (PARKED creation) |
| R28 | Auditor | Read / audit | **OUT of create**; **read-only** consumer of IADM-03 evidence (US-033) |
| R29 | Reporting / Analytics User | Reports | **OUT** |
| R30 | Guest | Restricted read | **OUT** |
| R31 | Registrar | Assigned campus (matrix wording; treat as college/resource — **not** “campus” scope in IADM-03) | **OUT of profile create**; **delegation recipient** only |
| R32 | Applicant | Own application | **OUT** → STM-02 |
| R33 | Admissions Officer | Admission cycle | **OUT** |
| R34 | Department Staff | Department | **OUT** |
| R35 | Academic Records Officer | Campus records (STM-08) | **OUT** |
| R36 | Document Verifier | Assigned documents | **OUT** |
| R37 | External Verification Agency | Assigned documents | **OUT** |
| R38 | Provisioning / IAM Operator | Institute (R03) or platform (R02) | **RECOMMENDATION: OUT of default create**; STM-07 actor — include later only if owner wants IADM-03 to invite R38 |
| R39 | Faculty Advisor | Advisees (+ R10) | **OUT** |

**RECOMMENDATION:** Keep default **R02 + R03 only** for S1 profile screens. Explicitly keep R01 in `plat.platform_admins`. Add R38 only when STM-07 integration needs an invite path.

---

### Guard 3 vs parked two-person rule (US-020 / D9)

| Piece | Matrix / blueprint | RECOMMENDATION |
|---|---|---|
| Two-person **appointment** of first/any R03 | PARKED (D9); production gate | **Do not build** in DEV IADM-03; keep IADM-01 flag off |
| **Remove R03** or **change R03 grants** | Guard 3 + US-020 | **Build** approval by R01 or different R03 (B3/B8) |
| IADM-01 `secondApproverRequired` | Defaults off | **Leave unchanged** |

**Owner must clarify** if US-020 should also cover “approve appointment” (parked) vs only remove/change. RECOMMENDATION: **US-020 = remove/change only**.

---

### D1 — IADM-03 (and CADM-03) service row still provisional (§21 note)

**Evidence:** Matrix §21 opening paragraph still says Access & Identity Governance (IADM-03) and CADM-03 stay provisional. §8 cells show R01/R02/R03 = M, R04 = request only, R28 = V.

**RECOMMENDATION:** Proceed with §8 + §21.1 as **DEV grantor baseline**. Do **not** display “RBAC v3.0 Compliant.” Leave provisional label visible until owner closes the IAM discussion.

---

### D2 — Schema / RLS

**Evidence:** Physical store is **`iam.*`** (not `access_control`). Access-request table **missing**. FORCE RLS **off** on all 19 `iam` tables.

**RECOMMENDATION:**

1. **Reuse** existing `iam` tables; no rename.
2. If access requests are in S3: propose **one** new table (or pair) — see §E — **owner-approved migration only**.
3. FORCE RLS: propose enable FORCE on `iam` domain tables as a **separate** approved migration (security hardening), not bundled silently into feature work.

---

### D3 — Permission keys

**Evidence:** No dedicated IADM-03 key family. `adm03.*` is API-client (R27) family and must stay dormant. IADM-01 uses `adm01.*`; IADM-02 uses `cfg.*`.

**RECOMMENDATION — proposed NEW keys** (owner must approve names before seed/grants):

| Capability | Proposed key | Notes |
|---|---|---|
| View role catalogue | `iam.catalog.roles.view` | NEW |
| View permission catalogue / mappings | `iam.catalog.permissions.view` | NEW |
| View access overview | `iam.access.overview.view` | NEW |
| View user access detail | `iam.access.subject.view` | NEW |
| Invite/create institute admin profile | `iam.profile.invite` | NEW |
| List/read institute admin profiles | `iam.profile.view` | NEW |
| Change profile active/inactive | `iam.profile.status` | NEW |
| Grant role within ceiling | `iam.grant.assign` | NEW |
| Revoke role within ceiling | `iam.grant.revoke` | NEW |
| Decide R03 grant change (US-020) | `iam.grant.r03.decide` | NEW |
| Submit access request | `iam.request.submit` | NEW |
| Decide access request | `iam.request.decide` | NEW |
| Create delegation | `iam.delegation.create` | NEW |
| Revoke delegation | `iam.delegation.revoke` | NEW |
| Propose catalogue mapping change | `iam.catalog.change.propose` | NEW |
| Approve catalogue mapping change | `iam.catalog.change.approve` | NEW |
| Start/decide access review | `iam.review.manage` | NEW |
| Read access audit evidence | `iam.audit.view` | NEW |
| Governance / ceiling read (consumers) | `iam.governance.read` | NEW |
| Assign platform technical identity (R02) | `iam.identity.technical.assign` | NEW |

**Do not activate `adm03.*`.** R27 denials stay fail-closed without granting `adm03.manage`.

Alternative: reuse `adm01.manage` for early R01-only mutations — **not recommended** (blurs IADM-01 vs IADM-03). Prefer NEW `iam.*` keys above.

---

### D4 — Module path / port / prefix

| Item | RECOMMENDATION |
|---|---|
| Module path | `services/administration-services/iadm-03-identity-access-service/` (mirror IADM-01/02 naming) |
| Gateway prefix | `/api/v1/iadm/03` (verified free) |
| Port | **8101** (free; not in current stack list) |

---

### D5 — Claims contract

**Evidence:** JWT proves Auth subject; gateway DB-resolves profile/roles; services use `iam.has_permission`. Permission arrays are **not** embedded as a refreshed JWT claim today. Gateway cache TTL 5 minutes; invalidate unused. Gateway still normalises R03 → `college_admin`.

**RECOMMENDATION:**

1. Keep **DB + `iam.has_permission`** as authoritative for protected actions (fail closed).
2. After grant/revoke: call gateway cache invalidate (wire `JdbcIdentityResolver.invalidate`) **and** document residual delay = cache TTL if invalidate fails.
3. Fix gateway `normalizeRole` so `INSTITUTION_ADMIN` → `institution_admin` (align with frontend keep). Treat as security/context fix in S2 with US-021.
4. Do **not** invent a new JWT permission-array contract in S0 without owner sign-off.

---

### D6 / D15 — Delegation model and active-check without per-request IADM-03

**Evidence:** `iam.delegations` + Path C `GET /api/v1/admin/internal/delegations/active-check` on institute-admin `:8081` for `STM-07.ACCOUNT.A`.

**RECOMMENDATION:**

- **REUSE** table and active-check semantics.
- Short term: keep check on `:8081` (or MOVE implementation into IADM-03 but keep URL stable via gateway).
- Consuming services continue Path C / claims — **never** require synchronous IADM-03 on every STM request.
- Validity max: read IADM-02 `iam.delegation.max_validity_days` (confirmed 90 on DEV).

---

### D11 — Audit atomicity

**Evidence:** IADM-01 `appendDurableAudit` → `audit.log_event` same transaction; fail closed.

**RECOMMENDATION:** **Same-TX `audit.log_event`** for every IADM-03 mutation. No local audit table. No success if audit insert fails. Outbox only if already-approved pattern appears later — not required for S0.

---

### D13 — Claims refresh / revocation speed

**Evidence:** Up to **5 minutes** gateway cache; no invalidate callers; JWT role metadata not rewritten.

**RECOMMENDATION (numbers for owner):**

| Mechanism | Expected delay after revoke |
|---|---|
| `iam.has_permission` in service TX | **Immediate** for new DB sessions (RLS sees deleted assignment) |
| Gateway identity cache | **0–300 seconds** until TTL or invalidate |
| JWT lifetime | Until token expiry/refresh — role in `app_metadata` may stay stale for **UI routing** if UI trusts JWT role |

Ship US-021 with: invalidate on grant/revoke + document **≤ 5 minutes** gateway header lag as DEV SLA unless owner demands stricter (e.g. invalidate-all or 0 TTL).

---

## D. Confirm reuse / do-not-rebuild list

| Item | Ask |
|---|---|
| `plat.assign_institution_admin` (K1) | Confirm **REUSE** |
| R25 endpoints on institute-admin | Confirm **REUSE** (STM-07 owns calling) |
| `STM-07.ACCOUNT.A` active-check | Confirm **REUSE** |
| Live R03 `maharani.campx@gmail.com` | Confirm **REUSE** |
| Add test-session `institution_admin` persona? | **Ask** — currently missing |
| 56-cell matrix | Confirm must stay green |

---

## E. Proposed new objects (proposal only — no apply)

| Object | Why | Status |
|---|---|---|
| Service module + gateway route + port 8101 | US-002 | Needs D4 APPROVE |
| NEW permission keys in §C D3 | US-003 | Needs D3 APPROVE |
| `iam.access_requests` (+ optional decision columns or child table) | US-023…025 | Needs D2 APPROVE before migration |
| FORCE RLS on `iam.*` | Hardening | Separate approval |
| IADM-03 UI routes under `/platform/identity` (name TBC) and `/institution/access` | S1 | After S0 |

**Not proposed:** Kafka, email provider beyond Supabase Auth, MongoDB, duplicate audit/identity stores, activating `adm03`.

---

## F. Proposed DEV seed list (US-007) — blocked until APPROVE

Reuse IADM-01 institutes where present:

| Fixture | Proposed DEV binding |
|---|---|
| Institute A | MAC `72ecbfe3-2077-41b5-8a20-b6d3e045311b` ACTIVE |
| Institute B | Second ACTIVE institute if present, else create under approved seed only |
| Colleges A1/A2 | Existing MAC colleges (verify at seed time) |
| R01 proposer | `platformadmin.campx@gmail.com` (existing SUPER_ADMIN) |
| R01 approver | Second SUPER_ADMIN in `plat.platform_admins` (count=2 today) — map emails in seed script without printing secrets |
| R02 | Existing SYSTEM_ADMIN in `plat.platform_admins` |
| R03 A | `maharani.campx@gmail.com` on MAC |
| R03 B | Second INSTITUTION_ADMIN on same or other institute (create only if missing, after approval) |
| R04 | Existing college admin persona (`campxsync@gmail.com` / audit college_admin) |
| R28 | Existing AUDITOR assignment if present; else seed |
| R38 | Existing IAM_OPERATOR if present; else defer |
| Unprofiled invitee | Fresh `*.campxsync.test` email with **no** `iam.user_profiles` row |
| R31 | Existing registrar pilot / Path C persona |

Seed rules: DEV only; re-runnable; **56-cell matrix must still pass**; no production project.

---

## G. Owner checkpoint checklist

Please reply with:

1. **PROPOSED defaults B1–B8:** APPROVE / changes  
2. **P1:** APPROVE R02+R03 only / expand list  
3. **Guard 3 vs D9:** APPROVE “US-020 = remove/change only” / other  
4. **D1–D6, D11, D13, D15:** APPROVE recommendations / changes  
5. **D3 key names:** APPROVE proposed `iam.*` keys / supply alternate names  
6. **D4:** APPROVE path/port/prefix  
7. **US-007 seed:** APPROVE list / changes  
8. **Test-session R03 persona:** add now / later / never  
9. Authorization to start **S0 build US-002…007** after the above  

---

## Owner response (recorded)

**2026-11-24:** Owner replied **Approved** to this pack (PROPOSED defaults B1–B8, P1 R02+R03 default, D1–D6/D11/D13/D15 recommendations, proposed `iam.*` keys, port **8101**, DEV seed list).  
S0 build US-002…007 executed — see `S0_BUILD_REPORT.md`.
