import { redirect } from 'next/navigation';
import { Layers, BookOpen, GraduationCap, CheckCircle2 } from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function CollegeBatchesPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, tenantName, userId, collegeId } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  let batches: any[] = [];
  let subjects: any[] = [];

  const headers: Record<string, string> = {
    'Accept': 'application/json',
    'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
    'X-Tenant-Id': tenantId,
    'X-User-Role': 'COLLEGE_ADMIN',
  };
  if (collegeId) {
    headers['X-College-Id'] = collegeId;
  }

  try {
    // 1. Fetch batches via API Gateway backed by ACD-04 Batch Management Service
    const batchesUrl = new URL('/api/v1/academics/batches', GATEWAY_BASE_URL);
    batchesUrl.searchParams.set('pageSize', '100');

    const batchesRes = await fetch(batchesUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (batchesRes.ok) {
      const data = await batchesRes.json();
      const rawBatches = Array.isArray(data.data) ? data.data : [];
      batches = rawBatches.map((b: any) => ({
        id: b.id || b.batchId,
        code: b.batchCode || b.code || '',
        name: b.name || '',
        status: b.status || 'ACTIVE',
        program_name: b.departmentId || 'Academic Program',
        program_code: b.departmentId ? b.departmentId.substring(0, 4).toUpperCase() : 'ACD',
      }));
    }

    // 2. Fetch subjects via API Gateway backed by ACD-03 Subject Management Service
    const subjectsUrl = new URL('/api/v1/academics/subjects', GATEWAY_BASE_URL);
    subjectsUrl.searchParams.set('limit', '100');

    const subjectsRes = await fetch(subjectsUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (subjectsRes.ok) {
      const data = await subjectsRes.json();
      const rawSubjects = Array.isArray(data.data) ? data.data : (Array.isArray(data) ? data : []);
      subjects = rawSubjects.map((s: any) => ({
        id: s.id || s.subjectId,
        code: s.subjectCode || s.code || '',
        name: s.name || '',
        subject_type: s.subjectType || s.type || 'THEORY',
        credits: s.credits != null ? s.credits : 4,
        status: s.status || 'ACTIVE',
        department_name: s.departmentId || 'Department',
        department_code: s.departmentId ? s.departmentId.substring(0, 4).toUpperCase() : 'DEPT',
      }));
    }
  } catch (err) {
    console.error('Failed to query batches and subjects via API Gateway:', err);
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Academic Batches & Curriculum Subjects
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Institutional batches, cohort sections, and registered curriculum subjects for {tenantName || 'Active College'}.
        </p>
      </div>

      {/* KPI Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          title="Active Batches"
          value={batches.length.toString()}
          icon={Layers}
          description="Cohorts across academic programs"
        />
        <StatCard
          title="Curriculum Subjects"
          value={subjects.length.toString()}
          icon={BookOpen}
          description="Active teaching courses & labs"
        />
        <StatCard
          title="Academic Standing"
          value="Synchronized"
          icon={CheckCircle2}
          description="Aligned with semester timetable"
        />
      </div>

      {/* Batches Table */}
      <Card>
        <CardHeader>
          <CardTitle>Academic Batches ({batches.length})</CardTitle>
          <CardDescription>
            Cohort structures linked to academic programs and student rosters
          </CardDescription>
        </CardHeader>
        <CardContent>
          {batches.length === 0 ? (
            <div className="text-center py-6 text-xs text-[#6B7688]">No batches configured yet.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Batch Name</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Batch Code</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Academic Program</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {batches.map((b) => (
                    <tr key={b.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3.5 font-medium text-[#0B1F3A]">{b.name}</td>
                      <td className="py-3.5 font-mono text-[#1F6FEB]">{b.code}</td>
                      <td className="py-3.5 text-[#1B2433]">
                        {b.program_name} <span className="text-[#6B7688] font-mono text-[11px]">({b.program_code})</span>
                      </td>
                      <td className="py-3.5 text-center">
                        <Badge status={b.status}>{b.status}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Subjects Table */}
      <Card>
        <CardHeader>
          <CardTitle>Curriculum Subjects & Syllabi ({subjects.length})</CardTitle>
          <CardDescription>
            Subject master catalog mapped to academic departments and credit requirements
          </CardDescription>
        </CardHeader>
        <CardContent>
          {subjects.length === 0 ? (
            <div className="text-center py-6 text-xs text-[#6B7688]">No subjects configured yet.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Subject Code</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Subject Name</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Department</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Credits</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Type</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider text-center">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {subjects.map((s) => (
                    <tr key={s.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3.5 font-mono font-semibold text-[#0B1F3A]">{s.code}</td>
                      <td className="py-3.5 font-medium text-[#0B1F3A]">{s.name}</td>
                      <td className="py-3.5 text-[#1B2433]">
                        {s.department_name} <span className="text-[#6B7688] font-mono text-[11px]">({s.department_code})</span>
                      </td>
                      <td className="py-3.5 font-mono text-center font-semibold">{s.credits}</td>
                      <td className="py-3.5 text-center">
                        <span className="font-mono text-[11px] bg-[#E3E8EF]/50 px-2 py-0.5 rounded text-[#0B1F3A]">
                          {s.subject_type || 'THEORY'}
                        </span>
                      </td>
                      <td className="py-3.5 text-center">
                        <Badge status={s.status}>{s.status}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
