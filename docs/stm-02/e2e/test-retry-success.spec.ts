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

test('Step 3b: Handover Retry with Live STM-01 (Status STUDENT_CREATED)', async ({ page }) => {
  const creds = getEnvCredentials();

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
    'idempotency-key': 'E2E-STM02-HANDOVER-RETRY-001',
    'x-correlation-id': 'E2E-STM02-RETRY-SUCCESS-TEST',
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const retryResp = await page.request.post(
    `http://localhost:3000/api/college/admissions/applications/${applicationId}/handover`,
    {
      headers,
      data: { idempotencyKey: 'E2E-STM02-HANDOVER-RETRY-001' },
    }
  );

  console.log('Handover retry HTTP status:', retryResp.status());
  const retryJson = await retryResp.json();
  console.log('Handover retry response body:', retryJson);

  expect(retryResp.ok()).toBeTruthy();
  expect(retryJson.enrolmentStatus).toBe('STUDENT_CREATED');
  expect(retryJson.createdStudentId).toBeTruthy();

  // Read back DB
  const handoverRows = await dbQuery('SELECT * FROM stu.admission_handovers WHERE application_id = $1 ORDER BY updated_at DESC', [applicationId]);
  console.log('DB Handover status after retry:', handoverRows[0].enrolment_status, 'student_id:', handoverRows[0].created_student_id);

  expect(handoverRows[0].enrolment_status).toBe('STUDENT_CREATED');
  expect(handoverRows[0].created_student_id).toBe(retryJson.createdStudentId);

  fs.writeFileSync(path.join(EVIDENCE_DIR, 'step3_retry_success_evidence.json'), JSON.stringify({
    test: 'live STM-01 handover retry success',
    apiResponse: retryJson,
    dbRow: handoverRows[0],
    verified: true,
  }, null, 2));

  console.log('>>> [PASSED] Handover retry successfully recovered to STUDENT_CREATED with student ID:', retryJson.createdStudentId);
});

