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
  if (!res.ok) throw new Error(data.detail || 'The record could not be saved.');
  return data;
}

export function CommunicationsManager(props: {
  colleges: { id: string; code: string; name: string }[];
  messages: { id: string; subject: string | null; channel: string; status: string }[];
  recipientId: string;
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Communications</h1>
        <p className="mt-1 max-w-3xl text-sm text-[#5C6B80]">Record an in-app campus notice and deliver it inside the app. It is not emailed, texted, or sent on WhatsApp.</p>
      </div>
      {message && <p className="text-sm text-[#067647]">{message}</p>}
      {error && <p className="text-sm text-[#B42318]">{error}</p>}
      <Card>
        <CardHeader><CardTitle>Campus notice</CardTitle><CardDescription>Draft in-app message with an audience label.</CardDescription></CardHeader>
        <CardContent className="space-y-3">
          <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={async (event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            setSaving(true); setError(null); setMessage(null);
            try {
              const data = await post('/api/college/communications', { collegeId: form.get('collegeId'), code: String(form.get('code')).toUpperCase(), subject: form.get('subject'), body: form.get('body') });
              setMessage(data.message || 'Saved.');
              router.refresh();
            } catch (err) {
              setError(err instanceof Error ? err.message : 'The notice could not be saved.');
            } finally { setSaving(false); }
          }}>
            <label className="text-xs font-semibold text-[#0B1F3A]">College<select name="collegeId" required defaultValue={props.colleges[0]?.id || ''} className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs">{props.colleges.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></label>
            <label className="text-xs font-semibold text-[#0B1F3A]">Code<Input name="code" placeholder="NOTICE-ODD" required className="mt-1" /></label>
            <label className="text-xs font-semibold text-[#0B1F3A]">Subject<Input name="subject" placeholder="Odd semester examination sitting" required className="mt-1" /></label>
            <label className="text-xs font-semibold text-[#0B1F3A]">Body<Input name="body" placeholder="CS301 sits on 16 Dec 2026 in HALL-A." required className="mt-1" /></label>
            <Button type="submit" variant="primary" isLoading={saving}>Save draft notice</Button>
          </form>
          {props.messages.length === 0 ? <p className="text-xs text-[#6B7688]">Nothing recorded yet.</p> : props.messages.map((item) => (
            <div key={item.id} className="flex items-center justify-between rounded-[8px] border border-[#E3E8EF] px-3 py-2">
              <div><p className="text-sm font-semibold text-[#0B1F3A]">{item.subject}</p><p className="text-xs text-[#6B7688]">{item.channel}</p></div>
              <div className="flex items-center gap-2">
                <Badge status={item.status} />
                {item.status === 'DRAFT' && props.recipientId && <Button type="button" variant="outline" isLoading={saving} onClick={async () => {
                  setSaving(true); setError(null); setMessage(null);
                  try {
                    const data = await post('/api/college/communications/deliver', { messageId: item.id, employeeId: props.recipientId });
                    setMessage(data.message || 'Delivered.');
                    router.refresh();
                  } catch (err) {
                    setError(err instanceof Error ? err.message : 'The notice could not be delivered.');
                  } finally { setSaving(false); }
                }}>Deliver in app</Button>}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
