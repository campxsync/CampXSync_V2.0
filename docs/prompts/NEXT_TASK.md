# Next Task

**STATUS: IADM Wave E (Parity & Cutover) — owner priority 2026-11-21. CADM deferred until Wave E exit.**

**Approved by:** Niranjan V.G. (product owner) — implement IADM Wave E before CADM.

## Plan

Full plan: `docs/iadm/WAVE_E_PLAN.md`

| Phase | Plain language | Status |
|---|---|---|
| **E0** | Prove five CADM/IADM contracts live | **in progress** |
| **E1-A** | IADM-01 lifecycle parity + SEC-11 Auth-linked College Admin | yet to pickup |
| **E1-B** | IADM-02 config parity | yet to pickup |
| **E1-C** | IADM-03 identity + IADM-08 audit/ops move | yet to pickup |
| **E1-D** | IADM-04 billing move + IADM-06 analytics | yet to pickup |
| **E1-E** | IADM-05 privacy + IADM-07 security depth | yet to pickup |
| **E2** | Security findings (HttpContext leak, SEC-11) | yet to pickup |
| **E3** | Stack scripts + gateway cutover off monolith | yet to pickup |

## Still deferred (no work)

Break-glass (IADM-03-US-008), two-person appoint (US-009), guardian-consent under-18 (IADM-05-US-007), API client R27 / adm03, Kafka.

## After Wave E exit

Set next task to: **CADM implementation: reconcile CADM stories, then Waves A–D (needs IADM contracts)**.

## Rules

DEV Supabase only. Permission keys. New migrations + rollbacks. `campx-logger` / §3.2. Local commits per phase cluster; push only on request. Circuit breaker after 3 identical failures.
