/**
 * CampXSync Centralized Placeholder Content & Metrics
 *
 * Strict Guideline: No invented metrics, percentages, GPAs, institution names or testimonials.
 * All metrics are clearly designated as demonstration/sample placeholders with clear tags.
 * This centralized file allows replacing all placeholders from one location.
 */

export interface MetricPlaceholder {
  id: string;
  target: number;
  decimals?: number;
  prefix?: string;
  suffix: string;
  label: string;
  description: string;
  isSample: boolean;
}

export interface MarqueeTile {
  id: string;
  name: string;
  category: string;
}

export interface PillarItem {
  id: string;
  title: string;
  subtitle: string;
  badge: string;
  features: string[];
}

export interface CapabilityTab {
  id: string;
  label: string;
  title: string;
  badge: string;
  description: string;
  bullets: { title: string; desc: string }[];
  previewType: 'curriculum' | 'timetable' | 'governance' | 'compliance';
}

export interface RolePersona {
  id: string;
  role: 'scholars' | 'faculty' | 'deans' | 'core';
  title: string;
  subtitle: string;
  tagline: string;
  description: string;
  responsibilities: string[];
  metricsPlaceholder: string;
}

export const PLACEHOLDERS = {
  brand: {
    name: 'CampXSync',
    wordmarkPrefix: 'Camp',
    wordmarkAccent: 'XSync',
    faviconText: 'CX',
    tagline: 'The college operating record: roster, timetable, learning, fees, exams, staff, and notices.',
  },

  // Marquee tiles (Placeholder institution / system partners)
  marqueePartners: [
    { id: 'p1', name: 'Academic calendar', category: 'College record' },
    { id: 'p2', name: 'Timetable and attendance', category: 'Learning' },
    { id: 'p3', name: 'Fee plans and office receipts', category: 'Finance' },
    { id: 'p4', name: 'Exam scripts and published marks', category: 'Examinations' },
    { id: 'p5', name: 'Staff and calculated payslips', category: 'People' },
    { id: 'p6', name: 'In-app notices', category: 'Communications' },
  ] as MarqueeTile[],

  // Impact statistics strip (All with sample markers)
  impactMetrics: [
    {
      id: 'metric-clash',
      target: 1,
      decimals: 0,
      suffix: '',
      label: 'Campus on the record',
      description: 'One campus is stored for the sample college. This is a count, not a performance score.',
      isSample: true,
    },
    {
      id: 'metric-audit',
      target: 1,
      decimals: 0,
      suffix: '',
      label: 'Published subject result',
      description: 'A subject result can be published as marks and a pass or fail. SGPA and CGPA are not calculated.',
      isSample: true,
    },
    {
      id: 'metric-units',
      target: 4,
      decimals: 0,
      prefix: '',
      suffix: '',
      label: 'Workspaces',
      description: 'Platform, college admin, faculty, and student each open their own workspace.',
      isSample: true,
    },
    {
      id: 'metric-relay',
      target: 1,
      decimals: 0,
      suffix: '',
      label: 'Office receipt matched',
      description: 'A fee receipt taken at the office can be marked matched. There is no payment gateway.',
      isSample: true,
    },
  ] as MetricPlaceholder[],

  // "One Shared Foundation" - 4 Pillars
  pillars: [
    {
      id: 'curriculum',
      title: 'Academic record',
      subtitle: 'Calendar, programme, batch, and subject',
      badge: 'College admin',
      features: [
        'Academic year, calendar, programme, batch, and subject are stored by the college admin',
        'A timetable slot names the subject, faculty, room, and day',
        'Faculty do not create students or academic records',
      ],
    },
    {
      id: 'timetable',
      title: 'Learning',
      subtitle: 'Attendance follows the timetable',
      badge: 'Faculty',
      features: [
        'Attendance is marked against a timetable session',
        'A correction keeps a reason',
        'There is no biometric, RFID, or scanner capture',
      ],
    },
    {
      id: 'examinations',
      title: 'Examinations',
      subtitle: 'Registration, script, marks, published result',
      badge: 'College admin',
      features: [
        'An exam, registration, script, and marks are recorded in order',
        'Publishing a result stores marks and pass or fail',
        'Hall-ticket PDFs and SGPA are not produced',
      ],
    },
    {
      id: 'governance',
      title: 'Fees, staff, campus, notices',
      subtitle: 'Office money, calculated pay, one campus, in-app notices',
      badge: 'College admin',
      features: [
        'A fee plan and an office receipt can be reconciled as matched',
        'A payslip is calculated from entered pay. It is not a bank payment',
        'One campus and an in-app notice can be stored and marked delivered',
      ],
    },
  ] as PillarItem[],

  // Capabilities tab items
  capabilities: [
    {
      id: 'curriculum-engine',
      label: 'Academic record',
      title: 'The college admin keeps the academic year, programme, batch, and subject',
      badge: 'Academic',
      description:
        'Programmes, batches, subjects, and timetable slots live on the college record. The page does not score outcomes or approve a board of studies.',
      bullets: [
        {
          title: 'Calendar and scheme',
          desc: 'The college admin records the academic year, calendar, programme, and batch.',
        },
        {
          title: 'Subject and faculty slot',
          desc: 'A timetable slot names who teaches which subject, in which room, on which day.',
        },
        {
          title: 'One creator',
          desc: 'Faculty cannot enrol students or create academic rows. The college admin does.',
        },
      ],
      previewType: 'curriculum',
    },
    {
      id: 'deterministic-scheduling',
      label: 'Learning',
      title: 'Attendance is taken on the session that the timetable already names',
      badge: 'Learning',
      description:
        'Faculty mark attendance for a scheduled session and can correct it with a reason. The product does not solve clashes or sync a biometric device.',
      bullets: [
        {
          title: 'Session attendance',
          desc: 'The mark follows the timetable slot, not a separate scanner feed.',
        },
        {
          title: 'Correction with a reason',
          desc: 'A later change stores why it was changed.',
        },
        {
          title: 'Learning material',
          desc: 'The college admin can attach learning records to the subject. File virus scanning is not part of this.',
        },
      ],
      previewType: 'timetable',
    },
    {
      id: 'examination-ledger',
      label: 'Examinations',
      title: 'A result is published as marks and a pass or fail',
      badge: 'Examinations',
      description:
        'The college admin records the exam, registration, script, and marks, then publishes the result. There is no hall-ticket PDF and no SGPA.',
      bullets: [
        {
          title: 'Registration and script',
          desc: 'A student registration and an answer script are stored before marks.',
        },
        {
          title: 'Marks then publish',
          desc: 'Publishing stores the marks and pass or fail on the subject result.',
        },
        {
          title: 'No grade-point rollup',
          desc: 'SGPA and CGPA are not computed from that result.',
        },
      ],
      previewType: 'compliance',
    },
    {
      id: 'executive-telemetry',
      label: 'College operations',
      title: 'Fees, staff pay, one campus, and an in-app notice',
      badge: 'Operations',
      description:
        'The college admin records money taken at the office, a calculated payslip, one campus, and a notice delivered inside the app. Accreditation scores are not generated.',
      bullets: [
        {
          title: 'Office receipt',
          desc: 'A receipt can be reconciled as matched against the fee demand. No card or bank gateway is connected.',
        },
        {
          title: 'Calculated payslip',
          desc: 'Pay is calculated from the amount entered. The product does not send it to a bank.',
        },
        {
          title: 'Campus and notice',
          desc: 'One campus is stored. A notice can be marked sent and delivered in the app.',
        },
      ],
      previewType: 'governance',
    },
  ] as CapabilityTab[],

  // Role persona slider items
  personas: [
    {
      id: 'scholars',
      role: 'scholars',
      title: 'Students',
      subtitle: 'Student workspace',
      tagline: 'The student sees the college record that the admin created for them.',
      description:
        'A student signs into a student workspace. Enrolment, fees, and results are created by the college admin, not by the student.',
      responsibilities: [
        'See the programme, batch, and timetable that belong to the student',
        'See attendance recorded against those sessions',
        'See a published subject result as marks and pass or fail',
        'See fee demand and an office receipt when one exists',
      ],
      metricsPlaceholder: 'Student workspace',
    },
    {
      id: 'faculty',
      role: 'faculty',
      title: 'Faculty',
      subtitle: 'Faculty workspace',
      tagline: 'Faculty teach and mark attendance. They do not enrol the college.',
      description:
        'Faculty open the sessions on their timetable, mark attendance, and correct a mark with a reason.',
      responsibilities: [
        'Open the faculty workspace after sign-in',
        'Mark attendance for a scheduled session',
        'Correct attendance only with a reason',
        'Leave student, fee, exam, and staff creation to the college admin',
      ],
      metricsPlaceholder: 'Faculty workspace',
    },
    {
      id: 'deans',
      role: 'deans',
      title: 'College admin',
      subtitle: 'College workspace',
      tagline: 'One college admin creates the people and the records inside that college.',
      description:
        'The college admin creates faculty, students, academic rows, learning, finance, exams, staff, notices, and the campus.',
      responsibilities: [
        'Create faculty and students for the college',
        'Record timetable, attendance corrections, fees, and exams',
        'Publish a subject result and match an office receipt',
        'Store one campus and deliver an in-app notice',
      ],
      metricsPlaceholder: 'College workspace',
    },
    {
      id: 'core',
      role: 'core',
      title: 'Platform admin',
      subtitle: 'Platform workspace',
      tagline: 'The platform admin sees institutes. They do not create people inside a college.',
      description:
        'Platform administration lists the institutes on the platform. College records stay with the college admin.',
      responsibilities: [
        'Open the platform workspace',
        'See the institutes that exist on the platform',
        'Leave faculty, students, and college records to the college admin',
        'There is no separate school-admin sign-in',
      ],
      metricsPlaceholder: 'Platform workspace',
    },
  ] as RolePersona[],

  // Walkthrough steps for "See How It Works"
  walkthroughSteps: [
    {
      step: '01',
      title: 'The college admin opens the record',
      description:
        'Create the academic year, programme, batch, subject, faculty, and students. Faculty cannot do that step.',
    },
    {
      step: '02',
      title: 'Learning, fees, and exams follow those rows',
      description:
        'Attendance uses the timetable. An office receipt can be matched. An exam script receives marks, then a published pass or fail.',
    },
    {
      step: '03',
      title: 'Staff, campus, and a notice stay on the same college',
      description:
        'A payslip is calculated from entered pay. One campus is stored. A notice can be delivered inside the app.',
    },
  ],

  // Placeholder testimonials
  placeholderTestimonials: [
    {
      quote:
        'This page describes the college record. It is not a quotation from a customer.',
      author: 'CampXSync',
      organization: 'Product note',
      roleTitle: 'Landing',
      tag: 'Product note',
    },
    {
      quote:
        'Counts on this page are the shape of the record, not a benchmark.',
      author: 'CampXSync',
      organization: 'Product note',
      roleTitle: 'Landing',
      tag: 'Product note',
    },
  ],
};
