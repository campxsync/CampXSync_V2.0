import type { Metadata } from 'next';
import { CampXSyncLandingPage } from '../../../../frontend/src/components/landing/campx-landing-page';

export const metadata: Metadata = {
  title: 'CampXSync — the college operating record',
  description:
    'CampXSync keeps one institute’s colleges apart. Attendance follows the timetable. A changed mark stays on the ledger with a reason. Platform, college, faculty, and student each have their own workspace.',
  alternates: { canonical: '/' },
  openGraph: {
    title: 'CampXSync — the college operating record',
    description:
      'Attendance follows the timetable. Each college stays inside its institute. Corrections keep their reason.',
    url: 'https://campxsync.com',
    siteName: 'CampXSync',
    type: 'website',
  },
};

export default function HomePage() {
  return <CampXSyncLandingPage />;
}
