import type { Metadata } from 'next';
import { CampXSyncLandingPage } from '@/components/landing/campx-landing-page';

export const metadata: Metadata = {
  title: 'CampXSync — the college operating record',
  description:
    'CampXSync is the operating record for one college. Students see timetable, attendance, the office fee receipt, and published marks. Faculty mark the class. The college office keeps exams, staff, one campus, and notices.',
  alternates: { canonical: '/' },
  openGraph: {
    title: 'CampXSync — the college operating record',
    description:
      'One college record for the roster, timetable, attendance, office fees, examinations, staff, one campus, and in-app notices.',
    url: 'https://campxsync.com',
    siteName: 'CampXSync',
    type: 'website',
  },
};

export default function HomePage() {
  return <CampXSyncLandingPage />;
}
