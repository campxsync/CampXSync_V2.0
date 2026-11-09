import type { MetadataRoute } from 'next';

export const dynamic = 'force-static';

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: 'https://campxsync.com/', changeFrequency: 'monthly', priority: 1 },
    { url: 'https://campxsync.com/login', changeFrequency: 'yearly', priority: 0.3 },
  ];
}
