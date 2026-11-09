# ReviewBranch page register

Every tracked Next.js page is listed. Source inspection and build coverage do not establish live database or browser correctness.

| Route | Source | Implemented evidence | Remaining validation |
|---|---|---|---|
| `/login` | [page.tsx](../../../frontend/src/app/(auth)/login/page.tsx) | UI/auth/routing implemented; browser journey unverified | Real auth/email recovery requires configured Supabase |
| `/reset-password` | [page.tsx](../../../frontend/src/app/(auth)/reset-password/page.tsx) | UI/auth/routing implemented; browser journey unverified | Real auth/email recovery requires configured Supabase |
| `/college/attendance` | [page.tsx](../../../frontend/src/app/(dashboard)/college/attendance/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/college/audit` | [page.tsx](../../../frontend/src/app/(dashboard)/college/audit/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/college/batches` | [page.tsx](../../../frontend/src/app/(dashboard)/college/batches/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/college/departments` | [page.tsx](../../../frontend/src/app/(dashboard)/college/departments/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/college/faculty` | [page.tsx](../../../frontend/src/app/(dashboard)/college/faculty/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/college` | [page.tsx](../../../frontend/src/app/(dashboard)/college/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/college/students` | [page.tsx](../../../frontend/src/app/(dashboard)/college/students/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/college/timetable` | [page.tsx](../../../frontend/src/app/(dashboard)/college/timetable/page.tsx) | Server page with database queries; live data unverified | Verify college scope within shared tenants |
| `/faculty/attendance` | [page.tsx](../../../frontend/src/app/(dashboard)/faculty/attendance/page.tsx) | Server page with database queries; live data unverified | Enforce employee/section assignment, not only tenant |
| `/faculty` | [page.tsx](../../../frontend/src/app/(dashboard)/faculty/page.tsx) | Server page with database queries; live data unverified | Enforce employee/section assignment, not only tenant |
| `/faculty/sessions` | [page.tsx](../../../frontend/src/app/(dashboard)/faculty/sessions/page.tsx) | Server page with database queries; live data unverified | Enforce employee/section assignment, not only tenant |
| `/faculty/timetable` | [page.tsx](../../../frontend/src/app/(dashboard)/faculty/timetable/page.tsx) | Server page with database queries; live data unverified | Enforce employee/section assignment, not only tenant |
| `/platform/audit` | [page.tsx](../../../frontend/src/app/(dashboard)/platform/audit/page.tsx) | Server page with database queries; live data unverified | Check role denial, empty/error states, accessibility |
| `/platform/institutes/new` | [page.tsx](../../../frontend/src/app/(dashboard)/platform/institutes/new/page.tsx) | UI/auth/routing implemented; browser journey unverified | Check role denial, empty/error states, accessibility |
| `/platform/institutes` | [page.tsx](../../../frontend/src/app/(dashboard)/platform/institutes/page.tsx) | Server page with database queries; live data unverified | Check role denial, empty/error states, accessibility |
| `/platform` | [page.tsx](../../../frontend/src/app/(dashboard)/platform/page.tsx) | Server page with database queries; live data unverified | Check role denial, empty/error states, accessibility |
| `/student` | [page.tsx](../../../frontend/src/app/(dashboard)/student/page.tsx) | Server page with database queries; live data unverified | Database-backed attendance exists; earlier static-only description is stale |
| `/access-denied` | [page.tsx](../../../frontend/src/app/access-denied/page.tsx) | UI/auth/routing implemented; browser journey unverified | Check role denial, empty/error states, accessibility |
| `/` | [page.tsx](../../../frontend/src/app/page.tsx) | UI/auth/routing implemented; browser journey unverified | Check role denial, empty/error states, accessibility |
