# Known Pitfalls & Hard-Won Solutions (9–13 Nov 2026)

This document catalogs technical traps, database invariants, framework quirks, and platform-specific behaviors encountered during the 9–13 November 2026 delivery sprint across Next.js 15, Java domain microservices, PostgreSQL (Supabase), and Windows dev environments.

---

## 1. Database & Schema Invariants

### 1.1 `stu.lifecycle_history.action` Character Limit (SQL 22001)
* **Trap:** The column `action` on `stu.lifecycle_history` is defined as `VARCHAR(64)`. When passing compound strings such as `hod.endorsement:RECOMMENDED_WITH_RESERVATIONS:Academic review completed pending grade reconciliation`, PostgreSQL throws `ERROR: value too long for type character varying(64)` (SQL state 22001).
* **Fix:** The action column must contain only the canonical action identifier (e.g. `hod.endorsement:RECOMMENDED_WITH_RESERVATIONS` or `hod.endorsement:NOT_RECOMMENDED`). Detailed remarks, rationales, and academic advisor notes must be appended to the corresponding `stu.lifecycle_requests.reason` text column.
* **Evidence:** [LifecycleDomainService.java](file:///d:/CampXSync/CampXSync_V2.0/services/student-services/lifecycle-service/src/main/java/com/campx/lifecycle/LifecycleDomainService.java#L280-L310)

### 1.2 Append-Only Triggers on `doc.document_versions` and `audit.events`
* **Trap:** The tables `doc.document_versions` and `audit.events` have immutable audit triggers installed in PostgreSQL. Any SQL `UPDATE` or `DELETE` executed against them throws an immediate exception: `ERROR: Table doc.document_versions is append-only`.
* **Fix:** Never attempt to modify or purge versions. Archival or superseding of documents must be performed by inserting a new row into `doc.document_versions` with an incremented version number, or by updating `doc.documents.status = 'ARCHIVED'`. In automated test cleanup scripts, never issue `DELETE FROM doc.document_versions` or `DELETE FROM audit.events`.
* **Evidence:** [schema_snapshot.sql](file:///d:/CampXSync/CampXSync_V2.0/docs/engineering/schema_snapshot.sql)

### 1.3 `mime_type` Location in Document Service
* **Trap:** Attempting to query `doc.document_versions.mime_type` fails because `mime_type` is stored on the parent table `doc.documents`, while `doc.document_versions` stores `storage_path`, `file_size_bytes`, `checksum_sha256`, and `created_at`.
* **Fix:** Always perform a join between `doc.documents` and `doc.document_versions` when retrieving document metadata for downloads or UI rendering.
* **Evidence:** [DocumentMetadata.java](file:///d:/CampXSync/CampXSync_V2.0/services/student-services/document-service/src/main/java/com/campx/document/DocumentDomainService.java)

### 1.4 Student Lifecycle Status Transition State Machine
* **Trap:** Calling `approveRequest()` or `submitRequest()` with an incompatible current status results in a domain exception (`Leave is only allowed from ACTIVE status`).
* **Invariants:**
  * `LEAVE` request: Student must be in `ACTIVE` standing.
  * `TRANSFER` request: Student must be in `ACTIVE` standing.
  * `REINSTATE` request: Student must be in `ON_LEAVE` standing.
  * `WITHDRAWAL` request: Student can be `ACTIVE` or `ON_LEAVE`.
* **UI Defense:** In the College Lifecycle Review Desk (`frontend/src/app/(dashboard)/college/lifecycle/page.tsx`), the "Approve" button is conditionally disabled with a tooltip explaining standing prerequisites if the student's current status conflicts with the requested transition. The "Reject" button remains enabled so registrars can clear invalid requests.

---

## 2. Frontend & Next.js 15 Quirks

### 2.1 Browser Extension Hydration Mismatches (`fdprocessedid`)
* **Trap:** Chrome password managers and autofill extensions (e.g., Bitwarden, 1Password, Dashlane) inject attributes like `fdprocessedid="..."` into `<button>` or `<input>` tags before React completes hydration. Next.js 15 throws a client-side hydration error and causes visible UI flicker or crashes.
* **Fix:** In client components rendering interactive buttons (such as `topbar.tsx` or form actions), either gate rendering behind a `mounted` state (`useEffect(() => setMounted(true), [])`) or add `suppressHydrationWarning={true}` to interactive elements exposed to extension injection.
* **Evidence:** [topbar.tsx](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/components/layout/topbar.tsx)

### 2.2 Next.js Route Cache & Turbopack File Locks on Windows
* **Trap:** Rapidly recompiling or rebuilding frontend pages while the Next.js dev server is running can cause `EPERM: operation not permitted` on `.next/cache` or `.next/trace` on Windows.
* **Fix:** When strange caching or bundle corruption occurs, stop the frontend task, execute `powershell -Command "Remove-Item -Recurse -Force frontend/.next"`, and restart `scripts/dev/start_stack.ps1`.

### 2.3 Frontend Direct Database Access Invariant
* **Trap:** Importing `@supabase/supabase-js` or executing SQL directly in Next.js Server Actions or Server Components bypasses Java business domain checks, audit trails, and the API gateway.
* **Invariant:** Frontend code MUST ONLY invoke API routes (`frontend/src/app/api/...`), which in turn proxy requests through API Gateway (`http://localhost:8080`) to Java microservices. Direct Supabase DB connections from the frontend are prohibited for domain mutations.

---

## 3. Windows & PowerShell Tooling

### 3.1 Parentheses in File Paths
* **Trap:** Next.js route groups use folder names with parentheses: `frontend/src/app/(dashboard)/college/lifecycle/page.tsx`. In PowerShell, unquoted parentheses are interpreted as subexpression operators:
  `cat frontend/src/app/(dashboard)/...` throws a syntax error.
* **Fix:** ALWAYS wrap paths containing parentheses in single or double quotes:
  `Get-Content "frontend/src/app/(dashboard)/college/lifecycle/page.tsx"`

### 3.2 Maven `-D` Parameter Quoting
* **Trap:** In PowerShell, unquoted arguments like `-Dtest=LifecycleDomainServiceTest` can be parsed incorrectly or stripped by the PowerShell parser before reaching `mvn.cmd`.
* **Fix:** Always wrap Maven system property arguments in quotes:
  `mvn test -pl services/student-services/lifecycle-service "-Dtest=LifecycleDomainServiceTest"`

### 3.3 Node.js & Java Memory Footprint Caps
* **Trap:** Running 5 microservices alongside Next.js on a Windows developer laptop can easily exhaust memory, leading to swapping or JVM OOM.
* **Fix:**
  * All Java microservices are configured with strict JVM heap limits in `scripts/dev/start_stack.ps1`: `-Xms32m -Xmx96m`.
  * Node.js / Next.js runs with standard memory limits.
  * API Gateway runs with `-Xms32m -Xmx64m`.
  * Total stack memory consumption remains under 1.2 GB RAM.

---

## 4. Test Harness & Automation

### 4.1 Playwright / Demo Runner Network Idle Threshold
* **Trap:** Setting `waitForLoadState('networkidle')` in automated demo runners can hang indefinitely if background analytics, long-polling, or WebSocket connections are active.
* **Fix:** In `scripts/demo/harness.mjs`, use a 500 ms debounce for network settling and fall back to explicit `waitForSelector('[data-testid="..."]')` rather than waiting for global network idle.

### 4.2 Seed Scripts Idempotency
* **Trap:** Running demo seed scripts repeatedly creates duplicate records or triggers unique constraint violations (`uq_student_number`, `uq_registration_no`).
* **Fix:** Seed scripts (e.g. `scripts/dev/seed_us003_demo.mjs`) must use upsert semantics or check for existing records (`SELECT id FROM stu.students WHERE student_number = ...`) before inserting test personas.
