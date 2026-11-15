# STM-04 R31 Registrar Phase A

- **Registrar identity:** `R31` / `REGISTRAR`; `COLLEGE_ADMIN` / `INSTITUTION_ADMIN` only when `CAMPX_STM_LEGACY_REGISTRAR_STANDIN=true` (or `-Dcampx.stm.legacyRegistrarStandin=true`).
- **Approve/reject (`decideRequest`):** requires `x-college-id` on the actor and active delegation `STM-04.LIFECYCLE.A` (fail-closed live via `FailClosedDelegationPort`).
- **Same-actor verify+approve:** not enforced here; STM-04 has no separate verifier-vs-approver cycle on the request record (faculty endorsement is a distinct role path).