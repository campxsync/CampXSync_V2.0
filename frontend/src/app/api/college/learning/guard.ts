import { NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

export async function requireCollegeAdmin(detail: string) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin' || !resolution.session.tenantId) {
    return {
      error: NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail,
        },
        { status: 403 }
      ),
    };
  }
  return {
    tenantId: resolution.session.tenantId,
    userId: resolution.session.userId,
    collegeId: resolution.session.assignedCollegeId,
  };
}

export function problem(status: number, title: string, detail: string) {
  const types: Record<number, string> = {
    400: 'validation-failed',
    404: 'not-found',
    409: 'conflict',
  };
  return NextResponse.json(
    {
      type: `https://campxsync.edu/errors/${types[status] || 'bad-request'}`,
      title,
      status,
      detail,
    },
    { status }
  );
}
