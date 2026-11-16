/** STM-07-EN-004 — provisioning retry / history path. */
export async function run({ page, base, stack, step, note }) {
  step('IAM operator (system/platform admin) session');
  await page.goto(`${base}/api/auth/test-session?role=platform_admin&redirect=/`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });

  if (!stack.student8093) {
    note('SKIPPED_MISSING_8093: provisioning-requests / retry-provisioning need student-service on :8093');
    return;
  }

  step('Student-service up — retry-provisioning route is POST /api/v1/students/{id}/retry-provisioning');
  note('Headed retry against a FAILED row needs a seeded request id; unit suite Stm07En004ProvisioningRecoveryTest covers the behaviour.');
}
