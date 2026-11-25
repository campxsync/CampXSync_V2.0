# IADM-06 findings (honest partials)

## F-01 — IADM-05 report-outcome on dataset views

**Story:** IADM-06-US-021  
**Finding:** IADM-05 `POST /requests/{id}/outcomes` requires a governance **request_id**. Analytics **dataset views** are not IADM-05 governed requests.

**What we do (no IADM-05 code change):**
- Resolve-before-release gate remains mandatory (`GET /domain/resolve-policy`).
- Dataset views record outcome in **shared audit** only (`IADM06_*_VIEW` via `audit.log_event`) with policy code/version in the message — **no figures**.
- We do **not** create IADM-05 governance requests for views.

**Status:** Resolve gate **completed**; bidirectional outcome-via-request_id for views remains **partial**.

## F-02 — EXPORT policy_type has no IADM-05 propose lifecycle

**Story:** IADM-06-US-021 / D7  
**Finding:** IADM-05 lifecycle propose APIs cover CLASSIFICATION / RETENTION / PRIVACY_CONSENT only. `EXPORT` exists as a **governance request** type and as an allowed `gov.policies.policy_type`, but there is **no** propose→approve→publish API for an EXPORT *policy* without changing IADM-05 code.

**What we do:**
- Export gate resolves the same **RETENTION + domain ANALYTICS** published version as data-handling.
- Direct-insert `DEMO-ANALYTICS-EXPORT` is superseded/retired; analytics handling policy is recreated through IADM-05 retention lifecycle (draft→review→approve→publish, author ≠ approver) and labelled DEMO ONLY.

## F-03 — Direct seed then lifecycle recreate

Initial DEV seed inserted `DEMO-ANALYTICS-HANDLING` / `DEMO-ANALYTICS-EXPORT` via migration (data). Owner finish-prompt requires lifecycle confirmation. See `docs/iadm/iadm-06/POLICY_LIFECYCLE_RECREATE.md` for the recreate procedure and evidence.
