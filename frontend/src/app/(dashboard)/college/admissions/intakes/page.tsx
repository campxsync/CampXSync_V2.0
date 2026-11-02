import { redirect } from 'next/navigation';
import Link from 'next/link';
import { 
  ArrowLeft, 
  Calendar, 
  CheckCircle2, 
  AlertCircle, 
  Clock, 
  DollarSign, 
  Layers,
  GraduationCap
} from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { fetchActiveIntakes } from '@/lib/api/admissions';
import { CreateIntakeDialog } from './create-intake-dialog';

export const dynamic = 'force-dynamic';

export default async function AdmissionsIntakesManagementPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, userId, collegeId } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  // Fetch active and scheduled admission intakes
  const intakes = await fetchActiveIntakes(tenantId, userId, 'COLLEGE_ADMIN', collegeId);

  // Available programs for intake binding
  const defaultPrograms = [
    { id: '11111111-2222-3333-4444-555555555551', name: 'B.Tech Computer Science and Engineering', code: 'CSE-UG' },
    { id: '11111111-2222-3333-4444-555555555552', name: 'B.Tech Electronics and Communication', code: 'ECE-UG' },
    { id: '11111111-2222-3333-4444-555555555553', name: 'B.Tech Mechanical Engineering', code: 'MECH-UG' },
  ];

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Link href="/college/admissions" className="inline-flex items-center text-xs font-semibold text-[#1F6FEB] hover:underline mb-2">
            <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Back to Admissions Hub
          </Link>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Admission Intakes & Eligibility</h1>
            <Badge status="ACTIVE" label={`${intakes.length} Active`} />
          </div>
          <p className="mt-1 text-sm text-[#475569]">
            Configure academic intake cycles, program capacities, application windows, and automated eligibility criteria.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <CreateIntakeDialog programs={defaultPrograms} />
          <Link href="/college/admissions">
            <Button variant="outline" size="sm">
              View Applications
            </Button>
          </Link>
        </div>
      </div>

      {/* Intakes List */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        {intakes.length === 0 ? (
          <div className="col-span-full rounded-[12px] border border-dashed border-[#CBD5E1] p-12 text-center">
            <Layers className="mx-auto h-12 w-12 text-[#94A3B8]" />
            <h3 className="mt-4 text-base font-semibold text-[#0B1F3A]">No Active Admission Intakes</h3>
            <p className="mt-2 text-sm text-[#64748B]">
              No active intake cycles found for this academic period. Click "+ Create Intake Cycle" to create one.
            </p>
            <div className="mt-4">
              <CreateIntakeDialog programs={defaultPrograms} />
            </div>
          </div>
        ) : (
          intakes.map((intake) => (
            <Card key={intake.id} className="flex flex-col justify-between hover:shadow-md transition-shadow">
              <CardHeader>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <span className="text-xs font-mono font-semibold uppercase tracking-wider text-[#1F6FEB]">
                      {intake.intakeCode}
                    </span>
                    <CardTitle className="mt-1">{intake.intakeName}</CardTitle>
                  </div>
                  <Badge 
                    status={intake.windowOpen ? 'ACTIVE' : 'LOCKED'} 
                    label={intake.windowOpen ? 'OPEN' : 'CLOSED'} 
                  />
                </div>
                <CardDescription className="line-clamp-2">
                  Program ID: {intake.programId ? intake.programId.substring(0, 8) : 'General'}
                </CardDescription>
              </CardHeader>

              <CardContent className="space-y-4 pt-2">
                <div className="grid grid-cols-2 gap-3 rounded-[8px] bg-[#F8FAFC] p-3 text-xs">
                  <div>
                    <span className="text-[#64748B]">Total Capacity:</span>
                    <p className="font-bold text-sm text-[#0B1F3A]">{intake.capacity} Seats</p>
                  </div>
                  <div>
                    <span className="text-[#64748B]">Admitted:</span>
                    <p className="font-bold text-sm text-[#15803D]">{intake.admittedCount || 0}</p>
                  </div>
                </div>

                <div className="space-y-2 text-xs text-[#475569]">
                  <div className="flex items-center gap-2">
                    <Calendar className="h-3.5 w-3.5 text-[#64748B]" />
                    <span>
                      Window: {new Date(intake.applicationWindowStart).toLocaleDateString()} – {new Date(intake.applicationWindowEnd).toLocaleDateString()}
                    </span>
                  </div>

                  <div className="flex items-center gap-2">
                    <DollarSign className="h-3.5 w-3.5 text-[#64748B]" />
                    <span>
                      Fee: {intake.feeRequired ? `${intake.currency} ${intake.feeAmount}` : 'No Application Fee'}
                    </span>
                  </div>
                </div>

                <div className="pt-2 border-t border-[#E3E8EF] flex items-center justify-between">
                  <span className="text-xs font-medium text-[#64748B]">Status</span>
                  <Badge status={intake.active ? 'ACTIVE' : 'ARCHIVED'} label={intake.active ? 'PUBLISHED' : 'DRAFT'} />
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
