import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  metadataBase: new URL('https://campxsync.com'),
  title: 'CampXSync — Remember. Understand. Act. Evolve.',
  description:
    'CampXSync is the campus platform for students, faculty, colleges, and the institute. One system for academic life, campus operations, and shared intelligence.',
  openGraph: {
    title: 'CampXSync — Remember. Understand. Act. Evolve.',
    description:
      'Intelligently connecting education. Today. Tomorrow. Together.',
    url: 'https://campxsync.com',
    siteName: 'CampXSync',
    type: 'website',
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-white text-[#12172a] font-sans antialiased">
        {children}
      </body>
    </html>
  );
}
