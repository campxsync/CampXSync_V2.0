# DEMO EVIDENCE REPORT: STM-03-EN-004

**Story Title:** API Gateway integration, OpenAPI documentation and service contracts
**Execution Timestamp:** 2026-11-14T11:16:15.922Z
**Environment:** Dev Supabase Database (`azulbaxszsqxymqmlwfe`), Local Stack (API Gateway: 8080, Next.js: 3000)

---

## 1. Acceptance Criteria Table

| # | Checkpoint / Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | **College Admin Authentication**<br>College Administrator authenticates and opens Course Registration Desk via Next.js BFF → API Gateway → enrollment-service | **PASS** | [`screenshots/01_college_admin_authentication.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots/01_college_admin_authentication.png ) |
| 2 | **Verify Enrolment Desk Loaded Through Gateway**<br>Desk heading proves the gateway-routed enrolment contract is reachable from the UI | **PASS** | [`screenshots/02_verify_enrolment_desk_loaded_through_gateway.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots/02_verify_enrolment_desk_loaded_through_gateway.png ) |
| 3 | **Select Academic Term Via Gateway Path**<br>Term selection issues BFF calls that proxy through :8080 to :8095 | **PASS** | [`screenshots/03_select_academic_term_via_gateway_path.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots/03_select_academic_term_via_gateway_path.png ) |
| 4 | **Confirm Gateway-Backed Success**<br>Success banner confirms gateway → STM-03 persistence path responded | **PASS** | [`screenshots/04_confirm_gateway_backed_success.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots/04_confirm_gateway_backed_success.png ) |
| 5 | **Gateway Health Reachable**<br>API Gateway health endpoint responds on ingress :8080 | **PASS** | [`screenshots/05_gateway_health_reachable.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots/05_gateway_health_reachable.png ) |
| 6 | **Negative RBAC — Unauthenticated Enrolment Contract Blocked**<br>Unauthenticated GET on gateway enrolment contract returns HTTP 401 | **PASS** | [`screenshots/06_negative_rbac___unauthenticated_enrolment_contract_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots/06_negative_rbac___unauthenticated_enrolment_contract_blocked.png ) |
| 7 | **Negative RBAC — Unauthenticated Handover Contract Blocked**<br>Unauthenticated POST on gateway handover contract returns HTTP 401 | **PASS** | [`screenshots/07_negative_rbac___unauthenticated_handover_contract_blocked.png`](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots/07_negative_rbac___unauthenticated_handover_contract_blocked.png ) |

---

## 2. Test Accounts & Personas


---

## 3. Demo Records Created

- **Tag:** `DEMO-STM-03-EN-004-968417`

---

## 4. Performance & Timings

- **College Admin Authentication:** 955 ms (Target <= 1500 ms: **PASS**)

---

## 5. Artifact Links

- **Video Recording:** [demo.webm](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/demo.webm)
- **Screenshots:** [screenshots/](file://D:/CampXSync/CampXSync_V2.0/docs/demos/STM-03-EN-004/screenshots)
