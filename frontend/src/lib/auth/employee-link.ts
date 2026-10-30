import { query } from '@/lib/db';

export async function resolveEmployeeId(
  tenantId: string,
  userId: string,
  email: string
): Promise<string | null> {
  const res = await query(
    `SELECT e.id
     FROM hrm.employees e
     LEFT JOIN iam.user_profiles up
       ON up.person_id = e.person_id
      AND up.tenant_id = e.tenant_id
      AND up.deleted_at IS NULL
     LEFT JOIN core.persons p
       ON p.id = e.person_id
      AND p.deleted_at IS NULL
     WHERE e.tenant_id = $1
       AND e.deleted_at IS NULL
       AND (
         e.person_id = $2::uuid
         OR up.id = $2::uuid
         OR lower(p.primary_email) = lower($3)
         OR lower(up.email) = lower($3)
       )
     LIMIT 1`,
    [tenantId, userId, email]
  );
  return res.rows[0]?.id ?? null;
}
