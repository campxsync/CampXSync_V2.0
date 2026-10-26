import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  kind: z.enum(['BADGE', 'CERTIFICATE']),
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  programId: z.string().uuid().optional(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can define a credential.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    if (data.kind === 'CERTIFICATE') {
      if (!data.programId) return problem(400, 'Validation Failed', 'A certificate belongs to a program.');
      const program = await query(
        `SELECT id, name FROM acd.programs WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
           AND ($3::uuid IS NULL OR college_id = $3)`,
        [data.programId, auth.tenantId, auth.collegeId]
      );
      if (!program.rows[0]) return problem(404, 'Not Found', 'That program is not in this institute.');
    }

    const inserted = data.kind === 'BADGE'
      ? await query(
          `INSERT INTO lms.badges (tenant_id, code, name, status, created_by)
           VALUES ($1, $2, $3, 'DRAFT', $4)
           RETURNING id, code, name, status`,
          [auth.tenantId, data.code, data.name, auth.userId]
        )
      : await query(
          `WITH credential_program AS (
             INSERT INTO lms.certification_programs (tenant_id, code, name, issuer, status, created_by)
             VALUES ($1, $2, $3, 'College office', 'ACTIVE', $4)
             RETURNING id
           )
           INSERT INTO lms.certifications (tenant_id, program_id, code, name, credential_type, status, created_by)
           SELECT $1, id, $2, $3, 'CERTIFICATE', 'DRAFT', $4 FROM credential_program
           RETURNING id, code, name, status`,
          [auth.tenantId, data.code, data.name, auth.userId]
        );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'CREDENTIAL_DEFINED', 'COLLEGE_ADMIN', $3, $4, $5, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, auth.collegeId, auth.userId, data.kind, inserted.rows[0].id]
    );
    return NextResponse.json({ success: true, message: 'Credential defined as a draft. It is not issued to a student from this screen.', credential: inserted.rows[0] }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A credential with that code already exists.');
    console.error('POST /api/college/learning/credentials', err);
    return problem(500, 'Server Error', 'The credential could not be defined.');
  }
}
