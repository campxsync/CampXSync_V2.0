export const SITE = {
  name: 'CampXSync',
  url: 'https://campxsync.com',
  title: 'CampXSync — the college operating record',
  description:
    'CampXSync is the operating record for one college. Students see their timetable, attendance, fee receipt, and published marks. Faculty mark the class. The college office keeps fees, exams, staff, one campus, and notices.',
} as const;

export const ANSWERS: { question: string; answer: string }[] = [
  {
    question: 'What is CampXSync?',
    answer:
      'CampXSync is the operating record for one college: roster, timetable, attendance, office fees, examinations, staff, one campus, and in-app notices.',
  },
  {
    question: 'What does a student see?',
    answer:
      'The student workspace shows the programme, timetable, attendance, fee demand, and a published subject result with marks and pass or fail.',
  },
  {
    question: 'What does a teacher do?',
    answer:
      'Faculty mark attendance for the scheduled class and can correct a mark with a reason. The college office creates the students.',
  },
  {
    question: 'Who runs the college record?',
    answer:
      'The college office creates faculty, students, the academic year, fees taken at the office, examinations, staff pay, the campus, and notices.',
  },
  {
    question: 'What does a published result show?',
    answer: 'A published result shows the subject marks and whether the student passed.',
  },
  {
    question: 'How are college fees recorded?',
    answer: 'The fee receipt is taken at the college office and kept against that student’s fee demand.',
  },
];
