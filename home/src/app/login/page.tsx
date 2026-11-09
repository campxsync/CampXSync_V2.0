import type { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = {
  title: 'Workspace sign-in',
  description: 'Workspace sign-in is not hosted on the public CampXSync page. The home page explains the college record.',
  robots: { index: false, follow: true },
  alternates: { canonical: '/' },
};

export default function LoginPlaceholderPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#F6F8FB] px-6 py-16 text-[#1B2433]">
      <div className="w-full max-w-lg border border-[#E3E8EF] bg-white p-8 sm:p-10">
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[#1F6FEB]">CampXSync</p>
        <h1 className="mt-4 text-3xl font-semibold tracking-tight text-[#0B1F3A]">
          Workspace sign-in is not open on this site yet
        </h1>
        <p className="mt-4 text-base leading-relaxed text-[#3E4C63]">
          This address publishes the CampXSync home page. Institutional sign-in opens when the platform server is connected.
        </p>
        <Link
          href="/"
          className="mt-8 inline-flex h-11 items-center rounded-md bg-[#0B1F3A] px-5 text-sm font-semibold text-white hover:bg-[#12315C]"
        >
          Back to CampXSync
        </Link>
      </div>
    </main>
  );
}
