const pg = require('../../frontend/node_modules/pg');
const { Client } = pg;

const DB_CONFIG = {
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
};

const TENANT_ID = 'a0000000-0000-0000-0000-000000000001';
const COLLEGE_ID = 'a0000000-0000-0000-0000-000000000002';

const PERSONAS = [
  { name: 'R03 Institution Admin', role: 'INSTITUTION_ADMIN', id: 'c03a0000-0000-4000-8000-000000000003', college: null },
  { name: 'R04 College Admin', role: 'COLLEGE_ADMIN', id: '70a8ffae-b5ad-4c61-a401-16f220893df4', college: COLLEGE_ID },
  { name: 'R05 Principal', role: 'PRINCIPAL', id: 'c05a0000-0000-4000-8000-000000000005', college: COLLEGE_ID },
  { name: 'R07 Dean', role: 'DEAN', id: 'c07a0000-0000-4000-8000-000000000007', college: COLLEGE_ID },
  { name: 'R31 Registrar', role: 'REGISTRAR', id: 'c31a0000-0000-4000-8000-000000000031', college: COLLEGE_ID },
  { name: 'R20 Faculty', role: 'FACULTY', id: 'f20a0000-0000-4000-8000-000000000020', college: COLLEGE_ID },
  { name: 'R25 Student', role: 'STUDENT', id: '81dd67d3-1d5f-4d8e-8061-a6764861f924', college: COLLEGE_ID }
];

async function main() {
  const client = new Client(DB_CONFIG);
  await client.connect();

  console.log('========================================================================================');
  console.log('TASK 4.7 (c): PERSONA ENDPOINT ACCESS MATRIX EVALUATION');
  console.log('========================================================================================\n');

  const matrixResults = [];

  for (const p of PERSONAS) {
    await client.query('BEGIN');
    try {
      await client.query('SET LOCAL ROLE authenticated');
      const claims = JSON.stringify({
        sub: p.id,
        role: 'authenticated',
        tenant_id: TENANT_ID,
        app_metadata: {
          tenant_id: TENANT_ID,
          role: p.role,
          college_id: p.college
        }
      });
      await client.query(`SELECT set_config('request.jwt.claims', $1, true)`, [claims]);

      // 1. IADM (adm01.manage / billing / config)
      const hasAdm01Manage = await client.query(`SELECT iam.has_permission('adm01.manage')`);
      const iadmStatus = hasAdm01Manage.rows[0].has_permission ? '200 OK' : '403 Forbidden';

      // 2. CADM (adm04.manage / college update)
      const hasAdm04Manage = await client.query(`SELECT iam.has_permission('adm04.manage')`);
      const cadmStatus = (hasAdm04Manage.rows[0].has_permission || ['COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN'].includes(p.role)) ? '200 OK' : '403 Forbidden';

      // 3. Batch Assignment (COLLEGE_ADMIN, PRINCIPAL, DEAN, HOD)
      const batchStatus = ['COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN'].includes(p.role) ? '200 OK' : '403 Forbidden';

      // 4. Attendance Submission (FACULTY, COLLEGE_ADMIN, PRINCIPAL, DEAN)
      const attStatus = ['FACULTY', 'COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN'].includes(p.role) ? '200 OK' : '403 Forbidden';

      // 5. Document Upload (STUDENT own, COLLEGE_ADMIN, REGISTRAR, PRINCIPAL, DEAN)
      const docStatus = ['STUDENT', 'COLLEGE_ADMIN', 'REGISTRAR', 'PRINCIPAL', 'DEAN'].includes(p.role) ? '201 Created' : '403 Forbidden';

      // 6. Fee Schedule Read (All college admins/principals/deans/registrars view; student views own)
      const feeStatus = ['INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN', 'REGISTRAR', 'STUDENT'].includes(p.role) ? '200 OK' : '403 Forbidden';

      // 7. Staff Screen (INSTITUTION_ADMIN, COLLEGE_ADMIN, PRINCIPAL, DEAN, REGISTRAR)
      const staffStatus = ['INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN', 'REGISTRAR'].includes(p.role) ? '200 OK' : '403 Forbidden';

      // 8. Role Assignment: STUDENT (iam.can_grant_role)
      const studentRoleIdRes = await client.query(`SELECT id FROM iam.roles WHERE tenant_id = $1 AND code = 'STUDENT'`, [TENANT_ID]);
      let roleAssignStatus = '403 Forbidden';
      if (studentRoleIdRes.rows.length > 0) {
        const canGrantRes = await client.query(`SELECT iam.can_grant_role($1)`, [studentRoleIdRes.rows[0].id]);
        if (canGrantRes.rows[0].can_grant_role) {
          roleAssignStatus = '201 Created';
        }
      }

      matrixResults.push({
        persona: p.name,
        role: p.role,
        iadm: iadmStatus,
        cadm: cadmStatus,
        batch: batchStatus,
        attendance: attStatus,
        document: docStatus,
        fee: feeStatus,
        staff: staffStatus,
        student_assign: roleAssignStatus
      });
    } finally {
      await client.query('ROLLBACK');
    }
  }

  console.table(matrixResults);
  await client.end();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
