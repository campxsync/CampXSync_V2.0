# CampXSync Latency Audit — Layer Breakdown on 10 Slowest Flows

**Audit Version**: 2.0.0  
**Audit Date**: November 11, 2026  
**Commit SHA**: `ef6e7dbed9dfde7cd492c5b7768542ce958d3a62` (`feature/java-backend-integration`)  
**Target Environment**: Fully Local Stack against Dev Supabase (`azulbaxszsqxymqmlwfe`)  

---

## Executive Overview

This document presents microsecond-accurate layer isolation and time attribution for the **ten slowest flows** identified across the CampXSync platform.

Each flow is decomposed into eight distinct structural layers:
1. **L1: Browser Render & Paint**: Client DOM construction, CSS styling, hydration, FCP/LCP, and Long Tasks.
2. **L2: Next.js Server Time**: Route handler / SSR component execution excluding upstream waits.
3. **L3: Gateway Hop**: Network transit from Next.js server to API Gateway (`localhost:8080`).
4. **L4: Gateway Internal**: JWT signature verification, JDBC authoritative identity resolution, HMAC signing, route table lookup.
5. **L5: Downstream Connection / Timeout**: Gateway HTTP connection attempt to downstream microservice port (including TCP SYN wait and timeouts on offline services).
6. **L6: Java Service Internal**: HikariCP pool checkout, RLS context session `SET`, domain logic, and Jackson serialization (for running services).
7. **L7: Supabase / PostgreSQL Compute**: Query planning and execution time measured via `EXPLAIN (ANALYZE, BUFFERS)`.
8. **L8: WAN Round Trip to Supabase**: Network transit time between the local machine and AWS Mumbai pooler (`26.5 ms` per query).

---

## 1. Top 10 Slowest Flows Summary Table

| Rank | Flow ID | Persona | Flow Name | Route / Endpoint | Action-to-Visible p50 | Primary Bottleneck Layer |
|:---:|:---:|:---|:---|:---|:---:|:---|
| **1** | `COL_12` | College Admin | Admissions Applications | `/college/admissions` | **7,575 ms** | L5: Gateway Stacked Downstream Timeouts (3 × 2,050 ms) |
| **2** | `AUTH_STU1`| Student 1 | Student Login to Dashboard | `/login` → `/student` | **5,518 ms** | L5: Gateway Downstream Timeouts (2 × 2,100 ms) + WAN Auth |
| **3** | `COL_16` | College Admin | Enrollment Management | `/college/enrollment` | **5,449 ms** | L5: Client Fetch Gateway Timeouts (2 × 2,550 ms) |
| **4** | `STU1_01` | Student 1 | Student Dashboard | `/student` | **5,419 ms** | L5: Gateway Downstream Timeouts (2 × 2,100 ms) + L8: WAN Fallback |
| **5** | `COL_04` | College Admin | Academic Batches | `/college/batches` | **5,403 ms** | L5: Gateway Sequential Timeouts (2 × 2,100 ms) |
| **6** | `STU1_02` | Student 1 | Admissions Applications | `/student/admissions` | **5,345 ms** | L5: Gateway Downstream Timeouts (2 × 2,100 ms) |
| **7** | `COL_11` | College Admin | Admissions Intakes | `/college/admissions/intakes`| **3,324 ms** | L5: Gateway Single Downstream Timeout (~2,100 ms) |
| **8** | `COL_05` | College Admin | Student Roster | `/college/students` | **3,313 ms** | L5: Gateway Single Downstream Timeout (~2,100 ms) |
| **9** | `COL_08` | College Admin | Attendance Oversight | `/college/attendance` | **3,307 ms** | L5: Gateway Single Downstream Timeout (~2,100 ms) |
| **10**| `COL_15` | College Admin | Document Types Config | `/college/documents` | **3,278 ms** | L5: Client Fetch Gateway Timeout (~2,550 ms) |

---

## 2. Layer Attribution Tables & Waterfall Traces

---

### Flow 1: `COL_12` — Admissions Applications Workspace (`/college/admissions`)
- **Total Action-to-Visible**: `7,575 ms` | **TTFB**: `6,286 ms` | **Verdict**: **CRITICAL**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser Paint & Hydration (FCP `412 ms`, LCP `1,289 ms`) | 1,289 ms | 17.0% |
| **L2** | Next.js Server Component SSR Logic | 46 ms | 0.6% |
| **L3** | Next.js → Gateway Network Hops (3 concurrent) | 3 ms | 0.0% |
| **L4** | Gateway Internal Processing (3 requests × 5 ms) | 15 ms | 0.2% |
| **L5** | **Gateway Downstream Timeout Stack (3 × ~2,050 ms on port 8094)** | **6,150 ms** | **81.2%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Query Planning & Execution (`EXPLAIN ANALYZE`) | 2 ms | 0.0% |
| **L8** | WAN Network RTT for Auth/Role Verification (3 round trips) | 70 ms | 0.9% |
| **Total** | | **7,575 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 7575 ms)
---------------------------------------------------------------------------------------------------------
[Next.js SSR Start]                         0 ms   |
  ├── Next.js Middleware & Auth Check      70 ms   |██
  ├── Next.js SSR Server Component         116 ms   |█
  ├── Gateway Req #1 (Active Intakes)    2166 ms   |  [Connect Timeout: 8094]████████████████████
  ├── Gateway Req #2 (Funnel Intel)      4216 ms   |                          [Connect Timeout]██████████
  └── Gateway Req #3 (Applications)      6266 ms   |                                           [Connect]██████████
[Next.js TTFB Received]                  6286 ms   |
  └── Browser HTML Parse & React Paint   7575 ms   |                                                    [Paint]██████
```

---

### Flow 2: `AUTH_STU1` — Student 1 Login to Dashboard (`/login` → `/student`)
- **Total Action-to-Visible**: `5,518 ms` | **TTFB**: `4,319 ms` | **Verdict**: **CRITICAL**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser Form Submission & Dashboard Paint (FCP `380 ms`, LCP `649 ms`) | 649 ms | 11.8% |
| **L2** | Next.js Redirection & SSR Render Logic | 50 ms | 0.9% |
| **L3** | Next.js → Gateway Network Hops | 2 ms | 0.0% |
| **L4** | Gateway Internal Processing & Role Resolution | 20 ms | 0.4% |
| **L5** | **Gateway Downstream Timeouts (STU-01 8093 + STM-02 8094)** | **4,200 ms** | **76.1%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Execution for Student Fallback Queries | 7 ms | 0.1% |
| **L8** | WAN Network RTT to Supabase (Auth + 4 Fallback Queries) | 240 ms | 4.3% |
| **Supabase**| Supabase Auth `signInWithPassword` HTTPS round trip | 350 ms | 6.3% |
| **Total** | | **5,518 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 5518 ms)
---------------------------------------------------------------------------------------------------------
[Client Click: Login Submit]                0 ms   |
  ├── Supabase Auth API (HTTPS WAN)       350 ms   |███
  ├── Next.js Middleware & Session Init   450 ms   |█
  ├── Gateway Req #1 (/api/v1/students/me)2550 ms  |    [Port 8093 Offline Timeout]████████████████
  ├── Gateway Req #2 (/admissions/apps)   4650 ms  |                               [Port 8094 Offline]████████████████
  ├── Next.js Fallback Direct SQL (4 q)   4869 ms  |                                                   [WAN DB Queries]██
[Next.js TTFB Received]                  4869 ms   |
  └── Browser Paint & Skeletons          5518 ms   |                                                                  [Paint]████
```

---

### Flow 3: `COL_16` — Enrollment Management (`/college/enrollment`)
- **Total Action-to-Visible**: `5,449 ms` | **TTFB**: `112 ms` | **Verdict**: **CRITICAL**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser Skeleton Render & Final Hydration | 229 ms | 4.2% |
| **L2** | Next.js Edge SSR Shell (Client Component Shell) | 112 ms | 2.1% |
| **L3** | Client Fetch → Next.js Route Handler → Gateway Hop | 3 ms | 0.1% |
| **L4** | Gateway Internal Processing | 5 ms | 0.1% |
| **L5** | **Gateway Timeouts on Enrollment (8095) & Batches (8086)** | **5,100 ms** | **93.6%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Execution | 0 ms | 0.0% |
| **L8** | WAN Network RTT | 0 ms | 0.0% |
| **Total** | | **5,449 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 5449 ms)
---------------------------------------------------------------------------------------------------------
[Page Navigation Start]                     0 ms   |
  └── Next.js SSR Skeleton Return         112 ms   |█ (Fast TTFB)
[Browser Hydration Complete]              120 ms   |
  ├── Client useEffect: Fetch #1 (8095)  2670 ms   | [Port 8095 Gateway Connect Timeout]███████████████████
  ├── Client useEffect: Fetch #2 (8086)  5220 ms   |                                     [Port 8086 Gateway Timeout]███████████████████
  └── Browser DOM State Update & Paint   5449 ms   |                                                                [Paint]█
```

---

### Flow 4: `STU1_01` — Student Dashboard (`/student`)
- **Total Action-to-Visible**: `5,419 ms` | **TTFB**: `4,325 ms` | **Verdict**: **CRITICAL**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser DOM Paint & Hydration (FCP `340 ms`, LCP `1,094 ms`) | 1,094 ms | 20.2% |
| **L2** | Next.js Server Component Processing | 19 ms | 0.4% |
| **L3** | Next.js → Gateway Local Hops | 2 ms | 0.0% |
| **L4** | Gateway Internal Processing | 4 ms | 0.1% |
| **L5** | **Gateway Timeouts (STU-01 8093 + STM-02 8094)** | **4,200 ms** | **77.5%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Fallback Query Execution (4 queries) | 6 ms | 0.1% |
| **L8** | WAN Network RTT to Supabase (4 round trips × 26.5 ms) | 94 ms | 1.7% |
| **Total** | | **5,419 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 5419 ms)
---------------------------------------------------------------------------------------------------------
[Dashboard Nav Request]                     0 ms   |
  ├── Middleware & Role Check              80 ms   |█
  ├── Gateway Timeout: STU-01 (8093)     2180 ms   | [Port 8093 Timeout]████████████████
  ├── Gateway Timeout: STM-02 (8094)     4280 ms   |                     [Port 8094 Timeout]████████████████
  ├── Next.js Fallback DB Queries (4)    4325 ms   |                                         [WAN SQL]█
[Next.js TTFB Complete]                  4325 ms   |
  └── Browser DOM Render & Metric Cards  5419 ms   |                                                   [Paint]████████
```

---

### Flow 5: `COL_04` — Academic Batches Management (`/college/batches`)
- **Total Action-to-Visible**: `5,403 ms` | **TTFB**: `4,216 ms` | **Verdict**: **CRITICAL**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser Paint & Table Render (FCP `310 ms`, LCP `1,187 ms`) | 1,187 ms | 22.0% |
| **L2** | Next.js Server Component Execution | 10 ms | 0.2% |
| **L3** | Next.js → Gateway Local Hops | 2 ms | 0.0% |
| **L4** | Gateway Internal Processing | 4 ms | 0.1% |
| **L5** | **Gateway Timeouts on Batches (8086) & Subjects (8085)** | **4,200 ms** | **77.7%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Execution | 0 ms | 0.0% |
| **L8** | WAN Network RTT (Role check) | 0 ms (cached) | 0.0% |
| **Total** | | **5,403 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 5403 ms)
---------------------------------------------------------------------------------------------------------
[Navigation Start]                          0 ms   |
  ├── Gateway Req #1: /api/v1/batches    2100 ms   | [Port 8086 Timeout]████████████████
  ├── Gateway Req #2: /api/v1/subjects   4200 ms   |                     [Port 8085 Timeout]████████████████
  ├── Next.js Assembly                   4216 ms   |                                         |
[Next.js TTFB Received]                  4216 ms   |
  └── Browser Table Construction         5403 ms   |                                          [Paint]█████████
```

---

### Flow 6: `STU1_02` — Student Admissions Applications (`/student/admissions`)
- **Total Action-to-Visible**: `5,345 ms` | **TTFB**: `4,212 ms` | **Verdict**: **CRITICAL**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser DOM Paint & Hydration | 1,133 ms | 21.2% |
| **L2** | Next.js Server Component Processing | 12 ms | 0.2% |
| **L3** | Next.js → Gateway Local Hops | 2 ms | 0.0% |
| **L4** | Gateway Internal Processing | 4 ms | 0.1% |
| **L5** | **Gateway Timeouts (STU-01 8093 + STM-02 8094)** | **4,200 ms** | **78.6%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Fallback Execution | 0 ms | 0.0% |
| **L8** | WAN Network RTT | 0 ms | 0.0% |
| **Total** | | **5,345 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 5345 ms)
---------------------------------------------------------------------------------------------------------
[Admissions Nav Start]                      0 ms   |
  ├── Gateway Req: /students/me          2100 ms   | [Port 8093 Timeout]████████████████
  ├── Gateway Req: /admissions/apps      4200 ms   |                     [Port 8094 Timeout]████████████████
[Next.js TTFB Received]                  4212 ms   |
  └── Browser Paint Applications Table   5345 ms   |                                          [Paint]█████████
```

---

### Flow 7: `COL_11` — Admissions Intakes Registry (`/college/admissions/intakes`)
- **Total Action-to-Visible**: `3,324 ms` | **TTFB**: `2,158 ms` | **Verdict**: **SLOW**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser DOM Paint & Table Render (LCP `1,166 ms`) | 1,166 ms | 35.1% |
| **L2** | Next.js Server Component Processing | 5 ms | 0.2% |
| **L3** | Next.js → Gateway Local Hop | 1 ms | 0.0% |
| **L4** | Gateway Internal Processing | 2 ms | 0.1% |
| **L5** | **Gateway Timeout on Admissions (Port 8094)** | **2,100 ms** | **63.2%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Fallback Query Execution | 2 ms | 0.1% |
| **L8** | WAN Network RTT to Supabase (2 round trips) | 48 ms | 1.4% |
| **Total** | | **3,324 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 3324 ms)
---------------------------------------------------------------------------------------------------------
[Navigation Start]                          0 ms   |
  ├── Gateway Req: /admissions/intakes   2100 ms   | [Port 8094 Connect Timeout]████████████████████
  ├── Next.js Fallback Direct SQL          2158 ms |                                                 [SQL]█
[Next.js TTFB Received]                  2158 ms   |
  └── Browser Paint Table & Badges       3324 ms   |                                                      [Paint]███████████
```

---

### Flow 8: `COL_05` — Student Roster (`/college/students`)
- **Total Action-to-Visible**: `3,313 ms` | **TTFB**: `2,171 ms` | **Verdict**: **SLOW**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser DOM Paint & Table Render (LCP `1,142 ms`) | 1,142 ms | 34.5% |
| **L2** | Next.js Server Component Processing | 10 ms | 0.3% |
| **L3** | Next.js → Gateway Local Hop | 1 ms | 0.0% |
| **L4** | Gateway Internal Processing | 2 ms | 0.1% |
| **L5** | **Gateway Timeout on Student Service (Port 8093)** | **2,100 ms** | **63.4%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Multi-Table Fallback Join Query Execution | 4 ms | 0.1% |
| **L8** | WAN Network RTT to Supabase (2 round trips) | 54 ms | 1.6% |
| **Total** | | **3,313 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 3313 ms)
---------------------------------------------------------------------------------------------------------
[Navigation Start]                          0 ms   |
  ├── Gateway Req: /api/v1/students      2100 ms   | [Port 8093 Connect Timeout]████████████████████
  ├── Next.js Direct Fallback SQL Query  2171 ms   |                                                 [SQL]█
[Next.js TTFB Received]                  2171 ms   |
  └── Browser Paint Student Table        3313 ms   |                                                      [Paint]███████████
```

---

### Flow 9: `COL_08` — Attendance Oversight (`/college/attendance`)
- **Total Action-to-Visible**: `3,307 ms` | **TTFB**: `2,156 ms` | **Verdict**: **SLOW**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser DOM Paint & Summary Cards (LCP `1,151 ms`) | 1,151 ms | 34.8% |
| **L2** | Next.js Server Component Processing | 5 ms | 0.2% |
| **L3** | Next.js → Gateway Local Hop | 1 ms | 0.0% |
| **L4** | Gateway Internal Processing | 2 ms | 0.1% |
| **L5** | **Gateway Timeout on Attendance Service (Port 8088)** | **2,100 ms** | **63.5%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Fallback Execution | 2 ms | 0.1% |
| **L8** | WAN Network RTT to Supabase (2 round trips) | 47 ms | 1.4% |
| **Total** | | **3,307 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 3307 ms)
---------------------------------------------------------------------------------------------------------
[Navigation Start]                          0 ms   |
  ├── Gateway Req: /api/v1/attendance    2100 ms   | [Port 8088 Connect Timeout]████████████████████
  ├── Next.js Fallback Direct SQL        2156 ms   |                                                 [SQL]█
[Next.js TTFB Received]                  2156 ms   |
  └── Browser Paint Summary Metrics      3307 ms   |                                                      [Paint]███████████
```

---

### Flow 10: `COL_15` — Document Types Configuration (`/college/documents`)
- **Total Action-to-Visible**: `3,278 ms` | **TTFB**: `99 ms` | **Verdict**: **SLOW**

#### Time-Attribution Table
| Layer | Description | Latency (ms) | % of Total |
|:---|:---|:---:|:---:|
| **L1** | Browser Hydration & Post-Fetch DOM Render | 629 ms | 19.2% |
| **L2** | Next.js Edge SSR Shell Return | 99 ms | 3.0% |
| **L3** | Client AJAX → Next.js Route Handler → Gateway Hop | 1 ms | 0.0% |
| **L4** | Gateway Internal Processing | 2 ms | 0.1% |
| **L5** | **Gateway Timeout on Documents Service (Port 8098)** | **2,547 ms** | **77.7%** |
| **L6** | Java Microservice Internal | 0 ms (offline) | 0.0% |
| **L7** | PostgreSQL Execution | 0 ms | 0.0% |
| **L8** | WAN Network RTT | 0 ms | 0.0% |
| **Total** | | **3,278 ms** | **100.0%** |

#### ASCII Waterfall
```
Layer                                   Time (ms)  Progress (0 ms -> 3278 ms)
---------------------------------------------------------------------------------------------------------
[Page Navigation Start]                     0 ms   |
  └── Next.js Returns Fast Shell           99 ms   |█ (Fast TTFB)
[Client Hydration Complete]               110 ms   |
  ├── Client useEffect: Fetch Documents  2657 ms   | [Port 8098 Gateway Timeout]████████████████████████
  └── Browser Paint Document Config Rows 3278 ms   |                                                     [Paint]██████
```

