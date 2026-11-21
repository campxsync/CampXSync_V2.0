import { redirect } from 'next/navigation';

/** Status catalog retired — Identity & Access is the live desk. */
export default function PlatformIadmRedirectPage() {
  redirect('/platform/identity');
}
