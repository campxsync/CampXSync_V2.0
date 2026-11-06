import { query } from '../frontend/src/lib/db.ts';
import { resolveUserRole } from '../frontend/src/lib/auth/resolve-role.ts';

const users = [
  { name: 'College Admin', id: '70a8ffae-b5ad-4c61-a401-16f220893df4', email: 'campxsync@gmail.com' },
  { name: 'Platform Admin', id: '54e8308c-ada3-4065-a9fe-51814af6b971', email: 'platformadmin.campx@gmail.com' },
  { name: 'Faculty', id: '619b460b-d7f9-46b6-b11a-56045a130a52', email: 'faculty.campx@gmail.com' },
];

async function testResolution() {
  console.log('Testing resolveUserRole() against live database directly...');
  for (const u of users) {
    const mockSupabase = {
      auth: {
        getUser: async () => ({
          data: {
            user: {
              id: u.id,
              email: u.email,
              user_metadata: { full_name: u.name },
            },
          },
          error: null,
        }),
      },
    };

    const res = await resolveUserRole(mockSupabase);
    if (res.success) {
      console.log(`[PASS] ${u.name} resolved to role: '${res.session.role}', tenant: '${res.session.tenantName}'`);
    } else {
      console.log(`[FAIL] ${u.name} failed with reason: '${res.reason}'`);
    }
  }
  process.exit(0);
}

testResolution().catch(console.error);
