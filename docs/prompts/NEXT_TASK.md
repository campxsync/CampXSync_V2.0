# Next Task

**STATUS: STOP. STM-10 batch 5 preferences implementation complete locally. No push.**

## Done this batch

| Plain name | Code | Commit |
|---|---|---|
| Save portal preferences in the database | STM-10-EN-001 | `0cf5190` |
| Configure dashboard widgets | STM-10-US-012 | `0d4e6a8` |
| Save shortcuts | STM-10-US-013 | `f8a7a22` |
| Configure language and display preferences | STM-10-US-014 | `9cd4cb9` |

## Demo

Headed Playwright under `docs/reports/playwright-stm10-batch5/` — all three specs **STOPPED at student sign-in** (test-session / Quick Dev bounced to `/login`). Java suites cover persistence and isolation. See `DEMO_GUIDE.md`.

## Still BLOCKED (do not build)

| Item | Why |
|---|---|
| Configure portal settings and publish announcements (STM-10-US-023) | D10 — communication service missing |
| Academic history (STM-08) / Student service requests (STM-09) | New service gate |
| Delegated Registrar staff-screen student visibility | Separate owner decision (do not touch RLS) |

## Next buildable Student portal items (when authorized)

- Secure logout (STM-10-US-020) if still open
- Other buildable STM-10 rows from `STM10_BUILDABLE_NOW.md` excluding parked/BLOCKED items

## Rules

No push unless authorized.
