# IADM-07 Security & Compliance Governance — Technical notes

**Service:** `iadm-07-security-compliance-service`  
**Port:** 8105  
**Gateway prefix:** `/api/v1/iadm/07`  
**Schema:** `sec` (registers only; FORCE row-level security)  
**Permission key family:** `iadm07.*` (legacy `adm07.*` departments keys untouched)  
**Audit:** shared `audit.log_event` → `audit.events` (no second store; fail-closed)  
**DEV DB:** `azulbaxszsqxymqmlwfe` only

## Boundary

IADM-07 governs security posture and evidence registers. It does **not** authenticate, authorize, remediate, or enforce. Standards are not IADM-02 configuration and not IADM-05 data policy. Evidence is a reference only (never a stored file).

## Permission keys (`iadm07.*`)

See migration `20261126130000_iadm07_foundation_keys.sql` and `SecurityPermissionMatrix`.  
Super Admin (R01) sole approver of standards and decider of exceptions.  
System Admin (R02) manage/triage/propose; never approve/decide.  
Institution Admin (R03) own institute limited writes.  
College Admin (R04) `iadm07.summary.view` only.  
Auditor (R28) read-only; no export.

## DEMO ONLY runtime

- Stale threshold default: **10080 minutes** (7 days); adjustable via `POST /settings/stale-threshold`.
- Severity labels Critical/High/Medium/Low; SLA values blank.
- Controls free-text framework; no certification claim.
- Signal intake simulator labelled DEMO ONLY; disable via `IADM07_SIMULATOR_DISABLED` / outage flags.

## Restart

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/restart_service.ps1 iadm-07
```

## Health

`GET http://127.0.0.1:8105/api/v1/iadm/07/health`

## UI desks

| Desk | Path | Personas |
|---|---|---|
| Platform security | `/platform/security` | Super Admin (R01), System Admin (R02) |
| Audit evidence | `/platform/security/audit` | Auditor (R28) (+ R01/R02) |
| Institution security | `/institution/security` | Institution Admin (R03) |
| College security | `/college/security` | College Admin (R04) |
