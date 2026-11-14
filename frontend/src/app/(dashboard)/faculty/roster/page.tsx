'use client';

import { useEffect, useState } from 'react';

type SectionRoster = {
  sectionId: string;
  courseCode: string;
  capacity: number;
  enrolledCount: number;
  studentIds: string[];
};

export default function FacultyRosterPage() {
  const [sections, setSections] = useState<SectionRoster[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    void (async () => {
      const res = await fetch('/api/faculty/enrollment', { cache: 'no-store' });
      const text = await res.text();
      if (!res.ok) {
        setError(text);
        return;
      }
      const json = JSON.parse(text);
      setSections(json.sections || []);
    })();
  }, []);

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-6" data-testid="faculty-roster-desk">
      <div>
        <h1 className="text-xl font-semibold text-[#0B1F3A]" data-testid="faculty-roster-heading">
          My section rosters
        </h1>
        <p className="text-sm text-[#475569]">
          Students enrolled in the sections assigned to you. Other faculty sections stay hidden.
        </p>
      </div>
      {sections.length === 0 && !error ? (
        <p className="text-sm text-[#475569]" data-testid="faculty-roster-empty">
          No sections are assigned to you yet.
        </p>
      ) : null}
      <ul className="space-y-3" data-testid="faculty-roster-list">
        {sections.map((section) => (
          <li
            key={section.sectionId}
            className="rounded border border-[#E3E8EF] bg-white p-4"
            data-testid={`faculty-roster-section-${section.sectionId}`}
          >
            <p className="text-sm font-medium text-[#0B1F3A]">
              {section.courseCode} · {section.enrolledCount} of {section.capacity} seats filled
            </p>
            {section.studentIds.length === 0 ? (
              <p className="mt-2 text-sm text-[#475569]">No students are enrolled in this section.</p>
            ) : (
              <ul className="mt-2 space-y-1 text-sm text-[#0B1F3A]">
                {section.studentIds.map((studentId) => (
                  <li key={studentId} data-testid={`faculty-roster-student-${studentId}`}>
                    Student {studentId.slice(0, 8)}…
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
      {error ? (
        <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]" data-testid="faculty-roster-error">
          {error}
        </pre>
      ) : null}
    </main>
  );
}
