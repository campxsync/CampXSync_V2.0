# STM-05 R31 Registrar Phase A

- **Registrar identity:** `R31` / `REGISTRAR`; legacy college admin when `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` is ON.
- **Evidence verify/reject (`reviewEvidence`):** requires campus and delegation `STM-05.GUARDIAN.A`.
- **Same-actor verify+approve:** blocked when the reviewer is the guardian person (`REVIEWER_IS_PARTY`); no separate document resubmit cycle like STM-06.