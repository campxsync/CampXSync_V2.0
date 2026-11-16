# STM-07.ACCOUNT.A — M1 grant note (Dev only)

**Project:** `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev)  
**Date:** 2026-11-16  
**Production:** never accessed  

## How STM-03..06 “grants” exist

They are **not** separate permission-key rows. Path C M1 uses:

1. One ACTIVE `iam.delegations` row of catalogue role `REGISTRAR` to the pilot Registrar  
   (`id = c31a0000-0000-4000-8000-000000000033`, `to_user_id = …000031`).  
   Applied via `docs/runbooks/R31_PILOT_REGISTRAR_ACTIVATION.md` §3.B.
2. An allowlist of STM `*.A` keys in  
   `DelegationAuthorizationService.STM_APPROVAL_KEYS`  
   (institute-admin). Keys STM-03..06 were already present; `STM-07.ACCOUNT.A` was added in code commit `1608f0c`.

`checkActive(tenant, user, permissionKey)` returns `ACTIVE_R31_DELEGATION` when the subject has that ACTIVE REGISTRAR delegation **and** the key is on the allowlist.

## STM-07.ACCOUNT.A

No new DB grant row is required or created. The existing pilot M1 delegation already covers `STM-07.ACCOUNT.A` once the allowlist includes it.

## Read-back (Dev)

```sql
SELECT id, from_user_id, to_user_id, role_id, status, valid_from, valid_to, deleted_at, reason
FROM iam.delegations
WHERE id = 'c31a0000-0000-4000-8000-000000000033'::uuid;
```

## Live Path C proof (existing tooling)

- Unit/live: `LivePostgresDelegationActiveCheckTest#liveAllowsAllStmApprovalKeys` (includes `STM-07.ACCOUNT.A`)
- HTTP smoke: `scripts/demo/smoke_path_c_http.mjs` (STM_KEYS includes `STM-07.ACCOUNT.A`)

Expect pilot `…000031` → `allowed:true` / `ACTIVE_R31_DELEGATION` for `STM-07.ACCOUNT.A`.  
Expect non-delegatee `81dd67d3-1d5f-4d8e-8061-a6764861f924` → denied (`NO_ACTIVE_REGISTRAR_DELEGATION`).
