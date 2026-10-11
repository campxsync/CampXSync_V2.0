# Performance Targets & SLA/SLO Specification
## Service: CampXSync Curriculum Management Service (ACD-02) (`services/curriculum-service`)

### 1. Service Level Objectives (SLOs) & Latency Budgets

| Endpoint / Operation | HTTP Method | Target P50 | Target P95 | Target P99 | Throughput (Peak RPS) | Error Budget (Monthly) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Active Catalog Lookup** (`/api/v1/curricula/active`) | `GET` | < 15ms | < 50ms | < 120ms | 500 rps | 99.99% success |
| **Curriculum Detail View** (`/api/v1/curricula/{id}`) | `GET` | < 20ms | < 60ms | < 150ms | 300 rps | 99.95% success |
| **Accreditation Compliance View** (`.../compliance-view`) | `GET` | < 40ms | < 120ms | < 300ms | 50 rps | 99.9% success |
| **Create Curriculum Aggregate** (`/api/v1/curricula`) | `POST` | < 35ms | < 100ms | < 250ms | 50 rps | 99.95% success |
| **Map Subject / Credit Calculation** (`.../subjects`) | `POST` | < 25ms | < 80ms | < 200ms | 80 rps | 99.95% success |
| **State Transitions (Approve / Publish / Revision)** | `POST` | < 45ms | < 150ms | < 350ms | 30 rps | 99.99% success |
| **Prometheus Metrics Endpoint** (`/metrics`) | `GET` | < 5ms | < 15ms | < 40ms | 20 rps | 99.99% success |

---

### 2. Database & Resource Latency Budgets

1. **MongoDB Query Execution Budget**:
   - Primary key / Unique index point lookups: `< 5ms` execution time (`COLLSCAN` strictly prohibited).
   - Compound index scans (`idx_tenant_status`, `idx_tenant_department`): `< 15ms` execution time.
   - Atomic document writes with outbox events (`TransactionContext`): `< 25ms` total round-trip.
2. **JVM Heap & GC Budget**:
   - Target GC pause: `< 20ms` (G1GC default max pause target).
   - Heap footprint: `< 1.2 GB` under sustained 500 RPS load.
3. **Outbox Relay Latency**:
   - Time from aggregate commit to broker publish: `< 2000ms` (P95), `< 5000ms` (P99).

---

### 3. Load & Stress Test Profile

- **Baseline Load**: 100 req/sec steady-state across 50 concurrent tenant sessions.
- **Peak Burst Load**: 500 req/sec during college term enrollment and accreditation audit cycles.
- **Soak Testing Profile**: 72-hour sustained run at 200 req/sec with continuous idempotency cleanup and outbox processing to verify zero memory leaks in JVM heap or in-memory caches.

---

### 4. Continuous Optimization Guidelines

1. **Index Coverage**: All query predicates (`tenantId`, `status`, `departmentId`, `academicYear`) must be satisfied via index scans (`IXSCAN`).
2. **Response Compression**: Enable HTTP gzip compression at API Gateway for payloads exceeding 2KB.
3. **Idempotency Cache**: Retain 24-hour TTL in memory / MongoDB to prevent duplicate request processing overhead.
4. **Data Classification Filtering**: In-memory response filtering executes in `< 0.5ms` per record prior to JSON serialization.
