/** View timetable (STM-10-US-004) — STOP when blocked. */
export async function run({ page, base, step, note }) {
  step('Record BLOCKED: no student-scoped timetable API');
  note(
    'BLOCKED: students-me has no batch id; timetable only offers /batch/{batchId} without membership check; D11 forbids inventing endpoints or relaxing rules.'
  );
  await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(800);
  throw new Error(
    'STOPPED: View timetable (STM-10-US-004) is BLOCKED — missing student-owned timetable read path'
  );
}
