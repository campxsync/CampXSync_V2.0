/**
 * Connect account setup to student, admission, lifecycle and portal (STM-07-EN-005)
 * Headed browser flow for pieces that already exist. Does not invent accounts or links.
 */
export async function run({ page, base, stack, step, note }) {
  const studentId = process.env.CAMPX_DEMO_STUDENT_ID || '81dd67d3-1d5f-4d8e-8061-a67648610002';

  step('Check admissions service for auto-login-setup after admission');
  if (!stack.admissions8094) {
    note('STOPPED_STEP: Admissions service (:8094) was not reachable at demo start');
  } else {
    note(
      'STOPPED_STEP: Automatic request-a-new-login right after admission handover is not wired (Connect account setup integration STM-07-EN-005 gap). Admissions is up for handover→student create only; no browser auto-provision step to run.'
    );
  }

  step('Platform Admin (R01) opens staff account-provisioning desk');
  await page.goto(
    `${base}/api/auth/test-session?role=platform_admin&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1500);

  const desk = page.locator('[data-testid="college-account-provisioning-desk"]');
  await desk.waitFor({ state: 'visible', timeout: 30000 });
  step('Staff desk visible — load link status for known Dev student record');

  if (!stack.gateway8080 || !stack.student8093) {
    note('STOPPED_STEP: Staff link-status needs API Gateway (:8080) and student-service (:8093)');
  } else {
    await page.fill('[data-testid="account-provisioning-student-id"]', studentId);
    await page.click('[data-testid="account-link-status-load"]');
    await page.waitForTimeout(2500);
    const errText = ((await page.locator('[data-testid="account-provisioning-error"]').textContent().catch(() => '')) || '').trim();
    const statusVal = (
      (await page.locator('[data-testid="account-link-status-value"]').textContent().catch(() => '')) || ''
    ).trim();
    if (errText) {
      note(`Staff link-status API returned an error (recorded, not invented): ${errText.slice(0, 200)}`);
    } else if (statusVal) {
      step(`Staff link status loaded: ${statusVal}`);
      note(`Staff view shows link status "${statusVal}" for student ${studentId} (live Dev read)`);
    } else {
      note('STOPPED_STEP: Staff link-status panel did not show a status value after load');
    }
  }

  step('Student 1 opens student portal and looks for own account link-status strip');
  await page.goto(`${base}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(2000);

  const strip = page.locator('[data-testid="student-account-link-status"]');
  await strip.waitFor({ state: 'visible', timeout: 30000 });
  step('Student portal account link-status strip is visible');
  await strip.first().scrollIntoViewIfNeeded();

  const ownStatus = (
    (await page.locator('[data-testid="student-account-link-status-value"]').textContent().catch(() => '')) || ''
  ).trim();
  const portalAccess = (
    (await page.locator('[data-testid="student-account-link-portal-access"]').textContent().catch(() => '')) || ''
  ).trim();

  if (ownStatus) {
    step(`Student portal shows link="${ownStatus}" portalAccess="${portalAccess || 'n/a'}"`);
    note(
      'Portal integration piece of Connect account setup (STM-07-EN-005) exercised. Live Dev currently has zero rows in stu.student_iam_links; strip may show NOT_LINKED / unknown without inventing a link.'
    );
  } else {
    note(
      'STOPPED_STEP: Link-status strip visible but no status value — likely no student profile resolution for this session (no invented link/account).'
    );
  }

  if (!stack.lifecycle8096) {
    note('Lifecycle service (:8096) not up — lifecycle→student notify not exercised in browser (covered by Java unit tests).');
  } else {
    note(
      'Lifecycle service is up; browser demo does not mutate student status (cut-access already covered by unit tests). Event-bus fan-out remains parked.'
    );
  }
}
