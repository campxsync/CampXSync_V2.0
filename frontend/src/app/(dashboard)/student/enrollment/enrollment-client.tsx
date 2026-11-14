'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';

export type Enrolment = {
  id: string;
  status: string;
  totalCredits?: number;
  programId?: string;
  termId?: string;
  yearCode?: string;
  termCode?: string;
  termName?: string;
  applicationId?: string;
  studentId?: string;
  selections?: { courseId: string; sectionId: string }[];
};

export type HistoryRow = {
  status: string;
  action: string;
  actorRole?: string;
  at?: string;
};

export type SectionOption = {
  id: string;
  capacity: number;
  enrolledCount: number;
  seatsAvailable: number;
};

export type CourseOption = {
  id: string;
  code: string;
  credits: number;
  core: boolean;
  sections: SectionOption[];
};

function RegisteredCourses({
  enrolment,
  courses,
  onChanged,
  onError,
  onSuccess,
}: {
  enrolment: Enrolment;
  courses: CourseOption[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
  onSuccess: (message: string) => void;
}) {
  const [swapTargets, setSwapTargets] = useState<Record<string, string>>({});

  async function dropCourse(courseId: string) {
    const res = await fetch('/api/student/enrollment', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'drop', enrolmentId: enrolment.id, courseId }),
    });
    const text = await res.text();
    if (!res.ok) {
      onError(text);
      return;
    }
    onSuccess('Course removed.');
    await onChanged();
  }

  async function swap(courseId: string) {
    const sectionId = swapTargets[courseId];
    if (!sectionId) {
      onError('Choose another section first.');
      return;
    }
    const res = await fetch('/api/student/enrollment', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'swap',
        enrolmentId: enrolment.id,
        courseId,
        sectionId,
      }),
    });
    const text = await res.text();
    if (!res.ok) {
      onError(text);
      return;
    }
    onSuccess('Section updated.');
    await onChanged();
  }

  return (
    <section className="space-y-3 rounded border border-[#BBF7D0] bg-[#F0FDF4] p-4 text-sm text-[#14532D]">
      <p className="font-medium">You are registered for this term.</p>
      <p>
        Status <span className="font-semibold">{enrolment.status}</span>
        {typeof enrolment.totalCredits === 'number' ? ` · ${enrolment.totalCredits} credits` : ''}.
        You can drop an elective or move to another open section while registration is open.
      </p>
      {enrolment.selections && enrolment.selections.length > 0 ? (
        <ul className="space-y-2">
          {enrolment.selections.map((sel) => {
            const course = courses.find((c) => c.id === sel.courseId);
            return (
              <li
                key={`${sel.courseId}-${sel.sectionId}`}
                className="flex flex-wrap items-center justify-between gap-2 rounded border border-[#86EFAC] bg-white p-2"
              >
                <div>
                  <span className="font-medium">{course?.code || sel.courseId}</span>
                  <span className="ml-2 text-[#475569]">Section {sel.sectionId.slice(0, 8)}…</span>
                  {course?.core ? <span className="ml-2 text-xs text-[#DC2626]">core</span> : null}
                </div>
                <div className="flex items-center gap-2">
                  {!course?.core ? (
                    <button
                      type="button"
                      className="text-xs text-[#DC2626] underline"
                      onClick={() => void dropCourse(sel.courseId)}
                    >
                      Drop
                    </button>
                  ) : null}
                  {course && course.sections.length > 1 ? (
                    <div className="flex items-center gap-1 text-xs">
                      <select
                        aria-label={`Change section for ${course.code || sel.courseId}`}
                        className="rounded border p-1"
                        value={swapTargets[sel.courseId] || ''}
                        onChange={(e) =>
                          setSwapTargets((prev) => ({
                            ...prev,
                            [sel.courseId]: e.target.value,
                          }))
                        }
                      >
                        <option value="">Move section…</option>
                        {course.sections
                          .filter((sec) => sec.id !== sel.sectionId)
                          .map((sec) => (
                            <option key={sec.id} value={sec.id}>
                              Section {sec.id.slice(0, 8)}… ({sec.seatsAvailable} seats)
                            </option>
                          ))}
                      </select>
                      <button
                        type="button"
                        className="rounded bg-[#15803D] px-2 py-1 text-white"
                        onClick={() => void swap(sel.courseId)}
                      >
                        Move
                      </button>
                    </div>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="text-xs text-[#475569]">No courses selected in this enrolment record.</p>
      )}
    </section>
  );
}

export function EnrollmentClient({
  initialEnrolments,
  initialAlerts = [],
  initialCourses = [],
  initialHistory = [],
}: {
  initialEnrolments?: Enrolment[];
  initialAlerts?: { eventType: string }[];
  initialCourses?: CourseOption[];
  initialHistory?: HistoryRow[];
}) {
  const [enrolments, setEnrolments] = useState<Enrolment[]>(initialEnrolments || []);
  const [activeId, setActiveId] = useState<string>(initialEnrolments && initialEnrolments.length > 0 ? initialEnrolments[0].id : '');
  const [courses, setCourses] = useState<CourseOption[]>(initialCourses);
  const [selections, setSelections] = useState<Record<string, string>>(() => {
    const defaults: Record<string, string> = {};
    for (const course of initialCourses) {
      const open = course.sections.find((section) => section.seatsAvailable > 0) || course.sections[0];
      if (open) {
        defaults[course.id] = open.id;
      }
    }
    return defaults;
  });
  const [loading, setLoading] = useState(initialEnrolments === undefined);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [alerts, setAlerts] = useState<{ eventType: string }[]>(initialAlerts);
  const [history, setHistory] = useState<HistoryRow[]>(initialHistory);

  const active = useMemo(
    () => enrolments.find((item) => item.id === activeId) || enrolments[0] || null,
    [enrolments, activeId],
  );

  const loadMine = useCallback(async () => {
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const [res, alertsRes] = await Promise.all([
        fetch('/api/student/enrollment?view=mine', { cache: 'no-store' }),
        fetch('/api/student/enrollment?view=alerts', { cache: 'no-store' }),
      ]);
      const text = await res.text();
      if (!res.ok) {
        setError(text);
        setEnrolments([]);
        return;
      }
      const json = JSON.parse(text);
      const list: Enrolment[] = json.enrolments || [];
      if (alertsRes.ok) {
        const alertsJson = await alertsRes.json();
        setAlerts(alertsJson.alerts || []);
      }
      setEnrolments(list);
      if (list.length > 0) {
        setActiveId((current) => current || list[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load enrolments');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadCatalogue = useCallback(async (enrolmentId: string) => {
    setError('');
    const res = await fetch(
      `/api/student/enrollment?enrolmentId=${encodeURIComponent(enrolmentId)}&view=catalogue`,
      { cache: 'no-store' },
    );
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      setCourses([]);
      return;
    }
    const json = JSON.parse(text);
    const nextCourses: CourseOption[] = json.courses || [];
    setCourses(nextCourses);
    const defaults: Record<string, string> = {};
    for (const course of nextCourses) {
      const open = course.sections.find((section) => section.seatsAvailable > 0) || course.sections[0];
      if (open) {
        defaults[course.id] = open.id;
      }
    }
    setSelections(defaults);
  }, []);

  const loadHistory = useCallback(async (enrolmentId: string) => {
    const res = await fetch(
      `/api/student/enrollment?enrolmentId=${encodeURIComponent(enrolmentId)}&view=history`,
      { cache: 'no-store' },
    );
    if (!res.ok) {
      setHistory([]);
      return;
    }
    const json = await res.json();
    setHistory(json.history || []);
  }, []);

  useEffect(() => {
    if (initialEnrolments === undefined) {
      void loadMine();
    }
  }, [initialEnrolments, loadMine]);

  useEffect(() => {
    if (!active?.id) {
      setCourses([]);
      return;
    }
    // If activeId changed from initial, load catalogue/history for the newly selected enrolment
    if (initialEnrolments && active.id === initialEnrolments[0]?.id && initialCourses.length > 0) {
      return;
    }
    void loadCatalogue(active.id);
    void loadHistory(active.id);
  }, [active?.id, active?.status, initialEnrolments, initialCourses.length, loadCatalogue, loadHistory]);

  async function submitRegistration() {
    if (!active) {
      return;
    }
    const payloadSelections = Object.entries(selections).map(([courseId, sectionId]) => ({ courseId, sectionId }));
    if (payloadSelections.length === 0) {
      setError('No course sections are available to register.');
      return;
    }
    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const res = await fetch('/api/student/enrollment', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': `ui-register-${active.id}`,
        },
        body: JSON.stringify({
          enrolmentId: active.id,
          selections: payloadSelections,
        }),
      });
      const text = await res.text();
      if (!res.ok) {
        setError(text);
        return;
      }
      setSuccess('Registration submitted successfully.');
      await loadMine();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed');
    } finally {
      setSubmitting(false);
    }
  }

  async function joinWaitlist(enrolmentId: string, sectionId: string) {
    setError('');
    setSuccess('');
    const res = await fetch('/api/student/enrollment', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'waitlist', enrolmentId, sectionId }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setSuccess('You joined the waitlist.');
  }

  const canRegister = !!active && active.status !== 'ENROLLED' && active.status !== 'REJECTED' && active.status !== 'SUSPENDED';

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6" data-testid="student-enrollment-desk">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold text-[#0B1F3A]" data-testid="student-enrollment-heading">
          Course registration
        </h1>
        <p className="text-sm text-[#475569]">
          Choose an open enrolment, pick a section for each course, then submit your registration.
        </p>
      </div>

      {alerts.length > 0 ? (
        <ul className="space-y-1 rounded border border-[#BFDBFE] bg-[#EFF6FF] p-3 text-sm text-[#1E3A8A]">
          {alerts.map((alert, index) => (
            <li key={`${alert.eventType}-${index}`}>{alert.eventType.replaceAll('.', ' ')}</li>
          ))}
        </ul>
      ) : null}

      {loading ? <p className="text-sm text-[#475569]">Loading your enrolments…</p> : null}

      {!loading && enrolments.length === 0 ? (
        <div className="rounded border border-[#E3E8EF] bg-[#F8FAFC] p-4 text-sm text-[#0B1F3A]">
          <p className="font-medium">No enrolment is open for you yet.</p>
          <p className="mt-2 text-[#475569]">
            After admissions completes handover, the registrar opens your enrolment for the term.
            Once that happens, this page will list it here so you can select courses and sections.
          </p>
        </div>
      ) : null}

      {enrolments.length > 0 ? (
        <section className="space-y-2" data-testid="student-enrollment-list">
          <h2 className="text-sm font-semibold text-[#0B1F3A]">Your enrolments</h2>
          <ul className="space-y-2">
            {enrolments.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  data-testid={`student-enrollment-select-${item.id}`}
                  onClick={() => setActiveId(item.id)}
                  className={`w-full rounded border px-3 py-2 text-left text-sm ${
                    active?.id === item.id
                      ? 'border-[#1F6FEB] bg-[#EFF6FF] text-[#0B1F3A]'
                      : 'border-[#E3E8EF] bg-white text-[#0B1F3A]'
                  }`}
                >
                  <span className="font-medium">{item.status}</span>
                  <span className="ml-2 text-[#64748B]">{item.id.slice(0, 8)}…</span>
                  {item.yearCode || item.termCode ? (
                    <span className="ml-2 text-[#64748B]">
                      {item.yearCode} {item.termCode}
                    </span>
                  ) : null}
                  {typeof item.totalCredits === 'number' ? (
                    <span className="ml-2 text-[#64748B]">{item.totalCredits} credits</span>
                  ) : null}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {active ? (
        <section
          className="space-y-2 rounded border border-[#E3E8EF] bg-white p-4"
          data-testid="student-enrollment-details"
        >
          <h2 className="text-sm font-semibold text-[#0B1F3A]">Enrolment details</h2>
          <dl className="grid gap-2 text-sm text-[#0B1F3A] sm:grid-cols-2">
            <div>
              <dt className="text-xs text-[#64748B]">Status</dt>
              <dd data-testid="student-enrollment-detail-status">{active.status}</dd>
            </div>
            <div>
              <dt className="text-xs text-[#64748B]">Programme</dt>
              <dd data-testid="student-enrollment-detail-program">{active.programId || '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-[#64748B]">Academic year</dt>
              <dd data-testid="student-enrollment-detail-year">{active.yearCode || '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-[#64748B]">Term</dt>
              <dd data-testid="student-enrollment-detail-term">
                {active.termName || [active.termCode, active.termId?.slice(0, 8)].filter(Boolean).join(' · ') || '—'}
              </dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-xs text-[#64748B]">Enrolment id</dt>
              <dd data-testid="student-enrollment-detail-id" className="font-mono text-xs">
                {active.id}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      {history.length > 0 ? (
        <section className="space-y-2">
          <h2 className="text-sm font-semibold text-[#0B1F3A]">Enrolment history</h2>
          <ul className="space-y-1 rounded border border-[#E3E8EF] bg-white p-3 text-sm text-[#0B1F3A]">
            {history.map((row, index) => (
              <li key={`${row.at || 'at'}-${row.action}-${index}`}>
                <span className="font-medium">{row.status}</span>
                <span className="text-[#64748B]"> · {row.action}</span>
                {row.at ? <span className="text-[#64748B]"> · {row.at.replace('T', ' ').replace('Z', ' UTC')}</span> : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {active?.status === 'ENROLLED' ? (
        <RegisteredCourses
          enrolment={active}
          courses={courses}
          onChanged={loadMine}
          onError={setError}
          onSuccess={setSuccess}
        />
      ) : null}

      {canRegister && courses.length > 0 ? (
        <section className="space-y-4">
          <h2 className="text-sm font-semibold text-[#0B1F3A]">Select sections</h2>
          {courses.map((course) => (
            <div key={course.id} className="rounded border border-[#E3E8EF] p-3">
              <p className="text-sm font-medium text-[#0B1F3A]">
                {course.code} · {course.credits} credits{course.core ? ' · required' : ''}
              </p>
              <div className="mt-2 space-y-1">
                {course.sections.length === 0 ? (
                  <p className="text-xs text-[#991B1B]">No sections opened for this course yet.</p>
                ) : (
                  course.sections.map((section) => (
                    <label key={section.id} className="flex items-center gap-2 text-sm text-[#0B1F3A]">
                      <input
                        type="radio"
                        name={`course-${course.id}`}
                        checked={selections[course.id] === section.id}
                        disabled={section.seatsAvailable <= 0 && selections[course.id] !== section.id}
                        onChange={() =>
                          setSelections((prev) => ({
                            ...prev,
                            [course.id]: section.id,
                          }))
                        }
                      />
                      <span>
                        Section {section.id.slice(0, 8)}… · {section.seatsAvailable} seats left
                        {section.seatsAvailable <= 0 ? ' (full)' : ''}
                      </span>
                      {section.seatsAvailable <= 0 && active ? (
                        <button
                          type="button"
                          className="text-xs text-[#1F6FEB]"
                          onClick={() => void joinWaitlist(active.id, section.id)}
                        >
                          Join waitlist
                        </button>
                      ) : null}
                    </label>
                  ))
                )}
              </div>
            </div>
          ))}
          <button
            type="button"
            disabled={submitting}
            className="rounded bg-[#15803D] px-4 py-2 text-sm text-white disabled:opacity-60"
            onClick={() => void submitRegistration()}
          >
            {submitting ? 'Submitting…' : 'Submit course registration'}
          </button>
        </section>
      ) : null}

      {canRegister && !loading && courses.length === 0 ? (
        <div className="rounded border border-[#FDE68A] bg-[#FFFBEB] p-4 text-sm text-[#92400E]">
          Enrolment is open, but no courses/sections have been catalogued for this term yet.
          Ask the registrar to publish the registration catalogue.
        </div>
      ) : null}

      {error ? <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]">{error}</pre> : null}
      {success ? <p className="rounded bg-[#F0FDF4] p-3 text-sm text-[#166534]">{success}</p> : null}

      <Link href="/student" className="inline-block text-sm text-[#1F6FEB]">
        Back to dashboard
      </Link>
    </main>
  );
}
