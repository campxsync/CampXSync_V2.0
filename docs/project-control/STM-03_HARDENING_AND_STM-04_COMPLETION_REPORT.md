# STM-03 Hardening + STM-04 Targeted Report — 2026-11-07

**Authorization:** CampXSync Product Manager — “Approved STM-04, STM-03 live JDBC/browser hardening”  
**Dev DB:** `azulbaxszsqxymqmlwfe` only  
**Branch:** `feature/java-backend-integration` (preserved; no commit/push)

## A. STM-03 live JDBC / path hardening

| Evidence | Result |
| :--- | :--- |
| `LivePostgresEnrollmentPersistenceTest` | **1/1 PASS** with `-Dcampx.approve.stm03.write=true` — insert term/window/course/section/enrolment/selection, same-tenant read-back, cross-tenant read=0, cross-tenant write denied; **unconditional rollback** |
| `EnrollmentHttpPathE2ETest` | **1/1 PASS** — local HTTP register → ENROLLED → GET enrolment |
| Unit regression | `EnrollmentDomainServiceTest` **20/20 PASS** (prior) |
| Browser UI | Next.js `npx next dev -p 3000` started. Browser navigation to `/student/enrollment` **redirected to** `/login?returnTo=%2Fstudent%2Fenrollment` (auth gate observed). Authenticated register E2E requires a real student session — not bypassed. HTTP E2E covers the service register path. |

## B. STM-04 Student Lifecycle (P0 vertical slice)

| Layer | Result |
| :--- | :--- |
| Service | `lifecycle-service` port **8096** |
| Unit | `LifecycleDomainServiceTest` **5/5 PASS** |
| Gateway | `LifecycleGatewayRouteTest` **1/1 PASS** (`/api/v1/lifecycle` → 8096) |
| DDL | `20261107000400_create_stu_lifecycle_tables` **applied** (FORCE RLS) |
| RBAC | `20261107000500_rbac_stu04_catalogue_addendum` **applied** (`stu04.*` → STUDENT/PARENT/REGISTRAR) |
| Intelligence | `student.lifecycle.changed.v1` via controlled adapter (`liveEndpoint=false`) |
| UI/BFF | `/student/lifecycle` + `/api/student/lifecycle` |

### RBAC posture implemented

- R25 / STUDENT: view own status; submit leave/transfer/withdrawal/reinstate requests  
- R31 / REGISTRAR: decide requests; CFG transition rules; graduation validate/record  
- R26 / PARENT: view linked student status only (`X-Linked-Student-Id`)  
- Faculty / others: fail-closed  

### Story coverage (this slice)

| IDs | Status |
| :--- | :--- |
| US-001, US-002, US-004, US-005, US-006 | Covered in domain + tests |
| US-012, US-013, US-014, US-015 | Covered (graduation + rules + intelligence event) |
| US-003, US-007–011, US-016–019 | Partial / deferred — transfer/withdrawal/advisor/report depth beyond P0 core |
| EN-001, EN-002, EN-005, EN-006 (contract) | Done for slice |
| EN-003 Kafka notifications, EN-004 deep STM cross-links | Deferred |

## Safety

- No production/legacy staging refs  
- No `.env.local` reads  
- No git commit/push/reset  
- Live JDBC writes used approved flag and rolled back  

## Next

- STM-04 remaining P1 stories (transfer/withdrawal depth, advisor US-018, reports)  
- Optional authenticated browser E2E once a student session is available  
- STM-05 when authorized  
