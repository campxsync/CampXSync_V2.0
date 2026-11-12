# CampXSync Latency Audit — Raw Data & Evidence Index

**Audit Version**: 2.0.0  
**Audit Date**: November 11, 2026  
**Commit SHA**: `ef6e7dbed9dfde7cd492c5b7768542ce958d3a62` (`feature/java-backend-integration`)  
**Target Environment**: Fully Local Stack against Dev Supabase (`azulbaxszsqxymqmlwfe`)  

---

## 1. Environmental Benchmark Context

| Parameter | Recorded Value | Verification Command / Source |
|:---|:---|:---|
| **Host CPU** | Intel(R) Core(TM) i5-1235U (10 Cores / 12 Threads) | `[System.Environment]::ProcessorCount` |
| **Physical RAM** | 15.73 GB Total Physical RAM (Memory Load: 93%) | Windows `GlobalMemoryStatusEx` API |
| **Operating System** | Windows 11 Pro 64-bit (`NT 10.0.26300.0`) | `[System.Environment]::OSVersion` |
| **Java JDK Runtime**| Java SE 1.8.0_202-b08 (HotSpot 64-Bit Server VM) | `java -version` (`C:\Java\jdk\jre`) |
| **Node.js / npm** | Node v22.14.0, npm 10.9.2 | `node -v`, `npm -v` |
| **Apache Maven** | Apache Maven 3.9.9 | `mvn -v` |
| **Browser Harness** | Playwright Chromium (Headed Mode, Visible UI) | `run_audit_measurements.mjs` |
| **Network Interface**| Intel Wi-Fi 6 AX201 160MHz | `Get-NetAdapter` |
| **Network RTT** | `25.06 ms – 29.14 ms` (median: `26.5 ms`) to Supabase Pooler | TCP Connect to AWS Mumbai (`aws-0-ap-south-1.pooler.supabase.com:6543`) |
| **Supabase Cloudflare Edge** | `7.10 ms – 7.94 ms` | Ping / HTTP to `azulbaxszsqxymqmlwfe.supabase.co` |

---

## 2. Integrity & Database Write Attestation

> [!IMPORTANT]
> **READ-ONLY ATTESTATION**: In strict compliance with Hard Boundary 4, **ZERO database writes, zero DDL statements, zero migrations, and zero role/permission modifications** were performed during the execution of this audit. All catalog checks and query timing were performed using `EXPLAIN (ANALYZE, BUFFERS)` on read-only `SELECT` queries. The development database state is completely pristine and unchanged.

---

## 3. Evidence Catalog: Flow Trace JSON Files

All raw telemetry traces from the 10 timed iterations, discarded warmups, client performance observers, TTFB, and network request counts are persisted under `docs/latency-audit/evidence/`:

| Flow ID | Flow Name / Target Route | Trace File | Collected Samples | Action-to-Visible p50 |
|:---|:---|:---|:---:|:---:|
| `AUTH_PLT` | Platform Admin Login to Dashboard (`/platform`) | [`AUTH_PLT.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/AUTH_PLT.json) | 1 warmup + 10 iterations | 1,233 ms |
| `PLT_01` | Platform Admin Dashboard Navigation (`/platform`) | [`PLT_01.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/PLT_01.json) | 1 warmup + 10 iterations | 1,134 ms |
| `PLT_02` | Institutes Registry (`/platform/institutes`) | [`PLT_02.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/PLT_02.json) | 1 warmup + 10 iterations | 982 ms |
| `PLT_03` | Onboard Institute View (`/platform/institutes/new`) | [`PLT_03.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/PLT_03.json) | 1 warmup + 10 iterations | 1,007 ms |
| `PLT_04` | Platform Audit Trail (`/platform/audit`) | [`PLT_04.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/PLT_04.json) | 1 warmup + 10 iterations | 1,130 ms |
| `AUTH_COL` | College Admin Login to Dashboard (`/college`) | [`AUTH_COL.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/AUTH_COL.json) | 1 warmup + 10 iterations | 1,274 ms |
| `COL_01` | College Admin Dashboard (`/college`) | [`COL_01.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_01.json) | 1 warmup + 10 iterations | 1,196 ms |
| `COL_02` | Departments Registry (`/college/departments`) | [`COL_02.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_02.json) | 1 warmup + 10 iterations | 1,081 ms |
| `COL_03` | Academic Programs (`/college/programs`) | [`COL_03.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_03.json) | 1 warmup + 10 iterations | 839 ms |
| `COL_04` | Academic Batches (`/college/batches`) | [`COL_04.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_04.json) | 1 warmup + 10 iterations | 5,403 ms |
| `COL_05` | Student Roster (`/college/students`) | [`COL_05.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_05.json) | 1 warmup + 10 iterations | 3,313 ms |
| `COL_07` | Timetable Management (`/college/timetables`) | [`COL_07.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_07.json) | 1 warmup + 10 iterations | 1,255 ms |
| `COL_08` | Attendance Oversight (`/college/attendance`) | [`COL_08.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_08.json) | 1 warmup + 10 iterations | 3,307 ms |
| `COL_10` | Guardian Registry (`/college/guardians`) | [`COL_10.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_10.json) | 1 warmup + 10 iterations | 1,074 ms |
| `COL_11` | Admissions Intakes (`/college/admissions/intakes`) | [`COL_11.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_11.json) | 1 warmup + 10 iterations | 3,324 ms |
| `COL_12` | Admissions Applications (`/college/admissions`) | [`COL_12.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_12.json) | 1 warmup + 10 iterations | 7,575 ms |
| `COL_15` | Document Types Config (`/college/documents`) | [`COL_15.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_15.json) | 1 warmup + 10 iterations | 3,278 ms |
| `COL_16` | Enrollment Management (`/college/enrollment`) | [`COL_16.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_16.json) | 1 warmup + 10 iterations | 5,449 ms |
| `COL_17` | Lifecycle Operations (`/college/lifecycle`) | [`COL_17.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_17.json) | 1 warmup + 10 iterations | 3,222 ms |
| `COL_18` | Faculty Directory (`/college/faculty`) | [`COL_18.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_18.json) | 1 warmup + 10 iterations | 1,150 ms |
| `COL_19` | College Audit Trail (`/college/audit`) | [`COL_19.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/COL_19.json) | 1 warmup + 10 iterations | 1,084 ms |
| `AUTH_FAC` | Faculty Login to Dashboard (`/faculty`) | [`AUTH_FAC.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/AUTH_FAC.json) | 1 warmup + 10 iterations | 1,026 ms |
| `FAC_01` | Faculty Dashboard (`/faculty`) | [`FAC_01.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/FAC_01.json) | 1 warmup + 10 iterations | 842 ms |
| `FAC_02` | Assigned Timetable (`/faculty/timetable`) | [`FAC_02.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/FAC_02.json) | 1 warmup + 10 iterations | 1,017 ms |
| `FAC_03` | Class Roster (`/faculty/classes`) | [`FAC_03.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/FAC_03.json) | 1 warmup + 10 iterations | 3,164 ms |
| `FAC_04` | Teaching Sessions (`/faculty/sessions`) | [`FAC_04.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/FAC_04.json) | 1 warmup + 10 iterations | 3,198 ms |
| `FAC_05` | Attendance Marking View (`/faculty/attendance`) | [`FAC_05.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/FAC_05.json) | 1 warmup + 10 iterations | 3,214 ms |
| `FAC_06` | Student Advisees (`/faculty/advisees`) | [`FAC_06.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/FAC_06.json) | 1 warmup + 10 iterations | 3,214 ms |
| `AUTH_STU1`| Student 1 Login to Dashboard (`/student`) | [`AUTH_STU1.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/AUTH_STU1.json) | 1 warmup + 10 iterations | 5,518 ms |
| `STU1_01` | Student Dashboard (`/student`) | [`STU1_01.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU1_01.json) | 1 warmup + 10 iterations | 5,419 ms |
| `STU1_02` | Student Admissions (`/student/admissions`) | [`STU1_02.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU1_02.json) | 1 warmup + 10 iterations | 5,345 ms |
| `STU1_03` | Student Address & Profile (`/student/address`) | [`STU1_03.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU1_03.json) | 1 warmup + 10 iterations | 3,294 ms |
| `STU1_04` | Student Corrections (`/student/corrections`) | [`STU1_04.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU1_04.json) | 1 warmup + 10 iterations | 3,302 ms |
| `STU1_05` | Student Documents (`/student/documents`) | [`STU1_05.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU1_05.json) | 1 warmup + 10 iterations | 1,032 ms |
| `STU1_06` | Student Course Enrollment (`/student/enrollment`) | [`STU1_06.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU1_06.json) | 1 warmup + 10 iterations | 5,308 ms |
| `STU1_07` | Student Lifecycle Tracking (`/student/lifecycle`)| [`STU1_07.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU1_07.json) | 1 warmup + 10 iterations | 3,243 ms |
| `AUTH_STU2`| Student 2 Login to Dashboard (`/student`) | [`AUTH_STU2.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/AUTH_STU2.json) | 1 warmup + 10 iterations | 5,486 ms |
| `STU2_01` | Student 2 Dashboard (`/student`) | [`STU2_01.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU2_01.json) | 1 warmup + 10 iterations | 5,457 ms |
| `STU2_02` | Student 2 Admissions (`/student/admissions`) | [`STU2_02.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU2_02.json) | 1 warmup + 10 iterations | 5,400 ms |
| `STU2_03` | Student 2 Address & Profile (`/student/address`) | [`STU2_03.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU2_03.json) | 1 warmup + 10 iterations | 3,342 ms |
| `STU2_04` | Student 2 Corrections (`/student/corrections`) | [`STU2_04.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU2_04.json) | 1 warmup + 10 iterations | 3,332 ms |
| `STU2_05` | Student 2 Documents (`/student/documents`) | [`STU2_05.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU2_05.json) | 1 warmup + 10 iterations | 1,301 ms |
| `STU2_06` | Student 2 Course Enrollment (`/student/enrollment`)| [`STU2_06.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU2_06.json) | 1 warmup + 10 iterations | 5,334 ms |
| `STU2_07` | Student 2 Lifecycle Tracking (`/student/lifecycle`)| [`STU2_07.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/STU2_07.json) | 1 warmup + 10 iterations | 3,321 ms |

---

## 4. Master Benchmark Aggregate Data File

The consolidated summary array containing the statistical aggregates for all evaluated flows is stored at:  
[`MASTER_BENCHMARK_SUMMARY.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence/MASTER_BENCHMARK_SUMMARY.json)

---

## 5. Phase B Post-Optimization Verification Evidence

Following implementation of REC-01, REC-02, REC-03 Part 1, REC-04, and REC-05, all 44 flows were re-benchmarked under identical conditions (Playwright Chromium, headed mode, slowMo 50, discarded warmup + timed iterations) with all 13 persona microservices active.

All raw telemetry files and route comparisons are indexed under:
- **Directory**: [`docs/latency-audit/evidence_after/`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence_after/)
- **Consolidated Summary**: [`AFTER_BENCHMARK_SUMMARY.json`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence_after/AFTER_BENCHMARK_SUMMARY.json)
- **Before vs. After Analysis (Joined by Route)**: [`08_BEFORE_AFTER.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/08_BEFORE_AFTER.md)


