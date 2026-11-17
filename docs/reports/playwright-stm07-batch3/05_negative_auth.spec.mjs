/** Batch-3 browser negatives: Faculty denied, student denied, Registrar without delegation denied. */
export async function run({ page, base, stack, step, note }) {
  const deskSel = '[data-testid="college-account-provisioning-desk"]';
  const errSel = '[data-testid="account-provisioning-error"]';
  const studentId = '00000000-0000-4000-8000-000000000099';

  step('NEGATIVE: Faculty denied — no staff account-provisioning desk in faculty workspace');
  await page.goto(`${base}/api/auth/test-session?role=faculty&returnTo=/faculty`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1000);
  await page.goto(`${base}/college/account-provisioning`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1000);
  const facultyUrl = page.url();
  const facultyHasDesk = (await page.locator(deskSel).count()) > 0;
  if (facultyHasDesk) {
    throw new Error('Faculty must not see college account-provisioning desk');
  }
  step(`Faculty denied desk access (url=${facultyUrl})`);
  note('NEGATIVE_PASS: Faculty denied staff account-provisioning desk');

  step('NEGATIVE: Student denied — student cannot open staff account-provisioning desk');
  await page.goto(`${base}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1000);
  await page.goto(`${base}/college/account-provisioning`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1000);
  const studentUrl = page.url();
  const studentHasDesk = (await page.locator(deskSel).count()) > 0;
  if (studentHasDesk) {
    throw new Error('Student must not see college account-provisioning desk');
  }
  step(`Student denied desk access (url=${studentUrl})`);
  note('NEGATIVE_PASS: Student denied staff account-provisioning desk');

  step('NEGATIVE: Registrar (R31) — desk UI may open; API path recorded');
  await page.goto(
    `${base}/api/auth/test-session?role=registrar&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1200);
  const registrarHasDesk = (await page.locator(deskSel).count()) > 0;
  if (!registrarHasDesk) {
    note('Registrar bounced from desk by middleware/nav (still a deny path for staff ops)');
    note('NEGATIVE_PASS: Registrar without delegation denied (UI gate)');
    return;
  }
  step('Registrar reached desk UI — probing link-status');
  if (!stack.student8093 || !stack.gateway8080) {
    note('SKIPPED_MISSING_STACK: registrar API deny needs gateway :8080 and student-service :8093');
    return;
  }
  await page.fill('[data-testid="account-provisioning-student-id"]', studentId);
  await page.click('[data-testid="account-link-status-load"]');
  await page.waitForTimeout(2000);
  const errText = ((await page.locator(errSel).textContent().catch(() => '')) || '').toLowerCase();
  const hasResult = (await page.locator('[data-testid="account-link-status-result"]').count()) > 0;
  if (hasResult && !errText) {
    throw new Error('Registrar without a valid delegated path must not load link-status successfully');
  }
  // Documented: gateway rejects pilot Registrar JWT (missing app_metadata.tenant_id) before Java ACCOUNT.A check.
  if (errText.includes('gateway_malformed_jwt') || errText.includes('malformed')) {
    step('Registrar API rejected at gateway as malformed login token (not Java ACCOUNT.A denial)');
    note('NEGATIVE_OBSERVED: Registrar rejected at gateway (malformed JWT / missing tenant_id) — Java delegation case STOPPED pending Auth metadata');
    return;
  }
  step(`Registrar denied by API (error=${errText.slice(0, 160) || 'non-success'})`);
  note('NEGATIVE_PASS: Registrar denied link-status (non-success response)');
}
