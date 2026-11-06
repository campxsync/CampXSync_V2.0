import { query } from '../frontend/src/lib/db.ts';

async function testParam() {
  const sql = 'SELECT id, status, role_code FROM plat.platform_admins WHERE user_id = $1 AND role_code = $2 AND status = $3 AND deleted_at IS NULL LIMIT 1';
  const params = ['54e8308c-ada3-4065-a9fe-51814af6b971', 'SUPER_ADMIN', 'ACTIVE'];
  const res = await query(sql, params);
  console.log('Query succeeded, rows:', res.rows);

  // Test profile query
  const pSql = 'SELECT id, tenant_id, status, deleted_at FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1';
  const pRes = await query(pSql, ['70a8ffae-b5ad-4c61-a401-16f220893df4']);
  console.log('Profile query succeeded, rows:', pRes.rows);

  // Test role assignments query
  const aSql = `SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to,
                r.code as role_code, r.name as role_name
         FROM iam.role_assignments ra
         JOIN iam.roles r ON ra.role_id = r.id
         WHERE ra.user_id = $1
           AND ra.deleted_at IS NULL AND r.deleted_at IS NULL`;
  const aRes = await query(aSql, ['70a8ffae-b5ad-4c61-a401-16f220893df4']);
  console.log('Assignments query succeeded, rows:', aRes.rows);

  // Test tenant query
  const tSql = 'SELECT id, name, code, status, deleted_at FROM core.tenants WHERE id = $1 LIMIT 1';
  const tRes = await query(tSql, [pRes.rows[0].tenant_id]);
  console.log('Tenant query succeeded, rows:', tRes.rows);

  process.exit(0);
}

testParam().catch(console.error);

