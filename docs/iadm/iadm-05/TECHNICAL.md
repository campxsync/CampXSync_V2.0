# IADM-05 Data Governance & Privacy — Technical notes

**Service:** `iadm-05-data-governance-service`  
**Port:** 8103  
**Gateway prefix:** `/api/v1/iadm/05`  
**Schema:** `gov` (policy and governance-request metadata only)  
**Audit:** shared `audit.log_event` → `audit.events` (no second store)  
**DEV DB:** `azulbaxszsqxymqmlwfe` only

## Boundary

IADM-05 governs policy. Domain-owning services execute retention, export and erasure. This service never reads/writes another service's database and never deletes or exports domain data.

## Permission keys (`iadm05.*`)

See migration `20261125170000_iadm05_foundation_keys.sql` and `GovernancePermissionMatrix`.  
Super Admin (R01) uses platform-admin path for manage/approve/publish.  
System Admin (R02) may propose/submit, never approve.  
Institution Admin (R03) view-only adherence. College Admin (R04) view + informational entry points. Auditor (R28) read-only.

## Restart

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/restart_service.ps1 iadm-05
```

## Health

`GET http://127.0.0.1:8103/api/v1/iadm/05/health`
