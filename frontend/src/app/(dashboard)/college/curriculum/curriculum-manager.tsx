'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AlertCircle, BookOpen, CheckCircle2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';

export interface ProgramOption {
  id: string;
  name: string;
  code: string;
}

export interface SubjectOption {
  id: string;
  code: string;
  name: string;
}

export interface PlacedSubject {
  termNo: number;
  category: string;
  credits: number | null;
  subjectCode: string;
  subjectName: string;
}

export interface CurriculumRecord {
  id: string;
  code: string;
  name: string;
  regulationYear: number | null;
  status: string;
  programName: string;
  programCode: string;
  versionId: string | null;
  versionNo: number | null;
  versionStatus: string | null;
  effectiveFrom: string | null;
  totalCredits: number | null;
  subjects: PlacedSubject[];
}

interface Props {
  programs: ProgramOption[];
  subjects: SubjectOption[];
  curricula: CurriculumRecord[];
}

async function readDetail(res: Response): Promise<string> {
  const data = await res.json().catch(() => ({}));
  return data.detail || data.message || 'The request could not be completed.';
}

export function CurriculumManager({ programs, subjects, curricula }: Props) {
  const router = useRouter();
  const [message, setMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [programId, setProgramId] = useState(programs[0]?.id || '');
  const [regulationYear, setRegulationYear] = useState(String(new Date().getFullYear()));
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 10));
  const [subjectId, setSubjectId] = useState(subjects[0]?.id || '');
  const [termNo, setTermNo] = useState('5');
  const [category, setCategory] = useState('CORE');
  const [credits, setCredits] = useState('4');
  const [targetVersionId, setTargetVersionId] = useState(
    curricula.find((item) => item.versionStatus === 'DRAFT')?.versionId || curricula[0]?.versionId || ''
  );

  useEffect(() => {
    const openDraft = curricula.find((item) => item.versionStatus === 'DRAFT' && item.versionId);
    if (openDraft?.versionId) {
      setTargetVersionId(openDraft.versionId);
    }
  }, [curricula]);

  const draft = curricula.find((item) => item.versionId === targetVersionId);

  const createCurriculum = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/curricula', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          programId,
          code: code.trim().toUpperCase(),
          name: name.trim(),
          regulationYear: Number(regulationYear),
          effectiveFrom,
        }),
      });
      const detail = await readDetail(res);
      if (!res.ok) {
        setMessage({ type: 'error', text: detail });
        return;
      }
      setCode('');
      setName('');
      setMessage({ type: 'success', text: detail });
      router.refresh();
    } finally {
      setSaving(false);
    }
  };

  const addSubject = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!targetVersionId) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/curricula/subjects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          versionId: targetVersionId,
          subjectId,
          termNo: Number(termNo),
          category,
          credits: Number(credits),
        }),
      });
      const detail = await readDetail(res);
      if (!res.ok) {
        setMessage({ type: 'error', text: detail });
        return;
      }
      setMessage({ type: 'success', text: 'Subject placed on the draft.' });
      router.refresh();
    } finally {
      setSaving(false);
    }
  };

  const publish = async () => {
    if (!targetVersionId) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/curricula/publish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ versionId: targetVersionId }),
      });
      const detail = await readDetail(res);
      setMessage({ type: res.ok ? 'success' : 'error', text: detail });
      if (res.ok) router.refresh();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      {message && (
        <div
          className={`flex items-center gap-2 rounded-[8px] border p-3 text-xs ${
            message.type === 'error'
              ? 'border-[#B91C1C] bg-[#FDF3F3] text-[#B91C1C]'
              : 'border-[#0D6535] bg-[#DFF6DD] text-[#0D6535]'
          }`}
        >
          {message.type === 'error' ? <AlertCircle className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}
          <span>{message.text}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Open a draft</CardTitle>
          <CardDescription>
            A draft belongs to one degree program. Subjects and credits are added before it is published.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={createCurriculum} className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Program
              <select
                className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                value={programId}
                onChange={(event) => setProgramId(event.target.value)}
                required
              >
                {programs.map((program) => (
                  <option key={program.id} value={program.id}>
                    {program.name} ({program.code})
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Curriculum code
              <Input
                value={code}
                onChange={(event) => setCode(event.target.value.toUpperCase())}
                placeholder="BTECH-CSE-2026"
                required
                className="mt-1 font-mono uppercase"
              />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Name
              <Input
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="B.Tech CSE regulation"
                required
                className="mt-1"
              />
            </label>
            <div className="grid grid-cols-2 gap-4">
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Regulation year
                <Input
                  type="number"
                  value={regulationYear}
                  onChange={(event) => setRegulationYear(event.target.value)}
                  required
                  className="mt-1"
                />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Effective from
                <Input
                  type="date"
                  value={effectiveFrom}
                  onChange={(event) => setEffectiveFrom(event.target.value)}
                  required
                  className="mt-1"
                />
              </label>
            </div>
            <div className="lg:col-span-2 flex justify-end">
              <Button type="submit" variant="primary" isLoading={saving} disabled={programs.length === 0}>
                Create draft
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {curricula.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title="No curriculum published"
          description="Create a draft, place the semester subjects, then publish the version."
        />
      ) : (
        curricula.map((item) => (
          <Card key={item.id}>
            <CardHeader>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <CardTitle>
                    {item.name} <span className="font-mono text-sm text-[#6B7688]">({item.code})</span>
                  </CardTitle>
                  <CardDescription>
                    {item.programName} ({item.programCode})
                    {item.regulationYear ? ` · Regulation ${item.regulationYear}` : ''}
                    {item.versionNo ? ` · Version ${item.versionNo}` : ''}
                  </CardDescription>
                </div>
                <div className="flex items-center gap-2">
                  <Badge status={item.versionStatus || item.status} />
                  {item.totalCredits !== null && (
                    <span className="text-xs font-semibold text-[#0B1F3A]">{item.totalCredits} credits</span>
                  )}
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {item.subjects.length === 0 ? (
                <p className="text-xs text-[#6B7688]">No subjects placed on this version yet.</p>
              ) : (
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                      <th className="pb-2 font-semibold">Semester</th>
                      <th className="pb-2 font-semibold">Subject</th>
                      <th className="pb-2 font-semibold">Category</th>
                      <th className="pb-2 text-right font-semibold">Credits</th>
                    </tr>
                  </thead>
                  <tbody>
                    {item.subjects.map((subject) => (
                      <tr key={`${item.id}-${subject.subjectCode}`} className="border-b border-[#E3E8EF]">
                        <td className="py-2 font-mono">{subject.termNo}</td>
                        <td className="py-2">
                          <span className="font-semibold text-[#0B1F3A]">{subject.subjectName}</span>
                          <span className="ml-2 font-mono text-[#6B7688]">{subject.subjectCode}</span>
                        </td>
                        <td className="py-2">{subject.category.replaceAll('_', ' ')}</td>
                        <td className="py-2 text-right font-mono">{subject.credits ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </CardContent>
          </Card>
        ))
      )}

      {draft?.versionStatus === 'DRAFT' && (
        <Card>
          <CardHeader>
            <CardTitle>Place a subject on {draft.code}</CardTitle>
            <CardDescription>
              Version {draft.versionNo} is still a draft. Publishing locks the subject list and credit total.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={addSubject} className="grid grid-cols-1 gap-4 lg:grid-cols-4">
              <label className="text-xs font-semibold text-[#0B1F3A] lg:col-span-2">
                Subject
                <select
                  className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                  value={subjectId}
                  onChange={(event) => setSubjectId(event.target.value)}
                  required
                >
                  {subjects.map((subject) => (
                    <option key={subject.id} value={subject.id}>
                      {subject.code} — {subject.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Semester
                <Input type="number" min={1} max={16} value={termNo} onChange={(event) => setTermNo(event.target.value)} required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Credits
                <Input type="number" min={0} step="0.5" value={credits} onChange={(event) => setCredits(event.target.value)} required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A] lg:col-span-2">
                Category
                <select
                  className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs"
                  value={category}
                  onChange={(event) => setCategory(event.target.value)}
                >
                  <option value="CORE">Core</option>
                  <option value="ELECTIVE">Elective</option>
                  <option value="OPEN_ELECTIVE">Open elective</option>
                  <option value="PROJECT">Project</option>
                  <option value="MANDATORY_NON_CREDIT">Mandatory non-credit</option>
                </select>
              </label>
              <div className="flex items-end justify-end gap-2 lg:col-span-2">
                <Button type="button" variant="outline" onClick={publish} isLoading={saving} disabled={draft.subjects.length === 0}>
                  Publish version
                </Button>
                <Button type="submit" variant="primary" isLoading={saving}>
                  Add subject
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
