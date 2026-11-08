export type ModuleId =
  | 'academic'
  | 'examination'
  | 'lms'
  | 'finance'
  | 'hr'
  | 'library'
  | 'hostel'
  | 'placement'
  | 'transport'
  | 'facilities'
  | 'alumni'
  | 'communication';

export const stakeholders = [
  {
    id: 'students',
    name: 'Students',
    promise: 'The day, the course, the fee, and the result live in one place.',
    modules: ['Academic', 'Examination', 'Learning', 'Finance', 'Communication'],
  },
  {
    id: 'faculty',
    name: 'Faculty',
    promise: 'Class, assessment, and course work stay on the same page.',
    modules: ['Academic', 'Examination', 'Learning', 'Communication'],
  },
  {
    id: 'administrators',
    name: 'Administrators',
    promise: 'Run the college without chasing the same fact across desks.',
    modules: ['Academic', 'Finance', 'Human Resource', 'Facilities', 'Communication'],
  },
  {
    id: 'parents',
    name: 'Parents',
    promise: 'Follow fees, results, and notices without calling the office.',
    modules: ['Finance', 'Examination', 'Communication'],
  },
  {
    id: 'management',
    name: 'Management',
    promise: 'See every college from one institute view, each with its own space.',
    modules: ['Administration', 'Intelligence'],
  },
  {
    id: 'alumni',
    name: 'Alumni',
    promise: 'Stay connected after the degree, for news, opportunity, and belonging.',
    modules: ['Alumni', 'Placement', 'Communication'],
  },
  {
    id: 'partners',
    name: 'External Partners',
    promise: 'Reach the right students and offices through an open platform.',
    modules: ['Placement', 'Communication', 'Integrations'],
  },
  {
    id: 'agents',
    name: 'AI Agents',
    promise: 'Carry routine campus work with the context the institution already trusts.',
    modules: ['Intelligence', 'Every module the task belongs to'],
  },
] as const;

export const channels = [
  'Web Portal',
  'Mobile App',
  'Conversational AI',
  'Email',
  'SMS',
  'WhatsApp',
  'Push Notifications',
  'API and Integrations',
] as const;

export const modules: {
  id: ModuleId;
  name: string;
  summary: string;
  capabilities: string[];
  related: ModuleId[];
}[] = [
  {
    id: 'academic',
    name: 'Academic Management',
    summary: 'The structure of teaching, from curriculum to the day’s timetable.',
    capabilities: [
      'Course Management',
      'Curriculum & Syllabus',
      'Class Scheduling',
      'Faculty Allocation',
      'Attendance Tracking',
      'Learning Pathways',
      'Academic Calendar',
      'Reports & Analytics',
    ],
    related: ['examination', 'lms', 'communication'],
  },
  {
    id: 'examination',
    name: 'Examination Management',
    summary: 'From registration to a result a student can trust.',
    capabilities: [
      'Exam Registration',
      'Timetable Management',
      'Question Bank',
      'Hall Ticket Generation',
      'Result Processing',
      'Grading & Evaluation',
      'Revaluation Management',
      'Reports & Analytics',
    ],
    related: ['academic', 'communication'],
  },
  {
    id: 'lms',
    name: 'Learning Management',
    summary: 'Course access, work, and progress after a student is enrolled.',
    capabilities: [
      'Course Content',
      'Assignments',
      'Discussions & Forums',
      'Virtual Classrooms',
      'Assessments',
      'Progress Tracking',
      'E-Library Integration',
      'Certificates & Badges',
    ],
    related: ['academic', 'communication'],
  },
  {
    id: 'finance',
    name: 'Finance & Accounts',
    summary: 'Fees, receipts, and the money story students and parents can follow.',
    capabilities: [
      'Fee Structure Setup',
      'Fee Collection & Receipts',
      'Scholarship Management',
      'Expense Management',
      'Budgeting & Planning',
      'Payroll Management',
      'Financial Reports',
      'Audit & Compliance',
    ],
    related: ['communication'],
  },
  {
    id: 'hr',
    name: 'Human Resource Management',
    summary: 'People, leave, and approvals for the staff who keep campus running.',
    capabilities: [
      'Employee Information',
      'Recruitment & Onboarding',
      'Leave Management',
      'Attendance & Timesheets',
      'Performance Management',
      'Training & Development',
      'Payroll Integration',
      'HR Analytics',
    ],
    related: ['communication', 'finance'],
  },
  {
    id: 'library',
    name: 'Library Management',
    summary: 'The collection, the member, and the reminder in one quiet system.',
    capabilities: [
      'Catalog Management',
      'Book Circulation',
      'Digital Library',
      'Membership Management',
      'Fine & Penalty',
      'E-Resource Access',
      'Stock Verification',
      'Reports & Insights',
    ],
    related: ['academic', 'communication'],
  },
  {
    id: 'hostel',
    name: 'Hostel Management',
    summary: 'Rooms, meals, and resident care without a separate paper trail.',
    capabilities: [
      'Room Allocation',
      'Check-in / Check-out',
      'Mess Management',
      'Visitor Management',
      'Student Discipline',
      'Complaint Management',
      'Hostel Fee Management',
      'Reports & Analytics',
    ],
    related: ['finance', 'communication'],
  },
  {
    id: 'placement',
    name: 'Placement Management',
    summary: 'A drive reaches only the students who are ready for it.',
    capabilities: [
      'Company Registration',
      'Job & Internship Drives',
      'Student Eligibility',
      'Application Tracking',
      'Interview Scheduling',
      'Offer Management',
      'Placement Analytics',
      'Alumni Connect',
    ],
    related: ['alumni', 'communication'],
  },
  {
    id: 'transport',
    name: 'Transport Management',
    summary: 'Routes, seats, and schedule changes students actually receive.',
    capabilities: [
      'Route & Vehicle Setup',
      'Student Allocation',
      'GPS Tracking',
      'Driver Management',
      'Maintenance Scheduling',
      'Fee Management',
      'Reports & Analytics',
    ],
    related: ['finance', 'communication'],
  },
  {
    id: 'facilities',
    name: 'Facilities Management',
    summary: 'Rooms, assets, and upkeep requests with a clear owner.',
    capabilities: [
      'Asset Management',
      'Maintenance Requests',
      'Work Order Management',
      'Utility Management',
      'Vendor Management',
      'Preventive Maintenance',
      'Inventory Management',
      'Reports & Analytics',
    ],
    related: ['academic', 'communication'],
  },
  {
    id: 'alumni',
    name: 'Alumni Management',
    summary: 'The relationship continues after the last examination.',
    capabilities: [
      'Alumni Directory',
      'Event Management',
      'Mentorship Programs',
      'Donation Management',
      'Alumni Analytics',
      'Feedback Management',
      'Engagement Tracking',
      'Reports & Analytics',
    ],
    related: ['placement', 'communication'],
  },
  {
    id: 'communication',
    name: 'Communication & Announcement',
    summary: 'The right notice, to the right people, on the channel they use.',
    capabilities: [
      'Announcements',
      'Targeted Messaging',
      'Email & SMS Gateway',
      'Push Notifications',
      'Event Notifications',
      'Survey & Feedback',
      'Emergency Alerts',
      'Communication Analytics',
    ],
    related: ['academic', 'examination', 'finance'],
  },
];

export const platformAdmin = [
  'Institute Management',
  'System & Platform Configuration',
  'Global roles and permissions',
  'Data Governance & Policies',
  'Global Analytics & Monitoring',
  'Billing & Subscription Management',
  'Security & Compliance',
  'Audit Logs & System Health',
];

export const collegeAdmin = [
  'College Configuration',
  'College Users Management',
  'College roles and permissions',
  'Academic & Operational Settings',
  'Student/Faculty Data Management',
  'Reports & Analytics',
  'College Audit Logs',
];

export const engines = [
  {
    id: 'context',
    name: 'Context Engine',
    facets: 'Who, What, When, Where, Why, How',
    plain: 'Understands who needs what, and when.',
  },
  {
    id: 'graph',
    name: 'Knowledge Graph',
    facets: 'Entities, Relationships, Events',
    plain: 'Keeps people, courses, and events in one connected picture.',
  },
  {
    id: 'decision',
    name: 'Decision Engine',
    facets: 'Rules, learned models, analytics',
    plain: 'Chooses the next step from institutional rules and what the campus has learned.',
  },
  {
    id: 'insight',
    name: 'Insight Engine',
    facets: 'Predictions, Recommendations',
    plain: 'Surfaces what is likely to need a person’s attention.',
  },
  {
    id: 'action',
    name: 'Action Engine',
    facets: 'Workflows, Automations',
    plain: 'Turns a decision into work people can follow.',
  },
  {
    id: 'notification',
    name: 'Notification Engine',
    facets: 'Alerts, Reminders, Updates',
    plain: 'Reaches the right person with the update that matters.',
  },
  {
    id: 'workflow',
    name: 'Workflow Engine',
    facets: 'Approvals, Tasks, Escalations',
    plain: 'Carries an approval through to a clear end.',
  },
  {
    id: 'audit',
    name: 'Audit & Compliance',
    facets: 'Logs, Policies, Governance',
    plain: 'Keeps a record of what happened, and under which rule.',
  },
] as const;

export const beats = [
  { word: 'Remember', copy: 'The campus keeps its context. A person, a course, and a decision are not lost between desks.' },
  { word: 'Understand', copy: 'Relationships become clear. Who teaches, who owes, who is eligible, and what happens next.' },
  { word: 'Act', copy: 'The right work moves. A confirmation, an approval, or an alert leaves the core and reaches someone.' },
  { word: 'Evolve', copy: 'The institution learns from the day it just finished, and the next morning is a little wiser.' },
];

export const flows = [
  ['Student', 'Academic', 'Exam', 'Result', 'Notification'],
  ['Fee Payment', 'Finance', 'Receipt', 'Student / Parent'],
  ['Leave Request', 'HR', 'Approval', 'Notification'],
  ['Placement Drive', 'Placement', 'Eligible Students', 'Alerts'],
];

export const scenario = [
  { title: 'Signs in', detail: 'The student opens the portal or the app.', moment: 'login' },
  { title: 'Enrollment is created', detail: 'Academic Management checks the request and records the enrollment.', moment: 'enroll' },
  { title: 'Fee status is checked', detail: 'Finance & Accounts confirms the fee and payment status.', moment: 'fee' },
  { title: 'Confirmation is sent', detail: 'The Notification Engine tells the student the enrollment is in place.', moment: 'note' },
  { title: 'The course opens', detail: 'Learning Management grants access to the course.', moment: 'course' },
  { title: 'Dashboards move', detail: 'The insight layer updates the pictures leaders and offices rely on.', moment: 'board' },
] as const;

export const externalSystems = [
  'Payment Gateway',
  'Government Portals',
  'Email and SMS providers',
  'BI and analytics tools',
  'Other institution systems',
];

export const pillars = [
  { title: 'Unified Data', copy: 'One source of truth for the people and the work of the institution.' },
  { title: 'Intelligent Automation', copy: 'Smarter workflows, so routine steps do not wait on a chase.' },
  { title: 'Real-time Insights', copy: 'Decisions made from the campus as it is, not from last month’s export.' },
  { title: 'Secure & Compliant', copy: 'Privacy, governance, and a record of what was done.' },
  { title: 'Scalable & Extensible', copy: 'An architecture ready for the next college, and the next decade.' },
];
