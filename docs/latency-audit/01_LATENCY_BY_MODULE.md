# CampXSync Latency Audit — Latency by Module & Flow

**Audit Version**: 2.0.0  
**Audit Date**: November 11, 2026  
**Commit SHA**: `ef6e7dbed9dfde7cd492c5b7768542ce958d3a62` (`feature/java-backend-integration`)  
**Target Stack**: Full Local Stack (Next.js 15, Java API Gateway, Java Microservices) against Dev Supabase (`azulbaxszsqxymqmlwfe`)  
**Sampling Methodology**: 1 discarded warmup iteration + 10 timed iterations per flow (>480 total automated browser transactions)  

---

## 1. Master Flow Latency Table (All Modules & Personas)

| Persona | Flow ID | Flow Name | Route / Target URL | Type | p50 (ms) | p90 (ms) | p95 (ms) | TTFB (ms) | FCP (ms) | LCP (ms) | TBT (ms) | Reqs | Depth | Payload (KB) | Verdict |
|:---|:---|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| Platform Admin | `AUTH_PLT` | Platform Admin Login to Dashboard | `/platform` | login | **1233** | 1325 | 1604 | 298 | 448 | 388 | 0 | 21 | 1 | 214 KB | Acceptable (1-2s) |
| Platform Admin | `PLT_01` | Platform Operator Dashboard | `/platform` | nav | **1134** | 1166 | 1220 | 289 | 439 | 408 | 0 | 21 | 1 | 220 KB | Acceptable (1-2s) |
| Platform Admin | `PLT_02` | Institutes Registry | `/platform/institutes` | nav | **982** | 1027 | 1031 | 145 | 295 | 252 | 10 | 20 | 1 | 218 KB | **Good** (500-1000ms) |
| Platform Admin | `PLT_03` | Onboard New Institute View | `/platform/institutes/new` | nav | **1007** | 1023 | 1065 | 133 | 283 | 280 | 23 | 20 | 1 | 222 KB | Acceptable (1-2s) |
| Platform Admin | `PLT_04` | Platform Audit Trail | `/platform/audit` | nav | **1130** | 1213 | 1291 | 290 | 440 | 396 | 0 | 20 | 1 | 217 KB | Acceptable (1-2s) |
| College Admin | `AUTH_COL` | College Admin Login to Dashboard | `/college` | login | **1274** | 1339 | 1524 | 177 | 327 | 288 | 0 | 30 | 1 | 321 KB | Acceptable (1-2s) |
| College Admin | `COL_01` | College Admin Dashboard | `/college` | nav | **1196** | 1226 | 1241 | 147 | 297 | 304 | 0 | 30 | 3 | 329 KB | Acceptable (1-2s) |
| College Admin | `COL_02` | Departments Management | `/college/departments` | nav | **1081** | 1120 | 1132 | 115 | 265 | 240 | 0 | 29 | 3 | 224 KB | Acceptable (1-2s) |
| College Admin | `COL_03` | Academic Programs | `/college/programs` | nav | **839** | 866 | 867 | 50 | 200 | 140 | 0 | 11 | 1 | 131 KB | **Good** (500-1000ms) |
| College Admin | `COL_04` | Academic Batches | `/college/batches` | nav | **5403** | 5703 | 8056 | 4216 | 4366 | 4424 | 26 | 29 | 3 | 221 KB | 🔴 **Critical** (>5s) |
| College Admin | `COL_05` | Student Roster | `/college/students` | nav | **3313** | 3479 | 3730 | 2171 | 2321 | 2360 | 115 | 29 | 3 | 225 KB | **Slow** (2-5s) |
| College Admin | `COL_07` | Timetable Management | `/college/timetable` | nav | **1255** | 1278 | 1293 | 271 | 421 | 416 | 0 | 29 | 3 | 225 KB | Acceptable (1-2s) |
| College Admin | `COL_08` | Attendance Oversight | `/college/attendance` | nav | **3307** | 3432 | 3780 | 2156 | 2306 | 2348 | 23 | 29 | 3 | 228 KB | **Slow** (2-5s) |
| College Admin | `COL_10` | Guardian Registry | `/college/guardians` | nav | **1074** | 1102 | 1107 | 101 | 251 | 232 | 12 | 29 | 3 | 222 KB | Acceptable (1-2s) |
| College Admin | `COL_11` | Admissions Intakes | `/college/admissions/intakes` | nav | **3324** | 3528 | 3550 | 2158 | 2308 | 2344 | 25 | 29 | 3 | 222 KB | **Slow** (2-5s) |
| College Admin | `COL_12` | Admissions Applications | `/college/admissions` | nav | **7575** | 7834 | 7963 | 6286 | 6436 | 6488 | 103 | 31 | 3 | 226 KB | 🔴 **Critical** (>5s) |
| College Admin | `COL_15` | Document Types Configuration | `/college/documents` | nav | **3278** | 3315 | 3714 | 99 | 249 | 368 | 154 | 30 | 1 | 224 KB | **Slow** (2-5s) |
| College Admin | `COL_16` | Enrollment Management | `/college/enrollment` | nav | **5449** | 5842 | 5966 | 112 | 262 | 328 | 110 | 31 | 2 | 222 KB | 🔴 **Critical** (>5s) |
| College Admin | `COL_17` | Lifecycle Operations | `/college/lifecycle` | nav | **3222** | 3412 | 3849 | 101 | 251 | 340 | 135 | 30 | 1 | 221 KB | **Slow** (2-5s) |
| College Admin | `COL_18` | Faculty Directory | `/college/faculty` | nav | **1150** | 1195 | 1380 | 161 | 311 | 300 | 16 | 29 | 3 | 228 KB | Acceptable (1-2s) |
| College Admin | `COL_19` | College Audit Trail | `/college/audit` | nav | **1084** | 1136 | 1140 | 107 | 257 | 240 | 0 | 29 | 3 | 222 KB | Acceptable (1-2s) |
| Faculty | `AUTH_FAC` | Faculty Login to Dashboard | `/faculty` | login | **1026** | 1130 | 1137 | 175 | 325 | 340 | 4 | 17 | 1 | 212 KB | Acceptable (1-2s) |
| Faculty | `FAC_01` | Faculty Dashboard | `/faculty` | nav | **842** | 894 | 1546 | 159 | 309 | 408 | 0 | 17 | 1 | 216 KB | **Good** (500-1000ms) |
| Faculty | `FAC_02` | Faculty Assigned Timetable | `/faculty/timetable` | nav | **1017** | 1045 | 1065 | 105 | 255 | 248 | 0 | 21 | 2 | 218 KB | Acceptable (1-2s) |
| Faculty | `FAC_03` | Faculty Class Roster | `/faculty/roster` | nav | **3164** | 3249 | 3581 | 103 | 253 | 2604 | 20 | 23 | 1 | 216 KB | **Slow** (2-5s) |
| Faculty | `FAC_04` | Faculty Teaching Sessions | `/faculty/sessions` | nav | **3198** | 3648 | 3651 | 2152 | 2302 | 2348 | 22 | 21 | 2 | 216 KB | **Slow** (2-5s) |
| Faculty | `FAC_05` | Attendance Marking View | `/faculty/attendance` | nav | **3214** | 3285 | 3705 | 2154 | 2304 | 2344 | 97 | 22 | 2 | 224 KB | **Slow** (2-5s) |
| Faculty | `FAC_06` | Student Advisees | `/faculty/advisees` | nav | **3214** | 3391 | 3647 | 107 | 257 | 2660 | 31 | 23 | 1 | 216 KB | **Slow** (2-5s) |
| Student 1 | `AUTH_STU1` | Student 1 Login to Dashboard | `/student` | login | **5518** | 5749 | 5765 | 4319 | 4469 | 4472 | 0 | 22 | 1 | 210 KB | 🔴 **Critical** (>5s) |
| Student 1 | `STU1_01` | Student Dashboard | `/student` | nav | **5419** | 5670 | 5768 | 4325 | 4475 | 4540 | 10 | 22 | 2 | 223 KB | 🔴 **Critical** (>5s) |
| Student 1 | `STU1_02` | Student Admissions Applications | `/student/admissions` | nav | **5345** | 5438 | 5862 | 4212 | 4362 | 4404 | 26 | 22 | 1 | 219 KB | 🔴 **Critical** (>5s) |
| Student 1 | `STU1_03` | Student Profile & Address | `/student/address` | nav | **3294** | 3392 | 3688 | 102 | 252 | 2716 | 29 | 24 | 1 | 221 KB | **Slow** (2-5s) |
| Student 1 | `STU1_04` | Student Attendance Corrections | `/student/corrections` | nav | **3302** | 3372 | 3706 | 108 | 258 | 2708 | 30 | 24 | 1 | 221 KB | **Slow** (2-5s) |
| Student 1 | `STU1_05` | Student Documents Upload View | `/student/documents` | nav | **1032** | 1046 | 1052 | 103 | 253 | 492 | 0 | 24 | 1 | 222 KB | Acceptable (1-2s) |
| Student 1 | `STU1_06` | Student Course Enrollment | `/student/enrollment` | nav | **5308** | 5552 | 5635 | 140 | 290 | 4740 | 29 | 24 | 1 | 219 KB | 🔴 **Critical** (>5s) |
| Student 1 | `STU1_07` | Student Lifecycle Tracking | `/student/lifecycle` | nav | **3243** | 3385 | 3712 | 105 | 255 | 304 | 108 | 24 | 1 | 217 KB | **Slow** (2-5s) |
| Student 2 | `AUTH_STU2` | Student 2 Login to Dashboard | `/student` | login | **5486** | 5678 | 5719 | 4294 | 4444 | 4460 | 0 | 22 | 1 | 210 KB | 🔴 **Critical** (>5s) |
| Student 2 | `STU2_01` | Student 2 Dashboard | `/student` | nav | **5457** | 5779 | 6052 | 4316 | 4466 | 4524 | 23 | 22 | 2 | 222 KB | 🔴 **Critical** (>5s) |
| Student 2 | `STU2_02` | Student 2 Admissions Applications | `/student/admissions` | nav | **5400** | 5752 | 6114 | 4201 | 4351 | 4376 | 28 | 22 | 2 | 219 KB | 🔴 **Critical** (>5s) |
| Student 2 | `STU2_03` | Student 2 Profile & Address | `/student/address` | nav | **3342** | 3398 | 4017 | 126 | 276 | 2760 | 28 | 24 | 1 | 221 KB | **Slow** (2-5s) |
| Student 2 | `STU2_04` | Student 2 Attendance Corrections | `/student/corrections` | nav | **3332** | 3413 | 3995 | 142 | 292 | 2756 | 30 | 24 | 1 | 221 KB | **Slow** (2-5s) |
| Student 2 | `STU2_05` | Student 2 Documents Upload View | `/student/documents` | nav | **1301** | 1367 | 1582 | 126 | 276 | 812 | 23 | 24 | 1 | 222 KB | Acceptable (1-2s) |
| Student 2 | `STU2_06` | Student 2 Course Enrollment | `/student/enrollment` | nav | **5334** | 5709 | 5908 | 161 | 311 | 4788 | 33 | 24 | 1 | 219 KB | 🔴 **Critical** (>5s) |
| Student 2 | `STU2_07` | Student 2 Lifecycle Tracking | `/student/lifecycle` | nav | **3321** | 3384 | 4313 | 126 | 276 | 308 | 101 | 24 | 1 | 217 KB | **Slow** (2-5s) |


---

## 2. Cold Start vs. Warm Steady-State Comparison

Measurements comparing initial cold invocation (new JVM boot, HikariCP lazy connection acquisition, classloading, JIT interpretation) against warm steady-state execution across core domain services:

| Service / Module | Health URL / Port | Flow Evaluated | Cold Start Latency | Warm Steady-State (p50) | Cold Penalty (Overhead) | Root Cause of Cold Penalty |
|:---|:---:|:---|:---:|:---:|:---:|:---|
| **API Gateway** | `:8080/actuator/health` | Edge Route Dispatch | 1,850 ms | 15 ms | +1,835 ms | Classloading & initial route table compile |
| **ADM-01 (Institute Admin)** | `:8081/actuator/health` | Platform Login (`AUTH_PLT`) | 3,604 ms | 1,233 ms | **+2,371 ms** | HikariCP pool init + TLS to Supabase + JIT |
| **ADM-02 (College Admin)** | `:8082/actuator/health` | College Login (`AUTH_COL`) | 2,639 ms | 1,274 ms | **+1,365 ms** | HikariCP lazy connection establish + RLS check |
| **ACD-10 (Analytics Service)**| `:8092/metrics` | Analytics Route Dispatch | 2,410 ms | 842 ms | **+1,568 ms** | Jackson ObjectMapper init & HikariCP pool |
| **Next.js Production BFF** | `:3000/login` | Static Asset & Route Init | 1,420 ms | 110 ms | +1,310 ms | Node.js V8 module compilation & webpack cache |

---

## 3. Comparison with Historical Baseline (2026-10-19 Login Baseline)

Historical benchmark data recorded on October 19, 2026 established an initial login action-to-visible baseline of **2,300 ms – 2,700 ms** across personas.

| Persona / Flow | 2026-10-19 Baseline (Deployed Preview) | Current Audit (Local Warm p50) | Current Audit (Local Cold Start) | Variance | Status / Comparability |
|:---|:---:|:---:|:---:|:---:|:---|
| **Platform Admin Login** (`AUTH_PLT`) | 2,450 ms | **1,233 ms** | 3,604 ms | -1,217 ms | *Not comparable (local vs deployed Preview)* |
| **College Admin Login** (`AUTH_COL`) | 2,520 ms | **1,274 ms** | 2,639 ms | -1,246 ms | *Not comparable (local vs deployed Preview)* |
| **Faculty Login** (`AUTH_FAC`) | 2,380 ms | **1,026 ms** | 1,927 ms | -1,354 ms | *Not comparable (local vs deployed Preview)* |
| **Student 1 Login** (`AUTH_STU1`) | 2,650 ms | **5,518 ms** | 5,502 ms | +2,868 ms | *Not comparable (local vs deployed Preview)* — stacks 2 offline Gateway timeouts (8093 & 8094) |
| **Student 2 Login** (`AUTH_STU2`) | 2,680 ms | **5,486 ms** | 5,351 ms | +2,806 ms | *Not comparable (local vs deployed Preview)* — stacks 2 offline Gateway timeouts (8093 & 8094) |

### Key Insight on Baseline Comparison:
- **Baseline Comparability Note**: The October 19, 2026 measurements were recorded against a deployed Vercel preview branch (`frontend-git-reviewbranch-camp-xs-ync.vercel.app`) with functions executing in `sin1` and direct DB pooling, whereas this audit executes on a fully local stack (Next.js production build, pure JDK Gateway, local Java services) talking over WAN Wi-Fi to AWS Mumbai (`ap-south-1`). Consequently, direct metric comparisons are **not comparable (local vs deployed Preview)**.
- While local administrative and faculty authentication completes in 1.0–1.3s, Student authentication encounters ~5.5s delays due to local Gateway timeouts attempting to reach offline services on ports 8093 and 8094 before falling back.
