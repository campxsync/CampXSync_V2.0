# Pilot Onboarding Runbook (H-4)

**Target:** Development Supabase only (`azulbaxszsqxymqmlwfe`)  
**Script:** `scripts/dev/pilot_onboard.mjs`  
**Fixtures:** `scripts/dev/fixtures/pilot/`

## Safety

- Never point at production (`ekspahbsbwdpraaqfqui`) or legacy staging (`kpwdvvcschankgggitnj`).
- Default mode is **dry-run** (validation only; no DB writes).
- Live tenant write requires exact approval phrase: `GO PROVISION-TENANT`.
- Do not print or commit passwords / JWTs.

## Dry-run (default)

```powershell
cd D:\CampXSync\CampXSync_V2.0
node scripts/dev/pilot_onboard.mjs `
  --config scripts/dev/fixtures/pilot/tenant.json `
  --students scripts/dev/fixtures/pilot/students.csv `
  --faculty scripts/dev/fixtures/pilot/faculty.csv
```

Writes plan report to `docs/demos/H-4/onboarding-plan.json`.

## Negative validation

```powershell
node scripts/dev/pilot_onboard.mjs `
  --students scripts/dev/fixtures/pilot/students.invalid.csv `
  --faculty scripts/dev/fixtures/pilot/faculty.csv `
  --expect-fail
```

## UI gated saga

1. Sign in as platform admin (`/api/auth/test-session?role=platform_admin&returnTo=/platform/institutes/new`).
2. Complete the onboarding wizard.
3. Without the approval header, BFF returns **gated** success (no permanent write).

## Live apply (optional; requires stack + JWT)

```powershell
node scripts/dev/pilot_onboard.mjs --apply --approve "GO PROVISION-TENANT" --bearer <platform-admin-jwt>
```

Roster `--roster-apply` only prints the import plan in H-4 (Auth user mint is out of scope without explicit approval).
