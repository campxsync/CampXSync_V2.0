import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#F6F8FB] px-6 text-[#1B2433]">
      <div className="max-w-lg">
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[#1F6FEB]">CampXSync</p>
        <h1 className="mt-4 text-4xl font-semibold text-[#0B1F3A]">Page not available</h1>
        <p className="mt-4 text-[#3E4C63]">This public site currently includes the home page only.</p>
        <Link href="/" className="mt-8 inline-flex text-sm font-semibold text-[#0B1F3A] underline underline-offset-4">
          Return to the home page
        </Link>
      </div>
    </main>
  );
}
