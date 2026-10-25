import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'CampXSync — Higher Education Operating System & Academic Governance',
  description: 'Enterprise operations platform for universities. Real-time timetable constraint satisfaction, role-isolated academic cockpits, and cryptographic institutional audit trails.',
  icons: {
    icon: '/favicon.ico',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark" suppressHydrationWarning>
      <body className="min-h-screen bg-[#070B14] text-[#F8FAFC] font-sans antialiased selection:bg-cyan-500/30 selection:text-cyan-200">
        {children}
      </body>
    </html>
  );
}
