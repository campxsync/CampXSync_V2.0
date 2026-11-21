# Next Task

**STATUS: CADM next — IADM Wave E exited 2026-11-21.**

**Approved by:** Niranjan V.G. (product owner) — IADM Wave E before CADM (complete).

## Wave E exit

Full report: `docs/iadm/WAVE_E_REPORT.md` · Contracts: `docs/iadm/WAVE_E_CONTRACTS.md`

| Phase | Status |
|---|---|
| E0 Contracts | completed |
| E1-A…E1-E parity | completed / partially completed (tracked gaps in WAVE_E_REPORT) |
| E2 Findings | completed (RBAC-01 documented OPEN) |
| E3 Stack cutover | completed |

## Next authorized work

**CADM implementation:** reconcile CADM stories, then Waves A–D (needs IADM contracts — now proven).

## Still deferred (no work)

Break-glass (IADM-03-US-008), two-person appoint (US-009), guardian-consent under-18 (IADM-05-US-007), API client R27 / adm03, Kafka.

## Rules

DEV Supabase only. Permission keys. New migrations + rollbacks. `campx-logger` / §3.2. Local commits; push only on request. Circuit breaker after 3 identical failures.
