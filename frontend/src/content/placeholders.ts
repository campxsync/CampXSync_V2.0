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
    tagline: 'Higher Education Operating System & Academic Governance',
  },

  // Marquee tiles (Placeholder institution / system partners)
  marqueePartners: [
    { id: 'p1', name: 'Institution Partner Alpha', category: 'State University System' },
    { id: 'p2', name: 'Regional Polytechnic System', category: 'Technical Board' },
    { id: 'p3', name: 'Collegiate Consortium Beta', category: 'Autonomous Colleges' },
    { id: 'p4', name: 'Institute of Advanced Science', category: 'Research Council' },
    { id: 'p5', name: 'National Medical Academy', category: 'Professional Board' },
    { id: 'p6', name: 'Central University Affiliation', category: 'Governance Body' },
  ] as MarqueeTile[],

  // Impact statistics strip (All with sample markers)
  impactMetrics: [
    {
      id: 'metric-clash',
      target: 100,
      decimals: 0,
      suffix: '%',
      label: 'Clash Resolution Engine',
      description: 'Sample Metric — Algorithmic room and faculty conflict detection',
      isSample: true,
    },
    {
      id: 'metric-audit',
      target: 100,
      decimals: 0,
      suffix: '%',
      label: 'Immutable Audit Trail',
      description: 'Sample Metric — End-to-end ledger verification across all schema updates',
      isSample: true,
    },
    {
      id: 'metric-units',
      target: 50,
      decimals: 0,
      prefix: '',
      suffix: '+',
      label: 'Connected Academic Units',
      description: 'Sample Metric — Federated departments operating on unified master records',
      isSample: true,
    },
    {
      id: 'metric-relay',
      target: 99,
      decimals: 1,
      suffix: '%',
      label: 'Attendance Relay Fidelity',
      description: 'Sample Metric — Real-time biometric and digital roster synchronization',
      isSample: true,
    },
  ] as MetricPlaceholder[],

  // "One Shared Foundation" - 4 Pillars
  pillars: [
    {
      id: 'curriculum',
      title: 'Curriculum & Schemes',
      subtitle: 'Outcome-Based Education Structure',
      badge: 'Academic Core',
      features: [
        'Multi-tier credit frameworks with prerequisite graph validation',
        'Federated syllabus versioning and Board of Studies governance',
        'Continuous assessment weighting with automated GPA calculations',
      ],
    },
    {
      id: 'timetable',
      title: 'Timetable & Attendance',
      subtitle: 'Deterministic Scheduling Matrix',
      badge: 'Operational Pulse',
      features: [
        'Constraint-satisfaction room and faculty allocation engine',
        'Dynamic elective conflict resolver with instant re-allocation',
        'Biometric, mobile, and RFID attendance relay synchronization',
      ],
    },
    {
      id: 'examinations',
      title: 'Examinations & Grading',
      subtitle: 'Tamper-Evident Assessment Ledger',
      badge: 'Evaluation Integrity',
      features: [
        'Anonymized optical grading and multi-evaluator moderation',
        'Cryptographic grade sheet generation with verifiable QR audit',
        'Automated backlog tracking and degree completion auditing',
      ],
    },
    {
      id: 'governance',
      title: 'Executive Governance',
      subtitle: 'Campus-Wide Command & Telemetry',
      badge: 'Institutional Trust',
      features: [
        'Multi-tenant security isolation across decentralized colleges',
        'Real-time accreditation telemetries (NAAC / NBA / ABET readiness)',
        'Comprehensive audit log with timestamped administrative trace',
      ],
    },
  ] as PillarItem[],

  // Capabilities tab items
  capabilities: [
    {
      id: 'curriculum-engine',
      label: 'Curriculum Engine',
      title: 'Syllabus & Scheme Governance with Total Integrity',
      badge: 'Academic Engineering',
      description:
        'Model intricate degree programs, multi-track electives, and credit prerequisite rules on an immutable schema. Empower departments to evolve curricula without disrupting historical transcripts.',
      bullets: [
        {
          title: 'Prerequisite Dependency Trees',
          desc: 'Automated validation prevents registration deadlocks and enforces sequential course compliance.',
        },
        {
          title: 'Outcome-Based Education (OBE)',
          desc: 'Direct mapping from Course Outcomes (CO) to Program Outcomes (PO) with dynamic attainment scoring.',
        },
        {
          title: 'Board of Studies Approval Flow',
          desc: 'Multi-stakeholder sign-off workflows with version diff inspection before semester deployment.',
        },
      ],
      previewType: 'curriculum',
    },
    {
      id: 'deterministic-scheduling',
      label: 'Automated Timetable',
      title: 'Zero-Collision Scheduling Across Faculty & Infrastructure',
      badge: 'Operational Engine',
      description:
        'Eliminate timetable clashes through computational constraint satisfaction. Compute optimized schedules for thousands of students and hundreds of lab spaces in seconds.',
      bullets: [
        {
          title: 'Constraint Satisfaction Solver',
          desc: 'Harmonizes faculty availability, room capacity, equipment requirements, and student elective choices.',
        },
        {
          title: 'Instant Contingency Rerouting',
          desc: 'Trigger substitute allocations or lecture swaps without cascading ripple conflicts.',
        },
        {
          title: 'Real-Time Roster Broadcast',
          desc: 'Push instant timetable alterations to scholar and faculty calendars with zero delay.',
        },
      ],
      previewType: 'timetable',
    },
    {
      id: 'examination-ledger',
      label: 'Examinations & Ledger',
      title: 'High-Stakes Evaluation with End-to-End Cryptographic Audit',
      badge: 'Evaluation Security',
      description:
        'From barcoded hall tickets to finalized transcripts, CampXSync ensures total examination security, preventing unauthorized grade alterations through row-level audit trails.',
      bullets: [
        {
          title: 'Anonymized Evaluation Queues',
          desc: 'Mask student identities during grading cycles to guarantee unbiased evaluation.',
        },
        {
          title: 'Multi-Tier Moderation Workflows',
          desc: 'Configurable moderation thresholds flag outlier scoring spreads for chair review.',
        },
        {
          title: 'Cryptographic Transcript Verification',
          desc: 'Generate instantly verifiable digital credentials anchored to institutional cryptographic keys.',
        },
      ],
      previewType: 'compliance',
    },
    {
      id: 'executive-telemetry',
      label: 'Executive Governance',
      title: 'Multi-Campus Telemetry & Accreditation Readiness',
      badge: 'Command Center',
      description:
        'Consolidate academic performance, faculty workload, space utilization, and compliance metrics into unified executive dashboards designed for Chancellors and Deans.',
      bullets: [
        {
          title: 'Continuous Accreditation Monitoring',
          desc: 'Live telemetry feeds satisfy compliance documentation requirements without manual year-end scrambles.',
        },
        {
          title: 'Strict Multi-Tenant Isolation',
          desc: 'Cryptographic and database-level tenancy guarantees complete data segregation between academic units.',
        },
        {
          title: 'Federated Administrative Controls',
          desc: 'Granular role-based access controls with full traceability across every administrative action.',
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
      title: 'Scholars & Students',
      subtitle: 'Academic Navigator',
      tagline: 'Frictionless academic journey from enrollment to convocation.',
      description:
        'A unified mobile-first dashboard providing instant timetable updates, degree audit progress, real-time attendance telemetry, and direct elective selections.',
      responsibilities: [
        'Interactive degree progression pathway with credit pacing indicators',
        'Live timetable with notifications for room or time shifts',
        'Transparent attendance ledger with alert thresholds',
        'Verified digital grade cards and credentials',
      ],
      metricsPlaceholder: 'Sample Data — Roster Pacing: Normal',
    },
    {
      id: 'faculty',
      role: 'faculty',
      title: 'Faculty & Researchers',
      subtitle: 'Teaching & Mentorship Cockpit',
      tagline: 'Less administrative overhead, more high-impact academic engagement.',
      description:
        'Streamlined course administration, attendance capture in two taps, automated continuous assessment calculations, and comprehensive scholar mentoring portfolios.',
      responsibilities: [
        'Single-click attendance logging with multi-session support',
        'Continuous evaluation rubrics with automatic grade distribution',
        'Direct syllabus coverage tracking against scheduled term weeks',
        'Advisory cohort monitoring with early retention risk signals',
      ],
      metricsPlaceholder: 'Sample Data — 100% Course Delivery Alignment',
    },
    {
      id: 'deans',
      role: 'deans',
      title: 'Deans & Department Chairs',
      subtitle: 'Academic Operations Directorate',
      tagline: 'Precision control over schemes, faculty allocations, and quality benchmarks.',
      description:
        'High-level operational cockpits to approve curriculum revisions, resolve multi-department resource bottlenecks, and oversee examination readiness.',
      responsibilities: [
        'Cross-departmental faculty teaching load balance analytics',
        'Curriculum revision diffs with Board of Studies approval trails',
        'Classroom and specialized laboratory utilization rates',
        'Real-time student retention and academic performance indices',
      ],
      metricsPlaceholder: 'Sample Data — Zero Unresolved Bottlenecks',
    },
    {
      id: 'core',
      role: 'core',
      title: 'University Core Leadership',
      subtitle: 'Executive Chancellor & Provost Suite',
      tagline: 'Strategic oversight across campuses with institutional-grade assurance.',
      description:
        'Strategic governance telemetry providing campus-wide synchronization, regulatory compliance reporting, multi-tenant policy enforcement, and audit observability.',
      responsibilities: [
        'Multi-campus aggregate enrollment and capacity monitoring',
        'Automated institutional compliance telemetry generation',
        'Tenant data isolation and role-based security audits',
        'Enterprise data continuity with sovereign cloud governance',
      ],
      metricsPlaceholder: 'Sample Data — Multi-Campus Relay Synchronized',
    },
  ] as RolePersona[],

  // Walkthrough steps for "See How It Works"
  walkthroughSteps: [
    {
      step: '01',
      title: 'Establish Institutional Schemas',
      description:
        'Model your credit structures, degree rules, and prerequisite dependencies once. The system guarantees mathematical validity across every semester catalog.',
    },
    {
      step: '02',
      title: 'Compute Clash-Free Operations',
      description:
        'Deploy the constraint satisfaction engine to allocate rooms, faculty, and elective sets with zero schedule conflicts and optimal physical space utilization.',
    },
    {
      step: '03',
      title: 'Relay Continuous Telemetry',
      description:
        'Capture attendance, internal assessments, and examination logs in a tamper-evident audit ledger that syncs directly with national accreditation formats.',
    },
  ],

  // Placeholder testimonials
  placeholderTestimonials: [
    {
      quote:
        '“Centralizing academic governance across our autonomous colleges eliminated weeks of manual scheduling and gave executive leadership unified visibility.”',
      author: 'Academic Dean',
      organization: 'Sample Institution Partner',
      roleTitle: 'Faculty Council Chair',
      tag: 'Sample Feedback',
    },
    {
      quote:
        '“The constraint satisfaction engine resolved complex multi-track elective conflicts that previously resulted in classroom bottlenecks every single semester.”',
      author: 'Registrar & Controller of Examinations',
      organization: 'Sample Technical University',
      roleTitle: 'University Administration',
      tag: 'Sample Feedback',
    },
  ],
};
