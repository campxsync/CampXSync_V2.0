'use client';

import { useState } from 'react';

/**
 * College desk for STM-05-US-001. A registrar adds a parent or guardian for a student record.
 */
export default function CollegeGuardiansPage() {
  const [studentId, setStudentId] = useState('');
  const [guardianId, setGuardianId] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [relationship, setRelationship] = useState('Mother');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [primary, setPrimary] = useState(true);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [list, setList] = useState('');

  async function addGuardian() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ studentId, firstName, lastName, relationship, email, phone, primary }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
    await load();
  }

  async function updateRelationship() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ guardianId, relationship, primary }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
    await load();
  }

  async function load() {
    if (!studentId) {
      return;
    }
    const res = await fetch(`/api/college/guardians?studentId=${encodeURIComponent(studentId)}`, { cache: 'no-store' });
    setList(await res.text());
  }

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold text-[#0B1F3A]">Guardians</h1>
        <p className="text-sm text-[#475569]">Add a parent or guardian for a student. Students cannot create this link themselves.</p>
      </div>
      <section className="space-y-2 rounded border p-4">
        <label className="block text-sm">
          Student record id
          <input className="mt-1 w-full rounded border px-2 py-1" value={studentId} onChange={(e) => setStudentId(e.target.value)} />
        </label>
        <label className="block text-sm">
          Guardian id
          <input className="mt-1 w-full rounded border px-2 py-1" value={guardianId} onChange={(e) => setGuardianId(e.target.value)} />
        </label>
        <label className="block text-sm">
          First name
          <input className="mt-1 w-full rounded border px-2 py-1" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
        </label>
        <label className="block text-sm">
          Last name
          <input className="mt-1 w-full rounded border px-2 py-1" value={lastName} onChange={(e) => setLastName(e.target.value)} />
        </label>
        <label className="block text-sm">
          Relationship
          <input className="mt-1 w-full rounded border px-2 py-1" value={relationship} onChange={(e) => setRelationship(e.target.value)} />
        </label>
        <label className="block text-sm">
          Email
          <input className="mt-1 w-full rounded border px-2 py-1" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="block text-sm">
          Phone
          <input className="mt-1 w-full rounded border px-2 py-1" value={phone} onChange={(e) => setPhone(e.target.value)} />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={primary} onChange={(e) => setPrimary(e.target.checked)} />
          Primary guardian
        </label>
        <div className="flex gap-2">
          <button type="button" className="rounded bg-[#0B1F3A] px-3 py-2 text-sm text-white" onClick={() => void addGuardian()}>
            Add guardian
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void updateRelationship()}>
            Update relationship
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void load()}>
            Load guardians
          </button>
        </div>
      </section>
      {message ? <pre className="overflow-auto rounded bg-[#F8FAFC] p-3 text-xs">{message}</pre> : null}
      {list ? <pre className="overflow-auto rounded bg-[#F8FAFC] p-3 text-xs">{list}</pre> : null}
      {error ? <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]">{error}</pre> : null}
    </main>
  );
}
