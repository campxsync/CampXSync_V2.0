'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AlertCircle, CheckCircle2, ListChecks } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';

export interface SubjectOption {
  id: string;
  code: string;
  name: string;
}

export interface VersionOption {
  id: string;
  subjectId: string;
  label: string;
}

export interface AssessmentScheme {
  id: string;
  code: string;
  name: string;
  status: string;
  totalMarks: number;
  passMarks: number | null;
  subjectCode: string | null;
  subjectName: string | null;
  components: {
    code: string;
    name: string;
    componentType: string;
    maxMarks: number;
    weightagePct: number | null;
    isExternal: boolean;
  }[];
}

interface Props {
  subjects: SubjectOption[];
  versions: VersionOption[];
  schemes: AssessmentScheme[];
}

export function AssessmentManager({ subjects, versions, schemes }: Props) {
  const router = useRouter();
  const [message, setMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const [selectedId, setSelectedId] = useState(schemes.find((item) => item.status === 'DRAFT')?.id || schemes[0]?.id || '');
  const [subjectId, setSubjectId] = useState(subjects[0]?.id || '');
  const [versionId, setVersionId] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [totalMarks, setTotalMarks] = useState('100');
  const [passMarks, setPassMarks] = useState('40');
  const [componentCode, setComponentCode] = useState('');
  const [componentName, setComponentName] = useState('');
  const [componentType, setComponentType] = useState('INTERNAL_TEST');
  const [maxMarks, setMaxMarks] = useState('40');
  const [weightagePct, setWeightagePct] = useState('40');

  const subjectVersions = versions.filter((item) => item.subjectId === subjectId);

  useEffect(() => {
    if (!selectedId && schemes[0]) setSelectedId(schemes[0].id);
  }, [schemes, selectedId]);

  useEffect(() => {
    setVersionId(subjectVersions[0]?.id || '');
  }, [subjectId, versions]);

  const selected = schemes.find((item) => item.id === selectedId) || null;

  const createScheme = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/assessments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subjectId,
          curriculumVersionId: versionId || undefined,
          code: code.trim().toUpperCase(),
          name: name.trim(),
          totalMarks: Number(totalMarks),
          passMarks: Number(passMarks),
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        setMessage({ type: 'error', text: data.detail || 'The scheme could not be opened.' });
        return;
      }
      if (data.structure?.id) setSelectedId(data.structure.id);
      setCode('');
      setName('');
      setMessage({ type: 'success', text: data.message || 'Draft scheme opened.' });
      router.refresh();
    } finally {
      setSaving(false);
    }
  };

  const addComponent = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selected) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/assessments/components', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          structureId: selected.id,
          code: componentCode.trim().toUpperCase(),
          name: componentName.trim(),
          componentType,
          maxMarks: Number(maxMarks),
          weightagePct: weightagePct === '' ? undefined : Number(weightagePct),
        }),
      });
      const data = await res.json().catch(() => ({}));
      setMessage({ type: res.ok ? 'success' : 'error', text: data.detail || data.message || 'The component could not be added.' });
      if (res.ok) {
        setComponentCode('');
        setComponentName('');
        router.refresh();
      }
    } finally {
      setSaving(false);
    }
  };

  const activate = async () => {
    if (!selected) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await fetch('/api/college/assessments/activate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ structureId: selected.id }),
      });
      const data = await res.json().catch(() => ({}));
      setMessage({ type: res.ok ? 'success' : 'error', text: data.detail || data.message || 'The scheme could not be activated.' });
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
          <CardTitle>Open a draft scheme</CardTitle>
          <CardDescription>The scheme belongs to one subject. Component marks must add up to the total before it can be activated.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={createScheme} className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Subject
              <select className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs" value={subjectId} onChange={(event) => setSubjectId(event.target.value)} required>
                {subjects.map((subject) => (
                  <option key={subject.id} value={subject.id}>{subject.code} — {subject.name}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Curriculum version
              <select className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs" value={versionId} onChange={(event) => setVersionId(event.target.value)}>
                <option value="">Subject only</option>
                {subjectVersions.map((version) => (
                  <option key={`${version.id}-${version.subjectId}`} value={version.id}>{version.label}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Code
              <Input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="CS301-ASSESS" required className="mt-1 font-mono uppercase" />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Name
              <Input value={name} onChange={(event) => setName(event.target.value)} placeholder="CS301 assessment" required className="mt-1" />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Total marks
              <Input type="number" min={1} value={totalMarks} onChange={(event) => setTotalMarks(event.target.value)} required className="mt-1" />
            </label>
            <label className="text-xs font-semibold text-[#0B1F3A]">
              Pass marks
              <Input type="number" min={0} value={passMarks} onChange={(event) => setPassMarks(event.target.value)} required className="mt-1" />
            </label>
            <div className="flex justify-end lg:col-span-2">
              <Button type="submit" variant="primary" isLoading={saving} disabled={subjects.length === 0}>Create draft</Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {schemes.length === 0 ? (
        <EmptyState icon={ListChecks} title="No assessment scheme" description="Open a draft, add the components, then activate it." />
      ) : (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <CardTitle>
                  <select className="bg-transparent text-base font-semibold text-[#0B1F3A]" value={selectedId} onChange={(event) => setSelectedId(event.target.value)}>
                    {schemes.map((scheme) => (
                      <option key={scheme.id} value={scheme.id}>{scheme.name} ({scheme.code})</option>
                    ))}
                  </select>
                </CardTitle>
                <CardDescription>
                  {selected?.subjectCode} {selected?.subjectName} · {selected?.totalMarks} marks
                  {selected?.passMarks != null ? ` · pass ${selected.passMarks}` : ''}
                </CardDescription>
              </div>
              {selected && <Badge status={selected.status} />}
            </div>
          </CardHeader>
          <CardContent>
            {selected && selected.components.length > 0 ? (
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-2 font-semibold">Component</th>
                    <th className="pb-2 font-semibold">Type</th>
                    <th className="pb-2 text-right font-semibold">Marks</th>
                    <th className="pb-2 text-right font-semibold">Weight</th>
                  </tr>
                </thead>
                <tbody>
                  {selected.components.map((component) => (
                    <tr key={component.code} className="border-b border-[#E3E8EF]">
                      <td className="py-2">
                        <span className="font-semibold text-[#0B1F3A]">{component.name}</span>
                        <span className="ml-2 font-mono text-[#6B7688]">{component.code}</span>
                      </td>
                      <td className="py-2">{component.componentType.replaceAll('_', ' ')}{component.isExternal ? ' · external' : ''}</td>
                      <td className="py-2 text-right font-mono">{component.maxMarks}</td>
                      <td className="py-2 text-right font-mono">{component.weightagePct ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p className="text-xs text-[#6B7688]">No components on this scheme yet.</p>
            )}
          </CardContent>
        </Card>
      )}

      {selected?.status === 'DRAFT' && (
        <Card>
          <CardHeader>
            <CardTitle>Add a component to {selected.code}</CardTitle>
            <CardDescription>An end-semester component is marked external. The marks of every component must reach the scheme total.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={addComponent} className="grid grid-cols-1 gap-4 lg:grid-cols-3">
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Code
                <Input value={componentCode} onChange={(event) => setComponentCode(event.target.value.toUpperCase())} placeholder="MID" required className="mt-1 font-mono uppercase" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A] lg:col-span-2">
                Name
                <Input value={componentName} onChange={(event) => setComponentName(event.target.value)} placeholder="Mid-semester test" required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Type
                <select className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs" value={componentType} onChange={(event) => setComponentType(event.target.value)}>
                  <option value="INTERNAL_TEST">Internal test</option>
                  <option value="ASSIGNMENT">Assignment</option>
                  <option value="LAB">Lab</option>
                  <option value="VIVA">Viva</option>
                  <option value="PROJECT">Project</option>
                  <option value="ATTENDANCE">Attendance</option>
                  <option value="END_SEMESTER">End semester</option>
                  <option value="OTHER">Other</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Marks
                <Input type="number" min={0} value={maxMarks} onChange={(event) => setMaxMarks(event.target.value)} required className="mt-1" />
              </label>
              <label className="text-xs font-semibold text-[#0B1F3A]">
                Weight percent
                <Input type="number" min={0} max={100} value={weightagePct} onChange={(event) => setWeightagePct(event.target.value)} className="mt-1" />
              </label>
              <div className="flex items-end justify-end gap-2 lg:col-span-3">
                <Button type="button" variant="outline" onClick={activate} isLoading={saving} disabled={selected.components.reduce((sum, item) => sum + item.maxMarks, 0) !== selected.totalMarks}>
                  Activate scheme
                </Button>
                <Button type="submit" variant="primary" isLoading={saving}>Add component</Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
