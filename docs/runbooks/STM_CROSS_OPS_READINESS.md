# STM-CROSS-EN-014 — Cross-service operational readiness pack

**Scope:** STM-03..STM-06 + API Gateway + IAM Path C on Development only.  
**Out of scope:** Kafka, live commercial AV, production, STM-07..STM-10 full rollout.

## Single page command list

| Step | Command |
|------|---------|
| 1. Env guard | `node scripts/dev/validate_dev_env.mjs` |
| 2. Ops regression | `powershell -ExecutionPolicy Bypass -File scripts/dev/run_ops_readiness_regression.ps1` |
| 3. Full CROSS E2E (optional; RAM-heavy) | `$env:CAMPX_OPS_RUN_E2E='1'; powershell -ExecutionPolicy Bypass -File scripts/dev/run_ops_readiness_regression.ps1` |
| 4. Standalone E2E | `powershell -ExecutionPolicy Bypass -File scripts/demo/run_cross_en_010_stack.ps1` |
| 5. Path C smoke | `powershell -ExecutionPolicy Bypass -File scripts/demo/run_path_c_stack_smoke.ps1` |
| 6. Stack listen map | `powershell -ExecutionPolicy Bypass -File scripts/dev/check_stack.ps1` |

## Port map (STM ops)

| Port | Component |
|------|-----------|
| 8080 | API Gateway |
| 8081 | Institute Admin (IAM Path C) |
| 8095 | Enrollment (STM-03) |
| 8096 | Lifecycle (STM-04) |
| 8097 | Guardian (STM-05) |
| 8098 | Documents (STM-06) |

## Evidence artifacts

| Artifact | Path |
|----------|------|
| Dev env validate | `docs/reports/OPS_DEV_ENV_VALIDATE_2026-11-16.json` |
| Ops regression | `docs/reports/OPS_READINESS_REGRESSION_2026-11-16.json` |
| CROSS-EN-010 E2E | `docs/reports/STM_CROSS_EN_010_E2E_2026-11-16.json` |
| STM-03 ops demo | `docs/demos/STM-03-EN-007/DEMO.md` |
| CROSS ops demo | `docs/demos/STM-CROSS-EN-014/DEMO.md` |

## Constraints

- Target project ref **azulbaxszsqxymqmlwfe** only.
- Never print JWTs, service-role keys, or passwords.
- Unsigned STM `/health` may return **401** under HMAC — treat as up.
- Do not invent Kafka or ClamAV substitutes.

## Related runbooks

- `docs/runbooks/STM-03-EN-007_ENROLLMENT_OPS.md`
- `docs/runbooks/R31_PILOT_REGISTRAR_ACTIVATION.md`
- `docs/latency-audit/START_LOCAL_STACK.md` (full matrix; prefer this pack for STM-03..06)
