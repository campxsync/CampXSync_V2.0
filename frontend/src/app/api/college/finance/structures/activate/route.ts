import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../../learning/guard';

const Schema = z.object({ structureId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can activate a fee structure.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', 'A fee structure is required.');
    const current = await query(
      `SELECT id, status, college_id FROM fin.fee_structures WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [parsed.data.structureId, auth.tenantId]
    );
    const structure = current.rows[0];
    if (!structure) return problem(404, 'Not Found', 'That fee structure is not in this institute.');
    if (structure.status !== 'DRAFT') return problem(409, 'Conflict', 'Only a draft fee structure can be activated.');
    await query(
      `UPDATE fin.fee_structure_versions SET status = 'ACTIVE', updated_by = $3, updated_at = now(), row_version = row_version + 1
       WHERE structure_id = $1 AND tenant_id = $2 AND version_no = 1 AND deleted_at IS NULL`,
      [structure.id, auth.tenantId, auth.userId]
    );
    await query(
      `UPDATE fin.fee_structures SET status = 'ACTIVE', updated_by = $3, updated_at = now(), row_version = row_version + 1
       WHERE id = $1 AND tenant_id = $2`,
      [structure.id, auth.tenantId, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
       VALUES ($1, $2, now(), 'FEE_STRUCTURE_ACTIVATED', 'COLLEGE_ADMIN', $3, 'FEE_STRUCTURE', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, structure.college_id, auth.userId, structure.id]
    );
    return NextResponse.json({ success: true, message: 'Fee structure activated.' });
  } catch (err) {
    console.error('POST /api/college/finance/structures/activate', err);
    return problem(500, 'Server Error', 'The fee structure could not be activated.');
  }
}
