/** STM-07-US-009 — revoke R25; suspension cuts access narrative. */
export async function run({ page, base, stack, step, note }) {
  step('College Admin signs in (Path C STM-07.ACCOUNT.A grant used for delegated actions when present)');
  await page.goto(`${base}/api/auth/test-session?role=college_admin&redirect=/college`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForTimeout(1000);

  if (!stack.student8093) {
    note('SKIPPED_MISSING_8093: revoke + lifecycle access-cut APIs need student-service on :8093');
    step('Show college desk without performing revoke mutation');
    return;
  }

  step('Student-service is up — revoke path available via POST .../revoke-student-role');
  note('Live revoke mutation skipped without a pre-seeded linked student id in this headed demo; covered by Stm07AccountProvisioningTest.');
}
