# Connect account setup to student, admission, lifecycle and portal (STM-07-EN-005)

**Date:** 2026-11-17  
**Status:** partially completed  
**Dev project:** `azulbaxszsqxymqmlwfe`

## What is connected today

| Plain link | How it works | Evidence |
|---|---|---|
| Student record (STM-01) ↔ login link | student-service enroll / link / provisioning APIs | `Stm07En005IntegrationContractTest` |
| Student portal (STM-10) ↔ link status | `/api/student/link-status` and student dashboard strip | existing US-013 UI + contract test portal ACTIVE |
| Lifecycle cut on **student status** path | `transitionLifecycle` → `revokeAccessForLifecycleEvent` | `stm07En005_lifecycleSuspendCutsPortalAccessOnStudentStatusPath` |
| Lifecycle service (STM-04) → student-service notify | best-effort PATCH when status is SUSPENDED / DETAINED / WITHDRAWN via `StudentAccountAccessPort` (needs `STM01_SERVICE_URL` / `STUDENT_SERVICE_URL`) | `Stm07En005LifecycleAccountNotifyTest` |
| Admission handover (STM-02) → create student | `HandoverOrchestrationService.DefaultStudentServiceAdapter` POST `/api/v1/students` when URL set | existing handover tests |

## Not covered (left open on purpose)

| Gap in plain words | Why not stubbed |
|---|---|
| Automatic “request a new login” right after admission handover | Needs VERIFIED identity first; auto-provision would invent policy |
| Event-bus fan-out from STM-04 lifecycle events to STM-07 | Kafka / CROSS-EN-007 held — no stub consumer |
| Academic history (STM-08) or Student service requests (STM-09) hooks | New-service gate — out of scope |
| HMAC Channel-A for admission→student and lifecycle→student HTTP adapters | Still use trusted-network headers; hardening is a separate security enabler |

## Operator note

Set `STM01_SERVICE_URL=http://127.0.0.1:8093` (or `STUDENT_SERVICE_URL`) when running admissions or lifecycle so the live adapters can reach student-service.
