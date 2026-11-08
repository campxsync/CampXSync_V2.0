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
  const [documentType, setDocumentType] = useState('BIRTH_CERTIFICATE');
  const [reasonCode, setReasonCode] = useState('IDENTITY_CONFIRMED');
  const [relationshipState, setRelationshipState] = useState('DISPUTED');
  const [accessPurpose, setAccessPurpose] = useState('FAMILY_SUPPORT');
  const [accessFrom, setAccessFrom] = useState('2026-11-01');
  const [accessUntil, setAccessUntil] = useState('2027-06-30');
  const [grantAcademics, setGrantAcademics] = useState(true);
  const [grantFees, setGrantFees] = useState(false);
  const [grantFeePayment, setGrantFeePayment] = useState(false);
  const [contactId, setContactId] = useState('');
  const [contactName, setContactName] = useState('');
  const [contactRelationship, setContactRelationship] = useState('Uncle');
  const [contactPhone, setContactPhone] = useState('');
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
    try {
      const saved = JSON.parse(text) as { id?: string };
      if (saved.id) {
        setGuardianId(saved.id);
      }
    } catch {
      setGuardianId(guardianId);
    }
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

  async function submitEvidence() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'submit-evidence', guardianId, documentType }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
  }

  async function saveContact(action: 'save-contact' | 'change-contact' | 'remove-contact') {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, studentId, contactId, contactName, contactRelationship, contactPhone }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
    try {
      const saved = JSON.parse(text) as { id?: string };
      if (saved.id) {
        setContactId(saved.id);
      }
    } catch {
      setContactId(contactId);
    }
  }

  async function viewAuthorized() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'view-authorized', guardianId }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
  }

  async function revokeAccess() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'revoke-access', guardianId }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
  }

  async function savePeriod(action: 'set-period' | 'check-access') {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, guardianId, validFrom: accessFrom, validTo: accessUntil }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
  }

  async function grantAccess() {
    setError('');
    setMessage('');
    const categories = [
      grantAcademics ? 'ACADEMICS' : '',
      grantFees ? 'FEES' : '',
      grantFeePayment ? 'FEE_PAYMENT' : '',
    ].filter((item) => item.length > 0);
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'grant-access', guardianId, categories, purpose: accessPurpose, validTo: accessUntil }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
  }

  async function markState() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'mark-state', guardianId, state: relationshipState }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
    await load();
  }

  async function deactivate() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'deactivate', guardianId }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
    await load();
  }

  async function reviewEvidence(decision: string) {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'review-evidence', guardianId, decision, reasonCode }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
  }

  async function loadHistory() {
    setError('');
    setMessage('');
    const res = await fetch('/api/college/guardians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'load-history', studentId }),
    });
    const text = await res.text();
    if (!res.ok) {
      setError(text);
      return;
    }
    setMessage(text);
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
        <label className="block text-sm">
          Evidence type
          <select className="mt-1 w-full rounded border px-2 py-1" value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
            <option value="BIRTH_CERTIFICATE">Birth certificate</option>
            <option value="AADHAAR">Aadhaar</option>
            <option value="PASSPORT">Passport</option>
            <option value="COURT_ORDER">Court order</option>
          </select>
        </label>
        <label className="block text-sm">
          Review reason
          <select className="mt-1 w-full rounded border px-2 py-1" value={reasonCode} onChange={(e) => setReasonCode(e.target.value)}>
            <option value="IDENTITY_CONFIRMED">Identity confirmed</option>
            <option value="DOCUMENT_MISMATCH">Document mismatch</option>
            <option value="DOCUMENT_UNREADABLE">Document unreadable</option>
            <option value="RELATIONSHIP_NOT_PROVEN">Relationship not proven</option>
          </select>
        </label>
        <label className="block text-sm">
          Relationship state
          <select className="mt-1 w-full rounded border px-2 py-1" value={relationshipState} onChange={(e) => setRelationshipState(e.target.value)}>
            <option value="DISPUTED">Disputed</option>
            <option value="SUSPENDED">Suspended</option>
            <option value="EXPIRED">Expired</option>
          </select>
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={primary} onChange={(e) => setPrimary(e.target.checked)} />
          Primary guardian
        </label>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="rounded bg-[#0B1F3A] px-3 py-2 text-sm text-white" onClick={() => void addGuardian()}>
            Add guardian
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void updateRelationship()}>
            Update relationship
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void submitEvidence()}>
            Submit evidence
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void reviewEvidence('VERIFY')}>
            Verify
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void reviewEvidence('REJECT')}>
            Reject
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void deactivate()}>
            Deactivate
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void markState()}>
            Mark state
          </button>
        </div>
        <label className="block text-sm">
          Access purpose
          <select className="mt-1 w-full rounded border px-2 py-1" value={accessPurpose} onChange={(e) => setAccessPurpose(e.target.value)}>
            <option value="FAMILY_SUPPORT">Family support</option>
            <option value="FEE_ASSISTANCE">Fee assistance</option>
            <option value="ACADEMIC_PROGRESS">Academic progress</option>
          </select>
        </label>
        <label className="block text-sm">
          Access from
          <input className="mt-1 w-full rounded border px-2 py-1" value={accessFrom} onChange={(e) => setAccessFrom(e.target.value)} />
        </label>
        <label className="block text-sm">
          Access until
          <input className="mt-1 w-full rounded border px-2 py-1" value={accessUntil} onChange={(e) => setAccessUntil(e.target.value)} />
        </label>
        <div className="flex flex-wrap gap-4 text-sm">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={grantAcademics} onChange={(e) => setGrantAcademics(e.target.checked)} />
            Academics
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={grantFees} onChange={(e) => setGrantFees(e.target.checked)} />
            Fees
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={grantFeePayment} onChange={(e) => setGrantFeePayment(e.target.checked)} />
            Fee payment
          </label>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void grantAccess()}>
            Grant access
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void savePeriod('set-period')}>
            Save period
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void savePeriod('check-access')}>
            Check access
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void revokeAccess()}>
            Revoke access
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void viewAuthorized()}>
            View authorized information
          </button>
        </div>
        <label className="block text-sm">
          Contact id
          <input className="mt-1 w-full rounded border px-2 py-1" value={contactId} onChange={(e) => setContactId(e.target.value)} />
        </label>
        <label className="block text-sm">
          Contact name
          <input className="mt-1 w-full rounded border px-2 py-1" value={contactName} onChange={(e) => setContactName(e.target.value)} />
        </label>
        <label className="block text-sm">
          Contact relation
          <input className="mt-1 w-full rounded border px-2 py-1" value={contactRelationship} onChange={(e) => setContactRelationship(e.target.value)} />
        </label>
        <label className="block text-sm">
          Contact phone
          <input className="mt-1 w-full rounded border px-2 py-1" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} />
        </label>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void saveContact('save-contact')}>
            Save contact
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void saveContact('change-contact')}>
            Change contact
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void saveContact('remove-contact')}>
            Remove contact
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void load()}>
            Load guardians
          </button>
          <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => void loadHistory()}>
            Load history
          </button>
        </div>
      </section>
      {message ? <pre className="overflow-auto rounded bg-[#F8FAFC] p-3 text-xs">{message}</pre> : null}
      {list ? <pre className="overflow-auto rounded bg-[#F8FAFC] p-3 text-xs">{list}</pre> : null}
      {error ? <pre className="overflow-auto rounded bg-[#FEF2F2] p-3 text-xs text-[#991B1B]">{error}</pre> : null}
    </main>
  );
}
