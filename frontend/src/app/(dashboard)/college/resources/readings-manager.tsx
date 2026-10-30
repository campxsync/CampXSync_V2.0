'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { AlertCircle, CheckCircle2, Library } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';

export interface CollegeOption {
  id: string;
  name: string;
  code: string;
}

export interface SubjectOption {
  id: string;
  code: string;
  name: string;
}

export interface ReadingRecord {
  id: string;
  title: string;
  resourceType: string;
  externalUrl: string | null;
  author: string | null;
  status: string;
  subjects: string[];
}

interface Props {
  colleges: CollegeOption[];
  subjects: SubjectOption[];
  readings: ReadingRecord[];
}

export function ReadingsManager({ colleges, subjects, readings }: Props) {
  const router = useRouter();
  const [message, setMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [collegeId, setCollegeId] = useState(colleges[0]?.id || '');
  const [subjectId, setSubjectId] = useState(subjects[0]?.id || '');
  const [title, setTitle] = useState('');
  const [resourceType, setResourceType] = useState('BOOK');
  const [externalUrl, setExternalUrl] = useState('');
  const [author, setAuthor] = useState('');

  const createReading = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/resources', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          collegeId,
          subjectId,
          title: title.trim(),
          resourceType,
          externalUrl: externalUrl.trim(),
          author: author.trim() || undefined,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        setMessage({ type: 'error', text: data.detail || 'The reading could not be recorded.' });
        return;
      }
      setTitle('');
      setExternalUrl('');
      setAuthor('');
      setMessage({ type: 'success', text: data.message || 'Reading recorded.' });
      router.refresh();
    } finally {
      setSaving(false);
    }
  };

  const publish = async (resourceId: string) => {
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/resources/publish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ resourceId }),
      });
      const data = await res.json().catch(() => ({}));
      setMessage({ type: res.ok ? 'success' : 'error', text: data.detail || data.message || 'The reading could not be published.' });
      if (res.ok) router.refresh();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      {message && (
        <div className={`flex items-center gap-2 rounded-[8px] border p-3 text-xs ${message.type === 'error' ? 'border-[#B91C1C] bg-[#FDF3F3] text-[#B91C1C]' : 'border-[#0D6535] bg-[#DFF6DD] text-[#0D6535]'}`}>
          {message.type === 'error' ? <AlertCircle className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}
          <span>{message.text}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Record a reading</CardTitle>
          <CardDescription>The reading is stored as a link bound to one subject. A file is not uploaded from this screen.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={createReading} className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <label className="text-xs font-semibold text-[#0B1F3A]">
              College
              <select className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs" value={collegeId} onChange={(event) => setCollegeId(event.target.value)} required>
                {colleges.map((college) => (
                  <option key={college.id} value={college.id}>{college.name} ({college.code})</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Subject
              <select className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs" value={subjectId} onChange={(event) => setSubjectId(event.target.value)} required>
                {subjects.map((subject) => (
                  <option key={subject.id} value={subject.id}>{subject.code} — {subject.name}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Title
              <Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Operating Systems: Three Easy Pieces" required className="mt-1" />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Type
              <select className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs" value={resourceType} onChange={(event) => setResourceType(event.target.value)}>
                <option value="BOOK">Book</option>
                <option value="SLIDES">Slides</option>
                <option value="PAPER">Paper</option>
                <option value="VIDEO">Video</option>
                <option value="LINK">Link</option>
                <option value="DATASET">Dataset</option>
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A] lg:col-span-2">
              Address
              <Input value={externalUrl} onChange={(event) => setExternalUrl(event.target.value)} placeholder="https://" required className="mt-1" />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Author
              <Input value={author} onChange={(event) => setAuthor(event.target.value)} className="mt-1" />
            </label>
            <div className="flex items-end justify-end">
              <Button type="submit" variant="primary" isLoading={saving} disabled={subjects.length === 0}>Record draft</Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {readings.length === 0 ? (
        <EmptyState icon={Library} title="No readings yet" description="Record a link for a subject, then publish it." />
      ) : (
        readings.map((reading) => (
          <Card key={reading.id}>
            <CardHeader>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <CardTitle>{reading.title}</CardTitle>
                  <CardDescription>
                    {reading.resourceType}
                    {reading.author ? ` · ${reading.author}` : ''}
                    {reading.subjects.length > 0 ? ` · ${reading.subjects.join(', ')}` : ''}
                  </CardDescription>
                </div>
                <div className="flex items-center gap-2">
                  <Badge status={reading.status} />
                  {reading.status === 'DRAFT' && (
                    <Button type="button" variant="outline" isLoading={saving} onClick={() => publish(reading.id)}>Publish reading</Button>
                  )}
                </div>
              </div>
            </CardHeader>
            {reading.externalUrl && (
              <CardContent>
                <a href={reading.externalUrl} className="break-all text-xs font-semibold text-[#1F6FEB]" target="_blank" rel="noreferrer">{reading.externalUrl}</a>
              </CardContent>
            )}
          </Card>
        ))
      )}
    </div>
  );
}
