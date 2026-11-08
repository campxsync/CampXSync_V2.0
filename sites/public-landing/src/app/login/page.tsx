import Link from 'next/link';
import { Logo } from '../../../../../frontend/src/components/landing/logo';

export default function LoginPlaceholderPage() {
  return (
    <main className="min-h-screen bg-[#070B14] text-[#F8FAFC] flex items-center justify-center px-6 py-16">
      <div className="w-full max-w-lg glass-panel rounded-3xl p-8 sm:p-10">
        <Logo />
        <h1 className="font-display text-3xl font-extrabold tracking-tight text-white mt-8">
          Workspace sign-in is not open yet
        </h1>
        <p className="mt-4 text-base leading-relaxed text-slate-300">
          This address currently publishes the CampXSync landing page only. Institutional
          sign-in opens when the platform server is connected.
        </p>
        <Link
          href="/"
          className="mt-8 inline-flex items-center justify-center rounded-xl bg-gradient-to-r from-rose-500 via-rose-600 to-amber-500 px-5 py-3 text-sm font-bold text-white focus-ring"
        >
          Back to CampXSync
        </Link>
      </div>
    </main>
  );
}
