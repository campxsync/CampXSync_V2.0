# Next Task

**STATUS: STM-06-EN-002 orphan deletion API DONE (2026-11-16).** Storage enabler complete (bucket + live path + frontend 1b/1c + orphan API). Live malware AV remains on EN-003.

## Just completed

- Orphan identification + cleanup API (College Admin; dry-run default; `confirmExecute` required to delete)
- Confirmed orphan = object under `tenants/{tenantId}/` not referenced by `doc.documents` or `doc.document_versions` (any status)
- Cross-tenant / ambiguous / referenced paths retained; deletes idempotent; partial failure reported
- Fake-storage unit tests; **no live Dev/prod Storage delete** in this task
- Verification: `mvn test -pl services/student-services/document-service` → **133/133 PASS**

## Remaining gaps

- Live malware AV still open (STM-06-EN-003 / D11)
- Blocked queue items unchanged (CROSS-EN-010 harness, US-015 DDL, …)

## Pickup queue (order)

0. IAM Path C / R31 pilot — **DONE**.
1. **STM-06-EN-002** — **completed** (live Storage + 1b + 1c + orphan API).
1b. **Frontend:** `contentBase64` upload — **DONE**.
1c. **Frontend:** Registrar role mapping — **DONE**.
2. **STM-06-EN-003** — **partially completed** (signed URL + heuristic scan; live AV open).
3. **STM-CROSS-EN-010** e2e — **BLOCKED** (harness approval).
6. **STM-06-US-015** — **BLOCKED** (notification DDL).
7. **STM-CROSS-EN-014** / **STM-03-EN-007** — **BLOCKED**.

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- Live orphan cleanup against Dev Storage requires separate owner authorization.
