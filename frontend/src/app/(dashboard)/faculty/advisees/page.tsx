'use client';

import { useEffect, useState } from 'react';

type Advisee = { studentId: string; status: string; effectiveFrom: string };

export default function FacultyAdviseesPage() {
  const [advisees, setAdvisees] = useState<Advisee[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    void (async () => {
      const res = await fetch('/api/faculty/lifecycle', { cache: 'no-store' });
      const text = await res.text();
      if (!res.ok) {
        setError(text);
        return;
      }
      const json = JSON.parse(text);
      setAdvisees(json.advisees || []);
    })();
  }, []);

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="text-xl font-semibold text-[#0B1F3A]">My advisees</h1>
      <p className="text-sm text-[#475569]">Limited lifecycle status for students the registrar assigned to you.</p>
      {advisees.length === 0 ? <p className="text-sm text-[#475569]">No advisees are assigned yet.</p> : null}
      <ul className="space-y-2">
        {advisees.map((advisee) => (
          <li key={advisee.studentId} className="rounded border p-3 text-sm">
            <p className="font-medium">{advisee.status}</p>
            <p className="text-[#475569]">{advisee.studentId}</p>
            <p className="text-xs text-[#64748B]">Since {advisee.effectiveFrom}</p>
          </li>
        ))}
      </ul>
      {error ? <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]">{error}</pre> : null}
    </main>
  );
}
