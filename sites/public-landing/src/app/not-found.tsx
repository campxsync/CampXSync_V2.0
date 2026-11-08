import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="min-h-screen bg-[#070B14] text-[#F8FAFC] flex items-center justify-center px-6">
      <div className="max-w-lg text-center">
        <p className="text-xs font-bold uppercase tracking-widest text-cyan-400">CampXSync</p>
        <h1 className="font-display text-4xl font-extrabold text-white mt-4">Page not available</h1>
        <p className="mt-4 text-slate-300">This public site currently includes the landing page only.</p>
        <Link href="/" className="mt-8 inline-flex text-sm font-semibold text-cyan-300 hover:text-cyan-200">
          Return to the landing page
        </Link>
      </div>
    </main>
  );
}
