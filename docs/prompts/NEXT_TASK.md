# Next Task

**STATUS: STOP after STM-10 batch 6. No push.**

## Done this batch

| Plain name | Code | Commit |
|---|---|---|
| Portal data aggregation with bounded calls, timeouts, caching and partial-failure handling | STM-10-EN-002 | `414105f` |
| Use the portal during one integrated service outage | STM-10-US-018 | `eff9749` |
| View timetable | STM-10-US-004 | **BLOCKED** (no student-scoped timetable API / no batch id on profile) |
| Log out securely | STM-10-US-020 | `dd3bda4` |

## Still BLOCKED (do not build)

| Item | Why |
|---|---|
| View timetable (STM-10-US-004) | No my-timetable API; profile lacks batch id; client batch id would be IDOR (D11) |
| Configure portal settings and publish announcements (STM-10-US-023) | D10 — communication service missing |
| Academic history (STM-08) / Student service requests (STM-09) | New service gate |
| Delegated Registrar staff-screen student visibility | Separate owner decision (do not touch RLS) |

## Next buildable Student portal items (when authorized)

- View recent portal activities (STM-10-US-015) — proposal only unless data source confirmed
- View permitted linked views for my student (STM-10-US-021)
- Existing IAM / Gateway / tenant context integration (STM-10-EN-003) gaps if any remain

## Rules

No push unless authorized.
