import { test, expect, Page } from '@playwright/test';
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
const ARTIFACTS_DIR = path.resolve(__dirname, 'artifacts');

if (!fs.existsSync(EVIDENCE_DIR)) fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
if (!fs.existsSync(ARTIFACTS_DIR)) fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });

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

async function loginAs(page: Page, email: string, pass: string): Promise<string> {
  await page.context().clearCookies();
  await page.goto('http://localhost:3000/login');
  await page.waitForSelector('input[type="email"]');
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', pass);
  await page.click('button[type="submit"]', { force: true });
  await page.waitForFunction(() => {
    return !window.location.pathname.includes('/login');
  }, { timeout: 15000 }).catch(async (e) => {
    const errText = await page.locator('.text-\\[\\#D64545\\]').textContent().catch(() => null);
    if (errText) throw new Error(`Login failed for ${email}: ${errText}`);
    throw e;
  });
  await page.waitForTimeout(1000);

  const cookies = await page.context().cookies();
  console.log('COOKIE NAMES:', cookies.map(c => c.name));
  let token = '';

  for (const c of cookies) {
    if (c.name.includes('-auth-token')) {
      let val = c.value;
      if (val.startsWith('base64-')) {
        val = Buffer.from(val.substring(7), 'base64').toString('utf8');
      }
      try {
        const parsed = JSON.parse(decodeURIComponent(val));
        if (parsed && parsed.access_token) {
          token = parsed.access_token;
          break;
        }
      } catch {
        try {
          const parsed = JSON.parse(val);
          if (parsed && parsed.access_token) {
            token = parsed.access_token;
            break;
          }
        } catch {}
      }
    }
  }

  // Also query localStorage if not found in cookies
  if (!token) {
    token = await page.evaluate(() => {
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i);
        if (k && k.includes('-auth-token')) {
          try {
            const parsed = JSON.parse(localStorage.getItem(k) || '');
            if (parsed && parsed.access_token) return parsed.access_token;
          } catch {}
        }
      }
      return '';
    });
  }

  return token;
}

test.describe('STM-02 Admissions Complete Browser & API Journey', () => {
  test('Complete End-to-End Admissions Journey to STM-01 Student Creation', async ({ browser }) => {
    const creds = getEnvCredentials();
    const context = await browser.newContext({
      recordHar: {
        path: path.join(ARTIFACTS_DIR, 'admissions-journey.har'),
        mode: 'minimal',
      },
    });
    const page = await context.newPage();

    let applicationId = '';
    let documentId = '';
    let offerId = '';
    let createdStudentId = '';
    const correlationId = 'E2E-STM02-CORR-12345678';
    const idempotencyKey = 'E2E-STM02-HANDOVER-KEY-001';

    // =========================================================================
    // STEP 1: Applicant Portal - Real Login, Apply & Attach Document
    // =========================================================================
    console.log('>>> [STEP 1] Real Login as Applicant and Submit Application...');
    const applicantToken = await loginAs(page, creds.E2E_APPLICANT_EMAIL, creds.E2E_APPLICANT_PASSWORD);
    expect(applicantToken).toBeTruthy();

    await page.goto('http://localhost:3000/student/admissions');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step1-applicant-portal.png') });

    // Submit Application
    const applyHeaders: Record<string, string> = {
      'x-correlation-id': correlationId,
    };
    if (applicantToken) applyHeaders['Authorization'] = `Bearer ${applicantToken}`;

    const applyResp = await page.request.post('http://localhost:3000/api/student/admissions/applications', {
      headers: applyHeaders,
      data: {
        intakeId: 'e2e00000-0000-0000-0000-000000000050',
        declarationAccepted: true,
        candidateName: 'E2E-STM02 Applicant',
        email: creds.E2E_APPLICANT_EMAIL,
        phone: '+91 9876543210',
        qualificationGrade: 'Grade A (92.5%)',
        applicantData: {
          firstName: 'Admitted',
          lastName: 'Student',
          dateOfBirth: '2005-06-15',
          gender: 'MALE',
          nationality: 'INDIAN',
        },
      },
    });
    if (!applyResp.ok()) {
      console.error('Apply response error:', applyResp.status(), await applyResp.text());
    }
    expect(applyResp.ok()).toBeTruthy();
    const applyJson = await applyResp.json();
    applicationId = applyJson.id;
    expect(applicationId).toBeTruthy();
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step1_apply_api_response.json'), JSON.stringify(applyJson, null, 2));

    // DB Read-back
    const appDbRows = await dbQuery('SELECT * FROM stu.applications WHERE id = $1', [applicationId]);
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step1_apply_db_readback.json'), JSON.stringify(appDbRows, null, 2));
    expect(appDbRows.length).toBe(1);

    // Attach Document
    const attachResp = await page.request.post(`http://localhost:3000/api/college/admissions/applications/${applicationId}/documents`, {
      headers: applyHeaders,
      data: {
        documentType: 'TRANSCRIPT',
        documentRefUrl: '/storage/e2e-docs/transcript.pdf',
        fileName: 'E2E-STM02-TRANSCRIPT.pdf',
        fileSizeBytes: 2048,
        mimeType: 'application/pdf',
      },
    });
    expect(attachResp.ok()).toBeTruthy();
    const attachJson = await attachResp.json();
    documentId = attachJson.id;
    expect(documentId).toBeTruthy();
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step1_attach_doc_api_response.json'), JSON.stringify(attachJson, null, 2));

    const docDbRows = await dbQuery('SELECT * FROM stu.application_documents WHERE id = $1', [documentId]);
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step1_doc_db_readback.json'), JSON.stringify(docDbRows, null, 2));
    expect(docDbRows.length).toBe(1);

    await page.goto('http://localhost:3000/student/admissions');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step1-applied.png') });
    console.log(`[STEP 1 PASSED] Application: ${applicationId}, Document: ${documentId}`);

    // =========================================================================
    // STEP 2: Document Verification & Reviewer Assignment (Verifier Persona)
    // =========================================================================
    console.log('>>> [STEP 2] Real Login as Document Verifier...');
    const verifierToken = await loginAs(page, creds.E2E_VERIFIER_EMAIL, creds.E2E_VERIFIER_PASSWORD);
    expect(verifierToken).toBeTruthy();

    await page.goto(`http://localhost:3000/college/admissions/applications/${applicationId}`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step2-verifier-portal.png') });

    const verifierHeaders: Record<string, string> = {
      'x-correlation-id': correlationId,
    };
    if (verifierToken) verifierHeaders['Authorization'] = `Bearer ${verifierToken}`;

    // Verify Document
    const verifyResp = await page.request.post(
      `http://localhost:3000/api/college/admissions/applications/${applicationId}/documents/${documentId}/verify`,
      {
        headers: verifierHeaders,
        data: { status: 'VERIFIED' },
      }
    );
    expect(verifyResp.ok()).toBeTruthy();
    const verifyJson = await verifyResp.json();
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step2_verify_api_response.json'), JSON.stringify(verifyJson, null, 2));

    const docVerifyDb = await dbQuery('SELECT * FROM stu.application_documents WHERE id = $1', [documentId]);
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step2_verify_db_readback.json'), JSON.stringify(docVerifyDb, null, 2));
    expect(docVerifyDb[0].verification_status).toBe('VERIFIED');

    // Assign Faculty Reviewer
    const assignResp = await page.request.post(
      `http://localhost:3000/api/college/admissions/applications/${applicationId}/assign`,
      {
        headers: verifierHeaders,
        data: {
          reviewerId: 'e2e00000-0000-0000-0000-000000000004',
          departmentId: 'e2e00000-0000-0000-0000-000000000020',
        },
      }
    );
    expect(assignResp.ok()).toBeTruthy();
    const assignJson = await assignResp.json();
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step2_assign_api_response.json'), JSON.stringify(assignJson, null, 2));

    await page.goto(`http://localhost:3000/college/admissions/applications/${applicationId}`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step2-verified-assigned.png') });
    console.log('[STEP 2 PASSED] Document verified and Faculty Reviewer assigned.');

    // =========================================================================
    // STEP 3: Faculty Review (Faculty Persona)
    // =========================================================================
    console.log('>>> [STEP 3] Real Login as Faculty Reviewer...');
    const reviewerToken = await loginAs(page, creds.E2E_REVIEWER_EMAIL, creds.E2E_REVIEWER_PASSWORD);
    expect(reviewerToken).toBeTruthy();

    await page.goto(`http://localhost:3000/college/admissions/applications/${applicationId}`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step3-faculty-review-portal.png') });

    const reviewerHeaders: Record<string, string> = {
      'x-correlation-id': correlationId,
    };
    if (reviewerToken) reviewerHeaders['Authorization'] = `Bearer ${reviewerToken}`;

    const reviewResp = await page.request.post(
      `http://localhost:3000/api/college/admissions/applications/${applicationId}/reviews`,
      {
        headers: reviewerHeaders,
        data: {
          totalScore: 95,
          recommendation: 'ADMIT',
          confidentialComments: 'E2E Reviewer: Outstanding candidate, unconditional admission recommended',
          applicantFacingRemarks: 'Recommended for admission offer',
          rubricVersion: '1.0',
        },
      }
    );
    expect(reviewResp.ok()).toBeTruthy();
    const reviewJson = await reviewResp.json();
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step3_review_api_response.json'), JSON.stringify(reviewJson, null, 2));

    const reviewDbRows = await dbQuery('SELECT * FROM stu.application_reviews WHERE application_id = $1', [applicationId]);
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step3_review_db_readback.json'), JSON.stringify(reviewDbRows, null, 2));
    expect(reviewDbRows.length).toBeGreaterThan(0);
    console.log('[STEP 3 PASSED] Faculty review submitted successfully.');

    // =========================================================================
    // STEP 4: Approval Decision (Approver Persona != Reviewer)
    // =========================================================================
    console.log('>>> [STEP 4] Real Login as Approver...');
    const approverToken = await loginAs(page, creds.E2E_APPROVER_EMAIL, creds.E2E_APPROVER_PASSWORD);
    expect(approverToken).toBeTruthy();

    await page.goto(`http://localhost:3000/college/admissions/applications/${applicationId}`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step4-approver-portal.png') });

    const approverHeaders: Record<string, string> = {
      'x-correlation-id': correlationId,
    };
    if (approverToken) approverHeaders['Authorization'] = `Bearer ${approverToken}`;

    const decisionResp = await page.request.post(
      `http://localhost:3000/api/college/admissions/applications/${applicationId}/decision`,
      {
        headers: approverHeaders,
        data: {
          decisionType: 'OFFER_APPROVED',
          decisionNotes: 'E2E Approver: Formal offer approved ratified by Dean',
        },
      }
    );
    expect(decisionResp.ok()).toBeTruthy();
    const decisionJson = await decisionResp.json();
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step4_decision_api_response.json'), JSON.stringify(decisionJson, null, 2));

    const decisionDbRows = await dbQuery('SELECT * FROM stu.admission_decisions WHERE application_id = $1 ORDER BY created_at DESC', [applicationId]);
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step4_decision_db_readback.json'), JSON.stringify(decisionDbRows, null, 2));
    expect(decisionDbRows.length).toBeGreaterThanOrEqual(1);
    expect(decisionDbRows[0].decision_type).toBe('OFFER_APPROVED');
    console.log('[STEP 4 PASSED] Offer approved decision committed.');

    // =========================================================================
    // STEP 5: Issue Offer (Approver Persona)
    // =========================================================================
    console.log('>>> [STEP 5] Approver Issues Formal Offer...');
    const offerResp = await page.request.post(
      `http://localhost:3000/api/college/admissions/applications/${applicationId}/offers`,
      {
        headers: approverHeaders,
        data: {
          offerCode: 'E2E-STM02-OFFER-001',
          validDays: 14,
        },
      }
    );
    expect(offerResp.ok()).toBeTruthy();
    const offerJson = await offerResp.json();
    offerId = offerJson.id;
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step5_offer_api_response.json'), JSON.stringify(offerJson, null, 2));

    const offerDbRows = await dbQuery('SELECT * FROM stu.admission_offers WHERE application_id = $1 ORDER BY created_at DESC', [applicationId]);
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step5_offer_db_readback.json'), JSON.stringify(offerDbRows, null, 2));
    expect(offerDbRows.length).toBeGreaterThanOrEqual(1);
    expect(['ISSUED', 'ACCEPTED']).toContain(offerDbRows[0].status);

    await page.goto(`http://localhost:3000/college/admissions/applications/${applicationId}`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step5-offer-issued.png') });
    console.log(`[STEP 5 PASSED] Offer issued: ${offerId}`);

    // =========================================================================
    // STEP 6: Offer Acceptance (Applicant Persona)
    // =========================================================================
    console.log('>>> [STEP 6] Real Login as Applicant to Accept Offer...');
    const applicantToken2 = await loginAs(page, creds.E2E_APPLICANT_EMAIL, creds.E2E_APPLICANT_PASSWORD);
    expect(applicantToken2).toBeTruthy();

    await page.goto('http://localhost:3000/student/admissions');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step6-offer-received.png') });

    const applicantHeaders2: Record<string, string> = {
      'x-correlation-id': correlationId,
    };
    if (applicantToken2) applicantHeaders2['Authorization'] = `Bearer ${applicantToken2}`;

    const acceptResp = await page.request.post(
      `http://localhost:3000/api/student/admissions/applications/${applicationId}/accept-offer`,
      {
        headers: applicantHeaders2,
        data: { termsAccepted: true },
      }
    );
    expect(acceptResp.ok()).toBeTruthy();
    const acceptJson = await acceptResp.json();
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step6_accept_api_response.json'), JSON.stringify(acceptJson, null, 2));

    const appAfterAccept = await dbQuery('SELECT status FROM stu.applications WHERE id = $1', [applicationId]);
    const offerAfterAccept = await dbQuery('SELECT status FROM stu.admission_offers WHERE application_id = $1', [applicationId]);
    fs.writeFileSync(
      path.join(EVIDENCE_DIR, 'step6_accept_db_readback.json'),
      JSON.stringify({ application: appAfterAccept, offer: offerAfterAccept }, null, 2)
    );
    expect(offerAfterAccept[0].status).toBe('ACCEPTED');

    await page.goto('http://localhost:3000/student/admissions');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step6-offer-accepted.png') });
    console.log('[STEP 6 PASSED] Offer accepted by applicant.');

    // =========================================================================
    // STEP 7: Handover & STM-01 Student Creation (Approver Persona)
    // =========================================================================
    console.log('>>> [STEP 7] Real Login as Approver and Trigger Handover...');
    const approverToken2 = await loginAs(page, creds.E2E_APPROVER_EMAIL, creds.E2E_APPROVER_PASSWORD);
    expect(approverToken2).toBeTruthy();

    const handoverHeaders: Record<string, string> = {
      'idempotency-key': idempotencyKey,
      'x-correlation-id': correlationId,
    };
    if (approverToken2) handoverHeaders['Authorization'] = `Bearer ${approverToken2}`;

    const handoverResp = await page.request.post(
      `http://localhost:3000/api/college/admissions/applications/${applicationId}/handover`,
      {
        headers: handoverHeaders,
      }
    );
    expect(handoverResp.ok()).toBeTruthy();
    const handoverJson = await handoverResp.json();
    createdStudentId = handoverJson.createdStudentId;
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'step7_handover_api_response.json'), JSON.stringify(handoverJson, null, 2));

    // DB Read-back of Handover & STM-01 Student row
    const handoverDb = await dbQuery('SELECT * FROM stu.admission_handovers WHERE application_id = $1 ORDER BY updated_at DESC', [applicationId]);
    const studentDb = await dbQuery('SELECT * FROM stu.students WHERE id = $1', [createdStudentId]);
    fs.writeFileSync(
      path.join(EVIDENCE_DIR, 'step7_handover_db_readback.json'),
      JSON.stringify({ handover: handoverDb, student: studentDb }, null, 2)
    );

    expect(handoverDb.length).toBeGreaterThanOrEqual(1);
    expect(handoverDb[0].enrolment_status).toBe('STUDENT_CREATED');
    expect(handoverDb[0].created_student_id).toBe(createdStudentId);
    expect(studentDb.length).toBe(1);
    expect(studentDb[0].id).toBe(createdStudentId);

    // Save linkage proof
    const linkageEvidence = {
      tenantId: 'e2e00000-0000-0000-0000-000000000001',
      applicationId,
      applicationNumber: appDbRows[0].application_number,
      handoverId: handoverDb[0].id,
      handoverEnrolmentStatus: handoverDb[0].enrolment_status,
      idempotencyKey,
      correlationId,
      createdStudentId,
      studentAdmissionNo: studentDb[0].admission_no,
      studentStatus: studentDb[0].status,
      studentCreatedAt: studentDb[0].created_at,
    };
    fs.writeFileSync(path.join(EVIDENCE_DIR, 'student_linkage_evidence.json'), JSON.stringify(linkageEvidence, null, 2));

    await page.goto(`http://localhost:3000/college/admissions/applications/${applicationId}`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'step7-handover-complete.png') });

    console.log('\n======================================================');
    console.log('>>> [JOURNEY PASSED] STM-01 Student Created:', createdStudentId);
    console.log('>>> Linkage:', JSON.stringify(linkageEvidence, null, 2));
    console.log('======================================================\n');

    await context.close();
  });
});
