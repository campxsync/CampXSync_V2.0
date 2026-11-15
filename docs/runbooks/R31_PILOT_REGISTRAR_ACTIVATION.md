# R31 Pilot Registrar Activation — Execution Package

**Status:** READY FOR OWNER WRITE APPROVAL (Dev only)  
**Date:** 2026-11-15  
**Branch:** `feature/java-backend-integration`  
**IAM Path C:** `268c036`  
**Target project:** `CampXSync-Architecture-Dev` / ref `azulbaxszsqxymqmlwfe`  
**Production:** FORBIDDEN (`ekspahbsbwdpraaqfqui`)

---

## 0. Verified live identity (read-only, 2026-11-15)

| Check | Result |
|---|---|
| Supabase MCP `list_projects` | Only project visible: **CampXSync-Architecture-Dev** |
| Project ref | `azulbaxszsqxymqmlwfe` |
| Region / status | `ap-south-1` / `ACTIVE_HEALTHY` |
| DB host | `db.azulbaxszsqxymqmlwfe.supabase.co` |
| Frontend `NEXT_PUBLIC_SUPABASE_URL` | points to `azulbaxszsqxymqmlwfe` only |
| Production ref in MCP session | **not present** |

### Pilot tenant / campus (already exist)

| Entity | UUID |
|---|---|
| Tenant | `a0000000-0000-0000-0000-000000000001` (CampX Architecture Dev Test Institute) |
| College / campus | `a0000000-0000-0000-0000-000000000002` (DEV_TEST_COLLEGE) |
| REGISTRAR role (`iam.roles`) | `a72bb0de-18ee-421c-b2db-2d89fdb0d773` (code `REGISTRAR`, college_id NULL = catalogue role) |
| Role template | `REGISTRAR` / catalogue_id `R31` present |
| Existing REGISTRAR `role_assignments` | **none** |
| Active `iam.delegations` for pilot | **none** (table empty for this use) |
| Delegator (College Admin) | `70a8ffae-b5ad-4c61-a401-16f220893df4` (`campxsync@gmail.com`) |
| Storage buckets | **0** (EN-002 Phase B still required separately) |

### Path C contract (already implemented — no redesign)

- `GET /api/v1/admin/internal/delegations/active-check?userId=&permissionKey=`
- M1: active `REGISTRAR`/`R31` role delegation → STM `*.A` keys
- STM clients: `HttpIamDelegationPort` when `CAMPX_IAM_DELEGATION_BASE_URL` set; else FailClosed
- Gateway: `/api/v1/admin/internal/delegations` → `:8081`

Correct service map: **STM-02 Admissions :8094**, **STM-03 Enrollment :8095**, **STM-04 Lifecycle :8096**, **STM-05 Guardian :8097**, **STM-06 Documents :8098**.

---

## 1. Gaps (implementation vs activation)

| Gap | Severity | Action in this package |
|---|---|---|
| No dedicated Registrar Auth user | Blocker for live A/R | Create Auth user + profile |
| No `iam.role_assignments` for REGISTRAR | Blocker | Insert campus-scoped assignment |
| No active `iam.delegations` | Blocker for Path C allow | Insert ACTIVE delegation |
| `CAMPX_IAM_DELEGATION_BASE_URL` unset by default | Runtime | Service config (no DB) |
| Frontend Registrar mapping (1c) | Partial UI | Out of scope; API/gateway can still carry R31 |
| Storage bucket missing | EN-002 Phase B | Separate approval (Phase 6) |
| Malware AV live scanner | Open security gap | Document only |
| R31 runbook still said adapter BLOCKED | Docs stale | Superseded by Path C Phase 2 |

---

## 2. Proposed pilot identity

| Field | Value |
|---|---|
| Email | `registrar.pilot.campx@gmail.com` |
| Display name | `Pilot Registrar` |
| Auth user id (pre-allocated) | `c31a0000-0000-4000-8000-000000000031` |
| Tenant | `a0000000-0000-0000-0000-000000000001` |
| Campus (`college_id`) | `a0000000-0000-0000-0000-000000000002` |
| Role | `REGISTRAR` / R31 → role_id `a72bb0de-18ee-421c-b2db-2d89fdb0d773` |
| Delegation from | College Admin `70a8ffae-b5ad-4c61-a401-16f220893df4` |
| Delegation to | Pilot Registrar `c31a0000-0000-4000-8000-000000000031` |
| Delegation validity | `now()` → `now() + 90 days`, status `ACTIVE` |
| Password | **Owner-set at Auth creation time** — never commit / never log |

---

## 3. Exact write operations (require explicit owner approval)

### 3.A Auth user (Supabase Auth)

**Preferred:** Supabase Dashboard → Authentication → Add user  
- Email: `registrar.pilot.campx@gmail.com`  
- Auto-confirm: yes  
- User UUID: set to `c31a0000-0000-4000-8000-000000000031` if UI allows; otherwise capture generated UUID and substitute in SQL below  
- Password: owner-chosen; store only in `.env.audit` under a new key name (e.g. `REGISTRAR_PILOT_EMAIL` / `REGISTRAR_PILOT_PW`) — **do not paste into chat/commits**

**Alternative (SQL Auth insert):** only if owner explicitly authorizes Auth-table writes; omitted from default apply to reduce risk. Prefer Dashboard/Admin API.

### 3.B IAM profile + assignment + delegation (SQL — Dev only)

Effects:
1. Upsert `iam.user_profiles` for the pilot Registrar in the pilot tenant.  
2. Insert `iam.role_assignments` with **non-null** `college_id` (assigned campus).  
3. Insert one `iam.delegations` row: College Admin → Pilot Registrar for REGISTRAR role, ACTIVE, 90-day window.  
4. Does **not** grant ACD/LMS/EXM/HRM/FIN. Does **not** grant delete. Does **not** change RLS/DDL.

```sql
-- TARGET: CampXSync-Architecture-Dev / azulbaxszsqxymqmlwfe ONLY
-- APPROVAL REQUIRED before execute

BEGIN;

-- Preconditions (fail if wrong project identity / missing role)
DO $$
DECLARE
  v_role uuid;
  v_college uuid;
BEGIN
  IF current_database() IS NULL THEN
    RAISE EXCEPTION 'No database context';
  END IF;

  SELECT id INTO v_role
  FROM iam.roles
  WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
    AND upper(code) = 'REGISTRAR'
    AND deleted_at IS NULL;
  IF v_role IS DISTINCT FROM 'a72bb0de-18ee-421c-b2db-2d89fdb0d773'::uuid THEN
    RAISE EXCEPTION 'Unexpected REGISTRAR role id: %', v_role;
  END IF;

  SELECT id INTO v_college
  FROM core.colleges
  WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
    AND id = 'a0000000-0000-0000-0000-000000000002'::uuid
    AND deleted_at IS NULL;
  IF v_college IS NULL THEN
    RAISE EXCEPTION 'Pilot college missing';
  END IF;
END $$;

-- 1) Profile (Auth user must already exist with this id)
INSERT INTO iam.user_profiles (
  id, tenant_id, email, full_name, status, created_at, updated_at
) VALUES (
  'c31a0000-0000-4000-8000-000000000031'::uuid,
  'a0000000-0000-0000-0000-000000000001'::uuid,
  'registrar.pilot.campx@gmail.com',
  'Pilot Registrar',
  'ACTIVE',
  now(),
  now()
)
ON CONFLICT (id) DO UPDATE
SET email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    status = 'ACTIVE',
    updated_at = now(),
    deleted_at = NULL;

-- 2) Campus-scoped REGISTRAR assignment
INSERT INTO iam.role_assignments (
  id, tenant_id, user_id, role_id, college_id, created_at, updated_at
) VALUES (
  'c31a0000-0000-4000-8000-000000000032'::uuid,
  'a0000000-0000-0000-0000-000000000001'::uuid,
  'c31a0000-0000-4000-8000-000000000031'::uuid,
  'a72bb0de-18ee-421c-b2db-2d89fdb0d773'::uuid,
  'a0000000-0000-0000-0000-000000000002'::uuid,
  now(),
  now()
)
ON CONFLICT (id) DO NOTHING;

-- Guard: no duplicate active REGISTRAR assignment for this user/campus
DO $$
DECLARE c int;
BEGIN
  SELECT count(*) INTO c
  FROM iam.role_assignments
  WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
    AND user_id = 'c31a0000-0000-4000-8000-000000000031'::uuid
    AND role_id = 'a72bb0de-18ee-421c-b2db-2d89fdb0d773'::uuid
    AND college_id = 'a0000000-0000-0000-0000-000000000002'::uuid
    AND deleted_at IS NULL;
  IF c < 1 THEN
    RAISE EXCEPTION 'REGISTRAR assignment missing after insert';
  END IF;
END $$;

-- 3) Active M1 delegation (College Admin -> Pilot Registrar)
INSERT INTO iam.delegations (
  id, tenant_id, from_user_id, to_user_id, role_id,
  valid_from, valid_to, reason, status, created_at, updated_at, created_by
) VALUES (
  'c31a0000-0000-4000-8000-000000000033'::uuid,
  'a0000000-0000-0000-0000-000000000001'::uuid,
  '70a8ffae-b5ad-4c61-a401-16f220893df4'::uuid,
  'c31a0000-0000-4000-8000-000000000031'::uuid,
  'a72bb0de-18ee-421c-b2db-2d89fdb0d773'::uuid,
  now(),
  now() + interval '90 days',
  'Pilot R31 activation — STM A/R Path C M1',
  'ACTIVE',
  now(),
  now(),
  '70a8ffae-b5ad-4c61-a401-16f220893df4'::uuid
)
ON CONFLICT (id) DO NOTHING;

COMMIT;
```

### 3.C Service configuration (no DB write)

On institute-admin (:8081) and each STM service (:8094–8098):

```
CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081
```

Keep `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` **unset/false** for primary verification. Optional negative test later: enable flag without delegation → A/R must still deny.

Gateway must resolve identity so downstream receives:
- `X-User-Id` = pilot Registrar UUID  
- `X-User-Role` = `REGISTRAR` or `R31`  
- `X-Tenant-Id` = pilot tenant  
- `X-College-Id` = pilot campus  

---

## 4. Verification queries (read-only after apply)

```sql
SELECT up.email, r.code, ra.college_id, ra.deleted_at
FROM iam.role_assignments ra
JOIN iam.roles r ON r.id = ra.role_id
JOIN iam.user_profiles up ON up.id = ra.user_id
WHERE ra.user_id = 'c31a0000-0000-4000-8000-000000000031';

SELECT id, from_user_id, to_user_id, role_id, status, valid_from, valid_to, deleted_at
FROM iam.delegations
WHERE to_user_id = 'c31a0000-0000-4000-8000-000000000031';
```

HTTP (with HMAC / stack up):

```
GET http://127.0.0.1:8081/api/v1/admin/internal/delegations/active-check?userId=c31a0000-0000-4000-8000-000000000031&permissionKey=STM-06.DOCUMENTS.A
```

Expect `{"allowed":true,"reason":"ACTIVE_R31_DELEGATION",...}`.

---

## 5. Rollback SQL (owner-approved only)

```sql
BEGIN;
UPDATE iam.delegations
SET status = 'REVOKED', deleted_at = now(), updated_at = now()
WHERE id = 'c31a0000-0000-4000-8000-000000000033'::uuid
  AND tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid;

UPDATE iam.role_assignments
SET deleted_at = now(), updated_at = now()
WHERE id = 'c31a0000-0000-4000-8000-000000000032'::uuid
  AND tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid;

UPDATE iam.user_profiles
SET status = 'INACTIVE', updated_at = now()
WHERE id = 'c31a0000-0000-4000-8000-000000000031'::uuid
  AND tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid;
-- Auth user disable/delete: Dashboard / Auth Admin (separate)
COMMIT;
```

Unset `CAMPX_IAM_DELEGATION_BASE_URL` to return STM services to FailClosed.

---

## 6. Storage Phase 6 (separate approval — not in §3)

- Current: **0** storage buckets on Dev.  
- Runbook: `docs/runbooks/STM-06-EN-002_STAGING_STORAGE_SETUP.md`  
- Requires separate owner authorization to create private `student-documents` bucket + policies + service env.  
- Malware AV remains an open gap.

---

## 7. Owner approval phrases (required)

Reply with one of:

1. **`APPROVED R31-PILOT-WRITE azulbaxszsqxymqmlwfe`** — authorize §3.A Auth create + §3.B SQL on Dev only; then continue Phases 4–5.  
2. **`APPROVED R31-PILOT-WRITE azulbaxszsqxymqmlwfe` + Auth user UUID `<uuid>`** — if Dashboard generated a different UUID.  
3. **`APPROVED EN-002-STORAGE azulbaxszsqxymqmlwfe`** — separate; authorize private bucket create (Phase 6).  
4. **`CHANGES R31-PILOT: …`** — modify package before any write.

Until (1) or (2): **no database writes, no Auth creates.**
