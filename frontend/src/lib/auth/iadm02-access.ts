/**
 * IADM-02 BFF gates. Java remains authoritative for ceilings and auditor deny message.
 * Auditor may attempt mutations so the service can return "Auditor access is read-only".
 */
export function canReadIadm02Config(role: string | null | undefined): boolean {
  return role === 'platform_admin' || role === 'auditor';
}

export function canAttemptIadm02Mutation(role: string | null | undefined): boolean {
  return role === 'platform_admin' || role === 'auditor';
}
