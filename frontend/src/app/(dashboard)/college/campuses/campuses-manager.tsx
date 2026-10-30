'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

async function post(url: string, body: unknown) {
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || 'The campus could not be saved.');
  return data;
}

export function CampusesManager(props: {
  colleges: { id: string; code: string; name: string; affiliating_university: string | null }[];
  campuses: { id: string; code: string; name: string; status: string; is_primary: boolean; college_code: string }[];
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const college = props.colleges[0];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Campuses</h1>
        <p className="mt-1 max-w-3xl text-sm text-[#5C6B80]">
          Record a campus under the existing college. The affiliating university is a name on that college.
          {college?.affiliating_university ? ` Current university: ${college.affiliating_university}.` : ' No affiliating university is set yet.'}
          {' '}A second institute and a load test are not part of this record.
        </p>
      </div>
      {message && <p className="text-sm text-[#067647]">{message}</p>}
      {error && <p className="text-sm text-[#B42318]">{error}</p>}
      <Card>
        <CardHeader>
          <CardTitle>Campus</CardTitle>
          <CardDescription>The first campus for a college is marked primary.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={async (event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            setSaving(true); setError(null); setMessage(null);
            try {
              const data = await post('/api/college/campuses', {
                collegeId: form.get('collegeId'),
                code: String(form.get('code')).toUpperCase(),
                name: form.get('name'),
                university: form.get('university'),
              });
              setMessage(data.message || 'Saved.');
              router.refresh();
            } catch (err) {
              setError(err instanceof Error ? err.message : 'The campus could not be saved.');
            } finally { setSaving(false); }
          }}>
            <label className="text-xs font-semibold text-[#0B1F3A]">College
              <select name="collegeId" required defaultValue={college?.id || ''} className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs">
                {props.colleges.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">Code<Input name="code" placeholder="DIT-MAIN" required className="mt-1" /></label>
            <label className="text-xs font-semibold text-[#0B1F3A]">Name<Input name="name" placeholder="DIT main campus" required className="mt-1" /></label>
            <label className="text-xs font-semibold text-[#0B1F3A]">Affiliating university<Input name="university" placeholder="Visvesvaraya Technological University" required className="mt-1" defaultValue={college?.affiliating_university || ''} /></label>
            <Button type="submit" variant="primary" isLoading={saving}>Save campus</Button>
          </form>
          {props.campuses.length === 0 ? <p className="text-xs text-[#6B7688]">Nothing recorded yet.</p> : props.campuses.map((item) => (
            <div key={item.id} className="flex items-center justify-between rounded-[8px] border border-[#E3E8EF] px-3 py-2">
              <div>
                <p className="text-sm font-semibold text-[#0B1F3A]">{item.code} · {item.name}</p>
                <p className="text-xs text-[#6B7688]">{item.college_code}{item.is_primary ? ' · primary' : ''}</p>
              </div>
              <Badge status={item.status} />
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
