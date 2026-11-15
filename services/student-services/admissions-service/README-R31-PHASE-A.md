# STM-02 R31 Registrar Phase A

- **Catalogue registrar** (`R31` / `REGISTRAR`) on `recordDecision` / `issueOffer`: requires `X-College-Id` on `UserSecurityContext` and delegation `STM-02.ADMISSIONS.A`.
- **Legacy college admin** on those paths only when `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` is ON (with delegation + campus).
- **Existing executive approver roles** (`ADMISSIONS_APPROVER`, etc.) remain unchanged in Phase A; `COLLEGE_ADMIN` in that set is denied unless legacy stand-in is ON.
- **SoD:** `validateSegregationOfDutiesOnDecision` already blocks reviewer-as-approver (distinct from STM-06 same-actor verify+approve).