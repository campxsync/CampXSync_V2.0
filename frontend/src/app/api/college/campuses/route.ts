import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  code: z.string().trim().regex(/^[A-Z0-9_-]+$/),
  name: z.string().trim().min(3).max(160),
  university: z.string().trim().min(3).max(160),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a campus.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const college = await query(
      `SELECT id, affiliating_university FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND ($3::uuid IS NULL OR id = $3)`,
      [data.collegeId, auth.tenantId, auth.collegeId]
    );
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');

    const created = await withTransaction(async (client) => {
      const existing = await client.query(
        `SELECT count(*)::int AS n FROM core.campuses WHERE college_id = $1 AND deleted_at IS NULL`,
        [data.collegeId]
      );
      const campus = await client.query(
        `INSERT INTO core.campuses (tenant_id, college_id, code, name, is_primary, status, created_by)
         VALUES ($1, $2, $3, $4, $5, 'ACTIVE', $6)
         RETURNING id, code, status`,
        [auth.tenantId, data.collegeId, data.code, data.name, existing.rows[0].n === 0, auth.userId]
      );
      if (!college.rows[0].affiliating_university) {
        await client.query(
          `UPDATE core.colleges SET affiliating_university = $2, updated_by = $3 WHERE id = $1 AND affiliating_university IS NULL`,
          [data.collegeId, data.university, auth.userId]
        );
      }
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'CAMPUS_RECORDED', 'COLLEGE_ADMIN', $3, 'CAMPUS', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, data.collegeId, auth.userId, campus.rows[0].id, JSON.stringify({ code: data.code })]
      );
      return campus.rows[0];
    });
    return NextResponse.json({
      success: true,
      message: 'Campus recorded under this college. No second institute was created, and no load test was run.',
      campus: created,
    }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That campus code already exists.');
    console.error('POST /api/college/campuses', err);
    return problem(500, 'Server Error', 'The campus could not be saved.');
  }
}
