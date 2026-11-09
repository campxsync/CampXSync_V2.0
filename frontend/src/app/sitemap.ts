import type { MetadataRoute } from 'next';

function siteUrl() {
  if (process.env.NEXT_PUBLIC_SITE_URL) return process.env.NEXT_PUBLIC_SITE_URL.replace(/\/$/, '');
  if (process.env.VERCEL_URL) return `https://${process.env.VERCEL_URL}`;
  return 'http://localhost:3000';
}

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: `${siteUrl()}/`, changeFrequency: 'monthly', priority: 1 },
    { url: `${siteUrl()}/login`, changeFrequency: 'yearly', priority: 0.4 },
  ];
}
