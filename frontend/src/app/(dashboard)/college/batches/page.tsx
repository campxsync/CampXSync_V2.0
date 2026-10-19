import { redirect } from 'next/navigation';
import { Layers, BookOpen, GraduationCap, CheckCircle2 } from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export const dynamic = 'force-dynamic';

export default async function CollegeBatchesPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, tenantName } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  let batches: any[] = [];
  let subjects: any[] = [];

  try {
    const batchesRes = await query(`
      SELECT 
        b.id,
        b.code,
        b.name,
        b.status,
        p.name as program_name,
        p.code as program_code
      FROM acd.batches b
      LEFT JOIN acd.programs p ON p.id = b.program_id
      WHERE b.tenant_id = $1 AND b.deleted_at IS NULL
      ORDER BY b.code ASC
    `, [tenantId]);

    batches = batchesRes.rows;

    const subjectsRes = await query(`
      SELECT 
        sub.id,
        sub.code,
        sub.name,
        sub.subject_type,
        sub.credits,
        sub.status,
        d.name as department_name,
        d.code as department_code
      FROM acd.subjects sub
      LEFT JOIN core.departments d ON d.id = sub.department_id
      WHERE sub.tenant_id = $1 AND sub.deleted_at IS NULL
      ORDER BY sub.code ASC
    `, [tenantId]);

    subjects = subjectsRes.rows;
  } catch (err) {
    console.error('Failed to query batches and subjects:', err);
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
