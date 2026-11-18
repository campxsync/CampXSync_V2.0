'use client';

import { useEffect, useState } from 'react';
import { Calendar, Clock, MapPin, BookOpen } from 'lucide-react';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

type TimetableEntry = {
  id?: string;
  batchId?: string;
  subjectId?: string;
  facultyId?: string;
  roomId?: string;
  dayOfWeek?: string;
  period?: number;
  startTime?: string;
  endTime?: string;
  entryType?: string;
};

type MyTimetablePayload = {
  membershipFound?: boolean;
  batchId?: string | null;
  entries?: TimetableEntry[];
  clientBatchIdIgnored?: boolean;
};

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

export default function StudentTimetablePage() {
  const [payload, setPayload] = useState<MyTimetablePayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch('/api/student/timetable', { cache: 'no-store' });
        const body = await res.json().catch(() => ({}));
        if (!res.ok) {
          if (!cancelled) {
            setError(body?.detail || body?.title || `Unable to load timetable (${res.status})`);
          }
          return;
        }
        // Gateway envelope: { success, data } or bare payload
        const data = (body?.data ?? body) as MyTimetablePayload;
        if (!cancelled) {
          setPayload(data);
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load timetable');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const entries = Array.isArray(payload?.entries) ? payload!.entries! : [];

  return (
    <div className="space-y-6" data-testid="student-timetable-page">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]" data-testid="student-timetable-title">
          My Timetable
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Your published weekly schedule from your enrolled batch. Batch identity is resolved from your login —
          never from a browser-supplied batch id.
        </p>
      </div>

      {loading && (
        <p className="text-sm text-[#6B7688]" data-testid="student-timetable-loading">
          Loading timetable…
        </p>
      )}

      {error && (
        <p className="text-sm text-red-600" data-testid="student-timetable-error" role="alert">
          {error}
        </p>
      )}

      {!loading && !error && payload && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <StatCard
              title="Batch membership"
              value={payload.membershipFound ? 'Linked' : 'None'}
              icon={BookOpen}
              description={
                payload.membershipFound
                  ? `Batch ${payload.batchId ?? '—'}`
                  : 'No active batch membership in Dev'
              }
            />
            <StatCard
              title="Published periods"
              value={`${entries.length}`}
              icon={Calendar}
              description="Slots for your batch"
            />
            <StatCard
              title="Client batch ignored"
              value={payload.clientBatchIdIgnored ? 'Yes' : 'N/A'}
              icon={Clock}
              description="Supplied batchId has no effect"
            />
          </div>

          {!payload.membershipFound && (
            <Card data-testid="student-timetable-no-membership">
              <CardHeader>
                <CardTitle>No batch membership</CardTitle>
                <CardDescription>
                  Your account is authenticated, but no active batch membership was found for this college.
                  Timetable slots cannot be shown until membership exists in Dev.
                </CardDescription>
              </CardHeader>
            </Card>
          )}

          {payload.membershipFound && entries.length === 0 && (
            <Card data-testid="student-timetable-empty">
              <CardHeader>
                <CardTitle>No published slots</CardTitle>
                <CardDescription>
                  Batch membership was resolved, but no published timetable entries are available yet (no data
                  in Dev).
                </CardDescription>
              </CardHeader>
            </Card>
          )}

          {DAYS.map((day) => {
            const dayEntries = entries.filter(
              (e) => String(e.dayOfWeek || '').toUpperCase() === day
            );
            if (dayEntries.length === 0) {
              return null;
            }
            return (
              <Card key={day} data-testid={`student-timetable-day-${day.toLowerCase()}`}>
                <CardHeader>
                  <CardTitle className="text-base">{day.charAt(0) + day.slice(1).toLowerCase()}</CardTitle>
                  <CardDescription>{dayEntries.length} period(s)</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {dayEntries.map((entry) => (
                    <div
                      key={entry.id || `${day}-${entry.period}-${entry.subjectId}`}
                      className="flex flex-wrap items-center gap-3 text-sm"
                      data-testid="student-timetable-entry"
                    >
                      <Badge variant="outline">P{entry.period ?? '—'}</Badge>
                      <span className="inline-flex items-center gap-1 text-[#0B1F3A]">
                        <Clock className="h-3.5 w-3.5" />
                        {entry.startTime || '—'}–{entry.endTime || '—'}
                      </span>
                      <span className="font-medium">{entry.subjectId || 'Subject'}</span>
                      {entry.roomId && (
                        <span className="inline-flex items-center gap-1 text-[#6B7688]">
                          <MapPin className="h-3.5 w-3.5" />
                          {entry.roomId}
                        </span>
                      )}
                    </div>
                  ))}
                </CardContent>
              </Card>
            );
          })}
        </>
      )}
    </div>
  );
}
