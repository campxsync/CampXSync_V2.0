# Next Task

**STATUS: STM-06-EN-003 malware lifecycle (controlled scanner) DONE (2026-11-16).** Fail-closed scan states enforced. Live commercial AV remains gated (new tool / D11).

## Just completed

- Malware lifecycle: `PENDING_SCAN` → `SCANNING` → `CLEAN` | `INFECTED` | `SCAN_FAILED`
- Content-byte EICAR evaluation via existing `ControlledMalwareScanAdapter` (`liveEndpoint=false`)
- Fail-closed: signed URL, download, Registrar verify blocked unless `CLEAN`
- Scanner unavailable/timeout/malformed → `SCAN_FAILED` (never CLEAN)
- Duplicate rescan idempotent; concurrent `SCANNING` → 409
- Verification: full document-service suite (see COMPLETION_REPORT)

## Remaining gaps / gates

- **Live commercial AV engine** — STOP: requires owner approval for a new tool/service (Agreement 4 / D11). Interface ready: `MalwareScanPort`.
- Blocked queue: CROSS-EN-010 harness, US-015 DDL, …

## Pickup queue (order)

0. IAM Path C / R31 pilot — **DONE**.
1. **STM-06-EN-002** — **completed**.
2. **STM-06-EN-003** — **partially completed** (controlled/heuristic + fail-closed DONE; live AV gated).
3. **STM-CROSS-EN-010** e2e — **BLOCKED**.
6. **STM-06-US-015** — **BLOCKED** (notification DDL).
7. **STM-CROSS-EN-014** / **STM-03-EN-007** — **BLOCKED**.

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- Do not add ClamAV/Docker/paid AV without explicit owner approval.
