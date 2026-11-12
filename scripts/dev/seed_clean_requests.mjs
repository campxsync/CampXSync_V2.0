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

// 1. Ensure students are ACTIVE
const studentIds = [
  '81dd67d3-1d5f-4d8e-8061-a6764861f924',
  '81dd67d3-1d5f-4d8e-8061-a67648610002',
  '81dd67d3-1d5f-4d8e-8061-a67648610003'
];

for (const sId of studentIds) {
  await client.query(`
    INSERT INTO stu.lifecycle_status (tenant_id, student_id, status, effective_from, updated_at)
    VALUES ($1, $2, 'ACTIVE', now() - interval '30 days', now())
    ON CONFLICT (tenant_id, student_id) DO UPDATE SET status = 'ACTIVE', updated_at = now()
  `, [tenantId, sId]);
}

// 2. Ensure transition rules exist for ACTIVE -> ON_LEAVE and ACTIVE -> TRANSFERRED
await client.query(`
  INSERT INTO stu.lifecycle_transition_rules (tenant_id, id, from_status, to_status, requires_approval, active)
  VALUES 
    ($1, '44444444-0004-0000-0000-000000000001', 'ACTIVE', 'TRANSFERRED', true, true),
    ($1, '44444444-0004-0000-0000-000000000002', 'ACTIVE', 'ON_LEAVE', true, true)
  ON CONFLICT (tenant_id, from_status, to_status) DO UPDATE SET active = true, requires_approval = true
`, [tenantId]);

// 3. Update existing pending requests to distinct active students
await client.query(`
  UPDATE stu.lifecycle_requests
  SET student_id = '81dd67d3-1d5f-4d8e-8061-a67648610002', status = 'PENDING', decided_by = null, decided_at = null
  WHERE id = '5f93063a-dde5-4902-a087-95b7fea20a7e'
`);

await client.query(`
  UPDATE stu.lifecycle_requests
  SET student_id = '81dd67d3-1d5f-4d8e-8061-a67648610003', status = 'PENDING', decided_by = null, decided_at = null
  WHERE id = '14a30ed3-4daf-4801-b1d2-345379465cfd'
`);

// 4. Create one fresh pending LEAVE request for student 81dd67d3-1d5f-4d8e-8061-a6764861f924
await client.query(`
  INSERT INTO stu.lifecycle_requests (tenant_id, id, student_id, request_type, status, reason, effective_date, created_at)
  VALUES (
    $1, 
    '99999999-0004-0000-0000-000000000001', 
    '81dd67d3-1d5f-4d8e-8061-a6764861f924', 
    'LEAVE', 
    'PENDING', 
    'Medical leave of absence for semester recovery following sports injury.', 
    '2026-12-05', 
    now()
  )
  ON CONFLICT (tenant_id, id) DO UPDATE 
  SET status = 'PENDING', student_id = EXCLUDED.student_id, reason = EXCLUDED.reason, decided_by = null, decided_at = null
`, [tenantId]);

console.log('Seeded clean pending requests for distinct active students.');

const rRes = await client.query("SELECT id, student_id, request_type, status, reason FROM stu.lifecycle_requests WHERE status = 'PENDING'");
console.log('PENDING REQUESTS:', rRes.rows);

await client.end();

