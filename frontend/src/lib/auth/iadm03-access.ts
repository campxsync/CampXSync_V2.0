/**
 * IADM-03 BFF gates. Java remains authoritative for permission keys and ceilings.
 */
export function canReadIadm03Access(role: string | null | undefined): boolean {
  return (
    role === 'platform_admin' ||
    role === 'institution_admin' ||
    role === 'auditor' ||
    role === 'system_admin'
  );
}

export function canMutateIadm03Profiles(role: string | null | undefined): boolean {
  return role === 'platform_admin' || role === 'institution_admin' || role === 'auditor';
}

/** R04 may submit access requests only (US-023). */
export function canSubmitIadm03AccessRequest(role: string | null | undefined): boolean {
  return (
    role === 'platform_admin' ||
    role === 'institution_admin' ||
    role === 'college_admin' ||
    role === 'auditor'
  );
}

/** US-030 — R01 only (UI gate; Java enforces R01 vs R02). */
export function canMutateIadm03CatalogChange(role: string | null | undefined): boolean {
  return role === 'platform_admin';
}

/** US-031 — platform / system admin governance read. */
export function canReadIadm03Governance(role: string | null | undefined): boolean {
  return role === 'platform_admin' || role === 'system_admin';
}
