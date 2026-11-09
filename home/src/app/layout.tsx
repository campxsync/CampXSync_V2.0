import type { Metadata } from 'next';
import './globals.css';
import { ANSWERS, SITE } from '@/content/site-facts';

export const metadata: Metadata = {
  metadataBase: new URL(SITE.url),
  title: {
    default: SITE.title,
    template: '%s · CampXSync',
  },
  description: SITE.description,
  keywords: [
    'college operating record',
    'college timetable',
    'student attendance',
    'college examinations',
    'college fee receipt',
    'CampXSync',
  ],
  alternates: { canonical: '/' },
  openGraph: {
    title: 'CampXSync — the college operating record',
    description:
      'One college record for roster, timetable, attendance, office fees, examinations, staff, one campus, and in-app notices.',
    url: 'https://campxsync.com',
    siteName: 'CampXSync',
    type: 'website',
    locale: 'en_IN',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'CampXSync — the college operating record',
    description:
      'Students, faculty, and the college admin share one record, from roster to published result.',
  },
  robots: { index: true, follow: true },
};

const jsonLd = {
  '@context': 'https://schema.org',
  '@graph': [
    {
      '@type': 'Organization',
      name: SITE.name,
      url: SITE.url,
      description: SITE.description,
    },
    {
      '@type': 'WebSite',
      name: SITE.name,
      url: SITE.url,
      description: SITE.description,
      inLanguage: 'en',
    },
    {
      '@type': 'WebPage',
      name: SITE.title,
      url: SITE.url,
      description: SITE.description,
      isPartOf: SITE.url,
      about: 'College operating record for students, faculty, and the college office.',
    },
    {
      '@type': 'SoftwareApplication',
      name: SITE.name,
      applicationCategory: 'EducationalApplication',
      operatingSystem: 'Web',
      url: SITE.url,
      description: SITE.description,
    },
    {
      '@type': 'FAQPage',
      mainEntity: ANSWERS.map((item) => ({
        '@type': 'Question',
        name: item.question,
        acceptedAnswer: { '@type': 'Answer', text: item.answer },
      })),
    },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-[#F6F8FB] text-[#1B2433] antialiased">
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
        {children}
      </body>
    </html>
  );
}
