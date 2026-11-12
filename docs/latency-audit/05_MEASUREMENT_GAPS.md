# CampXSync Latency Audit — Measurement Gaps & Limitations

**Audit Version**: 2.0.0  
**Audit Date**: November 11, 2026  
**Stack Context**: Local Development Environment (Next.js 15, Java API Gateway, Java Microservices)  
**Database**: Dev Supabase Project `azulbaxszsqxymqmlwfe` (AWS Mumbai)  

---

## 1. Overview

In accordance with Hard Boundary 5 (*"UI test actions must be non-destructive. If a flow cannot be exercised without a meaningful write, skip it and mark it NOT MEASURED – write required"*), destructive write mutations and state-modifying workflows were intentionally omitted from automated multi-iteration benchmarking.

This document catalogues every flow marked as **NOT MEASURED**, details the technical constraints preventing measurement, outlines the required instrumentation to capture them safely, and identifies environmental variances between the local benchmark harness and production deployment topologies.

---

## 2. Inventory of Omitted / Unmeasured Flows

| Persona | Flow Code | Flow Description | Reason Omitted | Required Instrumentation / Prerequisite |
|:---|:---|:---|:---|:---|
| **Platform Admin** | `PLT_W01` | Onboard New Institute Submit | **NOT MEASURED – write required** | Requires creating new tenant organization in `core.tenants`. Synthetic tenant cleanup harness needed. |
| **Platform Admin** | `PLT_W02` | Platform Config Update | **NOT MEASURED – write required** | Modifies global platform parameters. Reversible sandbox config fixture needed. |
| **College Admin** | `COL_W01` | Create Academic Program | **NOT MEASURED – write required** | Inserts into `acd.programs`. Automated teardown fixture required. |
| **College Admin** | `COL_W02` | Create Academic Batch | **NOT MEASURED – write required** | Inserts into `core.academic_batches`. Foreign key cascade cleanup needed. |
| **College Admin** | `COL_W03` | Create Student Record | **NOT MEASURED – write required** | Inserts into `stu.students`, `core.persons`, `iam.user_profiles`. |
| **College Admin** | `COL_W04` | Finalize Intake Decisions | **NOT MEASURED – write required** | Transitions applicant state to `ACCEPTED`/`REJECTED`. Permanent lifecycle change. |
| **Faculty** | `FAC_W01` | Submit Attendance Session | **NOT MEASURED – write required** | Writes attendance records to `stu.attendance_records`. Disturbs attendance percentage metrics. |
| **Student 1 / 2** | `STU_W01` | Submit Admission Application | **NOT MEASURED – write required** | Submits form creating un-revertible `STM-02` application rows. |
| **Student 1 / 2** | `STU_W02` | Upload Verification Document | **NOT MEASURED – write required** | Uploads binary blobs to Supabase Storage and records metadata. |
| **Student 1 / 2** | `STU_W03` | Submit Attendance Correction | **NOT MEASURED – write required** | Creates workflow approval tickets in `stu.attendance_corrections`. |

---

## 3. Instrumentation Gaps & Missing Telemetry

1. **Distributed Request Tracing (OpenTelemetry / W3C TraceContext)**:
   - *Current State*: Next.js assigns an `x-request-id` header; API Gateway assigns `X-Trace-Id` via `CorrelationFilter`. However, the correlation ID is not propagated across all downstream microservices and database sessions.
   - *Requirement*: Full W3C `traceparent` propagation across Next.js BFF, Gateway HTTP client, HikariCP JDBC connections, and PostgreSQL query tags (`SET application_name` / `/* trace_id=... */`).
2. **PostgreSQL Server-Side Statement Metrics (`pg_stat_statements`)**:
   - *Current State*: Dev Supabase database user lacks superuser permissions to reset or query detailed session-isolated `pg_stat_statements` under RLS.
   - *Requirement*: Dedicated read-only monitoring credentials with access to `pg_stat_statements` to measure buffer hits, dirty blocks, and remote execution timing per tenant.
3. **Internal Microservice Dispatch Metrics**:
   - *Current State*: When downstream microservices are offline, Gateway logs `ConnectException`, but does not export time-series metrics (`Timer` / Micrometer) for downstream connection latency.
   - *Requirement*: Micrometer metrics registry in `ReverseProxyHandler` recording `gateway.downstream.duration` tagged by route prefix and HTTP status.

---

## 4. Local vs. Production Topology Variances

The local audit stack introduces specific environmental differences compared to production cloud deployments:

| Dimension | Local Audit Stack | Production Cloud Topology | Net Latency Effect in Production |
|:---|:---|:---|:---|
| **Frontend Runtime** | Node.js 22 LTS standalone server (`npm run start` on localhost) | Vercel Serverless / Edge Functions | **Variance**: Serverless cold starts (500–1,500 ms on Vercel), but faster edge TLS termination. |
| **API Gateway** | JDK 1.8 HotSpot on Windows 11 host | AWS ECS / EKS / Containerized Linux | **Faster**: Linux epoll event loop; multi-core container CPU allocation. |
| **Network Hop: Gateway → Database** | Local PC Wi-Fi to AWS Mumbai (`26.5 ms` RTT) | AWS VPC Peering / Colocated in AWS `ap-south-1` | **Massive Improvement**: Intra-region latency drops from `26.5 ms` to **`<1.5 ms`**, reducing DB query overhead by ~95%. |
| **Static Asset CDN** | Local Next.js internal file server | Cloudflare / Vercel Global Edge CDN | **Faster**: Static chunks and images cached at regional edge PoPs. |
| **Host Concurrency** | Single PC running 6+ processes concurrently (93% RAM load) | Horizontally auto-scaled independent services | **Faster**: Eliminates local CPU context switching and RAM starvation. |

---

## 5. Summary

The latency numbers captured during this audit reflect the **true architectural characteristics** of the application code, routing table, identity resolution model, and database query topology. While colocating the backend with Supabase in AWS will drop network RTT, the multi-second downstream timeout penalties and redundant role resolution waterfalls identified in this audit will persist in production unless resolved.

