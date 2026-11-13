import pg from '../../frontend/node_modules/pg/lib/index.js';

const client = new pg.Client({
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
});

await client.connect();

const tenantId = 'a0000000-0000-0000-0000-000000000001';
const studentId = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
const advisorUserId = '11111111-9e05-0000-0000-000000000001';
const requestId = '33333333-3333-3333-3333-333333330003';

try {
  // 1. Ensure student is ACTIVE
  await client.query(`
    INSERT INTO stu.lifecycle_status (tenant_id, student_id, status, effective_from, updated_at)
    VALUES ($1, $2, 'ACTIVE', clock_timestamp(), clock_timestamp())
    ON CONFLICT (tenant_id, student_id) DO UPDATE 
    SET status = 'ACTIVE', updated_at = clock_timestamp()
  `, [tenantId, studentId]);

  // 2. Ensure faculty advisor assignment is recorded in history
  const advisorAction = `advisor:${advisorUserId}`;
  const existingAdvisor = await client.query(`
    SELECT id FROM stu.lifecycle_history
    WHERE tenant_id = $1 AND student_id = $2 AND action = $3
  `, [tenantId, studentId, advisorAction]);

  if (existingAdvisor.rows.length === 0) {
    await client.query(`
      INSERT INTO stu.lifecycle_history (id, tenant_id, student_id, status, action, actor_role, occurred_at)
      VALUES (gen_random_uuid(), $1, $2, 'ACTIVE', $3, 'COLLEGE_ADMIN', clock_timestamp())
    `, [tenantId, studentId, advisorAction]);
  }

  // 3. Clean up any previous test requests and endorsements for this student
  await client.query(`
    DELETE FROM stu.lifecycle_history
    WHERE tenant_id = $1 AND student_id = $2 AND action LIKE 'hod.endorsement:%'
  `, [tenantId, studentId]);

  await client.query(`
    DELETE FROM stu.lifecycle_requests
    WHERE tenant_id = $1 AND id = $2
  `, [tenantId, requestId]);

  // 4. Insert clean PENDING leave request
  await client.query(`
    INSERT INTO stu.lifecycle_requests (id, tenant_id, student_id, request_type, status, reason, effective_date, created_at)
    VALUES ($1, $2, $3, 'LEAVE', 'PENDING', 'Medical recovery leave following orthopedic surgery [Faculty Advisory Endorsement Pending]', '2026-12-01', clock_timestamp())
  `, [requestId, tenantId, studentId]);

  console.log('Seeded STM-04-US-003 demo data:');
  console.log('- Student:', studentId, '(ACTIVE)');
  console.log('- Advisor:', advisorUserId);
  console.log('- Request:', requestId, '(PENDING)');
} finally {
  await client.end();
}
