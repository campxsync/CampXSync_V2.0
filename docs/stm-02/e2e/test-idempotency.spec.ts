import { test, expect } from '@playwright/test';
import { Client } from '../../../frontend/node_modules/pg';
import * as fs from 'fs';
import * as path from 'path';

const DB_CONFIG = {
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false },
};

const EVIDENCE_DIR = path.resolve(__dirname, '../evidence/b3');

function getEnvCredentials() {
  const envPath = path.resolve(__dirname, '.env.e2e');
  const lines = fs.readFileSync(envPath, 'utf8').split('\n');
  const env: Record<string, string> = {};
  for (const l of lines) {
    const idx = l.indexOf('=');
    if (idx > 0) {
      env[l.substring(0, idx).trim()] = l.substring(idx + 1).trim();
    }
  }
  return env;
}

async function dbQuery(sql: string, params: any[] = []) {
  const client = new Client(DB_CONFIG);
  await client.connect();
  try {
    const res = await client.query(sql, params);
    return res.rows;
  } finally {
    await client.end();
  }
}

test('Handover Same-Idempotency-Key Repeat Test (No Second Student)', async ({ page }) => {
  const creds = getEnvCredentials();

  // Query DB before repeat call
  const countBefore = await dbQuery("SELECT count(*) FROM stu.students WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001'");
  console.log('Students count before repeat:', countBefore[0].count);

  // Login as approver
  await page.goto('http://localhost:3000/login');
  await page.fill('input[type="email"]', creds.E2E_APPROVER_EMAIL);
  await page.fill('input[type="password"]', creds.E2E_APPROVER_PASSWORD);
  await page.click('button[type="submit"]', { force: true });
  await page.waitForFunction(() => !window.location.pathname.includes('/login'), { timeout: 15000 });
  await page.waitForTimeout(1000);

  const cookies = await page.context().cookies();
  let token = '';
  for (const c of cookies) {
    if (c.name.includes('-auth-token')) {
      let val = c.value;
      if (val.startsWith('base64-')) val = Buffer.from(val.substring(7), 'base64').toString('utf8');
      try {
        const parsed = JSON.parse(decodeURIComponent(val));
        if (parsed?.access_token) { token = parsed.access_token; break; }
      } catch {
        try {
          const parsed = JSON.parse(val);
          if (parsed?.access_token) { token = parsed.access_token; break; }
        } catch {}
      }
    }
  }

  const applicationId = 'd8d6d55d-cd9a-440b-92b2-85f91de4b29d';
  const headers: Record<string, string> = {
    'idempotency-key': 'E2E-STM02-HANDOVER-KEY-001',
    'x-correlation-id': 'E2E-STM02-IDEMP-REPEAT-TEST',
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const repeatResp = await page.request.post(
    `http://localhost:3000/api/college/admissions/applications/${applicationId}/handover`,
    {
      headers,
      data: { idempotencyKey: 'E2E-STM02-HANDOVER-KEY-001' },
    }
  );

  expect(repeatResp.ok()).toBeTruthy();
  const repeatJson = await repeatResp.json();
  console.log('Repeat Handover API Response:', repeatJson);

  expect(repeatJson.createdStudentId).toBe('7a1a49ad-e28e-4ba7-bc2d-02e4e0a73363');
  expect(repeatJson.enrolmentStatus).toBe('STUDENT_CREATED');

  const countAfter = await dbQuery("SELECT count(*) FROM stu.students WHERE tenant_id = 'e2e00000-0000-0000-0000-000000000001'");
  console.log('Students count after repeat:', countAfter[0].count);
  expect(countAfter[0].count).toBe(countBefore[0].count);

  fs.writeFileSync(path.join(EVIDENCE_DIR, 'step3_idempotency_repeat_evidence.json'), JSON.stringify({
    test: 'same-idempotency-key repeat',
    countBefore: countBefore[0].count,
    countAfter: countAfter[0].count,
    returnedStudentId: repeatJson.createdStudentId,
    verified: true,
  }, null, 2));

  console.log('>>> [PASSED] Same-idempotency-key repeat test passed without creating duplicate student.');
});

