# Student portal (STM-10) — what is buildable now

**Date:** 2026-11-17  
**Purpose:** Read-only map for the owner. **No STM-10 implementation started in this batch.**  
**Dev project:** `azulbaxszsqxymqmlwfe`

Marks:

- **buildable now** — can proceed in existing frontend / gateway / Java services without a new microservice or parked policy.
- **depends on Academic history (STM-08) or Student service requests (STM-09)** — blocked until those services exist.
- **depends on a parked item** — event bus, role-configuration decision, antivirus, fees/finance, exams product, LMS, etc.
- **UNKNOWN** — needs an owner answer before coding.

CSV status in the backlog is noted where useful; this file is about *dependency readiness*, not a claim that CSV “completed” rows are re-verified.

## Stories

| Plain name | Code | Mark | Note |
|---|---|---|---|
| Access portal using existing IAM | STM-10-US-001 | buildable now | Already on existing IAM + gateway path; CSV `completed`. |
| View personalized dashboard | STM-10-US-002 | buildable now | `/student` dashboard exists; CSV `completed`. |
| View academic profile | STM-10-US-003 | buildable now | Student profile via student-service / gateway; CSV `completed`. |
| View timetable | STM-10-US-004 | buildable now | Academic timetable/attendance data already exists in ACD services; harden via gateway (no new STM service). CSV `yet to pickup`. |
| View enrolled courses and learning resources | STM-10-US-005 | buildable now | Enrollment desk + enrolment-service exist; “learning resources” depth may still need LMS — treat LMS extras as parked. CSV `completed` for base enrolment view. |
| View examination schedule and results | STM-10-US-006 | depends on Academic history (STM-08) or Student service requests (STM-09) | Exam results/transcripts sit with Academic history (STM-08) / EXM; no STM-08 service in repo. |
| View fee summaries and payment status | STM-10-US-007 | depends on a parked item | Needs Finance (FIN) fee/payment APIs — not in this student stack. |
| View document summaries | STM-10-US-008 | buildable now | Document desk + document-service exist; CSV `completed`. Live antivirus remains parked for upload scanning. |
| View service requests | STM-10-US-009 | depends on Academic history (STM-08) or Student service requests (STM-09) | Needs Student service requests (STM-09). |
| View academic progress and transcript status | STM-10-US-010 | depends on Academic history (STM-08) or Student service requests (STM-09) | Needs Academic history (STM-08). |
| View notifications and announcements | STM-10-US-011 | buildable now | Dashboard placeholder exists; deeper publish path ties to US-023. CSV `completed` for basic view. |
| Configure dashboard widgets | STM-10-US-012 | buildable now | UI completed in CSV; durable save needs portal preferences enabler (STM-10-EN-001). |
| Save shortcuts | STM-10-US-013 | buildable now | Needs portal preferences persistence (STM-10-EN-001) in existing stack. |
| Configure language and display preferences | STM-10-US-014 | buildable now | Same — EN-001 in existing services. |
| View recent portal activities | STM-10-US-015 | buildable now | Can aggregate from existing audit/activity sources in BFF; no new microservice. |
| Use portal across desktop/tablet/mobile | STM-10-US-016 | buildable now | Frontend-only; responsive enabler (STM-10-EN-005) already marked completed. |
| See clear loading, empty and error states | STM-10-US-017 | buildable now | CSV `completed`. |
| Use portal during one integrated service outage | STM-10-US-018 | buildable now | Needs bounded aggregation/timeouts (STM-10-EN-002) on existing BFF. |
| Use accessible portal navigation | STM-10-US-019 | buildable now | CSV `completed`. |
| Log out securely | STM-10-US-020 | buildable now | Supabase Auth sign-out already available; wire/confirm portal UX. |
| View permitted linked views for my student | STM-10-US-021 | buildable now | Guardian service (STM-05) + consent exist; portal surface can call existing APIs. |
| View role-scoped portal data | STM-10-US-022 | depends on a parked item | Broader role-configuration decision (FLAG-002 / STM-07-EN-003 family) still open for college-specific mappings. |
| Configure portal settings and publish announcements | STM-10-US-023 | **depends on a parked item / missing service** | Owner confirmed publishers: **College Admin** and **Communication Manager** (not Registrar R31). Under `services/` there is only `communication-services/notification-service/.gitkeep` — **no communication service implementation**. Marked **BLOCKED** in backlog; do not build. |

## Enablers

| Plain name | Code | Mark | Note |
|---|---|---|---|
| Supabase persistence for portal-owned preferences/configuration only | STM-10-EN-001 | buildable now | New portal prefs tables would need explicit DDL approval; otherwise implement against an agreed existing prefs store. |
| BFF/API aggregation with bounded calls, timeouts, caching and partial-failure handling | STM-10-EN-002 | buildable now | Frontend BFF work only; no new Java service. |
| Existing IAM, Gateway and tenant context integration | STM-10-EN-003 | buildable now | Mostly present; finish Platform Admin tenant-context gaps seen on staff desks. |
| STM-01 through STM-09, ACD, LMS, EXM and FIN integration | STM-10-EN-004 | depends on Academic history (STM-08) or Student service requests (STM-09) | Also depends on parked LMS / EXM / FIN products for full clause coverage. Partial STM-01–07 + ACD can proceed now. |
| Responsive frontend, accessibility validation and browser E2E tests | STM-10-EN-005 | buildable now | CSV `completed` — further E2E is optional hardening. |
| RLS, security testing, centralized logging, observability and documentation | STM-10-EN-006 | buildable now | CSV `completed` for portal scope; keep listing live AV as parked elsewhere. |
| Portal performance, operational readiness and authorized Intelligence Services dashboard analytics | STM-10-EN-007 | depends on a parked item | Intelligence live endpoint / event bus still often `liveEndpoint=false`; analytics clause waits on that parked path. CSV `completed` for base readiness claims should be re-checked before go-live. |

## Explicit non-starts

- Do **not** start Academic history (STM-08) or Student service requests (STM-09) from this map.
- Do **not** stub the event bus, antivirus, or unresolved role-configuration decisions.
- Next independent STM-10 pickup (when authorized): preferences persistence (STM-10-EN-001) or secure logout (STM-10-US-020).
