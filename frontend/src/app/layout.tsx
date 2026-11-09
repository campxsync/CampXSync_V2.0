import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'CampXSync — Multi-Tenant Institutional ERP & Academic Governance',
  description: 'Enterprise operations platform for higher education institutions. Timetable-validated attendance, role-isolated workspaces, and audited academic records.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-[#F6F8FB] text-[#1B2433] antialiased">
        {children}
      </body>
    </html>
  );
}
