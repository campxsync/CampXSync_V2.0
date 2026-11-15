# STM-03 R31 Registrar Phase A

- **Registrar identity:** `R31` / `REGISTRAR`; legacy college admin only when `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` is ON.
- **Status approve/reject (`updateStatus` ELIGIBLE/SUSPENDED/REJECTED):** requires assigned campus (`x-college-id`) and delegation `STM-03.ENROLMENT.A`.
- **Catalogue configure paths** (terms, courses): registrar role only; campus/delegation not required in Phase A.