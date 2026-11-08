import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  metadataBase: new URL('https://campxsync.com'),
  title: 'CampXSync — Higher Education Operations, Unified In Real Time.',
  description:
    'CampXSync eliminates operational silos across curricula, automated timetables, and campus operations.',
  openGraph: {
    title: 'CampXSync — Higher Education Operations, Unified In Real Time.',
    description:
      'CampXSync eliminates operational silos across curricula, automated timetables, and campus operations.',
    url: 'https://campxsync.com',
    siteName: 'CampXSync',
    type: 'website',
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-[#070B14] text-[#F8FAFC] font-sans antialiased">
        {children}
      </body>
    </html>
  );
}
