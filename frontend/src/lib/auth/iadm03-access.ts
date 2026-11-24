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
