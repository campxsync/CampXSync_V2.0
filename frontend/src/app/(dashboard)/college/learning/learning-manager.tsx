'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

type Named = { id: string; code: string; name: string };
type Labeled = { id: string; label: string };

const sections = ['Course', 'Content', 'Lessons', 'Assignments', 'Classroom', 'Progress', 'Credentials', 'Outcomes'] as const;

async function post(url: string, body: unknown) {
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || 'The record could not be saved.');
  return data;
}

export function LearningManager(props: {
  subjects: Named[];
  departments: Named[];
  faculty: Labeled[];
  programs: Named[];
  students: Labeled[];
  courses: { id: string; code: string; name: string; status: string; credits: number }[];
  contents: { id: string; title: string; status: string; subject_code: string | null; module_title: string | null; topic_title: string | null; content_type: string | null }[];
  lessons: { id: string; title: string; status: string; planned_hours: number | null; subject_code: string | null; employee_no: string | null; display_name: string | null; planned_date: string | null; session_status: string | null }[];
  assignments: { id: string; title: string; status: string; assignment_type: string; max_marks: number | null; subject_code: string | null }[];
  classrooms: { id: string; name: string; provider: string; status: string; title: string | null; session_status: string | null; scheduled_start: string | null }[];
  progress: { id: string; status: string; percent_complete: number; enrollment_no: string; subject_code: string | null }[];
  badges: { id: string; code: string; name: string; status: string }[];
  certificates: { id: string; code: string; name: string; status: string }[];
  outcomes: { course_code: string; program_code: string; correlation_level: number }[];
}) {
  const router = useRouter();
  const [section, setSection] = useState<(typeof sections)[number]>('Course');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const subjectId = props.subjects[0]?.id || '';
  const departmentId = props.departments[0]?.id || '';
  const facultyId = props.faculty[0]?.id || '';
  const programId = props.programs[0]?.id || '';
  const studentId = props.students[0]?.id || '';

  const run = async (work: () => Promise<{ message?: string }>) => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const data = await work();
      setMessage(data.message || 'Saved.');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The record could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-[#0B1F3A]">Learning</h1>
        <p className="mt-1 max-w-3xl text-sm text-[#5C6B80]">
          Delivery courses, content, lesson plans, assignments, classrooms, progress, credentials, and outcomes.
          Subjects remain the academic master. Readings stay in the <Link className="text-[#1F6FEB]" href="/college/resources">resource library</Link>.
          Attainment is not calculated here, and no meeting provider is called.
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        {sections.map((item) => (
          <button key={item} type="button" onClick={() => setSection(item)} className={`rounded-full px-3 py-1 text-xs font-semibold ${section === item ? 'bg-[#0B1F3A] text-white' : 'bg-white text-[#0B1F3A] ring-1 ring-[#E3E8EF]'}`}>
            {item}
          </button>
        ))}
      </div>
      {message && <p className="text-sm text-[#067647]">{message}</p>}
      {error && <p className="text-sm text-[#B42318]">{error}</p>}

      {section === 'Course' && (
        <Section title="Delivery course" description="The academic course identity. Activating a draft makes it the live delivery course.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/courses', {
            departmentId: String(form.get('departmentId')),
            code: String(form.get('code')).toUpperCase(),
            name: String(form.get('name')),
            credits: Number(form.get('credits')),
          }))}>
            <Select name="departmentId" label="Department" options={props.departments.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="code" label="Code" placeholder="CS301-DELIVERY" />
            <Field name="name" label="Name" placeholder="Operating Systems delivery" />
            <Field name="credits" label="Credits" type="number" defaultValue="4" />
            <Button type="submit" variant="primary" isLoading={saving}>Open draft</Button>
          </Form>
          <Rows rows={props.courses.map((item) => ({ id: item.id, title: `${item.code} · ${item.name}`, meta: `${item.credits} credits`, status: item.status, action: item.status === 'DRAFT' ? () => run(() => post('/api/college/learning/courses/publish', { courseId: item.id })) : undefined, actionLabel: 'Activate' }))} />
          <p className="text-xs text-[#5C6B80]">Subject master: {props.subjects.map((item) => item.code).join(', ') || 'none'}.</p>
        </Section>
      )}

      {section === 'Content' && (
        <Section title="Course content" description="One module and one topic on a subject. A link or video is an address. A file is not uploaded.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/content', {
            subjectId: String(form.get('subjectId') || subjectId),
            title: String(form.get('title')),
            moduleTitle: String(form.get('moduleTitle')),
            topicTitle: String(form.get('topicTitle')),
            contentType: String(form.get('contentType')),
            body: String(form.get('body') || '') || undefined,
          }))}>
            <Select name="subjectId" label="Subject" options={props.subjects.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="title" label="Content title" placeholder="CS301 delivery notes" />
            <Field name="moduleTitle" label="Module" placeholder="Processes" />
            <Field name="topicTitle" label="Topic" placeholder="Process model" />
            <Select name="contentType" label="Type" options={[{ value: 'TEXT', label: 'Text' }, { value: 'LINK', label: 'Link' }, { value: 'VIDEO', label: 'Video' }]} />
            <Field name="body" label="Text" placeholder="What the class covers" />
            <Button type="submit" variant="primary" isLoading={saving}>Record draft</Button>
          </Form>
          <Rows rows={props.contents.map((item) => ({ id: item.id, title: item.title, meta: `${item.subject_code || ''} · ${item.module_title || ''} · ${item.topic_title || ''} · ${item.content_type || ''}`, status: item.status, action: item.status === 'DRAFT' ? () => run(() => post('/api/college/learning/content/publish', { contentId: item.id })) : undefined, actionLabel: 'Publish' }))} />
        </Section>
      )}

      {section === 'Lessons' && (
        <Section title="Lesson plan" description="A plan for one faculty member and one subject, with the first planned session.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/lessons', {
            subjectId: String(form.get('subjectId') || subjectId),
            employeeId: String(form.get('employeeId') || facultyId),
            title: String(form.get('title')),
            plannedHours: Number(form.get('plannedHours')),
            plannedDate: String(form.get('plannedDate')),
          }))}>
            <Select name="subjectId" label="Subject" options={props.subjects.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Select name="employeeId" label="Faculty" options={props.faculty.map((item) => ({ value: item.id, label: item.label }))} />
            <Field name="title" label="Title" placeholder="OS week plan" />
            <Field name="plannedHours" label="Planned hours" type="number" defaultValue="4" />
            <Field name="plannedDate" label="First session" type="date" />
            <Button type="submit" variant="primary" isLoading={saving}>Record draft</Button>
          </Form>
          <Rows rows={props.lessons.map((item) => ({ id: item.id, title: item.title, meta: `${item.subject_code || ''} · ${item.display_name || item.employee_no || ''} · ${item.planned_date || ''} · ${item.session_status || ''}`, status: item.status, action: item.status === 'DRAFT' ? () => run(() => post('/api/college/learning/lessons/approve', { planId: item.id })) : undefined, actionLabel: 'Approve' }))} />
        </Section>
      )}

      {section === 'Assignments' && (
        <Section title="Assignment" description="A published assignment is fixed as a task. Students do not submit a file from this screen.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/assignments', {
            subjectId: String(form.get('subjectId') || subjectId),
            title: String(form.get('title')),
            assignmentType: String(form.get('assignmentType')),
            maxMarks: Number(form.get('maxMarks')),
          }))}>
            <Select name="subjectId" label="Subject" options={props.subjects.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="title" label="Title" placeholder="Process questions" />
            <Select name="assignmentType" label="Type" options={['ASSIGNMENT', 'QUIZ', 'PROJECT', 'LAB', 'PRESENTATION'].map((value) => ({ value, label: value }))} />
            <Field name="maxMarks" label="Marks" type="number" defaultValue="20" />
            <Button type="submit" variant="primary" isLoading={saving}>Open draft</Button>
          </Form>
          <Rows rows={props.assignments.map((item) => ({ id: item.id, title: item.title, meta: `${item.subject_code || ''} · ${item.assignment_type} · ${item.max_marks ?? '—'} marks`, status: item.status, action: item.status === 'DRAFT' ? () => run(() => post('/api/college/learning/assignments/publish', { assignmentId: item.id })) : undefined, actionLabel: 'Publish' }))} />
        </Section>
      )}

      {section === 'Classroom' && (
        <Section title="Classroom" description="Records the room and one scheduled session. The provider is a label. CampXSync does not open the meeting.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/classrooms', {
            name: String(form.get('name')),
            provider: String(form.get('provider')),
            title: String(form.get('title')),
            scheduledStart: new Date(String(form.get('start'))).toISOString(),
            scheduledEnd: new Date(String(form.get('end'))).toISOString(),
          }))}>
            <Field name="name" label="Room" placeholder="CS301 room" />
            <Select name="provider" label="Provider" options={['OTHER', 'MEET', 'ZOOM', 'TEAMS', 'JITSI', 'BIGBLUEBUTTON'].map((value) => ({ value, label: value }))} />
            <Field name="title" label="Session" placeholder="OS live class" />
            <Field name="start" label="Starts" type="datetime-local" />
            <Field name="end" label="Ends" type="datetime-local" />
            <Button type="submit" variant="primary" isLoading={saving}>Schedule</Button>
          </Form>
          <Rows rows={props.classrooms.map((item) => ({ id: item.id, title: item.name, meta: `${item.provider} · ${item.title || ''} · ${item.session_status || ''}`, status: item.status }))} />
        </Section>
      )}

      {section === 'Progress' && (
        <Section title="Learner progress" description="A subject completion percentage recorded by the college office for one student.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/progress', {
            studentId: String(form.get('studentId') || studentId),
            subjectId: String(form.get('subjectId') || subjectId),
            percentComplete: Number(form.get('percentComplete')),
          }))}>
            <Select name="studentId" label="Student" options={props.students.map((item) => ({ value: item.id, label: item.label }))} />
            <Select name="subjectId" label="Subject" options={props.subjects.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="percentComplete" label="Percent" type="number" defaultValue="40" />
            <Button type="submit" variant="primary" isLoading={saving}>Record</Button>
          </Form>
          <Rows rows={props.progress.map((item) => ({ id: item.id, title: item.enrollment_no, meta: `${item.subject_code || ''} · ${item.percent_complete}%`, status: item.status }))} />
        </Section>
      )}

      {section === 'Credentials' && (
        <Section title="Credentials" description="Defines a badge or a certificate. Neither is issued to a student from this screen.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/credentials', {
            kind: String(form.get('kind')),
            code: String(form.get('code')).toUpperCase(),
            name: String(form.get('name')),
            programId: String(form.get('kind')) === 'CERTIFICATE' ? String(form.get('programId') || programId) : undefined,
          }))}>
            <Select name="kind" label="Kind" options={[{ value: 'BADGE', label: 'Badge' }, { value: 'CERTIFICATE', label: 'Certificate' }]} />
            <Select name="programId" label="Program" options={props.programs.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="code" label="Code" placeholder="OS-READER" />
            <Field name="name" label="Name" placeholder="Operating systems reader" />
            <Button type="submit" variant="primary" isLoading={saving}>Define draft</Button>
          </Form>
          <Rows rows={[...props.badges.map((item) => ({ id: item.id, title: `Badge · ${item.code}`, meta: item.name, status: item.status })), ...props.certificates.map((item) => ({ id: item.id, title: `Certificate · ${item.code}`, meta: item.name, status: item.status }))]} />
        </Section>
      )}

      {section === 'Outcomes' && (
        <Section title="Outcomes" description="A course outcome on a subject, a program outcome on a program, and their correlation. Attainment is not calculated.">
          <Form disabled={saving} onSubmit={(form) => run(() => post('/api/college/learning/outcomes', {
            subjectId: String(form.get('subjectId') || subjectId),
            programId: String(form.get('programId') || programId),
            courseCode: String(form.get('courseCode')).toUpperCase(),
            courseStatement: String(form.get('courseStatement')),
            programCode: String(form.get('programCode')).toUpperCase(),
            programStatement: String(form.get('programStatement')),
            correlationLevel: Number(form.get('correlationLevel')),
          }))}>
            <Select name="subjectId" label="Subject" options={props.subjects.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Select name="programId" label="Program" options={props.programs.map((item) => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
            <Field name="courseCode" label="Course outcome code" placeholder="CS301-CO1" />
            <Field name="courseStatement" label="Course outcome" placeholder="Apply process scheduling" />
            <Field name="programCode" label="Program outcome code" placeholder="BTECH-PO1" />
            <Field name="programStatement" label="Program outcome" placeholder="Apply engineering knowledge" />
            <Select name="correlationLevel" label="Correlation" options={[{ value: '1', label: '1 Low' }, { value: '2', label: '2 Medium' }, { value: '3', label: '3 High' }]} />
            <Button type="submit" variant="primary" isLoading={saving}>Record mapping</Button>
          </Form>
          <Rows rows={props.outcomes.map((item) => ({ id: `${item.course_code}-${item.program_code}`, title: `${item.course_code} → ${item.program_code}`, meta: `correlation ${item.correlation_level}`, status: 'ACTIVE' }))} />
        </Section>
      )}
    </div>
  );
}

function Section({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">{children}</CardContent>
    </Card>
  );
}

function Form({ children, onSubmit, disabled }: { children: React.ReactNode; onSubmit: (form: FormData) => void; disabled: boolean }) {
  return (
    <form className="grid grid-cols-1 gap-3 lg:grid-cols-2" onSubmit={(event) => { event.preventDefault(); if (!disabled) onSubmit(new FormData(event.currentTarget)); }}>
      {children}
    </form>
  );
}

function Field({ name, label, type = 'text', placeholder, defaultValue }: { name: string; label: string; type?: string; placeholder?: string; defaultValue?: string }) {
  return (
    <label className="text-xs font-semibold text-[#0B1F3A]">
      {label}
      <Input name={name} type={type} placeholder={placeholder} defaultValue={defaultValue} required className="mt-1" />
    </label>
  );
}

function Select({ name, label, options }: { name: string; label: string; options: { value: string; label: string }[] }) {
  return (
    <label className="text-xs font-semibold text-[#0B1F3A]">
      {label}
      <select name={name} className="mt-1 h-10 w-full rounded-[8px] border border-[#E3E8EF] bg-white px-3 text-xs" required defaultValue={options[0]?.value || ''}>
        {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    </label>
  );
}

function Rows({ rows }: { rows: { id: string; title: string; meta: string; status: string; action?: () => void; actionLabel?: string }[] }) {
  if (rows.length === 0) return <p className="text-xs text-[#6B7688]">Nothing recorded yet.</p>;
  return (
    <div className="space-y-2">
      {rows.map((row) => (
        <div key={row.id} className="flex flex-wrap items-center justify-between gap-2 rounded-[8px] border border-[#E3E8EF] px-3 py-2">
          <div>
            <p className="text-sm font-semibold text-[#0B1F3A]">{row.title}</p>
            <p className="text-xs text-[#6B7688]">{row.meta}</p>
          </div>
          <div className="flex items-center gap-2">
            <Badge status={row.status} />
            {row.action && <Button type="button" variant="outline" onClick={row.action}>{row.actionLabel}</Button>}
          </div>
        </div>
      ))}
    </div>
  );
}
