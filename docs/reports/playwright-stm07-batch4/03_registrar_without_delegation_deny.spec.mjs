/**
 * "No delegation denied" browser case.
 * Owner: do not revoke pilot M1. Use College Admin only if it shares the same
 * STM-07.ACCOUNT.A delegation check. It does not — College Admin is rejected as
 * an unauthorised desk role (see Stm07Us003StaffLinkStatusTest
 * #stm07Us003_collegeAdminWithoutBeingOperatorDenied). Keep browser to success
 * case; rely on existing service-level tests for denial.
 */
export async function run({ page, base, stack, step, note }) {
  step('Confirm College Admin is not on the STM-07.ACCOUNT.A delegation path for link-status');
  note(
    'CODE_CHECK: assertCanViewStaffAccountLink allows only IAM operators R01–R03, or Registrar (R31) with active STM-07.ACCOUNT.A. College Admin is denied as unauthorised role even if a delegation were granted.'
  );
  note(
    'STOPPED_CASE: Browser deny demo kept to success-case only. Java denial covered by Stm07Us003StaffLinkStatusTest (college admin / faculty / student negatives). Pilot M1 not revoked; no accounts created.'
  );
  step('Skip College Admin browser path per owner instruction');
}
