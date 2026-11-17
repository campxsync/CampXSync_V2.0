# Next Task

**STATUS:** STM-10 batch 5 in progress. Save portal preferences in the database (STM-10-EN-001) completed. Next: Configure dashboard widgets (STM-10-US-012). **No push.**

## Done this turn

1. Preflight leftovers cleared via owner path C (evidence `c8595b8`, status docs `e36de86`, CSV swap `5eccfd2`).
2. Owner decisions D1 / D7 / D9 / D10 recorded in Progress.
3. **Save portal preferences in the database (STM-10-EN-001):** `stu.student_portal_preferences` FORCE RLS on Dev `azulbaxszsqxymqmlwfe`; student-service GET/PUT portal-preferences; `Stm10PortalPreferencesEn001Test` 10/0.

## Queue remaining (this batch only)

1. Configure dashboard widgets (STM-10-US-012) — persist widget choices on EN-001 store
2. Save shortcuts (STM-10-US-013)
3. Configure language and display preferences (STM-10-US-014)
4. Headed Playwright demos under `docs/reports/playwright-stm10-batch5/`

## Still BLOCKED (do not build)

| Item | Why |
|---|---|
| Configure portal settings and publish announcements (STM-10-US-023) | D10 — communication service missing |
| Academic history (STM-08) | New service gate |
| Student service requests (STM-09) | New service gate |

## Rules

No push unless authorized.
