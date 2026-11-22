import { InstitutionHomeClient } from './institution-home-client';

export const dynamic = 'force-dynamic';

/** Institution Admin (R03) minimal home — IADM01-US-005. */
export default function InstitutionHomePage() {
  return <InstitutionHomeClient />;
}
