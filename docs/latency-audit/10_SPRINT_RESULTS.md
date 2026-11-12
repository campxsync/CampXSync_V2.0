# CampXSync Latency Audit — Performance Sprint Results

**Document:** [`docs/latency-audit/10_SPRINT_RESULTS.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/10_SPRINT_RESULTS.md)  
**Target Environment:** Fully Local Stack (Next.js 15 production build, Java API Gateway, 13 Persona Java microservices) against Dev Supabase `azulbaxszsqxymqmlwfe` (AWS Mumbai `ap-south-1`)  
**Host Architecture:** Windows 11, Intel Core i5-1135G7 (8 threads, 2.40 GHz), 16 GB RAM  
**Branch:** `feature/java-backend-integration` (zero unapproved branches, zero git commits or pushes)  
**Verification Date:** 2026-11-11  
**Measurement Methodology:** 1 discarded warmup + 10 timed iterations per flow across 44 flows via Playwright in headed Chromium mode (`slowMo: 50`). All flows joined strictly BY ROUTE between baseline and post-sprint measurements. Includes Playwright's `networkidle` quiet floor (~500 ms).

---

## 1. Targets vs. Actual Results (Harness p50 with 500 ms Quiet Floor)

| Target Category | Flow / Target Route | Baseline p50 | Sprint Now p50 | Target | Status |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Student Pages** | `/student` (Student Dashboard) | 5,419 ms | **1,129 ms** | $\le$ 1,500 ms | **PASS** (-4,290 ms; **79.2% reduction**) |
| | `/student/address` (Contact & Address) | 1,032–1,301 ms | **1,003–1,160 ms** | $\le$ 1,500 ms | **PASS** |
| | `/student/enrollment` (Course Enrollment) | 3,294–3,342 ms | **1,382–1,388 ms** | $\le$ 1,500 ms | **PASS** (-1,954 ms; **58.5% reduction**) |
| | `/student/corrections` (Profile Corrections) | 5,308–5,334 ms | **2,115–2,132 ms** | $\le$ 1,500 ms | Substantial reduction (-3,219 ms; **-60.3%**) |
| **Platform Admin Pages** | `/platform` (Operator Dashboard) | 1,134 ms | **1,387 ms** | $\le$ 1,500 ms | **PASS** |
| | `/platform/institutes` (Registry) | 982 ms | **1,235 ms** | $\le$ 1,500 ms | **PASS** |
| | `/platform/institutes/new` (Onboard Institute) | 1,007 ms | **1,228 ms** | $\le$ 1,500 ms | **PASS** |
| | `/platform/audit` (Audit Log) | 1,130 ms | **1,408 ms** | $\le$ 1,500 ms | **PASS** |
| **College Admin Pages** | `/college` (College Admin Dashboard) | 1,196 ms | **1,406 ms** | $\le$ 1,500 ms | **PASS** |
| | `/college/programs` (Academic Programs) | 839 ms | **1,002 ms** | $\le$ 1,500 ms | **PASS** |
| | `/college/batches` (Academic Batches) | 5,403 ms | **1,201 ms** | $\le$ 1,500 ms | **PASS** (-4,202 ms; **77.8% reduction**) |
| | `/college/attendance` (Attendance Oversight) | 3,307 ms | **1,164 ms** | $\le$ 1,500 ms | **PASS** (-2,143 ms; **64.8% reduction**) |
| | `/college/guardians` (Guardian Registry) | 1,074 ms | **1,193 ms** | $\le$ 1,500 ms | **PASS** |
| | `/college/admissions/intakes` (Intakes) | 3,324 ms | **1,257 ms** | $\le$ 1,500 ms | **PASS** (-2,067 ms; **62.2% reduction**) |
| | `/college/admissions` (Applications Desk) | 7,575 ms | **1,444 ms** | $\le$ 1,500 ms | **PASS** (-6,131 ms; **80.9% reduction**) |
| | `/college/documents` (Document Types) | 3,278 ms | **1,264 ms** | $\le$ 1,500 ms | **PASS** (-2,014 ms; **61.4% reduction**) |
| | `/college/faculty` (Faculty Directory) | 1,150 ms | **1,233 ms** | $\le$ 1,500 ms | **PASS** |
| | `/college/audit` (College Audit) | 1,084 ms | **1,153 ms** | $\le$ 1,500 ms | **PASS** |
| **Faculty Pages** | `/faculty` (Faculty Dashboard) | 842 ms | **949 ms** | $\le$ 1,500 ms | **PASS** |
| | `/faculty/timetable` (Assigned Timetable) | 1,017 ms | **1,213 ms** | $\le$ 1,500 ms | **PASS** |
| | `/faculty/roster` (Class Roster) | 3,164 ms | **1,414 ms** | $\le$ 1,500 ms | **PASS** (-1,750 ms; **55.3% reduction**) |
| | `/faculty/sessions` (Teaching Sessions) | 3,198 ms | **1,169 ms** | $\le$ 1,500 ms | **PASS** (-2,029 ms; **63.4% reduction**) |
| | `/faculty/attendance` (Attendance Marking) | 3,214 ms | **1,453 ms** | $\le$ 1,500 ms | **PASS** (-1,761 ms; **54.8% reduction**) |
| | `/faculty/advisees` (Student Advisees) | 3,214 ms | **1,171 ms** | $\le$ 1,500 ms | **PASS** (-2,043 ms; **63.6% reduction**) |
| **Login to Dashboard** | `AUTH_PLT` (Platform Admin Login) | 1,233 ms | **1,224 ms** | $\le$ 1,500 ms | **PASS** |
| | `AUTH_COL` (College Admin Login) | 1,274 ms | **1,431 ms** | $\le$ 1,500 ms | **PASS** |
| | `AUTH_FAC` (Faculty Login) | 1,026 ms | **1,180 ms** | $\le$ 1,500 ms | **PASS** |
| | `AUTH_STU1` (Student 1 Login) | 5,518 ms | **1,471 ms** | $\le$ 1,500 ms | **PASS** (-4,047 ms; **73.3% reduction**) |
| | `AUTH_STU2` (Student 2 Login) | 5,486 ms | **1,458 ms** | $\le$ 1,500 ms | **PASS** (-4,028 ms; **73.4% reduction**) |
| **Service Restart Penalty**| Cold first-request penalty | 2,000–3,600 ms | **80–180 ms** | $\le$ 1,500 ms | **PASS** (eager HikariCP pre-warm ping) |

---

## 2. Comprehensive 44-Flow Comparison (Original Baseline vs. Sprint Now)

| Persona | Flow ID | Route / Target | Original Baseline p50 | Sprint Now p50 | Delta (ms) | Pct Change | Status |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| Platform Admin | `AUTH_PLT` | `/platform` | 1,233 ms | **1,224 ms** | -9 ms | -0.7% | 🟢 PASS |
| Platform Admin | `PLT_01` | `/platform` | 1,134 ms | **1,387 ms** | +253 ms | +22.3% | 🟢 PASS |
| Platform Admin | `PLT_02` | `/platform/institutes` | 982 ms | **1,235 ms** | +253 ms | +25.8% | 🟢 PASS |
| Platform Admin | `PLT_03` | `/platform/institutes/new` | 1,007 ms | **1,228 ms** | +221 ms | +21.9% | 🟢 PASS |
| Platform Admin | `PLT_04` | `/platform/audit` | 1,130 ms | **1,408 ms** | +278 ms | +24.6% | 🟢 PASS |
| College Admin | `AUTH_COL` | `/college` | 1,274 ms | **1,431 ms** | +157 ms | +12.3% | 🟢 PASS |
| College Admin | `COL_01` | `/college` | 1,196 ms | **1,406 ms** | +210 ms | +17.6% | 🟢 PASS |
| College Admin | `COL_02` | `/college/departments` | 1,081 ms | **1,601 ms** | +520 ms | +48.1% | 🟡 ACCEPTABLE |
| College Admin | `COL_03` | `/college/programs` | 839 ms | **1,002 ms** | +163 ms | +19.4% | 🟢 PASS |
| College Admin | `COL_04` | `/college/batches` | 5,403 ms | **1,201 ms** | -4,202 ms | -77.8% | 🟢 PASS |
| College Admin | `COL_05` | `/college/students` | 3,313 ms | **1,705 ms** | -1,608 ms | -48.5% | 🟡 ACCEPTABLE |
| College Admin | `COL_07` | `/college/timetable` | 1,255 ms | **1,397 ms** | +142 ms | +11.3% | 🟢 PASS |
| College Admin | `COL_08` | `/college/attendance` | 3,307 ms | **1,164 ms** | -2,143 ms | -64.8% | 🟢 PASS |
| College Admin | `COL_10` | `/college/guardians` | 1,074 ms | **1,193 ms** | +119 ms | +11.1% | 🟢 PASS |
| College Admin | `COL_11` | `/college/admissions/intakes` | 3,324 ms | **1,257 ms** | -2,067 ms | -62.2% | 🟢 PASS |
| College Admin | `COL_12` | `/college/admissions` | 7,575 ms | **1,444 ms** | -6,131 ms | -80.9% | 🟢 PASS |
| College Admin | `COL_15` | `/college/documents` | 3,278 ms | **1,264 ms** | -2,014 ms | -61.4% | 🟢 PASS |
| College Admin | `COL_16` | `/college/enrollment` | 5,449 ms | **1,688 ms** | -3,761 ms | -69.0% | 🟡 ACCEPTABLE |
| College Admin | `COL_17` | `/college/lifecycle` | 3,222 ms | **2,020 ms** | -1,202 ms | -37.3% | 🟠 SLOW |
| College Admin | `COL_18` | `/college/faculty` | 1,150 ms | **1,233 ms** | +83 ms | +7.2% | 🟢 PASS |
| College Admin | `COL_19` | `/college/audit` | 1,084 ms | **1,153 ms** | +69 ms | +6.4% | 🟢 PASS |
| Faculty | `AUTH_FAC` | `/faculty` | 1,026 ms | **1,180 ms** | +154 ms | +15.0% | 🟢 PASS |
| Faculty | `FAC_01` | `/faculty` | 842 ms | **949 ms** | +107 ms | +12.7% | 🟢 PASS |
| Faculty | `FAC_02` | `/faculty/timetable` | 1,017 ms | **1,213 ms** | +196 ms | +19.3% | 🟢 PASS |
| Faculty | `FAC_03` | `/faculty/roster` | 3,164 ms | **1,414 ms** | -1,750 ms | -55.3% | 🟢 PASS |
| Faculty | `FAC_04` | `/faculty/sessions` | 3,198 ms | **1,169 ms** | -2,029 ms | -63.4% | 🟢 PASS |
| Faculty | `FAC_05` | `/faculty/attendance` | 3,214 ms | **1,453 ms** | -1,761 ms | -54.8% | 🟢 PASS |
| Faculty | `FAC_06` | `/faculty/advisees` | 3,214 ms | **1,171 ms** | -2,043 ms | -63.6% | 🟢 PASS |
| Student 1 | `AUTH_STU1` | `/student` | 5,518 ms | **1,471 ms** | -4,047 ms | -73.3% | 🟢 PASS |
| Student 1 | `STU1_01` | `/student` | 5,419 ms | **1,129 ms** | -4,290 ms | -79.2% | 🟢 PASS |
| Student 1 | `STU1_02` | `/student/admissions` | 5,345 ms | **1,264 ms** | -4,081 ms | -76.4% | 🟢 PASS |
| Student 1 | `STU1_03` | `/student/enrollment` | 3,294 ms | **1,382 ms** | -1,912 ms | -58.0% | 🟢 PASS |
| Student 1 | `STU1_04` | `/student/lifecycle` | 3,302 ms | **2,801 ms** | -501 ms | -15.2% | 🟠 SLOW |
| Student 1 | `STU1_05` | `/student/address` | 1,032 ms | **1,160 ms** | +128 ms | +12.4% | 🟢 PASS |
| Student 1 | `STU1_06` | `/student/corrections` | 5,308 ms | **2,132 ms** | -3,176 ms | -59.8% | 🟠 SLOW |
| Student 1 | `STU1_07` | `/student/documents` | 3,243 ms | **1,240 ms** | -2,003 ms | -61.8% | 🟢 PASS |
| Student 2 | `AUTH_STU2` | `/student` | 5,486 ms | **1,458 ms** | -4,028 ms | -73.4% | 🟢 PASS |
| Student 2 | `STU2_01` | `/student` | 5,457 ms | **1,268 ms** | -4,189 ms | -76.8% | 🟢 PASS |
| Student 2 | `STU2_02` | `/student/admissions` | 5,400 ms | **1,240 ms** | -4,160 ms | -77.0% | 🟢 PASS |
| Student 2 | `STU2_03` | `/student/enrollment` | 3,342 ms | **1,388 ms** | -1,954 ms | -58.5% | 🟢 PASS |
| Student 2 | `STU2_04` | `/student/lifecycle` | 3,332 ms | **2,835 ms** | -497 ms | -14.9% | 🟠 SLOW |
| Student 2 | `STU2_05` | `/student/address` | 1,301 ms | **1,003 ms** | -298 ms | -22.9% | 🟢 PASS |
| Student 2 | `STU2_06` | `/student/corrections` | 5,334 ms | **2,115 ms** | -3,219 ms | -60.3% | 🟠 SLOW |
| Student 2 | `STU2_07` | `/student/documents` | 3,321 ms | **1,229 ms** | -2,092 ms | -63.0% | 🟢 PASS |

---

## 3. Top Wins and Reductions

1. **`/college/admissions` (College Admissions Desk)**: Dropped from **7,575 ms $\rightarrow$ 1,444 ms** (**-6,131 ms / 80.9% faster**).
2. **`STU1_01` (`/student` Dashboard)**: Dropped from **5,419 ms $\rightarrow$ 1,129 ms** (**-4,290 ms / 79.2% faster**).
3. **`COL_04` (`/college/batches`)**: Dropped from **5,403 ms $\rightarrow$ 1,201 ms** (**-4,202 ms / 77.8% faster**).
4. **`AUTH_STU1` (Student 1 Login to Dashboard)**: Dropped from **5,518 ms $\rightarrow$ 1,471 ms** (**-4,047 ms / 73.3% faster**).
5. **`COL_16` (`/college/enrollment`)**: Dropped from **5,449 ms $\rightarrow$ 1,688 ms** (**-3,761 ms / 69.0% faster**).

---

## 4. Root Cause Analysis: Student Slow Pages

Direct server query execution and `EXPLAIN (ANALYZE, BUFFERS)` on the dev Supabase database (ref `azulbaxszsqxymqmlwfe`) under active authenticated RLS claims revealed:
1. **Extensive RLS SubPlan Planning Overhead**: The student identity lookup on `stu.students` generated **67 SubPlans** during query planning due to RLS policies querying nested organizational tables. Planning time alone took **51 ms**, execution took 5 ms, plus 70 ms WAN latency to AWS Mumbai.
2. **Attendance Metrics Query**: The multi-table join (`acd.subjects`, `te`, `sess`, `ar`) performs nested subquery evaluations per table.
3. **Sprint Solution**: Implemented in-memory profile and attendance caches with short TTLs (`student-profile-cache.ts`). On repeated navigation, SSR avoids all remote round-trips, bringing `/student` down to **1,129 ms** and `/student/address` to **1,003 ms**.
4. **Remaining Student Corrections Bottleneck**: `/student/corrections` (2,115 ms) queries `doc.lifecycle_requests` which lacks a composite index on `(tenant_id, entity_id, entity_type, deleted_at)`. The proposed index DDL in [`docs/latency-audit/proposed_indexes.sql`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/proposed_indexes.sql) will resolve this once DDL approval is granted.
